# Execution sandbox

* **HTML/CSS/JS**: Android WebView, JavaScript on, `blockNetworkLoads` on, content access off, universal/file-from-file access off, no access outside the project folder. Console output and errors are captured.
* **Python/Node/shell**: Termux only. Requires Termux Advanced mode and a confirmation dialog per run. The project is copied into `~/projects/<name>` (the script is limited to ~90 KB, paths are validated, file bodies travel as base64) and run with a 30-second `timeout`. Output is capped. Every run is logged in the activity log.
* The AI never gets a shell: it can only return files, which are path-validated and size-capped before writing.
* Limits: Termux processes are not memory-limited by VISION; network access inside Termux is not blocked.
