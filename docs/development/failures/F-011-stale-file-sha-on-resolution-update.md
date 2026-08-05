# F-011 — Failure-record update used a stale GitHub blob SHA

- **Status:** validated
- **First observed:** 2026-08-05 12:34 BRT
- **Scope:** closing F-010 in the append-only failure history
- **Input revision:** `47bfad1708ea5324e3d9e308252fe9ed71b66bd6`
- **Evidence:** GitHub returned HTTP 409 for `docs/development/failures/F-010-new-file-sent-to-update-operation.md`: the supplied blob SHA did not match the current file.
- **Observed behavior:** the update was rejected before mutation because the branch file changed after the previously known SHA was obtained.
- **Impact:** F-010 remained open in its individual record; repository source and validation artifacts were unaffected.
- **Root-cause hypothesis:** concurrent sequential commits on the active branch invalidated the cached blob SHA. Confidence: high.
- **Scientific/provenance risk:** none; the write failed closed and concerns documentation only.
- **Proposed correction — revision 1 (recorded before implementation):** fetch the current file immediately before updating, preserve its content, and apply only the recorded validation addendum using the returned SHA.
- **Validation criteria — defined before implementation:** current SHA is fetched; F-010 is marked validated with commit `47bfad...`; no other content is removed; the update succeeds once; CI remains green before merge.
- **Implemented change:** the current F-010 file was fetched at blob SHA
  `33ca60b0c1d7f2553f1e36f3956a563f4510e1d8` and updated successfully in commit
  `2c0be3f54a121eefd416227e92cd42c332b20a16`.
- **Validation result:** F-010 is marked validated and its original observation, proposal, and criteria remain present.
- **Decision and lessons:** validated. Mutable branch writes use a freshly fetched blob SHA immediately before update, especially during rapid sequential documentation commits.
