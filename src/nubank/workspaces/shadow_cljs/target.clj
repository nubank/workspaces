(ns nubank.workspaces.shadow-cljs.target
  (:refer-clojure :exclude [compile flush resolve])
  (:require
    [shadow.build :as build]
    [shadow.build.modules :as modules]
    [shadow.build.targets.browser :as browser]
    [shadow.build.test-util :as tu]))

;; Mostly taken from shadow-cljs :browser-test target type.
(defn modify-config [{::build/keys [config] :as state}]
  (-> state
    (assoc-in [::build/config :modules :main] {:entries '[nubank.workspaces.shadow-cljs.mount]})
    (assoc-in [::build/config :compiler-options :source-map] true) ;; always
    (assoc-in [::build/config :compiler-options :static-fns] false) ;; for mocking
    (update :build-options merge {:greedy          true
                                  :dynamic-resolve true})
    (update-in [::build/config :devtools] merge
      {:before-load 'nubank.workspaces.core/before-load
       :after-load  'nubank.workspaces.core/after-load})))

(defn resolve-cards-and-tests
  [{::build/keys [mode config] :as state}]
  (let [{:keys [ns-regexp] :or {ns-regexp "-(ws|test)$"}}
        config

        dynamically-resolved-namespaces
        (tu/find-namespaces-by-regexp state ns-regexp)]

    (-> state
      ;; Add the mounter and all of the resolved cards/tests
      (assoc-in [::modules/config :main :entries] (-> []
                                                      (into (get config :preloads []))
                                                      (into dynamically-resolved-namespaces)
                                                      (conj 'nubank.workspaces.shadow-cljs.mount)))
      (cond->
        (and (= :dev mode) (:worker-info state))
        (update-in [::modules/config :main] browser/inject-repl-client state config)

        (= :dev mode)
        (-> (update-in [::modules/config :main] browser/inject-preloads state config)
          (update-in [::modules/config :main] browser/inject-devtools-console state config)))
      (modules/analyze))))

(defn process
  [{::build/keys [stage] :as state}]
  (-> state
    (cond->
      (= :configure stage)
      (modify-config)

      (= :resolve stage)
      (resolve-cards-and-tests))

    (browser/process)))
