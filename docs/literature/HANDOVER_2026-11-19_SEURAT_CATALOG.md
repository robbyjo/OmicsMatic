# Toolbox handover: Seurat source review and pending catalog admission

**The 250-paper cohort is collected; implementation and cumulative individual
review remain unfinished. The Seurat RNA workflow is implemented and qualified.
Finish its catalog admission next. This checkpoint preserves unfinished
review/transition work without applying it or awarding integration credit.**

## Resume here

Authoritative worktree: `D:/projects/JLinAlg/build/high-impact-stats-1`.
Branch: `codex/seurat-workflows-20261119`.
Published predecessor: `9574a7b1c9e20a99b704c91d9512c7137f7935e3`.
Repository: [robbyjo/OmicsMatic](https://github.com/robbyjo/OmicsMatic).
The commit containing this handover follows that predecessor on main.

Keep [HANDOVER_CURRENT.md](HANDOVER_CURRENT.md) as the entry point. The older
[19 November handover](HANDOVER_2026-11-19.md) describes an earlier prototype;
its broader-workflow implementation tasks were completed by the
[RNA checkpoint](SEURAT_WORKFLOW_CHECKPOINT_2026-11-19.md). Its machine paths
and retention rules still help. Do not rerun old output directories, modify
frozen libraries or broadly stage/reset/clean the original root worktree.

| Measure | Canonical now | Proposed after qualified admission |
|---|---:|---:|
| Papers | 250 | 250 |
| Named families / effective definitions | 446 / 485 | 446 / 485 |
| Paper/tool observations | 2,695 | 2,696 |
| New scoped integrations | 130 | 131 |
| Native Java / upstream integrations | 24 / 106 | 24 / 107 |
| Including three existing adapters | 133 | 134 |
| Individually reviewed positive uses | 1,290 | 1,339 |
| Awaiting individual review | 1,405 | 1,357 |
| Missing / method-overlap definitions | 259 / 45 | 259 / 44 |

The frozen window remains 2023-10-23 through 2026-10-23: 71 Nature, 104 Nature
Genetics and 75 AJHG papers. Do not replace identities or extend the filled
cohort. Scoped integrations do not imply whole-tool parity, historical author
execution, complete author recipes or whole-paper reproduction.

## Completed work to preserve

- METAL's [catalog admission](METAL_CATALOG_CHECKPOINT_2026-11-18.md), its 24
  roles, LDAK and native weighted-Z work are already completed.
- Original Seurat 5.6.0 RNA construction, LogNormalize/VST or SCT v2, typed
  regression/scaling, PCA, NN/SNN, clustering, optional UMAP/t-SNE and
  run/verify/replay; native `SeuratSnn` is included. See the
  [guide](../vignettes/seurat-rna-workflow.md),
  [example](../../examples/seurat-rna-workflow/README.md),
  [proof](seurat-workflow-development/VALIDATION.json) and
  [package pin](seurat-workflow-development/PACKAGE.json).
  Six original profiles, seven captures including replay, six tests per
  Windows/Linux JVM and 26 controls are retained. Original R ran on Windows.
- Separately qualified [native preprocessing](seurat-preprocessing-development-v2/VALIDATION.json),
  [original marker workflows](seurat-differential-development/VALIDATION.json)
  and [six-edition native marker summaries](seurat-marker-summary-development/VALIDATION.json).
  Check their actual scopes before adding overlapping code. No native
  P-value tests or complete historical edition parity are claimed.
- Manual Seurat decisions cover all 250 XML identities, 197 named paragraphs
  in 51 papers, **50 positive roles: 48 direct package roles and two adapted
  methods**, plus excluded P083 background. The [snapshot](seurat-catalog-handover/README.md)
  contains hashes and paraphrases, not full source text. The primary audit
  passed against exact local XML and seven author-PDF pages. No canonical
  review records have been appended by this checkpoint.
- `seurat_catalog/model.py` produces a deterministic in-memory transition of
  eight metadata files. Sixteen checkpoint tests passed, covering preservation,
  arithmetic and refusal cases. There is **no plan/apply/verify driver,
  complete cumulative evidence packet or catalog-admission qualification** yet.

## Exact next work and acceptance criteria

1. Read `tools/literature/seurat_catalog/{reviews.py,primary.py,model.py}` and
   the snapshot. Reuse the completed manual decisions. The source auditor binds
   identities, anchors, contextual edition literals and selected PDF pages.
   The draft model is not a catalog writer. Its tests use frozen Git bytes.
2. Implement `evidence.py` and `catalog.py` with no-overwrite plan/apply/verify,
   rollback limited to owned metadata, and retained failures. Freeze the eight
   `model.META` documents at Git `9574a7b1...`; confirm they equal the completed
   METAL proof's `after` pins. Retain actual before ZIP bytes, current manifest,
   effective registry, verifier sources and qualifications. Never refresh old
   scientific/source hashes merely to make changed code pass.
3. Validate the unchanged predecessor chain against retained stage bytes.
   Inspect `metal_catalog/{catalog,evidence,model}.py`; its own BASE is
   `b25f8e15bb9af3c3da7dc5344dc7a4b1427c2a9c`. Use its old before ZIP,
   qualification packet and predecessor routines; recompute the 24-role census
   and run its unchanged model against the eight Seurat-before documents.
   Do not run an old final-state verifier against newly admitted metadata or
   weaken old guards to accept new totals.
4. Bind unchanged RNA/native/marker source and artifact qualifications.
   RNA auditor: `tools/literature/seurat_workflow/verify.py`; native auditor:
   `tools/literature/seurat_preprocessing/verify.py`. Inspect older marker
   auditors/proof schemas before invoking them. Preserved old test results
   are not fresh R refits. Do not claim Linux R or whole-project tests.
5. Add only the required review compatibility delta, from 1,290 to 1,339
   records. Preserve the old 1,290 as an exact prefix, including P061. Reuse
   `wire_signac_catalog.delta` and `catalog_source_integrity.reverse_delta`.
   The latter already discovers `*-shared-source-compatibility.json`; do not
   edit its allowlist or legacy semantics. Scientific code need not change.
6. Preserve P160 as a PDF-backed new use. XML paragraph 59 links the document
   and names a table; that label alone does not establish analysis. Legacy
   `collect_omics_review.tool_matches` excludes this Supplementary-information
   section and cannot regenerate the PDF-backed use. **Keep that guard intact.**
   The new source-aware driver must require the exact PDF/page hashes/decisions
   and skip/fail when unavailable or inconsistent. Keep PDF evidence separate
   from XML-only supporting-evidence validation.
7. Write `SEURAT_CATALOG_INTEGRATION.md`, the 50-role crosswalk, qualification
   packet, admission proof and compatibility document. Preserve contextual
   historical editions, benchmarks, adapted methods, unsupported operations
   and no-author-reproduction flags. Only one scoped upstream family credit
   is proposed; native family credit added is zero.
8. Plan, inspect, apply and verify the exact transition. Preserve unrelated
   observations/inventory, identities and order. Confirm only P160 is added,
   49 reviews appended, P061 unchanged and proposed totals match the table.
   Require unchanged predecessor/scientific guards and a clean manifest via
   `common_statistics.prism_catalog`; preserve historical checksum entries.
   Add full-driver tests for missing/tampered XML/PDF, roles/editions/credit,
   review-prefix changes, invalid artifacts, failed and repeat admission.
9. Check the new notice at the top of both local pages on desktop/mobile.
   Existing pages have long old notices; the draft inserts a new notice after
   the body tag, but rendering is unqualified. Keep the two pages withheld by
   `.github/workflows/pages.yml`. Create no release. Push qualified admission
   to main without force, verify public bytes and preserve other worktrees.
10. Continue higher-impact analytical gaps from `IMPLEMENTATION_PRIORITY.md`
    and current inventory, checking existing components first. Integration,
    WNN/transfer, Harmony, MAST, AddModuleScore, spatial/protein assay IO and
    plots remain explicit Seurat gaps. Continue wider individual source review;
    neither this checkpoint nor eventual Seurat admission completes the goal.

## Source decisions that must not be lost

- P047/P061 adapt methodology; direct package execution is not established
  by those roles. Keep P061's existing use and review exact.
- P093 is a benchmark outside popV. P200 uses DotPlot on existing atlas
  annotations, not new annotation/DE; qualified overlap is empty. P083 stays
  excluded background.
- P085/P087 include real-valued protein/IMC branches: never round into RNA
  counts. P051/P087/P117/P124/P192 report editions tied to different branches;
  preserve literals and contexts. P124 uses MAST, not interchangeable Wilcox/LR.
- Retain P096's reported v.2.0/API inconsistency, P063's WNN ambiguity,
  P165's malformed parameter prose and P182's 25-bin/100-control account.
  P166 reports null DE. Do not silently repair any of these.
- P134 clusters external protein activities; P099 uses an external embedding.
  Neither is the qualified raw-RNA/PCA path. P088's code-availability text
  establishes data wrangling, not new annotation.
- P160's supplementary methods establish spatial Seurat 4.1.1 and single-cell
  4.3.0.1 roles plus integration/transfer, averaging and module scores. PDF pages
  27-30, 34, 36 and 55 were extracted with pinned PDFium and visually reviewed
  with Poppler. Printed page = PDF page minus one. Retain printed `min.pct.`,
  separate custom 150-cell and marker 300-cell limits, and distinct
  Harmony/Banksy/ligand-receptor engines. Component overlap does not qualify
  the complete historical author recipe.

## Runnable resume checks

This checks the public frozen census and proposed transition without R, PDF
extraction, catalog mutation or family credit:

```powershell
Set-Location D:/projects/JLinAlg/build/high-impact-stats-1
python -B tools/literature/seurat_catalog/test_catalog.py
git status --short --branch
```

The test deliberately expects the eight live metadata files at this handover
predecessor. After qualified admission, retain this historical checkpoint and
replace that live-state test in the new admission suite with explicit before/
after checks. Do not weaken old proofs.

For actual source re-audit, use Python with **pypdfium2 5.13.0**, exact private
files, and a NEW output folder. The public tests alone do not reread sources:

```powershell
python -B tools/literature/seurat_catalog/primary.py `
  --cache D:/projects/JLinAlg/build/literature-review/xml `
  --supplement-pdf D:/projects/JLinAlg/build/high-impact-stats-1/build/seurat-catalog-1/supplement-P160-2/SUPPLEMENT.pdf `
  --out build/seurat-catalog-1/resume-primary-audit-NEW
```

This audits sources only; it does not fit Seurat or admit metadata. Missing
sources/dependencies return SKIP3; integrity mismatches return FAIL2. See
[CHECKPOINT-CHECKS.json](seurat-catalog-handover/CHECKPOINT-CHECKS.json) for the
exact checks completed in this handover.

## Local evidence and machine transfer

**GitHub is not a full machine backup.** Preserve or transfer these local
stores separately under applicable access/license rules:

- XML: `D:/projects/JLinAlg/build/literature-review/xml/`.
- New review work: `build/seurat-catalog-1/` inside the authoritative worktree.
  Initial census: `primary-census-1/COMPLETED.json`; handover rerun:
  `handover-1/primary-audit-1/COMPLETED.json`.
- P160 retrieval: `https://www.ebi.ac.uk/europepmc/webservices/rest/PMC12597827/supplementaryFiles`.
  Keep `supplement-P160-2/supplementaryFiles.zip` and `SUPPLEMENT.pdf`.
  PDF: 52,222,963 bytes; SHA-256
  `d3137b3a674deedd915f68664e777ac610be0a72c11a85a8163e66ca95fcb18b`.
  ZIP member: `41588_2025_2352_MOESM1_ESM.pdf`.
  `pdfium-extraction-2/` has exact UTF-8 text; seven renders are in
  `visual-method-review-1/`. Retain raw/failed attempts. Version 1 had newline
  conversion; version 2 corrects extraction bytes only. Use the public binding's
  DOI publisher URL, not the earlier unverified private URL fragment.
- RNA source/captures/controls/JAR: `build/seurat-workflow-1/`;
  current JAR: `qualification-1/omicsmatic-0.4.0.jar`. Public pin:
  `seurat-workflow-development/PACKAGE.json`. Qualification ZIP: 40,277,394 bytes,
  SHA-256 `9171e8a1cb32f3724dae884ff23b8f026bbbd7f6f16d0c0fa6acfe211970dbc1`.
- Native preprocessing/original intake: `build/seurat-high-impact-1/`.
  Frozen R-library ledger: `probe-2/R-LIBRARIES-BEFORE.json` (24,841 files).
  The preceding workflow turn rehashed that library; this handover does not
  claim a fresh complete physical library rehash or R refit.
- Rscript: `C:/Program Files/R/R-4.6.1/bin/Rscript.exe`. Private frozen libraries:
  `D:/projects/JLinAlg/build/literature-review/signac-runtime/reference-setup-1/R-lib`
  and `D:/projects/JLinAlg/build/omics-sctransform-r`.
- Python with PDFium:
  `C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`.
  Poppler: `native/poppler/Library/bin/pdftoppm.exe` under that dependencies
  directory. JDK: `C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/`;
  compile `--release 17`. WSL: `JLinAlg-Omics-SAIGE-20261026`.
- Older METAL/LDAK stores: [earlier catalog handover](HANDOVER_2026-11-18_CATALOG.md).
- Publication/preservation receipts:
  `D:/projects/JLinAlg/build/git-publish/20261119-handover-seurat-catalog-27/`.
  Private orchestration helpers in `D:/projects/JLinAlg/build/` retain historical
  paths/branch/output assumptions; read before adapting.

All 21 worktrees matched the prior publication baseline before these edits,
allowing four new source-review files in the active worktree. The other 20
worktrees and historical dirty overlays remain preserved. Forty-one protected
artifacts include the previous 38 plus the source ZIP/PDF and initial census.
Do not replace newer integrated files with stale root overlays. This checkpoint
changes no production scientific code or canonical catalog metadata.

## Prominent warnings and carried permissions

**SKIP3: the affected analysis is skipped.** Download/install is already allowed
where the environment permits. If an exact permitted runtime/library/backend/
reference/source/platform is unavailable or forbidden, name the cause and
affected work, tell the user what to supply in a permitted private location,
or leave it skipped. Never silently substitute a requested algorithm/backend.

**FAIL2: no complete result is admitted.** Retain inputs, failures, partials,
diagnostics and superseded evidence. Correct the named numerical, input/model,
integrity, process or capacity issue; retry a new output path. Require
`results_admitted: true` and successful verification. Never silently alter
IDs, NaN/Inf, P values, failed fits or contributor populations.

Preserve t-SNE sensitivity: a retained independent fixture differs by only
`2.22e-16` in covariates yet produces substantially different t-SNE coordinates.
The exact captured-input original rerun agrees; preserve both observations.
Do not promise cross-input/host equality. Native checks do not independently
refit every R model; hashes do not authenticate the host.

Cell Ranger stays excluded. Accepted FIMO research/nonprofit and GATK 3.8
academic noncommercial terms persist. Legacy iDEP.96 licensing remains
unresolved; investigate current iDEP. Keep restricted software and full article
text private; do not repeat settled permission prompts.

Both literature pages remain withheld from Pages. The current long notices and
full warnings-page mobile layout remain unqualified. No release/deployment is
part of this checkpoint. Public 0.4.0 predates these source-build workflows.
The overall implementation and individual-review goal remains unfinished.
