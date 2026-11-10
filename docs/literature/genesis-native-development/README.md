# Native GENESIS matrix partition component

This development API implements the **finite, symmetric, uniquely labelled matrix profile of GENESIS 2.42.0 `pcairPartition`**, using the observed Windows R 4.6.1 arithmetic profile. It selects the original ordered related/unrelated sets from supplied matrices. It is a separate component: **GENESIS remains missing as a toolbox integration**. PC-AiR PCA/projection, PC-Relate, null models and association tests remain unfinished. The integration count stays 99.

The API and saved verification require no R installation. Original reference regeneration needs the exact private Windows R/GENESIS runtime. Java17 bytecode is supplied, tested on Windows Java21/25 and Linux Java21. The released executable is unchanged.

## Use the API

Compile against `genesis-partition-api-javac21.jar` in this directory. Supply complete matrices with the same ordered labels on their rows and columns; divergence matrices can use their own explicit ID order. Matrices and ID lists are copied, and outputs are immutable.

```java
import java.util.List;
import org.jlinalg.genetics.GenesisPcairPartition;

var kinship = new GenesisPcairPartition.Matrix(
    List.of("A", "B", "C", "D"),
    new double[][] {
        {0.5, 0.25, 0, 0}, {0.25, 0.5, 0, 0},
        {0, 0, 0.5, 0.25}, {0, 0, 0.25, 0.5}
    });
var choices = new GenesisPcairPartition.Options(
    0.02, -0.02, null, null); // explicit cutoffs, optional forcing/selection
var limits = new GenesisPcairPartition.Limits(
    100, 1_000_000, 10_000_000);
var result = GenesisPcairPartition.partition(kinship, null, choices, limits);
System.out.println(result.related());   // [A, C]
System.out.println(result.unrelated()); // [B, D]
```

These cutoffs are illustrative explicit choices, not inferred paper settings. Input order resolves final ties. The algorithm retains initial total-kinship/divergence priorities and the original forced-set degree bookkeeping. It does not substitute a generic independent-set optimizer.

**Read `warnings()` and `unrelatedSetPassesDeclaredKinshipCutoff()`.** Source forcing or matrix simplification can leave related pairs in the returned unrelated set. The complete original sets remain intact and `unrelatedPairsAboveThreshold()` reports the finding. **Skip dependent use when that flag fails.** Review forcing, the population and source assumptions before a new explicit request; do not silently repair the source set.

The native profile refuses NaN/Inf values, duplicate/missing/invalid Unicode IDs, asymmetric/incomplete matrices and nonfinite cutoffs. Literal `NA`/`NaN` strings remain valid IDs. It never fills values, symmetrizes data, renames samples or drops rows to suppress a failure. `SourceFailure` retains the original matrix-host cause category and preceding warnings. The original singleton-selection dimension failure and all-related-population refusal remain failures.

Logical limits apply to the complete supplied matrices, even if selection later excludes samples. The hard ceiling is 5,000 samples; matrix construction validates and copies arrays before partition budgets are applied. These limits are not hard OS memory quotas or evidence of production-cohort performance. Choose a justified heap and capacities; preserve failures.

## Saved verification and recovery

From the repository root:

```powershell
python tools/literature/verify_genesis_partition_evidence.py --out build/literature-review/genesis-runtime/my-native-replay-1 --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe"
```

Use Python3.11+ and an explicit permitted Java17+ executable. Linux can use `/usr/bin/java`. Choose a **new** output path. This checks both compiler builds against every retained source case and scalar sum; it does not run R, download or install software.

**SKIP3: missing/forbidden Java prerequisites mean skip verification.** Supply the named permitted prerequisite, installing only where allowed; otherwise keep verification skipped. **FAIL2: admit no verified result.** Preserve the attempt, read `WARNINGS.md`, `COMPLETED.json` and the named cause, correct it explicitly and retry new paths. A successful replay checks integrity and complete scientific relationships; it does not authenticate the producer or establish biological validity.

To regenerate original controls where the exact private runtime is permitted, use `collect_genesis_partition_reference.py --out NEW_DIRECTORY` after the separately documented GENESIS setup. The helper uses a fixed, hash-checked project-owned base fixture, pins the supplied complete R distribution/private library before and after, and performs no installation. Missing compatible prerequisites return SKIP3 with required actions. A current2.42.0 control does not reproduce the papers'2.16.1/2.26.0 releases.

## Evidence and source scope

The original collection has 75 case dispositions: 69 successful source calls and six source failures. Of these, 67 successes fit the native finite-matrix profile; two nonfinite source inputs are explicitly refused. The NaN-input source finding includes unassessable/duplicate identities and remains preserved. Inf-input source output is also retained; its input lies outside this finite profile.

Two compiler builds on Windows/Linux give **268 complete matching ordered partitions, 24 reproduced original failures and eight explicit nonfinite refusals**. All 2,048 scalar-sum comparisons match original R bits. Thirty focused API test executions cover identity/value preservation, immutable inputs, serialization of source warnings, source-specific failures and logical capacities. A fresh public-worker collection preserves all 528 compared original files; dependency skips and existing-output protection are separately checked. Failed compilation/reference-serialization attempts remain retained.

The kinship tie-breaking sum explicitly emulates the observed64-bit-significand long-double accumulation and pre-conversion `DBL_MAX` clamp of R4.6.1 `rsum`; ordinary double summation or an unrounded exact sum can choose different ties. Exact inputs, original outputs, source snapshots and limitations are bound in `VALIDATION.json` and `controls.zip`.

GENESIS source behavior is credited to its authors, including Matthew P. Conomos and Stephanie M. Gogarten, under GPL-3.0. R source is credited to the R Core Team, Robert Gentleman and Ross Ihaka under GPL-2.0-or-later; the retained headers and project GPL-3.0 license accompany source evidence. Original runtime binaries, package archives, participant data and primary article prose are not bundled. Broader GENESIS workflows and the full250-paper goal remain open.
