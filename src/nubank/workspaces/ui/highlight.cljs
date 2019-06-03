(ns nubank.workspaces.ui.highlight
  (:require [com.fulcrologic.fulcro.components :as fp]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [cljsjs.highlight]))

(fp/defsc Highlight [this {::keys [source language]}]
  {:componentDidMount
   (fn []
     (js/hljs.highlightBlock (dom/node this)))}

  (dom/pre {:className (or language "clojure")} source))

(def highlight (fp/factory Highlight))
