(ns nubank.workspaces.ui.core
  (:require [nubank.workspaces.model :as wsm]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]))

(def color-light-grey "#b1b1b1")
(def color-dark-grey "#404040")

(def color-red-dark "#ca2c29")
(def color-red-light "#f37976")
(def color-green-dark "#187d11")
(def color-green-light "#61d658")
(def color-yellow "#dea54e")

(def font-helvetica "Helvetica Neue,Arial,Helvetica,sans-serif")

(defn header-color [card bg]
  ((::wsm/set-card-header-style card) {:background bg})
  nil)

(def arrow-right "▶")
(def arrow-down "▼")

(def box-shadow "0 6px 6px rgba(0, 0, 0, 0.26),
                 0 10px 20px rgba(0, 0, 0, 0.19),
                 0 0 2px rgba(0,0,0,0.3)")

(def box-shadow-2 "rgba(0, 0, 0, 0.15) 0px 1px 4px,
                   rgba(0, 0, 0, 0.15) 0px 1px 1px")

(fp/defsc Button
  [this props]
  {:css [[:.button {:background-color "#fff"
                    :border           "none"
                    :border-radius    "2px"
                    :color            "#333"
                    :cursor           "pointer"
                    :display          "inline-block"
                    :font-family      "Verdana, sans-serif"
                    :font-size        "11px"
                    :padding          "0px 5px"
                    :line-height      "1.5"
                    :margin-bottom    "0"
                    :font-weight      "400"
                    :text-align       "center"
                    :white-space      "nowrap"
                    :vertical-align   "middle"
                    :user-select      "none"
                    :outline          "none"}
          [:&:hover {:background-color "#e6e6e6"
                     :border-color     "#adadad"
                     :color            "#333"}]]]}
  (apply dom/button :.button props (fp/children this)))

(def button (fp/factory Button))

(fp/defsc CSS [this _]
  {:css-include [Button]})
