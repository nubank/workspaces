(ns nubank.workspaces.core
  (:require [clojure.test :as test]
            [nubank.workspaces.model :as wsm]))

(defmacro defcard
  "Defines a new card, sym is a symbol for the card name (like def), and settings
  are maps that will be merged on definition time to define the card."
  ([sym & settings]
   (let [fqsym     (if (namespace sym)
                     sym
                     (symbol (name (ns-name *ns*)) (name sym)))
         file-meta (meta &form)
         form      &form]
     `(init-card '~fqsym (merge
                           {::wsm/card-form '~form
                            ::wsm/card-meta ~file-meta}
                           ~@settings)))))

(defmacro defworkspace
  "Creates a new static workspace, layouts is a transit string, get that string by
  exporting some of your local workspaces."
  [sym layouts]
  (let [fqsym     (if (namespace sym)
                    sym
                    (symbol (name (ns-name *ns*)) (name sym)))
        form-hash (hash &form)]
    `(init-workspace '~fqsym {::wsm/workspace-layouts ~layouts
                              ::wsm/form-hash         ~form-hash})))

(defn init-test "Stub method, does nothing in CLJ" [_ _ _])

(defmacro deftest
  "Creates a test card, you can replace your cljs.test/deftest call by this, will
  work the same, but also define a card (the original cljs.test/deftest will also be
  called)."
  [sym & forms]
  (let [fqsym  (if (namespace sym)
                 sym
                 (symbol (name (ns-name *ns*)) (name sym)))
        forms' (mapv (fn [exp] `(fn [] ~exp)) forms)
        card-form &form]
    `(do
       (init-test '~fqsym ~forms' '~card-form)
       (test/deftest ~sym ~@forms))))
