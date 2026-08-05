# F-005 — Shell environment could not retrieve adapter sources for SHA-256 freezing

- **Status:** proposal-ready
- **First observed:** 2026-08-05 11:47 BRT
- **Scope:** provenance capture for the executable-adapter manifest
- **Input revision:** `202da5b9dfca0d87e8eedc279066ecebef2a8b4f`
- **Evidence:** local container command attempted to download the 12 versioned adapter sources from
  `raw.githubusercontent.com` and terminated before the first file with
  `curl: (6) Could not resolve host: raw.githubusercontent.com`.
- **Observed behavior:** the container shell had no DNS/network path to the raw GitHub host. No source
  file was downloaded and no SHA-256 was calculated by that command.
- **Impact:** the adapter manifest cannot yet be populated with independently calculated SHA-256
  values using the planned shell download route. Existing repository files and commits were not
  modified by the failed command.
- **Root-cause hypothesis:** outbound network/DNS is unavailable in the container execution
  environment, while the authenticated GitHub connector remains available. Confidence: high for the
  immediate cause; the exact infrastructure policy is not inferred.
- **Scientific/provenance risk:** low if hashes are calculated from the exact checked-out files. High
  if Git blob SHA values are mislabeled as SHA-256 or if hashes are entered manually without
  reproducible calculation.
- **Proposed correction — revision 1 (not implemented):**
  1. Fetch each adapter through the GitHub connector at the exact branch revision.
  2. Calculate SHA-256 locally from the returned UTF-8 content, preserving bytes exactly as written
     by the contents API.
  3. Store those SHA-256 values in the machine-readable adapter manifest.
  4. Make the CI preparer recalculate SHA-256 from checked-out adapter files and fail on any mismatch;
     therefore the connector-to-local calculation is only the initial manifest population, not the
     final trust gate.
  5. Do not add network fallback logic to the production/CI preparer; CI uses the already checked-out
     repository and must remain offline with respect to adapter provenance.
- **Proposal revision 2 — preferred, not implemented:**
  1. Do not fetch raw files through either the shell or a manual connector-to-local transcription.
  2. Add a deterministic local hash-inventory command that reads only the already checked-out
     adapter roots, maps each Java file to its FQCN, and emits path, byte length, and SHA-256 in sorted
     JSON.
  3. Run that command in GitHub Actions after checkout of the exact PR head and upload/print the
     resulting inventory as evidence.
  4. Use the CI-produced values to populate the locked executable-adapter manifest in a later commit.
     Once locked, the same command/preparer must compare current bytes with the frozen values and fail
     before copying on any mismatch.
  5. Keep inventory generation separate from lock verification: generation is allowed only when the
     manifest is intentionally being established or revised under a registered failure; ordinary CI
     performs verification only.
  6. No external network access is added to scripts or workflows. The repository checkout is the
     sole byte source.
- **Validation criteria — revised before implementation:**
  - the inventory command is deterministic across repeated runs on the same checkout;
  - all 12 adapter/boundary Java files are discovered exactly once and mapped to the expected FQCN;
  - every emitted hash is 64-character lowercase SHA-256, not a Git blob SHA;
  - the evidence records the exact PR-head commit used for hashing;
  - the locked manifest accepts unchanged checked-out sources;
  - changing one adapter byte causes verification/preparation to fail before source copying;
  - generation mode cannot silently rewrite the lock during ordinary CI;
  - no raw-host/network dependency is added to the workflow or scripts;
  - the upstream oracle commit and source-closure hash remain unchanged.
- **Implemented change:** not implemented.
- **Validation result:** not run.
- **Decision and lessons:** revision 2 supersedes revision 1 because it removes manual content
  transfer and hashes the exact repository checkout that CI will later verify. Provenance capture
  must not depend on an assumed network path; the checked-out repository remains the final source of
  truth.
