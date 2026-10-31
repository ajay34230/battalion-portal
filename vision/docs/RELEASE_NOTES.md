# VISION release notes (Mission 2, phases 0-10)

Latest verified build: GitHub release `vision-dev-84` (CI compile and unit tests green). Nothing below has been run on a phone by me; device items are marked.

## Install
1. Download the APK from the newest `vision-dev-N` release on GitHub.
2. Install over the existing app (same signing key keeps data). If Android refuses, the signature differs: back up first, then uninstall and reinstall.
3. Open Settings -> Diagnostics after any slow or failed answer and share the log.

## Models
- Models & downloads: download verified (SHA-256) models. Small model for quick answers, Gemma 4 for photos, Qwen for code, optional EmbeddingGemma (165 MB) for meaning-based memory search.
- Settings -> Speed / Balanced / Quality chooses how the router picks a model. Default Balanced.
- Discover (Models screen) searches Hugging Face litert-community; only validated `.litertlm` links are accepted.

## Rollback
- Every build is a separate release; install an older `vision-dev-N` APK to go back (Android blocks downgrade unless you uninstall first, so use Settings -> Backup before).
- The database is backed up before an upgrade and a broken one is moved to `files/db_backups`, never deleted.

## Security and privacy summary
No destructive DB fallback, safe ZIP unpacking, Keystore-sealed voiceprint, strict URL/hash validation, no logging calls (guard test), 4 exported components, Termux writes go to a staging folder with backups.
Known issue: the signing key is in a public repo (see BUGS_AND_RISKS.md). Rotation needs one reinstall; awaiting your decision.

## Performance (honest status)
No speed numbers are claimed. Speed work done: streaming, one model call for most chat, router, 2-minute generation timeout, Diagnostics timing log. Measuring TTFT, tokens/s, RAM, battery and heat needs your phone: send the Diagnostics log. Colibri-class speed is not possible on this hardware; options A/B/C were given and are waiting for your choice.

## Not built / not tested
- llama.cpp (GGUF) engine: builds in CI, not integrated. ExecuTorch: evaluated only. Whisper/Piper: not built.
- Everything device-dependent (streaming, GPU, Termux, widgets, offline check in airplane mode, embeddings): NOT TESTED.
