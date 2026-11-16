# Sambamba duplicate-workflow development

**The integrated execution adapter is still pending.** This checkpoint provides
exact original downloads, verified paper-use candidates, 32 original synthetic
mark/remove executions, and a tested native Java BAM transition auditor.
An audit pass does not independently establish correct duplicate classification.
It does not qualify a complete workflow, promote paper-use reviews, change
catalog integration counts, or reproduce either paper.

## Reported paper uses

| Paper | Reported edition and use | Boundaries retained |
| --- | --- | --- |
| [P110, Nature Genetics](https://doi.org/10.1038/s41588-025-02409-6) | Sambamba0.6.6 removed duplicate ChIP-seq alignments after fastp trimming and Bowtie2 mapping, before downstream coverage/insert-size and MACS3 analyses. | Exact subcommand, complete flags, binary build, library/read-group metadata and resource settings are unreported. The tested direct `--remove-duplicates` route is an explicitly selected implementation of the role. |
| [P198, AJHG](https://doi.org/10.1016/j.ajhg.2024.09.005) | Sambamba0.8.2 marked duplicates after PAR alignments across haploid cells were combined per donor with SAMtools, before FreeBayes calling. Alignment used hg38 with the chrY PAR masked. | Marking is distinct from physical removal. No cell/UMI grouping is inferred. The paragraph also reports exclusion of doublet-associated barcodes without fixing its order relative to marking. The full donor/cell/variant workflow remains separate. |

Both exact cached primary XML hashes and selected paragraph hashes were
rechecked. Detailed candidates are in
`tools/literature/sambamba_workflows/source-roles.json`. They preserve unresolved
options and have not replaced the frozen catalog's existing reviewed uses.

## Original software and download entry point

The selected original releases are
[0.6.6](https://github.com/biod/sambamba/releases/tag/v0.6.6) and
[0.8.2](https://github.com/biod/sambamba/releases/tag/v0.8.2), distributed under
GPL-2.0-or-later. Exact binary and complete source archives are retained locally.
The downloader preserves the root license and nested component LICENSE files.
It changes no system packages and executes no downloaded program:

~~~powershell
python -B tools/literature/sambamba_workflows/download_runtime.py `
  --edition 0.6.6 --out build/NEW-sambamba066-download
python -B tools/literature/sambamba_workflows/download_runtime.py `
  --edition 0.8.2 --out build/NEW-sambamba082-download
~~~

These are Linux x86-64 executables. Windows needs an explicitly selected
permitted Linux/WSL execution environment; native Windows execution is absent.
The0.6.6 asset uses Linux shared libraries. The selected0.8.2 asset is static.
Fresh downloads of both releases matched all expected archive/executable hashes
and their original version commands ran successfully in the permitted WSL
environment. Existing directories were refused without changing their markers.
The first0.8.2 download attempt stopped on an ambiguous license-file selection
because the source includes a nested dependency LICENSE. That attempt and its
source were retained; the corrected downloader selects the exact root license
and preserves all nested LICENSE files. It does not discard a license to pass.

**SKIP3:** unavailable download/prerequisite; skip dependent analysis, supply
the exact permitted artifact/runtime, and retry NEW paths. **FAIL2:** altered
artifacts, existing output or recording failure; no valid download is admitted.
Read the console and `WARNINGS.md`/`FAILURE.json` when present. Preserve failures.
`DOWNLOADED.json` is a download receipt, not runtime or analysis qualification.
Do not replace saved identities or substitute editions. Integrity is not a
signature, upstream binary authenticity or a reproducible source-to-binary build.

## Source behavior preserved

The0.8.2 executable reports0.8.2 but writes `VN:1.0` in its BAM program record.
This is intentional: the original
[`utils/version_.d`](https://github.com/biod/sambamba/blob/v0.8.2/utils/version_.d)
sets a separate header-version constant. The0.6.6 release writes `VN:0.6.6`.
Do not infer the binary edition from header VN or replace1.0 with0.8.2.
The binary and source hashes, original version consoles and complete headers
are preserved together.

Original duplicate scoring sums raw base-quality bytes at least15. In the
selected missing-quality controls, byte255 remains255 and the known-quality
read is the duplicate. It is not replaced by zero. Primary duplicate bits are
recomputed, including clearing a preexisting bit on an otherwise unique or
unmapped primary read. Preexisting secondary/supplementary duplicate bits
persist; physical removal drops those flagged records too. Reference-library
grouping follows original RG/LB behavior. Different UMI tags do not establish
UMI-aware duplicate handling in this profile.

The eight independent synthetic inputs cover empty BAM, missing RG, paired
reads, paired-versus-fragment preference, clipping, strand/library differences,
existing flags, equal-score ties, missing qualities and rich auxiliary values.
All eight ran in mark and remove mode on both releases:32 original processes,
all returning zero. Complete raw BAMs, BAI sidecars, commands, stdout/stderr and
byte-level decoded observations are retained. Equal-score tie observations are
specific to these controls, not a global stability guarantee for all workloads.
The controls explicitly used one thread, compression6, hash table1024, overflow
list64, sort buffer16MiB and I/O buffer1MiB. These are project-selected controls,
not recovered author defaults or a large-data performance qualification.

## Native Java audit

`org.jlinalg.alignment.SambambaBamAudit` checks:

* Every BGZF member's structure, decompression size, CRC and terminal member;
  complete BAM/reference/record framing; auxiliary types/array bounds; coordinate
  order and mapped CIGAR/reference geometry.
* Identical complete binary reference dictionaries and all original header
  values. Header line/tag order is canonicalized for comparison; raw headers
  remain untouched. The added program record must have the source-defined VN
  and exact supplied command. Its previous-program reference must name an input
  program when present.
* Every marked record byte and record order, allowing only the duplicate bit to
  change. Float NaN payloads, signed zero, integer tag widths, arrays, CIGAR,
  sequence and qualities are compared as bytes, not reformatted SAM text.
* Exact retained records after removal against the original marked output.
  Between-run comparison allows only each independently checked exact command
  string to differ; scientific records and other header values must agree.

The API reports the complete flag transitions and source-defined quality and
five-prime metrics. Reverse coordinates retain the source convention
`position + reference span + trailing clipping`, without a minus-one adjustment.
The `scoredBySource` indicator distinguishes reads excluded from original
duplicate scoring. It deliberately accepts an otherwise valid flag-only
transition as a structural marking transition. Its tests demonstrate this
boundary. A complete adapter must additionally bind actual original execution
and replay that classifier; hashes and this audit alone cannot establish it.

The default bounds are64MiB compressed,256MiB decoded, one million records,
16MiB per record,4MiB header and fewer than16,384 references. These are not an
OS memory quota. Long-CIGAR `CG` semantics and nonstandard auxiliary types need
a separate profile and are refused. Coordinate sorting is required and never
silently repaired. Do not split or truncate biological inputs merely to pass a
limit: duplicate families can cross arbitrary chunks. Use a separately
qualified larger-capacity workflow when needed.

All8 Java tests passed on each of Windows JDK21 and JDK25. They include all32
original BAM outputs and deliberate changes to non-duplicate flags, sequence,
MAPQ, NaN payloads, signed zero, integer tag type, retained records, headers,
CRC, EOF, CIGAR and resource bounds. Packaged saved-output readback passed on
Windows JDK21/25 and Linux JDK21. This readback does not run Sambamba again.
The generated BAI files remain retained collateral outputs, without independent
index qualification in this checkpoint.

~~~powershell
.\gradlew.bat :test --tests org.jlinalg.alignment.SambambaBamAuditTest
.\gradlew.bat :executableJar
java -cp build/cli/omicsmatic-0.4.0.jar `
  tools/literature/sambamba_validation/Readback.java `
  src/test/resources/org/jlinalg/alignment/sambamba-original-markdup-066-082.zip `
  build/NEW-sambamba-native-readback
python -B tools/literature/sambamba_workflows/check_development.py
~~~

The JAR must be built from this development source. No previously published
release asset is replaced. Saved readback is distinct from a fresh original
runtime check or analysis.

## Work still required for execution admission

The next implementation needs a strict Linux-JVM execution entry point, explicit
mark/remove options, original binary/dependency pre/post checks, captured-input
and original/effective request coherence, and complete output recording. It
needs actual owned-process wall/memory/work/cancellation controls, including
parent-death handling and survival of unrelated jobs; real original reruns
against captured inputs; refusal/late-recording controls; spill/thread coverage;
and clearly scoped index handling. Linux `prlimit` and `setpriv --pdeathsig`
are available in the observed environment, but their integration is not yet
qualified here. A missing permitted runtime must skip its dependent analysis
with a named prerequisite and recovery instruction.

Catalog admission also requires the separate current-release source transition
review described in [the main warning](MAIN_SYNC_20261115_STATUS.md). This
checkpoint does not bypass that guard. Existing scientific evidence, the
250-paper corpus, integration counts and full-goal scope remain unchanged.
