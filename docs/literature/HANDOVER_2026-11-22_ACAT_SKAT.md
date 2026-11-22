# Handover: native ACAT checkpoint and remaining high-impact work

**The 250-paper implementation goal remains unfinished.** The corpus is complete
at 250 papers: 71 Nature, 104 Nature Genetics and 75 AJHG, in the frozen
2023-10-23 through 2026-10-23 window. Catalog totals remain **146 scoped families
(26 native + 120 upstream), 1,639 reviewed uses and 1,083 pending**; 149 includes
three preceding adapters. There are 446 labels, 485 definitions, 2,722 observed
paper/tool uses, 258 missing labels, 28 method overlaps and two zero-credit
aliases. The new ACAT component receives **no additional catalog credit yet**.

## Saved and qualified now

The [native ACAT 0.91 guide](ACAT091_NATIVE.md) and
[beginner example](../../examples/acat091/README.md) document the new
`acat-tools` global command. It combines declared matrix columns using the
selected unmodified upstream `ACAT()` source behavior, and supplies native
RUN/VERIFY/REPLAY captures. It does not fit genotypes or execute R. Existing
ACAT, ACAT-V/ACAT-O, rare-meta and SKAT-O APIs retain their prior behavior.

The final normal build passes **500 focused tests**, and the same 500 pass
against the packaged JAR on Windows Java 21, Windows Java 25 and Linux Java 21.
These include 274 original ACAT matrix cases, 192 original R column sum/mean
pairs, 19 CLI controls, six native API checks and nine legacy tests. The actual
global route runs in these tests. Of 22,441 preceding JAR payloads, **22,440 are
byte-identical**; only the reviewed dispatcher changes, with ten new payloads.

Qualified JAR:
`D:/projects/JLinAlg/build/sa-1/build/skat-acat-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar`

- Size: 197,362,611 bytes.
- SHA-256: `9965571a5f24f6d701b4da693d20b8c44e58c4359da54dd932476dc1ee19fd7b`.
- Native source profile: ACAT 0.91, upstream commit
  `e737d7d188ba6731800c5e6971539ca6a3888405`.
- Selected `R/ACAT.R`: 14,354 bytes, SHA-256
  `cab2a44bb165ee2da87cb0c57d2170b1f7a5c5e09bc231367d7e74b47f9c28eb`.
- Original references ran in R 4.6.1. The new native command requires no R.
- Public [qualification](acat091-development/QUALIFICATION.json),
  [transfer inventory](acat091-development/PRIVATE-TRANSFER.json) and
  [variant preservation](acat091-development/ROOT-VARIANTS.json) retain scope,
  hashes, transcripts and explicit failed controls.

The final replay guard resolves existing directory aliases before checking
parent/output separation. The added test actually uses an owned Windows junction
or Linux symlink and verifies an unchanged parent. The preceding 499-test build
and its three-host passes are historical. A direct pre-fix alias probe created a
child inside its synthetic parent before failing the final integrity check;
that mutated capture, old source, JAR, logs and seal remain **unadmitted**. Do not
repair, overwrite, promote or recursively follow its alias while transferring it.
An initial strict Java serialization compile failure is also retained.

## Warnings and actions that must remain prominent

**SKIP3: the dependent analysis or source audit did not run.** Supply the exact
named permitted executable, package, index, source XML/PDF or environment.
Downloads and private installation are authorized where the operating environment
permits. If installation is prohibited, retain the skip and state the affected
analysis and exact prerequisite needed. Cell Ranger is excluded by the user.

**FAIL2: no complete result is admitted.** Preserve raw, null, partial, failed
and superseded evidence, logs and process outcomes. Resolve the named input,
numerical, capacity, process or integrity cause and retry NEW output. Do not
silently repair NaN/Inf, drop zero-weight rows, change endpoints, infer options,
replace a method, overwrite old evidence or refresh old source hashes.
Read `analysis-status.json` and `WARNINGS.md` before using any export.

For ACAT, one unadmitted column withholds the entire batch export. Raw source NaN,
weight-sum-overflow 0.5 and reciprocal-overflow zero are preserved and refused.
A literal source input zero retains a warning; numeric zero does not establish
infinite biological significance. VERIFY can confirm a failed capture's internal
consistency without admitting a scientific result. Completed null results remain
labelled and do not establish association or biological replication.

## Resume here, in order

1. Read this handover and the ACAT guide. Check the public checkpoint with
   `python -B tools/literature/acat091/verify_checkpoint.py`.
   Add `--jar PATH` to check the exact qualified local JAR bytes. This checks
   saved evidence and source identity; it does not rerun R or the test suites.
   Use a NEW checkout for new work. The current owner is
   `D:/projects/JLinAlg/build/sa-1`, branch `codex/skat-acat-source-20261122`, based
   on `68c0f1957bfbc5bb3cef0165e76b9ca81ed67d2e` before this publication.

2. Finish the source-bound **ACAT/SKAT/SKAT-O paper review**, then admit only
   qualified scientific scope. The frozen fresh scan verified all 250 XML hashes
   and found 19 candidate-positive papers, 95 paragraphs and one table; it is a
   census, not a completed semantic review. Existing pending target observations
   are ACAT in seven papers, SKAT in six and SKAT-O in eight. Exact private
   context is under `build/skat-acat-1/source-discovery-1/private-context` in this
   owner. Read P127, P154, P204 and P207 fully; inspect P123/P147/P210 Cauchy-only
   candidates before deciding whether to add a named observation. Preserve
   primary pointers and unknown editions; do not give one family duplicate credit.

3. Implement a **standalone SKAT execution adapter** with tested upstream calls.
   SKAT 2.2.5 and dependencies are privately installed, and eight genuine original
   synthetic fits completed. These probes do not constitute an execution adapter.
   Use typed prepared phenotype/covariate/genotype inputs, explicit continuous or
   binary null models, `davies` versus `optimal.adj`, weights, missingness/MAF
   filters, imputation, adjustment settings and random seeds where relevant.
   Capture original null/results RDS, full warnings, variant exclusions, numerical
   fallback and RNG behavior. Implement owned process supervision, strict output
   admission and RUN/VERIFY/REPLAY, then qualify actual global commands from the
   normal packaged JAR on the intended hosts. Preserve genuine failed/null cases.

4. Reuse existing native ACAT-V/ACAT-O and SKAT-O methods where their scope fits;
   compare them against actual selected original software before claiming source
   equivalence. The existing rare-meta SKAT-O default is a 10,000-replicate
   parametric simulation with floor 1/10,001; optional analytic and conditional
   integration calibrations are different scopes. ACAT 0.91's selected namespace
   has no exported `ACAT_O`. Its dense matrix genotype routines fail on current
   R 4.6.1 in the retained probe, while sparse dgCMatrix probes work; do not patch
   the original silently or present those probes as new ACAT-V/O qualification.

5. Continue high-impact gaps: FUSION, GenomicRanges and g:Profiler each occur in
   seven papers, plus other six-paper scientific families. Paper frequency is a
   starting point, not permission to implement proprietary GUI software without
   suitable terms. Complete the remaining 1,083 individual use reviews, including
   finding missed tools and distinguishing comparators and reused results.

6. Rebuild source-bound catalog proofs and current manifest when admission
   actually changes the catalog. All eight canonical metadata/report files and
   the existing 844-member catalog manifest remain unchanged at this checkpoint.
   Historical catalog/workflow readers intentionally pin old scientific source:
   run them on their actual unchanged owners. Do not weaken their guards to accept
   the new dispatcher. Current catalog provider: `qtltools_catalog.model.registry`.
   Both unfinished literature reports remain excluded from Pages. No release,
   explicit Pages publication, whole-tool or whole-paper completion is claimed.

## Paper-specific boundaries already established

- **P131:** explicitly reports ACAT R 0.91 for combining T-SEM/FUSION tissue
  p-values. Preserve the source's tissue/gene/factor wording and QGENE filtering;
  its separate gsMAP Cauchy step has an unestablished implementation.
- **P246:** explicitly names SKAT R 2.2.5 and `SKAT()`, for CASR PheWAS with age,
  sex and ten PCs. SKAT-O method arguments are not reported in the inspected main
  text. Recover supplementary options before inventing an `optimal.adj` command.
- **P090:** SKAT/SKAT-O are comparators; the executed burden workflow uses
  REGENIE 3.2.4. **P152:** REGENIE Step 2 unified burden/SKAT/ACAT combinations.
  **P141:** SAIGE-GENE+ SKAT-O. These do not establish standalone R calls.
- **P106:** Meta-SAIGE score/model context and Cauchy combinations across MAF
  and annotations. Preserve provider context. **P132/P176:** SKAT-O execution
  appears, but standalone provider/edition is not established by inspected text.
- **P108:** isoTWAS uses an ACAT omnibus step followed by the reported two-step
  multiple-testing procedure; no ACAT package edition is established.
  **P221:** combine only when at least two FIVTWAS models exist; single-model
  results are reused. Unsigned p-values alone do not identify signed Z direction.
- **P190:** ACAT simulation comparison needs supplement recovery for its exact
  provider. Separately, the already reviewed QTLtools use is established by
  supplement Note S1.7, with unknown edition and explicit wording/code differences.
  **P220:** distinguish fresh analysis from reused AoU/Genebass summaries. Its
  cached table contains real aggregate human results and stays private.

## Machine handover and preservation

**GitHub is not a backup of ignored runtimes or private scientific evidence.**
Transfer the paths in [PRIVATE-TRANSFER.json](acat091-development/PRIVATE-TRANSFER.json)
before retiring this machine; no private transfer is claimed. Preserve the whole
`D:/projects/JLinAlg/build/sa-1/build/skat-acat-1` tree with link-aware handling,
the exact JAR above, the private R library, source archives, all original RDS,
XML/PDF caches and failed attempts. Do not follow or flatten deliberate test
junctions/symlinks blindly. Preserve existing link targets and labels separately.
Prior qualified runtime/JAR/source caches are listed recursively by the preceding
[QTLtools transfer inventory](qtltools-catalog-admission/PRIVATE-TRANSFER.json).

Private SKAT dependencies: SKAT 2.2.5, SPAtest 3.1.2, RSpectra 0.16-2,
Rcpp 1.1.2 and RcppEigen 0.3.4.0.2. Except SPAtest, the downloaded binaries were
built under R 4.6.2 and loaded under R 4.6.1; retain that warning. The eight
120-person/eight-variant probe fits are artificial and all preserve the original
one-variant exclusion warning. No system/shared R library was changed.

All **64 preceding worktrees** and their exact Git-visible file states are
preserved; the current owner is the 65th. Retain the dirty shared root
`D:/projects/JLinAlg` on `codex/analysis-pipeline`. Of its 309 Git-visible changed
files, 286 match the main snapshot already. The remaining 23 older variants are
saved in the labelled public handover archive with per-file hashes; they are
historical development evidence, not replacements for current main files.
No prior branch or worktree is deleted or reset.

Publication/preservation receipts are under
`D:/projects/JLinAlg/build/git-publish/20261122-skat-acat-108`.
After the push, start with `PUBLISHED.json` there for the exact published commit,
remote-main identity, GitHub readback and all-owner preservation results.
The handover operation finishes this publication; the larger implementation goal
remains active for the next session.
