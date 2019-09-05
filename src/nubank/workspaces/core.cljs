(ns nubank.workspaces.core
  (:require-macros nubank.workspaces.core)
  (:require [com.fulcrologic.fulcro.application :as app]
            [com.fulcrologic.fulcro.components :as fp]
            [nubank.workspaces.card-types.test :as ct.test]
            [nubank.workspaces.ui :as ui]
            [nubank.workspaces.data :as data]
            [nubank.workspaces.model :as wsm]
            [nubank.workspaces.lib.local-storage :as local-storage]))

(defn init-card [card-id card]
  (let [card (assoc card ::wsm/card-id card-id)]
    (if (and (:mounted? data/app*) (not (contains? @data/card-definitions* card-id)))
      (fp/transact! data/app* [`(ui/load-card ~card)]))
    (swap! data/card-definitions* assoc card-id card)))

(defn init-workspace [workspace-id {::wsm/keys [workspace-layouts] :as workspace}]
  (let [workspace (assoc workspace
                    ::wsm/workspace-static? true
                    ::ui/workspace-title (pr-str workspace-id)
                    ::ui/workspace-id workspace-id
                    ::ui/layouts (local-storage/read-transit workspace-layouts))]

    (if (app/mounted? data/app*)
      (if (not (contains? @data/workspace-definitions* workspace-id))
        (fp/transact! data/app* [`(ui/load-workspace ~workspace)])
        (fp/transact! data/app* [`(ui/update-workspace ~workspace)])))

    (swap! data/workspace-definitions* assoc workspace-id
      workspace)))

(defn init-test [sym forms card-form]
  (init-card sym (assoc (ct.test/test-card sym forms) ::wsm/card-form card-form))

  ; start ns test card
  (let [test-ns (symbol (namespace sym))]
    (if-not (contains? @data/card-definitions* test-ns)
      (init-card test-ns (ct.test/test-ns-card test-ns))))

  ; start all tests card
  (if-not (contains? @data/card-definitions* `ct.test/test-all)
    (init-card `ct.test/test-all (ct.test/all-tests-card))))

(fp/defsc Root [this {:keys [ui/root]}]
  {:initial-state (fn [_] {:ui/root (fp/get-initial-state ui/WorkspacesRoot @data/card-definitions*)})
   :query         [{:ui/root (fp/get-query ui/WorkspacesRoot)}]}
  (ui/workspaces-root root))

(defn mount
  "Mount the workspaces enviroment, by default it will try to mount at #app node.
  Use the selector string to pass a querySelector string to pick the mount node."
  ([] (mount "#app"))
  ([selector]
   (app/mount! data/app* Root (js/document.querySelector selector)) ))

(defn before-load
  {:dev/before-load true}
  []
  (reset! data/card-definitions-snap* @data/card-definitions*))

(defn after-load
  {:dev/after-load true}
  []
  (ui/refresh-active-workspace-cards data/app*)
  (reset! data/card-definitions-snap* @data/card-definitions*))
