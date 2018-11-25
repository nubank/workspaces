(ns nubank.workspaces.ui.spotlight
  (:require [nubank.workspaces.ui.cursor :as cursor]
            [fulcro.client.localized-dom :as dom]
            [goog.object :as gobj]
            [nubank.workspaces.ui.events :as dom-events]
            [fulcro.client.mutations :as fm]
            [fulcro.client.primitives :as fp]
            [fulcro.incubator.ui-state-machines :as fsm]
            [nubank.workspaces.ui.core :as uc]
            ["../lib/fts_fuzzy_match" :refer [fuzzy_match]]))

(def max-results 50)

(defn value->label [{::keys [id label]}]
  (or label (str id)))

(defn value->key [{::keys [type id] :as opt}]
  (if (= type ::workspace)
    (str id)
    (str (some-> opt ::type name) "-" (value->label opt))))

(defn match-one [filter opt]
  (let [[match? score hl] (fuzzy_match filter (value->label opt))]
    (if match?
      (assoc opt ::match? match? ::match-score score ::match-hl hl))))

(defn fuzzy-match [{:keys [options search-input]}]
  (if (seq search-input)
    (let [fuzzy   (partial match-one search-input)
          compare #(compare %2 %)]
      (->> options
           (keep fuzzy)
           (sort-by compare ::match-score)
           (take max-results)))
    options))

(fsm/defstatemachine spotlight-sm
  {::fsm/aliases
   {:search-input    [:spotlight ::filter]
    :options         [:spotlight ::options]
    :current-options [:spotlight ::filtered-options]}

   ::fsm/states
   {:initial
    {::fsm/handler
     (fn [env]
       (-> env
           (fsm/activate :searching)))}

    :searching
    {::fsm/events
     {::fsm/value-changed
      {::fsm/handler
       (fn [env]
         (let [filtered-options (fuzzy-match (fsm/aliased-data env))]
           (-> env
               (fsm/set-aliased-value :current-options filtered-options))))}

      :exit!
      {::fsm/target-state ::fsm/exit}}}}})

(fm/defmutation reset [{::keys [options]}]
  (action [{:keys [state ref]}]
    (let [{::keys [filter]} (get-in @state ref)
          options          (sort-by value->label options)
          filtered-options (fuzzy-match {:search-input filter :options options})]
      (swap! state update-in ref assoc
        ::options options
        ::filtered-options filtered-options))))

(fp/defsc SpotlightEntry
  [this {::keys [opt value on-change on-select]}]
  {:css [[:.option
          {:color         "#1d1d1d"
           :cursor        "pointer"
           :font-size     "16px"
           :padding       "2px 3px"
           :white-space   "nowrap"
           :overflow      "hidden"
           :text-overflow "ellipsis"}

          :b
          {:color "#000"}]

         [:.option-type
          {:font-size  "11px"
           :font-style "italic"}]

         [:.option-selected
          {:background "#582074"
           :color      "#fff"}]

         [:.solo-hint
          {:display "none"}]]}
  (dom/div :.option {:classes [(if (= opt value) :.option-selected)]
                     :onClick #(do
                                 (on-change opt)
                                 (on-select opt (.-altKey %)))}
    (dom/div {:dangerouslySetInnerHTML {:__html (or (::match-hl opt) (value->label opt))}})
    (dom/div :.option-type
      (some-> opt ::type name)
      (if (some-> opt ::type (not= ::workspace))
        (dom/span :.solo-hint " - open solo")))))

(def spotlight-entry (fp/factory SpotlightEntry))

(fp/defsc Spotlight
  [this
   {::keys [options filter value filtered-options]}
   {::keys [on-select]
    :or    {on-select identity}}
   css]
  {:initial-state        (fn [options]
                           (let [options (sort-by value->label options)]
                             {::id               (random-uuid)
                              ::options          options
                              ::value            (first options)
                              ::filter           ""
                              ::filtered-options []}))
   :ident                [::id ::id]
   :query                [::id ::options ::filter ::filtered-options ::value]
   :css                  [[:.area-container
                           {:height "600px"}]

                          [:.container
                           {:background    "#e2e2e2"
                            :border-radius "3px"
                            :box-shadow    "0 6px 6px rgba(0, 0, 0, 0.26), 0 10px 20px rgba(0, 0, 0, 0.19), 0 0 2px rgba(0,0,0,0.3)"
                            :padding       "10px"}]

                          [:.search
                           {:background  "#cccbcd"
                            :border      "0"
                            :box-sizing  "border-box"
                            :color       "#000"
                            :font-family uc/font-helvetica
                            :font-size   "32px"
                            :outline     "0"
                            :padding     "10px"
                            :width       "100%"}]

                          [:$cljs-workspaces-extended-views
                           [:.option-selected
                            [:.solo-hint
                             {:display "inline"}]]]]
   :css-include          [cursor/VerticalCursor SpotlightEntry]
   :componentDidMount    (fn []
                           (fsm/begin! this spotlight-sm ::spotlight {:spotlight this})
                           (.select (gobj/get this "input")))
   :componentWillUnmount (fn []
                           (fsm/trigger! this ::spotlight :exit!))
   :initLocalState       (fn []
                           (let [on-change #(fm/set-value! this ::value %)]
                             {:cursor-select
                              (fn [opt e]
                                (let [{::keys [on-select]
                                       :or    {on-select identity}}
                                      (-> this fp/props fp/get-computed)]
                                  (.stopPropagation e)
                                  (on-select opt (.-altKey e))))

                              :cursor-change
                              on-change

                              :cursor-factory
                              (fn [opt]
                                (let [{::keys [value] :as props} (fp/props this)
                                      {::keys [on-select] :or {on-select identity}}
                                      (-> props fp/get-computed)]
                                  (spotlight-entry {::opt       opt
                                                    ::value     value
                                                    ::on-change on-change
                                                    ::on-select on-select})))

                              :cursor-target
                              #(gobj/get this "input")}))}
  (let [options'  (if (seq filter) filtered-options (take max-results options))
        on-change (fp/get-state this :cursor-change)]
    (dom/div :.area-container
      (dom/div :.container
        (dom/input :.search {:value     filter
                             :autoFocus true
                             :ref       #(gobj/set this "input" %)
                             :onChange  #(fsm/set-string! this ::spotlight :search-input %)})
        (if (seq options')
          (dom/div :.options
            (cursor/vertical-cursor
              {:style              {:maxHeight "500px"}
               ::cursor/value      value
               ::cursor/options    options'
               ::cursor/on-change  on-change
               ::cursor/on-select  (fp/get-state this :cursor-select)
               ::cursor/factory    (fp/get-state this :cursor-factory)
               ::cursor/value->key value->key
               ::dom-events/target (fp/get-state this :cursor-target)})))))))

(def spotlight (fp/factory Spotlight))
