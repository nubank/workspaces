(ns nubank.workspaces.ui.grid-layout
  (:require [fulcro.client.primitives :as fp]
            [fulcro.client.localized-dom :as dom]
            [garden.selectors :as gs]
            [goog.object :as gobj]
            [cljsjs.react-grid-layout]
            [nubank.workspaces.ui.core :as uc]))

(def column-size 120)
(def max-columns 18)
(def columns-step 1)

(def breakpoints
  (vec
    (for [i (range 0 max-columns columns-step)
          :let [c (+ i columns-step)]]
      {:id         (str "c" c)
       :cols       c
       :breakpoint (if (zero? i) 0 (* c column-size))})))

(defn grid-item-css [props]
  [:$react-grid-item
   [(gs/& (gs/not :.react-grid-placeholder))
    props]])

(def WidthProvider js/ReactGridLayout.WidthProvider)
(def Responsive js/ReactGridLayout.Responsive)

(def GridWithWidth (WidthProvider Responsive))

(fp/defsc GridLayout
  [this props]
  {:css               [[:$react-grid-layout
                        {:position   "relative"
                         :transition "height 200ms ease"}]

                       [:$react-grid-item
                        {:transition          "all 200ms ease"
                         :transition-property "left, top"}

                        [:&$cssTransforms
                         {:transition-property "transform"}]

                        [:&$resizing
                         {:z-index     "1"
                          :will-change "width, height"}]

                        [:&$react-draggable-dragging
                         {:transition  "none"
                          :z-index     "3"
                          :will-change "transform"}]

                        [:&$react-grid-placeholder
                         {:background          "red"
                          :opacity             "0.2"
                          :transition-duration "100ms"
                          :z-index             "2"
                          :-webkit-user-select "none"
                          :-moz-user-select    "none"
                          :-ms-user-select     "none"
                          :-o-user-select      "none"
                          :user-select         "none"}]

                        [:> [:$react-resizable-handle
                             {:position "absolute"
                              :width    "20px"
                              :height   "20px"
                              :bottom   "0"
                              :right    "0"
                              :cursor   "se-resize"}

                             [:&:after
                              {:content       "\"\""
                               :position      "absolute"
                               :right         "5px"
                               :bottom        "5px"
                               :width         "5px"
                               :height        "5px"
                               :border-right  "2px solid rgba(0, 0, 0, 0.4)"
                               :border-bottom "2px solid rgba(0, 0, 0, 0.4)"}]]]]

                       [:$react-resizable
                        {:position "relative"}]

                       [:$react-resizable-handle
                        {:position            "absolute"
                         :width               "20px"
                         :height              "20px"
                         :bottom              "0"
                         :right               "0"
                         :background-position "bottom right"
                         :padding             "0 3px 3px 0"
                         :background-repeat   "no-repeat"
                         :background-origin   "content-box"
                         :box-sizing          "border-box"
                         :cursor              "se-resize"}]

                       (grid-item-css {:background    "#fff"
                                       :border-radius uc/card-border-radius
                                       :display       "flex"})]
   :componentDidMount (fn []
                        (let [{:keys [onBreakpointChange]} (fp/props this)
                              width (-> (gobj/getValueByKeys this "grid")
                                        (dom/node)
                                        (gobj/get "offsetWidth"))
                              bp    (->> (rseq breakpoints)
                                         (filter #(>= width (:breakpoint %)))
                                         first
                                         :id)]
                          (onBreakpointChange bp)))}
  (dom/create-element GridWithWidth (clj->js (assoc props :ref #(gobj/set this "grid" %)))
    (fp/children this)))

(def grid-layout (fp/factory GridLayout))
