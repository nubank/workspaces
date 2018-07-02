(ns nubank.workspaces.ui.modal
  (:require [goog.dom :as gdom]
            [goog.object :as gobj]
            [goog.style :as style]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]
            [nubank.workspaces.ui.events :as events]))

(defn render-subtree-into-container [parent c node]
  (js/ReactDOM.unstable_renderSubtreeIntoContainer parent c node))

(defn $ [s] (.querySelector js/document s))

(defn create-portal-node [props]
  (let [node (doto (gdom/createElement "div")
               (style/setStyle (clj->js (:style props))))]
    (cond
      (:append-to props) (gdom/append ($ (:append-to props)) node)
      (:insert-after props) (gdom/insertSiblingAfter node ($ (:insert-after props))))
    node))

(defn portal-render-children [children]
  (apply dom/div nil children))

(fp/defsc Portal [this _]
  {:componentDidMount
   (fn []
     (let [props (fp/props this)
           node  (create-portal-node props)]
       (gobj/set this "node" node)
       (render-subtree-into-container this (portal-render-children (fp/children this)) node)))

   :componentWillUnmount
   (fn []
     (when-let [node (gobj/get this "node")]
       (js/ReactDOM.unmountComponentAtNode node)
       (gdom/removeNode node)))

   :componentWillReceiveProps
   (fn [_]
     (let [node (gobj/get this "node")]
       (render-subtree-into-container this (portal-render-children (fp/children this)) node)))

   :componentDidUpdate
   (fn [_ _]
     (let [node (gobj/get this "node")]
       (render-subtree-into-container this (portal-render-children (fp/children this)) node)))}

  (dom/noscript))

(def portal (fp/factory Portal))

(fp/defsc WidgetContent [this props]
  {:css [[:.container {:max-height "70vh"
                       :overflow   "auto"}]]}
  (dom/div :.container props
    (fp/children this)))

(def widget-content (fp/factory WidgetContent))

(fp/defsc Modal [this {::keys [on-close]
                       :or    {on-close identity}}]
  {:css         [[:.background {:position        "fixed"
                                :left            0
                                :top             0
                                :background      "rgba(0, 0, 0, 0.5)"
                                :width           "100vw"
                                :height          "100vh"
                                :display         "flex"
                                :align-items     "center"
                                :justify-content "center"
                                :z-index         "100"
                                :overflow-y      "scroll"}]
                 [:.container {:display        "flex"
                               :flex-direction "column"
                               :max-width      "90vw"
                               :max-height     "80vh"}]
                 [:.close {:align-self     "flex-end"
                           :color          "white"
                           :cursor         "pointer"
                           :font-size      "10px"
                           :text-transform "uppercase"}]]
   :css-include [WidgetContent]}
  (portal {:append-to "body"}
    (events/dom-listener {::events/keystroke "escape"
                          ::events/action    on-close})
    (dom/div :.background {:onClick (fn [e]
                                      (if (= (.-currentTarget e) (.-target e))
                                        (on-close e)))}
      (dom/div :.container
        (dom/div
          (fp/children this))))))

(def modal (fp/factory Modal))
