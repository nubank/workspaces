(ns nubank.cljs.workspaces.card-types.test
  (:require
    [cljs.core.async :as async :refer [go chan go-loop put! close! <!]]
    [cljs.test]
    [cljs.reader :refer [read-string]]
    [cljsjs.react.dom]
    [fulcro.client.localized-dom :as dom]
    [nubank.cljs.workspaces.card-types.react :as ct.react]
    [nubank.cljs.workspaces.lib.fulcro-portal :as f.portal]
    [nubank.cljs.workspaces.data :as data]
    [nubank.cljs.workspaces.model :as wsm]
    [nubank.cljs.workspaces.ui.core :as uc]
    [fulcro.client.primitives :as fp]
    [fulcro.client.mutations :as fm]
    [fulcro-css.css :as css]
    [nubank.cljs.workspaces.lib.fulcro-portal :as f.portal]
    [nubank.cljs.workspaces.card-types.fulcro :as ct.fulcro]
    [nubank.cljs.workspaces.ui :as ui]
    [nubank.cljs.workspaces.ui.highlight :as highlight]))

(defonce test-context* (atom []))

(defn collect-test [m]
  (let [contexts (if (seq @test-context*)
                   @test-context*
                   (vec (get (cljs.test/get-current-env) :testing-contexts)))]
    (cljs.test/update-current-env! [::summary] (fnil conj [])
      (merge {:testing-contexts contexts} m))))

(defmethod cljs.test/report [::reporter :pass] [m]
  (cljs.test/inc-report-counter! :pass)
  (collect-test m)
  m)

(defmethod cljs.test/report [::reporter :fail] [m]
  (cljs.test/inc-report-counter! :fail)
  (collect-test m)
  m)

(defmethod cljs.test/report [::reporter :error] [m]
  (js/console.log "Error running test" (::test (cljs.test/get-current-env)))
  (js/console.error (:actual m))
  (cljs.test/inc-report-counter! :error)
  (collect-test m)
  m)

; fulcro reports

(defmethod cljs.test/report [::reporter :begin-behavior] [{:keys [string] :as m}]
  (swap! test-context* conj string)
  m)

(defmethod cljs.test/report [::reporter :end-behavior] [m]
  (swap! test-context* pop)
  m)

(defmethod cljs.test/report [::reporter :begin-manual] [{:keys [string] :as m}]
  (swap! test-context* conj string)
  m)

(defmethod cljs.test/report [::reporter :end-manual] [m]
  (swap! test-context* pop)
  m)

(defn now [] (.getTime (js/Date.)))

(defn create-test-env [test]
  (assoc (cljs.test/empty-env)
    :reporter ::reporter
    ::test test))

(def single-test-timeout 500)

(defn run-test-blocks* [{::keys [test blocks]}]
  (let [out      (async/promise-chan)
        test-env (create-test-env test)]
    (cljs.test/set-env! test-env)
    (let [tests (conj blocks #(put! out (cljs.test/get-current-env)))]
      (try
        (cljs.test/run-block tests)
        (catch :default e
          (let [m {:type :error :actual e}]
            (cljs.test/report m)
            (put! out (cljs.test/get-current-env))))))
    out))

(defn run-test-blocks [input]
  (go
    (let [start    (now)
          timer    (async/timeout single-test-timeout)
          [result ch] (async/alts! [(run-test-blocks* input) timer])
          duration (- (now) start)]
      (if (not= ch timer)
        (assoc result ::duration duration)
        (do
          (cljs.test/report {:type :error :actual "Tests timed out. Please check Dev Console for Exceptions"})
          (assoc (cljs.test/get-current-env)
            ::duration duration
            :error "Execution timed out!"))))))

(defn test-cards-by-namespace []
  (->> (vals @data/card-definitions*)
       (filterv ::wsm/test?)
       (group-by (comp (fnil symbol '_) namespace ::wsm/card-id))))

(defn namespace-test-cards [ns] (get (test-cards-by-namespace) ns))

(defonce test-channel (chan (async/dropping-buffer 512)))

(defmulti test-runner ::type)

(defmethod test-runner ::test-one [{::keys [app* test] :as input}]
  (go
    (fp/transact! (:reconciler @app*) [::test-result-id "singleton"]
      [`(fm/set-props {::running?  true
                       ::enqueued? false})])

    (<! (async/timeout 1))
    (ui/refresh-card-container test)
    (<! (async/timeout 1))

    (let [res (<! (run-test-blocks input))]
      (fp/transact! (:reconciler @app*) [::test-result-id "singleton"]
        [`(fm/set-props {:test-results ~res
                         ::done?       true
                         ::running?    false})])

      (<! (async/timeout 1))
      (ui/refresh-card-container test)

      res)))

(declare NSTestGroup)

(defn app-test-block [reconciler ns]
  (-> reconciler fp/app-state deref (get-in [::test-ns ns])))

(fm/defmutation start-ns-test-namespaces [{::keys [ns-tests]}]
  (action [{:keys [reconciler state ref]}]
    (let [blocks (mapv #(hash-map ::test-ns (::wsm/card-id %)
                                  :test-results nil
                                  ::disabled? (::disabled? (app-test-block reconciler (::wsm/card-id %)))) ns-tests)]
      (fp/merge-component! reconciler NSTestGroup {::test-namespaces blocks})
      (swap! state update-in ref assoc ::running? true ::enqueued? false))))

(defmethod test-runner ::test-ns [{::keys [test-ns app*]}]
  (go
    (let [test-cards (sort-by ::wsm/card-id (namespace-test-cards test-ns))
          app        @app*]
      (fp/transact! (:reconciler app) [::ns-test-run "singleton"]
        [`(start-ns-test-namespaces {::ns-tests ~test-cards})])

      (<! (async/timeout 1))
      (ui/refresh-card-container test-ns)
      (<! (async/timeout 1))

      (doseq [{::wsm/keys [card-id]
               ::keys     [test-forms]} test-cards]
        (if-not (::disabled? (app-test-block (:reconciler app) card-id))
          (let [res (<! (run-test-blocks {::test   card-id
                                          ::blocks test-forms}))]
            (fp/transact! (:reconciler app) [::test-ns card-id]
              [`(fm/set-props {:test-results ~res})]))))

      (fp/transact! (:reconciler app) [::ns-test-run "singleton"]
        [`(fm/set-props {::done? true ::running? false})])

      (<! (async/timeout 1))
      (ui/refresh-card-container test-ns)
      (fp/force-root-render! (:reconciler app))
      app)))

(defn run-test-loop [ch]
  (go
    (loop []
      (when-let [{::keys [done] :as input} (<! ch)]
        (let [result (<! (test-runner input))]
          (put! done result)
          (cljs.test/clear-env!)
          (<! (async/timeout 1))
          (recur))))))

(defonce test-loop (run-test-loop test-channel))

(defn run-ns-tests! [ns app*]
  (let [out (async/promise-chan)]
    (fp/transact! (:reconciler @app*) [::ns-test-run "singleton"]
      [`(fm/set-props {::enqueued?   true
                       ::done?       false
                       :test-results {}})])

    (put! test-channel {::type    ::test-ns
                        ::test-ns ns
                        ::done    out
                        ::app*    app*})
    out))

(defn run-card-tests! [test app*]
  (let [forms (-> (data/card-definition test) ::test-forms)
        out   (async/promise-chan)]
    (fp/transact! (:reconciler @app*) [::test-result-id "singleton"]
      [`(fm/set-props {::enqueued?   true
                       ::done?       false
                       :test-results {}})])

    (put! test-channel {::type   ::test-one
                        ::test   test
                        ::blocks forms
                        ::done   out
                        ::app*   app*})

    out))

(defn test-success? [{:keys [report-counters]}]
  (= 0 (:fail report-counters) (:error report-counters)))

(defn header-color [{::keys [card]} bg]
  ((::wsm/set-card-header-style card) {:background bg})
  nil)

(fp/defsc TestCSS [_ _]
  {:css [[:.test-result
          {:padding    "3px 6px"
           :margin-top "3px"}]
         [:.test-ns
          {:flex       "1"
           :align-self "flex-start"}]
         [:.test-ns-toolbar
          {:background      "#404040"
           :display         "flex"
           :align-items     "center"
           :justify-content "flex-end"
           :padding         "5px"
           :margin          "-10px -10px 10px"}]
         [:.test-ns-container
          {:margin-bottom "5px"}]
         [:.test-ns-var-header
          {:background  uc/color-dark-grey
           :color       "#fff"
           :font-family "Helvetica"
           :font-size   "14px"
           :padding     "4px 5px"}]
         [:.code
          {:font-family "monospace"
           :white-space "pre"}]]})

(def css (css/get-classnames TestCSS))

(f.portal/add-component-css! TestCSS)

(defn try-pprint [x]
  (try
    (with-out-str (cljs.pprint/pprint x))
    (catch :default _ x)))

(defn print-code [s]
  (highlight/highlight {::highlight/source (try-pprint s)}))

(fp/defsc TestResult
  [this {:keys [actual expected type testing-contexts message]}]
  {:initial-state (fn [_]
                    {})
   :query         [:actual :expected :type :testing-contexts :message]
   :css           [[:.test-result
                    {:padding    "3px 6px"
                     :margin-top "3px"}]
                   [:.compare-header
                    {:font-family uc/font-helvetica
                     :font-size   "14px"
                     :font-weight "bold"
                     :margin      "10px 0"}]]
   :css-include   []}
  (let [color (if (= :pass type) uc/color-green-light uc/color-red-light)]
    (dom/div :.test-result {:style {:borderLeft (str "5px solid " color)}}
      (mapv #(dom/div {:key (str (hash %))} %) testing-contexts)
      (if (and message (not (seq testing-contexts)))
        (dom/div message)
        (if (and (= :pass type) (not (seq testing-contexts)))
          (dom/div (pr-str expected))))
      (if (not= :pass type)
        (let [[extra missing] (clojure.data/diff expected actual)
              error? (instance? js/Error actual)]
          (dom/div :.diff
            (dom/div :.compare-header "Expected")
            (print-code expected)
            (dom/div :.compare-header "Actual")
            (print-code actual)
            (if (not error?)
              (dom/div
                (if extra (dom/div :.compare-header "Diff extra"))
                (if extra (print-code extra))
                (if missing (dom/div :.compare-header "Diff missing"))
                (if missing (print-code missing))))))))))

(def test-result (fp/factory TestResult {:keyfn (comp hash (juxt :expected :actual :type :testing-contexts :message))}))

(fp/defsc SingleTest
  [this {::keys [enqueued? running?]
         :keys  [test-results]}]
  {:initial-state (fn [_]
                    {::test-result-id (random-uuid)})
   :ident         (fn [] [::test-result-id "singleton"])
   :query         [::enqueued? ::running? ::done?
                   {:test-results
                    [:report-counters
                     {::summary (fp/get-query TestResult)}]}]
   :css           []
   :css-include   [TestResult]}
  (let [{::keys [summary]} test-results
        header-color #(header-color (fp/shared this) %)]
    (dom/div
      (cond
        enqueued?
        (do
          (header-color uc/color-yellow)
          "Waiting to run...")

        running?
        (do
          (header-color uc/color-yellow)
          "Running...")

        (test-success? test-results)
        (do
          (header-color uc/color-green-dark)
          (mapv test-result summary))

        :else
        (do
          (header-color uc/color-red-dark)
          (mapv test-result summary))))))

(defn test-card-init [card test]
  (let [{::ct.fulcro/keys [app*]
         :as              card}
        (ct.fulcro/fulcro-card-init card
          {::f.portal/root SingleTest
           ::f.portal/app  {:shared
                            {::card card}

                            :started-callback
                            (fn [app]
                              (run-card-tests! test (atom app)))}})

        run-tests
        #(run-card-tests! test app*)]

    (assoc card
      ::wsm/refresh (fn [_] (run-tests))
      ::wsm/render-toolbar (fn []
                             (let [state
                                   (-> @app* :reconciler fp/app-state deref)

                                   {::keys [running? done?]
                                    :keys  [test-results]}
                                   (get-in state [::test-result-id "singleton"])]
                               (dom/div {:style {:flex       "1"
                                                 :display    "flex"
                                                 :alignItems "center"}}
                                 (cond
                                   running?
                                   (dom/div {:style {:fontSize "12px"}} "Running...")

                                   done?
                                   (dom/div {:style {:fontSize "12px"}}
                                     "Finished in " (::duration test-results) "ms"))
                                 (dom/div {:style {:flex "1"}})
                                 (uc/button {:onClick run-tests} "Rerun tests")))))))

(defn test-card [card-id forms]
  {::test-forms forms
   ::wsm/align  ::wsm/align-top-flex
   ::wsm/test?  true
   ::wsm/init   #(test-card-init % card-id)})

(fp/defsc NSTestBlock
  [this {:keys  [test-results]
         ::keys [test-ns disabled?]}]
  {:initial-state (fn [_]
                    {})
   :ident         [::test-ns ::test-ns]
   :query         [::test-ns ::disabled?
                   {:test-results
                    [:report-counters
                     {::summary (fp/get-query TestResult)}]}]
   :css           [[:.test-ns-container
                    {:margin-bottom "5px"}]
                   [:.test-ns-var-header
                    {:background  uc/color-dark-grey
                     :color       "#fff"
                     :font-family "Helvetica"
                     :font-size   "14px"
                     :padding     "4px 5px"
                     :display     "flex"}]
                   [:.disabled {:text-decoration "line-through"}]
                   [:.title {:flex "1"}]]
   :css-include   [TestResult]}
  (let [bg-color (cond
                   disabled?
                   uc/color-light-grey

                   (seq test-results)
                   (if (test-success? test-results)
                     uc/color-green-light
                     uc/color-red-light)

                   :else
                   uc/color-yellow)]
    (dom/div :.test-ns-container
      (dom/div :.test-ns-var-header
        {:style   {:borderLeft (str "5px solid " bg-color)}
         :classes [(if disabled? :.disabled)]}
        (dom/div :.title (name test-ns))
        (dom/div (dom/input {:type     "checkbox"
                             :checked  (not disabled?)
                             :onChange #(fm/toggle! this ::disabled?)})))
      (mapv test-result (::summary test-results)))))

(def ns-test-block (fp/factory NSTestBlock {:keyfn ::test-ns}))

(fp/defsc NSTestGroup
  [this {::keys [test-namespaces enqueued? running? done?]}]
  {:initial-state (fn [_]
                    {::enqueued?       false
                     ::running?        false
                     ::test-namespaces []})
   :ident         (fn [] [::ns-test-run "singleton"])
   :query         [::enqueued? ::running? ::done? :report-counters
                   {::test-namespaces (fp/get-query NSTestBlock)}]
   :css           [[:.test-ns
                    {:flex       "1"
                     :align-self "flex-start"}]]
   :css-include   [NSTestBlock]}
  (let [header-color #(header-color (fp/shared this) %)]
    (dom/div :.test-ns
      (cond
        done?
        (header-color (if (->> test-namespaces
                               (map :test-results)
                               (filter seq)
                               (every? test-success?))
                        uc/color-green-dark
                        uc/color-red-dark))

        running?
        (header-color uc/color-yellow)

        enqueued?
        (header-color uc/color-yellow))

      (mapv ns-test-block test-namespaces))))

(defn results-duration [test-results]
  (transduce (map ::duration) + test-results))

(defn test-ns-card-init [card test-ns]
  (let [{::ct.fulcro/keys [app*]
         :as              card}
        (ct.fulcro/fulcro-card-init card
          {::f.portal/root          NSTestGroup
           ::f.portal/initial-state #(assoc % ::test-result-id "singleton")
           ::f.portal/app           {:shared
                                     {::card card}

                                     :started-callback
                                     (fn [app]
                                       (run-ns-tests! test-ns (atom app)))}})

        run-tests
        #(run-ns-tests! test-ns app*)]

    (assoc card
      ::wsm/refresh (fn [_] (run-tests))
      ::wsm/render-toolbar (fn []
                             (let [state
                                   (-> @app* :reconciler fp/app-state deref)

                                   {::keys [test-namespaces running? done?]}
                                   (get-in state [::ns-test-run "singleton"])

                                   test-results
                                   (->> test-namespaces
                                        (mapv #(get-in state %))
                                        (remove ::disabled?)
                                        (mapv :test-results))]
                               (dom/div {:style {:flex       "1"
                                                 :display    "flex"
                                                 :alignItems "center"}}
                                 (cond
                                   running?
                                   (dom/div {:style {:fontSize "12px"}} "Running...")

                                   done?
                                   (dom/div {:style {:fontSize "12px"}}
                                     "Finished in " (results-duration test-results) "ms"))
                                 (dom/div {:style {:flex "1"}})
                                 (uc/button {:onClick run-tests} "Rerun tests")))))))

(defn test-ns-card [test-ns]
  {::wsm/test?          true
   ::wsm/card-unlisted? true
   ::wsm/align          ::wsm/align-top-flex
   ::wsm/init           #(test-ns-card-init % test-ns)
   ::wsm/card-width     4
   ::wsm/card-height    15})
