# Dependency management

App: versions are pinned in `build.gradle.kts`; no dynamic versions. Models are SHA-256 pinned in `ResourceManifest`. Large files stay out of Git. New dependencies need a reason, licence check (THIRD_PARTY_COMPONENTS.md) and a CI pass.
Generated projects: templates use no third-party libraries. Installing libraries inside a project (pip/npm) is not automated; do it via Termux after reviewing the package. Security-advisory checks, conflict detection and rollback of installed tools are not implemented (project rollback is).
