# Coding agent architecture

```
UI (CodingScreen, DevToolsPanel)
        │
   CodeStudio ── ProjectStore (files, snapshots, zip)      code/ProjectStore.kt
        │     ├─ Templates (offline starters)              code/Templates.kt
        │     ├─ CodeParser + StaticChecker + FixLoop      code/CodeAgent.kt
        │     ├─ TermuxRunner (copy project + run)         code/TermuxRunner.kt
        │     ├─ DevEnv (catalogue, inventory, installer)  code/DevEnv.kt
        │     └─ DevResearch (GitHub/SO/MDN)               research/DevResearch.kt
        │
   LlmEngine.complete()  ← on-device model via LiteRT-LM (no cloud)
```
Workflow for "build X": snapshot → model returns `{"files":[...]}` → every path validated → files written → static checks → up to 3 fix rounds (same problem set is never retried; a round that adds problems is rolled back) → report. The report says *static checks only* unless a program was actually run.

Design choices: the simplest architecture that works (one store, one loop); JSON file protocol because it is easy to validate; the model is never given a shell.
