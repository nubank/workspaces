(ns nubank.workspaces.card-types.fulcro
  (:require [com.fulcrologic.fulcro.dom :as dom]
            [com.fulcrologic.fulcro.components :as fp]
            [com.fulcrologic.fulcro.application :as fapp]
            [goog.functions :as gfun]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.card-types.util :as ct.util]
            [nubank.workspaces.data :as data]
            [nubank.workspaces.model :as wsm]
            [nubank.workspaces.ui :as ui]
            [cljs.spec.alpha :as s]
            [nubank.workspaces.ui.core :as uc]
            [com.fulcrologic.fulcro.application :as app]))

(defn inspector-set-app [card-id]
  (let [{::keys [app*]} (data/active-card card-id)
        app-uuid (::app/id @app*)]
    (if app-uuid
      ;; TASK: Port Fulcro Inspect
      #_(fi.client/set-active-app app-uuid))))

(def debounced-refresh-css!
  (gfun/debounce f.portal/refresh-css! 100))

(defn fulcro-card-init
  [{::wsm/keys [card-id]
    :as        card}

   config]
  (let [app* (atom (f.portal/upsert-app (assoc config :fulcro.inspect.core/app-id card-id)))]
    (ct.util/positioned-card card
      {::wsm/dispose
       (fn [node]
         (f.portal/dispose-app @app*)
         (reset! app* nil)
         (js/ReactDOM.unmountComponentAtNode node))

       ::wsm/refresh
       (fn [_]
         (debounced-refresh-css!)
         (app/force-root-render! @app*))

       ::wsm/render
       (fn [node]
         (swap! data/active-cards* assoc-in [card-id ::app*] app*)
         (f.portal/mount-at app* config node))

       ::wsm/render-toolbar
       (fn []
         (dom/div
           (uc/button {:onClick #(inspector-set-app card-id)}
             "Inspector")
           (uc/button {:onClick #(ui/restart-card card-id)}
             "Restart")))

       ::app*
       app*})))

(defn fulcro-card [config]
  {::wsm/init
   #(fulcro-card-init % config)})

(s/fdef fulcro-card
  :args (s/cat :config (s/keys
                         :req [::f.portal/root]
                         :opt [::f.portal/wrap-root?
                               ::f.portal/app
                               ::f.portal/initial-state]))
  :ret ::wsm/card-instance)
