# Hail workflows

This development interface runs the unchanged original Hail package from Java.
It supports Hail 0.2.139 and 0.2.130 on Linux x86_64, with separately prepared
Python/Spark environments and Java 11. The canonical 0.3.7 application JAR has
not been replaced. Use a build containing `HailWorkflowCli` and
`HailWorkflowSetupCli`; the exact development artifact and SHA-256 are recorded
in [`hail-workflow-development/VALIDATION.json`](hail-workflow-development/VALIDATION.json).

The workflow proof and the public tool catalog record their separate admission
states. Source-bound roles for eight papers distinguish QC, liftover, MatrixTable
resource reuse and Batch orchestration. Neither a runtime health check nor a local
Batch example reproduces a paper or establishes cloud/cohort scale. Releases
0.2.78 and 0.2.116 mentioned in papers have not been substituted with a claim of
exact historical compatibility.

## Warnings and result admission

| Exit | Meaning | Required action |
| --- | --- | --- |
| 0 | The complete requested workflow passed its source, input, native readback and output checks. | Also require `analysis-status.json` to contain `result_admitted: true`; retain `manifest.json` and read `WARNINGS.md`. |
| 2 / FAIL2 | The complete requested batch failed. Earlier successful cases remain provisional. | Keep original inputs, all provisional native files, logs and receipts. Correct the named cause and retry into a **new** output directory. Do not use a partial batch. |
| 3 / SKIP3 | A required permitted runtime, executable or supported backend is unavailable. | Skip the dependent analysis. Prepare the exact permitted software separately, provide an allowed existing runtime, or keep it skipped. |

**Analysis installs nothing.** The setup program is a separate operation.
If installation or downloading is forbidden, do not enable the corresponding
setup flag. `CAPTURE` reads an already installed permitted environment.
`INSTALL` requires a new prefix and `--allow-install`; a missing archive also
requires `--allow-download`. Failed attempts and incomplete prefixes are retained.

**The Batch Service backend is unconfigured and unvalidated in this profile.**
A request that requires `SERVICE` skips before data capture or worker execution.
No cloud credentials are read and no cloud job is submitted. The supplied
`LOCAL` backend is the original Hail Batch local implementation. Local checks
do not certify the paper's service, RCTD program, billing configuration or scale.

**Liftover reports coordinates and strand while retaining source alleles.**
The only allele policy is `KEEP_SOURCE_REPORT_STRAND`. A target locus is not a
claim that the source allele strings match the destination reference. Unmapped
loci remain missing with their original identifiers/loci/alleles, or fail the
whole batch when the caller selects `FAIL_ATOMIC`. There is no implicit reverse
complementation, variant normalization or reference-allele repair.

The original runtime's warnings remain verbatim in `worker/original.stderr` and
`worker/original.stdout`; warning lines are also summarized in `WARNINGS.md`.
Historical Java 21 QC controls retained an upstream unsupported-Java warning.
The workflow profiles described here use the separately validated Java 11
runtime; those earlier controls and warnings have not been rewritten.

## Prepare a runtime separately

Examples use Linux paths. From Windows, run the Linux commands inside the permitted
WSL distribution. Replace the paths below with existing canonical paths and choose
new output directories; the tools do not overwrite existing attempts.

First export the self-contained setup kit from a build that contains this interface:

```bash
java -cp /path/to/DEVELOPMENT.jar org.jlinalg.cli.HailWorkflowSetupCli \
  --export-runtime-setup /path/to/new-hail-setup-kit
```

The kit contains both original wheel-member profiles, MIT notices, exact dependency
archive URLs/hashes and preparation code. Runtime archives and software are not
installed by export. The setup kit's hashes detect changed exported files; they
are integrity records, not a digital signature or a whole-OS attestation.

To validate an already installed environment without installing anything:

```bash
python3 -I -B /path/to/new-hail-setup-kit/setup/prepare.py \
  --mode CAPTURE --edition 0.2.139 \
  --prefix /path/to/existing-private-hail139-env \
  --java-home /path/to/java11 \
  --out /path/to/new-hail139-binding
```

The tested profiles use Python 3.12 / Spark 3.5.3 for Hail 0.2.139, and Python 3.10 /
Spark 3.3.2 for Hail 0.2.130. Both use Java 11. `CAPTURE` checks every original
Hail/hailtop wheel member, including the backend JAR; pip's rewritten `RECORD`
file is excluded from that upstream comparison. It also records the complete
declared prefix/JDK file and symbolic-link inventories and verifies that health
checks did not change them. Linked external files are recorded; this is not a
capture of the entire operating system or arbitrary job-program dependencies.
Full inventories can take several minutes on Windows-mounted storage.

Where installation is permitted, provide a compatible installed base Python and
Java 11, then create a new private environment from the pinned archives:

```bash
python3 -I -B /path/to/new-hail-setup-kit/setup/prepare.py \
  --mode INSTALL --edition 0.2.139 \
  --base-python /path/to/python3.12 \
  --java-home /path/to/java11 \
  --prefix /path/to/NEW-private-hail139-env \
  --out /path/to/NEW-hail139-preparation \
  --archive-cache /path/to/validated-archive-cache \
  --allow-install
```

Add `--allow-download` only where downloading is permitted and a required exact
archive is absent from the supplied caches. Cache files must match the recorded
hash and length. A corrupt cache or unavailable exact archive is reported; a
different package version is not selected silently. The installer bootstraps pip
inside the new environment and does not require system `ensurepip` or system pip.
It does not install the base Python or JDK. If either is unavailable, provide an
allowed compatible runtime or keep the analysis skipped.

A usable binding must finish with exit 0 and
`COMPLETED.json` status `PASS_BOUND_ORIGINAL_HAIL_WORKFLOW_REFERENCE_ONLY`.
That receipt is the `runtime_manifest` used by the analysis request. It establishes
framework health and source/runtime identity; it does not run genomic analysis.

## Run explicit operations

Save a request like this as `request.json`. The filter below is an illustrative
literal parameter from the synthetic controls, not an inferred paper QC policy.

```json
{
  "schema_version": 1,
  "edition": "0.2.139",
  "runtime_manifest": "/path/to/hail139-binding/COMPLETED.json",
  "out": "/path/to/NEW-analysis-output",
  "limits": {
    "maximum_rows": 10000,
    "maximum_columns": 1000,
    "maximum_entries": 1000000,
    "maximum_dense_ld_cells": 1000000
  },
  "cases": [{
    "id": "qc",
    "kind": "matrix-pipeline",
    "input": {
      "kind": "vcf",
      "path": "/path/to/input.vcf.bgz",
      "reference_genome": "GRCh37",
      "force_bgz": false,
      "contig_recoding": {}
    },
    "steps": [
      {"id": "before", "operation": "sample_qc", "name": "sample_before"},
      {
        "id": "entry-mask",
        "operation": "filter_entries",
        "predicate": {
          "operator": "ge",
          "left": {"field": {"axis": "entry", "path": ["GQ"]}},
          "right": {"literal": 20}
        },
        "keep": true
      },
      {"id": "after", "operation": "sample_qc", "name": "sample_after"},
      {"id": "variants", "operation": "variant_qc", "name": "variant_qc"}
    ]
  }],
  "maximum_input_bytes": 67108864,
  "maximum_output_bytes": 536870912,
  "maximum_files": 100000,
  "timeout_seconds": 1200
}
```

Run the Java interface inside Linux:

```bash
java -cp /path/to/DEVELOPMENT.jar org.jlinalg.cli.HailWorkflowCli \
  --request /path/to/request.json
```

The Java layer captures the original request and complete local inputs, retaining
their filenames and raw bytes. It verifies the original runtime before and after
execution, rereads final native files with the original Hail implementation, and
admits the whole batch only after all checks pass. A failed later case leaves the
earlier files available for diagnosis while the batch remains unadmitted.

The timeout applies to original worker execution. Runtime verification is separate
and reads every declared runtime member. Wall/output limits are sampled; they are
not hard OS memory/CPU containment. Self-detaching job programs are outside this
process-supervision profile.

## Supported request forms

`matrix-pipeline` reads a local `vcf` or `matrix-table` directory. A MatrixTable
input can instead name an earlier producer with
`{"kind":"matrix-table","from_case":"qc"}`. Producer cases must appear first.
A pipeline with no steps reads and preserves an existing resource, including a
gene-summary MatrixTable with no genotype field. It does not refit published
association statistics.

Operations are `sample_qc`, `variant_qc`, `filter_rows`, `filter_cols`,
`filter_entries`, `annotate`, `select_rows`, `select_cols`, `select_entries`,
`select_globals`, `key_rows_by`, `key_cols_by`, `ld_prune`, `ld_matrix`, and
`hwe_normalized_pca`. Operation identifiers must be unique. The typed Java request
model rejects missing/unknown fields and executable expression strings.

Filters and annotations use explicit `entry`, `row`, `col` or `global` field paths,
literal or typed-missing values, array indexes, numeric/Boolean operators, and a
small set of original Hail functions/call methods. There is no expression `eval`.
Required biallelic/diploid and other method-specific input restrictions remain
those of the original Hail operation; unsupported inputs fail without generic
substitutes. Recompute QC explicitly after a mask if updated QC fields are wanted;
earlier annotations are not silently refreshed.

`ld_prune` requires explicit `r2`, `bp_window_size`, `memory_per_core`,
`keep_higher_maf`, and `block_size`. `ld_matrix` requires `radius` and `block_size`;
its dense diagnostic output must fit `maximum_dense_ld_cells`.
`hwe_normalized_pca` requires `k` and `compute_loadings`. These original methods
handle missing calls according to their own source algorithms when deriving LD or
PCA values. The original input calls are retained. No independent imputation policy
is added by the adapter.

`liftover` requires a local JSON array of records with `id`, `contig`, `position`
and `alleles`; a complete local chain file; explicit `source_reference: GRCh37`,
`destination_reference: GRCh38`, `min_match`, `unmapped_policy: KEEP_MISSING` or
`FAIL_ATOMIC`, and `allele_policy: KEEP_SOURCE_REPORT_STRAND`. All original records
are retained. One atomic worker cannot silently replace an already bound
source/destination chain with a different chain.

`batch-local` requires `backend: LOCAL`, explicit local file inputs and a
topologically declared job list. Each job has a unique `id`, an `argv` list,
declared `outputs`, and `depends_on`. Arguments are literals or typed bindings
`{"input":"id"}`, `{"output":"id"}`, or
`{"job":"earlier-job","output":"id"}`. The first argument is an explicit
installed executable path. Missing executables skip the dependent batch. Input
and output resource dependencies use the original Batch implementation. The
read-only original dependency graph is retained for audit. User job-program
biology and its arbitrary library dependencies are not certified by these checks.

The original LocalBackend emits an unquoted scratch-directory `cd`. This profile
therefore requires a Batch output path containing only ASCII letters/digits,
slash, underscore, dot and hyphen, and portable ASCII input basenames. It rejects
unsupported paths explicitly rather than silently renaming the caller's files.
Original identifiers inside data are never normalized or deduplicated.

## Files to retain

Keep the entire output directory. `request.original.json`, `input-capture.json`
and `effective-request.json` document the original and bound request. Each case
contains original native `.mt`, `.ht` or `.bm` files and complete typed diagnostic
snapshots. Missing values, NaN and signed infinity have distinct representations;
native Hail schemas accompany the snapshots. Matrix snapshots retain original
column order and distinguish missing calls from filtered entries.

`worker/original.process.json` and `worker/java-process.json` record owned cleanup.
`manifest.json` binds all retained regular files and symbolic-link metadata.
`analysis-status.json` is the final admission marker. Direct output-directory
creation preserves original Batch scratch links; an existing output is never
overwritten or relocated. File hashes establish integrity, not biological accuracy.

The independent Java-only metric implementation remains available as
`org.jlinalg.genetics.HailSampleQc`; see
[`HAIL_DEVELOPMENT.md`](HAIL_DEVELOPMENT.md). It requires no Hail/Python/Spark
runtime and has its own separately frozen source comparisons. Its dense/streaming
input profile and limits differ from native Hail MatrixTable operations.

## Validation evidence

Run the portable evidence reader from the repository root:

```bash
python -B tools/literature/hail_workflow_evidence.py
```

It checks the complete saved source, native output families, typed snapshots and
independent QC/AF, LD, PCA, liftover and Batch controls without executing Hail.
Both original editions execute ten cases, including a genuine BGZF input, all
matrix axes, missing/NaN states, native MatrixTable reuse and a four-job dependency
graph. The final candidate also executes against a fresh private offline install.
Setup export is checked on Windows Java 21/25 and Linux Java 21; original Hail
uses the bound Java 11. Denied installation, unavailable/unsupported runtimes,
Service requests, timeout, late unmapped-locus failure and failed Batch jobs have
separate retained receipts and required actions.

Earlier local harness, compile and runtime failures remain in the proof archive.
Two successful complete original batches were followed by an independent-auditor
variable error. Their unchanged producer manifests and failed wrapper receipts
are retained alongside separate full audits with the corrected auditor. No
failed scientific batch or partial result was promoted. These bounded synthetic
checks do not establish cohort-scale performance, paper-specific policies,
cloud execution, biological calibration or a canonical application release.
