(ns nubank.workspaces.ui.core
  (:require [nubank.workspaces.model :as wsm]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.primitives :as fp]))

(def color-white "#fff")
(def color-light-grey "#b1b1b1")
(def color-dark-grey "#404040")

(def color-red-dark "#ca2c29")
(def color-red-light "#f37976")

(def color-green-dark "#187d11")
(def color-mint-green "#8efd86")
(def color-green-light "#61d658")

(def color-yellow "#dea54e")

(def color-mystic "#d9e2e9")
(def color-limed-spruce "#323c47")
(def color-geyser "#cdd7e0")
(def color-fiord "#4b5b6d")
(def color-iron "#e7e8e9")

(def classical-colors
  {::color-scheme                    "light"

   ::bg                              color-white
   ::primary-text-color              "#000"
   ::error-text-color                "#ef0000"

   ::button-bg                       color-fiord
   ::button-active-bg                color-fiord
   ::button-color                    color-white
   ::button-disabled-bg              "#8c95a0"
   ::button-disabled-color           "#ccc"

   ::menu-bg                         color-white
   ::menu-header-bg                  color-dark-grey
   ::menu-header-color               color-white
   ::menu-arrow-bg                   color-dark-grey
   ::menu-text                       "#000"

   ::tab-active-bg                   color-white
   ::tab-bg                          color-iron
   ::tab-text                        color-limed-spruce
   ::tab-border                      color-geyser
   ::tab-text-field-bg               "transparent"
   ::tab-text-field-focus-bg         color-white

   ::workspace-bg                    "#9fa2ab"
   ::workspace-tools-bg              color-white
   ::workspace-tools-color           color-limed-spruce

   ::card-default-color-scheme       "light"
   ::card-bg                         color-white
   ::card-default-text               "#000"
   ::card-toolbar-bg                 color-geyser
   ::card-toolbar-default-text       color-limed-spruce
   ::card-toolbar-more-actions       "#000"
   ::card-header-bg                  color-mystic
   ::card-header-text                color-limed-spruce
   ::card-ellipsis-menu-bg           color-mystic

   ::spotlight-bg                    "#e2e2e2"
   ::spotlight-search-field-bg       "#cccbcd"
   ::spotlight-search-text           "#000"
   ::spotlight-option-text           "#1d1d1d"
   ::spotlight-option-highlight-bg   "#e2d610"
   ::spotlight-option-highlight-text "#000"
   ::spotlight-option-selected-bg    "#582074"
   ::spotlight-option-selected-text  color-white

   ::welcome-msg-bg                  color-dark-grey

   ::help-dialog-bg                  "rgba(0, 0, 0, 0.8)"})

(def dark-colors
  {::color-scheme                    "dark"

   ::bg                              "#202124"
   ::primary-text-color              "#fafafa"
   ::error-text-color                "#CF6679"

   ::button-bg                       "#546E7A"
   ::button-active-bg                "#455A64"
   ::button-color                    "#fafafa"
   ::button-disabled-bg              "#8c95a0"
   ::button-disabled-color           "#ccc"

   ::menu-bg                         "#202124"
   ::menu-header-bg                  "#3f4043"
   ::menu-header-color               "#fafafa"
   ::menu-arrow-bg                   "#3f4043"
   ::menu-text                       "#fafafa"

   ::tab-active-bg                   "#3f4043"
   ::tab-bg                          "#202124"
   ::tab-text                        "#fafafa"
   ::tab-border                      "#3f4043"
   ::tab-text-field-bg               "transparent"
   ::tab-text-field-focus-bg         "#616161"

   ::workspace-bg                    "#202124"
   ::workspace-tools-bg              "#3f4043"
   ::workspace-tools-color           "#fafafa"

   ::card-default-color-scheme       "light"
   ::card-bg                         color-white
   ::card-default-text               "#000"
   ::card-toolbar-bg                 color-geyser
   ::card-toolbar-default-text       "#000"
   ::card-toolbar-more-actions       "#fafafa"
   ::card-header-bg                  "#3f4043"
   ::card-header-text                "#fafafa"
   ::card-ellipsis-menu-bg           "#3f4043"

   ::spotlight-bg                    "#202124"
   ::spotlight-search-field-bg       "#3f4043"
   ::spotlight-search-text           "#fafafa"
   ::spotlight-option-text           "#fafafa"
   ::spotlight-option-highlight-bg   "#FFF59D"
   ::spotlight-option-highlight-text "#000"
   ::spotlight-option-selected-bg    "#546E7A"
   ::spotlight-option-selected-text  "#fafafa"

   ::welcome-msg-bg                  color-dark-grey

   ::help-dialog-bg                  "rgba(0, 0, 0, 0.8)"})

(defn color [color-name]
  (get dark-colors color-name))

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
    (dom/g {:fill (color ::card-toolbar-more-actions)}
      (dom/path {:d "m20 26.6c1.8 0 3.4 1.6 3.4 3.4s-1.6 3.4-3.4 3.4-3.4-1.6-3.4-3.4 1.6-3.4 3.4-3.4z m0-10c1.8 0 3.4 1.6 3.4 3.4s-1.6 3.4-3.4 3.4-3.4-1.6-3.4-3.4 1.6-3.4 3.4-3.4z m0-3.2c-1.8 0-3.4-1.6-3.4-3.4s1.6-3.4 3.4-3.4 3.4 1.6 3.4 3.4-1.6 3.4-3.4 3.4z"}))))

(fp/defsc Button
  [this props]
  {:css [[:.button
          font-os12sb
          {:background-color (color ::button-bg)
           :border           "none"
           :border-radius    "3px"
           :color            (color ::button-color)
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
          [:&:active
           {:background (color ::button-active-bg)}]
          [:&:disabled
           {:background (color ::button-disabled-bg)
            :color      (color ::button-disabled-color)
            :cursor     "not-allowed"}]]]}
  (apply dom/button :.button props (fp/children this)))

(def button (fp/factory Button))

(fp/defsc CSS [this _]
  {:css-include [Button]})
