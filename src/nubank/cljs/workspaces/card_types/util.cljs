(ns nubank.cljs.workspaces.card-types.util
  (:require [goog.object :as gobj]
            [goog.dom :as gdom]
            [goog.style :as gstyle]
            [nubank.cljs.workspaces.model :as wsm]
            [nubank.cljs.workspaces.data :as data]))

(def predef-alignments
  {::wsm/align-top         {}
   ::wsm/align-top-flex    {:display "flex"
                            :flex    "1"}
   ::wsm/align-center      {:display         "flex"
                            :align-items     "center"
                            :justify-content "center"}
   ::wsm/align-center-flex {:display         "flex"
                            :flex            "1"
                            :align-items     "center"
                            :justify-content "center"}})

(defn position-style [{::wsm/keys [align] :or {align ::wsm/align-center}}]
  (let [custom (get predef-alignments align (if (map? align) align {}))]
    (merge {:align-self      "stretch"
            :justify-content "stretch"
            :max-width       "100%"}
           custom)))

(defn create-wrapper-node [card]
  (doto (js/document.createElement "div")
    (gobj/set "WORKSPACES_WRAPPER_NODE" true)
    (gstyle/setStyle (clj->js (position-style card)))))

(defn find-root-node [node]
  (loop [node node]
    (if (gobj/get node "WORKSPACES_WRAPPER_NODE")
      (recur (gdom/getParentElement node))
      node)))

(defn positioned-card [card {::wsm/keys [dispose refresh render] :as instance}]
  (let [real-node (create-wrapper-node card)]
    (assoc instance
      ::wsm/dispose
      (fn [_] (dispose real-node))

      ::wsm/refresh
      (fn [_] (refresh real-node))

      ::wsm/render
      (fn [node]
        (let [node (find-root-node node)]
          (gobj/set node "innerHTML" "")
          (.appendChild node real-node)
          (swap! data/active-cards* assoc-in [(::wsm/card-id card) ::wsm/node] real-node)
          (render real-node))))))
