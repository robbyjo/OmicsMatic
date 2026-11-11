# Native HOPS evidence statistics

**This is a native statistics component, with zero new catalog-tool credit.**
Complete HOPS/eager admission, MALT/LCA/MaltExtract filtering, the paper roles and
the full 250-paper implementation goal remain open. The component produces
statistics and class matrices; it does not produce the original PDFs or RData,
run upstream programs or authenticate biological material. The released JAR and
central catalog are unchanged.

**Missing or forbidden prerequisites: skip their dependent analyses.** Preserve
the complete inputs, warnings and failures. Perform permitted setup separately
or use an allowed environment before retrying. A missing statistic, class 1 or
failed run must never be reported as absence or a negative biological finding.

## What is implemented

`org.jlinalg.metagenomics.HopsEvidence` implements the `def_anc` statistics and
hierarchical class matrix of the exact `postprocessing.AMPS.r` installed with
HOPS `0.35-hdfd78af_1` in the eager 2.4.4 source image. Its SHA-256 is
`7912dfcede0084134d1d677368f851a4d55dadeb0b7eaa591fcfe2e9c6cbdf62`.
The installed script matches the
[official AMPS source at commit 9d52ae0](https://github.com/keyfm/amps/blob/9d52ae03f4ca474332af3aba8a71d872d886aefb/postprocessing.AMPS.r).
Algorithm attribution belongs to Felix M. Key; cite
[Hübler, Key et al., Genome Biology 20, 280 (2019)](https://doi.org/10.1186/s13059-019-1903-0)
when using meaningful HOPS functionality.

For edit-distance counts `x0` through `x5`, a decline ratio is the sum of
downward consecutive changes divided by the sum of absolute changes. The default
four-bin ratio uses changes across `x0..x3`; the ancient ratio uses `x1..x3`.
Six-bin ratios use `x0..x5` and `x1..x5`, respectively. Ratios retain the original
three-decimal R rounding behavior. A zero denominator remains `NaN`.

The source retains node/count/ratio fields only when `x0+x1+x2+x3 > 1`.
This executable rule differs from its comment mentioning ten reads. Otherwise
those fields remain `NA`, while terminal-damage and uniqueness metadata retain
their separate source values. The damage statistic is the maximum of `C>T_1`,
`C>T_2`, `G>A_20` and `G>A_19`; uniqueness comes from the default alignment table.

| Class | Original hierarchical criterion |
| --- | --- |
| 1 | Default rounded ratio is below 0.9 or unavailable. |
| 2 | Default ratio is at least 0.9; positive terminal damage is not established. |
| 3 | Default ratio and positive damage pass; ancient rounded ratio greater than 0.8 is not established. |
| 4 | All three source criteria pass. |

These are evidence classes, **not read counts**. Class 2 in the saved JSON does
not mean two reads. Missing/NaN statistics may cause an earlier class and must
remain visibly unassessable. Even class 4 is a software screening criterion,
requiring separate study-specific review before a biological claim.

The implementation retains the original scalar character/numeric conversion.
R's 15-digit settings select fixed or scientific notation, sometimes retaining
more integer digits in fixed notation. Decimal parsing uses the original Linux
R 4.0.5 path, including 64-bit-significand intermediate arithmetic. A separate
native helper emulates those operations. Java's ordinary double parser or a
generic rounding function would change some source values. This is a restricted
source-profile conversion, not a general R parser/formatter.

`NA` becomes JSON `null`; `NaN`, `Inf` and `-Inf` remain distinct explicit string
tokens. No missing or nonfinite value is changed to zero. Original tables are
read without rewriting their numbers or identities.

## Use the Java API

```java
var limits = new HopsEvidence.Limits(
    67_108_864, 1_000_000, 100_000_000, 10_000, 100_000);
var result = HopsEvidence.inspect(
    Path.of("complete-maltextract-results"),
    List.of("Homo_sapiens", "Mammuthus_primigenius"), limits);
```

The capacities limit each complete table's bytes, rows and cells, then the total
sample and requested-node counts. Sample names come from the default
`RunSummary.txt` header. Provide **literal output node identities** explicitly,
as shown in the tables. The API does not turn spaces into underscores, strip
quotes, remove duplicate requested nodes or repair sample names.

The full matrix retains the requested node order and original sample order.
The reduced matrix retains only rows/columns with a class above 1. It applies the
source's `>`/`<` to `.` substitution to reduced display labels, refusing a resulting
identity collision. Original identities remain in the full matrix and statistics.
Missing edit-distance nodes retain `NA` with an explicit `UNASSESSABLE` warning,
even when the source class is 1.

The parser reads the complete tables, rejects duplicate rows/columns and refuses
capacity exhaustion. It accepts the tested original unquoted table format.
Quoted/ambiguous identities, numeric tokens longer than 1,024 characters and
lines longer than 1 MiB refuse assessment. Edit counts must be nonnegative
integers with complete relevant sums/difference sums within `2^53-1`; unsupported
numeric populations refuse assessment rather than being truncated or repaired.

## Build and run the development CLI

Java 17 or newer is required. The native component needs no installed R, HOPS,
MALT, Conda or container. The Windows development builder uses the configured
JDK 21/25 locations and pinned public development dependency JAR. Configure those
allowed build prerequisites first if unavailable, and choose a new directory:

```powershell
python tools/literature/build_hops_evidence.py --compiler 21 --out build/literature-review/nfcore-runtime/my-hops-build-1
java -jar build/literature-review/nfcore-runtime/my-hops-build-1/hops-evidence.jar --results complete-maltextract-results --nodes-file literal-nodes.txt --out new-hops-statistics
```

`literal-nodes.txt` contains one exact output name per line. `--results` is the
directory containing `default/` and `ancient/`, not their parent pipeline output
directory. The complete edit-distance, damage-mismatch and default alignment
tables must be present for every sample. Run `--help` for optional capacities.

Success writes `statistics.json`, `WARNINGS.md` and `COMPONENT_COMPLETED.json`.
Exit 2 means **no result admitted**. An owned new output directory receives
`FAILURE.json` and required actions in `WARNINGS.md`. Existing output directories
are preserved. Resolve the named issue, adjust permitted complete-population
capacities/heap when needed, and retry a new directory.

## Validation and retained failures

The complete original eager screening workflow produced six sample/node pairs.
All 55 HOPS output files and the original target list were exported from verified
immutable objects. Native statistics matched the saved original RData values and
full matrix by literal sample/node keys, and matched the reduced source JSON.
Reordering by keys was used only for the saved-report comparison; original
files and identities were unchanged.

Exact, unmodified original R function and score AST nodes generated reference
results for those six pairs, 2,005 edit-count/rounding cases, 890 metadata cases
and 420 classification boundaries. The metadata cases cover decimal exponents,
subnormal values, nonfinite/missing tokens and large exact integer counts.
Both compiler builds passed on Windows Java 21/25 and Linux Java 21: **17,406
numeric-pair comparisons, 2,520 classification comparisons and 42 refusals**.
Separate packaged CLI controls checked 36 original pairs, 24 refusals and six
help invocations. Two Gradle regression tests replay the public pinned fixtures:

```powershell
./gradlew.bat :test --tests org.jlinalg.cli.HopsEvidenceTest
```

**Retained native discrepancy:** the first conversion approach failed 36 metadata
comparisons. Its outputs, source and diagnosis remain retained. The corrected
fixed/scientific selection and decimal arithmetic passed the wider controls.
An initial saved-matrix verifier also rejected equal-valued integer/double R
matrices because it compared their storage types. A new verifier explicitly
compared numeric values by literal keys; the failed verifier receipt remains.

**Original `default` mode fails:** a fresh invocation of the unmodified installed
script with `-m default` exited 1 with `undefined columns selected`, before a
heatmap result. This component implements `def_anc`; it does not silently fix
the original default branch. If default-only postprocessing is required, record
that analysis as skipped or select and independently validate an appropriate
source/runtime. Do not silently switch filters to manufacture a result.

The public [validation record](hops-evidence-development/VALIDATION.json) and
named archive retain the exact sources, fixtures, receipts, warnings and failures.
The AMPS README states CC BY without a version; that statement and attribution
are preserved without inventing a license version. The installed HOPS package
ships GPLv3. Own Java code is GPL-3.0-or-later; R-derived numeric algorithms retain
R source copyright/license notices. No upstream program JARs or images are
included in the public checkpoint.

This closes selected HOPS statistics/meaning checks alongside the
[screening read audit](NFCORE_EAGER_SCREENING.md). It does not establish the
authors' historical database, alignment/LCA correctness, whole-workflow
completeness or biological validity.
