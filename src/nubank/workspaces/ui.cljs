(ns nubank.workspaces.ui
  (:require [clojure.set :as set]
            [cljs.pprint]
            [cognitect.transit :as t]
            [fulcro-css.css-injection :as cssi]
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

(defonce components-with-error (atom #{}))

;region helpers
(def default-bindings
  {::keybinding-toggle-index        "alt-shift-i"
   ::keybinding-spotlight           "alt-shift-a"
   ::keybinding-toggle-card-headers "alt-shift-h"
   ::keybinding-new-workspace       "alt-shift-n"
   ::keybinding-close-workspace     "alt-shift-w"
   ::keybinding-fix-sizes           "alt-shift-s"})

(defn get-keybinding [name]
  (local-storage/get name (get default-bindings name)))

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

(defn workspace-card-ids [{::keys [cards] ::wsm/keys [card-id]}]
  (if cards
    (into #{} (map second) cards)
    #{card-id}))

(defn refresh-cards
  ([cards] (refresh-cards cards true))
  ([cards check-changes?]
   (doseq [[card-id {::wsm/keys [node refresh]}] cards]
     (try
       (if (and check-changes? (card-changed? card-id))
         (restart-card card-id)
         (if refresh (refresh node)))
       (catch :default e
         (js/console.error "Error refreshing card" card-id e))))

   (doseq [comp @components-with-error]
     (fp/set-state! comp {::error-catch? false}))

   (reset! components-with-error #{})))

(defn active-workspace-cards [reconciler]
  (if-let [state (some-> reconciler fp/app-state deref)]
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
;endregion

(fm/defmutation remove-card-from-active-ns [{::wsm/keys [card-id]}]
  (action [{:keys [state] :as env}]
    (let [ws-ref     (active-workspace-ref env)
          current-ws (get-in @state ws-ref)]
      (when (contains? (workspace-card-ids current-ws) card-id)
        (swap! state update-in ws-ref #(remove-workspace-card % card-id))
        (disposed-unreferenced-cards @state #{card-id}))))
  (refresh [_] [::cards]))

(fp/defsc WorkspaceSoloCard
  [this {::wsm/keys [card-id]}]
  {:initial-state     (fn [data] data)
   :ident             [::wsm/card-id ::wsm/card-id]
   :query             [::wsm/card-id]
   :css               [[:.container {:color-scheme   (uc/color ::uc/card-default-color-scheme)
                                     :background     (uc/color ::uc/card-bg)
                                     :box-shadow     "0 4px 9px 0 rgba(0,0,0,0.02)"
                                     :border-radius  uc/card-border-radius
                                     :display        "flex"
                                     :flex-direction "column"
                                     :flex           "1"
                                     :max-width      "100%"}]

                       [:.toolbar
                        uc/font-os12sb
                        {:display         "flex"
                         :align-items     "center"
                         :justify-content "flex-end"
                         :padding         "6px"
                         :background      (uc/color ::uc/card-toolbar-bg)
                         :color           (uc/color ::uc/card-toolbar-default-text)}
                        [:button {:margin-left "5px"}]]

                       [:.error {:color       (uc/color ::uc/error-text-color)
                                 :font-weight "bold"
                                 :padding     "10px"}]

                       [:.card
                        {:display         "flex"
                         :flex            "1"
                         :align-items     "center"
                         :justify-content "center"
                         :overflow        "auto"
                         :padding         "10px"
                         :background      (uc/color ::uc/card-default-bg)
                         :color           (uc/color ::uc/card-default-text)}]]

   :componentDidMount (fn []
                        (let [{::wsm/keys [card-id]} (fp/props this)
                              node (gobj/get this "cardNode")]
                          (try
                            (render-card {::wsm/card-id   card-id
                                          ::wsm/node      node
                                          ::wsm/component this})
                            (.forceUpdate this)
                            (catch :default e
                              (swap! components-with-error conj this)
                              (js/console.error "Error mounting card" card-id e)
                              (fp/set-state! this {::error-catch? true})))))}

  (let [{::wsm/keys [render-toolbar]} (data/active-card card-id)]
    (dom/div :.container
      (if render-toolbar
        (dom/div :.toolbar (render-toolbar))
        (dom/div))
      (if (fp/get-state this ::error-catch?)
        (dom/div :.error "Error rendering card, check console for details."))
      (dom/div :.card (merge-with merge
                        (::wsm/node-props (data/card-definition card-id))
                        {:ref #(gobj/set this "cardNode" %)})))))

(def workspace-solo-card (fp/factory WorkspaceSoloCard {:keyfn ::wsm/card-id}))

(fp/defsc WorkspaceCard
  [this
   {::wsm/keys [card-id card-header-style]
    ::keys     [show-source?]
    :as        props}
   {::keys [export-size open-solo-card]}]
  {:initial-state     (fn [data] data)
   :ident             [::wsm/card-id ::wsm/card-id]
   :query             [::wsm/card-id ::wsm/card-header-style ::show-source?
                       {[::workspace-root "singleton"] [::settings]}]
   :css               [[:.container {:color-scheme   (uc/color ::uc/card-default-color-scheme)
                                     :background     (uc/color ::uc/card-bg)
                                     :box-shadow     "0 4px 9px 0 rgba(0,0,0,0.08)"
                                     :border-radius  uc/card-border-radius
                                     :display        "flex"
                                     :flex-direction "column"
                                     :flex           "1"
                                     :max-width      "100%"}]

                       [:$cljs-workflow-static-workflow
                        [:.header {:cursor "default"}]]

                       [:.error {:color       (uc/color ::uc/error-text-color)
                                 :font-weight "bold"
                                 :padding     "10px"}]

                       [:.header
                        uc/font-os12sb
                        {:background    (uc/color ::uc/card-header-bg)
                         :border-radius (str uc/card-border-radius " " uc/card-border-radius " 0 0")
                         :color         (uc/color ::uc/card-header-text)
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
                         :background    (uc/color ::uc/card-ellipsis-menu-bg)
                         :border-radius "0 0 6px 6px"
                         :padding       "5px 10px 10px"
                         :grid-gap      "6px"}]

                       [:.toolbar
                        {:display         "flex"
                         :align-items     "center"
                         :justify-content "flex-end"
                         :background      (uc/color ::uc/card-toolbar-bg)
                         :padding         "6px"
                         :color           (uc/color ::uc/card-toolbar-default-text)}
                        [:button {:margin-left "5px"}]]

                       [:$react-draggable-dragging
                        [:.header
                         {:cursor "grabbing"}
                         {:cursor "-webkit-grabbing"}
                         {:cursor "-moz-grabbing"}]]

                       [:$cljs-workflow-static-workflow
                        [:.close {:display "none"}]]

                       [:.card
                        {:display         "flex"
                         :flex            "1"
                         :align-items     "center"
                         :justify-content "center"
                         :overflow        "auto"
                         :padding         "10px"
                         :background      (uc/color ::uc/card-default-bg)
                         :color           (uc/color ::uc/card-default-text)}]

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
                          (try
                            (render-card {::wsm/card-id   card-id
                                          ::wsm/node      node
                                          ::wsm/component this})
                            (.forceUpdate this)
                            (catch :default e
                              (swap! components-with-error conj this)
                              (js/console.error "Error mounting card" card-id e)
                              (fp/set-state! this {::error-catch? true})))))}
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
            (dom/div :.more-container
              (uc/more-icon {})
              (dom/div :.more
                (dom/div :.more-actions
                  (if card-form
                    (uc/button {:onClick #(fm/set-value! this ::show-source? true)}
                      "Source"))
                  (uc/button {:onClick #(open-solo-card {::wsm/card-id card-id})} "Solo")
                  (if-not test?
                    (uc/button {:onClick export-size} "Size"))
                  (uc/button {:onClick #(restart-card card-id)} "Remount"))))
            (dom/div :.close {:onClick #(fp/transact! this [`(remove-card-from-active-ns {::wsm/card-id ~card-id})])} "×")))
        (if render-toolbar
          (dom/div :.toolbar (render-toolbar))))
      (if (fp/get-state this ::error-catch?)
        (dom/div :.error "Error rendering card, check console for details."))
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
      (cond
        (::wsm/workspace-static? current-ws)
        (js/console.warn "Can't add card to static workspace, please duplicate the workspace to add cards.")

        (= ::wsm/card-id (first ws-ref))
        (js/console.warn "Can't add card to solo card, please switch a local workspace.")

        :else
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

(fm/defmutation open-solo-workspace [{::wsm/keys [card-id]}]
  (action [{:keys [state ref]}]
    (let [ws-ident [::wsm/card-id card-id]]
      (fp/integrate-ident! state ws-ident
        :append (conj ref ::open-workspaces)
        :replace (conj ref ::active-workspace))
      (local-storage/update! ::open-workspaces (fnil conj #{}) ws-ident)
      (local-storage/set! ::active-workspace ws-ident))))

(defn add-card-solo [this card-id]
  (fp/transact! (fp/get-reconciler this) [::workspace-tabs "singleton"] [`(open-solo-workspace ~{::wsm/card-id card-id})]))

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

(defn ws-data->ident-map [x]
  (if (vector? x)
    (apply hash-map x)
    {::workspace-id x}))

(defn workspace-id? [x]
  (or (uuid? x) (symbol? x)))

(defn workspace-ident [{::keys [workspace-id] ::wsm/keys [card-id]}]
  (cond
    (workspace-id? workspace-id) [::workspace-id workspace-id]
    card-id [::wsm/card-id card-id]
    :else [:invalid "ident"]))

(fm/defmutation close-workspace [{::keys [workspace-id] :as ws-data}]
  (action [{:keys [state]}]
    (let [ws-ref   (workspace-ident ws-data)
          ws       (get-in @state ws-ref)
          card-ids (workspace-card-ids ws)
          tabs-ref [::workspace-tabs "singleton"]]
      (swap! state update-in tabs-ref update ::open-workspaces
        #(filterv (fn [x] (not= x ws-ref)) %))
      (if (= (get-in @state (conj tabs-ref ::active-workspace))
             ws-ref)
        (let [active-ref (-> (get-in @state tabs-ref) ::open-workspaces first)]
          (swap! state update-in tabs-ref assoc ::active-workspace
            (-> (get-in @state tabs-ref) ::open-workspaces first))
          (local-storage/set! ::active-workspace active-ref)))
      (local-storage/update! ::open-workspaces disj workspace-id ws-ref)
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
  [this
   {::keys     [workspace-id cards layouts breakpoint workspace-title]
    ::wsm/keys [workspace-static?]}
   {::keys [open-solo-card]}]
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
   :css               [[:.container {:display        "flex"
                                     :flex           "1"
                                     :flex-direction "column"
                                     :font-size      "12px"}]
                       [:.grid {:flex       "1"
                                :overflow-y "auto"
                                :overflow-x "hidden"}]
                       [:.tools {:background  (uc/color ::uc/workspace-tools-bg)
                                 :color       (uc/color ::uc/workspace-tools-color)
                                 :padding     "8px 10px"
                                 :display     "flex"
                                 :align-items "center"}
                        [:select {:height      "24px"
                                  :font-size   "12px"
                                  :font-weight "600"}]
                        [:button {:font-size   "12px"
                                  :line-height "2"
                                  :margin-left "10px"
                                  :padding     "0 8px"}]]
                       [:.breakpoint {:flex "1"}]]

   :css-include       [grid/GridLayout]

   :componentDidCatch (fn [error info]
                        (swap! components-with-error conj this)
                        (fp/set-state! this {::error-catch? true}))
   :componentDidMount (fn [] (js/requestAnimationFrame #(fp/set-state! this {:render? true})))}

  (if (fp/get-state this ::error-catch?)
    (dom/div "Some error leaked to workspace level (ugh...), please report this and check console for details.")
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
                (workspace-card (fp/computed card {::export-size    #(export-card-size this card-id)
                                                   ::open-solo-card open-solo-card}))))))))))

(def workspace (fp/factory Workspace {:keyfn ::workspace-id}))

(fp/defsc WorkspaceContainer
  [this props {::keys [open-solo-card]}]
  {:ident             (fn [] (workspace-ident props))
   :query             (fn []
                        {::workspace-id (fp/get-query Workspace)
                         ::wsm/card-id  (fp/get-query WorkspaceSoloCard)})
   :css               [[:$workspaces-workspace-container {:background (uc/color ::uc/workspace-bg)
                                                          :flex       "1"}]
                       [:.error {:color       (uc/color ::uc/error-text-color)
                                 :font-weight "bold"
                                 :padding     "10px"}]]

   :componentDidCatch (fn [error info]
                        (swap! components-with-error conj this)
                        (fp/set-state! this {::error-catch? true}))}

  (if (fp/get-state this ::error-catch?)
    (dom/div :.error "Error rendering workspace, check console for details.")
    (case (first (fp/get-ident this))
      ::workspace-id (workspace (fp/computed props {::open-solo-card open-solo-card}))
      ::wsm/card-id (workspace-solo-card props))))

(def workspace-container (fp/factory WorkspaceContainer {:keyfn #(or (::workspace-id %) (::wsm/card-id %))}))

(fp/defsc WorkspaceTabItem [_ props]
  {:ident (fn [] (workspace-ident props))
   :query [::workspace-id ::workspace-title ::wsm/workspace-static? ::wsm/card-id]})

(fp/defsc WorkspaceTabs
  [this {::keys [active-workspace open-workspaces]}]
  {:initial-state (fn [_]
                    {::open-workspaces  (->> (local-storage/get ::open-workspaces [])
                                             (mapv ws-data->ident-map))
                     ::active-workspace (if-let [active (local-storage/get ::active-workspace)]
                                          (ws-data->ident-map active))})
   :ident         (fn [] [::workspace-tabs "singleton"])
   :query         [{::open-workspaces (fp/get-query WorkspaceTabItem)}
                   {::active-workspace (fp/get-query WorkspaceContainer)}]
   :css           [[:.container {:display        "flex"
                                 :flex           "1"
                                 :flex-direction "column"
                                 :max-width      "100%"}]
                   [:.tabs {:display    "flex"
                            :flex-wrap  "nowrap"
                            :overflow-x "auto"
                            :overflow-y "hidden"}]
                   [:.tab
                    uc/font-os12sb
                    {:background    (uc/color ::uc/tab-bg)
                     :border-top    (str "1px solid " (uc/color ::uc/tab-border))
                     :border-right  (str "1px solid " (uc/color ::uc/tab-border))
                     :border-left   (str "1px solid " (uc/color ::uc/tab-border))
                     :border-radius "4px 4px 0 0"
                     :color         (uc/color ::uc/tab-text)
                     :cursor        "pointer"
                     :display       "flex"
                     :flex          "0 0 auto"
                     :align-items   "center"
                     :margin-right  "1px"
                     :overflow      "hidden"
                     :padding       "8px 12px 8px 10px"
                     :z-index       "1"}
                    [:&.active-tab {:background (uc/color ::uc/tab-active-bg)}]]
                   [:.active {:border     (str "1px solid " (uc/color ::uc/tab-border))
                              :display    "flex"
                              :flex       "1"
                              :min-height "0"}]
                   [:.new-tab {:font-size   "23px"
                               :line-height "1em"
                               :padding     "8px 12px"}]
                   [:.welcome {:background      (uc/color ::uc/welcome-container-bg)
                               :flex            "1"
                               :display         "flex"
                               :align-items     "center"
                               :justify-content "center"}]
                   [:.welcome-content {:background  (uc/color ::uc/welcome-msg-bg)
                                       :color       (uc/color ::uc/welcome-msg-text)
                                       :font-family uc/font-open-sans
                                       :padding     "0 12px"}
                    [:p {:margin "12px 0"}]]
                   [:.workspace-title
                    uc/font-os12sb
                    {:background    (uc/color ::uc/tab-text-field-bg)
                     :color         (uc/color ::uc/tab-text)
                     :border        "1px solid transparent"
                     :box-shadow    "0 0 2px 0 transparent"
                     :cursor        "pointer"
                     :flex          "1"
                     :max-width     "152px"
                     :overflow      "hidden"
                     :text-overflow "ellipsis"
                     :white-space   "nowrap"}
                    [:&:focus {:background (uc/color ::uc/tab-text-field-focus-bg)
                               :border     "1px solid #0079bf"
                               :box-shadow "0 0 2px 0 #0284c6"
                               :outline    "0"
                               :cursor     "text"}]]
                   [:.workspace-close
                    uc/close-icon-css
                    {:margin-left "10px"}]]}
  (let [update-title
        (fn [new-title workspace-id]
          (fp/transact! this [`(update-workspace ~{::workspace-id    workspace-id
                                                   ::workspace-title new-title})]))]
    (dom/div :.container
      (events/dom-listener {::events/keystroke (get-keybinding ::keybinding-close-workspace)
                            ::events/action    #(fp/transact! this [`(close-workspace ~active-workspace)])})
      (events/dom-listener {::events/keystroke (get-keybinding ::keybinding-new-workspace)
                            ::events/action    #(fp/transact! this [`(create-workspace {})])})
      (dom/div :.tabs
        (for [{::keys     [workspace-id workspace-title]
               ::wsm/keys [workspace-static? card-id]
               :as        tab-ws} (sort-by ::workspace-title open-workspaces)
              :let [current? (= (workspace-ident tab-ws) (workspace-ident active-workspace))]]
          (dom/div :.tab {:key     (or workspace-id card-id)
                          :classes [(if current? :.active-tab)]
                          :onClick (fn []
                                     (let [ws-ident (workspace-ident tab-ws)]
                                       (fm/set-value! this ::active-workspace ws-ident)
                                       (local-storage/set! ::active-workspace ws-ident)))}
            (if (or workspace-static? card-id (not current?))
              (dom/div :.workspace-title {:title (str (or workspace-title card-id))} (str (or workspace-title card-id)))
              (dom/input :.workspace-title {:value     (str workspace-title)
                                            :onChange  (fn [_])
                                            :onClick   #(.select (.-target %))
                                            :onBlur    #(update-title (.. % -target -value) workspace-id)
                                            :onKeyDown #(if (contains? #{(get events/KEYS "escape") (get events/KEYS "return")} (.-keyCode %))
                                                          (.blur (.-target %)))}))
            (dom/div :.workspace-close {:onClick (fn [e] (.stopPropagation e) (fp/transact! this [`(close-workspace ~tab-ws)]))}
              "×")))
        (dom/div :.tab.new-tab {:onClick #(fp/transact! this [`(create-workspace {})])}
          "+"))
      (dom/div :.active
        (if active-workspace
          (workspace-container (fp/computed active-workspace {::open-solo-card #(fp/transact! this [`(open-solo-workspace ~%)])}))
          (dom/div :.welcome
            (dom/div :.welcome-content
              (dom/p "Welcome to workspaces!")
              (dom/p "Use the index on the left to start navigating.")
              (dom/p "If you like more instructions please check "
                (dom/a {:href "https://github.com/nubank/workspaces#using-workspaces" :target "_blank"}
                  "workspaces usage guide") "."))))))))

(def workspace-tabs (fp/factory WorkspaceTabs))

(fp/defsc CardIndexListing
  [this {::wsm/keys [card-id]}]
  {:initial-state (fn [card]
                    (select-keys card [::wsm/card-id ::wsm/test? ::wsm/card-unlisted?]))
   :ident         [::wsm/card-id ::wsm/card-id]
   :query         [::wsm/card-id ::wsm/test? ::wsm/card-unlisted?]
   :css           [[:.container {:cursor "pointer"}]]}
  (dom/div :.container
    (dom/div {:onClick #(if (.-altKey %)
                          (add-card-solo this card-id)
                          (add-card this card-id))}
      (name card-id))))

(def card-index-listing (fp/factory CardIndexListing {:keyfn ::wsm/card-id}))

(fp/defsc WorkspaceIndexListing [_ _]
  {:ident [::workspace-id ::workspace-id]
   :query [::workspace-id ::workspace-title ::wsm/workspace-static?]})

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
        options (-> []
                    (into (map (fn [[_ {::wsm/keys [card-id test?]}]]
                                 {::spotlight/type (if test? ::spotlight/test ::spotlight/card)
                                  ::spotlight/id   card-id}))
                          (::wsm/card-id state))
                    (into (map (fn [[_ {::keys [workspace-id workspace-title]}]]
                                 {::spotlight/type  ::spotlight/workspace
                                  ::spotlight/id    workspace-id
                                  ::spotlight/label workspace-title}))
                          (::workspace-id state)))]
    (fp/transact! (fp/get-reconciler this) (fp/get-ident spotlight/Spotlight spotlight)
      `[(spotlight/reset {::spotlight/options ~options})])
    (fm/set-value! this ::show-spotlight? true)))

(fp/defsc HelpDialog
  [this {::keys []}]
  {:css [[:.container
          {:background    (uc/color ::uc/help-dialog-bg)
           :border-radius "4px"
           :color         "#fff"
           :font-family   uc/font-monospace
           :padding       "20px"}]

         [:.header
          {:font-family uc/font-open-sans
           :font-size   "26px"
           :font-weight "bold"
           :margin      "-5px 0 20px"
           :text-align  "center"}]]}
  (dom/div :.container
    (dom/div :.header "Keyboard Shortcuts")
    (dom/div (dom/strong (get-keybinding ::keybinding-spotlight)) ": Add card to current workspace (open spotlight for card picking)")
    (dom/div (dom/strong (get-keybinding ::keybinding-toggle-index)) ": Toggle index view")
    (dom/div (dom/strong (get-keybinding ::keybinding-toggle-card-headers)) ": Toggle card headers")
    (dom/div (dom/strong (get-keybinding ::keybinding-new-workspace)) ": Create new local workspace")
    (dom/div (dom/strong (get-keybinding ::keybinding-close-workspace)) ": Close current workspace")
    (dom/div (dom/strong "alt-shift-?") ": Toggle shortcuts modal")))

(def help-dialog (fp/factory HelpDialog))

(fp/defsc WorkspacesRoot
  [this {::keys [cards ws-tabs workspaces settings expanded spotlight show-spotlight?
                 show-help-modal?]}]
  {:initial-state  (fn [card-definitions]
                     {::cards            (mapv #(fp/get-initial-state CardIndexListing %)
                                           (vals card-definitions))
                      ::workspaces       (->> (local-storage/get ::local-workspaces [])
                                              (mapv #(fp/get-initial-state Workspace
                                                       (local-storage/tget [::workspace-id %])))
                                              (into (initialize-static-workspaces)))

                      ::expanded         (local-storage/get ::expanded {})
                      ::ws-tabs          (fp/get-initial-state WorkspaceTabs {})

                      ::spotlight        (fp/get-initial-state spotlight/Spotlight [])
                      ::show-spotlight?  false
                      ::show-help-modal? false
                      ::settings         {::show-index? (local-storage/get ::show-index? true)}})
   :ident          (fn [] [::workspace-root "singleton"])
   :query          [::settings ::expanded ::show-spotlight? ::show-help-modal?
                    {::cards (fp/get-query CardIndexListing)}
                    {::workspaces (fp/get-query WorkspaceIndexListing)}
                    {::ws-tabs (fp/get-query WorkspaceTabs)}
                    {::spotlight (fp/get-query spotlight/Spotlight)}]
   :css            [[:body {:margin     0
                            :overflow   "hidden"
                            :background (uc/color ::uc/bg)}]
                    [:.container {:color-scheme (uc/color ::uc/color-scheme)
                                  :color        (uc/color ::uc/primary-text-color)
                                  :box-sizing   "border-box"
                                  :display      "flex"
                                  :width        "100vw"
                                  :height       "100vh"
                                  :padding      "10px"}]
                    [:.menu {:background    (uc/color ::uc/menu-bg)
                             :color         (uc/color ::uc/menu-text)
                             :padding-right "10px"
                             :font-family   uc/font-open-sans
                             :flex-shrink   "0"
                             :overflow      "auto"
                             :min-width     "300px"}]
                    [:.workspaces {:display    "flex"
                                   :flex       "1"
                                   :max-height "100vh"
                                   :overflow   "hidden"}]
                    [:.index-action-button {:background  "transparent"
                                            :border      "none"
                                            :cursor      "pointer"
                                            :font-size   "23px"
                                            :font-weight "bold"
                                            :width       "20px"
                                            :margin-top  "-4px"
                                            :outline     "none"
                                            :padding     "0"}
                     [:&.spotlight {:color       "transparent"
                                    :text-shadow "0 0 #ffffff"
                                    :font-size   "14px"
                                    :margin      "-2px 10px 0 0"}]
                     [:&.help {:font-size "17px"
                               :margin    "-2px 10px 0 0"}]]
                    [:.header {:background    (uc/color ::uc/menu-header-bg)
                               :border-radius "4px"
                               :color         "#fff"
                               :font-weight   "bold"
                               :padding       "3px 7px"
                               :box-shadow    "0 1px 3px rgba(0,0,0,0.12), 0 1px 2px rgba(0,0,0,0.24)"
                               :margin        "1px 1px 6px"
                               :max-width     "100%"}
                     [:button {:color "#fff"}]]
                    [:.row {:display "flex"}]
                    [:.pointer {:cursor "pointer"}]
                    [:.flex {:flex "1"}]
                    [:.workspaces-solo {:max-width "100%"}]
                    [:.workspace {:cursor "pointer"}]
                    [:.nest-group {:margin-left "32px"}]
                    [:.nest-group-small {:margin-left "18px"}]
                    [:.ns-header {:display "flex" :align-items "center"}]
                    [:.expand-arrow {:margin-right "5px"
                                     :cursor       "pointer"
                                     :font-size    "14px"}]]
   :css-include    [uc/CSS HelpDialog]
   :initLocalState (fn [] {:spotlight-select
                           (fn [{::spotlight/keys [id type]} solo?]
                             (if id
                               (cond
                                 (= type ::spotlight/workspace)
                                 (fp/transact! this [`(select-workspace {::workspace-id ~id})])

                                 solo?
                                 (add-card-solo this id)

                                 :else
                                 (add-card this id)))

                             (fm/set-value! this ::show-spotlight? false))})}
  (dom/div :.container
    (cssi/style-element {:component WorkspacesRoot})
    (events/dom-listener {::events/keystroke (get-keybinding ::keybinding-toggle-index)
                          ::events/action    #(fp/transact! this [`(toggle-index-view {})])})
    (events/dom-listener {::events/keystroke "alt-shift-/"
                          ::events/action    #(fm/toggle! this ::show-help-modal?)})
    (events/dom-listener {::events/keystroke (get-keybinding ::keybinding-fix-sizes)
                          ::events/action    #(events/trigger-event js/window {::events/event "resize"})})
    (events/dom-listener {::events/keystroke (get-keybinding ::keybinding-toggle-card-headers)
                          ::events/action    #(fm/set-value! this ::settings (update (::settings (fp/props this)) ::hide-card-header? not))})
    (events/dom-listener {::events/keystroke (get-keybinding ::keybinding-spotlight)
                          ::events/action    (events/pd #(open-spotlight this))})
    (events/dom-listener {::events/event  "keydown"
                          ::events/action #(if (= (.-keyCode %) 18)
                                             (js/document.body.classList.add "cljs-workspaces-extended-views"))})
    (events/dom-listener {::events/event  "keyup"
                          ::events/action #(if (= (.-keyCode %) 18)
                                             (js/document.body.classList.remove "cljs-workspaces-extended-views"))})

    (if show-help-modal?
      (modal/modal {::modal/on-close #(fm/set-value! this ::show-help-modal? false)}
        (help-dialog {})))

    (if show-spotlight?
      (modal/modal {::modal/on-close #(fm/set-value! this ::show-spotlight? false)}
        (spotlight/spotlight
          (fp/computed spotlight
            {::spotlight/on-select (fp/get-state this :spotlight-select)}))))

    (if (::show-index? settings)
      (let [{uis false tests true} (group-by (comp true? ::wsm/test?) cards)]
        (dom/div :.menu
          (dom/div :.row.header
            (dom/div "Workspaces")
            (dom/div :.flex)
            (dom/button :.index-action-button.spotlight {:onClick #(open-spotlight this)}
              "\uD83D\uDD0D")
            (dom/button :.index-action-button.help {:onClick #(fm/toggle! this ::show-help-modal?)}
              "?")
            (dom/button :.index-action-button {:onClick #(fp/transact! this [`(toggle-index-view {})])}
              "«"))
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

          (dom/div :.header "Cards")
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

          (dom/div :.pointer.header {:onClick #(add-card this 'nubank.workspaces.card-types.test/test-all)}
            "Tests")
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
                  (mapv card-index-listing (sort-by ::wsm/card-id cards))))))))
      (dom/div
        (dom/button :.index-action-button {:onClick #(fp/transact! this [`(toggle-index-view {})])}
          "»")))
    (dom/div :.workspaces
      (workspace-tabs ws-tabs))))

(def workspaces-root (fp/factory WorkspacesRoot))
