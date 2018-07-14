(ns nubank.workspaces.core
  (:require-macros nubank.workspaces.core)
  (:require [fulcro.client :as fulcro]
            [fulcro.client.primitives :as fp]
            [nubank.workspaces.card-types.test :refer [test-card test-ns-card]]
            [nubank.workspaces.ui :as ui]
            [nubank.workspaces.data :as data]
            [nubank.workspaces.model :as wsm]
            [nubank.workspaces.lib.local-storage :as local-storage]))

(defn init-card [card-id card]
  (let [card (assoc card ::wsm/card-id card-id)]
    (if (and (:mounted? @data/app*) (not (contains? @data/card-definitions* card-id)))
      (fp/transact! (:reconciler @data/app*) [`(ui/load-card ~card)]))
    (swap! data/card-definitions* assoc card-id card)))

(defn init-workspace [workspace-id {::wsm/keys [workspace-layouts] :as workspace}]
  (let [workspace (assoc workspace
                    ::wsm/workspace-static? true
                    ::ui/workspace-title (pr-str workspace-id)
                    ::ui/workspace-id workspace-id
                    ::ui/layouts (local-storage/read-transit workspace-layouts))]

    (if (:mounted? @data/app*)
      (if (not (contains? @data/workspace-definitions* workspace-id))
        (fp/transact! (:reconciler @data/app*) [`(ui/load-workspace ~workspace)])
        (fp/transact! (:reconciler @data/app*) [`(ui/update-workspace ~workspace)])))

    (swap! data/workspace-definitions* assoc workspace-id
      workspace)))

(defn init-test [sym forms card-form]
  (init-card sym (assoc (test-card sym forms) ::wsm/card-form card-form))
  (let [test-ns (symbol (namespace sym))]
    (if-not (contains? @data/card-definitions* test-ns)
      (init-card test-ns (test-ns-card test-ns)))))

(fp/defsc Root [this {:keys [ui/root]}]
  {:initial-state (fn [_] {:ui/root (fp/get-initial-state ui/WorkspacesRoot @data/card-definitions*)})
   :query         [{:ui/root (fp/get-query ui/WorkspacesRoot)}]
   :css           []
   :css-include   [ui/WorkspacesRoot]}
  (ui/workspaces-root root))

(defn mount []
  (swap! data/app* fulcro/mount Root (js/document.querySelector "#app")))

(defn before-load []
  (reset! data/card-definitions-snap* @data/card-definitions*))

(defn after-load []
  (ui/refresh-active-workspace-cards (:reconciler @data/app*))
  (reset! data/card-definitions-snap* @data/card-definitions*))
