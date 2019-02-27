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

(defn make-root [Root]
  (let [factory (fp/factory Root)]
    (fp/ui
      static fp/InitialAppState
      (initial-state [_ params]
        {:ui/root (or (fp/get-initial-state Root params) {})})

      static fp/IQuery
      (query [_] [:fulcro.inspect.core/app-id {:ui/root (fp/get-query Root)}])

      Object
      (render [this]
        (let [{:ui/keys [root]} (fp/props this)]
          (if (seq root)
            (factory root)))))))

(defn fulcro-initial-state [{::keys [initial-state wrap-root? root]
                             :or    {wrap-root? true}}]
  (let [state (if (fn? initial-state)
                (initial-state (fp/get-initial-state root nil))
                (fp/get-initial-state root initial-state))]
    (if wrap-root?
      {:ui/root state}
      state)))

(defn upsert-app [{::keys                    [app persistence-key]
                   :fulcro.inspect.core/keys [app-id]
                   :as                       config}]
  (if-let [instance (and persistence-key (get @persistent-apps* persistence-key))]
    instance
    (let [app      (cond-> app
                     (not (contains? app :initial-state))
                     (assoc :initial-state (fulcro-initial-state config))

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
  [this _]
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

  (dom/div))

(def fulcro-portal* (fp/factory FulcroPortal))

(defn fulcro-portal [component options]
  (fulcro-portal* (assoc options ::root component)))
