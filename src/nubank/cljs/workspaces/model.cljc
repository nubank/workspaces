(ns nubank.cljs.workspaces.model
  (:require [clojure.spec.alpha :as s]))

(s/def ::card-id symbol?)

; card instance

(s/def ::dom-node any?)

(s/def ::dispose (s/fspec :args (s/cat :node ::dom-node)))
(s/def ::refresh (s/fspec :args (s/cat :node ::dom-node)))
(s/def ::render (s/fspec :args (s/cat :node ::dom-node)))
(s/def ::render-toolbar (s/fspec :args (s/cat)))

(s/def ::card-instance (s/keys :req [::render] :opt [::dispose ::refresh ::render-toolbar]))

(s/def ::card-meta (s/keys))
(s/def ::node-props (s/map-of keyword? any?))
(s/def ::init (s/fspec :ret ::card-instance))
(s/def ::card-width pos-int?)
(s/def ::card-height pos-int?)
(s/def ::card-resizable? boolean?)
(s/def ::card-unlisted? boolean?)
(s/def ::card (s/keys :req [::init] :opt [::title ::node-props]))
(s/def ::workspace-layouts map?)

(s/def ::align (s/or :predefined keyword? :custom map?))
(s/def ::form-hash int?)
