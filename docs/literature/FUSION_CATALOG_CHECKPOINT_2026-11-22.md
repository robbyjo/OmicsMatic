# FUSION catalog checkpoint and remaining work

The original FUSION workflow is admitted as **one upstream family with six new
source reviews**. Current totals: **151 scoped families (27 native +124 upstream),
154 including three existing adapters, 1,679 reviewed and 1,043 pending uses**.
The same 250 papers, 446 labels and 485 provider definitions remain. Positive
paper-tool observations are now **2,722** because one cohort-name false positive
was removed and retained separately. The full objective is unfinished.

Run `fusion-tools` using the [beginner guide](FUSION_WORKFLOWS.md) and
[artificial examples](../../examples/fusion-original/README.md). The [qualified
source/runtime checkpoint](FUSION_QUALIFICATION_CHECKPOINT_2026-11-22.md) records
75 focused tests, 30 packaged cases, 17 offline controls, six runtime/resource
controls, independent Windows/Linux saved readers and original-output/payload
comparisons. Its zero-credit counts describe the earlier pre-admission state.
No existing release binary was replaced; build current source for this command.

## Reviewed paper roles

| Paper | Actual FUSION role and important boundary |
| --- | --- |
| P112 | CanUD TWAS with adult/fetal cortex weights, European 1000 Genomes LD and a weighted burden test. The reported reference-model/CV and association filters are distinct from an inferred caller command. |
| P120 | Hypothyroidism TWAS in six GTEx v8 tissues followed by internal COLOC. Gene-association P<2.97e-6 and PP4>0.75 are reported paper filters; exact trigger/prior/caller options are not established. |
| P131 | Default-setting univariate TWAS across 45 European GTEx v8 tissues, plus FUSION colocalization integrated with T-SEM. Genomic SEM0.0.5 and ACAT0.91 are separate package versions. |
| P181 | Prediction-model training and xTWAS for DDP/rDDP phenotypes over 114 traits, with Pantry KDP comparisons. Subsequent coloc-package analyses are separately named. |
| P183 | BSLMM/LASSO/top1/elastic-net training with five-fold CV, followed by TWAS of 14 traits for lncRNAs/protein-coding genes. CircRNAs explicitly excluded from FUSION; its LD is European 1KGP3, while HRS is used for SMR/OPERA. |
| P238 | A LeafCutter/FUSION software comparator, supported by Results separately from the Methods' shared ImpG-Summary-style formula. The formula alone does not establish original-script execution. |

Original positive extraction anchors, matched names, source versions/unknowns
and locators remain unchanged. Additional reviewed source pointers and specific
implementation limits are recorded. All 1,673 earlier reviews, 2,716 unrelated
observations, 445 other tool rows and non-tool paper metadata are conserved.

**P212:** FUSION names the diabetes cohort. Its exact original extraction and
source pointers remain in the [role audit](fusion-catalog-sources/ROLES.json)
and [coverage/exclusion record](omics-2023-2026/fusion-source-role-coverage.json),
with zero positive-use credit. It is absent from positive paper/tool counts.

**P108:** the paper's “univariate FUSION” describes a baseline. The archived
v1.0.0 manuscript training scripts import isoTWAS and call its univariate
elastic-net, BLUP and SuSiE functions; they contain no original FUSION command
names. We therefore retain this as method-baseline context without claiming
original FUSION execution or adding a positive software use. The manuscript
archive's published MD5, SHA-256, all member hashes and four script/line witnesses
are recorded; author code was neither executed nor redistributed. Exact installed
isoTWAS edition remains unestablished. See the [paper](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703692/)
and its [archived manuscript-code record](https://doi.org/10.5281/zenodo.8323001).
SuSiE prediction in that baseline is distinct from FUSION's SuSiE colocalization.

## Validation, provenance and source limitations

The guarded plan/inspect/apply transaction starts at
`25cbe501eb83049df29c16147a38b1dbbd590318`. It checks all 250 original XML hashes,
DOIs and complete FUSION candidate population, plus the preserved author archive,
the exact qualified producer and all eight predecessor metadata files. The
complete catalog checksum manifest increases from 853 to **855 entries**.
The installed model and manifest pass readback. Twelve source/model/rollback
tests pass on each host. Styled preview and installed desktop/mobile checks
verify six role summaries, both excluded positive-software rows, family/review
counts and prominent warnings. Each isolated browser closed gracefully with
zero remaining processes from its unique profile; no OS process-tree kill.

The [admission packet](fusion-catalog-admission/README.md) retains original
metadata, predecessor checksum manifest, planned registry, source-bound proposal
and qualification status. [Validation receipts](fusion-catalog-checkpoint/VALIDATION.json)
record actual checks. Use the current controller; older exact-catalog controllers
require their historical retained context. Do not refresh old verifier hashes.

```powershell
python -B tools/literature/close_fusion_catalog.py --verify --out build/NEW-fusion-catalog-verify
python -B tools/literature/verify_fusion_workflow_checkpoint.py
```

Optional exact producer `--jar`, original XML `--cache` and preserved
`--author-intake` strengthen current verification. Read
[admission instructions](../../tools/literature/fusion_catalog/ADMISSION.md)
before planning a change. Admission never establishes a historical author runtime,
complete upstream replacement, whole-paper reproduction or biological validity.

**OPEN WEBSITE CHECK:** the fresh `checkWebsite` run fails at
`docs/vignettes/broad-gsea-tools.md`. The same 33 Markdown vignettes lack required
scientific-citation panel markers; their bytes and the checker rule are unchanged
from the predecessor. Their exact list is in the validation receipts. Add proper
mapped scientific citations to those vignettes and rerun the full website check;
do not suppress the rule or insert empty placeholder panels. The scoped
catalog/UI checks pass; full website success is not claimed. Unfinished
`literature-review.html` and `literature-warnings.html` remain excluded from Pages.

## Warnings, private transfer and next priorities

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Provide the exact named permitted runtime,
package, input or binary, installing privately where allowed; otherwise retain
the skip and its explicit remedy. **FAIL2: NO COMPLETE RESULT.** Preserve raw,
partial, failed, null and superseded evidence; fix the stated cause and retry
NEW output. Read the workflow guide's NA/NaN/Inf/zero-p, source edition, explicit
compatibility, replay, original-plot and process/resource conditions.

Transfer `D:/projects/JLinAlg/build/gp-1/build/fusion-1/` in full, the private
`P108-author-intake-1/` archive/record, producer JARs and installed runtimes,
`D:/projects/JLinAlg/build/fu-1/build/fusion-catalog-1/`, original XML cache,
publication receipts and the worktrees' unique edits through a private channel
before retiring this machine. Owner is `D:/projects/JLinAlg/build/fu-1`, branch
`codex/fusion-qualification-20261122`. Keep scientific evidence at its retained
paths. Historical sensitive branch exports remain withheld pending a
content-specific review and separate approval; current work does not export them.
Cell Ranger remains excluded; prior MEME/FIMO and GATK3.8 use permissions persist.
Read the [process incident handover](HANDOVER_2026-11-22_PROCESS_SAFETY.md) and
never execute pre-fix worker producers or stop/restart unrelated processes.

Next: prioritize remaining implementable tools using current paper coverage and
actual missing capabilities, including nf-core, SCENIC/SCENIC+, spatial/genetics
gaps and newest iDEP where applicable. Commercial GUI/tool labels require their
actual source/method/license boundary; generic statistics do not establish whole
application equivalence. Complete all 1,043 pending positive-use reviews and
missed-provider screening across all 250 papers. Retain the P108 method-baseline
distinction when implementing isoTWAS-specific prediction. No full-goal completion
or reliable full-goal ETA is claimed.

## Publication and cleanup readback

The catalog commit `fc20fd5818a7b763b14fc386481cacea14c4b467` passed GitHub Pages
[run45083603357](https://github.com/robbyjo/OmicsMatic/actions/runs/45083603357),
both build and deploy. Live index/styles/app script match source after line-ending
normalization; both unfinished literature report URLs remain404. The
[exact deployment receipt](fusion-catalog-checkpoint/pages-public-verification.json)
records this boundary. The receipt-only follow-up does not change site bytes.

Automatic review rejected entire test-profile deletion as potentially containing
session data; that command did not launch. The approved safer alternative removed
only eight reproducible HTTP/JavaScript/GPU cache directories, 13,706,340 logical
bytes (13.7MB); measured free-space increase was13,697,024 bytes. All16 retained
profile/session records matched their pre-cleanup hashes. Full profile roots and
all scientific evidence/runtimes/JARs/worktrees remain. See the
[cleanup receipt](fusion-catalog-checkpoint/cleanup.json); do not run the
superseded entire-profile deletion helper. Publication/cleanup logs are retained
under `D:/projects/JLinAlg/build/git-publish/20261122-fusion-catalog-127/`.
