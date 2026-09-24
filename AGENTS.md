# Backseat Driver workshop

This repo is a **next-slide** presentation deck: markdown slides, `slides.edn`, and CSS for VS Code’s markdown preview.

## Commits

Commit often. Short, plain English messages that say why.

<!-- BEGIN workshop-init:check -->
## Init check

Unless the human clearly wants something else this turn, check whether this workshop's Joyride setup still needs init, and run **workshop-init** when it does (or when they ask).

Clear intents that mean init help: **hello**, **init**, **workshop-init**, get started, set up the workshop.

Init installs npm dependencies in `.joyride` when they are missing, removes the pastedown `#_` guards in `workspace_activate`, and merges the workshop keybindings. The scripts stay in this project. Load the **workshop-init** skill and follow it (MCP gate, path resolve, `bb init`). Do not hand-edit those files from a chat recipe.

Installing the same scripts into the user Joyride directory, for every window, is a separate ask. The skill covers that with `bb install-user-joyride`.

Successful `bb init` removes this Init check section from `AGENTS.md`.
<!-- END workshop-init:check -->
