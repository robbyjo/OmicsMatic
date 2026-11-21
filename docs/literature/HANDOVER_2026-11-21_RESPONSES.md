# Toolbox handover: response preparation draft, 21 November 2026

The 250-paper collection is complete. **The implementation and paper-use review
goal remains unfinished.** The canonical catalog has **144 scoped integration
families: 26 native and 118 upstream**, plus three existing adapters outside that
new-work count. There are **1,630 reviewed and 1,092 pending paper-tool uses**, out
of 2,722 observations and 446 distinct labels. The 259 missing and 29 method-overlap
labels are a backlog, not implemented tools. Alias labels add no family credit.

This handover saves the latest Java/R/Python draft and all Git-visible dirty
variants from **54 original worktrees**. The new publication owner is the 55th.
Unfinished Java and its test are saved **outside the compiled source tree**. The
main build, scientific implementations, canonical catalog and website sources
remain unchanged from `a4e025527bc8e5b3580002397056fcc33e1d6918` (P96). The public
packet adds **zero integration credit**. It is an integrity/continuation record,
not a scientific qualification or whole-paper reproduction.

## Warnings and required actions

**DO NOT USE THE RESPONSE DRAFT AS A QUALIFIED ANALYSIS.** Its six-case original-R
run failed a final serialized-body hash guard after computing 66 gene tests. It
has no admitted top-level result. A later diagnostic found strict original-source
body and formal equality before and after six calls, despite a one-byte serialized
difference. The exact cause and a suitable guarded correction still need review.
Preserve the failed run; do not refresh its saved hashes or relabel it a pass.

**THE NEW TEST FIXTURE DOES NOT EXIST YET.** The saved test expects
`src/test/resources/org/jlinalg/singlecell/monocle-responses/original-controls.json.gz`.
Copying that test into an active test tree before producing an admitted fixture
will fail. Latest production drafts pass strict Java 17 compilation only; the
draft test suite, normal JAR, command contracts and scientific comparisons have
not been qualified. The saved `reference.R` still contains the failing hash guard.

**GITHUB IS NOT A MACHINE BACKUP.** Before retiring this machine, separately copy
ignored runtimes, JARs, full text, private author source, ignored outputs and full
private evidence; verify checksums at the destination. Follow
[PRIVATE-TRANSFER.json](handover-2026-11-21-responses/PRIVATE-TRANSFER.json) and prior
transfer records. Preserve all raw, null, failed, invalidated and superseded work.
The archive preserves Git-visible file variants, staged patches and deletion
states; it does not restore live Git indexes, ignored directories or all branches.

**SKIP3 means an unmet declared requirement.** Supply the exact permitted runtime,
input or path named by the command, then retry in NEW output. Download/install
authorization persists where the operating environment permits it. If a required
installation is forbidden, skip that dependent analysis and report the affected
analysis, reason and precise item the user must supply. Do not report a result.
**FAIL2 means validation or execution failed.** Retain errors and partial evidence,
correct the named cause and rerun into NEW output. Require exit 0, a valid completion
record and explicit admission before consuming scientific outputs. Do not weaken
guards, change identifiers, infer roots or silently substitute tool editions.

Cell Ranger remains excluded by user request. Research/educational/nonprofit use
and the separate GATK 3.8 academic noncommercial terms were already accepted; do
not ask again for those decisions. The user suggested current iDEP instead of
legacy iDEP.96: finish the current-version source/license assessment; current terms
do not automatically license old code. Both unfinished literature reports remain
off Pages. The inherited `broad-gsea-tools.md` citation gate and six descriptive
inventory/registry differences remain recorded. No new full-site pass, release,
Pages deployment or complete-goal claim is made.

## What is ready to use

The preceding [Monocle graph checkpoint](MONOCLE100_GRAPH_CHECKPOINT_2026-11-21.md)
is the latest qualified stage. Its normal JAR supports prepared-CDS original
Monocle3 package **1.0.0** LEARN/ORDER/RUN and VERIFY/REPLAY. Recorded qualification
is **824 focused tests per Windows Java 21/25 and Linux Java 21 packaged JVM**;
23 actual expectations comprise 18 completed graphs and five retained failures
or refusals. Complete original graph/ordering/RNG comparisons, real replays,
three-host verification, process failures and namespace-change refusals are saved.
These are preceding checkpoint results, not new model executions in this handover.

Qualified JAR on this machine:

```text
D:/projects/JLinAlg/build/mo-5/build/monocle100-graph-qualification-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar
bytes: 197238277
sha256: 006cbaad86ac8a6d4dc4e5aa6658b634f2a08b72c753c4815295d71fe87ad681
```

That stage begins with prepared coordinates/factors. It does not perform raw-count
preprocessing, choose a biological root or reproduce P021's custom gene test.
Existing `monocle-tools` routes ORDER, RUN 1.4.27, MODELS 1.3.1, GRAPH 1.3.3,
ALIGN 1.3.7, DIFFERENTIAL 2.26.0 and MORAN-LM remain available. The new global
GRAPH-1.0.0 / NEIGHBORS-1.0.0 / WEIGHTS-1.0.0 / RESPONSES-1.0.0 routes exist
only in the saved draft; do not advertise them as shipped commands.

## Exact state of the saved draft

Original owner: `D:/projects/JLinAlg/build/mo-6`, branch
`codex/monocle-role-coverage-20261121`, based on P96 above. Its original files and
dirty state are untouched. The new publication owner is `build/handover-98`,
branch `codex/handover-20261121-responses`. Publication receipts are under
`build/git-publish/20261121-handover-responses-98`.

The [development directory](../../tools/literature/monocle_responses/development/README.md)
contains a byte-exact restoration map and these drafts:

- `MonocleResponsePreparation.java`: native expression preparation. The log branch
  uses `log10(expression / supplied_size_factor + 0.1)`; the as-is branch preserves
  finite supplied values. Optional `log_n_genes` is the natural log of explicitly
  supplied detected-gene metadata, never recomputed from a selected gene matrix.
  Bounds and nonfinite/ragged input refusals are explicit. This is not GLM fitting.
- `MonocleResponsePreparationCli.java`: proposed RUN/VERIFY/REPLAY with captured
  inputs, explicit provenance, literal axes, optional supplied numeric base design,
  full-output recomputation, resource limits and completion-last semantics.
  The latest edit refuses rounded nFeature_RNA decimal values using BigDecimal.
  Its command/forgery/failure tests remain pending.
- `MonocleToolsCli.java`: proposed explicit delegation to the three qualified
  standalone 1.0.0 stages and the new response draft. It changes an existing file;
  compare the current target against the baseline before restoring it.
- `MonocleResponsePreparationTest.java`: seven draft API tests, including a missing
  six-case fixture dependency. It has not been run and is not in the main test tree.
- `reference.R`, `reference.py`, `diagnose_guard.R`: original-control generation
  and diagnosis. They contain machine-local/sibling-worktree path assumptions.
  Python/R syntax checks do not establish runtime or scientific correctness.

Private working evidence is `build/mo-6/build/monocle-roles-1`:

1. `compile-1`: older draft production sources compiled with --release 17,
   -Xlint:all and -Werror. Later edits postdate this capture. The handover's separate
   strict compilation checks the latest three production drafts, not the test.
2. `response-reference-1` and `response-reference-2`: exit 1, AST traversal failed
   on an R missing argument from blank indexing. Both attempts and source snapshots
   remain retained. The current walker skips only that syntactic missing argument.
3. `response-reference-3`: exit 1. All six per-case objects and 66 original spdep
   gene tests were written; the final serialized `lm.morantest` body hash changed.
   There is no top-level RESULT.json. These partial files are unadmitted.
4. `guard-diagnosis-1`: exit 0, observational diagnosis only, no namespace mutation.
   Both parsed-official body and formal checks are true before and after six real
   spdep calls, as is strict saved-body equality. Both serializations are 8,292
   bytes; byte 555 differs. Before hash:
   `07590a758b48a6819c9d0f372c58fccf17848e8af877abc3b7f468c6a1f76c25`.
   After hash:
   `7955ee8f6729a56b579605d2b7f7f8217b47aad447d20f0d6b470fa2f353b57c`.
   This does not by itself repair or admit attempt 3. Full bytes and commands are
   in the packet. No scientific Java/R comparison was completed for this draft.

No author fork was executed. The arithmetic branch is also in official MIT
Monocle3 1.0.0 `graph_test.R`; controls extract that exact AST rather than invoke
full graph_test. Original spdep 1.4-1 `lm.morantest.R` comes from the already-retained
official archive. P021's privately inspected author file has an unresolved executed
commit/license and is not redistributed in this handover.

## Resume in this order

1. Verify/restore this packet into NEW output using the command below. Read the
   restoration map; create a fresh worktree from current main. Compare the existing
   MonocleToolsCli target with its recorded P96 baseline before copying reviewed
   drafts. Keep the original mo-6 owner and every preceding worktree unchanged.
2. Investigate byte 555 and the serialized metadata. If strict parsed-official
   body/formal equality is the justified guard, retain both serialized hashes as
   diagnostics and prove changed-body and changed-formal refusals. Do not accept
   deparsed text alone or blind hash replacement. Never patch the original namespace
   or runtime. Then run fresh `response-reference-4` in NEW output, retaining all
   three failures and the diagnostic. Require a valid top-level result before
   deriving any fixture.
3. Freeze the admitted six-case original fixture with full provenance and stable
   compression. Run all seven API tests and add genuine CLI tests for every branch,
   exact supplied metadata, literal axes, ignored/missing inputs, operation limits,
   altered/resealed bundles, incomplete output, console failure and parent-preserving
   replay. Test exact global delegation and preservation of all existing routes.
4. Build the normal source JAR. Compare prior payloads, keep existing graph/neighbor/
   Moran science unchanged, and run appropriate prior regressions plus new tests on
   Windows Java 21/25 and Linux Java 21. Exercise actual global graph/neighbor/weight
   commands and native preparation feeding MORAN-LM against all 66 original tests,
   all alternatives, finite metadata and complete replay/verification. Preparation
   alone is not statistical or whole-paper admission.
5. Refresh the nine already-reviewed Monocle roles against actual editions/settings.
   Use a guarded current catalog transition with exact old metadata/provenance and
   actual predecessor verification. Do not add nine duplicate reviews or helper
   families. The current catalog verifier is
   `tools/literature/verify_twosamplemr_catalog_checkpoint.py` and current registry
   provider is `tools/literature/twosamplemr_catalog/model.py` (485 definitions).
   `monocle_catalog_context.py` is historical 917-review context, not the current
   1,630-review ledger. Do not mutate frozen providers or hash-refresh old packets.
6. Continue the highest-impact omics/GWAS gaps and individual-use reviews. Previously
   identified candidate frequencies are QTLtools 9, SKAT-O 8, ACAT 7, FUSION 7,
   MR-PRESSO 6, SKAT 6, fastQTL 5, GenomicSEM 5 and PRSice 5. Recheck current coverage
   and source roles before selecting one. Reuse existing implementations (t-SNE
   already exists). Prefer feasible native Java methods; use tested version-bound
   upstream adapters for substantial external software. The 1,092 pending reviews
   and 259 missing labels remain part of the original objective.

P021 explicitly says **UMAT**, not UMAP. Preserve its supplied size factors, assay,
population, root and factor coding uncertainties. Its modified Moran covariates
are batch, sex, numeric PMI and natural-log supplied nFeature_RNA; ordinary residual
lm plus spdep is distinct from quasipoisson GLM fitting. The selected author commit
does not establish the exact executed paper recipe. Do not call a generic stock
Moran command a reproduction. Existing native Moran self-arc refusals stay intact.

Other nine-role boundaries: P024 py-monocle is separate Python software, with 200
Braak-selected roots; the user's nonprofit/research category is already answered,
but no runtime was qualified here. P043 is Cicero/platform context. P051's exact
1.3.7/Batchelor 1.20 ALIGN adapter already exists. P085's original 1.3.1 MODELS
adapter already exists; old grr/R4.6 failure wording is stale. Preserve original
quasi-Poisson/t/grouped-Holm versus documentation BH differences. P099's patch,
formula and units remain partial; retained source failures are unadmitted. P117's
1.3.3 graph settings are 130/140/150, branch 15/20, no loops, unknown root. P134
uses protein activity. P233 conversion is condition-specific; do not silently pool.

## Verify and transfer

From a checkout with Python 3.11+ and Git:

```powershell
python -B tools/literature/monocle_responses/verify_handover.py --out build/NEW-response-handover-check --restore
```

The reader verifies saved bytes/scope, extracts into NEW output only and executes
no R/Java/model. It does not apply patches. SHA256 establishes integrity, not
producer identity or scientific correctness. A normal clone must have the P96
ancestor; a shallow clone missing it must fetch history first. In WSL on this
Windows-linked checkout, pass `--git '/mnt/c/Program Files/Git/cmd/git.exe'`.

Run the preceding frozen graph reader on the actual unchanged `build/mo-5` owner
(or exact P96 checkout), because its old HANDOVER_CURRENT hash remains authoritative:

```powershell
python -B tools/literature/monocle100_graph/qualification/verify.py --out build/NEW-P96-graph-check
python -B tools/literature/verify_twosamplemr_catalog_checkpoint.py --out build/NEW-current-catalog-check
```

The new handover reader checks current draft transport; preceding scientific checks
remain separately scoped. Do not refresh the P96 packet to fit a new handover page.

Exact original R library:
`D:/projects/JLinAlg/build/mo-1/build/monocle100-1/install-1/R-lib`.
Readiness/source receipt: `build/mo-1/build/monocle100-1/install-1/RESULT.json`.
R 4.3.3 base:
`build/literature-review/monocle-runtime/monocle133-reference-setup-1/.pixi/envs/default`;
Rscript is `bin/Rscript`, R_HOME `lib/R`, native libraries `lib`, base R library
`lib/R/library`. WSL distro `JLinAlg-Omics-SAIGE-20261026` runs original R controls;
`JLinAlg-Omics-PyMOL-20261026` supplies Linux Java/Python. Use `/mnt/d/...` paths.
Public `reference.py` retains sibling-owner and runtime assumptions: inspect a NEW
copy and repoint it when moving machines. It is not a portable installer.

The paper window is frozen at **2023-10-23 through 2026-10-23**, with 71 Nature,
104 Nature Genetics and 75 AJHG papers; no extension was required. This handover
does not roll the cohort forward to today's date. The
[catalog handover](HANDOVER_2026-11-21_CATALOG.md) and
[graph checkpoint](MONOCLE100_GRAPH_CHECKPOINT_2026-11-21.md) retain earlier source,
qualification and transfer details. Lower HANDOVER_CURRENT entries are historical.
