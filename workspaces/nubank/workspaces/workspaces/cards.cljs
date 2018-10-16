(ns nubank.workspaces.workspaces.cards
  (:require [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [nubank.workspaces.card-types.fulcro :as ct.fulcro]
            [nubank.workspaces.lib.fulcro-portal :as f.portal]
            [nubank.workspaces.ui.spotlight :as spotlight]
            [nubank.workspaces.ui.highlight :as highlight]
            [cljs.test :refer [is testing]]
            [fulcro.client.primitives :as fp]
            [nubank.workspaces.ui :as ui]
            [nubank.workspaces.card-types.react :as ct.react]
            [fulcro.client.localized-dom :as dom]))

(def options
  '[{::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.fulcro.db-helpers-ws}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.logic.misc-ws}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.ui.modal-ws}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.atenta.components-ws}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.cca.components-ws}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.chargeback.components-ws}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.purchases.components-ws}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.temporary.tests}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.fulcro.db-helpers-ws/init-state}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.fulcro.db-helpers-ws/resolve-path}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.fulcro.db-helpers-ws/swap-entity!}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.fulcro.db-helpers-ws/swap-in!}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.logic.misc-ws/test-capitalize-words}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.logic.misc-ws/test-google-maps-location-uri}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.logic.misc-ws/test-index-of}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.logic.misc-ws/test-shuffle-uri}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.tools-ws/auth-token-provider}
    {::spotlight/type ::spotlight/workspace
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/all}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/alerts-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/button-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/calendar}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/checkbox-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/clipboard}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/collapsible-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/color-dropdown-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/dropdown-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/graph-errors-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/hint-link-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/hint-link-right-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/horizontal-rule}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/radio-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/row-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/row-card-align-center}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/row-card-space-around}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/row-card-space-between}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/row-card-space-evenly}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/sup-content-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/text-area-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/text-field-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/text-field-titled-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/togglers-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/wide-button-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/widget-header-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/widget-info-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/widget-list-items-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/widget-list-items-clickable-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/widget-list-items-separator-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/widget-list-items-timeline-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/widget-list-scroll-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/widget-loader-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/widget-shadow}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.layout-ws/widget-with-floater}
    {::spotlight/type ::spotlight/workspace
     ::spotlight/id   nubank.shuffle.common.ui.modal-ws/all}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.modal-ws/modal-gallery}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.modal-ws/modal-raw}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.common.ui.modal-ws/modal-widget}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.ui.modal-ws/next-image-mutation-test}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.ui.modal-ws/prev-image-mutation-test}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.common.ui.modal-ws/rotate-image-mutation-test}
    {::spotlight/type ::spotlight/workspace
     ::spotlight/id   nubank.shuffle.modules.atenta.components-ws/main}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.atenta.components-ws/atenta}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.atenta.components-ws/atenta-loading}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.atenta.components-ws/atenta-no-questions}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.atenta.components-ws/atenta-only-customer-message}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.atenta.components-ws/quality-rate-widget}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.atenta.components-ws/test-atenta-rendering}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.atenta.components-ws/transcript-widget}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.cca.components-ws/cancel-card-modal}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.cca.components-ws/cancel-reissue-card-modal}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.cca.components-ws/card-active}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.cca.components-ws/card-blocked}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.cca.components-ws/card-canceled}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.cca.components-ws/card-inactive}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.cca.components-ws/cards-widget}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.cca.components-ws/test-cancel-card-modal}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.cca.components-ws/test-cancel-reissue-card-modal}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.cca.components-ws/test-cards-widget}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.chargeback.components-ws/chargeback-details}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.chargeback.components-ws/chargebacks-widget}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.chargeback.components-ws/chargebacks-widget-blank}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.chargeback.components-ws/reversals-widget}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.chargeback.components-ws/reversals-widget-blank}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.chargeback.components-ws/test-chargeback-details}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.chargeback.components-ws/test-chargeback-widget}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.chargeback.components-ws/test-reversals-widget}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-delivery-status-unavailable}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-expanded}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-expanded-readable-barcode}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-origin-unavailable-with-bill}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-origin-unavailable-without-bill}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-paid}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-paid-unregistered}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-registered}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-registered-with-dda}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-state-unavailable-with-payments}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-unregistered}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-widget-can-remap-orphan-payment}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-widget-cannot-remap-orphan-payment}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-widget-load-failed}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-widget-loading}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-widget-no-boletos}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boleto-widget-unloaded}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boletos-widget}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boletos-widget-remap-error}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.payments.components-ws/boletos-widget-test}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.person.components-ws/empty-income-proofs-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.person.components-ws/empty-rollout-widget-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.person.components-ws/income-proofs}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.person.components-ws/person-header-card}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.person.components-ws/person-information}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.person.components-ws/rollout-widget-view}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.person.components-ws/rollouts}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.purchases.components-ws/purchases-details}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.purchases.components-ws/purchases-list}
    {::spotlight/type ::spotlight/card
     ::spotlight/id   nubank.shuffle.modules.purchases.components-ws/purchases-widget}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.purchases.components-ws/test-purchase-details}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.purchases.components-ws/test-purchase-list}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.purchases.components-ws/test-purchase-status-history}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.shuffle.modules.purchases.components-ws/test-purchases-widget}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.temporary.tests/assertion-error}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.temporary.tests/async-err}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.temporary.tests/async-err-assertion}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.temporary.tests/simple}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.temporary.tests/simple-async}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.temporary.tests/simple-async-succ}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.temporary.tests/simple-fail}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.temporary.tests/simple-fail-eq}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.temporary.tests/simple-succ}
    {::spotlight/type ::spotlight/test
     ::spotlight/id   nubank.temporary.tests/simpler-err}])

(ws/defcard card-react-error
  (ct.react/react-card
    (dom/div "Hello World" :fail)))

(fp/defsc SpotlightContainer
  [this {:keys [spot]}]
  {:initial-state (fn [_]
                    {:spot (fp/get-initial-state spotlight/Spotlight options)})
   :ident         [::id ::id]
   :query         [::id
                   {:spot (fp/get-query spotlight/Spotlight)}]
   :css           []
   :css-include   [spotlight/Spotlight]}
  (spotlight/spotlight spot))

(ws/defcard spotlight-card
  {::wsm/align       {:flex "1"}
   ::wsm/card-width  4
   ::wsm/card-height 17}
  (ct.fulcro/fulcro-card
    {::f.portal/root SpotlightContainer}))

(ws/defcard highlight-card
  {::wsm/align      {:flex "1"}
   ::wsm/card-width 5 ::wsm/card-height 12}
  (ct.react/react-card
    (highlight/highlight {::highlight/source   "(ws/defcard\n purchases-charges\n {:nubank.workspaces.model/card-width 7,\n  :nubank.workspaces.model/card-height 11}\n cards/widget-card-config\n (cards/fulcro-card\n  chargeback/PurchaseCharges\n  {:nubank.shuffle.workspaces.card-types/gen-env gen-env,\n   :nubank.shuffle.workspaces.card-types/load true}))"
                          ::highlight/language "clojure"})))

(defn child-component [props]
  (dom/div "Inside changed"))

(fp/defsc MyComponent [this _]
  {}
  (dom/div {} "Hello World Now it goes"
    (dom/create-element child-component)))

(def my-component (fp/factory MyComponent))

(ws/defcard my-component-card
  {::wsm/align      {:flex "1"}
   ::wsm/card-width 2 ::wsm/card-height 6}
  (ct.react/react-card
    (my-component {})))

(ws/deftest test-build-grid
  (is (= (ui/build-grid [(ui/block 2 2 0 0)])
         {[0 0] (ui/block 2 2 0 0)
          [0 1] (ui/block 2 2 0 0)
          [1 0] (ui/block 2 2 0 0)
          [1 1] (ui/block 2 2 0 0)})))

(ws/deftest test-smart-item-position
  (is (= (ui/smart-item-position 10 (ui/block 4 4 0 0) [])
         (ui/block 4 4 0 0)))

  (is (= (ui/smart-item-position 10 (ui/block 4 4 0 0) [(ui/block 2 2 0 0)])
         (ui/block 4 4 2 0)))

  (is (= (ui/smart-item-position 8 (ui/block 4 15 0 0) [(ui/block 5 12 0 0)])
         (ui/block 4 15 0 12))))
