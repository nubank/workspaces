(ns nubank.workspaces.ui.cursor
  (:require [com.fulcrologic.fulcro.components :as fp]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [nubank.workspaces.ui.events :as dom-events]
            [goog.object :as gobj]))

(defn get-nth [i s]
  (->> s (drop i) first))

(defn seq-prev [current items]
  (let [index (.indexOf items current)]
    (if (= -1 index)
      (first items)
      (let [item-count (count items)
            next-index (-> (if (= 0 index)
                             (dec item-count)
                             (dec index))
                           (mod item-count))]
        (get-nth next-index items)))))

(defn seq-next [current items]
  (let [selected-index (.indexOf items current)]
    (if (= -1 selected-index)
      (first items)
      (let [item-count (count items)
            next-index (-> (if (= selected-index (dec item-count))
                             0
                             (inc selected-index))
                           (mod item-count))]
        (get-nth next-index items)))))

(defn scroll-up [options container item]
  (if item
    (if-not (<= (gobj/get container "scrollTop")
              (gobj/get item "offsetTop")
              (+ (gobj/get container "scrollTop") (gobj/get container "offsetHeight")))
      (if (= (last options) next)
        (gobj/set container "scrollTop" (-> (gobj/get container "scrollHeight")))
        (gobj/set container "scrollTop" (-> (gobj/get item "offsetTop")))))))

(defn scroll-down [options container item]
  (if item
    (let [item-bottom (+ (gobj/get item "offsetTop")
                        (gobj/get item "scrollHeight"))]
      (if-not (<= (gobj/get container "scrollTop")
                item-bottom
                (+ (gobj/get container "scrollTop") (gobj/get container "offsetHeight")))
        (if (= (first options) next)
          (gobj/set container "scrollTop" 0)
          (gobj/set container "scrollTop" (-> item-bottom
                                              (- (gobj/get container "offsetHeight")))))))))

(defn dom-props [props]
  (into {} (remove (comp namespace first)) props))

(fp/defsc VerticalCursor
  [this
   {::keys [options factory on-change on-select value->key]
    :or    {value->key pr-str}
    :as    props}]
  {:css [[:.container {:overflow "auto"
                       :flex     "1"
                       :position "relative"}]]
   :componentDidMount
        (fn [this]
          (let [{::keys [options value value->key]} (fp/props this)]
            (scroll-down options
                         (gobj/get this "container")
                         (gobj/get this (str "item-" (value->key value))))))}
  (dom/div :.container (dom-props (assoc props :ref #(gobj/set this "container" %)))
    (dom-events/dom-listener
      (assoc props
        ::dom-events/keystroke "up"
        ::dom-events/action
        (fn [e]
          (.preventDefault e)
          (let [{::keys [value options]} (fp/props this)
                next      (seq-prev value options)
                container (gobj/get this "container")
                item      (gobj/get this (str "item-" (value->key next)))]
            (when (and container item)
              (scroll-up options container item)
              (on-change next e))))))

    (dom-events/dom-listener
      (assoc props
        ::dom-events/keystroke "down"
        ::dom-events/action
        (fn [e]
          (.preventDefault e)
          (let [{::keys [value options]} (fp/props this)
                next        (seq-next value options)
                container   (gobj/get this "container")
                item        (gobj/get this (str "item-" (value->key next)))]
            (when (and container item)
              (scroll-down options container item)
              (on-change next e))))))

    (dom-events/dom-listener
      (assoc props
        ::dom-events/keystroke "return"
        ::dom-events/action #(on-select (-> this fp/props ::value) %)))

    (dom-events/dom-listener
      (assoc props
        ::dom-events/keystroke "escape"
        ::dom-events/action #(on-select nil %)))

    (for [x options]
      (dom/div {:key (value->key x)
                :ref #(gobj/set this (str "item-" (value->key x)) %)}
        (factory x)))))

(def vertical-cursor (fp/factory VerticalCursor))
