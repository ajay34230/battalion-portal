# Security and Privacy

- Local-first: conversations, memory and notification history stay in the app's private Room database.
- No analytics, no hidden uploads. Network calls only for user-initiated research/downloads; each is logged.
- Tool registry with allowlist: every action has declared inputs, permission, risk level (READ / PREPARE / SEND / DESTRUCTIVE).
  SEND and DESTRUCTIVE always need explicit confirmation; the model can never bypass `ConfirmationGate`.
- Web pages, emails, documents and notifications are **untrusted data**: wrapped in delimiters, stripped of instruction-like
  text for the planner, never allowed to trigger tools by themselves.
- Termux gateway: allowlist of command templates, argument validation, 30 s default timeout, output cap, audit log;
  no raw shell from the model.
- Locked mode: never reads OTPs/banking codes aloud (regex filter), hides notification bodies unless unlocked, no sensitive actions.
- Tokens (Gmail) in EncryptedSharedPreferences / Android Keystore. No keys in git.
- Download verification: SHA-256 required; unknown hash ⇒ refuse install.
- Backup/export is user-initiated; database can be wiped from the Privacy Centre.
- This repo also contains an unrelated battalion web portal; VISION lives only under `vision/` and its CI only triggers on `vision/**`.
  No battalion data is read, used or shipped in the APK.
