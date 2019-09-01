(ns myapp.workspaces.custom-card
  (:require [nubank.workspaces.core :as ws]
            [nubank.workspaces.model :as wsm]
            [nubank.workspaces.card-types.react :as ct.react]
            [nubank.workspaces.card-types.util :as ct.util]
            [goog.dom :as gdom]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [nubank.workspaces.ui.core :as uc]))

(ws/defcard custom-card
  {::wsm/align {:flex 1}
   ::wsm/init
               (fn [card]
                 (let [counter (atom 0)]
                   ; wrap our definition with positioned.card, from nubank.workspaces.card-types.util
                   (ct.util/positioned-card card
                     {::wsm/dispose
                      (fn [node]
                        ; doesn't make a real difference for resource cleaning, just a dummy example
                        ; so you can replace the code
                        (gdom/setTextContent node ""))

                      ::wsm/refresh
                      (fn [node]
                        (gdom/setTextContent node (str "Card updated, count: " (swap! counter inc) "!")))

                      ::wsm/render
                      (fn [node]
                        (gdom/setTextContent node (str "Card rendered, count: " @counter "!")))})))})

; it's a good pattern to have the card init function separated from the card function
; this will make easier for others to use your card as a base for extension.
(defn react-timed-card-init [card state-atom component]
  (let [{::wsm/keys [dispose refresh render] :as react-card} (ct.react/react-card-init card state-atom component)
        timer (js/setInterval #(swap! state-atom update ::ticks inc) 1000)]
    (assoc react-card
      ::wsm/dispose
      (fn [node]
        ; clean the timer on dispose
        (js/clearInterval timer)
        (dispose node))

      ::wsm/refresh
      (fn [node]
        (refresh node))

      ::wsm/render
      (fn [node]
        (render node))

      ::wsm/render-toolbar
      (fn []
        (dom/div
          (uc/button {:onClick #((::wsm/set-card-header-style card) {:background "#cc0"})} "Change header color")
          (uc/button {:onClick #(js/console.log "State" @state-atom)} "Log app state"))))))

(defn react-timed-card [state-atom component]
  {::wsm/init #(react-timed-card-init % state-atom component)})

(ws/defcard react-timed-card-sample
  (let [state (atom {})]
    (react-timed-card state
      ; note since we are not using the macro it's better to send a function to avoid
      ; premature rendering
      (fn []
        (dom/div (str "Tick: " (::ticks @state)))))))
