(ns nubank.workspaces.data
  (:require [fulcro.client :as fulcro]
            [nubank.workspaces.ui.events :as events]))

(defonce app* (atom (fulcro/new-fulcro-client
                      :shared
                      {}

                      :started-callback
                      (fn [app]
                        (js/setTimeout #(events/trigger-event js/window {::events/event "resize"}) 600)))))

(defonce workspace-definitions* (atom {}))
(defonce card-definitions* (atom {}))
(defonce card-definitions-snap* (atom {}))

(defonce active-cards* (atom {}))

(defn card-definition [card-id]
  (get @card-definitions* card-id))

(defn active-card [card-id]
  (get @active-cards* card-id))
