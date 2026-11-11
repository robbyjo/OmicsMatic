# Native diploid LD and reference-window API

`HardcallLd` implements unphased diploid allele-count frequencies and
pairwise-complete Pearson LD from packed BED calls. The FUMA reference API now
also records inclusive base-pair and variant-rank eligibility windows. This is
the numerical foundation for reference preparation; **the typed BED-to-reference
command, captured resource replay and automatic locus handoff are not available
yet**. The FUMA catalog badge remains missing while that broader work continues.

The [validation record](fuma-ld-core/VALIDATION.json) identifies the isolated
development JAR. This API does not change the canonical executable or establish
equivalence to the fourteen papers' historical FUMA versions/resources.

## Run the synthetic API example

From the repository root in PowerShell:

```powershell
$ldJdk = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin'
$ldProof = Get-Content -LiteralPath docs/literature/fuma-ld-core/VALIDATION.json -Raw | ConvertFrom-Json
$ldJar = (Resolve-Path $ldProof.artifact.path).Path
New-Item -ItemType Directory -Path build/fuma-ld-example-classes-1 -ErrorAction Stop | Out-Null
& "$ldJdk/javac.exe" --release 17 -Xlint:all -Werror -cp $ldJar -d build/fuma-ld-example-classes-1 examples/fuma-ld-core/Example.java
if ($LASTEXITCODE -ne 0) { throw 'LD example compilation failed; retain the diagnostic.' }
& "$ldJdk/java.exe" -Xmx128m -cp "build/fuma-ld-example-classes-1;$ldJar" Example
if ($LASTEXITCODE -ne 0) { throw 'LD example did not complete.' }
```

Choose new output paths on retry. If the identified artifact is unavailable,
skip this example until a validated current-source artifact is built/provided.
Native execution requires Java only; original PLINK is used for validation.

## Samples, calls and statistics

Create one `HardcallLd.Frame` with an explicit identity, original sample count,
ordered distinct sample indices, limits and a cancellation callback. Every row
in a pair must share that same immutable frame object. Matching row lengths or
identical labels in separate frames do not prove alignment.

PLINK 1.9 LD uses founders. The native numeric API leaves sample selection to the
caller. With `PlinkBedDataset`, explicitly select the intended FAM founder and
population rows before creating the frame; no ancestry, relatedness or sample
population is inferred. The declared identity is a label, not authentication.
Caller inputs must stay unchanged during calculation.

`encode` accepts one complete SNP-major BED row. Codes 00,10,11 represent two,
one,zero BIM A1 alleles; 01 is missing. It rejects truncated/oversized rows and
nonzero unused padding. Masks are owned by the returned immutable object.
`packedCalls()` returns a fresh row in the selected sample order, with zero
padding. Supplied allele labels remain the caller's responsibility.

Frequencies use nonmissing selected diploid calls. A1 frequency and the minor
frequency are separate; A1 can be the major allele. Equal frequencies retain
`TIED`, and no observed call yields `UNOBSERVED` with NaN frequencies. These are
not zero-frequency observations.

LD uses only the intersection of nonmissing calls, without imputation or
marginal-mean substitution. Bit counts produce exact integer sufficient
statistics before floating division. The result retains paired N, sums,
cross-products, covariance/variance numerators, signed correlation and r-squared.
`INSUFFICIENT_PAIRED_CALLS`, `CONSTANT_FIRST`, `CONSTANT_SECOND` and
`CONSTANT_BOTH` retain NaN correlations. A marginally variable marker can become
constant in a particular pair's observed samples. **Undefined LD must never be
stored as zero or silently treated as below a sparse reporting floor.**

An exceptional floating overshoot invokes an exact integer/rational check and
records `exactRatioRecovery`; no generic probability clamp is used. The sample
bound protects integer products from overflow. Frame/row storage and complete
pair word-work are admitted against caller limits and half the current heap.
The future reference builder must additionally bound its entire metadata,
window cache, pair family, input capture and outputs; per-pair limits do not
bound an arbitrary caller that retains all rows or results. Limits/cancellation
are application checks, not an OS sandbox.

**Only the declared diploid unphased model is supported.** This API does not
implement phased LD, D-prime, dosage uncertainty, sex-chromosome ploidy/weighting,
MAF imputation or automatic founder selection. Do not apply those interpretations
to a successful allele-count calculation.

## Window coverage in FUMA references

The existing four-argument `Reference` constructor retains its unbounded sparse
contract. The new constructor additionally requires `LdWindow(maximumDistanceBp,
maximumIndexGap)`. Both comparisons are inclusive. Rank is ascending genomic
position within each chromosome of the declared reference; input list order
does not establish rank. One reference variant per position is still required.

`coverage(firstUid,secondUid)` distinguishes `SELF`, `STORED`,
`BELOW_DECLARED_FLOOR`, `OUTSIDE_DECLARED_WINDOW` and `DIFFERENT_CHROMOSOMES`.
Only unrecorded **in-window** pairs are declared below the stored floor.
Out-of-window pairs are excluded by the protocol without asserting their LD.
Stored out-of-window pairs fail admission. A zero floor requires every eligible
within-chromosome pair, including zero-valued pairs. Windowed locus results
prominently warn that protocol independence is not proof of biological absence
of long-range LD.

The original PLINK window count `k` corresponds to maximum index gap `k-1`.
The current [FUMA tutorial](https://fuma.ctglab.nl/tutorial#referencePanel)
describes reference preparation with PLINK1.9 and an r-squared reporting floor
of0.05; the [PLINK LD documentation](https://www.cog-genomics.org/plink/1.9/ld)
describes its count/distance windows and founder-only calculations. The retained
original controls test the precise boundaries rather than inferring paper
defaults. **The existing `fuma-tools --method loci` file parser does not yet
expose these new window/bundle options.** Use the Java constructor explicitly
until the typed preparation/handoff work is complete.

## Evidence, failures and required action

The candidate is tested with56 tests per JVM on Windows Java21/25 and Linux
Java21 using128MiB heaps. These include32 new tests and24 prior locus tests.
Ten fresh original PLINK1.9.0 controls provide7,568 binary double LD values and
228 frequency rows. They cover all4,096 three-sample diploid/missing vector
pairs, founder exclusion, explicit keep selections, 63/64/65-bit boundaries,
2,504 founders, indel labels and actual original distance/rank report membership.
Separate actual-original probes retain exact0.05/0.1 boundary inclusion.

Original undefined estimates must match native undefined states. Zero and one
are checked exactly; other binary reference values use absolute3e-15 plus
relative1e-12 tolerance. This is not a bit-for-bit claim for every upstream
edition/platform. No biological accuracy, historical panel reconstruction,
public reference-preparation workflow or whole-FUMA claim follows.

Invalid frames, malformed calls, unknown IDs/windows or capacity failures raise
an exception and return no completed value. Cancellation propagates without a
prefix result. Required action: retain inputs/diagnostics, correct the named
cause or explicitly raise permitted limits/heap, and rerun the complete request.
When integrating a future stream/sink, treat every emitted row as provisional
until the entire request completes. Unavailable/forbidden dependencies in an
upstream analysis must skip that dependent analysis with clear recovery; this
native API itself needs no external software installation.
