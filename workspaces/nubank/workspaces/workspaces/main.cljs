(ns nubank.workspaces.workspaces.main
  (:require [nubank.workspaces.core :as ws]
            [nubank.workspaces.cards]))

(defonce init (ws/mount))
