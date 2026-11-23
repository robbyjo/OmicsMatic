# SCENIC catalog admission and remaining full-goal work

The global `scenic-tools` workflow now has guarded catalog admission for five
individual RNA-only SCENIC roles. The catalog contains **155 scoped families
(29 native Java and 126 upstream), 158 including three existing adapters**.
There are **1,702 reviewed and 1,020 pending uses**, 250 papers, 446 named labels,
485 provider definitions and 2,722 positive observations. One upstream family
is added; its native AUCell component earns no separate family credit. All
1,697 prior reviews, 2,717 unrelated observations and 445 unrelated inventory
rows are unchanged. No positive observation was added or removed.

Read the [workflow guide](SCENIC_WORKFLOWS.md),
[runnable artificial example](../../examples/scenic-workflow/README.md),
[admission evidence](scenic-catalog-admission/README.md), and
[checkpoint reader instructions](scenic-catalog-checkpoint/README.md).
The earlier workflow packet retains its historical `catalog_admitted=false`
status; this later catalog transaction supplies admission without rewriting it.
Use a current source build; no release asset containing this command is claimed.

## Reviewed roles and limits

| Paper | Preserved source role and boundary |
| --- | --- |
| P058 | Raw Seurat counts, mouse TF/motif resources, grn/ctx/AUCell, reported NES 2.5 and min_genes 10. Edition, seed and exact reference bytes remain unknown; cluster scaling and pheatmap are separate. |
| P070 | Reported pySCENIC 0.10.3 and a per-cluster 10% expression filter. **That historical analysis is SKIP3 under the available 0.12.1 profile.** Its semantic use is reviewed; no version equivalence or replacement analysis is asserted. |
| P100 | Reported 0.12.1, GRNBoost2 multiprocessing, 5% sampling per broad cell type and dropout masking; preserve the v9/mc9nr promoter references. Exact reference bytes, seed and fit/activity membership are not inferred. |
| P145 | Reported 0.12.1 and GRNBoost2 on 20,000 sampled normalized/log-transformed nuclei, with activity on the full dataset and distinct v10 resources. Fitting and activity matrices remain separate; downstream averages and Wilcoxon are separate. |
| P225 | Orthogonal expression/ATAC comparator. Motif-assignment limitations and author-reported overlaps remain author observations. Edition, seed, options and references remain unreported. |

Literal extraction hashes, locators and near-name edition fields are unchanged,
including null fields when an edition is known only from broader context.
Source-role qualifications and the review ledger retain broader reported editions
separately from the available runtime profile. These reviews establish neither
original author environments nor biological or whole-paper reproduction.

## Validation

Sixteen model/transaction tests passed on both Windows and Linux. They cover
family/review credit, every edition boundary, fitting/activity separation,
unchanged extractions, no SCENIC+ credit, source/producer refusals and transaction
rollback with concurrent writers. Ten actual CLI controls on both hosts refused
missing or altered primary text, a wrong executable, changed plans and changed
preview bytes without modifying canonical metadata. Independent inspect and
post-install verify commands passed on both hosts.

Fresh checks bound all 250 primary XML identities and DOIs to the frozen cohort,
34 matching paragraphs and seven additional body/table/code structures. This
is a source census, not a claim that every supplement or linked repository is
fully reviewed. P153's SCENIC+ benchmark remains a separately retained candidate.

Styled preview and installed desktop/mobile checks passed, with no page errors,
correct five-role versions/skips, correct family counts and retained smartpca/
Squidpy renderers and FUSION exclusions. The initial browser selector failed
before canonical modification; its console, screenshot and exact harness remain.
The corrected harness selected the explicit reviewed-version element. Both
isolated browsers closed gracefully; no unrelated process was terminated.

The complete catalog checksum manifest has 863 entries. Admission rereads the
unchanged workflow qualification: 2,069 selected normal tests, four full original
scientific comparisons with zero observed activity error, 14 workflow scenarios
and 13 integrity commands. Catalog validation does not claim fresh GRN/CTX fits.
Exactly four archived predecessor catalog inputs are supplied to the historical
workflow reader; live scientific source, examples, dispatcher and exact producer
remain strict. No historical verifier or hash was changed.

## Warnings and required action

**SKIP3: dependent analysis skipped.** Supply/install the named permitted exact
Linux runtime, compatible matrices/TFs/motif references and exact producer JAR,
then retry a new output directory. If installation or a required download is
unavailable or forbidden, keep the dependent analysis skipped. P070's 0.10.3
edition requires separate qualification; the current profile does not replace
it. Cell Ranger remains excluded by the user.

**FAIL2: no complete result.** Preserve raw, partial, failed, undefined and
superseded evidence. Resolve the named input, source, numerical, capacity,
deadline or integrity cause, then retry fresh paths. Read `WARNINGS.md`,
`analysis-status.json`, all task ledgers and every activity eligibility flag.
An explicitly allowed empty or unreportable capture does not make its numeric
fields eligible. `--inspect` checks closure/metadata; full VERIFY performs fresh
original GRN/CTX calls and native scoring. Never repair IDs, replace NaN/Inf,
clamp outputs or refresh historical hashes to bypass a failure.

**Full website validation still fails.** The same 33 existing vignette files
lack scientific citation panels; the first failure is `broad-gsea-tools.md`.
The saved website validation lists every path and identity. Add appropriate
mapped citations and rerun full `checkWebsite`; do not weaken the gate or add
empty panels. No new missing panel was found. Both literature reports remain
excluded from Pages by the unchanged workflow. Their repository HTML and local
preview are updated; publication of either literature report is not claimed.

**GitHub alone is insufficient for a machine handover.** Privately transfer the
full `JLinAlg-Omics-SAIGE-20261026` WSL distribution, the working runtime at
`/var/tmp/omicsmatic-scenic-runtime-20261123-274`, exact retained producer JARs,
primary XML cache and original/failed local evidence. The exact current producer
is `build/scenic-3/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar` inside
the owned checkout: 199,085,384 bytes, SHA-256
`ae5f81ed6a520a1a9f4be32f6558ecb2120b20ffbaa2248b701f8aafcecf3a2a`.
Retain older producers for their own captures. Transfer is not yet complete.
All 19 worktrees and restricted historical refs remain local and preserved;
follow [the prior handover](HANDOVER_SCENIC_WORKFLOW_2026-11-23.md) for exact
restrictions and private locations. Do not export restricted histories indirectly.

## Continue the full objective

Review P153's Supplementary Note 2 and linked code to distinguish execution of
SCENIC+ from reuse of predictions before correcting or reviewing that observation.
Keep SCENIC+ distinct from this RNA-only workflow and the P094 SCENIC+ context.
Then prioritize remaining capabilities by actual paper impact, inspecting the
existing nf-core and newest iDEP development before restarting either. Current
iDEP terms do not establish legacy iDEP.96 rights. Continue all 1,020 pending
paper-use reviews and missed-provider screening across the frozen 250-paper
cohort, including supplements and linked code. Keep the original 2023-10-23
through 2026-10-23 cohort window. The full implementation objective is unfinished.

The owned checkout is `D:/projects/JLinAlg/build/fu-1`, branch
`codex/scenic-catalog-20261123`. Preserve `build/scenic-catalog-355/` and root
`build/git-publish/20261123-scenic-catalog-355/`, alongside preceding SCENIC
evidence and runtimes. Cleanup may remove only verified disposable copies after
publication and must retain raw source, failed tests, captures and reports.
