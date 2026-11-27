# WASP mapping and remapping

`wasp-tools` runs the original standalone WASP mapping workflow from an existing
aligned BAM: allele-swapped FASTQs, BWA remapping, original CIGAR-aware filtering,
merge of retained original records, optional original paired duplicate removal,
and a sorted indexed final BAM. Native Java checks account for the complete
input, generated FASTQ and retained-read populations. Original scientific
programs execute unchanged in a private Linux runtime.

**Warnings and required action:** Read `WARNINGS.md`, `INPUT-AUDIT.json`,
`OUTPUT-AUDIT.json` and every original process log. Require `COMPLETED.json` and a
successful fresh replay with the exact producing JAR before using a result.
An empty final BAM explicitly skips downstream analysis. This workflow does not
establish a paper's reference, sample preparation, original alignment or author
revision. Hornet, STARsolo's built-in WASP mode, and WASP's Combined Haplotype
Test are separate implementations.

## Run

Use Linux x86_64 Java 17 or later; Windows can run Linux Java inside WSL.
Native tests run on Windows and Linux. Original runtime execution requires
Linux; running Windows Java with Linux executable paths produces **SKIP3**.
Use the qualification's exact producing JAR or build this commit's
`executableJar` task. Source integration and publication of a release asset are
separate; the catalog records the artifact's qualification scope explicitly.

```sh
java -jar omicsmatic.jar wasp-tools --profiles
java -jar omicsmatic.jar wasp-tools --example binding > binding-request.json
java -jar omicsmatic.jar wasp-tools --bind binding-request.json --out NEW-binding
java -jar omicsmatic.jar wasp-tools --example analysis > analysis-request.json
java -Xmx8g -jar omicsmatic.jar wasp-tools --request analysis-request.json --out NEW-capture
java -Xmx8g -jar omicsmatic.jar wasp-tools --verify NEW-capture --scratch NEW-replay
```

Replace the example paths, preparation and mapper provenance, variant/read
domain, settings and capacities. All input and runtime paths are canonical
absolute paths. Output directories must be new and separate from inputs and
runtime roots. Keep each unsuccessful attempt; retry in new directories.
The analysis command does not install software.

## Original runtime

The profile `WASP-0.3.4-d3b8447f-BWA-0.7.17` binds:

- WASP source at `d3b8447fd7719fffa00b856fd1f27c845554693e`, including the original
  mapping Python programs and the original `snp2h5` C converter when requested.
- Original BWA 0.7.17-r1188 source at
  `9f26bfcc7780753129b60717ecab0ebba6f04b7c` and its executable.
- Python 3.11.0, NumPy 1.23.5, SciPy 1.10.1, Pysam 0.21.0,
  PyTables 3.8.0 and H5py 3.8.0, with their complete runtime closure.
- Samtools 1.19.2 for owned sorting, merging and BAM indexing.

Inspect the exact source manifests in the packaged `profiles.json`. Create
private source views containing exactly the 82 WASP and 77 BWA listed files,
preserving their upstream bytes. Keep original archives separately. A Git
checkout containing `.git`, build products or excluded example datasets is not
that source view. Retain the full original source and license provenance.
Build original BWA using `-fcommon` with modern GCC; build original `snp2h5`
against HDF5 1.10.10 using `-DH5_USE_16_API` and its original dependencies.
No scientific source patch or NumPy alias monkeypatch is admitted.

The binding request declares the source directories, Python, BWA, Samtools,
optional `snp2h5`, complete runtime/library roots, and distinct helper library
paths. Include the venv's base Python interpreter/stdlib and native libraries in
those roots. Preserve Python's lexical venv executable path. Keep helper HDF5 or
Samtools library paths out of the Python environment. The binder performs real
import/version, executable and converter health checks, hashes the complete
runtime and source populations, and binds the exact producing JAR. Restore that
JAR and those runtime bytes to replay an old result; do not refresh old seals.

Installation is allowed only where the operating environment permits it. If a
required package, helper, reference, installation permission or license is
unavailable, **SKIP3: DEPENDENT WASP ANALYSIS SKIPPED** identifies the missing
prerequisite. Supply/install it where permitted and create a new binding and
capture. Do not run the dependent analysis with a substitute tool or version.

The original [WASP repository](https://github.com/bmvdgeijn/WASP) uses Apache 2.0;
the packaged notice credits the source-derived native filter rules. Other
upstream runtime components retain their respective terms. Original software,
archives, article text and process traces are not bundled with this adapter.

## Inputs and source behavior

The reference is an explicit complete FASTA or gzip FASTA. Native checks compare
all BAM dictionary lengths and any supplied BAM M5 checksums with the actual
sequence. Equal names and lengths alone do not prove identical reference builds.
An optional original BWA ALT annotation is supplied explicitly. Remapping uses
the declared BWA threads, `-M`, insert distribution and typed additional numeric
options; output redirection and conflicting owned options are refused.

Choose one variant mode:

| Mode | Required input | Explicit interpretation |
| --- | --- | --- |
| `text` | Flat `<literal-contig>.snps.txt.gz` directory | Every source-eligible contig needs a file. An intentionally empty gzip file declares no variants. Source uppercasing and hyphen removal are reported; S10 allele truncation, duplicate positions and unsorted positions are refused. |
| `hdf5` | `snp_table`, `snp_index`, `haplotypes`, and `samples` | `samples: null` uses all original samples; a nonempty list selects literal existing names. Complete fixed-type columns and arrays, index-to-SNP correspondence, genotype/phase values and sample/contig shapes are checked. |
| `vcf-to-hdf5` | `chromosomes`, per-chromosome `vcfs`, and `samples` | Native Java checks every input row and source field limit, the original C converter writes HDF5, and every converted variant/genotype/phase/sample population is compared with the VCF. No additional VCF QC or statistical phasing is applied. |

For HDF modes select `hdf_contig_policy: "literal"` or explicitly opt into
`"original-add-remove-chr"`. Declare intentional missing genotype coverage in
`explicit_no_variant_contigs`. Unknown selected samples and undeclared missing
eligible-contig coverage fail admission. Contigs whose placed reads are all
source-ineligible are accounted separately.

VCF conversion accepts literal biallelic diploid `0`/`1`/missing GT calls.
Original C can silently truncate allele fields, so alleles of 100 or more bytes
are refused before conversion. Partially missing calls become two missing
alleles in the original converter; its phase flag remains 1 after parse failure.
Both behaviors are reported. Fully parsed `/` calls have phase 0. No missing
allele is filled, identifier renamed, coordinate repaired or alternate ploidy
reinterpreted. Supply a compatible, provenance-backed preparation when a
different domain is needed.

`read_layout` selects `single` or `paired-with-optional-single-reads`.
`duplicate_policy: "none"` preserves all retained original records;
`"original-random-paired"` invokes original `rmdup_pe.py` with an explicit seed
and drops single reads. Original single-read `rmdup.py` omits its final position
group and is not admitted. The original paired filter compares CIGARs as an
unordered set. Its coordinate-only chromosome omission is preserved in source
execution, with an additional native refusal if a retained read maps to the
wrong chromosome.

Original generated FASTQ quality/orientation conventions remain unchanged,
including unchanged quality order when the second paired sequence is reverse
complemented. Zero-alternative candidates and nominal sequence-limit boundary
behavior are reported explicitly. Do not interpret source fidelity as a
correction of those upstream behaviors.

## Resources, replay and failures

Declare complete input/output bytes, files, records, variant rows, decoded text,
line lengths, HDF cells, native SAM characters and wall limits, plus the owned
process address-space, RSS, CPU, thread/process/file and workspace limits.
Native record audits currently retain BAM records and population maps in JVM
memory. Those capacities do not reserve heap or establish whole-genome scale;
allocate a suitable JVM heap and storage for all intermediate BAMs and replay.
No downsampling, record truncation or silently reduced model is used.

**FAIL2: NO COMPLETE WASP WORKFLOW** applies to invalid or changed input,
runtime/source/producer bytes, native/original disagreement, failed QC,
cancellation, memory exhaustion or other capacity failure. Preserve logs and
`FAILURE.json`, correct the named cause and retry new paths. Partial files do not
constitute a completed result. Expected messages about the optional unindexed
to-remap intermediate are retained; required final BAM indexes are always checked.

Fresh verification reruns the original pipeline from the saved raw inputs and
compares all canonical populations, decoded FASTQ bytes, fixed-type HDF cells,
BWA index files and complete final BAM/index bytes. Gzip/HDF container creation
metadata can differ. Saved-file seals also detect missing, additional or changed
files. Seals and replay establish this bounded computation's consistency, not
the authenticity of an author's inputs or biological conclusions.

Qualification and remaining paper-specific gaps are recorded in
`docs/literature/wasp-validation/QUALIFICATION.json` and
`docs/literature/wasp-source-audit/ROLES.json`. The 250-paper implementation goal
remains active.
