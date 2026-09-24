---
name: workshop-init
description: >-
  Set up Joyride for this workshop deck. Use when: the user says hello, init,
  or /workshop-init, wants to get started or set up the workshop, install
  next-slide, presentation shortcuts are missing, or npm deps for Joyride are
  missing. Also when they ask to install the Joyride utils for every window.
---

# Workshop init

This skill sets up Joyride for the deck. The scripts already live in `.joyride`. Init installs npm dependencies there, removes the npm ignore marker in `workspace_activate`, and merges keybindings. It does not copy scripts into `~/.config/joyride`.

## When to use

- User says **hello**, **init**, or **workshop-init**
- User wants to get started or set up the workshop
- Presentation shortcuts or Joyride npm packages are missing
- `AGENTS.md` **Init check** section says to run workshop-init

Installing the same scripts for every window is a separate ask. See [Every window](#every-window). Do not do that on hello.

## MCP gate (before path resolution)

Init needs a live Joyride evaluate path. On Cursor and other MCP harnesses, that is the **Joyride** MCP server. This workshop also expects **Backseat Driver** MCP when the harness uses MCP for it.

**Do not continue** with `bb init --editor …` or guessed paths when Joyride evaluation is unavailable. Resolve the connection first. Stop and wait for the human when you cannot.

### GitHub Copilot in VS Code / Cursor Copilot

Copilot uses Joyride and Backseat Driver as Language Model tools, not as MCP. There is no MCP gate. Use those tools for evaluate / REPL. If a tool is missing, ask the human to enable the Joyride and Backseat Driver extensions and their chat tools, then stop until that is done.

### Cursor (Agent / MCP)

1. Check that Joyride MCP tools work (e.g. evaluate `(+ 1 1)`). Check Backseat Driver MCP the same way when you will use it.
2. If either server failed to start or is missing from the session:
   - Open the Customize MCPs tab: [Customize → MCPs](cursor://anysphere.cursor-deeplink/mcp) (or Command Palette → **Open MCPs**).
   - Find **joyride** and **calva-backseat-driver** (names may vary slightly in the UI).
   - Reload: toggle the server off and on, or remove and re-add it. Check **Output → MCP Logs** if it still fails. Restart Cursor only if reload does not bring the tools back.
   - Tell the human what you need them to do. **Stop.** Do not run `bb init` until Joyride evaluate works again in this chat.
3. If the servers are not configured at all, use the registry path below to obtain wrapper/port config, then return to Customize → MCPs to add or repair them. Still stop until tools work.

### Other agents (ECA, TUI, CLI, …)

If Joyride / Backseat Driver MCP tools are not in this session:

1. Open registry home: `~/.config/vscode-mcp/registry` — read `AGENTS.md` and `README.md` there.
2. Prefer `bb list` from that directory (live windows). Only if servers for this window are **missing** from the harness config, use a registry entry’s `mcp` fields (`wrapperPath`, `portFilePath`, `host`) to write or update the harness MCP config, then ask the human to connect.
3. If entries exist but the harness still cannot attach, follow `bb-mcp.md` (`bb mcp --readme` …) or stop and ask the human. Do not guess paths and continue init.

## Before you write files

1. Confirm once with the human. Init writes `keybindings.json` and may run `npm install` in this project's `.joyride`.
2. Prefer the deterministic installer. Do not invent a manual copy recipe. Do not copy files into `~/.config/joyride` during init.
3. `bb init` is idempotent. Report wrote vs skipped, and any keybinding clashes, from the installer output.

## Install (agent)

1. **Resolve paths with Joyride** (MCP gate must already pass). Evaluate:

```clojure
(require '[workshop-init] :reload)
(workshop-init/paths)
```

That returns `{:user-joyride-dir ... :keybindings-path ... :repo-root ...}`.

2. **Run the installer** from a shell (cwd = `:repo-root`):

```bash
bb init --repo-root <repo-root> --user-joyride-dir <user-joyride-dir> --keybindings-path <keybindings-path>
```

Optional: add `--dry-run` first to preview. Do not ask the human to run the Joyride script as the install step. Joyride is only for path resolution.

## Shell for humans (not an agent fallback)

When a human runs install themselves without an agent:

```bash
bb init --editor cursor
bb init --user-joyride-dir ~/.config/joyride --keybindings-path "/path/to/User/keybindings.json"
bb init --dry-run
```

Agents must not use `--editor` to bypass a failed Joyride MCP.

## What `bb init` does

The slide runtime is hosted in `.joyride/src/prezo` (`next_slide.cljs`, `next_slide_notes.cljs`, `slide_zoom.cljs`). Flares, pastedown, and the keybinding palette are hosted in `.joyride/src`. This window loads those files. Init does not copy them anywhere.

1. Runs `npm install` in `.joyride` when `jsonc-parser`, `turndown`, or `turndown-plugin-gfm` is missing and `package.json` is present.
2. After those modules exist, removes each `#_` in `.joyride/scripts/workspace_activate.cljs` that sits under a comment starting with `;;#_`. There are two: `#_pastedown` in the `ns` require, and `#_(pastedown/activate!)` in `main`. Until then, workspace activate does not load pastedown. The wording after `;;#_` is not part of the match.
3. Merges workshop keybindings into `keybindings.json`. Chords use Joyride's `ctrl+alt+j` prefix. Arrow keys, page up, page down, F5, and zoom use `when: next-slide:active`. Zoom and restart use `cmd` on macOS and `ctrl` on Windows and Linux. Util chords use `flares:active`, `pastedown:active`, or `keybinding-palette:active`. Other next-slide chords use `workshop:open`. `workspace_activate` sets those contexts. A key that is already bound is still added, with its `when` clause, and reported as a clash. Tell the human which keys clash.
4. Removes the `<!-- BEGIN workshop-init:check -->` … `<!-- END workshop-init:check -->` section from repo `AGENTS.md` when present. Skips that edit when this folder is nested inside another git repo.

## Every window

When the human asks to install these Joyride utils for every window, run `bb install-user-joyride` with the same `--repo-root`, `--user-joyride-dir`, and `--keybindings-path` flags.

That task:

1. Copies `.joyride/src` files (`flares.cljs`, `pastedown.cljs`, `keybinding_palette.cljs`, and `prezo/*.cljs`) to `<user-joyride-dir>/src` when the destination file is absent. Never overwrites an existing file.
2. Runs `npm install jsonc-parser turndown turndown-plugin-gfm` in the user Joyride directory when those modules are missing.
3. Appends a marked context block to `<user-joyride-dir>/scripts/user_activate.cljs` (or creates that file) so every window sets `workshop:open`, `flares:active`, `pastedown:active`, and `keybinding-palette:active`. Skips when the block is already there.

The keybindings from `bb init` stay as they are. The contexts are what make them fire outside this window.

## After install

Report wrote vs skipped for npm, the activate script, keybindings, and agents-md. Report clashes by key. After a global install, report wrote vs skipped for each copied source, npm, and `user_activate.cljs`.

When keybindings were written, say slide mode is `ctrl+alt+j` then `s` inside this window.
