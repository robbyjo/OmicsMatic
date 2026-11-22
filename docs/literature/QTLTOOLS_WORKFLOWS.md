# Versioned QTLtools workflows

`qtltools-tools` executes prepared-input cis/trans mapping, MBV, quantification
and functional-density operations with an explicit edition and engine. Native
Java supplies the selected1.3.1 fdensity operation; other operations invoke exact
upstream Linux builds. Build current source: the previously released JAR predates
this command. This is a scoped workflow, not every QTLtools mode or whole-paper
reproduction. Catalog admission and remaining source reviews are separate work.

## Warnings and what to do

**SKIP3: the dependent analysis was skipped.** Supply the named exact executable,
matching libraries, input/index or Linux x86_64/Python3.11+/WSL environment.
Download/private installation is allowed where your environment permits it. If
installation is prohibited, retain the skip and identify what must be provided.
No version, engine or method is substituted. Native fdensity needs Java only.

**FAIL2: no complete result was admitted.** Preserve the capture, including raw
inputs, indexes, partial/null outputs, logs, exit/signal and cleanup ledgers.
Correct the named input, numerical, capacity, runtime, timeout or integrity cause
and retry into NEW output. Do not repair NaN/Inf, overwrite old evidence, silently
change thresholds, or refresh old source hashes. A source parsing error can return
exit0; the adapter also requires completion, full outputs and valid invariants.

**Completed null results are explicitly labelled.** Empty cis selections,
zero-variant permutation families, no defined covered-genotype concordance, or
no quantified counts have a `null-...` result_kind and a prominent warning.
They are not positive associations, donor matches or expression evidence.
Raw zero-variant NA tails and defined source diagnostic NaNs remain unchanged.
Nonfinite fitted/test/quantification values fail admission.

**MBV1.1 truncates concordance fractions.** Its original integer division exports
only0 or1, even when some sites are consistent. The complete counters and original
exported fractions are retained. Do not interpret its exported0 as zero consistent sites
or silently replace that field with a continuous rate.1.2/1.3.1 divide in floating
point. Required coverage and genotype-quality thresholds remain declared inputs.

**Trans null-file columns differ.**1.1/1.2 read the LAST column;1.3.1 reads column
THREE. The1.2 sampled best export ends with a variant ID and fails if fed directly
back as its null file. Supply a deliberately prepared numeric null table and
retain its preparation provenance. The adapter never selects/removes columns.

**FDensity can depend on annotation order.** The original tree guard for fewer
than64 annotation rows on a chromosome can skip matches based on the first row.
The1.3.1 audit reports `source_order_guarded_qtl_rows` and prominently warns when
nonzero. Never silently sort input. Counts are per QTL with any overlap in a bin,
not annotation multiplicity or enrichment significance. See the
[native component's coordinate and capacity rules](QTLTOOLS_FDENSITY.md).

**Paper recipes stay literal.** The reported bare `--grp` is ambiguous in all
three inspected editions, while typed grouping requires none/best/pca1/mean.
Do not infer which grouping the paper intended. Cis permutations are a fixed
integer count; no adaptive scheme is inferred from a paper's wording. Unreported
editions, cohort preparation, reference assembly, lead-QTL selection, FDR/qvalue
threshold construction, PEER/PC counts and allele orientation need their own
source evidence. Do not treat a successful adapter test as biological replication.

## Quick start

Run the [native artificial example](../../examples/qtltools/README.md) first:

```powershell
java -jar build/cli/omicsmatic-0.4.0.jar qtltools-tools --help
java -jar build/cli/omicsmatic-0.4.0.jar qtltools-tools --request examples/qtltools/native-fdensity.json
java -jar build/cli/omicsmatic-0.4.0.jar qtltools-tools --verify examples/qtltools/build/native-fdensity
```

For an upstream example, copy
[`cis-permute.example.json`](../../examples/qtltools/cis-permute.example.json),
fill its input/index/runtime paths and NEW out, then use the same `--request`
command. JSON paths resolve against the request's directory. No download,
alignment, genotype calling, index creation, sample filtering or matrix repair
is performed automatically. Required inputs are copied unchanged and hashed;
the selected upstream tool performs its own declared handling under an explicit
`acknowledge_original_handling:true` acknowledgement.

## Request reference

RUN requires schema_version1, mode RUN, edition1.1/1.2/1.3.1, engine original/native,
operation, out, inputs, parameters and the acknowledgement. Original also needs
runtime; native forbids an unused runtime. Arbitrary flags and unknown fields are
refused. Native is restricted to1.3.1 FDENSITY. The following are scoped operations:

| operation | Required prepared inputs | Main parameters |
| --- | --- | --- |
| CIS_NOMINAL | genotypes, phenotypes | nominal_p, window, grouping |
| CIS_PERMUTE | genotypes, phenotypes | permutations (fixed integer>=10), window, grouping |
| CIS_CONDITIONAL | genotypes, phenotypes, thresholds | window, grouping; supplied per-phenotype/group nominal thresholds |
| TRANS_NOMINAL | genotypes, phenotypes | threshold, bins, window (cis exclusion radius) |
| TRANS_PERMUTE | genotypes, phenotypes | threshold, bins, window; one full phenotype shuffle |
| TRANS_SAMPLE | genotypes, phenotypes | sample, threshold, bins, window |
| TRANS_ADJUST | genotypes, phenotypes, adjustment | threshold, bins, window; explicit null table |
| MBV | genotypes, alignment | mapping_quality, base_quality, minimal_coverage, binomial_p, imputation_quality, imputation_probability, keep_duplicates, region |
| QUAN | gtf, alignments (array) | sample_names (array), rpkm, tpm, gene_types, mapping_quality, mismatch, mismatch_total, proper_pairs, consistency, no_merge, legacy_options, failed_qc, minimum_exon, remove_duplicates, region, filename_hash |
| FDENSITY | qtl, annotations | window (radius), bin |

Mapping may also supply covariates and explicit include/exclude files for samples,
sites, positions, phenotypes or covariates. Cis/trans accept normal and seed;
cis accepts region and chunk, trans accepts chunk except with TRANS_SAMPLE.
Grouping is none/best/pca1/mean.1.3.1 cis alone offers standard_error. Native seed
has no effect because fdensity is deterministic.

Each scalar input is an object with path and format. Optional index and
index_format must appear together. Alignment arrays contain those same objects.
Supported declarations include VCF_GZ/VCF/BCF, BED_GZ/BED, BAM/SAM, TEXT_GZ/TEXT
and GTF_GZ/GTF as appropriate to the operation. Declared plain/gzip compression
must match bytes; suffixes are preserved for upstream filename-based decoding.
Index formats are TBI, CSI or BAI as appropriate. Supply prepared valid indexes;
the original tool still determines whether a combination is executable. The
qualified artificial models use indexed BGZF VCF/phenotype BED, BAM/BAI, GTF and
text tables; declaration acceptance alone does not qualify every format recipe.

Phenotype BED has six fixed fields (chrom,start,end,phenotype ID,group ID,strand),
then literal sample columns. Covariates have a row-ID column then literal sample
columns. Genotype and phenotype headers are captured and their common sample
identities are reported. Original software retains responsibility for intersection,
missing-value treatment, covariate regression and transformations. No Java input
reordering, imputation, chromosome-name repair or statistical preprocessing occurs.

Quan1.1 accepts multiple BAMs, uses plural --samples, supports RPKM but not TPM,
and optionally maximum_read_length. Its statistics are a wide per-sample table.
Quan1.2/1.3.1 accept one alignment, use singular --sample, support TPM and offer
filename_hash source/content/none. Source hash uses the captured GTF location
and options, so a new capture/replay can have a different filename hash; content
hash uses original source behavior. Original names remain unchanged. All tables
within a capture must share one prefix. Region cannot be combined with RPKM/TPM.
Original chromosome/coordinate and read-count exports remain literal.

Default limits are timeout_seconds3600, maximum_input_bytes1GB (all files and
indexes combined), maximum_output_bytes2GB of monitored output growth,
maximum_rows10million per text file, maximum_line_bytes8MB, maximum_bins100000.
Text decoding also has a per-file decoded-byte cap. Request explicit larger
capacities if required and permitted; the adapter does not thin data or reduce
permutations to fit. The owned child has core dumps disabled to bound crash output;
previous evidence and all raw inputs,partial exports,logs and exit records remain.

## Exact runtime profiles

Original execution requires Linux x86_64 and Python3.11+ with ldd. Windows uses
an explicit installed WSL distribution and drive-backed executable/library/input
paths. Set runtime.executable, optional library_directories, and on Windows
wsl_distribution. There is no automatic provider discovery or edition fallback.

| Edition/build | Executable SHA256 |
| --- | --- |
| Official1.1 Ubuntu16.04 x86_64 | `0c5e469b42753ffe9ec2d9690aa21f626a9e5294cdf9ecbe3c024f78c72a7024` |
| Official1.2 Ubuntu16.04 x86_64 | `1350229c46349fae4337edbc7088b2a65a3f38d8e3cfc8d1daf7910faaaad0f0` |
| Ubuntu noble1.3.1+dfsg-4build3 | `246108e394f04961741c0393667e5d0b0a2f965aca2aea59ddd50c779f14f2c0` |

The [machine-readable profiles](../../src/main/resources/org/jlinalg/omics/qtltools-workflow/PROFILES.json)
pin worker bytes and required scientific libraries. All loaded library hashes
are captured before/after execution. The [runtime setup guide](QTLTOOLS_RUNTIME.md)
lists all nine exact Ubuntu packages, private extraction steps and the names
corresponding to all eight critical-library hashes. Official historical binary archives are
[1.1 Ubuntu16.04](https://qtltools.github.io/qtltools/binaries/QTLtools_1.1_Ubuntu16.04_x86_64.tar.gz)
and [1.2 Ubuntu16.04](https://qtltools.github.io/qtltools/binaries/QTLtools_1.2_Ubuntu16.04_x86_64.tar.gz).
Selected1.3.1 source is
[`a9606ee...`](https://github.com/qtltools/qtltools/tree/a9606ee736d8489e3ec54c8b8c92c32fecad3bd0).
The local approved private installation is under
`D:/projects/JLinAlg/build/literature-review/qtltools-runtime`; its transfer records
and exact library paths are in the [handover](HANDOVER_2026-11-21_QTLTOOLS.md).
These GPL source profiles do not license unrelated proprietary tools or datasets.

## Saved captures, verification and replay

Read analysis-status.json and WARNINGS.md first. The capture retains source-request,
captured/effective requests, all copied inputs/indexes, prepared header metadata,
raw outputs, output-audit, manifest and capture-files seal. Source records pin
executing implementation/helper classes and Python/profile resources. Original
workers retain exact argv, runtime/library inventories, distinct supervisor and
original PID/session/start identities, token-bound cleanup and closure receipts.
Cancellation markers forbid a delayed worker from starting an analysis after
timeout. Invalid/missing inputs and runtime failures keep diagnostic captures.

VERIFY is offline: it reparses captured inputs/outputs and checks the complete
inventory, scientific settings, source classes/resources, original completion,
edition schemas, null/undefined metrics and provenance bindings. Native1.3.1
fdensity recomputes every bin and metadata field. Original VERIFY does not refit
models or authenticate a coordinated replacement of all evidence. SHA256 is
integrity, not a signature. Use the exact original producer build to verify an
older source-bound capture; keep older producers and failed attempts unchanged.

For replay, a NEW request is:

```json
{"schema_version":1,"mode":"REPLAY","analysis":"existing-capture","out":"NEW-replay"}
```

Original replay may add a runtime object for that host; the same declared edition
and scientific parameters remain required. Runtime flavor cannot be silently
substituted. Replay copies saved inputs, reruns the actual operation, and checks
that the parent seal is unchanged. Output must be outside every retained capture.
Changed inputs/criteria require a NEW RUN, not an edited parent or a refreshed hash.
