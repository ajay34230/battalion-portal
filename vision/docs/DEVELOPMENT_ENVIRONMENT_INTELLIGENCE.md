# Development environment intelligence

* Hardware/OS: `DeviceProfiler` (RAM, storage, ABI, cores, API level, tier) + manufacturer/model shown in Dev tools.
* Tools: a catalogue of 17 tools (python, pip, node, npm, git, java, gradle, clang, go, rust, php, ruby, sqlite, typescript, android-sdk, godot). Each has a probe command, minimum version, Termux package, licence, approximate size.
* Scan: one confirmed Termux command prints `id=first line of version output` for every probe. Output is parsed into the statuses INSTALLED_AND_READY, INSTALLED_BUT_OUTDATED, PARTIALLY_INSTALLED, NOT_INSTALLED, INSTALLATION_REQUIRED (dependency missing), PERMISSION_REQUIRED (Termux not usable), UNSUPPORTED. INSTALLED_BUT_INCOMPATIBLE exists in the model but nothing sets it yet.
* The inventory (status, version, last verified time) is stored locally in `vision_devenv` preferences. Install location, dependency graph beyond `dependsOn`, update availability and storage use are **not** tracked yet.
* Requirement detection (`RequirementDetector`) is dynamic: it reads the request text and file extensions. HTML/CSS/JS-only projects need nothing.
* Tested in CI: version parsing/comparison, status rules, probe-script format, JSON round-trip, requirement detection. Untested: the real Termux scan.
