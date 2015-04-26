(ns glimmering.examples
  (:require [glimmering.api :as glimmering]
            [sparkling.api :as spark]
            [sparkling.conf :as conf]
            [t6.from-scala.core :refer [$] :as $])
  (:import [org.apache.spark.graphx.util GraphGenerators$]
           [org.apache.spark.graphx GraphOps]
           [scala.collection Iterator Iterator$]))

(comment (def master "local")
         (def conf {})
         (def env {})

         (def c
           (-> (conf/spark-conf)
               (conf/master master)
               (conf/app-name "svd")
               (conf/set "spark.akka.timeout" "300")
               (conf/set conf)
               (conf/set-executor-env env)))

         (defonce sc
           (spark/spark-context c)))

(defn make-edges [sc]
  (spark/parallelize sc [(glimmering/edge 1 2 3)
                         (glimmering/edge 2 1 5)]))

(defn make-vertices [sc]
  (spark/parallelize-pairs sc [#sparkling/tuple[1 1]
                               #sparkling/tuple[2 2]]))

(defn make-graph []
  (let [v (make-vertices)
        e (make-edges)]
    (glimmering/graph v e)))

(comment (defn generate-graph []
   (let [graph (.logNormalGraph GraphGenerators$/MODULE$ (.sc sc) 5 0 4.0 1.3 -1)]
     (-> graph
         (glimmering/map-edges (fn [e]
                              (double (.attr e))))
         (glimmering/map-vertices (fn [id value]
                                 0.0))))))

#_(if (and (.srcAttr triplet)
           (.attr triplet)
           (< (+ (.srcAttr triplet) (.attr triplet))
              (.dstAttr triplet)))
    ($ Iterator & #sparkling/tuple[(.dstId triplet) (+ (.srcAttr triplet) (.attr triplet))])
    ($ Iterator/empty))

(defn vertex-program [id weight message]
  (println "VERTEX PROG" id weight message)
  message)

(defn send-message [triplet]
  (println "SEND MESSAGE")
  (println triplet)
  (println (class (.srcId triplet)))
  ($ Iterator & ($/tuple (.dstId triplet) 1.0)))

(defn merge-message [a b]
  (println "MERGE MESSAGE" a)
  (+ a b))

(defn test-mr-triplets []
  (spark/with-context sc (-> (conf/spark-conf)
                             (conf/master "local[*]")
                             (conf/app-name "sparkling-test"))
    (let [graph (.logNormalGraph GraphGenerators$/MODULE$ (.sc sc) 5 0 4.0 1.3 -1)]
      (-> graph
          (glimmering/map-edges (fn [e]
                               (double (.attr e))))
          (glimmering/map-vertices (fn [id value]
                                  (if (= id 42)
                                    0 100)))
          (glimmering/map-reduce-triplets send-message merge-message)
          (.first)))))

#_(defn test-pregel []
  (spark/with-context sc (-> (conf/spark-conf)
                             (conf/master "local[*]")
                             (conf/app-name "sparkling-test"))
    (let [graph (.logNormalGraph GraphGenerators$/MODULE$ (.sc sc) 5 0 4.0 1.3 -1)]
      (-> graph
          (glimmering/map-edges (fn [e]
                               (double (.attr e))))
          (glimmering/map-vertices (fn [id value]
                                  (if (= id 42)
                                    0 100)))
          (glimmering/pregel 0.0 1 vertex-program send-message merge-message)
          (.vertices)
          (spark/save-as-text-file "/tmp/vertices")))))

#_(defn semi-clustering [graph]
  (let [vfn (fn [vid attr message]
              (println "Vertex" vid attr message)
              (if (empty? message)
                attr
                (ffirst (sort-by second > message))))
        sfn (fn [triplet]
              (println "Triplet" triplet)
              (let [src-id   (.srcId triplet)
                    src-attr (.srcAttr triplet)
;;                    src-attr (or src-attr src-id)
                    dst-id   (.dstId triplet)
                    dst-attr (.dstAttr triplet)
;;                    dst-attr (or dst-attr dst-id)
                    ]
                ($ Iterator &
                   ($/tuple src-id {src-attr 1})
                   ($/tuple dst-id {dst-attr 1}))))
        mfn (fn [a b]
              (println "Merging" a b)
              (merge-with + a b))
        init {}
        max 1]
    (-> graph
        (glimmering/map-reduce-triplets sfn mfn)
        #_(glimmering/map-vertices (fn [vid attr] vid))
        #_(glimmering/pregel init max vfn sfn mfn))))

#_(defn test-connected-components [out]
  (spark/with-context sc (-> (conf/spark-conf)
                             (conf/master "local[*]")
                             (conf/app-name "sparkling-test"))
    (let [vfn (fn [vid attr message]
                (println "##### Vertex" vid attr message)
                (if (empty? message)
                  attr
                  (ffirst (sort-by second > message))))
          sfn (fn [ctx]
                (println "##### Send Message")
                (let [src-id   (.srcId ctx)
                      src-attr (.srcAttr ctx)
                      dst-id   (.dstId ctx)
                      dst-attr (.dstAttr ctx)                      ]
                  (println src-id ":" src-attr " " dst-id ":" dst-attr)
                  (.sendToSrc ctx {dst-attr 1})
                  (.sendToDst ctx {src-attr 1})))
          mfn (fn [a b]
                (println "##### Merging" a b)
                (merge-with + a b))
          init {}
          n 5
          max 10
          clique1 (for [u (range n)
                        v (range n)]
                    (glimmering/edge u v 1))
          clique2 (for [u (range n)
                        v (range n)]
                    (glimmering/edge (+ u n) (+ v n) 1))
          edges (spark/parallelize sc (concat clique1 clique2 [(glimmering/edge 0 n 1)]))]
      (-> (glimmering/graph-from-edges edges 1)
          (glimmering/map-vertices (fn [vid attr] vid)) 
          (glimmering/pregel-impl init max vfn sfn mfn)
          (.vertices)
          (spark/save-as-text-file out)))))
