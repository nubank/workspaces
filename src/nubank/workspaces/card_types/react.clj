(ns nubank.workspaces.card-types.react)

(defmacro react-card
  ([comp] `(react-card* nil (fn [] ~comp)))
  ([state comp] `(react-card* ~state (fn [] ~comp))))
