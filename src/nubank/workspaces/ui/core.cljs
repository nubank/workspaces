(ns nubank.workspaces.ui.core
  (:require [nubank.workspaces.model :as wsm]
            [com.fulcrologic.fulcro-css.localized-dom :as dom]
            [com.fulcrologic.fulcro.components :as comp]))

(def color-white "#fff")
(def color-light-grey "#b1b1b1")
(def color-dark-grey "#404040")
(def color-mystic "#d9e2e9")
(def color-limed-spruce "#323c47")
(def color-geyser "#cdd7e0")
(def color-fiord "#4b5b6d")
(def color-iron "#e7e8e9")

(def color-red-dark "#ca2c29")
(def color-red-light "#f37976")
(def color-green-dark "#187d11")
(def color-mint-green "#8efd86")
(def color-green-light "#61d658")
(def color-yellow "#dea54e")

(def card-border-radius "6px")

(def font-helvetica "Helvetica Neue,Arial,Helvetica,sans-serif")
(def font-open-sans "'Open Sans', sans-serif")
(def font-monospace "monospace")

(def font-os12sb
  {:font-size   "12px"
   :font-family font-open-sans
   :font-weight "600"})

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

(def close-icon-css
  {:cursor      "pointer"
   :font-size   "23px"
   :line-height "1em"})

(defn more-icon [props]
  (dom/svg (merge {:width 20 :height 19 :viewBox "0 0 40 40"} props)
    (dom/g {:fill "#000"} (dom/path {:d "m20 26.6c1.8 0 3.4 1.6 3.4 3.4s-1.6 3.4-3.4 3.4-3.4-1.6-3.4-3.4 1.6-3.4 3.4-3.4z m0-10c1.8 0 3.4 1.6 3.4 3.4s-1.6 3.4-3.4 3.4-3.4-1.6-3.4-3.4 1.6-3.4 3.4-3.4z m0-3.2c-1.8 0-3.4-1.6-3.4-3.4s1.6-3.4 3.4-3.4 3.4 1.6 3.4 3.4-1.6 3.4-3.4 3.4z"}))))

(comp/defsc Button
  [this props]
  {:css [[:.button
          font-os12sb
          {:background-color color-fiord
           :border           "none"
           :border-radius    "3px"
           :color            color-white
           :cursor           "pointer"
           :display          "inline-block"
           :padding          "2px 8px"
           :line-height      "1.5"
           :margin-bottom    "0"
           :text-align       "center"
           :white-space      "nowrap"
           :vertical-align   "middle"
           :user-select      "none"
           :outline          "none"}
          [:&:disabled
           {:background "#8c95a0"
            :color      "#ccc"
            :cursor     "not-allowed"}]]]}
  (apply dom/button :.button props (comp/children this)))

(def button (comp/factory Button))

(comp/defsc CSS [_ _]
  {:css-include [Button]})
