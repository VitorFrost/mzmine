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
- **Scientific/provenance risk:** low if the fallback hashes the exact UTF-8 contents returned by the
  GitHub connector. High if Git blob SHA values are mislabeled as SHA-256 or if hashes are entered
  manually without reproducible calculation.
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
- **Validation criteria — defined before implementation:**
  - all 12 adapter files are fetched from the exact working branch/revision through GitHub;
  - every manifest SHA is 64-character lowercase SHA-256, not a Git blob SHA;
  - CI recalculates each hash from the checked-out file and accepts the unchanged sources;
  - changing one adapter byte causes preparation to fail before source copying;
  - no raw-host/network dependency is added to the workflow or scripts;
  - the upstream oracle commit and source-closure hash remain unchanged.
- **Implemented change:** not implemented.
- **Validation result:** not run.
- **Decision and lessons:** provenance capture must not depend on an assumed network path. The
  checked-out repository remains the final source of truth, and the preparer must verify hashes
  locally on every run.
