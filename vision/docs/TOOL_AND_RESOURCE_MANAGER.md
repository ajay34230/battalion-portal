# Tool and resource manager

Permission categories as implemented:
* **A inspection** – the version scan (dialog explains it; needs Termux Advanced mode).
* **B/C download + install** – one dialog per tool showing: resource, purpose, reason, source, version note, licence, cost, approximate size, permissions, method, security note, and a low-storage warning. Buttons: *Approve & install* / *Reject*. Nothing runs before approval. The installer uses `pkg install -y <package>` in Termux (signed repository) with a 25-minute limit, then re-scans to verify and update the inventory.
* **D/E elevated and high-risk** – existing Termux confirm tiers; deleting project files takes a snapshot first.

Not implemented: arbitrary downloads from websites, signature/SHA verification of tools (pkg verifies its own signatures), pause/resume of tool installs, uninstall/repair/update flows, resource history. Model downloads (separate system) do have SHA-256 verification, pause/resume and storage checks – see TECHNICAL_ARCHITECTURE.md.
Android SDK and Godot are reported UNSUPPORTED with an explanation instead of offering a download that cannot work.
