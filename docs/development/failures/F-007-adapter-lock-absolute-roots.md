# F-007 — Generated adapter inventory contains runner-specific absolute root paths

- **Status:** proposal-ready
- **First observed:** 2026-08-05 12:04 BRT
- **Scope:** locked executable-adapter provenance manifest
- **Input revision:** `96c899e15fd3326d5abcae70f056d5a4070fcac7`
- **Evidence:** successful workflow run `31018232351`, job `92347915173`, artifact
  `v408-executable-oracle-31018232351`, file `v408-adapter-inventory.json`.
- **Observed behavior:** all 12 files were identified and hashed correctly, and Java 21 compilation
  passed. However, every adapter record stores an absolute `root`, for example
  `/home/runner/work/mzmine/mzmine/oracle/v408-executable/adapters/src/main/java`. The inventory
  content SHA-256 therefore includes runner/workspace-specific paths.
- **Impact:** committing this JSON as the lock would cause verification to fail on Windows, another
  runner workspace, a developer checkout, or any different absolute repository path even when the
  adapter bytes are identical. It would also make the lock describe infrastructure rather than only
  repository content and logical source origin.
- **Root-cause hypothesis:** `inventory()` resolves roots for safe filesystem traversal and then
  serializes those resolved paths directly. Resolution is appropriate for reading files but not for
  canonical provenance. Confidence: high.
- **Scientific/provenance risk:** medium. Adapter bytes and compilation are correct, but a
  nonportable lock would produce false failures and encourage bypasses or manual lock regeneration.
  Removing the root entirely would also lose the distinction between executable adapters and the
  separately governed boundary root.
- **Proposed correction — revision 1 (not implemented):**
  1. Require each root to have a stable logical identifier supplied explicitly, for example:
     - `executable-adapters=oracle/v408-executable/adapters/src/main/java`;
     - `boundary-adapters=oracle/v408-boundaries/src/main/java`.
  2. Resolve the filesystem path internally for safe reading, but serialize only `root_id` and the
     path relative to that root.
  3. Reject duplicate/empty root identifiers, duplicate FQCNs, and files escaping the configured
     roots.
  4. Keep the file SHA-256 and byte length unchanged; regenerate the inventory and freeze the new
     environment-independent content hash.
  5. Update workflow and tests to use named roots. Do not accept an implicit current-working-directory
     identifier.
- **Validation criteria — defined before implementation:**
  - inventory output contains no `/home/runner`, drive letter, home directory, or absolute path;
  - the two roots appear only as stable `root_id` values;
  - identical adapter bytes under different temporary parent directories produce identical inventory
    JSON and content SHA-256;
  - root identifiers are required, unique, and validated;
  - the real inventory still contains exactly 12 adapters with the previously observed per-file byte
    lengths and SHA-256 values;
  - Java 21 compilation remains green;
  - lock verification passes in CI using the checked-out files and fails when one byte changes;
  - no network access or automatic lock rewrite is introduced.
- **Implemented change:** not implemented.
- **Validation result:** not run.
- **Decision and lessons:** reproducible provenance must separate logical source origin from the
  physical workspace path used to read bytes.
