<div class="slide">

# Backseat Driver is Project Editor **Situated**

Connected to your work/project via Calva

* Situated support from **vscode-mcp**
* Two external paths to the MCP server:
  1. **stdio** wrapper/relay
  2. **bb mcp** a babashka task in the **vscode-mcp registry**

```mermaid
%%{init: {"theme": "base", "themeVariables": {"background": "#0d1117", "primaryColor": "#161b22", "primaryTextColor": "#e6edf3", "primaryBorderColor": "#8babf1", "lineColor": "#8b949e", "secondaryColor": "#1c2738", "tertiaryColor": "#161b22", "clusterBkg": "#111820", "clusterBorder": "#303d4c", "edgeLabelBackground": "#1f1f1f00", "fontFamily": "-apple-system, BlinkMacSystemFont, Segoe UI, sans-serif", "fontSize": "22px"}, "flowchart": {"curve": "basis", "nodeSpacing": 40, "rankSpacing": 24, "padding": 16}}}%%
flowchart LR
    subgraph IDE["VS Code"]
        N["Copilot"]
        F["Cursor · ECA"]
        I["Claude · Codex · ..."]
        subgraph BD[" "]
            M["MCP server"]
            T["Backseat Driver<br/>Tools + skills"]
        end
        C["Calva"]
        N <-->|"Native tools"| T
        M <--> T
        T <-->|"Calva API"| C
        P["Project<br/>documents"]
    end
    E["TUIs / apps<br/>Copilot · Cursor · Claude TUI · Codex · ..."]
    G["Claude app · Grok Bot · ..."]
    W["MCP stdio<br/>wrapper"]
    Q["bb mcp"]
    D[("vscode-mcp<br/>registry")]
    H["You"]
    R["Running<br/>application"]

    I <-->|"stdio"| W
    E <-->|"stdio"| W
    F <-->|"stdio"| W
    W <-->|"TCP"| M
    G <-->|"Commands"| Q
    Q <-->|"TCP"| M
    D -.->|"Discover"| E
    D -.->|"Discover"| I
    D -.->|"Discover"| G
    H <--> C
    C <-->|"Edit"| P
    C <-->|"REPL"| R

    classDef agent fill:#1c2738,stroke:#8babf1,color:#e6edf3,stroke-width:2px;
    classDef core fill:#ffa657,stroke:#ffbd80,color:#0d1117,stroke-width:3px,font-weight:bold;
    classDef mcp fill:#3d2416,stroke:#ffa657,color:#ffd4a8,stroke-width:2px;
    classDef live fill:#19291d,stroke:#65c74a,color:#dcf4d5,stroke-width:2px;
    classDef human fill:#65c74a,stroke:#8dde76,color:#0d1117,stroke-width:3px,font-weight:bold;
    class N,I,E,G,F agent;
    class T,C core;
    class M,W,Q,D mcp;
    class P,R live;
    class H human;
    style IDE fill:#111820,stroke:#536477,stroke-width:2px,color:#e6edf3
    style BD fill:#292017,stroke:#ffa657,stroke-width:2px,color:#ffa657
    %% linkStyle is 0-based declaration order. 3-8 = stdio/TCP/Commands. 12+14 = You + REPL.
    linkStyle 3,4,5,6,7,8 stroke:#8babf1,stroke-width:2px;
    linkStyle 12,14 stroke:#65c74a,stroke-width:2px;
```

</div>
