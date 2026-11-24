# Exact nf-core source setup

**Source setup is available for seven editions. It does not install process
dependencies or run a pipeline. The nf-core catalog family remains pending.**
The existing [ATAC-seq 1.2.1 execution adapter](NFCORE_ATACSEQ121.md), `ENGINE`,
`CAPTURED` and portable ATAC audit commands remain available through their
separate, explicit routes. This source setup does not widen their qualified
scope. No new tool-family or reviewed-use credit is claimed here.

The source audit rechecks every XML in the frozen 250-paper corpus against its
saved hash and DOI. It binds six original `nfcore` observations to eleven
paragraphs and ten pipeline branches. The original near-name version fields
remain null. Contextual versions below do not overwrite those observations.

| Paper | Reported workflow and use | Source distinction |
| --- | --- | --- |
| [P002](https://doi.org/10.1038/s41586-024-08515-5) | eager 2.4.4 for ancient pathogen screening, processing and human-read removal; eager 2.3.4 for human ancestry read processing | The separate capture-mapping paragraph reports no edition. Preserve the literal `samtools c1.12`, `dedup v0.21.8` and concatenated citations. Custom pseudo-haploid genotyping is separate. |
| [P027](https://doi.org/10.1038/s41586-025-09853-8) | rnasplice 1.0.4 with Nextflow 24.04.4, supporting QC, trimming, STAR alignment and downstream rMATS | The paper's event thresholds and visualization require their separately described steps. |
| [P032](https://doi.org/10.1038/s41586-024-08314-y) | chipseq 2.0.0 for default read processing, hg38/mm10 alignment and filtering | Mouse spike normalization, donor/control selection and specified MACS2 calls are separate. |
| [P050](https://doi.org/10.1038/s41586-023-06821-y) | Unversioned ChIP-seq workflow for intersample comparison/HOMER, plus an unversioned workflow literally named `ATAQ-SEQ` | Mapping `ATAQ-SEQ` to atacseq is an explicit inference. Neither edition is known. Preserve `bwa2` and the stated Picard operation without silently repairing them. |
| [P169](https://doi.org/10.1038/s41588-024-01900-w) | chipseq 1.2.1 for ChIPmentation; atacseq 1.2.1 for Omni-ATAC | ChromHMM 1.24/15-state analysis is downstream. Neither Nextflow edition is reported. |
| [P228](https://doi.org/10.1016/j.ajhg.2025.07.020) | hic 2.1.0 with Arima digestion, HiCExplorer TAD calling, mapQ 10 and GRCh38 | HiC-Pro attribution and the Unicode dash in the Bowtie2 text remain source uncertainties. Matrix normalization, differential tests and visualization are separately described. |

The complete source records and paragraph hashes are in
[ROLES.json](nfcore-source-audit/ROLES.json) and
[CENSUS.json](nfcore-source-audit/CENSUS.json). They document source review;
they are not catalog admission or paper reproduction.

## Install source only

Build the project as described in the [project README](../../README.md). Use the
resulting JAR as `OMICS_JAR`; older released JARs may lack these source commands.
List the exact IDs and export the setup helper to a new directory:

```sh
java -jar "$OMICS_JAR" nfcore-tools --source-profiles
java -jar "$OMICS_JAR" nfcore-tools --export-source-setup ./nfcore-source-setup-1
```

From that exported directory, with Python 3.11 or newer:

```sh
python setup.py --profile nf-core/chipseq-1.2.1 \
  --home /absolute/new-source-home --report /absolute/new-report \
  --cache /absolute/private-source-cache --allow-install --allow-download
```

Home, report and cache must be disjoint physical paths with existing parents.
The home and report must be new for installation. Every setup/check uses a new
report. Paths containing spaces or Unicode are supported. On PowerShell, use
one line or replace shell continuation backslashes with PowerShell backticks.

The seven IDs are `nf-core/eager-2.3.4`, `nf-core/eager-2.4.4`,
`nf-core/chipseq-1.2.1`, `nf-core/chipseq-2.0.0`, `nf-core/atacseq-1.2.1`,
`nf-core/hic-2.1.0` and `nf-core/rnasplice-1.0.4`. Each pins an original Git
commit, compressed archive, complete source namespace, file sizes/hashes and
executable declarations. All 998 files across the seven editions are covered.
The original MIT notices remain in the extracted sources. SHA-256 establishes
integrity relative to the saved lock, not publisher authentication.

Downloads and installation require their respective flags. Omit
`--allow-download` to use an offline cache. Its exact required filename is
printed if missing; supply the pinned official archive under that filename.
Corrupt cached files and partial downloads are preserved. Do not change expected
hashes to accept a replacement archive.

Verify an installed source home without installing or downloading anything:

```sh
python setup.py --profile nf-core/chipseq-1.2.1 \
  --home /absolute/existing-source-home --report /absolute/new-check-report \
  --cache /absolute/private-source-cache
java -jar "$OMICS_JAR" nfcore-tools --inspect-source \
  nf-core/chipseq-1.2.1 /absolute/existing-source-home
```

Both readers check every source file and reject missing, changed or extra
members and path aliases. Exact POSIX execution modes are reported separately;
Windows source verification does not claim POSIX mode preservation. Use a
captured working copy for upstream execution so generated files do not alter
the pinned source home. Source verification does not run upstream code.

## Warnings and required action

- **Exit 0 means source-only success.** It is not a pipeline-analysis result.
  Read the report's `WARNINGS.md` and `COMPLETED.json`, whose
  `analysis_admitted` value is false.
- **SKIP3 / exit 3:** a required source, permission, installation or download is
  unavailable. The dependent operation/analysis is skipped. Provide the exact
  permitted offline prerequisite, enable the relevant flag where allowed, or
  retain the skip. Do not proceed using an empty or partial source directory.
- **FAIL2 / exit 2:** input, archive, namespace or integrity checks failed.
  Preserve the failed home/cache/report, correct the named cause, and choose new
  paths. Existing reports are never overwritten.
- **eager dependency restriction:** its original environment specifications
  include GATK 3.5. The source installer does not install that software. The
  project's GATK 3.8 acceptance does not establish acceptance of GATK 3.5 terms.
  Leave an analysis requiring that unaccepted bundle skipped, or qualify a
  permitted, explicitly declared process environment. Do not silently substitute
  a GATK edition.
- Qualify process software/containers and the engine independently from source.
  Check reference FASTA/annotations/indexes, blacklist/peak resources, eager
  screening databases, RNA-splicing contrasts, and Hi-C restriction/chromosome
  resources for the actual selected pipeline and settings. Source readiness
  does not establish resource readiness. No production genome or database is
  downloaded by this helper.
- Capture remote nf-core configuration includes and any plugins explicitly.
  A current `master` include or container tag does not identify an author's
  historical configuration/image. Naming a pipeline does not reproduce every
  separate method in its paper. Cell Ranger remains excluded.

## Verify development evidence

The packaged validator installs all seven exact archives from an offline cache,
rechecks their sources, exercises the Java reader and tests refusal paths:

```sh
python tools/literature/nfcore_workflows/validate_sources.py \
  --jar "$OMICS_JAR" --java /absolute/java \
  --intake /absolute/pinned-intake --out /absolute/new-validation
```

The intake layout is `PROJECT-VERSION/source.tar.gz`; archives must match the
packaged lock. `--online` additionally tests an explicitly permitted source
download. Raw captures, caches, runtime paths and process receipts remain
private. The public source checkpoint reports scoped counts and hashes only.
