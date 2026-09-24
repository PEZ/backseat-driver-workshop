(ns keybinding-palette
  (:require
   ["jsonc-parser" :as jsonc]
   ["fs" :as fs]
   ["os" :as os]
   ["path" :as path]
   ["vscode" :as vscode]
   [clojure.string :as str]
   [joyride.core :as joyride]
   [promesa.core :as p]))

;; Search keybindings.json and run a binding.
;;
;; This file is on the workshop Joyride classpath while this folder is open.
;; It needs jsonc-parser. From the workshop folder: npm install --prefix .joyride
;;
;; To use it in every window:
;;   1. Copy this file to ~/.config/joyride/src/keybinding_palette.cljs
;;   2. In ~/.config/joyride: npm install jsonc-parser
;;   3. Add the keybinding below to keybindings.json
;;
;; Keybinding:
;; {
;;   "title": "Keybinding Command Palette",
;;   "key": "ctrl+alt+j ctrl+alt+j",
;;   "command": "joyride.runCode",
;;   "args": "(require '[keybinding-palette :as kp] :reload) (kp/show-palette!+)"
;; }
;;
;; NB: Add "title"s to your keybindings for best experience

(defn- keybindings-path []
  (let [app-name (str/replace vscode/env.appName #"^Visual Studio " "")
        home (os/homedir)]
    (case (.-platform js/process)
      "darwin" (path/join home "Library" "Application Support" app-name "User" "keybindings.json")
      "win32" (path/join (or (.-APPDATA (.-env js/process))
                             (path/join home "AppData" "Roaming"))
                         app-name "User" "keybindings.json")
      (path/join home ".config" app-name "User" "keybindings.json"))))

(defn- read-keybindings []
  (jsonc/parse (fs/readFileSync (keybindings-path) "utf8")))

(def ^:private modifier-symbols
  {"ctrl"  "⌃"
   "alt"   "⌥"
   "shift" "⇧"
   "cmd"   "⌘"
   "meta"  "⌘"})

(def ^:private special-keys
  {"enter"     "⏎"
   "escape"    "ESC"
   "tab"       "⇥"
   "backspace" "⌫"
   "delete"    "⌦"
   "space"     "␣"
   "up"        "↑"
   "down"      "↓"
   "left"      "←"
   "right"     "→"
   "pageup"    "⇞"
   "pagedown"  "⇟"
   "home"      "↖"
   "end"       "↘"})

(defn- prettify-key [key-str]
  (when key-str
    (->> (str/split key-str #" ")
         (map (fn [chord]
                (let [parts (str/split chord #"\+")]
                  (apply str (map (fn [part]
                                    (or (get modifier-symbols part)
                                        (get special-keys part)
                                        (str/upper-case part)))
                                  parts)))))
         (str/join " "))))

(defn- js-entry->item [entry]
  (let [category (.-category entry)
        title (.-title entry)
        command (.-command entry)
        key-str (.-key entry)
        when-clause (.-when entry)
        args (.-args entry)]
    (clojure.core/when (and title
                            (not (str/starts-with? command "-")))
      #js {:label (if category
                    (str category ": " title)
                    title)
           :description (prettify-key key-str)
           :detail (str key-str " · " command
                        (clojure.core/when when-clause
                          (str " · when: " when-clause)))
           :_command command
           :_args args})))

(defn- execute-keybinding! [item]
  (let [command (.-_command item)
        args (.-_args item)]
    (if (some? args)
      (vscode/commands.executeCommand command args)
      (vscode/commands.executeCommand command))))

(defn show-palette!+ []
  (p/let [entries (read-keybindings)
          items (->> entries
                     (keep js-entry->item)
                     (sort-by #(str/lower-case (.-label %)))
                     into-array)
          selected (vscode/window.showQuickPick
                    items
                    #js {:title "Keybinding Command Palette"
                         :placeHolder "Search keybindings..."
                         :matchOnDescription true
                         :matchOnDetail true})]
    (clojure.core/when selected
      (execute-keybinding! selected))))

(clojure.core/when (= (joyride/invoked-script) joyride/*file*)
  (show-palette!+))
