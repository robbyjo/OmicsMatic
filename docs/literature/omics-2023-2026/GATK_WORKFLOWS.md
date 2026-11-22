# GATK germline workflows and annotation filters

`gatk-tools` provides typed local HaplotypeCaller, CombineGVCFs, GenotypeGVCFs and
VariantFiltration workflows using the official GATK 4.7.0.0 local JAR. A native
Java helper generates exact-reference FASTA metadata. Inputs, output identities,
commands, versions, selected kernels and warnings are retained in each bundle.
This source work is not a release or a complete implementation of every GATK
program/published workflow.

## Installation and unavailable dependencies

Where downloads are permitted, run from the project root:

```powershell
python tools/literature/install_gatk.py --allow-download
```

The helper verifies the official 699,681,649-byte ZIP, extracts only its local
426,730,186-byte JAR into `build/omics-gatk/gatk.jar`, and checks Java loading.
It makes no OS, Conda, Python or Spark installation. Existing unmanaged or corrupt
runtime directories are preserved; choose a new `--environment` to repair them.
Without `--allow-download`, an already verified managed runtime can be checked
and reused; an unavailable runtime produces an explicit skip.

| Item | SHA-256 |
|---|---|
| Official ZIP | `d093d2693b1626361a413ca59d6d4a0bf968717f280a8fd9ce060b25eb2ed1db` |
| Local JAR extracted from that ZIP | `882e180707e0e6887885853fc486fa54098620fe9a80bdcdaaa655c7e3861365` |

The [official release](https://github.com/broadinstitute/gatk/releases/tag/4.7.0.0)
is the source; the JAR hash identifies the member inside the verified archive.
Use Java 17+ as specified upstream. The adapter accepts an explicit `--java`
executable; it validates the pinned JAR and version before analysis. Default heap
is 2048 MiB (range 256–65536) and timeout 3600 seconds (range 1–86400).

**If installation/runtime loading is forbidden or fails, skip the dependent
analysis.** The CLI returns **3**, retains `skipped-dependency-unavailable`, names
the exact error and gives recovery steps in `WARNINGS.md` and the console. Have an
administrator provide the pinned JAR and Java runtime, or use an allowed private
installation/cache path; then rerun to a new output directory. Installation and
version probing are separate from scientific output validation.

## Prepare the exact reference

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar gatk-tools `
  --method reference --reference data/reference.fa --out build/reference-metadata
```

This native Java/HTSJDK step needs no GATK runtime. It indexes actual LF/CRLF byte
geometry and writes `reference.fa.fai` plus `reference.dict` in the output bundle.
The dictionary contains ordered contig names/lengths; it does not invent MD5 or
assembly identifiers. The source FASTA and existing adjacent sidecars are preserved.

GATK calling requires verified sidecars beside that exact FASTA. Preserve/reconcile
existing files before placing the new metadata; do not overwrite them blindly:

```powershell
$gatkSidecars = @(
  @{ Source='build/reference-metadata/reference.fa.fai'; Target='data/reference.fa.fai' },
  @{ Source='build/reference-metadata/reference.dict'; Target='data/reference.dict' }
)
foreach ($gatkSidecar in $gatkSidecars) {
  if (Test-Path -LiteralPath $gatkSidecar.Target) { throw "Preserve and reconcile existing sidecar: $($gatkSidecar.Target)" }
}
foreach ($gatkSidecar in $gatkSidecars) {
  Copy-Item -LiteralPath $gatkSidecar.Source -Destination $gatkSidecar.Target
}
```

The adapter checks the actual FASTA index offsets/wrapping and dictionary
names/lengths/order before calling. Use uncompressed FASTA and local coordinate-sorted
indexed BAM. Every read needs a valid read group, and all groups must identify
exactly one sample. Input/reference dictionaries must agree. Sources and resolved
indexes are hashed; stable input hashes are checked after execution. Remote input
and CRAM are not exposed in this workflow.

## Single-sample calling and gVCFs

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar gatk-tools `
  --method haplotype-call --gatk build/omics-gatk/gatk.jar `
  --reference data/reference.fa --input data/sample.bam --ploidy 2 `
  --emit gvcf --out build/sample-gvcf
```

Ploidy is required, uniform across the selected input, and bounded to 1–64. Known
haploid, diploid and tetraploid fixtures test allele dosage. Do not infer sex,
mitochondrial heteroplasmy, pooled-ploidy or mosaic models from sample labels.
Default `--emit vcf` produces ordinary single-sample calls; `--emit gvcf` emits
`variants.g.vcf` with reference blocks and `<NON_REF>`.

**GVCF IS AN INTERMEDIATE.** The CLI prominently warns that reference blocks and
symbolic alleles are not final variant calls for association/interpretation.
Combine/genotype with the intended cohort/model. GVCF emission uses upstream's
reference-confidence behavior, including its effective call threshold; it does
not turn reference blocks into validated alternate calls.

`--min-baseq` defaults to 10 and `--call-confidence` to 30. Other upstream caller
defaults remain in the pinned tool and its full command/logs. The adapter selects
Java `LOGLESS_CACHING` PairHMM, `JAVA` Smith-Waterman and explicit JDK compression
options. It uses one compute thread and does not silently select AVX/GPU/Spark.
Whole-genome performance and alternative kernel parity are separate checks.

Optional `--region chr1:150-500` uses one-based inclusive coordinates checked
against the reference. The adapter writes a typed Picard interval list before
calling GATK. This avoids GATK's Windows parser treating a colon-bearing region
as an invalid filesystem path. The first failed interval attempt is retained in
validation history rather than relabeled successful.

## Combine and genotype the intended cohort

Create `gvcfs.txt` with one local gVCF path per line, relative to the list's parent
when not absolute. At most 10,000 inputs/1 MiB are allowed. Paths must be unique
and sample IDs disjoint; duplicate samples fail explicitly.

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar gatk-tools `
  --method combine-gvcfs --gatk build/omics-gatk/gatk.jar `
  --reference data/reference.fa --inputs-list gvcfs.txt --out build/combined-gvcf

java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar gatk-tools `
  --method genotype-gvcfs --gatk build/omics-gatk/gatk.jar `
  --reference data/reference.fa --input build/combined-gvcf/combined.g.vcf `
  --ploidy 2 --out build/cohort-calls
```

Combination still emits an **intermediate combined gVCF**, with a warning.
GenotypeGVCFs requires actual gVCF `<NON_REF>`/sample content, not an ordinary
variant-only VCF. Input genotype ploidy must match the explicit uniform model.
Sample identities, output VCF records and emitted `.idx` files are checked.
The output records actual sample order; no arbitrary input-list order is promised.
GenomicsDB, mixed-ploidy cohort orchestration and incremental database updates
are separate work.

## Study-specific SNP/indel annotation filters

```powershell
java -jar build/literature-review/validated-cli/jlinalg-0.3.7.jar gatk-tools `
  --method hard-filter --gatk build/omics-gatk/gatk.jar `
  --input build/cohort-calls/variants.vcf --missing-policy fail `
  --out build/annotated-filters
```

This invokes actual VariantFiltration with separate SNP/indel expressions.
Defaults follow the reported P248 annotation thresholds; they are study settings,
not a general recommendation for every assay/species:

| Type | Typed options and default failing comparisons |
|---|---|
| SNP | `--snp-qd-min 2` (QD<2), `--snp-fs-max 60` (FS>60), `--snp-mq-min 40` (MQ<40), `--snp-mq-rank-min -12.5`, `--snp-read-pos-min -8` |
| Indel | `--indel-qd-min 2`, `--indel-fs-max 200`, `--indel-read-pos-min -20` |

It adds `JL_SNP_HARD_FILTER`/`JL_INDEL_HARD_FILTER`, retaining sites, alleles,
genotypes and existing filter labels. Fingerprints independently check that the
operation preserves record content outside FILTER. It does not remove failed
records, exclude non-biallelic variants, apply depth/population filters or infer
genotype-level filtering. Those reported P248 stages remain separate work.

**CHOOSE MISSING-ANNOTATION BEHAVIOR EXPLICITLY.** Required `--missing-policy pass`
or `fail` controls GATK's `--missing-values-evaluate-as-failing` expression
evaluation. Compound JEXL expressions and short-circuit evaluation retain upstream
semantics. Missing rank-sum/other annotations are counted and promoted to a warning;
absence of a filter label does not prove every metric was measured and passed.
Boundary, missing-value and previous-filter fixtures test both policies.

## Warnings, retained errors and evidence

Success with findings returns **0** and `completed-with-warnings`; execution/input
failure returns **2**, quarantines partials and publishes no successful results.
Dependency failure returns **3**. `WARNINGS.md`, console notices, `analysis-status.json`,
`session.yaml` and native logs give the cause, affected operation and recovery.
Existing output destinations are never overwritten. Empty final calls warn and
require checking coverage/reference/interval/model choices before interpreting absence.

**Observed Windows probe warning:** GATK 4.7.0.0 can log
`Failed to delete temp file for testing temp dir` with a null-path stack trace.
The upstream executable-temp probe skips file creation on non-POSIX filesystems,
then attempts cleanup; the diagnostic is retained. Successful Java-profile truth
fixtures completed despite this warning. Check the actual exit/status and output
validation; a nonzero analysis exit remains a failure requiring correction.
[Upstream probe source](https://github.com/broadinstitute/gatk/blob/4.7.0.0/src/main/java/org/broadinstitute/hellbender/cmdline/CommandLineProgram.java)
explains this platform behavior. Other warnings retain their own meaning.

Independent fixtures verify known heterozygous/homozygous SNPs and a 3-bp deletion,
joint sample identities/genotypes, haploid interval selection, tetraploid dosage,
filter thresholds/missing policies, duplicate samples and stale reference indexes.
`verify_gatk_workflows.py` retains actual packaged outputs. The Cutadapt/MultiQC
warning correction is separately checked by `verify_qc_warnings.py`.

P025's GATK 1.3-14 indel realignment, P042's Mutect2 somatic workflow and proprietary
CancerVision orchestration remain missing. P027 does not identify its GATK
genotyping program or pooled ploidy. P041's old haploid mitochondrial workflow,
P168's old exome/ALT/contig-QC pipeline and P248's reported `4.1.5.0135` version
need separate reconciliation. Modern germline validation does not establish
those historical models or complete published pipelines.
