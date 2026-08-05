# F-010 — New README was sent to the GitHub update operation

- **Status:** proposal-ready
- **First observed:** 2026-08-05 12:29 BRT
- **Scope:** repository documentation write for the executable oracle
- **Input revision:** `aa27181e187e1cee9c8e93ac810914b2e39b4f0c`
- **Evidence:** GitHub connector rejected the attempted write to
  `oracle/v408-executable/README.md` because `GitHub.update_file` requires the current blob `sha`.
  The path had not been created, so no SHA existed and no repository mutation occurred.
- **Observed behavior:** a complete new-file payload was passed to the update operation instead of
  the create operation. Schema validation failed before contacting the repository write endpoint.
- **Impact:** the explanatory README was not added. Source, lock, contract, workflows, and validation
  evidence were unchanged.
- **Root-cause hypothesis:** the operation was selected from the intended content change rather than
  from the current existence state of the path. Confidence: high.
- **Scientific/provenance risk:** none. This is a fail-closed documentation-write error.
- **Proposed correction — revision 1 (not implemented):**
  1. Confirm the target path is new.
  2. Use `GitHub.create_file` with the same reviewed content and branch.
  3. For future writes, fetch an existing path before `update_file`; use `create_file` only when the
     path is absent.
- **Validation criteria — defined before implementation:**
  - `oracle/v408-executable/README.md` is created exactly once;
  - the content documents the locked hashes, scope, preparation rules, compilation result, and
    failure-first process;
  - no source, lock, adapter, workflow, or scientific setting changes in the documentation commit;
  - subsequent CI remains green;
  - any further failure is registered before correction.
- **Implemented change:** not implemented.
- **Validation result:** not run.
- **Decision and lessons:** file existence must determine the connector operation before preparing a
  write payload.
