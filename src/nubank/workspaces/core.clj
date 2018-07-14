(ns nubank.workspaces.core
  (:require [cljs.test]
            [nubank.workspaces.model :as wsm]))

(defmacro defcard
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

(defmacro defworkspace [sym layouts]
  (let [fqsym     (if (namespace sym)
                    sym
                    (symbol (name (ns-name *ns*)) (name sym)))
        form-hash (hash &form)]
    `(init-workspace '~fqsym {::wsm/workspace-layouts ~layouts
                              ::wsm/form-hash         ~form-hash})))

(defmacro deftest [sym & forms]
  (let [fqsym  (if (namespace sym)
                 sym
                 (symbol (name (ns-name *ns*)) (name sym)))
        forms' (mapv (fn [exp] `(fn [] ~exp)) forms)
        card-form &form]
    `(do
       (init-test '~fqsym ~forms' '~card-form)
       (cljs.test/deftest ~sym ~@forms))))
