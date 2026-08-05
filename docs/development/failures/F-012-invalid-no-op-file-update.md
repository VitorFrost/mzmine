# F-012 — Invalid no-op file update was sent instead of the intended PR update

- **Status:** proposal-ready
- **First observed:** 2026-08-05 12:40 BRT
- **Scope:** GitHub operation routing during PR documentation cleanup
- **Input revision:** `baedaa543a3ee24141d06cc830669f6a4bfaee8c`
- **Evidence:** `GitHub.update_file` was called for the already validated F-011 file without a required `content` field. Tool schema validation rejected the request before repository mutation.
- **Observed behavior:** an unrelated no-op file update was selected while the intended next operation was updating the PR description.
- **Impact:** no file, source, workflow, lock, contract, or PR was changed by the failed operation.
- **Root-cause hypothesis:** tool/action selection error during rapid sequential repository operations. Confidence: high.
- **Scientific/provenance risk:** none; the request failed before mutation.
- **Proposed correction — revision 1 (not implemented):** do not retry a no-op file update. Route the intended task to `GitHub.update_pull_request`, which does not require file content or create a source commit.
- **Validation criteria — defined before implementation:** PR #48 description is updated; no repository file changes are made for this correction; CI head remains unchanged; the operation succeeds once.
- **Implemented change:** not implemented.
- **Validation result:** not run.
- **Decision and lessons:** confirm both target resource type and mutation intent before selecting a connector action; a PR metadata update is not a file operation.
