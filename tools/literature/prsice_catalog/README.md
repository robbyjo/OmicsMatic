# PRSice catalog admission

This guarded transition adds one upstream family and four reviews, and refines
the existing P150 review without duplicate credit. It checks all 250 primary
XMLs and the five frozen source roles. Raw versions, source anchors, separate
methods and unresolved historical choices remain explicit.

`close_prsice_catalog.py --plan`, `--inspect`, `--apply` and `--verify` require
NEW owned `--out` plus the exact `--cache`, `--jar`, `--private-root` and
`--workflow-source-root`. Inspect/apply also require the saved `--plan-dir`.
Plan and inspect leave canonical metadata unchanged. Apply conditionally writes
only its declared paths and preserves concurrent edits during rollback.

The exact 40-file predecessor context and its completed unchanged-reader receipt
must be preserved before canonical/source changes. Original scientific captures,
warnings, errors, private runtimes and exact producer JARs remain private. No old
source or producer hash is refreshed. Missing/forbidden dependencies mean SKIP3;
invalid or changed evidence means FAIL2 and no complete result. Correct the
named cause and retry NEW output; install only where permitted.

Run the model/transaction tests without Python `-O`. Browser checks exercise
the unpublished review/warnings pages at desktop/mobile sizes. This admission
does not deploy them, certify historical paper editions or rerun original cohorts.
