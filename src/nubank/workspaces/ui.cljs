(ns nubank.workspaces.ui
  (:require [clojure.set :as set]
            [cljs.pprint]
            [cognitect.transit :as t]
            [fulcro-css.css :as css]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as fm]
            [fulcro.client.primitives :as fp]
            [goog.object :as gobj]
            [nubank.workspaces.data :as data]
            [nubank.workspaces.lib.local-storage :as local-storage]
            [nubank.workspaces.model :as wsm]
            [nubank.workspaces.ui.core :as uc]
            [nubank.workspaces.ui.events :as events]
            [nubank.workspaces.ui.grid-layout :as grid]
            [nubank.workspaces.ui.spotlight :as spotlight]
            [nubank.workspaces.ui.modal :as modal]
            [nubank.workspaces.ui.highlight :as highlight]))

(defn card-title [card-id]
  (name card-id))

(defn card-changed? [card-id]
  (not=
    (get-in @data/card-definitions-snap* [card-id ::wsm/card-form])
    (get-in @data/card-definitions* [card-id ::wsm/card-form])))

(defn use-card [card-id node reconciler]
  (if-let [active (get @data/active-cards* card-id)]
    active
    (if-let [{::wsm/keys [init] :as card-def} (data/card-definition card-id)]
      (let [card (init (merge card-def
                              {::wsm/node
                               node

                               ::wsm/reconciler
                               reconciler

                               ::wsm/set-card-header-style
                               (fn [style]
                                 (fp/transact! reconciler [::wsm/card-id card-id]
                                   [`(fm/set-props {::wsm/card-header-style ~style})]))}))]
        (swap! data/active-cards* assoc card-id card)
        card)
      (js/console.warn "Card card-id" card-id "not found"))))

(defn dispose-card [card-id]
  (when-let [{::wsm/keys [node dispose]} (get @data/active-cards* card-id)]
    (if dispose (dispose node))
    (swap! data/active-cards* dissoc card-id)))

(defn render-card [{::wsm/keys [card-id component node]}]
  (let [{::wsm/keys [render]} (use-card card-id node (fp/get-reconciler component))]
    (swap! data/active-cards* update card-id assoc
      ::wsm/node node
      ::wsm/component component)
    (render node)))

(defn refresh-card-container [card-id]
  (if-let [comp (get (data/active-card card-id) ::wsm/component)]
    (.forceUpdate comp)))

(defn restart-card [card-id]
  (let [old-card (data/active-card card-id)]
    (dispose-card card-id)
    (render-card (assoc old-card ::wsm/card-id card-id))
    (refresh-card-container card-id)))

(defn workspace-card-ids [workspace]
  (into #{} (map second) (::cards workspace)))

(defn refresh-cards
  ([cards] (refresh-cards cards true))
  ([cards check-changes?]
   (doseq [[card-id {::wsm/keys [node refresh]}] cards]
     (if (and check-changes? (card-changed? card-id))
       (restart-card card-id)
       (if refresh (refresh node))))))

(defn active-workspace-cards [reconciler]
  (let [state (-> reconciler fp/app-state deref)]
    (if-let [ref (get-in state [::workspace-tabs "singleton" ::active-workspace])]
      (let [card-ids (workspace-card-ids (get-in state ref))]
        (select-keys @data/active-cards* card-ids)))))

(defn refresh-active-workspace-cards [reconciler]
  (refresh-cards (active-workspace-cards reconciler)))

(defn refresh-active-cards []
  (refresh-cards @data/active-cards*))

(declare Workspace)

(defn lookup-ref [state ref]
  (if (vector? ref)
    (get-in state ref)
    ref))

(defn normalize-ws-cards [state ws]
  (update ws ::cards #(mapv (partial lookup-ref state) %)))

(defn create-workspace* [{:keys [reconciler state]} ws & args]
  (let [ws (fp/get-initial-state Workspace ws)]
    (apply fp/merge-component! reconciler Workspace (normalize-ws-cards @state ws)
      :append [::workspace-root "singleton" ::workspaces]
      args)
    ws))

(defn save-local-workspace [{::keys [workspace-id] :as workspace}]
  (local-storage/update! ::open-workspaces (fnil conj #{}) workspace-id)
  (local-storage/update! ::local-workspaces (fnil conj #{}) workspace-id)
  (local-storage/tset! [::workspace-id workspace-id]
    (select-keys workspace [::workspace-id ::workspace-title ::layouts]))
  workspace)

(defn active-workspace-ref [{:keys [state] :as env}]
  (if-let [ref (get-in @state [::workspace-tabs "singleton" ::active-workspace])]
    ref
    (let [ws (create-workspace* env {}
               :append [::workspace-tabs "singleton" ::open-workspaces]
               :replace [::workspace-tabs "singleton" ::active-workspace])]
      (local-storage/set! ::active-workspace (::workspace-id ws))
      (save-local-workspace ws)
      [::workspace-id (::workspace-id ws)])))

(defn map-values [f m]
  (into {} (map (fn [[k v]] [k (f v)])) m))

(defn all-referenced-cards [state]
  (reduce
    (fn [card-ids ws-ref]
      (into card-ids (workspace-card-ids (get-in state ws-ref))))
    #{}
    (get-in state [::workspace-tabs "singleton" ::open-workspaces])))

(defn disposed-unreferenced-cards [state card-ids]
  (doseq [unreferenced-card-id (set/difference card-ids (all-referenced-cards state))]
    (dispose-card unreferenced-card-id)))

(defn remove-workspace-card [workspace card-id]
  (-> workspace
      (update ::cards
        (fn [cards]
          (filterv #(not= (second %) card-id) cards)))
      (update ::layouts
        (fn [breakpoints]
          (map-values
            (fn [layouts]
              (filterv #(not= (get % "i") card-id) layouts))
            breakpoints)))))

(fm/defmutation remove-card-from-active-ns [{::wsm/keys [card-id]}]
  (action [{:keys [state] :as env}]
    (let [ws-ref     (active-workspace-ref env)
          current-ws (get-in @state ws-ref)]
      (when (contains? (workspace-card-ids current-ws) card-id)
        (swap! state update-in ws-ref #(remove-workspace-card % card-id))
        (disposed-unreferenced-cards @state #{card-id}))))
  (refresh [_] [::cards]))

(fp/defsc WorkspaceCard
  [this
   {::wsm/keys [card-id card-header-style]
    ::keys     [show-source?]
    :as        props}
   {::keys [export-size]}]
  {:initial-state     (fn [data] data)
   :ident             [::wsm/card-id ::wsm/card-id]
   :query             [::wsm/card-id ::wsm/card-header-style ::show-source?
                       {[::workspace-root "singleton"] [::settings]}]
   :css               [[:.container {:background     uc/color-white
                                     :box-shadow     "0 4px 9px 0 rgba(0,0,0,0.02)"
                                     :border-radius  uc/card-border-radius
                                     :display        "flex"
                                     :flex-direction "column"
                                     :flex           "1"
                                     :max-width      "100%"}]

                       [:$cljs-workflow-static-workflow
                        [:.header {:cursor "default"}]]

                       [:.header
                        uc/font-os12sb
                        {:background    uc/color-mystic
                         :border-radius (str uc/card-border-radius " " uc/card-border-radius " 0 0")
                         :color         uc/color-limed-spruce
                         :cursor        "grab"}
                        {:cursor "-webkit-grab"}
                        {:cursor "-moz-grab"}]

                       [:.header-title
                        {:align-items "center"
                         :display     "flex"
                         :padding     "6px 10px"
                         :box-sizing  "border-box"
                         :position    "relative"}]

                       [:.card-title
                        {:flex          "1"
                         :overflow      "hidden"
                         :text-overflow "ellipsis"
                         :white-space   "nowrap"}]

                       [:.card-actions
                        {:display        "grid"
                         :grid-auto-flow "column"
                         :align-items    "center"
                         :grid-gap       "5px"}

                        [:.close uc/close-icon-css]]

                       [:.more-container
                        {:display     "flex"
                         :align-items "center"}

                        [:&:hover
                         [:.more {:display "block"}]]]

                       [:.more
                        {:position    "absolute"
                         :display     "none"
                         :right       "0"
                         :top         "100%"
                         :margin-top  "-10px"
                         :padding-top "10px"
                         :z-index     "999"}]

                       [:.more-actions
                        {:display       "grid"
                         :background    uc/color-mystic
                         :border-radius "0 0 6px 6px"
                         :padding       "5px 10px 10px"
                         :grid-gap      "6px"}]

                       [:.toolbar
                        {:align-items     "center"
                         :background      uc/color-geyser
                         :display         "flex"
                         :justify-content "flex-end"
                         :padding         "6px"}
                        [:button {:margin-left "5px"}]]

                       [:$react-draggable-dragging
                        [:.header
                         {:cursor "grabbing"}
                         {:cursor "-webkit-grabbing"}
                         {:cursor "-moz-grabbing"}]]

                       [:$cljs-workspaces-extended-views
                        [:.card-actions:hover
                         [:button {:visibility "visible"}]]]

                       [:$cljs-workflow-static-workflow
                        [:.close {:display "none"}]]

                       [:.card
                        {:display         "flex"
                         :flex            "1"
                         :align-items     "center"
                         :justify-content "center"
                         :overflow        "auto"
                         :padding         "10px"}]

                       [:.source
                        {:background    "#fff"
                         :max-width     "80vw"
                         :max-height    "80vh"
                         :overflow      "auto"
                         :padding       "0 12px"
                         :border-radius uc/card-border-radius
                         :box-shadow    uc/box-shadow}]]
   :css-include       [highlight/Highlight modal/Modal]
   :componentDidMount (fn []
                        (let [{::wsm/keys [card-id]} (fp/props this)
                              node (gobj/get this "cardNode")]
                          (render-card {::wsm/card-id   card-id
                                        ::wsm/node      node
                                        ::wsm/component this})
                          (.forceUpdate this)))}
  (let [{::wsm/keys [render-toolbar]} (data/active-card card-id)
        {::wsm/keys [card-form test?]} (data/card-definition card-id)]
    (dom/div :.container
      (dom/div :.header$workspaces-cljs-card-drag-handle {:style (merge card-header-style
                                                                        (if (get-in props [[::workspace-root "singleton"] ::settings ::hide-card-header?])
                                                                          {:display "none"}))}
        (dom/div :.header-title
          (dom/div :.card-title {:title (str card-id)}
            (card-title card-id))
          (dom/div :.card-actions
            (if (or card-form (not test?))
              (dom/div :.more-container
                (uc/more-icon {})
                (dom/div :.more
                  (dom/div :.more-actions
                    (if card-form
                      (uc/button {:onClick #(fm/set-value! this ::show-source? true)}
                        "Source"))
                    (if-not test?
                      (uc/button {:onClick export-size} "Size"))))))
            (dom/div :.close {:onClick #(fp/transact! this [`(remove-card-from-active-ns {::wsm/card-id ~card-id})])} "×")))
        (if render-toolbar
          (dom/div :.toolbar (render-toolbar))))
      (dom/div :.card (merge-with merge
                        (::wsm/node-props (data/card-definition card-id))
                        {:ref #(gobj/set this "cardNode" %)}))
      (if show-source?
        (modal/modal {::modal/on-close #(fm/set-value! this ::show-source? false)}
          (dom/div :.source
            (highlight/highlight
              {::highlight/source
               (with-out-str
                 (cljs.pprint/pprint card-form))})))))))

(def workspace-card (fp/factory WorkspaceCard {:keyfn ::wsm/card-id}))

(defn block [w h x y] {"w" w "h" h "x" x "y" y})

(defn build-grid [items]
  (reduce
    (fn [grid {:strs [w h x y] :as item}]
      (into grid
            (for [x' (range w)
                  y' (range h)]
              [[(+ x' x) (+ y' y)] item])))
    {}
    items))

(defn fits-in? [{:strs [w h x y]} grid]
  (let [coords (for [x' (range w)
                     y' (range h)]
                 [(+ x' x) (+ y' y)])]
    (every? #(not (contains? grid %)) coords)))

(defn smart-item-position [columns {:strs [w h] :as new-item} items]
  (let [grid (build-grid items)
        w    (min w columns)]
    (loop [x 0
           y 0]
      (if (> (+ x w) columns)
        (recur 0 (inc y))
        (if-let [block (get grid [x y])]
          (recur (+ (get block "x") (get block "w")) y)
          (if (fits-in? (block w h x y) grid)
            (assoc new-item "x" x "y" y "w" w)
            (recur (inc x) y)))))))

(fm/defmutation pick-card-to-namespace [{::wsm/keys [card-id]}]
  (action [{:keys [state reconciler] :as env}]
    (let [ws-ref     (active-workspace-ref env)
          current-ws (get-in @state ws-ref)
          card       (data/card-definition card-id)]
      (if (::wsm/workspace-static? current-ws)
        (js/console.warn "Can't add card to static workspace, please duplicate the workspace to add cards.")
        (when-not (contains? (workspace-card-ids current-ws) card-id)
          (fp/merge-component! reconciler WorkspaceCard (fp/get-initial-state WorkspaceCard {::wsm/card-id card-id})
            :append (conj ws-ref ::cards))
          (swap! state update-in ws-ref update ::layouts (fn [layouts]
                                                           (reduce
                                                             (fn [l {:keys [id cols]}]
                                                               (update l id (fnil conj [])
                                                                 (smart-item-position
                                                                   cols
                                                                   {"i"    card-id
                                                                    "w"    (or (::wsm/card-width card) 2)
                                                                    "h"    (or (::wsm/card-height card) 4)
                                                                    "x"    0
                                                                    "y"    100
                                                                    "minH" 2}
                                                                   (get l id []))))
                                                             layouts
                                                             grid/breakpoints))))))))

(defn add-card [this card-id]
  (fp/transact! this [`(pick-card-to-namespace {::wsm/card-id ~card-id})]))

(defn normalize-layout [layout]
  (mapv #(-> (into {} (filter (fn [[_ v]] v)) %)
             (update "i" symbol))
    layout))

(fm/defmutation normalize-sizes [_]
  (action [{:keys [state ref]}]
    (let [ws    (get-in @state ref)
          items (-> ws ::layouts (get (::breakpoint ws))
                    (->> (group-by #(get % "i"))
                         (map-values first)))]
      (swap! state update-in ref update ::layouts
        (fn [breaks]
          (map-values
            (fn [layouts]
              (mapv #(if-let [{:strs [w h x y]} (get items (get % "i"))]
                       (assoc % "w" w "h" h "x" x "y" y)
                       %) layouts))
            breaks)))
      (save-local-workspace (get-in @state ref)))))

(fm/defmutation update-workspace [{::keys     [workspace-id]
                                   ::wsm/keys [workspace-static?]
                                   :as        ws}]
  (action [{:keys [state]}]
    (swap! state update-in [::workspace-id workspace-id] merge ws)
    (if-not workspace-static?
      (save-local-workspace (get-in @state [::workspace-id workspace-id]))))
  (refresh [_] [::workspace-id ::workspaces]))

(fm/defmutation close-workspace [{::keys [workspace-id]}]
  (action [{:keys [state]}]
    (let [ws       (get-in @state [::workspace-id workspace-id])
          card-ids (workspace-card-ids ws)
          tabs-ref [::workspace-tabs "singleton"]]
      (swap! state update-in tabs-ref update ::open-workspaces
        #(filterv (fn [x] (not= (second x) workspace-id)) %))
      (if (= (get-in @state (conj tabs-ref ::active-workspace))
             [::workspace-id workspace-id])
        (let [active-ref (-> (get-in @state tabs-ref) ::open-workspaces first)]
          (swap! state update-in tabs-ref assoc ::active-workspace
            (-> (get-in @state tabs-ref) ::open-workspaces first))
          (local-storage/set! ::active-workspace (second active-ref))))
      (local-storage/update! ::open-workspaces disj workspace-id)
      (disposed-unreferenced-cards @state card-ids))))

(fm/defmutation remove-workspace [{::keys [workspace-id]}]
  (action [{:keys [state]}]
    (swap! state update-in [::workspace-root "singleton" ::workspaces]
      #(filterv (fn [x] (not= (second x) workspace-id)) %))
    (swap! state update ::workspace-id dissoc workspace-id)
    (local-storage/update! ::local-workspaces disj workspace-id)
    (local-storage/remove! [::workspace-id workspace-id]))
  (refresh [_] [::workspaces]))

(fm/defmutation create-workspace [ws]
  (action [{:keys [ref] :as env}]
    (let [{::keys [workspace-id]}
          (-> (create-workspace* env (or ws {})
                :append (conj ref ::open-workspaces)
                :replace (conj ref ::active-workspace))
              (save-local-workspace))]
      (local-storage/set! ::active-workspace workspace-id))))

(fm/defmutation copy-breakpoint-layout [{::keys [source-breakpoint]}]
  (action [{:keys [state ref]}]
    (let [{::keys [breakpoint layouts]} (get-in @state ref)]
      (swap! state update-in ref assoc-in [::layouts breakpoint]
        (get layouts source-breakpoint)))))

(defn export-card-size [this card-id]
  (let [{::keys [layouts breakpoint]} (fp/props this)
        {:strs [w h]} (->> (get layouts breakpoint)
                           (filter #(= card-id (get % "i")))
                           first)]
    (js/console.log (str "{::wsm/card-width " w " ::wsm/card-height " h "}"))))

(fp/defsc Workspace
  [this {::keys     [workspace-id cards layouts breakpoint workspace-title]
         ::wsm/keys [workspace-static?]}]
  {:initial-state     (fn [{::keys [layouts workspace-title workspace-id] :as ws}]
                        (let [layouts (or layouts {})]
                          (merge ws
                                 {::workspace-id    (or workspace-id (random-uuid))
                                  ::workspace-title (or workspace-title "new workspace")
                                  ::cards           (or (some->> layouts first val
                                                          (mapv #(vector ::wsm/card-id (get % "i"))))
                                                        [])
                                  ::layouts         layouts
                                  ::breakpoint      ""})))
   :ident             [::workspace-id ::workspace-id]
   :query             [::workspace-id ::layouts ::breakpoint
                       ::workspace-title ::wsm/workspace-static?
                       {::cards (fp/get-query WorkspaceCard)}]
   :css               [[:$workspaces-workspace-container {:background "#9fa2ab"
                                                          :flex       "1"}]
                       [:.container {:display        "flex"
                                     :flex           "1"
                                     :flex-direction "column"}]
                       [:.grid {:flex       "1"
                                :overflow-y "scroll"
                                :overflow-x "hidden"}]
                       [:.tools {:background  uc/color-white
                                 :color       uc/color-limed-spruce
                                 :padding     "5px 9px"
                                 :display     "flex"
                                 :align-items "center"}
                        [:button {:margin-left "5px"}]]
                       [:.breakpoint {:flex "1"}]]
   :css-include       [grid/GridLayout WorkspaceCard]
   :componentDidMount (fn [] (js/requestAnimationFrame #(fp/set-state! this {:render? true})))}

  (dom/div :.container$workspaces-workspace-container
    (dom/div :.tools
      (dom/div :.breakpoint (str breakpoint))
      (if-not workspace-static?
        (dom/select {:value    "-"
                     :onChange (fn [e]
                                 (fp/transact! this [`(copy-breakpoint-layout ~{::source-breakpoint (.. e -target -value)})])
                                 (gobj/set (.-target e) "selectedIndex" 0))}
          (dom/option {:value "-"} "Copy layout")
          (for [{:keys [id]} grid/breakpoints]
            (dom/option {:key id :value id} id))))
      (uc/button {:onClick #(refresh-cards (active-workspace-cards (fp/get-reconciler this)) false)} "Refresh cards")
      (uc/button {:onClick #(fp/transact! (fp/get-reconciler this) [::workspace-tabs "singleton"]
                              [`(create-workspace ~{::workspace-title (str workspace-title " copy")
                                                    ::layouts         layouts})])} "Duplicate")
      (if-not workspace-static?
        (uc/button {:onClick #(fp/transact! this [`(normalize-sizes {})])} "Unify layouts"))
      (if-not workspace-static?
        (uc/button {:onClick #(js/console.log (let [writer (t/writer :json)]
                                                (pr-str (t/write writer layouts))))} "Export"))
      (if-not workspace-static?
        (uc/button {:onClick #(when (js/confirm "Delete workspace?")
                                (fp/transact! this [`(close-workspace {::workspace-id ~workspace-id})])
                                (fp/transact! this [`(remove-workspace {::workspace-id ~workspace-id})]))} "Delete")))

    (dom/div :.grid
      (if (fp/get-state this :render?)
        (grid/grid-layout
          (cond->
            {:className          (str "layout " (if workspace-static? "cljs-workflow-static-workflow"))
             :rowHeight          30
             :breakpoints        (into {} (map (juxt :id :breakpoint)) grid/breakpoints)
             :cols               (into {} (map (juxt :id :cols)) grid/breakpoints)
             :layouts            layouts
             :draggableHandle    ".workspaces-cljs-card-drag-handle"
             :onBreakpointChange (fn [bp _]
                                   (fm/set-value! this ::breakpoint bp))
             :onLayoutChange     (fn [_ layouts]
                                   (let [layouts' (->> (js->clj layouts)
                                                       (into {} (map (fn [[k v]] [k (normalize-layout v)]))))]
                                     (fp/transact! this [`(update-workspace ~{::workspace-id workspace-id
                                                                              ::layouts      layouts'})])))}

            workspace-static?
            (assoc :isDraggable false :isResizable false
                   :onLayoutChange (fn [_ _])))
          (for [{::wsm/keys [card-id] :as card} cards
                :when card-id]
            (dom/div {:key (str card-id)}
              (workspace-card (fp/computed card {::export-size #(export-card-size this card-id)})))))))))

(def workspace (fp/factory Workspace {:keyfn ::workspace-id}))

(fp/defsc WorkspaceTabItem [_ _]
  {:ident [::workspace-id ::workspace-id]
   :query [::workspace-id ::workspace-title ::wsm/workspace-static?]})

(fp/defsc WorkspaceTabs
  [this {::keys [active-workspace open-workspaces]}]
  {:initial-state (fn [_]
                    (let [ws (fp/get-initial-state Workspace {})]
                      {::open-workspaces  (->> (local-storage/get ::open-workspaces [])
                                               (mapv #(-> {::workspace-id %})))
                       ::active-workspace (if-let [active (local-storage/get ::active-workspace)]
                                            {::workspace-id active})}))
   :ident         (fn [] [::workspace-tabs "singleton"])
   :query         [{::open-workspaces (fp/get-query WorkspaceTabItem)}
                   {::active-workspace (fp/get-query Workspace)}]
   :css           [[:.container {:display        "flex"
                                 :flex           "1"
                                 :flex-direction "column"
                                 :max-width      "100%"}]
                   [:.tabs {:display    "flex"
                            :flex-wrap  "nowrap"
                            :overflow-x "auto"}]
                   [:.tab
                    uc/font-os12sb
                    {:background    uc/color-iron
                     :border        (str "1px solid " uc/color-geyser)
                     :border-radius "6px 6px 0 0"
                     :color         uc/color-limed-spruce
                     :cursor        "pointer"
                     :display       "flex"
                     :flex          "0 0 auto"
                     :align-items   "center"
                     :margin-right  "1px"
                     :margin-bottom "-1px"
                     :overflow      "hidden"
                     :padding       "7px 12px 9px"
                     :z-index       "1"}
                    [:&.active-tab {:background    uc/color-white
                                    :border-bottom (str "1px solid " uc/color-white)}]]
                   [:.active {:border     (str "1px solid " uc/color-geyser)
                              :display    "flex"
                              :flex       "1"
                              :min-height "0"}]
                   [:.new-tab {:font-size   "23px"
                               :line-height "1em"}]
                   [:.welcome {:background      uc/color-dark-grey
                               :color           "#fff"
                               :flex            "1"
                               :display         "flex"
                               :align-items     "center"
                               :justify-content "center"}]
                   [:.workspace-title
                    uc/font-os12sb
                    {:flex          "1"
                     :background    "transparent"
                     :border        "1px solid transparent"
                     :box-shadow    "0 0 2px 0 transparent"
                     :cursor        "pointer"
                     :text-overflow "ellipsis"}
                    [:&:focus {:background "#fff"
                               :border     "1px solid #0079bf"
                               :box-shadow "0 0 2px 0 #0284c6"
                               :outline    "0"
                               :color      "#000 !important"
                               :cursor     "text"}]]
                   [:.workspace-close
                    uc/close-icon-css
                    {:margin-left "10px"}]]
   :css-include   [Workspace]}
  (let [update-title
        (fn [new-title workspace-id]
          (fp/transact! this [`(update-workspace ~{::workspace-id    workspace-id
                                                   ::workspace-title new-title})]))]
    (dom/div :.container
      (events/dom-listener {::events/keystroke "alt-shift-w"
                            ::events/action    #(fp/transact! this [`(close-workspace ~active-workspace)])})
      (events/dom-listener {::events/keystroke "alt-shift-n"
                            ::events/action    #(fp/transact! this [`(create-workspace {})])})
      (dom/div :.tabs
        (for [{::keys     [workspace-id workspace-title]
               ::wsm/keys [workspace-static?]} (sort-by ::workspace-title open-workspaces)
              :let [current? (= workspace-id (::workspace-id active-workspace))]]
          (dom/div :.tab {:key     workspace-id
                          :classes [(if current? :.active-tab)]}
            (if (or workspace-static? (not current?))
              (dom/div :.workspace-title
                {:onClick (fn []
                            (fm/set-value! this ::active-workspace [::workspace-id workspace-id])
                            (local-storage/set! ::active-workspace workspace-id))}
                (str workspace-title))
              (dom/input :.workspace-title {:value     (str workspace-title)
                                            :onChange  (fn [_])
                                            :onClick   #(.select (.-target %))
                                            :onBlur    #(update-title (.. % -target -value) workspace-id)
                                            :onKeyDown #(if (contains? #{(get events/KEYS "escape") (get events/KEYS "return")} (.-keyCode %))
                                                          (.blur (.-target %)))}))
            (dom/div :.workspace-close {:onClick #(fp/transact! this [`(close-workspace {::workspace-id ~workspace-id})])}
              "×")))
        (dom/div :.tab.new-tab {:onClick #(fp/transact! this [`(create-workspace {})])}
          "+"))
      (dom/div :.active
        (if active-workspace
          (workspace active-workspace)
          (dom/div :.welcome "Go Navigate"))))))

(def workspace-tabs (fp/factory WorkspaceTabs))

(fp/defsc CardIndexListing
  [this {::wsm/keys [card-id]}]
  {:initial-state (fn [card]
                    (select-keys card [::wsm/card-id ::wsm/test? ::wsm/card-unlisted?]))
   :ident         [::wsm/card-id ::wsm/card-id]
   :query         [::wsm/card-id ::wsm/test? ::wsm/card-unlisted?]
   :css           [[:.container {:cursor "pointer"}]]
   :css-include   []}
  (dom/div :.container
    (dom/div {:onClick #(add-card this card-id)}
      (name card-id))))

(def card-index-listing (fp/factory CardIndexListing {:keyfn ::wsm/card-id}))

(fp/defsc WorkspaceIndexListing
  [this {::keys []}]
  {:initial-state (fn [_]
                    {})
   :ident         [::workspace-id ::workspace-id]
   :query         [::workspace-id ::workspace-title ::wsm/workspace-static?]
   :css           []
   :css-include   []}
  (dom/div))

(def workspace-index-listing (fp/factory WorkspaceIndexListing {:keyfn ::workspace-id}))

(fm/defmutation load-card [card]
  (action [{:keys [reconciler]}]
    (fp/merge-component! reconciler CardIndexListing (fp/get-initial-state CardIndexListing card)
      :append [::workspace-root "singleton" ::cards])))

(defn initialize-static-workspaces []
  (mapv #(fp/get-initial-state Workspace %) (vals @data/workspace-definitions*)))

(fm/defmutation load-workspace [workspace]
  (action [{:keys [reconciler state]}]
    (fp/merge-component! reconciler Workspace (normalize-ws-cards @state (fp/get-initial-state Workspace workspace))
      :append [::workspace-root "singleton" ::workspaces])))

(fm/defmutation select-workspace [{::keys [workspace-id]}]
  (action [{:keys [state]}]
    (let [open-workspaces (->> (get-in @state [::workspace-tabs "singleton" ::open-workspaces])
                               (into #{} (map second)))
          ws-ref          [::workspace-id workspace-id]]
      (if-not (contains? open-workspaces workspace-id)
        (swap! state update-in [::workspace-tabs "singleton" ::open-workspaces] conj ws-ref))
      (swap! state assoc-in [::workspace-tabs "singleton" ::active-workspace] ws-ref)
      (local-storage/update! ::open-workspaces (fnil conj #{}) workspace-id)
      (local-storage/set! ::active-workspace workspace-id)))
  (refresh [_] [::active-workspace]))

(fm/defmutation toggle-ns-expansion [{::keys [expand-path]}]
  (action [{:keys [state ref]}]
    (swap! state update-in ref update ::expanded update-in expand-path not)
    (local-storage/set! ::expanded (get-in @state (conj ref ::expanded)))))

(fm/defmutation toggle-index-view [_]
  (action [{:keys [state ref]}]
    (let [show-index? (get-in @state (conj ref ::settings ::show-index?))]
      (swap! state assoc-in (conj ref ::settings ::show-index?) (not show-index?))
      (local-storage/set! ::show-index? (not show-index?))
      (js/setTimeout #(events/trigger-event js/window {::events/event "resize"}) 100))))

(defn open-spotlight [this]
  (let [{::keys [spotlight]} (fp/props this)
        state   (-> (fp/get-reconciler this) fp/app-state deref)
        options (into [] (map (fn [[_ {::wsm/keys [card-id test?]}]]
                                {::spotlight/type (if test? ::spotlight/test ::spotlight/card)
                                 ::spotlight/id   card-id}))
                      (::wsm/card-id state))]
    (fp/transact! (fp/get-reconciler this) (fp/get-ident spotlight/Spotlight spotlight)
      `[(spotlight/reset {::spotlight/options ~options})])
    (fm/set-value! this ::show-spotlight? true)))

(fp/defsc WorkspacesRoot
  [this {::keys [cards ws-tabs workspaces settings expanded spotlight
                 show-spotlight? extended-views?]}]
  {:initial-state (fn [card-definitions]
                    {::cards           (mapv #(fp/get-initial-state CardIndexListing %)
                                         (vals card-definitions))
                     ::workspaces      (->> (local-storage/get ::local-workspaces [])
                                            (mapv #(fp/get-initial-state Workspace
                                                     (local-storage/tget [::workspace-id %])))
                                            (into (initialize-static-workspaces)))

                     ::expanded        (local-storage/get ::expanded {})
                     ::ws-tabs         (fp/get-initial-state WorkspaceTabs {})

                     ::spotlight       (fp/get-initial-state spotlight/Spotlight [])
                     ::show-spotlight? false
                     ::extended-views? false
                     ::settings        {::show-index? (local-storage/get ::show-index? true)}})
   :ident         (fn [] [::workspace-root "singleton"])
   :query         [::settings ::expanded ::show-spotlight? ::extended-views?
                   {::cards (fp/get-query CardIndexListing)}
                   {::workspaces (fp/get-query WorkspaceIndexListing)}
                   {::ws-tabs (fp/get-query WorkspaceTabs)}
                   {::spotlight (fp/get-query spotlight/Spotlight)}]
   :css           [[:body {:margin     0
                           :background "#f7f7f7"
                           :overflow   "hidden"}]
                   [:.container {:box-sizing "border-box"
                                 :display    "flex"
                                 :width      "100vw"
                                 :height     "100vh"
                                 :padding    "10px"}]
                   [:.menu {:padding-right "10px"
                            :font-family   uc/font-open-sans
                            :flex-shrink   "0"
                            :overflow      "auto"
                            :min-width     "300px"}]
                   [:.workspaces {:display    "flex"
                                  :flex       "1"
                                  :max-height "100vh"
                                  :overflow   "hidden"}]
                   [:.workspaces-solo {:max-width "100%"}]
                   [:.workspace {:cursor "pointer"}]
                   [:.nest-group {:margin-left "32px"}]
                   [:.nest-group-small {:margin-left "18px"}]
                   [:.ns-header {:display "flex" :align-items "center"}]
                   [:.expand-arrow {:margin-right "5px"
                                    :cursor       "pointer"
                                    :font-size    "14px"}]]
   :css-include   [WorkspaceTabs WorkspaceIndexListing CardIndexListing spotlight/Spotlight uc/CSS]}
  (dom/div :.container {:classes [(if extended-views? :$cljs-workspaces-extended-views)]}
    (css/style-element WorkspacesRoot)
    (events/dom-listener {::events/keystroke "alt-shift-i"
                          ::events/action    #(fp/transact! this [`(toggle-index-view {})])})
    (events/dom-listener {::events/keystroke "alt-shift-s"
                          ::events/action    #(events/trigger-event js/window {::events/event "resize"})})
    (events/dom-listener {::events/keystroke "alt-shift-h"
                          ::events/action    #(fm/set-value! this ::settings (update (::settings (fp/props this)) ::hide-card-header? not))})
    (events/dom-listener {::events/keystroke "alt-shift-a"
                          ::events/action    (events/pd #(open-spotlight this))})
    (events/dom-listener {::events/event  "keydown"
                          ::events/action #(if (= (.-keyCode %) 18)
                                             (fm/set-value! this ::extended-views? true))})
    (events/dom-listener {::events/event  "keyup"
                          ::events/action #(if (= (.-keyCode %) 18)
                                             (fm/set-value! this ::extended-views? false))})

    (if show-spotlight?
      (modal/modal {::modal/on-close #(fm/set-value! this ::show-spotlight? false)}
        (spotlight/spotlight
          (fp/computed spotlight
            {::spotlight/on-select (fn [{::spotlight/keys [id type]}]
                                     (if id
                                       (cond
                                         (= type ::spotlight/workspace)
                                         (fp/transact! this [`(select-workspace {::workspace-id ~id})])

                                         :else
                                         (add-card this id)))

                                     (fm/set-value! this ::show-spotlight? false))}))))

    (when (::show-index? settings)
      (let [{uis false tests true} (group-by (comp true? ::wsm/test?) cards)]
        (dom/div :.menu
          "WORKSPACES"
          (let [{statics true locals false} (group-by (comp boolean ::wsm/workspace-static?) workspaces)]
            (dom/div
              (dom/div
                "Local workspaces"
                (dom/div :.nest-group-small
                  (for [{::keys [workspace-id workspace-title]} (sort-by ::workspace-title locals)]
                    (dom/div :.workspace {:key     (str workspace-id)
                                          :onClick #(fp/transact! this [`(select-workspace {::workspace-id ~workspace-id})])}
                      (str workspace-title)))))

              (dom/br)

              (for [[ns workspaces] (->> (group-by (comp namespace ::workspace-id) statics)
                                         (sort-by first))]
                (dom/div {:key (str ns)}
                  (str ns)
                  (dom/div :.nest-group-small
                    (for [{::keys [workspace-id workspace-title]} (sort-by ::workspace-title workspaces)]
                      (dom/div :.workspace {:key     (str workspace-id)
                                            :onClick #(fp/transact! this [`(select-workspace {::workspace-id ~workspace-id})])}
                        (name (symbol workspace-title)))))))))

          (dom/br)

          "CARDS"
          (for [[ns cards] (->> (group-by (comp namespace ::wsm/card-id) uis)
                                (sort-by first))]
            (dom/div {:key (str ns)}
              (dom/div :.ns-header
                (dom/div :.expand-arrow {:onClick #(fp/transact! this [`(toggle-ns-expansion {::expand-path ~[:card-ns ns]})])}
                  (if (get-in expanded [:card-ns ns])
                    uc/arrow-down
                    uc/arrow-right))
                (str ns))
              (if (get-in expanded [:card-ns ns])
                (dom/div :.nest-group
                  (mapv card-index-listing (sort-by ::wsm/card-id cards))))))

          (dom/br)

          "TESTS"
          (for [[ns cards] (->> tests
                                (remove ::wsm/card-unlisted?)
                                (group-by (comp namespace ::wsm/card-id))
                                (sort-by first))]
            (dom/div {:key (str ns)}
              (dom/div :.ns-header
                (dom/div :.expand-arrow {:onClick #(fp/transact! this [`(toggle-ns-expansion {::expand-path ~[:test-ns ns]})])}
                  (if (get-in expanded [:test-ns ns])
                    uc/arrow-down
                    uc/arrow-right))
                (card-index-listing {::wsm/card-id (symbol ns)}))

              (if (get-in expanded [:test-ns ns])
                (dom/div :.nest-group
                  (mapv card-index-listing (sort-by ::wsm/card-id cards)))))))))
    (dom/div :.workspaces
      (workspace-tabs ws-tabs))))

(def workspaces-root (fp/factory WorkspacesRoot))
