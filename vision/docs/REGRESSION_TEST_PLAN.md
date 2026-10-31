# Regression Test Plan

Automated (CI, JVM unit tests): parsers, payment logic, model policy, voiceprint maths, routine/search logic, coding helpers. New per phase: migration tests (Phase 1), path/ZIP tests (Phase 1), crypto wrapper tests (Phase 1, logic only).

Manual on iQOO 7 (BLOCKED until the owner runs them): app starts after update with data kept; chat answers; model switch; wake word; payment notification capture; Termux run; widget.

A test is PASSED only if it ran. Anything needing hardware, permissions or credentials is BLOCKED.
