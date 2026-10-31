# Security and Privacy Review (Mission 2)

Builds on `SECURITY_AND_PRIVACY.md` and Mission 1 phases 1 and 7.

## In place
Explicit DB migrations with backups; Keystore-sealed voiceprint; ZIP limits (SafeZip); staged Termux writes with backups; only four exported components; FileProvider limited to a swept folder; cleartext traffic off; backup/transfer excluded; SHA-256 on every model; no cloud AI API; untrusted-content wrapper for web text; confirmations and audit for sensitive tools.

## New risks introduced by this mission and required controls
| Risk | Control |
|---|---|
| Malicious model metadata (names, sizes, URLs) from discovery | Parse as data only; allow only https hosts on an allowlist (huggingface.co and its CDN); cap field lengths; never render as markup; never execute text from model cards |
| Unverified model files | Install only after SHA-256 match. For discovered models the hash comes from the repository's LFS metadata; show it to the user and refuse a mismatch |
| Malicious GGUF/archive | Size and path limits; llama.cpp parses untrusted files natively, so files are fetched only from the allowlist and only on user approval; keep llama.cpp pinned and updated |
| Prompt injection from downloaded docs/model cards | Existing untrusted-content wrapper; retrieved text can never trigger tools without the user's confirmation |
| Privacy leakage during discovery | Discovery sends only the search text and standard HTTP headers; no memory, chats or files; consent text shown on first use |
| Native memory-safety bugs | Pinned native library versions; crash-guarded first load (existing `loading` marker pattern) |
| Public repo exposes the signing key | Open item for the owner: rotate to a GitHub-secret key (needs one reinstall and a new Gmail SHA-1) |

## Not secure by design
Speaker recognition is a convenience filter, not authentication. Notification and payment parsing is best-effort.
