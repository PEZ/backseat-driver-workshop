(ns workshop-init-test
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as string]
            [clojure.test :refer [deftest is run-tests]]
            [workshop-init :as wi]))

(deftest marked-block-wraps-fragment
  (let [block (wi/marked-block "  {\"a\": 1}\n")]
    (is (string/includes? block wi/begin-marker))
    (is (string/includes? block wi/end-marker))
    (is (string/includes? block "{\"a\": 1}"))))

(deftest merge-into-empty-array
  (let [out (wi/merge-keybindings-text "[]\n" "{\"title\": \"X\"}")]
    (is (string/includes? out wi/begin-marker))
    (is (string/includes? out "\"title\": \"X\""))
    (is (string/ends-with? (string/trimr out) "]"))))

(deftest merge-appends-with-comma
  (let [existing "[\n  {\"key\": \"a\"}\n]\n"
        out (wi/merge-keybindings-text existing "{\"key\": \"b\"}")]
    (is (re-find #"\}\s*,\s*// BEGIN workshop-init:next-slide" out))
    (is (string/includes? out "\"key\": \"b\""))))

(def activate-fragment
  "Minimal next-slide fragment with binding fingerprints."
  "{\n    \"title\": \"Activate Slide Mode\",\n    \"key\": \"ctrl+alt+j s\",\n    \"command\": \"joyride.runCode\",\n    \"args\": \"(prezo.next-slide/activate!)\"\n  }")

(deftest merge-skips-when-bindings-present
  (let [existing "[\n  {\"title\": \"Activate Slide Mode\", \"key\": \"ctrl+alt+j s\", \"command\": \"joyride.runCode\", \"args\": \"(prezo.next-slide/activate!)\"}\n]\n"
        out (wi/merge-keybindings-text existing activate-fragment)]
    (is (= existing out))
    (is (not (string/includes? out wi/begin-marker)))))

(deftest merge-inserts-when-only-markers-present
  (let [existing (str "[\n  {\"keep\": true},\n  "
                      wi/begin-marker
                      "\n  {\"old\": true}\n  "
                      wi/end-marker
                      "\n]\n")
        out (wi/merge-keybindings-text existing activate-fragment)]
    (is (not= existing out))
    (is (false? (wi/next-slide-bindings-present? existing activate-fragment)))
    (is (string/includes? out "(prezo.next-slide/activate!)"))))

(def activate-and-zoom-fragment
  "Next-slide fragment with activate and zoom bindings."
  "{\n    \"title\": \"Activate Slide Mode\",\n    \"key\": \"ctrl+alt+j s\",\n    \"command\": \"joyride.runCode\",\n    \"args\": \"(prezo.next-slide/activate!)\"\n  },\n  {\n    \"title\": \"Zoom In Slide\",\n    \"key\": \"cmd+=\",\n    \"command\": \"joyride.runCode\",\n    \"args\": \"(prezo.slide-zoom/zoom-in!+)\"\n  }")

(deftest merge-replaces-marked-block-without-duplicating-markers
  (let [existing (str "[\n  {\"keep\": true},\n  "
                      wi/begin-marker
                      "\n  {\"title\": \"Activate Slide Mode\", \"key\": \"ctrl+alt+j s\", \"command\": \"joyride.runCode\", \"args\": \"(prezo.next-slide/activate!)\"}\n  "
                      wi/end-marker
                      "\n]\n")
        out (wi/merge-keybindings-text existing activate-and-zoom-fragment)]
    (is (string/includes? out "(prezo.next-slide/activate!)"))
    (is (string/includes? out "(prezo.slide-zoom/zoom-in!+)"))
    (is (= 1 (count (re-seq (re-pattern (java.util.regex.Pattern/quote wi/begin-marker)) out))))))

(def sample-agents-md
  (str "# Workshop\n\n"
       "## Commits\n\n"
       "Commit often.\n\n"
       wi/agents-begin-marker
       "\n## Init check\n\n"
       "Run init when needed.\n"
       wi/agents-end-marker
       "\n"))

(deftest clear-agents-init-check-text-removes-section
  (let [out (wi/clear-agents-init-check-text sample-agents-md)]
    (is (not (string/includes? out wi/agents-begin-marker)))
    (is (not (string/includes? out wi/agents-end-marker)))
    (is (string/includes? out "## Commits"))
    (is (string/includes? out "Commit often."))
    (is (string/ends-with? out "\n"))))

(deftest clear-agents-init-check-text-no-op-without-markers
  (let [text "# Workshop\n\nNo init section here.\n"
        out (wi/clear-agents-init-check-text text)]
    (is (= text out))))

(deftest copy-and-merge-roundtrip-then-skip
  (let [tmp (fs/create-temp-dir {:prefix "workshop-init-test-"})
        repo (fs/path tmp "repo")
        joyride (fs/path tmp "joyride")
        kb (fs/path tmp "keybindings.json")
        agents-md (fs/path repo "AGENTS.md")]
    (fs/create-dirs repo)
    (fs/create-dirs repo)
    (spit (str kb) "[\n  {\"title\": \"Other\", \"key\": \"x\"}\n]\n")
    (spit (str agents-md) sample-agents-md)
    (let [opts {:repo-root (str repo)
                :user-joyride-dir (str joyride)
                :keybindings-path (str kb)}
          first-run (wi/exec! opts)
          second-run (wi/exec! opts)
          agents-after (slurp (str agents-md))
          kb-text (slurp (str kb))]
      (is (= :skipped (:action (:npm first-run))))
      (is (= :skipped (:action (:activate first-run))))
      (is (= :wrote (:action (:keybindings first-run))))
      (is (= :wrote (:action (:agents-md first-run))))
      (is (string/includes? kb-text "Activate Slide Mode"))
      (is (string/includes? kb-text "workshop:open"))
      (is (not (string/includes? agents-after wi/agents-begin-marker)))
      (is (string/includes? agents-after "## Commits"))
      (is (= :skipped (:action (:keybindings second-run))))
      (is (= :skipped (:action (:agents-md second-run))))
      (is (= 1 (count (re-seq (re-pattern (java.util.regex.Pattern/quote wi/begin-marker))
                              kb-text)))))
    (fs/delete-tree tmp)))

(deftest copy-prezo-keeps-existing-file
  (let [tmp (fs/create-temp-dir {:prefix "workshop-init-keep-"})
        repo (fs/path tmp "repo")
        joyride (fs/path tmp "joyride")
        kb (fs/path tmp "keybindings.json")
        src-dir (fs/path repo ".joyride" "src" "prezo")
        dest-dir (fs/path joyride "src" "prezo")
        dest (fs/path dest-dir "next_slide.cljs")]
    (fs/create-dirs src-dir)
    (fs/create-dirs dest-dir)
    (spit (str (fs/path src-dir "next_slide.cljs")) "(ns prezo.next-slide)")
    (spit (str (fs/path src-dir "next_slide_notes.cljs")) "(ns prezo.next-slide-notes)")
    (spit (str dest) "MINE")
    (spit (str kb) "[]\n")
    (doseq [pkg ["jsonc-parser" "turndown" "turndown-plugin-gfm"]]
      (fs/create-dirs (fs/path joyride "node_modules" pkg)))
    (let [opts {:repo-root (str repo)
                :user-joyride-dir (str joyride)
                :keybindings-path (str kb)
                :user-source-files ["prezo/next_slide.cljs"
                                    "prezo/next_slide_notes.cljs"]}
          result (wi/install-user! opts)
          actions (into {} (map (fn [m]
                                  [(str (fs/file-name (:path m))) (:action m)])
                                (:sources result)))]
      (is (= "MINE" (slurp (str dest))))
      (is (= :skipped (get actions "next_slide.cljs")))
      (is (= :wrote (get actions "next_slide_notes.cljs"))))
    (fs/delete-tree tmp)))

(deftest nested-workshop-keeps-agents-init-check
  (let [tmp (fs/create-temp-dir {:prefix "workshop-init-nested-"})
        parent (fs/path tmp "parent")
        repo (fs/path parent "workshop")
        joyride (fs/path tmp "joyride")
        kb (fs/path tmp "keybindings.json")
        agents-md (fs/path repo "AGENTS.md")
        assets (fs/path repo ".agents" "skills" "workshop-init" "assets" "prezo")]
    (fs/create-dirs parent)
    (fs/create-dirs assets)
    (process/shell {:dir (str parent) :out :string} "git" "init")
    (spit (str (fs/path assets "next_slide.cljs")) "(ns prezo.next-slide)")
    (spit (str (fs/path assets "next_slide_notes.cljs")) "(ns prezo.next-slide-notes)")
    (spit (str (fs/path repo ".agents" "skills" "workshop-init" "assets" "next-slide-keybindings.jsonc"))
          "{\n    \"title\": \"Activate Slide Mode\",\n    \"key\": \"ctrl+alt+j s\"\n  }")
    (spit (str kb) "[]\n")
    (spit (str agents-md) sample-agents-md)
    (let [opts {:repo-root (str repo)
                :user-joyride-dir (str joyride)
                :keybindings-path (str kb)}
          result (wi/exec! opts)]
      (is (= :skipped (:action (:agents-md result))))
      (is (string/includes? (slurp (str agents-md)) wi/agents-begin-marker)))
    (fs/delete-tree tmp)))

(deftest unignore-npm-text-removes-guard
  (let [text (str "(ns workspace-activate\n"
                  "  (:require\n"
                  "    " wi/npm-ignore-comment " leftover wording here\n"
                  "    #_pastedown))\n"
                  "(defn- main []\n"
                  "  " wi/npm-ignore-comment "\n"
                  "  #_(pastedown/activate!)\n"
                  "  #_(other/keep!)))\n")
        out (wi/unignore-npm-text text)]
    (is (string/includes? out "pastedown"))
    (is (string/includes? out "(pastedown/activate!)"))
    (is (string/includes? out "#_(other/keep!)"))
    (is (not (string/includes? out (str wi/npm-ignore-comment " leftover"))))
    (is (not (re-find #"#_pastedown" out)))
    (is (not (re-find #"#_\(pastedown/activate!\)" out)))))

(defn run! []
  (let [{:keys [fail error]} (run-tests 'workshop-init-test)]
    (when (or (pos? fail) (pos? error))
      (System/exit 1))))
