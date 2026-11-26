# RFMix: source review before implementation

The complete frozen 250-paper main-text census contains **13 RFMix contexts across four papers**. All 13 contexts were reviewed; exact XML/paragraph hashes and locators are in [CENSUS.json](CENSUS.json), with paraphrased roles in [ROLES.json](ROLES.json). No article text is redistributed. Supplementary method details and actual runtime/adapter qualification remain pending.

| Paper | Reported version | Use and interpretation |
| --- | --- | --- |
| P041 | 1.5.4 | Local ancestry for Indigenous Australian/Papuan ancestry masking, global-ancestry verification and ancestry-specific demographic analyses. Exact parameters/reference details are delegated to supplementary notes. |
| P142 | Unreported | Per-chromosome inference with three 92-person reference groups, then per-locus averaging of African ancestry for mosaic chromosomal-alteration association analysis. |
| P184 | 2 | MHIB cohort curation based on disagreement between genetic ancestry and self-reported ethnicity. RFMix was explicitly excluded from the Diet Network performance benchmark. |
| P229 | 2 | Local ancestry for phased GRCh38 Omani/reference data, followed by distinct excess-ancestry, tract-dating and ABC summaries. Reports 10 EM iterations, 10 generations and 0.2 cM windows. |

These four source reviews are eligible for a later catalog transition. **They have not yet been applied to the canonical review ledger and receive zero implementation credit.** Current catalog totals remain 453 labels, 492 providers, 2,733 observations, 1,821 reviewed/912 pending uses and 171 scoped implemented families. RFMix remains missing.

## Warnings and required actions

**Keep v1.5.4 and v2 separate.** The original legacy download page redirected to a Google login during retrieval. Obtain a permitted original v1.5.4 archive with provenance, or retain that legacy runtime analysis skipped. A current v2 program does not implement the historical v1.5.4 recipe. P142's unreported version must stay unreported.

**Current source defaults differ from P229's description.** The inspected v2 source initializes RF-window size 50 and CRF spacing 5, interpreted as SNP counts. P229 describes default 0.2 cM windows. Require explicit settings in the future adapter; do not silently equate those current source defaults with the authors' defaults or claim an exact author revision. The inspected manual also labels the RF-window short option `-r`, while the actual source option table uses `-s` and reserves `-r` for the reference file. Validate actual original executable behavior before exposing options.

**No RFMix runtime is qualified yet.** Read-only Linux preflight found GNU compilers and make, but no RFMix, autoreconf, bcftools, bgzip or tabix on the inspected PATH and no RFMix-named private prefix under `/opt`. This is an initial prerequisite inventory, not evidence that private installation is forbidden. Inspect existing permitted prerequisites, then prepare an unmodified private runtime in a new prefix. If required installation is unavailable or forbidden, mark the dependent analysis SKIP3 and state exactly what must be supplied. Invalid data, changed runtime/source, numerical/resource failures or cancellation must produce FAIL2 without a complete result.

The [original v2 license](https://github.com/slowkoni/rfmix/blob/09599c19c69f81571c7a7aab4cd2d847a9e0633a/README.md) permits academic research use; other uses require separate Stanford permission. Existing session authorization covers the private academic research scope. Preserve all original notices and keep upstream source/binaries private. No extra user approval is needed for already authorized eligible work.

## Retained original source

[UPSTREAM.json](UPSTREAM.json) pins all 38 files and the original archive from `slowkoni/rfmix` commit `09599c19c69f81571c7a7aab4cd2d847a9e0633a`, which declares `v2.03-r0`. No source was modified, built, installed or executed in this review. This inspected current source is not asserted to be an author's exact historical revision. Original private source remains available for the next build stage.

Verify with Python 3.10+, the original frozen XML cache and the exact private source tree:

```console
python -B tools/literature/rfmix_source_audit.py --xml-cache /path/to/original/xml --upstream-source /path/to/original-v2/source
```

A pass checks all 250 original XMLs, all 13 main-text contexts, all four role anchors and all 38 original source-file hashes. It does not execute RFMix, validate a reference panel, complete supplementary-note review or reproduce a paper. Missing source prerequisites skip this source review with exit 3; changed/invalid evidence fails with exit 2. Restore exact permitted sources and parser context; retain failures and do not refresh old seals.

Next work is original runtime preparation, typed input/options contracts, complete output audits, direct upstream comparisons and fresh saved-input replay. The full 250-paper implementation goal remains active.
