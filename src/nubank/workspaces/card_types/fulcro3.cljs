(ns nubank.workspaces.card-types.fulcro3
  (:require
    [cljs.spec.alpha :as s]
    [com.fulcrologic.fulcro-css.css-injection :as cssi]
    [com.fulcrologic.fulcro.application :as fapp]
    [com.fulcrologic.fulcro.algorithms.normalize :refer [tree->db]]
    [com.fulcrologic.fulcro.components :as fc]
    [com.fulcrologic.fulcro.dom :as dom]
    [com.fulcrologic.fulcro.inspect.inspect-client :as fi.client]
    [goog.functions :as gfun]
    [goog.object :as gobj]
    [nubank.workspaces.card-types.util :as ct.util]
    [nubank.workspaces.data :as data]
    [nubank.workspaces.model :as wsm]
    [nubank.workspaces.ui :as ui]
    [nubank.workspaces.ui.core :as uc]
    [ghostwheel.core :refer [>defn >fdef => | <- ?]]))

; region portal

(s/def ::root any?)
(s/def ::wrap-root? boolean?)
(s/def ::app map?)
(s/def ::persistence-key any?)
(s/def ::initial-state (s/or :fn? fn? :factory-param any?))
(s/def ::root-state map?)
(s/def ::computed map?)
(s/def ::root-node-props map?)

(defonce css-components* (atom #{}))
(defonce persistent-apps* (atom {}))

(defn gen-css-component []
  (let [generated-name (gensym)
        component-key  (keyword "nubank.workspaces.card-types.fulcro3" (name generated-name))]
    (fc/configure-component! (fn *dyn-root* [])
      component-key
      {:query (fn [_] (into
                        []
                        (keep-indexed (fn [i v] {(keyword (str "item" i))
                                                 (or (fc/get-query v) (with-meta [] {:component v}))}))
                        @css-components*))})))

(defn safe-initial-state [comp params]
  (if (fc/has-initial-app-state? comp)
    (fc/get-initial-state comp params)
    params))

(defn make-root [Root]
  (let [factory        (fc/factory Root)
        generated-name (gensym)
        component-key  (keyword "nubank.workspaces.card-types.fulcro3" (name generated-name))]
    (fc/configure-component! (fn *dyn-root* [])
      component-key
      {:initial-state (fn [_ params]
                        {:ui/root (or (safe-initial-state Root params) {})})
       :query         (fn [_] [:fulcro.inspect.core/app-id {:ui/root (fc/get-query Root)}])
       :render        (fn [this]
                        (let [{:ui/keys [root]} (fc/props this)
                              computed (fc/shared this ::computed)]
                          (if (seq root)
                            (factory (cond-> root computed (fc/computed computed))))))})))

(defn fulcro-initial-state [{::keys [initial-state wrap-root? root root-state]
                             :or    {wrap-root? true initial-state {}}}]
  (let [state (if (fn? initial-state)
                (initial-state (safe-initial-state root nil))
                (safe-initial-state root initial-state))]
    (tree->db
      root
      (merge
        (if wrap-root?
          {:ui/root state}
          state)
        root-state)
      true)))

(defn upsert-app [{::keys                    [app persistence-key computed]
                   :fulcro.inspect.core/keys [app-id]
                   :as                       config}]
  (if-let [instance (and persistence-key (get @persistent-apps* persistence-key))]
    instance
    (let [app      (cond-> app
                     (not (contains? app :initial-state))
                     (assoc :initial-db (fulcro-initial-state config))

                     computed
                     (update :shared assoc ::computed computed)

                     app-id
                     (assoc-in [:initial-db :fulcro.inspect.core/app-id] app-id))
          ;; TASK: explicit initial state handling
          instance (fapp/fulcro-app app)]
      (if persistence-key (swap! persistent-apps* assoc persistence-key instance))
      instance)))

(defn dispose-app [{::keys [persistence-key] :as app}]
  (if persistence-key (swap! persistent-apps* dissoc persistence-key))
  (when-let [app-uuid (fi.client/app-uuid app)]
    (fi.client/dispose-app app-uuid)))

(defn refresh-css! []
  (cssi/upsert-css "fulcro-portal-css" {:component (gen-css-component)}))

(defn add-component-css! [comp]
  (swap! css-components* conj comp)
  (refresh-css!))

(defn mount-at [app {::keys [root wrap-root? persistence-key] :or {wrap-root? true}} node]
  (add-component-css! root)
  (let [instance (if wrap-root? (make-root root) root)
        new-app  (fapp/mount! app instance node)]
    (if persistence-key
      (swap! persistent-apps* assoc persistence-key new-app))
    new-app))

(fc/defsc FulcroPortal
  [this {::keys [root-node-props]}]
  {:componentDidMount
   (fn [this]
     (let [props (fc/props this)
           app   (upsert-app props)]
       (gobj/set this "app" app)
       (mount-at app props (dom/node this))))

   :componentDidUpdate
   (fn [this _ _] (some-> (gobj/get this "app") fapp/force-root-render!))

   :componentWillUnmount
   (fn [this]
     (let [app (gobj/get this "app")]
       (dispose-app app)
       (reset! app nil)
       (js/ReactDOM.unmountComponentAtNode (dom/node this))))

   :shouldComponentUpdate
   (fn [this _ _] false)}

  (dom/div root-node-props))

(def fulcro-portal* (fc/factory FulcroPortal))

(defn fulcro-portal
  "Create a new portal for a Fulcro app, available options:

  ::root - the root component to be mounted
  ::app This is the app configuration, same options you could send to `fulcro/new-fulcro-client`
  ::wrap-root? - by default the portal expects a component with ident to be mounted and
  the portal will wrap that with an actual root (with no ident), if you wanna provide
  your own root, set this to `false`
  ::initial-state - Accepts a value or a function. A value will be used to call the
  initial state function of your root. If you provide a function, the value returned by
  it will be the initial state.
  ::root-state - This map will be merged into the app root state to be part of the initial
  state in the root, this is useful to set things like `:ui/locale` considering
  ::computed - send computed props to the root
  ::root-node-props - use this to send props into the root note created to mount the
  portal on."
  [component options]
  (fulcro-portal* (assoc options ::root component)))

; endregion

; region card definition

(defn inspector-set-app [card-id]
  (let [{::keys [app]} (data/active-card card-id)
        app-uuid (fi.client/app-uuid app)]
    (if app-uuid
      (fi.client/set-active-app app-uuid))))

(def debounced-refresh-css!
  (gfun/debounce refresh-css! 100))

(defn fulcro-card-init
  [{::wsm/keys [card-id]
    :as        card}
   config]
  (let [app (upsert-app (assoc config :fulcro.inspect.core/app-id card-id))]
    (ct.util/positioned-card card
      {::wsm/dispose
       (fn [node]
         (dispose-app app)
         (js/ReactDOM.unmountComponentAtNode node))

       ::wsm/refresh
       (fn [_]
         (debounced-refresh-css!)
         (fapp/force-root-render! app))

       ::wsm/render
       (fn [node]
         (swap! data/active-cards* assoc-in [card-id ::app] app)
         (mount-at app config node))

       ::wsm/render-toolbar
       (fn []
         (dom/div
           (uc/button {:onClick #(inspector-set-app card-id)}
             "Inspector")
           (uc/button {:onClick #(ui/restart-card card-id)}
             "Restart")))

       ::app
       app})))

(defn fulcro-card [config]
  {::wsm/init
   #(fulcro-card-init % config)})

(s/fdef fulcro-card
  :args (s/cat :config (s/keys
                         :req [::root]
                         :opt [::wrap-root?
                               ::app
                               ::initial-state]))
  :ret ::wsm/card-instance)

; endregion
