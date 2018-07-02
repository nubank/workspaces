(ns nubank.workspaces.lib.local-storage
  (:refer-clojure :exclude [get set!])
  (:require [cljs.reader :refer [read-string]]
            [cognitect.transit :as t]))

(def local-storage (.-localStorage js/window))

;; edn

(defn get
  ([key] (get key nil))
  ([key default]
   (if-let [value (.getItem local-storage (pr-str key))]
     (read-string value)
     default)))

(defn set! [key value]
  (.setItem local-storage (pr-str key) (pr-str value)))

(defn update! [key f & args]
  (.setItem local-storage (pr-str key) (pr-str (apply f (get key) args))))

(defn remove! [key]
  (.removeItem local-storage key))

;; transit

(defn read-transit [s]
  (let [reader (t/reader :json)]
    (t/read reader s)))

(defn write-transit [x]
  (let [writer (t/writer :json)]
    (t/write writer x)))

(defn tget
  ([key] (tget key nil))
  ([key default]
   (if-let [value (.getItem local-storage (pr-str key))]
     (read-transit value)
     default)))

(defn tset! [key value]
  (.setItem local-storage (pr-str key) (write-transit value)))

(defn tupdate! [key f & args]
  (.setItem local-storage (pr-str key) (write-transit (apply f (tget key) args))))

(comment
  (-> `(:hello {})
      type)

  (-> `(:hello {})
      (write-transit)
      (read-transit)
      type)

  (-> `(:hello {})
    (pr-str)
    (read-string)
    type))
