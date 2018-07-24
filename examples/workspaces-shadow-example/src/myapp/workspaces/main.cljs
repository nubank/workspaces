(ns myapp.workspaces.main
  (:require [nubank.workspaces.core :as ws]
            [myapp.workspaces.cards]
            [myapp.workspaces.custom-card]
            [myapp.workspaces.fulcro-demo-cards]
            [myapp.workspaces.reframe-demo-cards]))

(defonce init (ws/mount))
