(ns workshop-init
  "Resolve Joyride user dir, keybindings path, and workshop repo root for workshop-init.
   Run `bb init` with those paths to install."
  (:require ["vscode" :as vscode]
            ["path" :as path]
            ["os" :as os]
            ["fs" :as fs]
            [clojure.string :as string]
            [joyride.core :as joyride]))

(defn- keybindings-path []
  (path/join (os/homedir)
             "Library" "Application Support"
             (string/replace vscode/env.appName #"^Visual Studio " "")
             "User" "keybindings.json"))

(defn- linux-keybindings-path []
  (path/join (os/homedir)
             ".config"
             (string/replace vscode/env.appName #"^Visual Studio " "")
             "User" "keybindings.json"))

(defn- win-keybindings-path []
  (path/join (or (.-APPDATA (.-env js/process))
                 (path/join (os/homedir) "AppData" "Roaming"))
             (string/replace vscode/env.appName #"^Visual Studio " "")
             "User" "keybindings.json"))

(defn- platform-keybindings-path []
  (case (.-platform js/process)
    "darwin" (keybindings-path)
    "win32" (win-keybindings-path)
    (linux-keybindings-path)))

(defn- workshop-repo-root []
  (when-let [folders (.-workspaceFolders vscode/workspace)]
    (or (some (fn [folder]
                (let [p (.-fsPath (.-uri folder))]
                  (when (fs/existsSync (path/join p "bb.edn"))
                    p)))
              folders)
        (some-> folders first .-uri .-fsPath))))

(defn paths
  "Resolve workshop-init paths for this editor and workspace."
  []
  {:user-joyride-dir joyride/user-joyride-dir
   :keybindings-path (platform-keybindings-path)
   :repo-root (workshop-repo-root)})

(when (= (joyride/invoked-script) joyride/*file*)
  (let [{:keys [user-joyride-dir keybindings-path repo-root]} (paths)
        message (str "workshop-init paths:\n"
                     "  repo-root: " repo-root "\n"
                     "  user-joyride-dir: " user-joyride-dir "\n"
                     "  keybindings-path: " keybindings-path)]
    (vscode/window.showInformationMessage message)
    (.appendLine (joyride/output-channel) message)))
