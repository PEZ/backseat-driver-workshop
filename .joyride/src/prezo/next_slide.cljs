;; == Keyboard shortcuts ==
;; Weird indent because of how comment block/selection works
  ;; {
  ;;   "key": "ctrl+alt+j s",
  ;;   "command": "joyride.runCode",
  ;;   "args": "(prezo.next-slide/present!)"
  ;; },
  ;; {
  ;;   "key": "ctrl+alt+j ctrl+alt+s",
  ;;   "command": "joyride.runCode",
  ;;   "args": "(prezo.next-slide/deactivate!)"
  ;; },
  ;; {
  ;;   "key": "ctrl+alt+j ctrl+alt+m",
  ;;   "command": "markdown.showPreview"
  ;; },
  ;; {
  ;;   "key": "right",
  ;;   "command": "joyride.runCode",
  ;;   "args": "(prezo.next-slide/next! true)",
  ;;   "when": "next-slide:active && !inputFocus"
  ;; },
  ;; {
  ;;   "key": "left",
  ;;   "command": "joyride.runCode",
  ;;   "args": "(prezo.next-slide/next! false)",
  ;;   "when": "next-slide:active && !inputFocus"
  ;; },
  ;; {
  ;;   "key": "pagedown",
  ;;   "command": "joyride.runCode",
  ;;   "args": "(prezo.next-slide/next! true)",
  ;;   "when": "next-slide:active"
  ;; },
  ;; {
  ;;   "key": "pageup",
  ;;   "command": "joyride.runCode",
  ;;   "args": "(prezo.next-slide/next! false)",
  ;;   "when": "next-slide:active"
  ;; },
  ;; {
  ;;   "key": "F5",
  ;;   "command": "workbench.action.toggleZenMode",
  ;;   "when": "next-slide:active && !inZenMode"
  ;; },
  ;; {
  ;;   "key": "F5",
  ;;   "command": "joyride.runCode",
  ;;   "args": "(prezo.next-slide/current!)",
  ;;   "when": "next-slide:active && inZenMode"
  ;; },
  ;; {
  ;;   "key": "ctrl+alt+cmd+left",
  ;;   "command": "joyride.runCode",
  ;;   "args": "(prezo.next-slide/source! false)"
  ;; },
  ;; {
  ;;   "key": "ctrl+alt+cmd+right",
  ;;   "command": "joyride.runCode",
  ;;   "args": "(prezo.next-slide/source! true)"
  ;; },

(ns prezo.next-slide
  (:require ["vscode" :as vscode]
            ["path" :as path]
            [joyride.core :as joyride]
            [promesa.core :as p]
            [clojure.edn :as edn]
            [clojure.walk :as walk]))

(def !state (atom {:next/active? false
                   :next/active-slide 0
                   :next/config-path nil}))

(defn ws-root []
  (if (not= js/undefined
            vscode/workspace.workspaceFolders)
    (.-uri (first vscode/workspace.workspaceFolders))
    (vscode/Uri.parse ".")))

(defn get-config!+ [config-path]
  (p/let [config-uri (vscode/Uri.joinPath (ws-root) config-path)
          config-data (vscode/workspace.fs.readFile config-uri)
          config-text (-> (js/Buffer.from config-data) (.toString "utf-8"))
          config (edn/read-string config-text)]
    config))

(defn- config-path [state]
  (or (:next/config-path state) ["slides.edn"]))

(defn- step-index [current count-slides forward?]
  (if forward?
    (min (inc current) (dec count-slides))
    (max (dec current) 0)))

(defn- slide-filename [slide-path]
  (last (.split slide-path "/")))

(defn- slide-index-by-name [slides slide-name]
  (->> slides
       (map-indexed vector)
       (filter (fn [[_idx slide-path]]
                 (or (= slide-path slide-name)
                     (.endsWith slide-path slide-name))))
       ffirst))

(defn read-slides+ [config-path]
  (p/let [config (get-config!+ (apply path/join config-path))]
    (:slides config)))

(defn- editor-rel
  "Path of editor, relative to the workspace root."
  [editor]
  (when editor
    (let [rel (path/relative (.-fsPath (ws-root))
                             (.-fsPath (.-uri (.-document editor))))]
      (when-not (.startsWith rel "..")
        rel))))

(defn active-slide-rel
  "Path of the active editor, relative to the workspace root."
  []
  (editor-rel vscode/window.activeTextEditor))

(defn slide-index
  "Index of rel in slides, or nil."
  [slides rel]
  (some (fn [[idx slide]]
          (when (= slide rel) idx))
        (map-indexed vector slides)))

(defn- preview-source-rel+
  "Path of the markdown preview source, relative to the workspace root."
  []
  (p/let [editor (vscode/commands.executeCommand "markdown.showSource")]
    (or (editor-rel editor)
        (active-slide-rel))))

(defn gather-source+ [config-path]
  (p/let [slides (read-slides+ config-path)
          from-editor (active-slide-rel)
          rel (if (slide-index slides from-editor)
                from-editor
                (preview-source-rel+))]
    {:slides slides :rel rel}))

(defn handle-action [state [op & args]]
  (case op
    :slide/ax.activate
    (let [config-path (first args)]
      {:uf/db (assoc state
                     :next/active? true
                     :next/config-path config-path)
       :uf/fxs [[:vscode/fx.set-context "next-slide:active" true]
                [:vscode/fx.info "next-slide:activated"]]})

    :slide/ax.deactivate
    {:uf/db (assoc state :next/active? false)
     :uf/fxs [[:vscode/fx.set-context "next-slide:active" false]
              [:vscode/fx.info "next-slide:deactivated"]]}

    :slide/ax.present
    {:uf/db (-> state
                (assoc :next/active? true
                       :next/config-path ["slides.edn"]
                       :next/active-slide 0))
     :uf/fxs [[:vscode/fx.set-context "next-slide:active" true]
              [:vscode/fx.info "next-slide:activated"]
              [:vscode/fx.hide-panels]
              [:uf/await :slide/fx.read-slides ["slides.edn"]]]
     :uf/dxs [[:slide/ax.show 0 :uf/prev-result]]}

    :slide/ax.current
    {:uf/fxs [[:uf/await :slide/fx.read-slides (config-path state)]]
     :uf/dxs [[:slide/ax.show (:next/active-slide state) :uf/prev-result]]}

    :slide/ax.step
    (let [forward? (first args)]
      {:uf/fxs [[:uf/await :slide/fx.read-slides (config-path state)]]
       :uf/dxs [[:slide/ax.land forward? :uf/prev-result]]})

    :slide/ax.land
    (let [[forward? slides] args
          index (step-index (:next/active-slide state) (count slides) forward?)]
      (when (pos? (count slides))
        {:uf/db (assoc state :next/active-slide index)
         :uf/fxs [[:preview/fx.show (nth slides index)]]}))

    :slide/ax.restart
    {:uf/db (assoc state :next/active-slide 0)
     :uf/fxs [[:uf/await :slide/fx.read-slides (config-path state)]]
     :uf/dxs [[:slide/ax.show 0 :uf/prev-result]]}

    :slide/ax.show
    (let [[index slides] args]
      (when (nth slides index nil)
        {:uf/fxs [[:preview/fx.show (nth slides index)]]}))

    :slide/ax.source
    (let [forward? (first args)]
      {:uf/fxs [[:uf/await :slide/fx.gather-source (config-path state)]]
       :uf/dxs [[:slide/ax.source-on :uf/prev-result forward?]]})

    :slide/ax.source-on
    (let [[{:keys [slides rel]} forward?] args
          idx (slide-index slides rel)]
      (if (nil? idx)
        {:uf/fxs [[:vscode/fx.warn "This file is not in the slide list"]]}
        (let [next-idx (if forward? (inc idx) (dec idx))
              slide (when (and (<= 0 next-idx) (< next-idx (count slides)))
                      (nth slides next-idx))]
          (when slide
            {:uf/db (assoc state :next/active-slide next-idx)
             :uf/fxs [[:editor/fx.show slide]]}))))

    :slide/ax.show-name
    (let [slide-name (first args)]
      {:uf/fxs [[:uf/await :slide/fx.read-slides (config-path state)]]
       :uf/dxs [[:slide/ax.show-name-on slide-name :uf/prev-result]]})

    :slide/ax.show-name-on
    (let [[slide-name slides] args
          idx (slide-index-by-name slides slide-name)]
      (if (some? idx)
        (let [slide (nth slides idx)]
          {:uf/db (assoc state :next/active-slide idx)
           :uf/fxs [[:preview/fx.show slide]]})
        {:uf/fxs [[:log/fx.warn "Slide not found:" slide-name "Available slides:" slides]
                  [:vscode/fx.warn (str "Slide not found: " slide-name)]]}))

    :slide/ax.slides-and-index
    {:uf/fxs [[:uf/await :slide/fx.slides-and-index
               (config-path state)
               (:next/active-slide state)]]}

    :slide/ax.current-name
    {:uf/fxs [[:uf/await :slide/fx.current-name
               (config-path state)
               (:next/active-slide state)]]}

    :chrome/ax.hide
    {:uf/fxs [[:vscode/fx.hide-panels]]}

    (do
      (js/console.warn "Unhandled action:" op)
      nil)))

(defn handle-actions [state actions]
  (reduce
   (fn [acc action]
     (if-let [result (handle-action (:uf/db acc) action)]
       (cond-> acc
         (contains? result :uf/db) (assoc :uf/db (:uf/db result))
         (seq (:uf/fxs result)) (update :uf/fxs into (:uf/fxs result))
         (seq (:uf/dxs result)) (update :uf/dxs into (:uf/dxs result)))
       acc))
   {:uf/db state :uf/fxs [] :uf/dxs []}
   actions))

(defn perform-effect! [_dispatch [effect & args]]
  (case effect
    :slide/fx.read-slides
    (read-slides+ (first args))

    :slide/fx.gather-source
    (gather-source+ (first args))

    :slide/fx.slides-and-index
    (let [[config-path index] args]
      (p/let [slides (read-slides+ config-path)]
        {:slides slides :index index}))

    :slide/fx.current-name
    (let [[config-path index] args]
      (p/let [slides (read-slides+ config-path)]
        (some-> (nth slides index nil) slide-filename)))

    :preview/fx.show
    (let [rel (first args)]
      (vscode/commands.executeCommand
       "markdown.showPreview"
       (vscode/Uri.joinPath (ws-root) rel)))

    :editor/fx.show
    (let [rel (first args)]
      (vscode/window.showTextDocument
       (vscode/Uri.joinPath (ws-root) rel)))

    :vscode/fx.set-context
    (let [[k v] args]
      (vscode/commands.executeCommand "setContext" k v))

    :vscode/fx.info
    (vscode/window.showInformationMessage (first args))

    :vscode/fx.warn
    (vscode/window.showWarningMessage (first args))

    :vscode/fx.hide-panels
    (do
      (vscode/commands.executeCommand "workbench.action.closeSidebar")
      (vscode/commands.executeCommand "workbench.action.closePanel")
      (vscode/commands.executeCommand "workbench.action.closeAuxiliaryBar"))

    :log/fx.warn
    (apply js/console.warn args)

    :uf/unhandled-fx))

(defn- await-fx? [fx]
  (and (vector? fx) (= :uf/await (first fx))))

(defn- unwrap-fx [fx]
  (if (await-fx? fx) (vec (rest fx)) fx))

(defn- replace-prev-result [form prev-result]
  (walk/postwalk
   (fn [x] (if (= :uf/prev-result x) prev-result x))
   form))

(defn- replace-prev-result-in-actions [actions prev-result]
  (mapv #(replace-prev-result % prev-result) actions))

(defn- execute-effect! [dispatch fx]
  (let [result (perform-effect! dispatch fx)]
    (if (= :uf/unhandled-fx result)
      (do
        (js/console.warn "Unhandled effect:" fx)
        nil)
      result)))

(defn- execute-effects! [dispatch fxs]
  (reduce
   (fn [promise-chain raw-fx]
     (.then promise-chain
            (fn [prev-result]
              (let [is-await? (await-fx? raw-fx)
                    fx (-> raw-fx unwrap-fx (replace-prev-result prev-result))
                    result (execute-effect! dispatch fx)]
                (if is-await?
                  (js/Promise.resolve result)
                  prev-result)))))
   (js/Promise.resolve nil)
   fxs))

(defn dispatch! [actions]
  (let [old-state @!state
        {:uf/keys [db fxs dxs]} (handle-actions old-state actions)]
    (when (some? db)
      (reset! !state db))
    (if (seq fxs)
      (-> (execute-effects! dispatch! (remove nil? fxs))
          (.then (fn [prev-result]
                   (when (seq dxs)
                     (dispatch! (replace-prev-result-in-actions dxs prev-result)))
                   prev-result)))
      (do
        (when (seq dxs)
          (dispatch! dxs))
        (js/Promise.resolve nil)))))

(defn slides-and-index+ []
  (dispatch! [[:slide/ax.slides-and-index]]))

(defn slides-list+ []
  (p/let [snap (slides-and-index+)]
    (:slides snap)))

(defn current! []
  (dispatch! [[:slide/ax.current]])
  nil)

(defn next!
  ([]
   (next! true))
  ([forward?]
   (dispatch! [[:slide/ax.step forward?]])
   nil))

(defn source!
  "Open the previous or next slide source file."
  [forward?]
  (dispatch! [[:slide/ax.source forward?]])
  nil)

(defn restart! []
  (dispatch! [[:slide/ax.restart]])
  nil)

(defn deactivate! []
  (dispatch! [[:slide/ax.deactivate]])
  nil)

(defn activate!
  ([]
   (activate! ["slides.edn"]))
  ([config-path]
   (dispatch! [[:slide/ax.activate config-path]])
   nil))

(defn hide-panels!
  "Close the sidebar, panel, and secondary sidebar."
  []
  (dispatch! [[:chrome/ax.hide]])
  nil)

(defn present!
  "Activate slide mode, hide panels, and show the first slide."
  []
  (dispatch! [[:slide/ax.present]])
  nil)

(defn get-current-slide-name+
  "Filename of the active slide (no path prefix)."
  []
  (dispatch! [[:slide/ax.current-name]]))

(defn show-slide-by-name!+
  "Show a slide by its filename (e.g. 'hello.md' or 'slides/hello.md')"
  [slide-name]
  (dispatch! [[:slide/ax.show-name slide-name]])
  nil)

(comment
  (show-slide-by-name!+ "ecosystem.md")
  :rcf)

(when (= (joyride/invoked-script) joyride/*file*)
  (activate!))

(comment
  @!state
  (slides-and-index+)
  (next!)
  (next! false)
  (activate!)
  (restart!)
  (deactivate!)
  :rcf)
