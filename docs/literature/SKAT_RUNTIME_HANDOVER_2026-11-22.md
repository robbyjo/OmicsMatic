# Current SKAT runtime handover — 2026-11-22

**The full 250-paper implementation goal is unfinished.** Catalog totals remain
250 papers, **147 scoped families: 27 native + 120 upstream**; 150 includes three
existing adapters. There are 1,661 reviewed and 1,062 pending paper/tool uses,
446 labels, 485 provider definitions and 2,723 observations. The frozen window is
2023-10-23..2026-10-23, with 71 Nature, 104 Nature Genetics and 75 AJHG papers.
This development checkpoint adds **zero catalog credit** and is not a release.

## Resume location and public evidence

Current owner: `D:/projects/JLinAlg/build/sk-2`, branch
`codex/skat-runtime-20261122`, based on main commit
`00c262af2a2677baf7cdb35ca0e662a8a6100526`.

* Work and raw evidence: `build/skat-runtime-1` under that owner.
* Publication receipts: `D:/projects/JLinAlg/build/git-publish/20261122-skat-runtime-112`.
* [STATUS.json](skat-runtime-handover-20261122/STATUS.json) pins this checkpoint.
* [EVIDENCE.json](skat-runtime-handover-20261122/EVIDENCE.json) describes the
  content-addressed ZIP of selected source, artificial inputs, original R models,
  RNG states, logs and failed attempts. `manifest.json` inside that ZIP maps
  original relative paths to exact stored objects. This avoids duplicating the
  same upstream source thousands of times. It does not omit failed captures.
* [PRIVATE-TRANSFER.json](skat-runtime-handover-20261122/PRIVATE-TRANSFER.json)
  identifies all retained development JARs, private R libraries and excluded build
  material. Private transfer has not been performed.
* [INVALIDATED-EVIDENCE.json](skat-runtime-handover-20261122/INVALIDATED-EVIDENCE.json)
  identifies the old constant-outcome capture. Its original seal and erroneous
  admitted export stay unchanged for audit; **do not use that result**, even if
  its old producer confirms its historical integrity.

Run the checkpoint verifier with Python 3.11 or later from a clean checkout:

```powershell
python tools/literature/skat_runtime_development/verify_handover.py
python tools/literature/skat_runtime_development/verify_handover.py --restore build/skat-evidence-restored --prefix packaged-matrix-5
```

Restoration requires a NEW directory and preserves selected file bytes. This
verifier checks source/archive integrity and recorded scope, not scientific
correctness. To run Java VERIFY on a restored capture, use its **original exact
producer JAR**. Older captures intentionally have different worker/Java hashes.
Do not refresh their seals or use the newest JAR to relabel them as current.

The preceding prototype packet/verifier in `skat-workflow-handover-20261122`
belongs to the frozen base checkout in `D:/projects/JLinAlg/build/sk-1`. Run it
there or at that exact commit. Its stale hashes on newer source are expected.

## Implemented in the development CLI

`SkatWorkflowRuntime.java`, `SkatWorkflowAudit.java` and `SkatWorkflowCli.java`
now connect the earlier closed contracts, literal intake and owned-process code.
Global dispatch is `java -jar YOUR-JAR.jar skat-tools --help`.
[Runnable artificial templates](../../examples/skat225/README.md) document
RUN/VERIFY/REPLAY, exact prerequisites, paths and warning recovery.

Runtime behavior includes original-source and producer fingerprints, declared and
resolved Rscript/library paths, controlled environment and RNG seed, nonce ACK
before the first original model call, runtime-file hashes before ACK and after
execution, and 239 original namespace function bodies/formals before/after. The
original SKAT namespace is never replaced or patched. The original 302-member
reference descriptor remains unchanged.

The audit rederives intake, complete-case masks and generated parameters; checks
null/design/residual relationships, original field types, set order, tested
counts, variant maps, source MAC, supplied weights, p-value exports and explicit
admission policies. Native `RSourceColumnSum` exposes the existing source-bound
64-significand summation for bitwise comparisons with original R column sums.
This is not a new separately credited tool family.

RUN retains raw/effective requests, input/source pins, full original R null and
set objects, RNG states, conditions, diagnostics, logs, warnings and file seals.
Only **`admitted/p-values.tsv`**, accompanied by `results_admitted=true` and a
valid complete capture, is the final export. `results/p-values.tsv` is an
original candidate that can remain after refusal. Untested source p=1 exports
NA; retained source-reported zero bounds also export NA. Neither is an exact
tested p-value. A failed or unaccepted set withholds the batch.

VERIFY is offline integrity and table-relationship checking. It does not
deserialize RDS, refit the original model, authenticate coordinated replacements
or certify unreported upstream convergence. A verified retained FAIL2/SKIP3
capture remains unadmitted. REPLAY performs fresh original calls into NEW output,
protects physical parent aliases and checks the parent before and after.

Process limits monitor logical workspace footprint and wall time and close the
owned root plus start-identified observed descendants. They are not an OS job
container, a hard aggregate RAM quota or proof of catching unobserved escaped
children. Hard JVM/host failure can interrupt capture finalization; always require
the status, final export and successful complete verification together.

## Validation and retained failures

Exact commands, source/JAR hashes, test suites and results are in STATUS.json and
the evidence ZIP. The latest normal build and packaged matrix are named there;
read their actual result rather than assuming all planned gates passed.

* Normal Gradle builds the actual global executable JAR and runs the selected
  original ACAT regressions, actual R column-sum fixtures, source/metadata guards,
  real process timeout/child cleanup controls and global CLI warning/skip tests.
* The independent matrix oracle makes separate, unmodified original SKAT calls;
  it never sources the production worker. Cases cover both outcomes, all 15
  declared method names, six kernels, missingness/imputation, ordered overlapping
  sets and RNG chains, dosage, supplied signed/zero weights, signed features,
  ALT flipping, literal Unicode/whitespace IDs, strict cutoff boundaries,
  intercept/rank policies, rho vectors, resampling, MomentAdjust and zero bounds.
  Expected source errors/refusals are successful negative controls only when the
  CLI returns the required failure and admits no result.
* Earlier API controls include eight-case original fits, 27 structural audit
  controls and three dependency refusals. These have older producer hashes and
  do not qualify the newest packaged endpoint by themselves. A successful older
  source-classpath REPLAY is likewise historical, not current-JAR qualification.
* Initial runtime probes failed before ACK because Windows R.home("bin") omitted
  the parent launchers from the reported file table. Java now fingerprints those
  two additional files explicitly; original reported records remain retained.
* Initial output auditing correctly exposed differing original NA field types;
  generated parameter order was also unstable after canonical JSON rereading.
  Both implementation defects were fixed in subsequent NEW attempts.
* `packaged-matrix-2` is incomplete: its harness/oracle encountered Unicode input
  conversion under LC_ALL=C. The Java/R attempt also refused before original
  model calls. `read.delim(fileEncoding=...)` and `write.table` converted/escaped
  literal IDs. The new worker marks UTF-8 bytes on input and writes explicit
  binary UTF-8 tables. No locale change, ID repair or upstream namespace patch
  was used. Both failed and successful protocol/analysis attempts are retained.
* `packaged-matrix-4` completed 89 cases: **88 passed; qualification failed** on
  a constant continuous outcome with an intercept. Original R returned tiny
  positive residual variance, and the workflow admitted it. Its admitted export
  remains preserved as **invalidated development evidence**, not a usable
  result. The current intake explicitly refuses constant included outcomes for
  binary models or continuous models with an intercept, after complete cases and
  before any original fit. It does not apply this intercept rule to continuous
  no-intercept models. The subsequent build/matrix status is recorded separately.
  `normal-gradle-7` retained three test failures because new assertions looked for
  the detailed error in stderr instead of the saved WARNINGS.md. Exit2 and the
  input guard were already correct. `normal-gradle-8` checks the saved warning
  and passes all **722 selected tests**, with no skipped tests. Its exact producer
  is **197,764,157 bytes**, SHA-256
  `d87eb1597fcdd48214a4ee6fb72b7b2e4cb4c7b0ce547d1782bcd4303ae902cc`.
* **Current `packaged-matrix-5`: all 89 cases pass**, with no failed cases.
  Each runs the actual normal-gradle-8 global packaged endpoint and a separate
  original-R oracle, then verifies its saved capture. Expected failures admit no
  final result; the constant-outcome case now exits2 before an original fit.
  Compared finite p-values use max(8 ULP, 2e-13 relative) tolerance, with original
  type/count/bound relationships and captured RNG pairs checked separately.
  All 154 resolved runtime files stayed unchanged. This is completed RUN/VERIFY
  matrix coverage; the remaining REPLAY/adversarial/source-review/catalog gates
  below still prevent claiming full adapter qualification or new family credit.

Qualification so far is Windows x86_64 with Java 21 and R 4.6.1. Strict
`--release 17` compilation is not a Java 17 runtime claim. No Linux R fit or
cross-platform SKAT runtime is qualified in this checkpoint.

## Next tasks, in order

1. Read STATUS.json, the latest normal-build and matrix RESULT.json and the
   invalidated constant-outcome case. If any latest gate fails, retain it and
   correct the cause in NEW output before claiming qualification. Keep old JARs,
   runtime libraries, worker hashes, failed seals and prior owners unchanged.
2. Finish **current normal-JAR** end-to-end qualification: real REPLAY with an
   unchanged parent, physical link/junction parent-alias refusal, deliberate
   parent mutation during REPLAY using a disposable artificial clone, final
   export quarantine on refusal, missing/wrong private dependency exit3 before
   model calls, and resealed contradiction checks through the public verifier.
   Include source MAC/map mutations, supplied-weight mismatches, empty sets,
   status/warnings/request contradictions and relevant process/capacity failures.
   Earlier component tests are evidence, not permission to skip these gates.
   Also inspect the null-resampling type comparison when `n_resampling=0` with
   a nondefault requested type: the current audit compares the returned type
   unconditionally, while the worker checks substitution for positive resampling
   counts. This is an unqualified branch to test against original source before
   changing behavior, not an observed numerical failure in the recorded corpus.
3. If claiming Linux or another JVM/R runtime, privately install and qualify that
   exact profile separately. Do not imply it from Java portability or a compile.
   Current R packages include binaries built under 4.6.2 loaded by R 4.6.1;
   preserve and prominently show those recorded build warnings.
4. Complete **P246** source review. Document S1 PDF pages 24–25 were independently
   extracted and visually checked. They name SKAT 2.2.5/SKAT() for stated SKAT-O
   but do not establish method/rho, kernel/beta weights, imputation or small-null
   options in the inspected subsection. Data S1 XLSX Tables S15/S16 remain
   **uninspected**. Download the original workbook, retain bytes and inspect
   read-only before deciding whether settings are established. Do not infer
   defaults. A total-ALT-allele burden score elsewhere does not establish SKAT
   kernel weights. [Original supplementary PDF](https://ars.els-cdn.com/content/image/1-s2.0-S0002929725002447-mmc1.pdf),
   [Data S1 workbook](https://ars.els-cdn.com/content/image/1-s2.0-S0002929725002447-mmc2.xlsx),
   [paper DOI](https://doi.org/10.1016/j.ajhg.2025.06.013).
5. Only after the source/runtime/public-endpoint gates pass, admit **one**
   standalone upstream SKAT family; SKAT-O in that provider is an operation/alias.
   Re-audit each actual provider/paper role. REGENIE, SAIGE, REMETA, SEAK and COAST
   models remain distinct. Do not substitute this prepared autosomal workflow
   for SSD, sex chromosomes or whole-paper reproduction. Regenerate/validate the
   catalog and amend counts from observed output, not from this proposed credit.
6. Resume high-impact remaining families and the **1,062 pending uses**. Existing
   screening signals include GenomicRanges, g:Profiler and FUSION (seven papers
   each), Squidpy, smartpca and MR-PRESSO (six each). Frequency is a prioritization
   signal, not execution evidence. Prefer feasible high-impact methods, easy
   native Java implementations and exact tested adapters where appropriate.
   The 250 papers are collected; semantic completeness and missed-tool recovery
   remain open. Do not redefine the objective around the current milestone.

## Warnings, recovery and transfer

Persistent user decisions: implement feasible methods natively in Java and use
tested upstream adapters for substantial tools; prioritize scientific impact.
Download/private installation is authorized where the operating environment
allows it. Educational/research/nonprofit MEME/FIMO use was confirmed. Academic
noncommercial GATK 3.8 use under its disclosed additional terms was accepted.
Cell Ranger was explicitly excluded. For iDEP, the user preferred evaluating the
newest version rather than relying on unclear legacy iDEP.96 terms. These are
prior decisions, not a request to reinstall or requalify them during handover.

**SKIP3: dependent analysis skipped.** Supply the named exact permitted Rscript,
package profile or input. Download/install privately where allowed. If prohibited,
retain the skip and tell the user exactly what must be supplied. Never install a
nearby version and relabel it. Cell Ranger remains excluded by the user.

**FAIL2: no complete result.** Preserve raw/null/failed/partial/superseded
evidence; fix the named input, numerical, capacity, process or integrity cause;
retry into NEW output. Never silently repair identifiers, NaN/Inf, endpoints,
weights or unspecified scientific choices. Read WARNINGS.md first, then complete
conditions/logs and the admission status. Warnings contain decoded per-set
failures, including late failures after the first 100 clean rows.

Retain **all 68 worktrees**, the dirty root and indexes, private libraries, all
development JARs and source/evidence trees. The separately qualified native ACAT
JAR is still `9965571a5f24f6d701b4da693d20b8c44e58c4359da54dd932476dc1ee19fd7b`
at the path in PRIVATE-TRANSFER.json. Never overwrite it with a newer build.

The nine historical snapshot branches plus `codex/release-v0.3.1` remain local.
Automatic approval review rejected publishing those broad unpublished payloads
because they may contain sensitive material; specific user approval remains
pending. The prior private incremental Git bundle is retained locally and must
not be uploaded or merged as a workaround. Public current-task source and
artificial evidence are the scope of this push. Private manuscript/rendering
outputs remain local. A GitHub clone alone is **not** a full private handover.
