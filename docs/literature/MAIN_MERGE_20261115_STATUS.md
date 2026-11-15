# Main merge validation: 2026-11-15

**WARNING: whole-catalog verification does not pass on this merged source tree.**
The SingleR predecessor guard refuses the merged study/manuscript/cleanup source
changes relative to its historical baseline. This is an unresolved validation
limitation, not a passed catalog check or a new scientific result. Do not refresh
saved hashes, replace original evidence, or claim a newly qualified full catalog.

The merge preserves all eight current catalog metadata files exactly from main
commit `357c9301be726b3b6dc2a4e92cf71ff05ed053d2`. It retains the original
qualifications and historical evidence. The existing scoped integration counts
remain historical claims with their original boundaries. The iDEP 2.4.4 addition
is a source profile; it adds no implemented workflow or catalog credit.

The merged application passed **1,159 tests in 108 suites, with zero failures,
errors or skips**, under Windows JDK 21. Compilation, the library and executable
JAR builds, and `checkWebsite` passed. The packaged executable also started and
printed study cleanup help under JDK 25; this is not a JDK 25 test-suite run.

The newly retained initial velociraptor dossier is historical. All 14 archive
volumes and 2,452 payloads were checked against its original recorded identities.
This checks stored content integrity; it does not grant current workflow
qualification, repeat an original scientific computation, or replace the newer
dossier. All 336 iDEP source-profile members matched the captured original source.

## Reproduce the refusal and understand the next action

With the permitted saved primary-paper cache available in
`build/literature-review`, run from this checkout using a NEW output path:

```text
python -B tools/literature/tpmcalculator_catalog/catalog.py --verify --rescan-sources --out build/NEW-main-catalog-check
```

The current refusal is `FAIL2_TPMCALCULATOR_CATALOG_UNADMITTED`, caused by
`Preceding tracked source changed or baseline unavailable; preserve it and run a
separately reviewed predecessor integration`. The traceback identifies
`singler_catalog.catalog.predecessor`, whose Git comparison covers preceding
tracked files outside its specific exclusions. The original verifier remains
unchanged. Missing cached primary papers are a separate SKIP3 prerequisite,
and must not be mistaken for this source-baseline refusal.

**Required action before claiming full verification of the merged catalog:**
complete a separate predecessor integration that accounts for the study-source
changes, preserves the prior qualifications and this refusal, and runs the
appropriate source and scientific checks. A passing application test suite does
not override this guard. For the historical catalog checkpoint, use a separate
checkout of `357c9301be726b3b6dc2a4e92cf71ff05ed053d2` with the saved paper cache;
passing there would verify that checkpoint only.

Publication diagnostics, complete test results, the initial output-path/cache
failures, and the source-baseline refusal are retained locally under
`build/git-publish/20261115-all-main-1/`. Original working-copy files and all prior
research evidence remain preserved. The 250-paper implementation goal remains
open. The website workflow continues excluding the unfinished literature review
and warning pages from GitHub Pages deployment.
