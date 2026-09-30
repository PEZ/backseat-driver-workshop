(ns repl-connect
  (:require ["vscode" :as vscode]
            [joyride.core :as joyride]))

(defn- log!
  [msg]
  (.appendLine (joyride/output-channel) (str "repl-connect: " msg)))

(defn- ^:async activate-calva!
  []
  (if-let [extension (vscode/extensions.getExtension "betterthantomorrow.calva")]
    ;; exports throws while Calva is still activating during editor startup.
    (await (.activate extension))
    (throw (js/Error. "Calva is unavailable. Install or enable the Calva extension."))))

(defn- session-named?
  [calva session-key]
  (boolean (and session-key
                (some (fn [s]
                        (= session-key (.-replSessionKey s)))
                      (or (some-> calva
                                  .-v1
                                  .-repl
                                  .listSessions)
                          #js [])))))

(defn- task-named?
  [label]
  (boolean (some (fn [ex]
                   (= label (some-> ex .-task .-name)))
                 (.-taskExecutions vscode/tasks))))

(defn ^:async connect!
  "Connect Calva with :connect-sequence and confirm :session-key."
  [{:keys [connect-sequence port ]}]
  (await (activate-calva!))
  (await (vscode/commands.executeCommand
          "calva.connect"
          (clj->js (cond-> {}
                     connect-sequence (merge {:connectSequence connect-sequence})
                     port (merge {:host "127.0.0.1"
                                  :port port})))))
  "ok")

(defn ^:async start!
  "Skip if :session-key is connected; connect! if :repl-task is running; else start :connect-task."
  [{:keys [connect-task repl-task connect-sequence session-key]
    :as opts}]
  (let [calva (await (activate-calva!))]
    (cond
      (session-named? calva session-key)
      (log! (str connect-sequence " already connected"))

      (task-named? repl-task)
      (do
        (log! (str repl-task " already running"))
        (await (connect! opts)))

      :else
      (let [tasks (await (vscode/tasks.fetchTasks))
            task (->> tasks
                      (filter (fn [t]
                                (= connect-task (.-name t))))
                      first)]
        (if-not task
          (log! (str connect-task " task not found"))
          (do
            (await (vscode/tasks.executeTask task))
            (log! (str "started " connect-task)))))))
  "ok")
