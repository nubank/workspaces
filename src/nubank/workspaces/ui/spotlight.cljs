(ns nubank.workspaces.ui.spotlight
  (:require [nubank.workspaces.ui.cursor :as cursor]
            [fulcro.client.localized-dom :as dom]
            [goog.object :as gobj]
            [nubank.workspaces.ui.events :as dom-events]
            [fulcro.client.mutations :as fm]
            [fulcro.client.primitives :as fp]
            [nubank.workspaces.ui.core :as uc]))

(def max-results 50)

(def value->label (comp str ::id))

(defn escape-re [input]
  (let [re (js/RegExp. "([.*+?^=!:${}()|[\\]\\/\\\\])" "g")]
    (-> input str (.replace re "\\$1"))))

(defn fuzzy-re [input]
  (-> (reduce (fn [s c] (str s (escape-re c) ".*")) "" input)
      (js/RegExp "i")))

(defn fuzzy-filter [{::keys [options filter]}]
  (if (seq filter)
    (let [fuzzy (fuzzy-re filter)]
      (->> options
           (filterv #(re-find fuzzy (value->label %)))
           (take max-results)
           (sort-by value->label)))
    options))

(fm/defmutation filter-results [{::keys [filter]}]
  (action [{:keys [state ref]}]
    (let [{::keys [options]} (get-in @state ref)
          filtered-options (fuzzy-filter {::filter filter ::options options})]
      (swap! state update-in ref assoc
        ::filter filter
        ::filtered-options filtered-options
        ::value (first filtered-options)))))

(fm/defmutation reset [{::keys [options]}]
  (action [{:keys [state ref]}]
    (let [{::keys [filter]} (get-in @state ref)
          options          (sort-by value->label options)
          filtered-options (fuzzy-filter {::filter filter ::options options})]
      (swap! state update-in ref assoc
        ::options options
        ::filtered-options filtered-options))))

(fp/defsc Spotlight
  [this
   {::keys [options filter value filtered-options]}
   {::keys [on-select]
    :or    {on-select identity}}
   css]
  {:initial-state     (fn [options]
                        (let [options (sort-by value->label options)]
                          {::id               (random-uuid)
                           ::options          options
                           ::value            (first options)
                           ::filter           ""
                           ::filtered-options []}))
   :ident             [::id ::id]
   :query             [::id ::options ::filter ::filtered-options ::value]
   :css               [[:.area-container
                        {:height "600px"}]
                       [:.container
                        {:background "#e2e2e2"
                         :padding    "10px"}]
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
                       [:.options
                        {:font-family uc/font-open-sans
                         :margin-top  "10px"}]
                       [:.option
                        {:cursor        "pointer"
                         :font-size     "16px"
                         :padding       "2px 3px"
                         :white-space   "nowrap"
                         :overflow      "hidden"
                         :text-overflow "ellipsis"}]
                       [:.option-selected
                        {:background "#582074"
                         :color      "#fff"}]]
   :css-include       [cursor/VerticalCursor]
   :componentDidMount (fn [] (.select (gobj/get this "input")))}
  (let [options'  (if (seq filter) filtered-options (take max-results options))
        on-change #(fm/set-value! this ::value %)]
    (dom/div :.area-container
      (dom/div :.container
        (dom/input :.search {:value     filter
                             :autoFocus true
                             :ref       #(gobj/set this "input" %)
                             :onChange  #(fp/transact! this [`(filter-results ~{::filter (.. % -target -value)})])})
        (if (seq options')
          (dom/div :.options
            (cursor/vertical-cursor
              {:style              {:maxHeight "500px"}
               ::cursor/value      value
               ::cursor/options    options'
               ::cursor/on-change  on-change
               ::cursor/on-select  #(do
                                      (.stopPropagation %2)
                                      (on-select %))
               ::cursor/factory    (fn [opt]
                                     (dom/div {:className (str (:option css) " " (if (= opt value) (:option-selected css)))
                                               :onClick   #(do
                                                             (on-change opt)
                                                             (on-select opt))}
                                       (value->label opt)))
               ::cursor/value->key value->label
               ::dom-events/target #(gobj/get this "input")})))))))

(def spotlight (fp/factory Spotlight))
