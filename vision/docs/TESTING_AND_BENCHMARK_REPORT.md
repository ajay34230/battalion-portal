# Testing and Benchmark Report

## What has actually run
CI (GitHub Actions) compiles the app and runs the JVM unit tests on every push; last green build: vision-dev-74 (commit 44b99ec). These cover parsing, policies, payments, memory ranking, ZIP/crypto/Termux-script safety, manifest guards, model status logic and diagnostics formatting.

## What has not run
No instrumented tests, no on-device tests, no airplane-mode test, no benchmark. This session cannot reach the phone. Therefore every speed, battery, thermal and offline claim is NOT TESTED.

## Benchmark plan (device, owner-run or adb-linked)
Per model and backend, record: load time, time to first token, tokens per second, total reply time, peak memory, battery drop over 10 replies, temperature. The in-app Diagnostics log (Settings > Diagnostics > Share log) already records load time, per-step generate time, and total reply time per model and backend. Streaming (Phase 1) adds time to first token. Results are copied into this file with the date and device.

| Model | Backend | Load | First token | Tokens/s | Reply total | Date |
|---|---|---|---|---|---|---|
| (no measurements yet) | | | | | | |
