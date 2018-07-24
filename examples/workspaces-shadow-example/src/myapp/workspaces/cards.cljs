(ns myapp.workspaces.cards
  (:require [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [nubank.workspaces.card-types.react :as ct.react]
            [cljs.test :refer [is]]
            [fulcro.client.dom :as dom]))

; simple function to create react elemnents
(defn element [name props & children]
  (apply js/React.createElement name (clj->js props) children))

(ws/defcard hello-card
  (ct.react/react-card
    (element "div" {} "Hello World")))

(ws/defcard counter-example-card
  (let [counter (atom 0)]
    (ct.react/react-card
      counter
      (element "div" {}
        (str "Count: " @counter)
        (element "button" {:onClick #(swap! counter inc)} "+")))))

(ws/deftest sample-test
  (is (= 1 1)))

(ws/defcard styles-card
  {::wsm/node-props {:style {:background "red" :color "white"}}}
  (ct.react/react-card
    (dom/div "I'm in red")))

(def purple-card {::wsm/node-props {:style {:background "#79649a"}}})
(def align-top {::wsm/align {:flex 1}})

(ws/defcard widget-card
  {::wsm/card-width 3 ::wsm/card-height 7}
  purple-card
  align-top
  (ct.react/react-card
    (dom/div "💜")))
