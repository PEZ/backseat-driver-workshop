(ns workshop-init
  "Installs this workshop's Joyride npm deps and keybindings. Copies sources into the user Joyride directory only when asked."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.string :as string]))

(def begin-marker "// BEGIN workshop-init:next-slide")
(def agents-begin-marker "<!-- BEGIN workshop-init:check -->")

(def agents-end-marker "<!-- END workshop-init:check -->")

(def end-marker "// END workshop-init:next-slide")

(def npm-ignore-comment
  ";;#_")

(def npm-packages
  ["jsonc-parser" "turndown" "turndown-plugin-gfm"])

(def user-context-begin ";; BEGIN workshop-init:user-contexts")
(def user-context-end ";; END workshop-init:user-contexts")

(def user-source-files
  ["flares.cljs"
   "pastedown.cljs"
   "keybinding_palette.cljs"
   "prezo/next_slide.cljs"
   "prezo/next_slide_notes.cljs"
   "prezo/slide_zoom.cljs"])

(defn unignore-npm-text
  "Removes each comment that starts with `;;#_` and the `#_` on the next form."
  [text]
  (string/replace text
                  #"(?m)^[ \t]*;;#_.*\n([ \t]*)#_"
                  "$1"))

(def cli-spec
  {:spec {:user-joyride-dir {:desc "Joyride user directory (default: ~/.config/joyride)"}
          :keybindings-path {:desc "Absolute path to User keybindings.json"}
          :editor {:desc "Editor for default keybindings path: cursor, code, or code-insiders"}
          :dry-run {:coerce :boolean
                    :alias :n
                    :desc "Print actions without writing"}
          :repo-root {:desc "Workshop repo root (default: cwd)"}}})

(defn- repo-root [opts]
  (fs/canonicalize (or (:repo-root opts) ".")))

(defn- git-toplevel
  "Nearest directory at or above dir that contains `.git`."
  [dir]
  (loop [p (fs/canonicalize dir)]
    (when p
      (let [git (fs/path p ".git")]
        (if (or (fs/directory? git)
                (fs/regular-file? git))
          (str p)
          (recur (fs/parent p)))))))

(defn- nested-in-other-git-repo?
  "True when dir is inside a git repo whose root is a parent of dir."
  [dir]
  (let [root (str (fs/canonicalize dir))
        top (git-toplevel dir)]
    (boolean (and top
                  (not= root (str (fs/canonicalize top)))))))

(defn agents-md-path [opts]
  (str (fs/path (repo-root opts) "AGENTS.md")))

(defn assets-dir [opts]
  (fs/path (repo-root opts)
           ".agents" "skills" "workshop-init" "assets"))

(defn default-user-joyride-dir []
  (fs/path (System/getProperty "user.home") ".config" "joyride"))

(defn- os-key []
  (let [name (string/lower-case (System/getProperty "os.name" ""))]
    (cond
      (string/includes? name "mac") :mac
      (string/includes? name "win") :win
      :else :linux)))

(defn- editor-app-dir
  "Folder name under Application Support / config for the editor."
  [editor]
  (case (keyword editor)
    :cursor "Cursor"
    :code "Code"
    :code-insiders "Code - Insiders"
    (throw (ex-info "Unknown --editor (use cursor, code, or code-insiders)"
                    {:editor editor :babashka/exit 1}))))

(defn default-keybindings-path
  "User keybindings.json for editor on this OS."
  [editor]
  (let [home (System/getProperty "user.home")
        app (editor-app-dir editor)]
    (str (case (os-key)
           :mac (fs/path home "Library" "Application Support" app "User" "keybindings.json")
           :win (fs/path (or (System/getenv "APPDATA") (fs/path home "AppData" "Roaming"))
                         app "User" "keybindings.json")
           :linux (fs/path home ".config" app "User" "keybindings.json")))))

(defn resolve-keybindings-path [opts]
  (cond
    (:keybindings-path opts) (str (fs/absolutize (:keybindings-path opts)))
    (:editor opts) (default-keybindings-path (:editor opts))
    :else (default-keybindings-path :cursor)))

(defn resolve-user-joyride-dir [opts]
  (str (fs/absolutize (or (:user-joyride-dir opts) (default-user-joyride-dir)))))

(defn- joyride-dir [opts]
  (fs/path (repo-root opts) ".joyride"))

(defn- modules-present? [dir packages]
  (every? #(fs/exists? (fs/path dir "node_modules" %)) packages))

(defn ensure-npm!
  "Installs npm packages in dir when any are missing. extra-packages, when set, are passed to npm install."
  [dir opts extra-packages]
  (let [dir (str dir)
        packages (or extra-packages npm-packages)
        ready? (modules-present? dir packages)]
    (cond
      ready?
      {:path dir :action :skipped}

      (and (nil? extra-packages)
           (not (fs/exists? (fs/path dir "package.json"))))
      {:path (str (fs/path dir "package.json")) :action :skipped}

      (:dry-run opts)
      {:path dir :action :would-write}

      :else
      (do
        (if extra-packages
          (apply process/shell {:dir dir} "npm" "install" extra-packages)
          (process/shell {:dir dir} "npm" "install"))
        {:path dir :action :wrote}))))

(defn unignore-npm!
  "Removes npm #_ guards in workspace_activate after node_modules is present."
  [opts]
  (let [path (str (fs/path (joyride-dir opts) "scripts" "workspace_activate.cljs"))
        dry? (:dry-run opts)]
    (cond
      (not (fs/exists? path))
      {:path path :action :skipped}

      (not (modules-present? (joyride-dir opts) npm-packages))
      {:path path :action :skipped}

      :else
      (let [existing (slurp path)
            updated (unignore-npm-text existing)]
        (cond
          (= existing updated)
          {:path path :action :skipped}

          dry?
          {:path path :action :would-write}

          :else
          (do
            (spit path updated)
            {:path path :action :wrote}))))))

(defn marked-block
  "Wrap fragment body (array entries) in workshop-init markers."
  [fragment]
  (let [body (-> fragment string/trimr
                 (string/replace #"\A\s+" ""))]
    (str begin-marker "\n  " body "\n  " end-marker)))

(defn parse-fragment
  "Parse a keybindings fragment (array entries without outer brackets) into a vector of maps."
  [fragment]
  (json/parse-string (str "[" (string/trim fragment) "]") true))

(defn- args-present?
  [text args]
  (cond
    (nil? args) true
    (string? args) (string/includes? text args)
    (map? args) (every? (fn [[k v]]
                          (string/includes? text (str "\"" (name k) "\": \"" v "\"")))
                        args)
    :else false))

(defn binding-present?
  "True when existing keybindings text contains this binding's fingerprints."
  [text {:keys [key command args when]}]
  (and (string/includes? text (str "\"key\": \"" key "\""))
       (or (nil? command) (string/includes? text (str "\"command\": \"" command "\"")))
       (args-present? text args)
       (or (nil? when) (string/includes? text when))))

(defn next-slide-bindings-present?
  "True when every binding from the fragment is already present in the text."
  [text fragment]
  (every? #(binding-present? text %) (parse-fragment fragment)))

(defn- trim-right-ws [s]
  (string/replace s #"\s+\z" ""))

(defn- needs-comma-before-insert?
  "True when text before the closing bracket needs a comma before a new entry."
  [before]
  (let [t (trim-right-ws before)]
    (and (pos? (count t))
         (not (string/ends-with? t "["))
         (not (string/ends-with? t ",")))))

(defn merge-keybindings-text
  "Keep the marked Next-slide block identical to the asset fragment.
   When markers exist, replace that block if it differs.
   When markers are absent, insert the block unless every fragment binding is already present."
  [existing fragment]
  (let [block (marked-block fragment)]
    (cond
      (and (string/includes? existing begin-marker)
           (string/includes? existing end-marker))
      (let [begin-idx (string/index-of existing begin-marker)
            end-idx (string/index-of existing end-marker)
            end-at (+ end-idx (count end-marker))
            current (subs existing begin-idx end-at)]
        (if (= current block)
          existing
          (str (subs existing 0 begin-idx) block (subs existing end-at))))

      (next-slide-bindings-present? existing fragment)
      existing

      :else
      (let [close-idx (string/last-index-of existing "]")]
        (if (nil? close-idx)
          (str "[\n  " block "\n]\n")
          (let [before (subs existing 0 close-idx)
                after (subs existing close-idx)
                comma? (needs-comma-before-insert? before)]
            (str (trim-right-ws before)
                 (when comma? ",")
                 "\n  "
                 block
                 "\n"
                 after)))))))

(defn copy-prezo!
  "Copy vendored prezo cljs into user Joyride src/prezo when the dest file is absent.
   Never overwrites an existing file.
   Returns a vector of {:path :action} where :action is :wrote, :skipped, or :would-write."
  [opts]
  (let [src-dir (fs/path (assets-dir opts) "prezo")
        dest-dir (fs/path (resolve-user-joyride-dir opts) "src" "prezo")
        files ["next_slide.cljs" "next_slide_notes.cljs"]
        dry? (:dry-run opts)]
    (when-not (fs/directory? src-dir)
      (throw (ex-info "Missing vendored prezo assets"
                      {:path (str src-dir) :babashka/exit 1})))
    (mapv (fn [name]
            (let [from (fs/path src-dir name)
                  to (fs/path dest-dir name)
                  to-str (str to)]
              (when-not (fs/exists? from)
                (throw (ex-info "Missing vendored file"
                                {:path (str from) :babashka/exit 1})))
              (cond
                (fs/exists? to)
                {:path to-str :action :skipped}

                dry?
                {:path to-str :action :would-write}

                :else
                (do
                  (fs/create-dirs dest-dir)
                  (fs/copy from to {:replace-existing false})
                  {:path to-str :action :wrote}))))
          files)))

(defn- zoom-keys []
  (if (= :mac (os-key))
    {:in "cmd+[Minus]" :out "cmd+-" :reset "cmd+0" :restart "ctrl+alt+cmd+left"}
    {:in "ctrl+=" :out "ctrl+-" :reset "ctrl+0" :restart "ctrl+alt+j ctrl+left"}))

(defn- run-code
  [title category key args when]
  (cond-> {:title title
           :category category
           :key key
           :command "joyride.runCode"
           :args args}
    when (assoc :when when)))

(defn keybinding-entries
  []
  (let [{:keys [in out reset restart]} (zoom-keys)]
    [(run-code "Activate Slide Mode" "Next-slide" "ctrl+alt+j s"
               "(prezo.next-slide/activate!)" "workshop:open")
     (run-code "Deactivate Slide Mode" "Next-slide" "ctrl+alt+j ctrl+alt+s"
               "(prezo.next-slide/deactivate!)" "workshop:open")
     {:title "Show Markdown Preview"
      :category "Next-slide"
      :key "ctrl+alt+j ctrl+alt+m"
      :command "markdown.showPreview"
      :when "workshop:open"}
     (run-code "Next Slide" "Next-slide" "right"
               "(prezo.next-slide/next! true)" "next-slide:active && !inputFocus")
     (run-code "Previous Slide" "Next-slide" "left"
               "(prezo.next-slide/next! false)" "next-slide:active && !inputFocus")
     (run-code "Next Slide" "Next-slide" "pagedown"
               "(prezo.next-slide/next! true)" "next-slide:active")
     (run-code "Previous Slide" "Next-slide" "pageup"
               "(prezo.next-slide/next! false)" "next-slide:active")
     {:title "Enter Zen Mode"
      :category "Next-slide"
      :key "F5"
      :command "workbench.action.toggleZenMode"
      :when "next-slide:active && !inZenMode"}
     (run-code "Show Current Slide" "Next-slide" "F5"
               "(prezo.next-slide/current!)" "next-slide:active && inZenMode")
     (run-code "Restart Presentation" "Next-slide" restart
               "(prezo.next-slide/restart!)" "workshop:open")
     (run-code "Prepare Speaker Notes" "Next-slide" "ctrl+alt+j ctrl+n"
               "(prezo.next-slide-notes/prepare!)" "workshop:open")
     (run-code "Edit Active Note" "Next-slide" "ctrl+alt+j shift+n"
               "(prezo.next-slide-notes/edit-active-note!)" "workshop:open")
     (run-code "Print Speaker Notes" "Next-slide" "ctrl+alt+j alt+n"
               "(prezo.next-slide-notes/print!)" "workshop:open")
     (run-code "Toggle Speaker Notes" "Next-slide" "ctrl+alt+j ctrl+alt+n"
               "(prezo.next-slide-notes/toggle!)" "workshop:open")
     (run-code "Zoom In" "Next-slide" in
               "(prezo.slide-zoom/zoom-in!+)" "next-slide:active")
     (run-code "Zoom Out" "Next-slide" out
               "(prezo.slide-zoom/zoom-out!+)" "next-slide:active")
     (run-code "Reset Zoom" "Next-slide" reset
               "(prezo.slide-zoom/zoom-reset!+)" "next-slide:active")
     (run-code "Open URL in Sidebar" "Flares" "ctrl+alt+j ctrl+alt+b"
               "(do (require '[flares] :reload) (flares/prompt-and-open-url-in-sidebar!+))"
               "flares:active")
     (run-code "Open URL as Panel" "Flares" "ctrl+alt+j alt+b"
               "(do (require '[flares] :reload) (flares/prompt-and-open-url-as-panel!+))"
               "flares:active")
     (run-code "Show Flares" "Flares" "ctrl+alt+j ctrl+shift+f"
               "(do (require '[flares] :reload) (flares/show-flares-picker!+))"
               "flares:active")
     (run-code "Paste as Markdown in Chat" "Pastedown" "ctrl+alt+j ctrl+alt+v"
               "(require 'pastedown :reload) (pastedown/pastedown-in-chat!)"
               "pastedown:active && inChatInput")
     {:title "Paste as Markdown"
      :category "Pastedown"
      :key "ctrl+alt+j ctrl+alt+v"
      :command "editor.action.pasteAs"
      :when "pastedown:active && editorTextFocus"
      :args {:kind "pastedown"}}
     (run-code "Keybinding Command Palette" "Keybinding Palette" "ctrl+alt+j ctrl+alt+j"
               "(require '[keybinding-palette :as kp] :reload) (kp/show-palette!+)"
               "keybinding-palette:active")]))

(defn keybindings-fragment
  []
  (->> (keybinding-entries)
       (map #(-> (json/generate-string % {:pretty true})
                 (string/replace #"\" :" "\":")))
       (string/join ",\n")))

(defn clashing-keys
  "Keys already bound to something other than this fragment's binding."
  [existing fragment]
  (vec (distinct (keep (fn [b]
                         (let [k (:key b)]
                           (when (and (string/includes? existing (str "\"key\": \"" k "\""))
                                      (not (binding-present? existing b)))
                             k)))
                       (parse-fragment fragment)))))

(defn merge-keybindings!
  "Merge Next-slide keybindings when the fragment's bindings are absent.
   Returns {:path :action} with :wrote, :skipped, or :would-write."
  [opts]
  (let [kb-path (resolve-keybindings-path opts)
        fragment (or (:keybindings-fragment opts) (keybindings-fragment))
        existing (if (fs/exists? kb-path)
                   (slurp kb-path)
                   "[]\n")
        dry? (:dry-run opts)]
    (cond
      (next-slide-bindings-present? existing fragment)
      {:path kb-path :action :skipped}

      dry?
      {:path kb-path :action :would-write}

      :else
      (do
        (fs/create-dirs (fs/parent kb-path))
        (spit kb-path (merge-keybindings-text existing fragment))
        {:path kb-path :action :wrote}))))

(defn clear-agents-init-check-text
  "Remove the workshop-init check section from AGENTS.md text when both markers are present.
   Trims excess blank lines at the join; file ends with a single trailing newline."
  [text]
  (if (and (string/includes? text agents-begin-marker)
           (string/includes? text agents-end-marker))
    (let [begin-idx (string/index-of text agents-begin-marker)
          end-idx (string/index-of text agents-end-marker)
          before (subs text 0 begin-idx)
          after (subs text (+ end-idx (count agents-end-marker)))
          before' (string/replace before #"\s+\z" "")
          after' (string/replace after #"\A\s+" "")
          body (cond
                 (and (seq before') (seq after'))
                 (str before' "\n" after')

                 (seq before')
                 before'

                 (seq after')
                 after'

                 :else "")]
      (str body "\n"))
    text))

(defn clear-agents-init-check!
  "Remove the workshop-init check section from repo AGENTS.md when present.
   Skips when this folder is nested in another git repo.
   Returns {:path :action} with :wrote, :skipped, or :would-write."
  [opts]
  (let [path (agents-md-path opts)
        dry? (:dry-run opts)]
    (cond
      (not (fs/exists? path))
      {:path path :action :skipped}

      (nested-in-other-git-repo? (repo-root opts))
      {:path path :action :skipped}

      :else
      (let [existing (slurp path)]
        (cond
          (not (and (string/includes? existing agents-begin-marker)
                    (string/includes? existing agents-end-marker)))
          {:path path :action :skipped}

          dry?
          {:path path :action :would-write}

          :else
          (do
            (spit path (clear-agents-init-check-text existing))
            {:path path :action :wrote}))))))

(defn exec!
  "Installs npm deps, drops npm ignore markers, and merges keybindings."
  [opts]
  (let [fragment (or (:keybindings-fragment opts) (keybindings-fragment))
        kb-path (resolve-keybindings-path opts)
        existing (if (fs/exists? kb-path) (slurp kb-path) "[]\n")]
    {:npm (ensure-npm! (joyride-dir opts) opts nil)
     :activate (unignore-npm! opts)
     :keybindings (merge-keybindings! (assoc opts :keybindings-fragment fragment))
     :clashes (clashing-keys existing fragment)
     :agents-md (clear-agents-init-check! opts)
     :dry-run (boolean (:dry-run opts))}))

(def user-context-block
  (str user-context-begin "\n"
       "(let [vscode (js/require \"vscode\")]\n"
       "  (.executeCommand (.-commands vscode) \"setContext\" \"workshop:open\" true)\n"
       "  (.executeCommand (.-commands vscode) \"setContext\" \"flares:active\" true)\n"
       "  (.executeCommand (.-commands vscode) \"setContext\" \"pastedown:active\" true)\n"
       "  (.executeCommand (.-commands vscode) \"setContext\" \"keybinding-palette:active\" true))\n"
       user-context-end "\n"))

(defn copy-user-sources!
  "Copies workshop Joyride sources into the user src directory when absent."
  [opts]
  (let [root (repo-root opts)
        dest-root (fs/path (resolve-user-joyride-dir opts) "src")
        files (or (:user-source-files opts) user-source-files)
        dry? (:dry-run opts)]
    (mapv (fn [rel]
            (let [from (fs/path root ".joyride" "src" rel)
                  to (fs/path dest-root rel)
                  to-str (str to)]
              (when-not (fs/exists? from)
                (throw (ex-info "Missing workshop Joyride source"
                                {:path (str from) :babashka/exit 1})))
              (cond
                (fs/exists? to)
                {:path to-str :action :skipped}

                dry?
                {:path to-str :action :would-write}

                :else
                (do
                  (fs/create-dirs (fs/parent to))
                  (fs/copy from to {:replace-existing false})
                  {:path to-str :action :wrote}))))
          files)))

(defn ensure-user-contexts!
  "Appends the context block to user_activate.cljs, or creates that file."
  [opts]
  (let [path (str (fs/path (resolve-user-joyride-dir opts) "scripts" "user_activate.cljs"))
        dry? (:dry-run opts)
        existing (when (fs/exists? path) (slurp path))]
    (cond
      (and existing (string/includes? existing user-context-begin))
      {:path path :action :skipped}

      dry?
      {:path path :action :would-write}

      (not existing)
      (do
        (fs/create-dirs (fs/parent path))
        (spit path (str "(ns user-activate\n"
                        "  (:require [\"vscode\" :as vscode]\n"
                        "            [joyride.core :as joyride]))\n\n"
                        user-context-block))
        {:path path :action :wrote})

      :else
      (do
        (spit path (str existing (when-not (string/ends-with? existing "\n") "\n") "\n" user-context-block))
        {:path path :action :wrote}))))

(defn install-user!
  "Copies Joyride sources to the user directory, installs npm deps there, and sets contexts."
  [opts]
  {:sources (copy-user-sources! opts)
   :npm (ensure-npm! (resolve-user-joyride-dir opts) opts npm-packages)
   :user-activate (ensure-user-contexts! opts)
   :dry-run (boolean (:dry-run opts))})

(defn- print-actions! [label items]
  (when (seq items)
    (println label)
    (doseq [{:keys [path action]} items]
      (println (str "  [" (name action) "] " path)))))

(defn- print-one! [label {:keys [path action]}]
  (println (str label ":"))
  (println (str "  [" (name action) "] " path)))

(defn print-result! [result]
  (print-one! "npm" (:npm result))
  (print-one! "Activate" (:activate result))
  (print-one! "Keybindings" (:keybindings result))
  (print-one! "Agents.md" (:agents-md result))
  (when (seq (:clashes result))
    (println "Keybinding clashes:")
    (doseq [k (:clashes result)]
      (println (str "  " k)))))

(defn print-user-result! [result]
  (print-actions! (if (:dry-run result) "Sources (dry-run):" "Sources:")
                  (:sources result))
  (print-one! "npm" (:npm result))
  (print-one! "User activate" (:user-activate result)))
