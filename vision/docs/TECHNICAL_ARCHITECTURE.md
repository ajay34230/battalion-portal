# Technical Architecture

```
UI (Compose, 18 screens)  ──►  ViewModels
                                   │
                          AgentOrchestrator  ◄── Planner (LLM or rule-based)
                           │        │
                  ToolRegistry   ConfirmationGate ── AuditLog (Room)
                           │
   ┌───────────┬───────────┼────────────┬──────────────┬────────────┐
 Device      Research    Memory      Comms         Termux        Tasks
 (intents,   (search,    (Room+FTS,  (Notif.       (gateway)     (WorkManager,
 a11y)       fetch,      optional    listener,                    AlarmManager)
             extract)    embeddings) Gmail)
   Core: DeviceProfiler · ModelManager · DownloadManager · NetworkMonitor · Prefs (DataStore)
   Voice: SttEngine(Vosk|Whisper|SpeechRecognizer) · TtsEngine(Android|Piper) · WakeWordService
   AI:    LlmEngine(RuleBased | LlamaCpp | LiteRT-LM)
```

Package layout (`vision/app`, `com.vision.assistant`): `core`, `ai`, `voice`, `memory`, `tools`, `research`,
`comms`, `termux`, `tasks`, `ui`, `service`.

Key design rules
1. Every capability sits behind an interface with a safe fallback (RuleBasedEngine, AndroidTts, SpeechRecognizer) so the app is
   useful on weak devices and before any model is downloaded.
2. The orchestrator reports only what a tool result confirms (`ToolResult.Success/Failure/NeedsConfirmation/NeedsPermission`).
3. Language handling: script detection (Devanagari vs Latin) plus Hinglish keyword lexicon for the rule-based parser; LLM prompt
   instructs reply in the user's language.
4. Offline-first; `NetworkMonitor` gates online tools and queues research steps for resume.
