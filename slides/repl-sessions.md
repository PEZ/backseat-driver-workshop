<div class="slide cols-6-6">

# Calva REPL sessions

<div class="pane">

- **Connection**: nREPL client <-> server
- **Session**: Evaluation <-> Clojure (et al) REPL
  - 1-2 sessions / connection: **primary**, **secondary**
  - Clojure project/repl types: clj (primary) + cljs (secondary)
  - All other project/repl types: only primary
  - Sessions have names and file patterns, defined per primary/secondary

## Session naming

- Default names based on project type
- Override defaults to get e.g. `backend` instead of `clj`, and `frontend` over `cljs`

## Session routing

- Project root path
- File patterns / globs (specificity ranked)
- Defaults often suffices

(shadow-cljs: session <- builds <- runtimes)

</div>

<div class="pane">

## Connect Sequences ~= Project Type

```jsonc
  "calva.replConnectSequences": [
    // Already defined in this project
    {
      "name": "Try Clojure",
      "projectRootPath": [
        "."
      ],
      "cljsType": "none",
      "projectType": "deps.edn",
      "menuSelections": {
        "cljAliases": []
      },
      "replSessionNames": {
        "primary": "try-clojure"
      }
    },

    // Not defined in this project, yet
    {
      "name": "Shadow fullstack",
      "projectType": "deps.edn",
      "cljsType": "shadow-cljs",
      "projectRootPath": [
        "projects",
        "shadow-w-backend"
      ],
      "replSessionNames": {
        "primary": "backend",
        "secondary": "frontend"
      },
      "afterPrimaryReplConnectedCode": [
        "(println :renamed-primary-to-backend)",
        "(println :renamed-secondary-to-frontend)"
      ],
      "menuSelections": {
        "cljAliases": [
          "dev",
        ],
        "cljsLaunchBuilds": [
          "app",
        ],
        "cljsDefaultBuild": "app"
      }
    },
```

</div>

</div>
