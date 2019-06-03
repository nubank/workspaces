(ns nubank.workspaces.data
  (:require [com.fulcrologic.fulcro.application :as fulcro]
            [nubank.workspaces.ui.events :as events]))

(defonce app* (fulcro/fulcro-app))

(defonce workspace-definitions* (atom {}))
(defonce card-definitions* (atom {}))
(defonce card-definitions-snap* (atom {}))

(defonce active-cards* (atom {}))

(defn card-definition [card-id]
  (get @card-definitions* card-id))

(defn active-card [card-id]
  (get @active-cards* card-id))
