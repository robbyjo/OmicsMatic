# Native GCTA GRM component checkpoint

**The prepared-hard-call Java GRM component is qualified and included in the
normal JAR. The GCTA/COJO file workflows and catalog admission remain unfinished.**
The full 250-paper implementation/review goal remains active. Totals stay
**140 scoped integrations (26 native +114 upstream), 1,548 reviewed uses and
1,166 pending**, with 826 unchanged catalog manifest entries. This checkpoint
adds no GCTA/COJO integration credit and does not establish a full paper result.

The scientific/catalog predecessor is main
`23f5b5008e4e9e716e69a5c2c260e7764293ce4b`.
Current stage: `D:/projects/JLinAlg/build/gc-2`, branch
`codex/gcta-native-grm-20261120`. All **23 predecessor worktrees are preserved**;
there are now **24 worktrees**. Continue in a new worktree/output destination;
do not reset old checkouts or refresh old verifier fingerprints.

## Implemented scope and evidence

[`GctaHardcallGrm`](../../src/main/java/org/jlinalg/genetics/GctaHardcallGrm.java)
computes source GCTA 1.94.1 GRM algorithms 0/1 from caller-prepared autosomal
diploid allele counts. Ordered composite FID/IID identities, explicit missing
calls, excluded-marker dispositions, pair-specific called counts, source HWE
variance, 128-marker blocks and float32 output/algorithm-1 rounding are retained.
Matrices can be indefinite under pairwise missingness. Result storage is
defensive. Explicit input/pair/work capacities apply before materialization.

The existing generic GRM and conditional-model classes remain unchanged.
Genotype-file intake, sex/chromosome coding, polyploid preparation, allele
updates, external frequencies, dominance and additional filters are outside
this component. COJO/PCA/REML/LD-score/fastGWA/mtCOJO workflows are separate.

The normal executable JAR is **195,913,446 bytes**, SHA-256
`16e2baefe4f8f04e0d7ee3b92de4785c8882762f16ad61e302728ff9c454ad86`.
Its **23,522 predecessor payloads are byte-identical**; eight native GRM classes
are added. No existing product payload is removed or changed. No release/tag
has been created.

The [original-source corpus](../../src/test/resources/org/jlinalg/genetics/gcta1941-hardcall-originals.zip)
contains 35 cases: five retained initial cases and 30 new edge cases from 15
datasets/two algorithms. All 60 fresh original Windows/Linux edge executions
exit zero and their GRM/N binary bytes match across hosts. Exact rational
centered-product calculations independently check their pair counts and values
within the recorded float32 rounding bound. The 29 positive cases compare
**190,428 GRM/count float32 fields exactly per JVM**; six cases establish the
explicit strict no-estimate boundaries below.

New controls cover complete/missing 127/128/129-marker boundaries, excluded
markers around a raw block boundary, monomorphic/all-missing markers, rare/major
allele coding, constant heterozygotes, one-sample prepared input, indefinite
pairwise matrices, zero joint counts, Unicode/composite IDs, invalid calls and
dimensions, early capacity rejection, defensive snapshots, progress and
cancellation. These are scoped synthetic controls, not an assertion that every
possible data distribution or platform is qualified.

**All nine focused JUnit tests pass through the normal JAR** on Windows Java
21/25 and Linux Java 21: seven new component tests plus two existing generic-GRM
regressions. Main product classes load from the normal JAR; only JUnit/test/
launcher classes are added to the test classpath. The actual
[Java example](../../examples/gcta-native-grm/README.md) also runs on all three
JVMs with identical logical output. Compilation uses Windows JDK 21; Linux
source compilation is SKIP3 because `/usr/bin/javac` is absent. Linux runtime
checks use the exact source-built classes and do pass.

## Retained failure and correction history

The new cancellation test first fails on the predecessor: an interruption
requested by the last progress callback can still return a GRM. The correction
adds cooperative cancellation checks during pair rows, after progress callbacks
and before return. It changes no numerical equation, normalization or marker
filtering. The failing Gradle/JUnit run, exact old source, patch and succeeding
normal-JAR results are all retained. The old prototype's five original cases
are included in the new comparison corpus.

An initial final-build preparation attempt also fails because the custom
classpath-inspection task is incompatible with Gradle's configuration cache.
That attempt has no completed scientific tests or producer. The corrected
fresh build uses `--no-configuration-cache` for that inspection helper; normal
project source/build settings are unchanged. Its exact command and output are
retained separately. Do not relabel either failed attempt as successful.

The earlier original binary fastGWA attempts remain **FAIL2: NO COMPLETE
RESULT**, because their 768-SNP fixture cannot supply 2,000 autosomal SNPs for
tuning. Preserve both hosts' null-fit/partial diagnostics and retry with a
larger independent fixture in new output. Native GRM qualification does not
resolve that failure.

## Warnings and required actions

- **No complete native estimate:** a sample pair with zero jointly called
  retained markers, or no usable polymorphic markers, is refused. The original
  writes zero/N=0 in these cases; the declared strict boundary prevents those
  placeholders being reported as complete estimates. Six captured-source cases
  demonstrate this difference. Invalid input, integrity, numeric, capacity or
  cancellation errors also produce no complete result. Retain inputs and
  diagnostics, correct the cause and retry new output.
- **Do not clamp or repair valid source output.** Pairwise GRMs may be
  indefinite; P238's converged unconstrained heritability may be outside 0–1.
  Preserve literal IDs, alleles, populations, thresholds, editions and units.
  Never substitute a generic GRM/conditional model for a GCTA mode silently.
- **SKIP3: unavailable exact prerequisite.** Supply the named permitted
  runtime/input/JAR, or keep its dependent analysis skipped if installation is
  forbidden. Old/incomplete reported editions remain unresolved. Cell Ranger
  remains excluded. The two missing historical producer identities and exact
  recovery instructions remain in the [prior handover](HANDOVER_2026-11-20_GCTA.md).
- **The original prototype owner is frozen.** Its source pin is the old
  `12be9935b8a12c7f370eddd7d139dfd7b7b05635bfeb388f6b02f8b9c7ab3d1b`
  class source. Run it in its unchanged `build/gc-1`/`23f5b500` checkout;
  do not refresh its hash or apply its old source to this stage. Use the new
  component evidence for the corrected source.
- **Separate machine transfer is still required.** GitHub contains source,
  tests, selected synthetic evidence and handover documents. Ignored producer
  JARs, permitted upstream runtimes, full XML/PDF caches, private evidence and
  unrelated local worktree revisions remain local. Preserve all 24 worktrees
  and checksum/verify a separate transfer before moving machines. Both
  literature HTML reports remain withheld from Pages.

## Resume commands and next work

Read the [API/example guide](../../examples/gcta-native-grm/README.md),
[source roles](gcta-development/SOURCE-NOTES.md) and
[component evidence guide](gcta-native-development/README.md).
Focused normal build/tests, from a host with a permitted JDK:

```powershell
./gradlew.bat executableJar :test --tests org.jlinalg.genetics.GctaHardcallGrmTest --tests org.jlinalg.genetics.GenomicRelationshipMatrixTest
```

The saved-evidence check performs no new Java/model execution:

```powershell
python -B tools/literature/gcta_native/verify_evidence.py --out build/gcta-native-saved-check-1 --jar build/gcta-native-1/gradle-qualified-2/project-build/cli/omicsmatic-0.4.0.jar
```

Use a new output directory. Omitting/unlocating the exact producer records
SKIP3 for its integrity check; it does not imply fresh scientific execution.
Keep the exact captured JAR before changing build outputs.

Private current evidence root:
`D:/projects/JLinAlg/build/gc-2/build/gcta-native-1`.
Important children: `edge-fixtures-1`, `edge-original-1`, `edge-native-1`,
`gradle-before-cancellation-fix-1`, `gradle-qualified-1` (configuration-cache
failure), `gradle-qualified-2` (successful normal producer), `packaged-tests-1`
and `example-checks-1`. Prior GCTA source/runtime/advanced-model evidence stays
under `D:/projects/JLinAlg/build/gc-1/build/gcta-high-impact-1`.

1. Implement explicit prepared-data/native and original-runtime GCTA file
   workflows with complete input/model/partial/diagnostic retention,
   RUN/VERIFY/REPLAY, exact edition profiles and bounded execution. Qualify
   actual COJO selection/joint/conditioning, PCA, REML variants, LD-score modes,
   sparse GRM, the larger fastGWA retry and mtCOJO's distinct prerequisites.
2. Resolve source-role edition/settings ambiguities, including reported
   1.92.4/1.93.2/1.93.0, P140's old-edition/binary-fastGWA combination, P171
   tetraploid preparation and P248's ambiguous XML formula. A search preview of
   the [official changelog](https://yanglab.westlake.edu.cn/software/gcta/) lists
   beta-suffixed older assets, but direct page retrieval
   still fails; neither that index nor a nearby release establishes which
   runtime a paper used. Verify exact original assets/source before use.
3. Complete guarded GCTA/COJO source-role review and cumulative admission.
   Preserve all previous reviews and exact-before observations; handle missed
   P112 and proposed P127 comparison-only removal explicitly. Do not count
   overlapping GCTA/COJO names as separate studies/software by default.
4. Continue the high-impact backlog (limma, PEER, TwoSampleMR) and remaining
   1,166 individual reviews. The all-250 cohort, admitted GSEA and unrelated
   qualified components should not be restarted. Full goal completion remains
   unproven until every requirement and remaining tool/use is resolved.
