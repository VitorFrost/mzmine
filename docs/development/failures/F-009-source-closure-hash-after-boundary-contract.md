# F-009 — Adapter contract references the pre-F-004 source-closure hash

- **Status:** proposal-ready
- **First observed:** 2026-08-05 12:18 BRT
- **Scope:** provenance binding between the source closure and executable adapter contract
- **Input revision:** `f3fa28ddc04c496bd600270befc699305824cdc9`
- **Evidence:** workflow run `31019493070`, job `92352205476`, step
  `Prepare locked executable source set`; error:
  `Executable source preparation refused: adapter contract targets the wrong source closure`.
  The preceding closure step passed with:
  - 2,585 indexed top-level types;
  - 48 reachable source types;
  - 99 dependency edges;
  - 12 reviewed boundaries;
  - zero wildcard imports, prohibited references, unresolved references, or violations;
  - new canonical closure SHA-256
    `6662c196b9487154cf7efb4aafb1574c42a6c90a554c8805c6585b5787a0ffd6`.
- **Observed behavior:** `adapter-contract.json` still references the earlier passing closure SHA-256
  `eeb5ac4f151b80b567da045b0fb1d360b5d754eafa212277ea0f4f696596cda3`. The closure hash changed
  because F-004 corrected boundary classifications, reasons, and preserved-member inventories. The
  graph metrics and upstream commit did not change. The new preparer correctly failed before source
  copying.
- **Impact:** the locked source set cannot be prepared until the semantic contract is rebound to the
  reviewed closure revision. Compilation and physical-path checks were skipped. No generated source
  was retained.
- **Root-cause hypothesis:** the adapter contract was created using the previously documented closure
  hash after the source-closure manifest had already been semantically corrected by F-004. Since the
  closure content hash includes boundary metadata, the old value became stale even though reachability
  remained identical. Confidence: high.
- **Scientific/provenance risk:** low for updating the binding after review; high if the hash check is
  removed or ignored. The changed boundary member inventory is directly relevant to the executable
  adapters and therefore should invalidate the old contract.
- **Proposed correction — revision 1 (not implemented):**
  1. Update `adapter-contract.json` to bind to the newly generated passing closure SHA-256
     `6662c196b9487154cf7efb4aafb1574c42a6c90a554c8805c6585b5787a0ffd6`.
  2. Update `docs/public_validation/V408_SOURCE_CLOSURE.md` to distinguish:
     - the historical first passing focused closure hash `eeb5...`;
     - the current semantically corrected closure hash `6662...`;
     - unchanged graph metrics and upstream source commit.
  3. Preserve the strict equality check in the preparer. Do not make the contract accept multiple or
     partial hashes.
  4. Record the contract file SHA-256 and current closure hash in the next preparation artifact.
- **Validation criteria — defined before implementation:**
  - source closure passes again with hash `6662...` and unchanged 48/99/12 metrics;
  - adapter lock still verifies as `1f3fa335...` with the same 12 source-file hashes;
  - preparation passes strict closure/lock/contract equality;
  - preparation manifest is created with no physical workspace paths;
  - counts remain 48 classes, 47 source files, and 12 adapters;
  - Java 21 preview compilation remains green;
  - documentation retains the historical hash rather than silently replacing it;
  - any subsequent failure is registered before correction.
- **Implemented change:** not implemented.
- **Validation result:** not run.
- **Decision and lessons:** semantic boundary metadata is part of the executable-oracle provenance.
  Correcting preserved members must intentionally invalidate and rebind downstream contracts.
