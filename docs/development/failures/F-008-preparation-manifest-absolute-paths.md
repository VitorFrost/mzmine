# F-008 — Executable source-preparation manifest contains absolute workspace paths

- **Status:** proposal-ready
- **First observed:** 2026-08-05 12:12 BRT
- **Scope:** deterministic executable source-set provenance
- **Input revision:** `92ff5d5a8c9a153e5e9b0640558c519f3163e34d`
- **Evidence:** successful workflow run `31018670376`, artifact
  `v408-executable-oracle-31018670376`, file `v408-executable-source-manifest.json`.
  The manifest records:
  - `adapter_roots` as `/home/runner/work/...` absolute paths;
  - every `source_files[].source` as an absolute upstream or adapter path;
  - content SHA-256 `4efa5b928a60ec0031bc4b8933b647a6c8d8449e9b92f474d46e5576bc174f12`, which therefore depends on the runner workspace.
- **Observed behavior:** source copying and Java 21 compilation succeeded, but the preparation evidence
  is not portable between runners, operating systems, or local checkouts even when all input bytes
  are identical.
- **Impact:** the preparation manifest cannot serve as a stable reproducibility lock or be compared
  across Linux/Windows. It would generate false differences and obscure whether an actual source
  byte, adapter contract, or only the workspace path changed.
- **Root-cause hypothesis:** the preparer correctly resolves filesystem paths for safe access but
  serializes those physical paths into the canonical manifest. Confidence: high.
- **Scientific/provenance risk:** medium. The scientific source bytes are unchanged, but unstable
  provenance can hide meaningful changes among environmental noise and encourage ignoring hashes.
- **Proposed correction — revision 1 (not implemented):**
  1. Rework preparation around the frozen adapter lock and adapter contract.
  2. Serialize only stable origins:
     - upstream source: `origin=upstream-v4.0.8` plus the repository-relative closure path;
     - adapter source: `origin=open-offline-adapter`, `root_id`, and root-relative path from the lock.
  3. Remove physical `adapter_roots` and absolute `source` fields from canonical output. Physical paths
     may appear only in transient error messages, never in the hashed manifest.
  4. Require exact equality among:
     - reached reviewed-boundary classes in the passing closure;
     - classes in the adapter lock;
     - classes in the semantic adapter contract;
     - actual checked-out adapter files.
  5. Refuse preparation before copying if any reached boundary lacks a locked adapter, a lock hash
     differs, a semantic contract is missing, or an unreviewed adapter exists.
  6. Record the adapter-lock SHA-256 and contract ID/hash in the preparation manifest.
- **Validation criteria — defined before implementation:**
  - preparation manifest contains no absolute path, `/home/runner`, drive letter, or checkout root;
  - identical inputs in different temporary directories produce byte-identical manifest JSON and the
    same content SHA-256;
  - all 12 reached boundaries are represented exactly once by locked adapters;
  - deleting an adapter, changing one byte, adding an undeclared adapter, or removing a contract
    entry causes failure before copying;
  - all 36 non-boundary source classes retain verified upstream v4.0.8 hashes;
  - preparation reports 48 classes, 47 source files, and 12 adapters;
  - Java 21 compilation remains green;
  - upstream commit and source-closure SHA-256 remain unchanged;
  - any new failure is registered before correction.
- **Implemented change:** not implemented.
- **Validation result:** not run.
- **Decision and lessons:** canonical evidence must describe logical repository origins and bytes, not
  the temporary filesystem used to assemble them.
