# Original LOFTEE workflows

This development workflow runs unchanged VEP105.0 and one of two captured LOFTEE GRCh38 sources. Java validates the request, captures the complete input family, checks reference bases and indices, supervises the original processes, and audits every returned variant, allele and transcript annotation. It keeps the original VCF, sample/genotype tokens, confidence calls, filters, flags and diagnostic files. It does not choose a canonical or MANE transcript or construct a paper's variant mask.

**Require exit0 and `analysis-status.json` with `result_admitted: true`.** Always read `WARNINGS.md`, original logs, process receipts and both native ledgers before using a result. Exit3 means the dependency is unavailable and the dependent analysis was skipped. Exit2 means the whole batch is unadmitted, including earlier cases that finished. Preserve the attempt, correct the named cause and retry new output paths. Do not admit provisional worker files after a failed batch.

The [native VCF audit](LOFTEE_AUDIT_DEVELOPMENT.md) is a separate portable Java component. Original software execution here requires Linux x86_64, Java17 or newer, Python3.11 or newer for preparation, and the exact captured host libraries. The original sources use Apache2.0; their full license and notices are retained in the source profile and parent evidence. Software dependencies keep their own licenses.

## Prepare original dependencies

Run from the repository root on Linux. Installation requires permission to download software and execute it in a new private directory. The helper checks224 exact package archives by SHA256, installs them without resolving newer versions, checks820 original BioPerl source files, then exports the reviewed execution closure. Allow at least6 GiB free space. Preparation can take several minutes; installer logs and all partial downloads are retained under the chosen output.

```sh
python3 tools/literature/install_loftee_workflow_runtime.py \
  --out build/lft-my-runtime --allow-install
```

The resulting compact runtime is `build/lft-my-runtime/runtime`. A successful `COMPLETED.json` records `PASS_EXACT_ORIGINAL_DEPENDENCIES_INSTALLED_AND_COMPACT_RUNTIME_PREPARED`. Its nested runtime receipt records `PASS_EXACT_COMPACT_LOFTEE_RUNTIME_PREPARED_ONLY`. This step runs no variant analysis and downloads no human reference or annotation resources.

**If installation is forbidden or fails, keep LOFTEE-dependent analysis skipped.** Read the preparation directory's `WARNINGS.md`, `COMPLETED.json` and named download/installer logs. Correct the specific network, permission, storage, host-library or prefix problem where permitted, then retry a new directory. Host-library drift is an unsupported runtime; do not refresh hashes to accept it. The output prefix must fit the captured binary prefix fields. A short ASCII directory under `build` is required. Existing environments and shared libraries are never changed.

If exact dependencies are already available, export a new compact runtime without downloading or installing:

```sh
python3 tools/literature/setup_loftee_workflow_runtime.py \
  --environment /absolute/path/to/original-vep105-environment \
  --bioperl /absolute/path/to/original-bioperl-api \
  --out build/lft-my-export --allow-prepare
```

The exporter verifies every selected byte, mode and symlink. It allows only the exact captured text-prefix substitutions and enumerated NUL-padded binary prefix fields. Different versions, extra runtime files, changed scientific code and partial relocations skip dependent analysis. An existing installation of all224 exact locked packages can be inspected read-only with `--installed-environment /absolute/path/to/environment`; the helper prepares a new compact runtime and records `PASS_EXACT_LOCKED_ENVIRONMENT_AND_COMPACT_RUNTIME_PREPARED`. It preserves the existing environment and validates the same package/archive/file identities. Read-only package-cache and archive options for the installer are documented by `--help`; supplied archives must match the same SHA256 lock.

## Run the complete synthetic example

Use a current source build containing `org.jlinalg.cli.LofteeWorkflowCli`. From a fresh checkout, `./gradlew executableJar` creates the project executable JAR. The separate development artifact in the validation receipt can also be used locally. An older executable does not acquire this direct class automatically.

```sh
python3 tools/literature/prepare_loftee_workflow_example.py \
  --runtime-home build/lft-my-runtime/runtime \
  --out build/literature-review/loftee-runtime/my-example

java -Xmx2g -cp build/cli/omicsmatic-0.3.7.jar \
  org.jlinalg.cli.LofteeWorkflowCli \
  --request build/literature-review/loftee-runtime/my-example/request.json
```

The example uses an entirely synthetic4000-base sequence, five synthetic transcripts,15 variant rows and two sample columns. It compares the original default GERP branch with an explicitly selected percentile branch. These are software controls, not a human GRCh38 analysis or a paper reproduction. The request preparer runs no analysis. An explicit source edition can be selected with `--edition cited-fork-c8fdde00`.

Open `my-example/analysis/analysis-status.json` and `WARNINGS.md`. Results appear in `results/default-GERP` and `results/explicit-percentile` only after the entire batch passes. Each contains the unchanged `original.vcf`, `native-variants.tsv`, `native-annotations.tsv` and `native-audit.json`. The `worker` directory retains original logs, warnings and process cleanup receipts; `inputs` retains every requested input/resource/index; provenance and manifests retain settings and file identities. No biological interpretation is supplied by these synthetic controls.

## Supply study inputs

Start from the complete generated request. Replace its explicit input and resource fields, set `reference_origin` to `supplied-reference`, use matching assembly/release labels, and choose a new output directory. Labels are caller assertions. The workflow cannot independently certify their biological provenance or the transcript completeness of a supplied GTF/cache.

| Field | Required behavior |
|---|---|
| `input_vcf`, `input_compression` | Complete VCF4 with literal uppercase A/C/G/T/N alleles; explicit `none` or ordinary `gzip`. Prior CSQ, symbolic alleles and duplicate sample/INFO identifiers are refused. Preserve the original elsewhere and explicitly supply the intended unannotated input. |
| `fasta` | `file`, complete `index`, explicit `compression` (`none` or `bgzip`), assembly/release labels, and `gzi` for BGZF. Every input REF must match the exact supplied indexed sequence. No allele correction, build conversion or chromosome aliasing is inferred. |
| `transcripts` | BGZF GTF with its TBI index and labels, or a reserved cache request that currently returns **SKIP3** before deserialization or annotation because cache execution is unvalidated in this development profile. Its typed fields are `kind: vep-cache`, `directory`, labels, `cache_version: 105`, `species: homo_sapiens`, `cache_trust: trusted-original-ensembl-cache`. Use the validated supplied GTF/FASTA profile where appropriate, or keep cache-dependent annotation skipped. |
| `gerp` | `kind: bigwig` with file/labels when any case uses GERP, otherwise exactly `{"kind":"not-applied"}`. An unused supplied resource is refused. |
| `ancestor` | Exactly `{"kind":"source-disabled"}`, or `kind: fasta` plus the same explicit FASTA/index/compression/label fields. At least one case must apply any supplied resource. The original ancestral check skips indels and non-biallelic-SNP allele strings. |
| `conservation` | Exactly `{"kind":"source-disabled"}`, or `kind: sqlite` with file and labels. The original SQLite schema and query are required. Use `threads: 1`; the original database handle is not safely shared by forked workers. Missing tables remain original errors. |
| `runtime`, `edition` | Explicit prepared `environment`, `bioperl`, `source` directories and `grch38-1.0.4` or `cited-fork-c8fdde00`. No source/version substitution or automatic installation during analysis. |
| `analyses` | One to eight unique case IDs, each with `settings`, JSON booleans `ancestral_check` and `conservation_check`, and `complete_cds_check: source-disabled`. The original complete-CDS check needs database access and is outside this closed local-resource profile. |

Original LOFTEE's shell calls require an ASCII output-parent path using letters, digits, slash, dot, underscore and hyphen, and a portable exact chromosome identifier. Unsafe paths/identifiers are refused without renaming. Input files are copied to owned paths while biological identifiers and rows remain unchanged.

## Original settings and limits

Empty `settings` selects recorded original defaults: `min_intron_size: 15`, `fast_length_calculation: fast`, `use_gerp_end_trunc: true`, `gerp_end_trunc_cutoff: -58`, `filter_position: 0.05`, `run_splice_predictions: false`, `apply_all: false`. The effective request lists every value, including inactive source settings. For percentile filtering explicitly set `use_gerp_end_trunc: false`; its GERP resource is unnecessary only when every case uses percentile filtering. Never pass the original string `"false"` as a substitute for a JSON boolean.

Extended splice predictions are enabled explicitly with `run_splice_predictions: true`. Their defaults are `get_splice_features: true`, `weak_donor_cutoff: -4`, donor/acceptor MES cutoffs6/7, donor/acceptor disruption cutoffs0.98/0.99, scan distance15, rescue cutoffs8.5, `exonic_denovo_only: true`, maximum de novo donor distance200, de novo donor cutoff0.995 and SRE flank size100. Explicit extended settings are refused while that branch is disabled. `fast_length_calculation` accepts `fast` or `slow`. No setting is silently enabled to match a paper.

Set `threads` explicitly if needed (1–64; default1) and `buffer_size` (default5000). The default per-process `timeout_millis` is1800000. The default capacities are8 GiB input,64 GiB combined captured resources,32 GiB decoded reference/text units,10 million variants,100 million annotations,2,097,152 UTF-16 characters per line,16 GiB combined worker/published outputs and100000 output files. Their explicit request keys begin with `maximum_`; inspect the effective request for exact values. Capacity/timeout failure stops only owned processes and admits no batch result. Runtime/source checks read complete files before and after execution and can take several minutes on mounted filesystems; timeout applies to each original process.

Original extended splice `LoF_info` contains features emitted by Perl hash iteration. Separate runs can return the same literal tokens in different orders. Raw VCFs and ledgers keep that order. Independent validation allows only this documented token-order difference, retains duplicate-token counts and uses no numerical tolerance; every other field and annotation order must agree. NA, NaN and Inf remain distinct literal values. HC flags remain flags; empty confidence on a non-target consequence is not a benign call.

Paper-specific VEP releases, MANE/canonical transcript selection, confidence masks, other pathogenicity predictors, frequency filters and downstream burden tests remain separate decisions. This development profile does not establish historical paper binary identity, biological calibration or whole-paper reproduction.

The captured Perl5.32.1 include paths cannot load the eight original Perl5.22 ViennaRNA files. These unused files are explicitly excluded from the execution closure, with their original identities and the earlier failed installation/export retained. All224 locked package archives and all remaining runtime bytes keep their exact identities. No LOFTEE source, variant input or biological output was changed by this closure refinement.
