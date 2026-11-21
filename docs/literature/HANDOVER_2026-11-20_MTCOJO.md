# Toolbox handover, 20 November 2026: mtCOJO development checkpoint

**The full 250-paper implementation/review goal is unfinished. Start here when
continuing.** This checkpoint saves the current Java implementation, tests,
original comparisons and development scripts. It supersedes the older statement
that mtCOJO is unimplemented. It does not admit GCTA/COJO to the catalog.

## Current totals and priorities

| Item | Verified checkpoint |
| --- | --- |
| Papers collected | 250: 71 Nature, 104 Nature Genetics, 75 AJHG |
| Frozen publication interval | 2023-10-23 through 2026-10-23; no extension used |
| Named / effective tool definitions | 446 / 485 |
| Tool-use observations | 2,714; 1,548 reviewed, **1,166 pending** |
| Admitted scoped integrations | **140: 26 native Java +114 upstream** |
| Including three pre-existing integrations | 143 |
| Missing / method-overlap definitions | 259 /35 |
| Current literature manifest | 826 entries; unchanged by this checkpoint |
| New GCTA/COJO catalog credit | **0** |

The interval above is the frozen cohort chosen when the task began. Do not
silently replace it with a rolling interval or count a development component
as an admitted whole-tool implementation. Paper collection is complete;
individual source review and toolbox implementation are not complete.

Prioritize high-impact missing scopes. First finish the current GCTA/COJO
qualification and source-role admission. Then continue **limma (16 existing
observations), PEER (15), TwoSampleMR (12)** and the remaining individual reviews.
These observation frequencies are prioritization inputs, not proof that all
uses are interchangeable or that every named tool needs a separate engine.

## What is saved now

- `MtcojoConditional` supplies native Java prepared-parameter single/multiple
  covariate conditional effects, standard errors and P values. The multiple
  covariate calculation follows `D^-1 R^-1 D b`. It does **not** estimate GSMR,
  LD scores, heritability, genetic correlation or prevalence transformations.
  It preserves signed estimates and refuses undefined variance/factorization,
  nonfinite results, capacity exhaustion and cancellation without partial results.
- Standalone `org.jlinalg.cli.GctaWorkflowCli` has development original-runtime
  `model:"MTCOJO"` RUN/VERIFY/REPLAY support for exact GCTA 1.94.1, explicit
  ordered traits, all 22 reference/weight LD shards, and GSMR or supplied causal
  effects. It retains inputs, available exports, logs, status and warnings.
  Global `gcta-tools` registration is still absent.
- Eight original successful compositions (single/multiple covariates, GSMR/
  supplied effects, Windows/Linux) each export 10,000 conditional rows. The
  eight prototype captures and eight current normal-JAR runs match the
  corresponding original table tokens and pass separate-JVM offline VERIFY.
- Eighteen additional actual original runs establish prevalence, low-instrument,
  supplied-zero, supplied-100 and decimal LD-FDR behavior. Their raw failures and
  nonfinite tables remain saved; an original exit code of zero is not admission.
- The native reference corpus checks **300,000 original conditional fields per
  JVM**, with independent NumPy/scalar/`erfc` point calculations. Bounds account
  for six-significant-digit and fixed-six-decimal source parameter/table exports.
  They are software comparison bounds, not confidence intervals or added causal
  estimation uncertainty; they do not recover unexported internal precision.
- The Java17-target normal build passes **24 focused tests**. Packaged execution
  on Windows Java21/25 and Linux Java21 passes the same 24 tests, including eight
  frozen mtCOJO captures and a resealed invalid conditional-SE refusal. All
  **56 earlier complete GCTA captures verify on each JVM**. See saved receipts
  for commands, exact bytes and the limited scope of each check.

Normal producer: `build/gc-5/build/gcta-mtcojo-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar`
under `D:/projects/JLinAlg`, **196,022,241 bytes**, SHA-256
`3de49eb979a01116320a9ebc0d2c76174ae058cad8e45c68814dedce1c2044e2`.
Compared with the previous producer, 23,533 payloads are unchanged, eight GCTA
classes change and seven mtCOJO classes are added; none are removed.
Independent Linux compilation remains **SKIP3: Linux javac is absent**.
Actual Java17 execution was not performed; Windows JDK21 compiled to Java17.

The [saved evidence directory](gcta-mtcojo-development/README.md) explains the
lossless archive and read-only verification. Development helpers are retained
under [tools/literature/gcta_mtcojo](../../tools/literature/gcta_mtcojo/README.md).
They preserve exact historical workspace paths and exclusive-create output
directories; they are evidence, not a portable install or safe blind rerun.

## Prominent warnings and the required action

**Prevalence does not cause a liability-scale conversion in this source profile.**
GCTA 1.94.1 parses prevalence columns but does not consume them downstream in
mtCOJO. Actual unequal and swapped-prevalence runs produce identical tables.
Declare `effect_scale:"as_supplied"`; supplied prevalences are retained metadata.
Do not claim P090's reported 1.92.4 prevalence-dependent analysis is reproduced.
Resolve the exact older edition and its behavior before admitting that scope.

**The source parses `--gsmr-ld-fdr` with integer conversion.** The default 0.05
is obtained by omitting the flag. Explicit 0.05 is parsed as zero in actual
source runs. This adapter omits the flag for its genuine default, permits
explicit integer 0/1, and refuses other decimals. Do not silently truncate them.

**Supplied causal effects with absolute value at least 100 are source sentinels.**
They invoke GSMR rather than representing the supplied effect. The adapter
refuses them. Zero is a legitimate supplied effect. Use a supported explicit
mode; do not replace failed GSMR with an invented fallback result.

**Insufficient-instrument GSMR can exit zero and write NaN tables.** The source
announces an rg-based fallback and then overwrites it with the failed GSMR
return. Such output is **FAIL2: NO COMPLETE RESULT**, retained for diagnosis.
The original supplied-effect mode skips GSMR and can remain valid on the same
fixture. Do not infer result validity from an exit code or warning alone.

**Source matrices and printed precision must retain their meaning.** The source
`rg:` matrix contains h2 on the diagonal and genetic correlations off diagonal.
The native API requires a correlation matrix with unit diagonal and separate
positive covariate genetic variances. The conditional sampling matrix uses unit
diagonal despite the separate univariate intercepts. Preserve valid negative
intercepts and signed/indefinite estimates; do not clamp or project matrices.

**Input transport is strict.** Keep literal SNP/allele/trait identities and the
declared reference population. Supply all 22 LD shards and reference M files;
M is not assumed equal to the number of LD-score rows. Signed LD scores remain
unchanged. Gzip input must pass CRC checks, end with LF, and obey the source's
1,023-byte record bound. Do not repair missing identifiers, strands, alleles,
nonfinite values, final records or thresholds silently.

**SKIP3: dependent analysis skipped.** Supply the exact permitted missing
runtime/input in a NEW request, or retain the skip if installation is forbidden.
An unimplemented analysis remains unfinished, not a fabricated prerequisite skip.
Cell Ranger remains excluded at the user's request. Previously accepted FIMO
research/nonprofit and GATK3.8 academic noncommercial terms remain in scope.

**FAIL2: NO COMPLETE RESULT.** Read `WARNINGS.md`, `analysis-status.json`,
`manifest.json`, model objects and worker logs. Preserve every raw input,
partial/null export, failure and superseded attempt. Correct the stated cause
and retry into a **new directory**. Never interpret an incomplete bundle as a
complete scientific result. Offline VERIFY checks saved invariants and integrity;
it does not refit original models or establish authenticity or biological validity.

## Exact next work

1. Finish mtCOJO-specific qualification of the current normal producer. Add
   actual gzip, zero-effect, prevalence and supplied-low-IV adapter runs;
   actual low-IV GSMR, sentinel, fractional-FDR, missing-runtime/LD, CRC and
   truncated-record refusals; MT-specific timeout/output/alias controls; and
   actual nested-path cross-host REPLAY with explicit runtime-flavor consent.
   The current saved unit/source controls and positive runs do not establish
   all these execution paths. Preserve all attempts and separately verify them.
2. Add and execute a beginner-friendly native conditional API example and an
   original-runtime request builder. Explain which already-fitted parameters
   the native component needs. Do not present it as native full mtCOJO.
3. Resolve exact older editions: 1.92.4, 1.93.2 and 1.93.0, plus P140's
   1.91.7beta/binary-fastGWA statement. Do not substitute a beta/nearby release.
   Resolve P171 wheat ploidy/preparation and P248's formula/threshold ambiguity.
   Consult [the 22-context source census](gcta-development/SOURCE-NOTES.md).
4. Register global GCTA command discovery with dispatcher compatibility checks,
   then perform guarded source-role/cumulative catalog admission. Preserve all
   1,548 existing reviews. Account for P112's missed full-name mtCOJO role and
   P127's proposed comparison-only removal with exact before/after evidence.
   The 17 GCTA and nine COJO observations span **18 distinct papers**, not 26
   independent studies or tools. Retain unresolved versions/preparation as such.
5. Continue high-impact missing tools and all 1,166 pending reviews. Keep the
   catalog, warnings, implementation scope and manifest consistent. Admit only
   the precise qualified operations; do not label the whole goal complete until
   the remaining implementation/review work and required evidence are closed.

The [previous model checkpoint](GCTA_MODEL_CHECKPOINT_2026-11-20.md) retains the
completed REML repair, 104 export comparisons, independent AI-covariance checks
and actual process controls. Its mtCOJO-unimplemented statements are historical.
Do not refresh old reader-source hashes to accept new code. Run old readers in
their exact preserved owner, or make a separately named, qualified new profile.

## Resume paths and transfer checklist

Current checkout: **`D:/projects/JLinAlg/build/gc-5`**, branch
`codex/gcta-mtcojo-20261120`, based on main
`e62e64302995a8672a89b8bbfbb7b80d7c48822c`. Read GitHub main for the handover
commit; the commit itself is the source owner. No release is requested or made.
The full goal remains unfinished; a handover is not a completion claim.

Private stage: `D:/projects/JLinAlg/build/gc-5/build/gcta-mtcojo-1`:

| Directory | Contents |
| --- | --- |
| `synthetic-inputs-1` | Frozen 512-subject, 10,000-marker, 22-chromosome synthetic reference and three GWAS/LD input families |
| `original-1.94.1-1` | Eight successful actual original runs |
| `original-behavior-controls-1` | Eighteen actual original edge/control runs, including NaN exports |
| `compile-1`, `prototype-workflow-1` | Initial Java source/class owner and eight development captures; not normal-producer runs |
| `native-reference-corpus-1`, `workflow-test-corpus-1` | Reference construction receipts; public test ZIPs retain exact corpus bytes |
| `normal-gradle-1`, `normal-gradle-2` | Preserved 23-test earlier and 24-test current source/build owners |
| `normal-workflow-runs-1` | Eight current normal-JAR RUNs and separate-JVM VERIFY, compared with original tables |
| `packaged-qualification-1` | 24 packaged tests plus 56 prior capture checks per JVM; payload transition |

Original source/runtime owner:
`D:/projects/JLinAlg/build/gc-1/build/gcta-high-impact-1/upstream-1.94.1-intake-1`.
Source tag `v1.94.1`, commit `6da64e9d8be838d01c34efb974e69c72e861f4c2`;
read its GPLv3 `LICENSE`. Windows executable SHA-256
`2529bccb2f685332f115726c3cb2bf8cab91f12ae3344a991e3a52d751bdb07e`,
adjacent `libomp.dll` SHA-256
`ee9ce7570217bbccf87d569eb06e71fe9ec246899d2b2fe4fd918215e29e1c79`;
Linux `download` ELF SHA-256
`dbb2b5e5d51e278328ff297a403d3dd8ee5f5280f0b6d3d8db74b3e7fead7b49`.
Existing WSL distro: `JLinAlg-Omics-PyMOL-20261026`; do not restart it.

**GitHub is not a complete workstation backup. Before changing machines,
separately transfer the ignored runtimes, exact producer JARs, complete private
evidence/recovery stores, primary full text and all 27 worktrees with checksums.**
Preserve the dirty root/historical overlays; do not reset/clean them or apply
old overlays over main. They were preserved through earlier publications.
Private full-text intake is at `D:/projects/JLinAlg/build/literature-review/xml`;
earlier GSEA P077 supplement/producer owners and missing historical producer
details are retained in the [previous workflow handover](HANDOVER_2026-11-20_GCTA_WORKFLOWS.md).
Two missing historical producers remain unresolved; a rebuilt or nearby JAR
cannot substitute for the exact saved hash. Retain the recorded SKIP3 checks
unless their exact permitted backups are supplied.

Both literature HTML reports remain withheld from Pages. This handover changes
no site or release. No input, old evidence, failure or worktree is retired.
