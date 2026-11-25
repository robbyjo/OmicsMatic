# CrossMap coordinate conversion

The source build provides `crossmap-tools` for original CrossMap **0.5.2** and
**0.6.3**. Select an edition and BED, VCF or BigWig explicitly. Original programs
perform the conversion; Java captures complete inputs/results, checks the
record populations, reads every raw BigWig interval and checks VCF reference
bases against the captured target FASTA. This is a scoped upstream adapter.

Execution requires Linux Java, including Linux Java inside WSL on Windows.
Saved-result verification works on Windows and Linux with the exact producing
executable JAR. The earlier v0.4.1 release asset predates this source feature;
build the current project and retain that new executable. These changes do not
publish a new release.

```sh
OMICS_JAR=/absolute/omicsmatic-0.4.1.jar
java -jar "$OMICS_JAR" crossmap-tools --profiles
java -jar "$OMICS_JAR" crossmap-tools --help
```

## Prepare the private runtime

Export the setup kit to a new directory, then run its setup separately with the
qualified Linux x86_64 CPython **3.12.3** interpreter. Linux execution also
requires permitted `sort`, `setsid`, `prlimit` and readable `/proc` metadata. This provider contains
NumPy 1.26.4, pysam 0.22.1, bx-python 0.12.0, pyBigWig 0.3.24 and Cython 3.0.12.
Original source wheels, dependencies and source member hashes are pinned.
The Python 3.7 environment reported by P072 has not been reproduced.

```sh
java -jar "$OMICS_JAR" crossmap-tools --export-runtime-setup /absolute/new-kit
cd /absolute/new-kit
python3 -B setup.py --home /absolute/new-private-home \
  --report /absolute/new-setup-report --cache /absolute/wheel-cache \
  --allow-install --allow-download
```

Installation/download must be permitted. For offline setup, supply all eight
locked wheels in the cache and omit `--allow-download`. Without `--allow-install`,
setup checks an existing complete runtime and does not install a missing one.
Use a **new report path** for each check. Existing homes are checked against
their original seals; changed files are not accepted by refreshing hashes.
Existing report directories are preserved.

Require `COMPLETED.json`, read `WARNINGS.md` and retain the complete setup report.
Its six small original-format health controls are artificial. They do not admit
a user conversion. Analysis never installs software, downloads a genome or
changes host configuration.

```sh
java -jar "$OMICS_JAR" crossmap-tools \
  --inspect-runtime /absolute/private-home \
  --setup-report /absolute/completed-setup-report
```

## Declare and run a conversion

Supply canonical absolute local input paths. BED uses zero-based, half-open
coordinates; VCF uses its original one-based POS convention. If representing
one-based GWAS positions as BED, the caller must explicitly prepare `[POS-1,POS)`
intervals and retain the original position table. No coordinate convention,
assembly identity, file format or author-unreported edition is inferred.

This BED example declares bounds for a small conversion. Choose bounds for your
actual complete files and available memory. Combined copied input bytes include
the chain and, for VCF, the target FASTA. A whole human FASTA alone can exceed
the example's input bound. Text record bounds include header/blank logical lines.

```json
{
  "schema_version": 1,
  "edition": "0.6.3",
  "format": "bed",
  "input": "/absolute/regions.bed",
  "chain": "/absolute/directional.chain.gz",
  "input_encoding": "plain",
  "chain_encoding": "gzip",
  "chrom_id_style": "a",
  "source_build": "GRCh38",
  "target_build": "GRCh37",
  "unmapped_policy": "retain",
  "process": {
    "wall_millis": 3600000,
    "address_space_bytes": 8589934592,
    "cpu_seconds": 3600,
    "file_bytes": 1073741824,
    "open_files": 1024,
    "workspace_bytes": 1073741824,
    "file_count": 10000,
    "processes": 32,
    "threads": 256,
    "sampled_rss_bytes": 4294967296,
    "monitor_millis": 100
  },
  "capture": {
    "input_bytes": 268435456,
    "output_bytes": 536870912,
    "records": 1000000,
    "bigwig_block_bytes": 67108864
  }
}
```

For edition 0.5.2, set `chrom_id_style` to `source-default`; the 0.6.3 chromid
option is not fabricated for that older command. For 0.6.3, `a`, `s` and `l`
select the original as-is, short or long naming option. Read source diagnostics
for naming conflicts; the adapter does not repair chromosome identities.

For VCF, use `format: "vcf"` and additionally provide `target_fasta` with an
uncompressed FASTA path and an explicit boolean `compare_alleles`. The original
program generates an index beside the **captured copy**, leaving the original
reference untouched. `compare_alleles: false` passes the original
`--no-comp-alleles` option. Genotype columns remain source pass-through fields;
this operation does not phase, impute or harmonize GWAS effect alleles.

For BigWig, use `format: "bigwig"` and `input_encoding: "binary"`. Plain/gzip
text inputs and chains are selected explicitly. BAM/CRAM/SAM, GFF/GTF, WIG,
GVCF, MAF and region-mode contracts are not qualified by this adapter.

```sh
java -jar "$OMICS_JAR" crossmap-tools --request /absolute/request.json \
  --home /absolute/private-home --setup-report /absolute/completed-setup-report \
  --out /absolute/new-conversion
java -jar "$OMICS_JAR" crossmap-tools --verify /absolute/new-conversion
```

Keep the exact producing JAR, complete conversion directory, original inputs
and private runtime evidence. Verification checks every captured file and
selected domain contract. It does not consult live producer paths, execute the
Python runtime or refit a source model. Existing conversion paths are preserved;
changed requests or failed attempts require new output paths.

## Warnings and required action

- **SKIP3 / exit 3:** a required exact runtime, permitted setup step, platform or
  supported capability is unavailable. The dependent conversion is skipped.
  Supply the named permitted prerequisite or complete separate authorized setup.
- **FAIL2 / exit 2:** invalid/changed inputs, process/capacity/cancellation,
  inconsistent output or finalization failure admits no new complete result.
  Read `FAILURE.json`, `BRIDGE-FAILURE.json`, `audit/original.stderr` and
  `process/process.stderr`; preserve all originals and attempts, correct the
  named cause and use new paths. Never treat a partial track or exit zero alone
  as a completed result.
- **Review every outcome.** `OUTPUT-LEDGER.jsonl` accounts for each selected
  source record. `OUTPUT-AUDIT.json` records mapped/rejected inputs, split or
  multiple output blocks, partial spans and source metadata omissions. With
  `unmapped_policy: "error"`, rejections/discards or partial spans prevent
  admission. `retain` preserves the original outcomes and warnings. A source
  rejection or unmapped row is not a biological negative finding.
- **Original limitations are preserved.** Both editions reject the tested
  reverse multiblock BED12. BED20 can produce BED12 output without its eight
  extra metadata columns. Original 0.6.3 VCF can fail without contig metadata;
  preserve that attempt and provide authoritative metadata only in a separately
  prepared input. The 0.5.2 indel ALT behavior differs from 0.6.3's updated
  anchors. Source-generated path, basename and date metadata describe the
  captured execution; original paths and exact original bytes remain recorded.
- **Signal conversion can lose records by source policy.** Original BigWig
  conversion discards intervals without a single mapped block and sums retained
  overlapping target signal. It can fail for all-unmapped tracks. In the tested 0.6.3 profile, forcing short chromosome IDs (`s`) for
  BigWig also fails in the original header/data writer; `a` and `l` passed.
  Tracks with an empty input chromosome can fail in original iteration. Original
  diagnostics and nonfinite/unassessable-output failures are retained; values
  are not replaced with zero or silently averaged.
- **Resource limits are explicit.** Complete inputs, outputs and witnesses must
  fit declared bounds; generated files also have an aggregate output budget plus
  a 16 MiB metadata reserve. Oversized lines/records or insufficient process capacity
  fail without truncation. Sampled child RSS is not a hard aggregate memory
  bound, and capture limits do not guarantee sufficient JVM heap.

The original distributions are [CrossMap 0.5.2](https://pypi.org/project/CrossMap/0.5.2/)
and [CrossMap 0.6.3](https://pypi.org/project/CrossMap/0.6.3/). Their original
license notices remain installed with the unchanged sources. The paper roles
remain distinct: P003 used BigWig conversion; P108 converted typed genotype
coordinates; P072 harmonized GWAS builds; P242 converted DHS intervals before
separate SNP-overlap analysis. Author-unreported formats/options and downstream
QC or statistical procedures are not invented. These checks establish a scoped
source/output contract, not biological accuracy or whole-paper reproduction.

## Paper uses and remaining scope

| Paper | Reported use | Edition and remaining information |
| --- | --- | --- |
| [P003](https://doi.org/10.1038/s41586-025-09890-3) | Convert epigenomic BigWig coordinates from hg19 to hg38 before chromatin-state annotation. | CrossMap 0.5.2. ChromHMM and the study's track selection remain separate. |
| [P072](https://doi.org/10.1038/s41588-023-01584-8) | Harmonize GWAS datasets to hg19 before ancestry meta-analysis. | CrossMap edition and input format are unreported; Python 3.7 is reported. The qualified Python 3.12.3 provider does not reproduce that interpreter. |
| [P108](https://doi.org/10.1038/s41588-023-01560-2) | Convert typed genotype coordinates to GRCh38 before filtering and imputation preparation. | CrossMap 0.6.3. The paragraph does not identify a file format or exact flags; availability of this VCF adapter does not establish the authors used VCF. |
| [P242](https://doi.org/10.1016/j.ajhg.2025.07.004) | Convert DHS intervals from hg38 to hg19 using a UCSC chain, before separate SNP-to-interval matching. | CrossMap edition is unreported. GenomicRanges overlap and mask construction remain separate. |

The full 250-paper primary-XML scan finds five eligible method paragraphs in
these four papers. A broader spelling scan also finds P102's generic verb
“cross-map” describing ambiguous short-read alignment; it is not a CrossMap
software use. A chain-download link establishes the data source, not execution
of the UCSC liftOver program. No unreported edition, chain identity or command
option is inferred from the runtime available here.

## Validation record

The retained qualification includes 26 admitted captures verified with the
exact producer on Windows and Linux: six base version/format combinations,
11 option/compressed-input cases, eight cases on complete retained UCSC human
chains, and one conversion from a freshly exported/offline-installed runtime.
The human-chain controls use five declared full-block intervals per direction;
their coordinates agree with an independent native HTSJDK mapper. These are
small synthetic queries or signals on real chains, not reanalyses of study data.

Seventeen intentional conversion refusals cover loss policy, original source
failures and capture/process capacities. Readonly tamper checks, runtime mutation
refusals, setup/platform skips and existing-path preservation were also exercised.
All 26 selected Java tests passed on both operating systems. The full JAR member
comparison preserves every one of the 681 earlier genetics classes byte for byte.

The [workflow checkpoint](crossmap-workflow-checkpoint/STATUS.json) contains
source hashes and scoped counts; raw runtimes, process traces and evidence remain
private. Its readonly reader needs the exact producing and predecessor JARs,
private evidence and 250 cached source XMLs. Preserve those prerequisites and the
immutable source context for future readback; published hashes are never updated
to accept changed historical evidence.
