(ns nubank.cljs.workspaces.card-types.react
  (:require-macros [nubank.cljs.workspaces.card-types.react])
  (:require [nubank.cljs.workspaces.model :as wsm]
            [nubank.cljs.workspaces.card-types.util :as ct.util]
            [cljsjs.react.dom]
            [nubank.cljs.workspaces.data :as data]
            [nubank.cljs.workspaces.ui :as ui]))

(defn render-at [c node]
  (let [comp (if (fn? c) (c) c)]
    (js/ReactDOM.render comp node)))

(defn react-card-init [{::wsm/keys [card-id]
                        :as        card} state-atom component]
  (ct.util/positioned-card card
    {::wsm/dispose
     (fn [node]
       (if state-atom
         (remove-watch state-atom ::card-watch))

       (js/ReactDOM.unmountComponentAtNode node))

     ::wsm/refresh
     (fn [node]
       (render-at component node))

     ::wsm/render
     (fn [node]
       (when state-atom
         (swap! data/active-cards* assoc-in [card-id ::state*] state-atom)
         (add-watch state-atom ::card-watch
           (fn [_ _ _ _]
             (render-at component node)
             (ui/refresh-card-container card-id))))

       (render-at component node))}))

(defn react-card* [state-atom component]
  {::wsm/init #(react-card-init % state-atom component)})
