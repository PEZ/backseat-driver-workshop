<div class="slide cols-6-6">

# Keep the REPLs connected

<div class="pane">

## Lest the agent works REPL-less!

The workshop starts **Try Clojure** on window start (and connects on reload if the nREPL is alread running). 

Here's how to do the same for **Babashka**.

1. Create a task that starts the repl and connects it (like Calva Jack-in)
   * **Input** with Joyride code + repl start task + connect task
2. Start the task on workspace activation, also with ...

![Joyride!](../images/joyride-logo.png)

</div>

<div class="pane">

In [workspace_activate.cljs](../.joyride/scripts/workspace_activate.cljs), add to `(defn- main [] ...`

```clojure
(repl-connect/start!
  {:connect-task "Babashka"
   :repl-task "Babashka repl"
   :connect-sequence "Babashka"
   :session-key "bb"})
```

In [tasks.json](../.vscode/tasks.json) - add to `"inputs": [`:
```jsonc
    {
      "id": "bbConnect",
      "type": "command",
      "command": "joyride.runCode",
      "args": "(require '[repl-connect]) (repl-connect/connect! {:connect-sequence \"Babashka\" :port 6667}) \"ok\""
    }
```

In [tasks.json](../.vscode/tasks.json) - add to `"tasks": [`
```jsonc
    {
      "label": "Babashka repl",
      "type": "shell",
      "command": "bb --nrepl-server 6667",
      "options": {
        "cwd": "${workspaceFolder}"
      },
      "isBackground": true,
      "presentation": {
        "reveal": "never",
      },
      "problemMatcher": {
        "pattern": {
          "regexp": "^(~never~)$",
        },
        "background": {
          "activeOnStart": true,
          "beginsPattern": ".",
          "endsPattern": "Started nREPL server"
        }
      }
    },
    {
      "label": "Babashka",
      "dependsOn": [
        "Babashka repl"
      ],
      "dependsOrder": "sequence",
      "type": "shell",
      "command": "echo ${input:bbConnect}",
      "presentation": {
        "reveal": "never",
        "close": true,
      },
      "group": {
        "kind": "build",
        "isDefault": true
      },
    }
```

**NB**: Can't get it to stop respawning? Some bug in VS Code/Cursor (sometimed) makes the background tasks, with `problemMatcher`, sticky even after removing them. Defining a non-background task of the same name clears it... 

```jsonc
    {
      "label": "Babashka repl",
      "type": "shell",
      "command": "echo :boo",
    },
```

... Maybe. You need to terninate the old task, run this one, type something in its terminal to make it go away, then reload the window... Then maybe.

</div>

</div>
