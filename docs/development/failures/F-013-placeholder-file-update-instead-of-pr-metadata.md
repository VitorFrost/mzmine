# F-013 — Placeholder file update was sent instead of PR metadata update

- **Status:** proposal-ready
- **First observed:** 2026-08-05 12:42 BRT
- **Scope:** GitHub action routing for PR #48 description
- **Input revision:** `8527c63c558d357388a02b754ebacb3667516d5c`
- **Evidence:** `GitHub.update_file` was called with placeholder SHA/content for the F-012 record and GitHub rejected it with HTTP 409. No repository mutation occurred.
- **Observed behavior:** despite F-012 identifying the correct target as PR metadata, a file-update action was selected again.
- **Impact:** PR #48 description remains unchanged; source and documentation files were not modified by the failed request.
- **Root-cause hypothesis:** action-selection lapse, not a data or repository-state problem. Confidence: high.
- **Scientific/provenance risk:** none; fail-closed connector behavior prevented mutation.
- **Proposed correction — revision 1 (not implemented):** cease all file operations for this task and invoke only `GitHub.update_pull_request` with PR number 48, title/body, and no repository file payload.
- **Validation criteria — defined before implementation:** PR #48 body is updated; the head commit does not change as a result of the PR metadata operation; no file is changed; the operation succeeds once.
- **Implemented change:** not implemented.
- **Validation result:** not run.
- **Decision and lessons:** after a routing failure, explicitly constrain the next operation to the intended resource namespace before invoking a tool.
