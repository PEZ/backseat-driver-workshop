<div class="slide">

# Slide Zoom Keybindings

<div class="pane">

```jsonc
  {
    "title": "Zoom In",
    "category": "Next-slide",
    "key": "cmd+[Minus]",
    "command": "joyride.runCode",
    "args": "(require '[\"vscode\" :as vscode]) (if-let [f (try (requiring-resolve 'prezo.slide-zoom/zoom-in!+) (catch :default _ nil))] (f) (vscode/commands.executeCommand \"workbench.action.zoomIn\")) :zoomed-in"
  },
  {
    "key": "cmd+[Minus]",
    "command": "-workbench.action.zoomIn"
  },
  {
    "title": "Zoom Out",
    "category": "Next-slide",
    "key": "cmd+-",
    "command": "joyride.runCode",
    "args": "(require '[\"vscode\" :as vscode]) (if-let [f (try (requiring-resolve 'prezo.slide-zoom/zoom-out!+) (catch :default _ nil))] (f) (vscode/commands.executeCommand \"workbench.action.zoomOut\")) :zoomed-out"
  },
  {
    "key": "cmd+-",
    "command": "-workbench.action.zoomOut"
  },
  {
    "title": "Reset Zoom",
    "category": "Next-slide",
    "key": "cmd+0",
    "command": "joyride.runCode",
    "args": "(require '[\"vscode\" :as vscode]) (if-let [f (try (requiring-resolve 'prezo.slide-zoom/zoom-reset!+) (catch :default _ nil))] (f) (vscode/commands.executeCommand \"workbench.action.zoomReset\")) :zoom-resetted"
  },
  {
    "key": "cmd+0",
    "command": "-workbench.action.zoomReset"
  }
```

</div>

</div>
