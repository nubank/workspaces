(ns nubank.workspaces.lib.uri
  (:import [goog Uri]))

(defn location-href []
  js/location.href)

(defn uri-query-params*
  "Extract query params from URL, returns map with data, keys are strings."
  [uri]
  (let [query-data (-> (Uri/parse uri)
                       (.getQueryData))]
    (into {}
      (map (juxt identity #(.get query-data %)))
      (.getKeys query-data))))

(defn uri-query-params
  "Read query params from current URL"
  []
  (uri-query-params* (location-href)))
