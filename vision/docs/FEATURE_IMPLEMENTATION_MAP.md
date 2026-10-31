# Feature Implementation Map (Phase 0)

Honest status: IMPLEMENTED (code + unit tests in CI), UNVERIFIED-ON-DEVICE, PARTIAL, NOT BUILT.

| Feature | Status | Where | Notes |
|---|---|---|---|
| Chat with local LLM (LiteRT-LM) | IMPLEMENTED, UNVERIFIED-ON-DEVICE speed | `ai/*`, `:litert` | GPU only for Gemma |
| Rule-based fast commands + JSON planner | IMPLEMENTED | `Orchestrator`, `PlanParser` | |
| Reminders, events, routines | IMPLEMENTED | `tasks/*` | |
| Payments from notifications | IMPLEMENTED, UNVERIFIED-ON-DEVICE | `finance/*` | no SMS |
| Gmail payment sync | PARTIAL | `finance/Gmail.kt` | needs user's OAuth client |
| Voice: push-to-talk + TTS | IMPLEMENTED | `voice/*` | |
| Wake word + enrolment | IMPLEMENTED, UNVERIFIED-ON-DEVICE | `service/WakeWordService`, `voice/Voiceprint` | convenience filter, not authentication |
| Memory | PARTIAL | `memory/SemanticIndex`, `MemoryDao` | TF-IDF only |
| Coding Studio | PARTIAL | `code/*`, `ui/Coding*` | build/test only through Termux |
| Termux control | UNVERIFIED-ON-DEVICE | `termux/*` | manual permission grant |
| Image / OCR / PDF | PARTIAL | `vision/SeeTool` | see Phase 5 |
| Model manager | IMPLEMENTED | `core/*` | state labels to be unified (Phase 8) |
| Widget | IMPLEMENTED, UNVERIFIED-ON-DEVICE | `VisionWidget` | |
| Whisper / Piper voices | NOT BUILT | - | sherpa-onnx not on Maven |
| On-phone APK building | NOT BUILT | - | unsupported |
