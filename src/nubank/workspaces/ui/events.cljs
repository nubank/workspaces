(ns nubank.workspaces.ui.events
  (:require [cljs.spec.alpha :as s]
            [clojure.string :as str]
            [goog.object :as gobj]
            [fulcro.client.dom :as dom]
            [fulcro.client.primitives :as fp]))

(def KEYS
  {"backspace" 8
   "tab"       9
   "return"    13
   "escape"    27
   "space"     32
   "left"      37
   "up"        38
   "right"     39
   "down"      40
   "slash"     191
   "0"         48
   "1"         49
   "2"         50
   "3"         51
   "4"         52
   "5"         53
   "6"         54
   "7"         55
   "8"         56
   "9"         57
   "a"         65
   "b"         66
   "c"         67
   "d"         68
   "e"         69
   "f"         70
   "g"         71
   "h"         72
   "i"         73
   "j"         74
   "k"         75
   "l"         76
   "m"         77
   "n"         78
   "o"         79
   "p"         80
   "q"         81
   "r"         82
   "s"         83
   "t"         84
   "u"         85
   "v"         86
   "w"         87
   "x"         88
   "y"         89
   "z"         90
   ";"         186
   "="         187
   ","         188
   "minus"     189
   "."         190
   "/"         191
   "["         219
   "\\"        220
   "]"         221
   "'"         222})

(s/def ::key-string (set (keys KEYS)))
(s/def ::modifier #{"ctrl" "alt" "meta" "shift"})
(s/def ::keystroke
  (s/and string?
    (s/conformer #(str/split % #"-") #(str/join "-" %))
    (s/cat :modifiers (s/* ::modifier) :key ::key-string)))
(s/def ::key-code pos-int?)

(defn pd
  "Wraps function f with a call to .preventDefault on the event. This is a helper
  to compose with event callback functions so they also cancel the default browser
  event handler.

  Usage:

  (dom/a {:href \"#\" :onClick (pd #(console.log :clicked))} \"No default\")"
  [f]
  (fn [e]
    (.preventDefault e)
    (f e)))

(defn parse-keystroke [keystroke]
  (if-let [{:keys [modifiers key]} (s/conform ::keystroke keystroke)]
    {::key-code  (get KEYS key)
     ::modifiers modifiers}))

(defn match-key? [e key-code]
  (if key-code
    (= (gobj/get e "keyCode") key-code)
    true))

(defn match-modifiers? [e modifiers]
  (if modifiers
    (every? #(gobj/get e (str % "Key")) modifiers)
    true))

(defn match-keystroke? [e {::keys [modifiers key-code]}]
  (and (match-key? e key-code) (match-modifiers? e modifiers)))

(defn get-target [target]
  (cond
    (fn? target) (target)
    target target
    :else js/document.body))

(defn attach-event [this]
  (let [{::keys [target event action]} (fp/props this)
        target (get-target target)]
    (assert event "You must provide an event to dom-listener")
    (gobj/set this "handler" action)
    (if target
      (.addEventListener target event action))))

(defn dettach-event [this]
  (if-let [handler (gobj/get this "handler")]
    (let [{::keys [target event]} (fp/props this)
          target (get-target target)]
      (if target
        (.removeEventListener target event handler)))))

(fp/defsc DomListener [this props]
  {:componentDidMount    #(attach-event this)
   :componentWillUnmount #(dettach-event this)}

  (dom/noscript nil))

(def dom-listener* (fp/factory DomListener))

(defn dom-listener [{::keys [keystroke click action] :as event}]
  (cond
    click
    (dom-listener* (assoc event ::event "click" ::action click))

    keystroke
    (if-let [matcher (parse-keystroke keystroke)]
      (dom-listener* (assoc event
                       ::event "keydown"
                       ::action #(if (match-keystroke? % matcher) (action %))))
      (js/console.warn (str "Keystroke `" keystroke "` is not valid.")))

    :else
    (dom-listener* event)))

(defn create-event [{::keys [event]}]
  (doto (js/document.createEvent "HTMLEvents")
    (.initEvent event true true)))

(defn trigger-event [target evt]
  (.dispatchEvent target (create-event evt)))
