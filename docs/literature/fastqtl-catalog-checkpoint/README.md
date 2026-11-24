# FastQTL catalog checkpoint

**Admitted: one scoped upstream family and five source reviews.** The catalog
contains 250 papers, 159 scoped families (30 native +129 upstream), 1,760 reviewed
and 964 pending uses. All 1,755 previous reviews and 2,724 observations remain.
Three existing adapters bring the integration total to 162. Historical 2.165,
an invented local P212 run, whole-paper reproduction and full-goal completion
are explicitly unqualified.

Read the [workflow/source-role guide](../FASTQTL_CATALOG_WORKFLOWS.md) and
[prepared-data interface](../../FASTQTL_WORKFLOW.md). Source-role inputs are
frozen before admission; their false `source_role_review_applied` markers are
historical. Applied qualifications appear in the canonical papers/inventory,
review ledger and `fastqtl-source-role-coverage.json`.

The packet records 16 model/transaction tests per host, plan/inspect/verify on
Windows and Linux, guarded application on Windows, fifteen actual CLI refusals,
and all five roles on desktop/mobile preview and installed pages. The original
upstream qualification remains separate: eight direct-original comparisons,
two fresh refits, 240 independent R statistics and thirty selected Java tests.
The first Linux launch's Windows Git-pointer failure and the first browser
controller's earlier-task closure-bound refusal are retained. Corrected runs pass;
zero live exact-profile browser processes were checked without OS termination.

**Website gate:** full `checkWebsite` still fails on the same 33 existing missing
scientific-citation panels. No new panels are missing and the check is unchanged.
Add actual mapped citations to those panels and rerun the full check. The Pages
workflow continues withholding both literature reports. Do not infer hosted
report publication from a source push or passing local UI checks.

## Check the saved catalog

Use the exact source revision and Git history containing base commit
`4547f09cb445b88dd7dd5bf608550bcc025ad0a6`. In a shallow checkout, fetch that
commit first. Windows linked worktrees used from WSL require mapped `GIT_DIR`
and `GIT_WORK_TREE`; preserve their original Windows `.git` pointers.

```sh
python3 -B tools/literature/verify_fastqtl_catalog_checkpoint.py
```

This default checks saved public evidence, source hashes, the deterministic
catalog transition, all prior review invariants, the complete 871-entry manifest
and the archived 61-file pre-admission workflow context. It performs no model
fit and does not execute the older reader. Optional stronger checks are reported
individually:

```sh
python3 -B tools/literature/verify_fastqtl_catalog_checkpoint.py \
  --jar /absolute/path/to/exact-fastqtl-producer.jar \
  --private-root /absolute/path/to/fastqtl-catalog-733 \
  --cache /absolute/path/to/frozen-primary-XML-cache \
  --workflow-source-root /absolute/path/to/pre-admission-fastqtl-source \
  --historical-root /absolute/path/to/unchanged-cytometry-catalog-source \
  --historical-jar /absolute/path/to/exact-cytometry-producer.jar \
  --workflow-private-root /absolute/path/to/fastqtl-701
```

`--workflow-source-root` executes the unchanged FastQTL reader in its physical
pre-admission context and, in turn, the unchanged historical cytometry reader.
The native/scientific models are not fitted again. The exact source-built JAR is
identified in `STATUS.json`; current release assets have not been replaced.
Omitted optional checks report zero files or false execution flags.

`FILES.json` maps logical paths to deduplicated `evidence.zip` objects.
`predecessor-source/` objects contain the exact 61-file workflow context;
`predecessor-context-1/FILES.json` records its identities. `PRIVATE-FILES.json`
protects retained raw diagnostics, failed controls and local source evidence.
It does not authorize redistribution of private runtime/evidence. `OMISSIONS.json`
names reproducible copies/caches excluded from that freeze-time inventory.
Later reader, publication and cleanup receipts are separate.

**Errors:** exit 3 skips a dependent check whose exact permitted prerequisite is
missing. Restore that prerequisite through an allowed method or leave the check
skipped. Exit 2 means no valid qualification: retain the failure, restore exact
source/metadata/producer and fix the cause. Never update historical hashes to
force a pass. SHA-256 records integrity, not execution authentication.
