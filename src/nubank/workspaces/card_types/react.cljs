(ns nubank.workspaces.card-types.react
  (:require-macros [nubank.workspaces.card-types.react])
  (:require [nubank.workspaces.model :as wsm]
            [nubank.workspaces.card-types.util :as ct.util]
            [cljsjs.react.dom]
            [goog.object :as gobj]
            [nubank.workspaces.data :as data]
            [nubank.workspaces.ui :as ui]))

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
       (if-let [comp (render-at component node)]
         (if (gobj/get comp "forceUpdate")
           (.forceUpdate comp))))

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
