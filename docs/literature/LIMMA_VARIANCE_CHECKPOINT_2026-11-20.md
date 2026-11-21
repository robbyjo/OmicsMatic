# Native limma variance checkpoint and next work

**A native limma variance component is implemented and qualified. The full
limma integration and 250-paper implementation/review goal remain unfinished.**
Use the [component guide](LIMMA_VARIANCE.md) for exact scope and API usage.

The normal producer contains untrended `fitFDist`, explicit legacy nonrobust
`squeezeVar`, supplied-hyperparameter `.squeezeVar`, real statmod `logmdigamma`
and scalar inverse trigamma. It retains source filtering/offsets and valid
infinite prior df. Existing generic moderated/voom inference APIs are unchanged.
No R installation or subprocess is needed for the native component.

Original R4.6.1 namespaces match recovered limma3.68.5/statmod1.5.2 bodies and
formals. There are 372 original controls (seven errors, 36 warning cases).
The normal Java17-target build and all **379 tests** pass. Packaged tests pass
on Windows21/25 and Linux21, checking code origin in the actual normal JAR and
**23,873 original numeric fields per JVM**, maximum relative difference
`2.041495319926257e-15`. All three actual examples match seven original fields.
All 23,548 old JAR payloads remain exact. The initial developer-launcher compile
failure from a missing compile-only API Guardian annotation JAR is retained;
adding that existing dependency fixes it without weakening strict javac or
changing the scientific producer. No full statistical model was fitted here.

The fresh all250 hash/DOI scan now has 20 source-role candidates. The
[review proposals](limma-source-review-proposal/README.md) distinguish operators,
versions and providers. P237 uses edgeR `voomLmFit` and adds no limma proposal.
P228's reported limma attribution/lowercase `lmfit` remain unresolved; actual
current namespaces and original edgeR source are retained as separate evidence.
P088/P183/P203 additions and P059 historical-only exclusion remain proposals.
The first NAMESPACE audit assumed literal exports; its refusal is retained,
followed by an original exportPattern check. Canonical metadata is untouched.

Totals remain **250 papers, 141 scoped families (26 native +115 upstream),
1,575 reviewed uses and 1,140 pending observations**, with 829 manifest entries.
The current component adds zero family/review credit. COJO remains an alias
with zero extra credit. The source interval remains 2023-10-23..2026-10-23.

## Continue next

1. Implement and qualify original-R limma model/voom/TREAT, correlation/blocking,
   batch/covariate-removal and cameraPR workflows as required by the papers.
   Source-match modern versus legacy variance fitting and trend/robust options.
   Retain complete original model/null exports, warnings, inputs and failures;
   no generic inference fallback. Native modern unequal-df estimation remains
   unimplemented; never route its default to `squeezeLegacy` silently.
2. Recover exact reported source/runtime editions and author preparation where
   permitted. Distinguish ProStaR/dreamlet and edgeR `voomLmFit` providers and
   reported 3.44 without inventing a patch. Current3.68.5 is not a substitute.
3. Build discoverable file RUN/VERIFY/REPLAY and global CLI/example help, with
   prominent warning recovery and integrity/convergence/process controls.
   Qualify the exact operations and appropriate original comparisons before
   admitting limma or changing the pending source observations.
4. Use the current GCTA registry chain for guarded catalog admission, preserving
   all 1,575 old reviews and unrelated metadata. Continue PEER, TwoSampleMR and
   the other high-impact gaps. Keep the full goal unfinished until its full
   source/tool requirements are actually satisfied.

**FAIL2: NO COMPLETE RESULT.** Read the component warnings and check output
validity. Preserve raw input, original objects/null/partial exports and logs;
fix the named failure and retry new output. Original exit zero is insufficient
when results are nonfinite or a fit failed. No silent identifier/allele,
population, version, threshold or estimator repair, or clamping valid
unconstrained model estimates. Source-defined variance offsets are reported.

**SKIP3: dependent analysis skipped.** Obtain the exact permitted missing
runtime/package/source/input and rerun new output, or retain the skip if it is
unavailable/installation is forbidden. Downloads/private installation remain
authorized where permitted. Cell Ranger is excluded. Linux source compilation
is still skipped because javac is absent; execution of transferred classes is
separately verified. Native variance use needs no R runtime. Original-control
regeneration does need its exact R/packages/source and must not be fabricated.

## Exact resume paths

Current checkout: `D:/projects/JLinAlg/build/lm-2`, branch
`codex/limma-native-20261120`, based on `3efd2dad`.
Private stage: `build/limma-native-1` beneath that checkout. It retains both
official archives/member inventories, expanded primary full-text intake, actual
namespace membership, original R objects and seven errors, normal Gradle
producer/tests, the first refused launcher/audit attempts and corrected runs.

Latest normal producer:
`D:/projects/JLinAlg/build/lm-2/build/limma-native-1/normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar`,
**196,035,628 bytes**, SHA-256
`707a65d628f4f82007afb64c22ca88885cafa1d95a5aea0904a3431747d02821`.
The old `gc-7` producer and its exact owner remain intact. See the
[saved checkpoint packet](limma-variance-checkpoint/README.md) for retained
qualification bytes and a portable byte-only reader.

The prior [handover](HANDOVER_2026-11-20_LIMMA.md) retains exact package-library,
full-text-cache and historical GCTA recovery paths. Preserve all **32 worktrees**
and the Git common metadata. **Before changing machines, separately transfer
ignored exact runtimes, current/old producer JARs, complete private scientific
evidence/recovery stores and full text with checksums. GitHub is not a complete
workstation backup.** Never reset, clean or overlay historical dirty worktrees.
Both literature HTML reports remain withheld from Pages. No release or full-goal
completion is claimed.
