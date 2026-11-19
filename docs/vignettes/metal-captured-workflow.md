# Captured original METAL workflows

Use this workflow to run original METAL **2020-05-05** with explicit inputs,
settings and result admission. It supports inverse-variance and sample-size
schemes, optional original genomic control, sample-overlap correction, pooled
frequency fields and non-allelic gene identifiers. Java captures the original
runtime, inputs, commands, consoles and outputs; it does not replace the
original statistical engine.

**Original execution requires permitted Linux x86_64/WSL.** Read-only bundle
verification also works on Windows. Java 17+ and the current source-build JAR
are required. The previously published 0.4.0 release predates this command.
Use the standalone entry point `org.jlinalg.cli.MetalWorkflowCli`; it is not
automatically selected by the older generic `meta` command.

## First run with synthetic data

From the repository root in Windows PowerShell, build the current executable:

```powershell
.\gradlew.bat --no-daemon --no-parallel :executableJar
```

Inside Linux/WSL, change to the same repository root. Prepare the private
original runtime, then run the complete DNA and gene examples:

```bash
python3 -B tools/literature/metal_workflows/setup.py \
  --out build/metal-private-new

python3 -B examples/metal-workflow/run-example.py \
  --runtime build/metal-private-new/RUNTIME.tsv \
  --jar build/cli/omicsmatic-0.4.0.jar \
  --out build/metal-dna-example-new --mode dna

python3 -B examples/metal-workflow/run-example.py \
  --runtime build/metal-private-new/RUNTIME.tsv \
  --jar build/cli/omicsmatic-0.4.0.jar \
  --out build/metal-gene-example-new --mode gene
```

Every output path must be **new**. Preparation requires Python 3.9+, CMake,
CTest, a C++11 compiler, make and zlib development files. It downloads the
checksum-pinned official commit archive, compiles unchanged source, runs its
official tests and packages the executable privately. It does not install
system packages. Use `--archive /path/to/pinned-official.zip` without network
access, or `--cmake /permitted/path/cmake --ctest /permitted/path/ctest` for
workspace tools. Install missing prerequisites only where permitted. Otherwise
leave dependent analysis skipped and report the named missing prerequisite.

**Keep the original source, archive, executable and complete execution bundles
private.** Original source headers restrict redistribution. The helper retains
the source notice and verifies all 136 source files unchanged. This upstream
release installs inside its native build directory; the helper copies that
unchanged executable into its private runtime directory without patching source.

Both examples use synthetic data. The DNA example includes an allele swap,
pooled frequency fields and a second genomic-control pass. The gene example
uses sample-size weighting and explicitly accepts the original no-allele-column
warnings. Neither is biological validation or a reproduction of an author dataset.

## Run, verify and replay

```bash
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.MetalWorkflowCli \
  run --request prepared/REQUEST.tsv --out build/metal-analysis-new

java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.MetalWorkflowCli \
  verify --bundle build/metal-analysis-new

java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.MetalWorkflowCli \
  replay --bundle build/metal-analysis-new --out build/metal-replay-new
```

`verify` checks exact membership/hashes, captured method bytecode, runtime
identity, process records and regenerated native summary/warning tables. It
does not execute METAL. `replay` validates the parent, actually runs the captured
original workflow again and requires scientific tables and native summaries
to match byte for byte. Keep a matching frozen JAR for historical bundles.
These checks establish integrity and reproducibility of the saved controls,
not producer authentication or biological truth.

For analysis admission require **exit 0, `COMPLETED.tsv` with
`result_admitted=true`, and successful verification**. An original zero exit,
an existing marker or a prepared model alone is insufficient.

## Prepare the request and study tables

`REQUEST.tsv` is literal UTF-8 with header `key<TAB>value`. Each key occurs
exactly once. Use actual tab characters, not the two characters backslash+t.
The examples generate complete copyable request and input files under their
`supplied` directories. Relative request paths resolve against the request's
directory; study file paths resolve against the study-list directory.

| Required keys | Meaning |
|---|---|
| `executable`, `executable_sha256`, `notice`, `release` | Private original runtime, its explicit lowercase SHA-256, source notice and literal `2020-05-05`; copy these fields from the helper's `RUNTIME.tsv`. |
| `execution` | `allow` or `forbid`; forbidden execution is SKIP3. |
| `studies` | Path to the literal study list. |
| `scheme` | `STDERR` or `SAMPLESIZE`; no default scheme is inferred. |
| `allele_mode` | `paired` for single-base DNA alleles; `none` for explicitly non-allelic units such as genes. |
| `strand`, `frequency`, `overlap`, `heterogeneity` | Each explicitly `ON` or `OFF`. Overlap requires `SAMPLESIZE`. Non-allelic mode requires strand/frequency `OFF`. |
| `z_cutoff` | Positive original overlap cutoff when enabled; otherwise literal `-`. |
| `output_probability` | `ordinary` or `log10`, referring to original output. This is not log-P input support. |
| `post_genomic_control` | `OFF`, `ON` or a positive numeric lambda for a second original pass. |
| `warning_policy` | `forbid` or `allow-recorded`; neither permits lost input contributions. |
| `hypothesis` | Caller declaration of the common hypothesis, units, effect orientation and effective-N interpretation. |
| `max_studies`, `max_rows` | Whole-population study and total input-row limits. Rows are summed across all tables. Long row budgets are accepted; no large-scale performance claim is implied. |
| `max_metadata_bytes`, `max_bytes`, `max_files` | Estimated marker-memory budget, total workspace-byte budget and workspace-entry budget. |
| `max_line_bytes`, `wall_ms` | Literal line-byte limit and per-original-process wall time. |

The study list has exact header `study<TAB>file<TAB>genomic_control`. Use unique
study IDs, distinct source files, and `OFF`, `ON` or an explicit positive lambda
for each study's genomic control. Automatic GC is appropriate only for the
intended genome-wide input context; the adapter does not infer that scientific
condition or silently add a correction. Already adjusted inputs can use `OFF`.

Every summary has this exact tab-separated header:

```text
MARKER	ALLELE1	ALLELE2	EFFECT	PVALUE	N	STDERR	FREQ	STRAND
```

For `STDERR`, provide finite effect, positive finite SE, positive declared N
and literal `-` in unused `PVALUE`. For `SAMPLESIZE`, provide two-sided P in
`(0,1]`, an explicit `+`/`-` or finite signed effect, positive N and `-` in
unused `STDERR`. Zero effect is accepted only with P=1. Input P=0 or a finite
Z whose P cannot be represented is not silently converted or floored; use the
separate native weighted-Z API explicitly when its independent-cohort scope
matches the intended analysis.

Paired alleles must be two distinct uppercase A/C/G/T bases. Non-allelic mode
requires `-` in both allele fields; no dummy SNP alleles are manufactured.
When frequency tracking is enabled, provide effect-allele frequency in `[0,1]`;
otherwise use `-`. Enabled strand needs `+` or `-`; disabled strand needs the
literal `+` wire-format declaration. For non-allelic units that is an unused
placeholder, not an inferred biological gene strand. Palindromic SNPs still
require trustworthy common-strand information from the caller.

Invalid rows, duplicates, incompatible allele pairs, changing sources and
nonfinite/unrepresentable totals fail the complete request. They are not
omitted. Genuine between-study variant absence is retained as explicit source
direction-vector missingness and checked against the captured marker union.

## Read results without losing source conventions

Raw tables and `.info` files remain under `stages/primary` and, when requested,
`stages/post-gc`. Both original consoles and process records are retained.
`native/summary.tsv` keeps final effect/SE or Z/probability alongside **primary**
heterogeneity and primary frequency fields. `native/WARNINGS.tsv` retains
source warning text with stage and line pointers.

`REQUEST-ORIGINAL.tsv` and `STUDIES-ORIGINAL.tsv` preserve exact original
declarations alongside the canonical captured request/list. Changes between
parsing and capture are refused; verification checks their semantic agreement
without needing the original external file locations.

The post-GC pass reloads original printed results. It runs without a new
heterogeneity test on the single aggregate: the original first-stage Q, degrees
of freedom and heterogeneity probability stay separate. First-stage frequency
fields also stay separate from a second pass's artificial single-input
dispersion. Twelve-decimal source effect/SE precision is explicit; a printed
nonpositive SE or an unusable printed sample-size P/weight handoff fails.

**Declared N, source Weight and overlap-adjusted N are different fields.**
Original custom `DeclaredN` is printed with six significant digits, audited
against the captured input sum at that precision. In the overlap control,
declared N is 20,000, original Weight is 23,317 and adjusted N is 18,341.5.
The workflow preserves all three; it does not relabel one as another.

**Original overlap/frequency behavior can look unconventional.** With every
input frequency equal to 0.3, the original overlap control prints `Freq1=0.2573`
and `MinFreq=MaxFreq=0.3000`, using its adjusted Weight in this calculation.
Those values and original `FreqSE` are retained literally. They are not clipped
into the min/max range or independently certified as frequency uncertainty.

Original log output is base 10. Singleton and very-small-Q heterogeneity use
the original source conventions, including P=1. The independent native Java
weighted-Z API instead uses natural logs and null singleton inference; do not
mix their conventions. No workflow silently switches engines when the original
runtime is unavailable.

## Warnings, errors and recovery

**SKIP3: dependent analysis skipped.** Supply the named permitted runtime,
edition, platform, loader/library or Java prerequisite, or leave the analysis
skipped. The 2011 editions named by several papers are not replaced with this
2020 profile. Runtime preparation offers explicit prerequisite actions and
never attempts a system-wide install.

**FAIL2: no complete result.** Read `FAILURE.txt`, warnings and both original
consoles. Retain inputs and partials, correct the identified cause and choose
new output. `allow-recorded` preserves source warnings but does not authorize
dropped rows, inconsistent contribution counts or nonfinite results. Do not
repair NaN/Inf, guess directions, change identifiers or insert P floors to make
a result pass. Host/JVM failure can prevent a final receipt; an incomplete
directory is not an admitted result.

Memory is estimated as `8192 + sum(1024 + 4*marker_ID_characters +
8*ceil(studies/64))` bytes over distinct markers. This is not a heap guarantee.
Supply adequate JVM heap and explicit limits for the entire intended analysis.
Stat-only process budget monitoring can overshoot between polls and is not an
operating-system disk quota. No capacity check silently truncates data.

Read the [qualification](../literature/metal-workflow-development/README.md),
[warnings](../literature/metal-workflow-development/WARNINGS.md) and
[paper-role review preview](../literature/metal-workflow-development/ROLE-REVIEW-PREVIEW.json).
Canonical METAL catalog admission is a separate pending step. These controls
do not claim whole-paper or historical-edition reproduction.
