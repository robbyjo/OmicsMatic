# Prepared FastQTL workflow

**Status: source implementation; the v0.4.0 release asset has not been replaced.**
Build the current project executable JAR to obtain `fastqtl-tools`.
This command runs **Ubuntu `2.184+v7+dfsg-4build3`, identifying itself as
`v2.184_gtex`**. The distribution includes an HTSlib patch. It does not provide
historical FastQTL 2.165, conditional mapping, FDR/q-values, PEER estimation or
a complete paper analysis. The five corpus uses remain pending catalog admission.

**Warnings and failures require action.** Exit **3** means the dependent analysis
was **skipped** because a dependency or capability was unavailable. Install or
restore the exact named prerequisite using a method allowed by your environment;
if installation is prohibited, keep the analysis skipped. Exit **2** means there
is **no complete admitted result**: read `FAILURE.md`, `analysis-status.json`,
`result-audit.tsv` when present, and `worker/` logs; preserve that directory, fix
the stated cause and retry a **new output path**. Do not use a partial file as a
completed result. The analysis command never installs or downloads software.

## Install a private runtime

Use Linux x86_64; Windows users can run Linux Java and Python in WSL. The tested
environment uses Ubuntu Noble-compatible libraries. Native Windows execution
of the upstream ELF program is skipped with exit 3.

The separate installer downloads the pinned package from the official Ubuntu
archive unless `--deb` supplies an existing copy. It extracts into a **new private
directory**, copies the resolved shared libraries and records their identities.
It does not run a package manager or modify shared libraries. `dpkg-deb`, `ldd`
and compatible dependencies must already be available.

```sh
mkdir -p build
python3 tools/literature/install_fastqtl_runtime.py \
  --out "$PWD/build/fastqtl-runtime"
```

If dependencies are missing, `ldd.stdout` names their exact SONAMEs. The package
requires Rmath, BLAS, GSL, HTSlib and Boost 1.83, plus their system dependencies.
Obtain permitted compatible packages from your operating system provider.
You can extract dependencies privately and repeat `--library-dir` for each
directory, including Rmath and BLAS subdirectories:

```sh
python3 tools/literature/install_fastqtl_runtime.py \
  --deb /absolute/path/fastqtl_2.184+v7+dfsg-4build3_amd64.deb \
  --library-dir /absolute/path/private-dependencies/usr/lib \
  --library-dir /absolute/path/private-dependencies/usr/lib/x86_64-linux-gnu \
  --library-dir /absolute/path/private-dependencies/usr/lib/x86_64-linux-gnu/blas \
  --out "$PWD/build/fastqtl-runtime-retry"
```

Read `INSTALLATION.json` and the bundled `COPYRIGHT`. Keep runtime bundles
private unless you have separately met every component's redistribution and
source obligations. Installer success means **staged**, not scientifically tested.
The Java command checks the exact executable/copyright, all 18 declared ELF
dependencies before and after execution, and uses the private loader with its
cache disabled. Every library is fingerprinted; different library builds are
not assumed numerically equivalent. Fresh verification requires the saved
runtime identities and kernel to match.

## Prepare inputs

Supply one chromosome, with sorted **BGZF** VCF and phenotype BED files and
their matching **TBI** indexes. Ordinary gzip and CSI indexes are unsupported.
The wrapper compares full-contig and requested regional index lookups against
the sequential source records. It copies inputs without rewriting their values.

- VCF: unique explicit variant IDs, biallelic A/C/G/T SNVs, scalar finite
  `DS` in [0,2] or diploid biallelic `GT`. When both exist, the original program
  uses **DS**. Missing tokens retain upstream semantics. Indels, SVs,
  multiallelic records and ploidy conversions need another qualified profile.
- BED: `#chrom`, `start`, `end`, `id`, then unique sample IDs; tab separated.
  The original cis anchor is **zero-based BED start + 1**. Prepare TSS or peak
  centers explicitly; strand, gene annotation and center conversion are not
  inferred. Finite numbers and literal `NA` are supported.
- Optional covariates: first column ID, then sample IDs; subsequent rows contain
  unique covariate IDs and finite numeric values. Prepare numeric sex/disease
  indicators and PEER/PC values upstream. Categorical expansion and missing
  covariates are outside this profile.
- Sample ID **sets must match exactly**. Different column orders are allowed
  through the original ID mapping. No trimming, silent sample dropping or
  sample independence is inferred. Use literal ASCII with LF line endings.

Assembly labels are recorded, not checked against a genome. Confirm scientific
eligibility, preprocessing, covariates and coordinates before setting
`input_reviewed`. Related samples need an appropriate model; this adapter does
not turn FastQTL into a mixed model.

## Run a prepared analysis

Save this as `run.json` beside a `prepared/` input directory. Paths resolve
relative to the request file. Its output parent must exist; `fastqtl-result`
must be new.

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "profile": "fastqtl-2.184-v7-ubuntu-noble-prepared-v1",
  "operation": "PERMUTATION",
  "runtime_home": "build/fastqtl-runtime",
  "out": "fastqtl-result",
  "assembly": "GRCh38",
  "region": "22:20000000-21000000",
  "inputs": {
    "vcf": "prepared/chr22.vcf.gz",
    "vcf_index": "prepared/chr22.vcf.gz.tbi",
    "phenotypes": "prepared/chr22.bed.gz",
    "phenotype_index": "prepared/chr22.bed.gz.tbi",
    "covariates": "prepared/covariates.txt"
  },
  "options": {
    "seed": 701,
    "window": 1000000,
    "normal": false,
    "maf_threshold": 0.1,
    "ma_sample_threshold": 2,
    "anchor_policy": "bed-start-plus-one",
    "input_reviewed": true,
    "permutations": [1000]
  }
}
```

```sh
java -jar build/cli/omicsmatic-0.4.0.jar fastqtl-tools --request run.json
```

For `NOMINAL`, replace `"operation": "PERMUTATION"` with `"NOMINAL"` and
`"permutations": [1000]` with `"threshold": 1.0`. This threshold filters
variant–phenotype pairs; it is not a gene-level FDR threshold. Original adaptive
permutations accept `[better,max]` or `[min,better,max]`; array positions retain
the upstream meanings. `normal: true` requests upstream inverse-normal
transformation **before** covariate correction. Missing phenotype/genotype
values undergo upstream mean imputation; Java does not impute them.

FastQTL uses rounded dosages to calculate MAF/minor-allele counts in this
edition. Nominal DF uses the supplied covariate count; permutation uses the
residualizer's retained count and a fitted effective DF. These need not equal
matrix rank. Check original warnings, especially for dependent covariates.

## Inspect, verify and interpret outputs

`worker/result.txt` is the untouched upstream output. `result-audit.tsv` adds
column names and row status; `scientific-audit.json` records numeric, no-cis and
nonfinite rows. A permutation no-cis row is an explicit **no result**. An empty
nominal output is allowed. Omitted pairs can include undefined P values; absence
does not establish no association. A printed nonfinite statistic causes exit 2
and leaves raw output intact. Beta-adjusted and empirical P values are **not**
FDR/q-values. Apply an explicitly chosen downstream multiple-testing procedure.

```sh
java -jar build/cli/omicsmatic-0.4.0.jar fastqtl-tools --inspect fastqtl-result
```

Inspection checks saved closure and derives the input/output audits again. It
does not fit a model. Keep the exact producing JAR; a newer build cannot qualify
an older capture by refreshing hashes. Captures bind their original absolute
paths; inspect them in their original host/path context.

For a new upstream fit on captured inputs, save `verify.json`:

```json
{"schema_version":1,"mode":"VERIFY","analysis":"fastqtl-result","out":"fastqtl-verified"}
```

```sh
java -jar build/cli/omicsmatic-0.4.0.jar fastqtl-tools --request verify.json
```

`VERIFY` and `REPLAY` both execute the original program again, preserve the
parent and require exact scientific output/audit/runtime identity matches.
Optional `runtime_home` relocates an identical bundle. Logs, command paths and
timings are retained but excluded from the scientific byte comparison.

Resource `limits` can lower documented defaults in `FastqtlWorkflowContracts`;
unknown fields and arbitrary upstream flags are refused. File/byte/deadline
limits are sampled, not hard OS memory or process containment. Cleanup targets
only the owned worker and start-identified observed descendants. Original stdout
has a separate 64 MiB review bound; exceeding capacity leaves an unadmitted
capture for review.

See [the synthetic validation procedure](../tools/literature/fastqtl_workflow/README.md)
and [source-role audit](literature/FASTQTL_SOURCE_ROLES_2026-11-24.md).
Upstream sources: [author-maintained GTEx fork](https://github.com/francois-a/fastqtl/tree/v7),
[official Ubuntu package/source archive](https://archive.ubuntu.com/ubuntu/pool/universe/f/fastqtl/).
