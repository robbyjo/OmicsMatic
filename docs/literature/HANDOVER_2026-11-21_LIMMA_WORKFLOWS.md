# Toolbox handover: limma workflow development

**The 250-paper collection is complete; the implementation and individual
source-review goal remains unfinished. This is a development checkpoint.**
The last qualified native limma checkpoint is main `30599215`. This handover
saves the subsequent standalone original-R workflow adapter, synthetic evidence
and remaining work. It adds no integration or paper-review credit.

| Measure | Current value |
| --- | ---: |
| Papers | 250: 71 Nature, 104 Nature Genetics, 75 AJHG |
| Frozen publication interval | 2023-10-23 through 2026-10-23 |
| Scoped integration families | 141: 26 native Java +115 upstream |
| Including three pre-existing adapters | 144 |
| Tool-use observations | 2,715 |
| Individually reviewed / pending | 1,575 /1,140 |
| Catalog manifest entries | 829 |
| Preserved worktrees | 33 |

See the [native limma checkpoint](LIMMA_VARIANCE_CHECKPOINT_2026-11-20.md)
for the separately qualified native variance components and the
[source-role proposals](limma-source-review-proposal/README.md) for 20 candidates.
The full goal requires the remaining implementations and semantic source reviews.

## Saved implementation and evidence

The new standalone `org.jlinalg.cli.LimmaWorkflowCli` accepts closed JSON
RUN/VERIFY/REPLAY requests. It is not yet registered as a global command.
The sole profile is **limma 3.68.5, edgeR 4.10.7, statmod 1.5.2, R 4.6.1**.
The profile currently requires all three packages even for non-count operations.

Six prepared-data operations are implemented for development:

- `LM`: dense continuous/log/methylation matrices, numeric design, declared
  contrasts, observational weights and optional fixed/estimated subject blocking.
- `VOOM`: declared raw/estimated counts, original edgeR normalization, original
  limma voom and lmFit; estimated blocking requires explicit one/two-pass choice.
- `EDGER_VOOM_LMFIT`: original **edgeR::voomLmFit**, with optional blocking and
  sample weights. Retain its actual provider rather than relabeling it limma.
- `BATCH`: original removeBatchEffect, keep-design, batches and covariates.
- `NORMALIZE`: original normalizeBetweenArrays on dense matrices.
- `CAMERAPR`: original cameraPR on supplied statistics and exact set memberships.

Fitted workflows support source eBayes/TREAT and declared adjustment families.
Existing generic differential APIs and the qualified native variance API remain
unchanged. Literal Unicode/NA-like identifiers, supplied normalization factors,
source warnings, valid infinite prior df and source zero-residual-df NA sigma
are retained. There is no automatic identifier repair or generic fallback.

There are **45 successful synthetic original-runtime compositions**, each with a
separate Java offline VERIFY. Independent reference code invokes stock original
namespaces without sourcing the adapter worker, then compares full original
returns. The [saved receipts](limma-workflow-handover/README.md) give the exact
comparison totals and current normal-build results. Synthetic controls do not
establish whole-tool, historical-edition or biological reproduction.

The test fixture ZIP retains original models, immutable snapshots, binary
internal-model journal, convergence records, inputs, source and diagnostics.
The R-free verifier checks integrity, input identities, exported structure,
variance, moderated-t/TREAT probabilities, adjustments and selected arithmetic.
It does not independently refit models, decode RDS/journal model contents,
authenticate coordinated substitutions or independently derive all F/B outputs.

## Resume in this order

1. **Complete limma qualification before catalog admission.** Inspect
   `gamma.observer` in `limma_workflow.R`: its on.exit path can calculate metrics
   from incomplete locals when the original fit errors early, potentially
   masking the original diagnostic. Preserve the original error and partial
   state, make observer failures explicit, and add an actual original-runtime
   failure control. This is a code-review concern, not a reproduced failure.
   If worker/source bytes change, keep all old captures and rerun into new
   output; never overlay new source hashes onto earlier scientific evidence.
2. Add an actual tiny **positive** original probability control. The fixture
   labeled `SMALL_P` contains a source-underflowed zero and ordinary positives
   (minimum P about 0.00572), not the intended tiny positive value. The first
   normal build passed 67/69 tests; two test-harness assertions expected absent
   tiny positives. The corrected tests replace the smallest actual positive P
   and adjusted P by zero and require refusal. Tolerances and original fixture
   bytes were not relaxed or rewritten. Preserve that first failed attempt.
3. Qualify **actual normal-JAR** RUN and successful REPLAY, timeouts, output
   limits, owned-process termination and immutable parents. The 45 existing
   execution controls used development classes; the normal build's saved-capture
   tests alone do not qualify its live execution path. Execute packaged tests
   with product JAR first on Windows Java21/25 and Linux Java21, and verify all
   predecessor payloads. Linux currently has neither Rscript nor javac; this
   is an environment observation, not an installation prohibition. Private
   installation remains authorized where permitted; otherwise record the named
   prerequisite as SKIP3. Do not claim Linux original-R fits have run.
4. Review sample-weight/internal convergence failure paths, partial/null objects,
   joint F/B audit scope and remaining source boundaries. Add user documentation,
   runnable examples and global CLI help after execution qualification. Keep
   `DEVELOPMENT_SCOPE` explicit until those gates pass.
5. Finish the **20 source-role proposals** against primary paper paragraphs and
   actual operations/editions. Investigate missing observations P088/P183/P203;
   retain P059's reimplementation distinction, P228's unresolved provider/
   spelling and P237's explicit edgeR ownership. Current-edition controls do
   not supply older author editions. Start catalog changes from
   `tools/literature/gcta_catalog/model.py::registry()`; preserve all 1,575
   reviews and COJO's zero-credit alias, then perform guarded admission and
   fresh source/manifest/report verification.
6. Continue higher-impact **PEER, then TwoSampleMR**, subject to the source-role
   audit. Continue the 1,140 pending observations and remaining missing tools.
   Existing GCTA historical-edition/input gaps remain open as documented in
   the [previous handover](HANDOVER_2026-11-20_LIMMA.md).

## Warnings and required recovery

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Name the exact unavailable runtime,
package, source or input and affected analysis. Obtain the permitted prerequisite
and rerun into **new output**, or retain the skip if it cannot be supplied.
Downloads/private installation are authorized where the environment allows.
Cell Ranger is excluded at the user's request. Previously accepted FIMO
research/nonprofit and GATK3.8 academic noncommercial terms remain applicable.

**FAIL2: NO COMPLETE RESULT.** Invalid inputs/design, source/integrity mismatch,
nonfinite numerics, convergence/capacity/process failure remain failures even
after an upstream zero exit. Read `WARNINGS.md`, `analysis-status.json`,
`manifest.json`, `worker/report.json`, warnings and logs. Preserve original
inputs, models, partials, failed/null results and diagnostics. Fix the named
cause and retry **new output**. Do not silently repair IDs, counts, versions,
populations, thresholds or estimators, or clamp valid unconstrained components.
Unfinished implementation is unfinished work, not an executed SKIP3 analysis.

**GitHub is not a machine backup. Before leaving this machine, transfer the
ignored runtimes, exact producer JARs, primary full text and complete private
evidence separately with checksums. Preserve all 33 worktrees and Git common
metadata. Do not reset/clean or overlay the historical dirty root onto main.**
Both literature HTML reports remain withheld from Pages. No release is made.

## Exact resume and transfer paths

- Checkout: `D:/projects/JLinAlg/build/lm-3`, branch
  `codex/limma-workflows-20261121`.
- Private stage: `build/limma-workflows-1` under that checkout. Preserve every
  failed and successful `worker-probe-*`, `java-probe-*`, `java-variants-*`,
  `direct-originals-*`, `compile-probe-*`, `saved-fixture-*` and `normal-gradle-*`.
- The first normal attempt is `normal-gradle-1`; corrected focused tests and
  producer are in `normal-gradle-2`. Exact pins are in the saved receipts.
- Qualified native predecessor JAR:
  `D:/projects/JLinAlg/build/lm-2/build/limma-native-1/normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar`.
- Installed Windows R: `C:/Program Files/R/R-4.6.1/bin/Rscript.exe`.
  Existing library list:
  `D:/projects/JLinAlg/build/literature-review/monocle-runtime/idep244-adopted-provider-4/libraries-requested.txt`.
  Reuse exact permitted libraries read-only; avoid unnecessary shared upgrades.
- Current source-role paragraphs remain private in the earlier `build/lm-1`
  intake; source census/proposals are tracked in Git.
- Exact helper sources are saved in the handover packet. Their original
  `D:/projects/JLinAlg/build/*limma*20261121*.py` paths and fixed output names
  need deliberate new-attempt adaptation before rerunning.
- Publication/preservation receipts:
  `D:/projects/JLinAlg/build/git-publish/20261121-limma-handover-77`.
  Check `PUBLISHED.json` after publication for the verified main commit.

Before resuming, check remote main and the latest publication receipt, run
`python -B tools/literature/verify_limma_workflow_handover.py`, inspect the
canonical manifest and preserve parent captures before creating new outputs.
The packet verifier checks saved bytes and declared scope; it performs no
scientific fit and needs no R installation.
