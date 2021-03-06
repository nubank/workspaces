(ns nubank.workspaces.lib.fulcro-portal
  (:require [cljs.spec.alpha :as s]
            [fulcro-css.css-injection :as cssi]
            [fulcro.client :as fulcro]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]
            [fulcro.inspect.client :as fi.client]
            [goog.object :as gobj]))

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
  (fp/ui
    static fp/IQuery
    (query [_]
      (into
        []
        (keep-indexed (fn [i v] {(keyword (str "item" i)) (or (fp/get-query v) (with-meta [] {:component v}))}))
        @css-components*))))

(defn safe-initial-state [comp params]
  (if (fp/has-initial-app-state? comp)
    (fp/get-initial-state comp params)
    params))

(defn make-root [Root]
  (let [factory (fp/factory Root)]
    (fp/ui
      static fp/InitialAppState
      (initial-state [_ params]
        {:ui/root (or (safe-initial-state Root params) {})})

      static fp/IQuery
      (query [_] [:fulcro.inspect.core/app-id {:ui/root (fp/get-query Root)}])

      Object
      (render [this]
        (let [{:ui/keys [root]} (fp/props this)
              computed (fp/shared this ::computed)]
          (if (seq root)
            (factory (cond-> root computed (fp/computed computed)))))))))

(defn fulcro-initial-state [{::keys [initial-state wrap-root? root root-state]
                             :or    {wrap-root? true initial-state {}}}]
  (let [state (if (fn? initial-state)
                (initial-state (safe-initial-state root nil))
                (safe-initial-state root initial-state))]
    (merge
      (if wrap-root?
        {:ui/root state}
        state)
      root-state)))

(defn upsert-app [{::keys                    [app persistence-key computed]
                   :fulcro.inspect.core/keys [app-id]
                   :as                       config}]
  (if-let [instance (and persistence-key (get @persistent-apps* persistence-key))]
    instance
    (let [app      (cond-> app
                     (not (contains? app :initial-state))
                     (assoc :initial-state (fulcro-initial-state config))

                     computed
                     (update :shared assoc ::computed computed)

                     app-id
                     (assoc-in [:initial-state :fulcro.inspect.core/app-id] app-id))
          instance (apply fulcro/new-fulcro-client (apply concat app))]
      (if persistence-key (swap! persistent-apps* assoc persistence-key instance))
      instance)))

(defn dispose-app [{::keys [persistence-key] :as app}]
  (if persistence-key (swap! persistent-apps* dissoc persistence-key))
  (when-let [app-uuid (some-> app :reconciler fp/app-state deref (get fi.client/app-uuid-key))]
    (fi.client/dispose-app app-uuid)))

(defn refresh-css! []
  (cssi/upsert-css "fulcro-portal-css" {:component (gen-css-component)}))

(defn add-component-css! [comp]
  (swap! css-components* conj comp)
  (refresh-css!))

(defn mount-at [app* {::keys [root wrap-root? persistence-key] :or {wrap-root? true}} node]
  (add-component-css! root)
  (let [instance (if wrap-root? (make-root root) root)
        new-app (swap! app* fulcro/mount instance node)]
    (if persistence-key
      (swap! persistent-apps* assoc persistence-key new-app))
    new-app))

(fp/defsc FulcroPortal
  [this {::keys [root-node-props]}]
  {:componentDidMount
   (fn []
     (let [props (fp/props this)
           app*  (atom (upsert-app props))]
       (gobj/set this "app" app*)
       (mount-at app* props (dom/node this))))

   :componentDidUpdate
   (fn [_ _] (some-> (gobj/get this "app") deref :reconciler fp/force-root-render!))

   :componentWillUnmount
   (fn []
     (let [app* (gobj/get this "app")]
       (dispose-app @app*)
       (reset! app* nil)
       (js/ReactDOM.unmountComponentAtNode (dom/node this))))

   :shouldComponentUpdate
   (fn [_ _] false)}

  (dom/div root-node-props))

(def fulcro-portal* (fp/factory FulcroPortal))

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
