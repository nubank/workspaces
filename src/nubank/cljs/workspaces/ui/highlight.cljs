(ns nubank.cljs.workspaces.ui.highlight
  (:require [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            ["highlight.js" :as hljs]))

(fp/defsc Highlight [this {::keys [source language]}]
  {:componentDidMount
   (fn []
     (hljs/highlightBlock (dom/node this)))}

  (dom/pre {:className (or language "clojure")} source))

(def highlight (fp/factory Highlight))
