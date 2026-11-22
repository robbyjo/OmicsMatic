# Original SKAT prepared-matrix examples

These inputs are wholly artificial: 120 samples, eight declared variants and
three explicit sets. They contain no participant data. The sets deliberately
include an ordinary gene, a single informative variant and a monomorphic variant.
They demonstrate original-source behavior, not a biological association result.

Use the same executable JAR that produced the capture when verifying it. Source
and Java producer fingerprints are checked; a changed producer is refused rather
than made to pass by replacing old hashes. Keep that JAR and its qualification
record with your private evidence.

## Prerequisites and editable paths

Every RUN template needs the exact permitted **R4.6.1 x86_64** runtime and a private
library containing **SKAT2.2.5, SPAtest3.1.2, RSpectra0.16-2, Rcpp1.1.2 and
RcppEigen0.3.4.0.2**. Matrix1.7-5 and stats/methods/utils/tools4.6.1 must also be
available in the declared profile. Canonical R packageVersion uses dots where
DESCRIPTION versions may contain hyphens; the workflow retains both.

Edit `rscript` and `r_libraries` in a RUN JSON to your actual absolute executable
and library paths. The supplied library path `D:/PRIVATE/skat-library` is an
example, not an installation. Rscript is explicitly bound; it is not searched on
PATH. JSON backslashes must be escaped; forward slashes are convenient on Windows.

Download/install in a private permitted location. Use exact official CRAN source
archives or exact permitted binaries, preserve licenses, source/archive hashes
and build metadata, and qualify NEW output after changing dependencies. Do not
install an adjacent version and relabel it. Windows source builds require an
appropriate R build toolchain. Some tested private binaries were built under
R4.6.2 and loaded by R4.6.1; the saved warnings identify them explicitly. This is
not a claim of one uniform compiler/R build.

If installation is prohibited, retain **SKIP3**, skip the dependent analysis and
provide the exact permitted executable/library named in WARNINGS.md. Runtime
packages and JARs are not included in these example files.

## Run, verify and replay

From the project root, with your actual JAR path:

```powershell
java -jar PATH/omicsmatic-0.4.0.jar skat-tools --help
java -jar PATH/omicsmatic-0.4.0.jar skat-tools --request examples/skat225/continuous-skat.json
java -jar PATH/omicsmatic-0.4.0.jar skat-tools --verify build/skat225-demo/continuous-skat
java -jar PATH/omicsmatic-0.4.0.jar skat-tools --request examples/skat225/replay.json
```

`continuous-skato.json`, `binary-skat.json` and `binary-skato.json` exercise the
other outcome/test combinations. Input/output paths are relative to the JSON
file, not the shell directory. Outputs go under `build/skat225-demo`. Every output
must be NEW. Preserve an existing attempt and change `out` before retrying.

REPLAY performs fresh original R calls with the parent's captured inputs and
scientific options, then checks the parent again. Its output must be physically
outside the parent, including through links/junctions. Across machines, explicitly
add replacement `rscript` and `r_libraries` fields to the REPLAY control; otherwise
the saved paths are used. A failed/skipped parent needs a corrected NEW RUN,
rather than being replayed as a completed analysis.

## Read the result and warnings

**Only `admitted/p-values.tsv` is the final export**, and only when
`analysis-status.json` says `results_admitted=true` and the complete capture
verifies. `results/p-values.tsv` is the original candidate and can remain as raw
evidence after a failure. It is not a substitute for the admission status.

Read WARNINGS.md first. It gives the status, recovery steps, original conditions,
package build differences and verification limits. Full original null/result RDS,
RNG states, exclusion maps, processed genotypes and raw conditions stay captured.
Offline VERIFY checks integrity and table relationships; it does not deserialize
RDS, independently refit the null/association model, authenticate coordinated
replacements or certify unreported upstream numerical convergence.

The examples explicitly allow a source adaptive-test substitution for the
single-variant set and an untested monomorphic set. Both policies default to
false in ordinary requests. Untested sets retain source p=1 but export **NA**.
Choose your own policies explicitly. A numeric zero is refused by default;
retaining a source-reported bound exports NA, not an exact zero. Any failed set
or unaccepted policy outcome withholds the batch. An explicitly accepted untested
set can remain NA in an otherwise admitted batch; its own result_admitted stays
false because it has no tested p-value.

**SKIP3** means the dependent analysis did not run: supply the named exact
permitted runtime, package or input, or retain the skip. **FAIL2** means no
complete result is admitted: preserve everything, fix the stated input, numerical,
capacity, process or integrity cause, and retry NEW output. Never silently repair
IDs, NaN/Inf, endpoints or scientific options. Verification can confirm the
integrity of a retained failed capture without making its result admissible.

## Input and original-source details

Literal UTF-8 TSVs have no quoting, BOM or automatic ID matching. Sample order
must agree exactly. Missing phenotype/covariates require explicit complete-case
policy. Genotype NA and source missing code 9 are handled by the declared
imputation choices. Set membership names explicit variant columns. Supplied
weights must name every genotype variant in the same order and replace, rather
than supplement, beta weights.

Source missingness and MAF cutoff comparisons are strict; source MAF filtering
precedes imputation/flipping. The actual default optimal.adj rho grid contains
seven values, despite a manual listing with eight. Dosage forces fixed imputation
in upstream source. Small binary adjusted nulls use source kurtosis resampling;
the explicit RNG seed and state are retained. Some upstream integration fallbacks
have no returned indicator: `allow_reported_fallback` concerns reported evidence
only. Do not infer omitted paper options from these examples.

Dense nonlinear kernels and resampling need explicit appropriate capacities.
Limits include captured inputs, source and outputs; they are logical/monitored,
not OS job containment or a hard aggregate RAM quota. For larger real data,
choose scientifically intended limits and adequate Java/R memory before running.
This prepared autosomal workflow does not implement SSD, sex chromosomes,
SAIGE/REGENIE/REMETA/SEAK/COAST pipelines or whole-paper reproduction.
