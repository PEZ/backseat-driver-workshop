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
            [clojure.edn :as edn]))

(def !state (atom {:next/active? false
                   :next/active-slide 0
                   :next/config-path nil}))

(defn ws-root []
  (if (not= js/undefined
            vscode/workspace.workspaceFolders)
    (.-uri (first vscode.workspace.workspaceFolders))
    (vscode/Uri.parse ".")))

(defn get-config!+ [config-path]
  (p/let [config-uri (vscode/Uri.joinPath (ws-root) config-path)
          config-data (vscode/workspace.fs.readFile config-uri)
          config-text (-> (js/Buffer.from config-data) (.toString "utf-8"))
          config (edn/read-string config-text)]
    config))

(defn slides-list+
  []
  (p/let [config-path (or (:next/config-path @!state) ["slides.edn"])
          config (get-config!+ (apply path/join config-path))]
    (:slides config)))

(defn current! []
  (p/let [slides (slides-list+)]
    (vscode/commands.executeCommand
     "markdown.showPreview"
     (vscode/Uri.joinPath (ws-root)
                          (nth slides (:next/active-slide @!state)))))
  nil)

(defn next!
  ([]
   (next! true))
  ([forward?]
   (p/let [slides (slides-list+)
           next (if forward?
                  #(min (inc %) (dec (count slides)))
                  #(max (dec %) 0))]
     (swap! !state update :next/active-slide next)
     (current!))
   nil))

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

(defn source!
  "Open the previous or next slide source file."
  [forward?]
  (p/let [slides (slides-list+)
          from-editor (active-slide-rel)
          rel (if (slide-index slides from-editor)
                from-editor
                (preview-source-rel+))
          idx (slide-index slides rel)]
    (if (nil? idx)
      (vscode/window.showWarningMessage "This file is not in the slide list")
      (let [next-idx (if forward? (inc idx) (dec idx))
            slide (when (and (<= 0 next-idx) (< next-idx (count slides)))
                    (nth slides next-idx))]
        (when slide
          (swap! !state assoc :next/active-slide next-idx)
          (vscode/window.showTextDocument (vscode/Uri.joinPath (ws-root) slide))))))
  nil)

(defn restart!
  []
  (swap! !state assoc :next/active-slide 0)
  (current!)
  nil)

(defn deactivate! []
  (swap! !state assoc :next/active? false)
  (vscode/commands.executeCommand "setContext" "next-slide:active" false)
  (vscode/window.showInformationMessage
    (str "next-slide:" "deactivated"))
  nil)

(defn activate!
  ([]
   (activate! ["slides.edn"]))
  ([config-path]
   (swap! !state assoc
          :next/active? true
          :next/config-path config-path)
   (vscode/commands.executeCommand "setContext" "next-slide:active" true)
   (vscode/window.showInformationMessage
    (str "next-slide:" "activated"))
   nil))

(defn hide-panels!
  "Close the sidebar, panel, and secondary sidebar."
  []
  (vscode/commands.executeCommand "workbench.action.closeSidebar")
  (vscode/commands.executeCommand "workbench.action.closePanel")
  (vscode/commands.executeCommand "workbench.action.closeAuxiliaryBar")
  nil)

(defn present!
  "Activate slide mode, hide panels, and show the first slide."
  []
  (activate!)
  (swap! !state assoc :next/active-slide 0)
  (hide-panels!)
  (current!)
  nil)

(defn get-current-slide-name+
  "Get the filename of the currently active slide (without path prefix)"
  []
  (p/let [slides (slides-list+)
          current-slide-path (nth slides (:next/active-slide @!state))
          filename (last (.split current-slide-path "/"))]
    filename))

(defn show-slide-by-name!+
  "Show a slide by its filename (e.g. 'hello.md' or 'slides/hello.md')"
  [slide-name]
  (p/let [slides (slides-list+)
          slide-index (->> slides
                           (map-indexed vector)
                           (filter (fn [[_idx slide-path]]
                                     (or (= slide-path slide-name)
                                         (.endsWith slide-path slide-name))))
                           ffirst)]
    (if slide-index
      (do
        (swap! !state assoc :next/active-slide slide-index)
        (current!))
      (do
        (js/console.warn "Slide not found:" slide-name "Available slides:" slides)
        (vscode/window.showWarningMessage (str "Slide not found: " slide-name))))))

(comment
  (show-slide-by-name!+ "ecosystem.md")
  :rcf)

(when (= (joyride/invoked-script) joyride/*file*)
  (activate!))

(comment
  @!state
  (next!)
  (next! false)
  (activate!)
  (restart!)
  (deactivate!)
  :rcf)