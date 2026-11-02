# Native reported composite-proxy scores

`MashCompositeScores` implements the reported dreamlet composite calculation
used by the P104 companion analysis and cited by P024. It supports all-included
or at-least-one-included conditions, multiplied by all-excluded complements.
The native API explicitly records the **independent marginal proxy assumption**.
It does not integrate the joint mash posterior, test independence, establish
biological specificity or return a calibrated frequentist P value.

The [source-role audit](mashr-composite/role-audit.json) records the exact paper
paragraphs, author-code commits, thresholds and unresolved input choices. The
earlier frozen drafts are retained as historical evidence. The relevant method
is now source-defined; whole-paper reproduction and catalog promotion remain
open. Study registration is a separate integration step. The existing summary
command's unsupported `formal_composite` placeholder remains unchanged; do not
treat it as this newly identified, explicitly named proxy method.

## Run the synthetic example

From the repository root in PowerShell:

```powershell
$compositeJdk = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin'
$compositeProof = Get-Content -LiteralPath docs/literature/mashr-composite/VALIDATION.json -Raw | ConvertFrom-Json
$compositeJar = (Resolve-Path $compositeProof.artifact.path).Path
New-Item -ItemType Directory -Path build/mashr-composite-example-classes-1 -ErrorAction Stop | Out-Null
& "$compositeJdk/javac.exe" --release 17 -cp $compositeJar -d build/mashr-composite-example-classes-1 examples/mashr-composite/Example.java
if ($LASTEXITCODE -ne 0) { throw 'Example compilation failed; retain diagnostics.' }
& "$compositeJdk/java.exe" -Xmx128m -cp "build/mashr-composite-example-classes-1;$compositeJar" Example
if ($LASTEXITCODE -ne 0) { throw 'Example did not pass.' }
```

Use a new class-output directory on retry. If the identified development JAR is
unavailable, skip this example until an independently validated artifact is
provided. The canonical executable under `build/cli` is unchanged. Native
execution needs Java only. R is used for reference validation, not user analyses.

## Declare the method and selectors

For marginal sign proxy `p`, the method is:

| Requested mode | Source calculation |
| --- | --- |
| ALL | `prod(p[include]) * prod(1-p[exclude])` |
| AT_LEAST_ONE | `(1-prod(1-p[include])) * prod(1-p[exclude])` |

Choose `Scale.SIGN_PROBABILITY` for supplied `p`, or `Scale.LFSR` to calculate
`p=1-LFSR`. No effect/SE, unsigned-P or missing-data conversion is inferred.
Declare `Assumption.INDEPENDENT_MARGINAL_PROXIES`, complete matrix dimensions,
named patterns, limits and a cancellation callback. Patterns use exact zero-based
condition indices. The original helper matches exact column names; regex-looking
names are not automatically expanded. Resolve group membership explicitly.

The original source keeps repeated selectors and allows overlap between include
and exclude sets. The native score preserves those multiplications and reports
their counts. **An overlapping proxy calculation is not the probability of a
logically consistent joint present/absent event.** Do not silently deduplicate,
expand patterns or replace a nonzero overlapping score with a different method.
Unlisted conditions are ignored. Empty ALL inclusion has factor1; empty
AT_LEAST_ONE inclusion has factor0; empty exclusion has factor1. A declared
zero-row or zero-condition matrix retains these source identities.

P104 explicitly uses `>0.5` and also reports a stricter `0.8` cutoff; the exact
boundary at `0.8` is not established by the inspected methods/caller. The control
suite separately checks the API's explicitly requested strict `>0.8` decision.
P024 reports an
inclusive shared-component cutoff of `>=0.05`. `Score.passes` requires the chosen
threshold and strict/inclusive boundary explicitly. The P024 analysis script
separately removes shared genes at `>=0.01` for downstream correlations. These
are recorded as distinct reported/code steps, not silently reconciled to `0.95`.
The actual P024 composite-producing CSV and its generating code were not found
in the inspected public script; the new implementation follows the cited
published formula and pinned helper, without claiming that missing data lineage.

## Missing inputs and arithmetic diagnostics

`Missing.REJECT` rejects all missing marginal inputs. Explicit
`SOURCE_ZERO_PROBABILITY` reproduces the original helper's NA/NaN-to-zero
probability rule. This is a source-specific convention, **not evidence that an
untested effect is biologically absent**. Results retain total missing matrix
cells and each score's selected missing occurrences. Infinite or out-of-range
probabilities and ragged matrices always fail the native probability contract.

The primary `sourceScore` preserves separate rounded source products followed
by the literal double complement and multiplication. Its factors are available
as `sourceInclusion` and `sourceExclusion`. Products are rounded once from exact
binary factors; the native code does not claim bit-for-bit identity to every R
platform's extended-precision product accumulator. Reference tests check numeric
tolerance and exact decisions for the reported thresholds.

`stableLogScore` uses the original input scale and stable log1p/expm1 arithmetic
to diagnose lost precision without replacing the source score. States distinguish
`EXACT_ZERO`, `SOURCE_ROUNDING_TO_ZERO`, `UNDERFLOW_TO_ZERO` and `DEFINED`.
Rounded marginal occurrences and material source/log discrepancies are retained.
A finite stable log beside a rounded zero must not be silently turned into a
different threshold decision. Exact zero has log `-Inf`. Always inspect
missing, repeated/overlapping selectors and numerical-loss diagnostics.

## Validation and recovery

The [validation proof](mashr-composite/VALIDATION.json) covers 440 tests per JVM on
Windows Java21/25 and Linux Java21 at 128 MiB. New tests include 30 cases executing
the **unmodified original** `compositePosteriorTest` and internal helper, plus
14 analytic, capacity, cancellation and complete 26,000-feature/20-condition tests.
The Artistic-2.0 original source and license are retained in the reference ZIP.
The full dreamlet package is neither installed nor validated by this utility
test. The original reference is pinned at version1.9.3/commit
`6ddcb83fb060eae989e541b6b82b46b3eaebd7bb`; the exact dreamlet binary edition used
by the papers is unreported and is not inferred.

Limits cover complete input/selector cells, work, family metadata and owned
storage, with planned owned storage constrained to half the current heap.
These are admission checks, not an OS sandbox. Invalid input/capacity or callback
cancellation raises an exception and returns no prefix. Required action: retain
the request and diagnostics, correct the named cause or explicitly raise only
permitted limits/heap, and rerun the complete family. Do not claim a completed
paper workflow from a successful utility call or a high proxy score.
