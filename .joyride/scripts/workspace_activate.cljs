(ns workspace-activate
  (:require ["vscode" :as vscode]
            [joyride.core :as joyride]
            prezo.next-slide
            prezo.next-slide-notes
            ;;#_ Unignore when npm install has been run for this Joyride directory.
            #_pastedown
            [prezo.slide-zoom :as slide-zoom]
            [repl-connect]))

(defn- err-text
  [e]
  (or (.-message e) (js/String e)))

(defn- log!
  [msg]
  (.appendLine (joyride/output-channel) (str "workspace_activate: " msg)))

(defn- set-context!
  [k]
  (vscode/commands.executeCommand "setContext" k true))

(defn- activate-util-contexts!
  []
  (set-context! "workshop:open")
  (set-context! "flares:active")
  (set-context! "pastedown:active")
  (set-context! "keybinding-palette:active")
  (log! "util contexts set"))

(defn- load-presentation-runtime!
  []
  (try
    (prezo.next-slide/activate!)
    (log! "next-slide activated")
    (catch :default e
      (log! (str "next-slide load failed: " (err-text e))))))

(defn- main
  []
  (slide-zoom/sync!+)
  (log! "slide-zoom synced")
  (activate-util-contexts!)
  
  (load-presentation-runtime!)
  ;;#_ Unignore when npm install has been run for this Joyride directory.
  #_(pastedown/activate!)
  (repl-connect/start! {:connect-task "Try Clojure"
                        :repl-task "Try Clojure repl"
                        :connect-sequence "Try Clojure"
                        :session-key "try-clojure"})
  "ok")

(when (= (joyride/invoked-script) joyride/*file*)
  (main))
