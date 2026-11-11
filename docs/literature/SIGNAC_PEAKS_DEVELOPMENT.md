# Typed original MACS peak-calling development

`org.jlinalg.cli.SignacPeaksCli` combines native Java fragment preparation and peak geometry with tested adapters to original MACS2. It captures explicit populations, runtime/source identities, bounded process receipts, original outputs, public peak tables and one-based region files. Java independently rechecks splitting, peak transport, sequence ordering and grouped interval reduction. Start with the [five runnable examples and feature-counting chain](../../examples/signac-peaks/README.md).

**Standalone development scope:** Signac remains `missing` and Seurat remains `method-overlap` in the 250-paper catalog. Counts remain **70 new scoped integrations, 73 including existing adapters and 258 missing named tools**. Broader Signac coverage, including coverage plotting, paper-role review and unified public dispatch, remains unfinished. Cell Ranger stays excluded. No release, push, deployment, full project test suite, whole-paper reproduction or whole-goal completion is claimed.

## Warnings and required actions

| Condition | What to do |
| --- | --- |
| **SKIP3 / exit 3: dependency unavailable or forbidden** | Skip dependent peak analysis. Read the named private home, Python/package or Linux process-helper requirement in `WARNINGS.md` and `analysis-status.json`. Where allowed, provide that exact runtime and retry into a **new** output path. If installation or execution is forbidden, retain the skipped analysis. Analysis never installs software. |
| **FAIL2 / exit 2: no result admitted** | Preserve the attempt and all source logs/partial files. Correct the named input, map, grouping, source-version, source-process, integrity or capacity error and retry into new paths. A partial peak file from an earlier group does not admit the requested analysis. |
| Successful empty peak table | A zero-row table is valid when the original call succeeds. It is not a source failure. Grouped combined calling omits zero-peak groups, as Signac does; if all groups are empty, original `reduce(NULL)` fails and no combined result is admitted. `combine_peaks: false` can preserve successful empty group tables. |
| Original MACS warnings | Inspect complete `process/g#/process.stderr` and corresponding original spreadsheets. The first 1,000 warning/error lines also appear in diagnostics/warnings, with a complete count. Exit 0 does not establish a suitable statistical model or experiment. |
| Name substitutions/collisions | Linux Signac substitutes ASCII spaces and `/` with `_`. Distinct labels can collapse and pool fragments. Read each job's `source_name` and `original_labels` in `prepared-summary.json`; choose explicit populations before interpreting comparisons. Safe `g#` filenames are recorded transport prefixes. |
| Combined intervals | Reduction merges overlapping **and adjacent** intervals within chromosome/strand. Source scores and p/q statistics are discarded from combined intervals. `peak_called_in` records source-normalized group membership; `groups_json` disambiguates labels that contain commas. Combined regions do not have newly calculated significance. |
| Duplicate peak coordinates | All distinct source rows remain. A downstream feature definition may need a declared decision before FeatureMatrix, which refuses repeated exact feature labels. No automatic row deletion or coordinate repair occurs. |
| **VERIFY scope** | Checks complete member populations/hashes, captured settings/inputs, bundled source bytes, runtime identity claims, recorded commands/limits, native split bytes, geometry and reduction. It does **not** refit MACS statistics, reconcile all spreadsheet statistics, authenticate execution/host libraries, certify biology or reproduce a full paper. Consistently fabricated source statistics with recomputed copies and hashes can pass. |
| Completion | Require exit 0, `results_admitted: true`, successful VERIFY and the expected requested population. A completion filename alone is insufficient; failed/skipped captures are retained and refuse VERIFY. |

## Exact runtime profiles and setup

Calling requires **Linux Java**, including Java inside WSL on Windows. The closed profiles support Signac **1.9.0 / 1.14.0** command semantics and original MACS2 **2.2.7.1 / 2.2.9 / 2.2.9.1**, each built unchanged with Python **3.9.20** and NumPy **1.24.4**. The retained private reference toolchain used Cython 0.29.37, pip 21.3.1, setuptools 57.5.0, wheel 0.37.1 and GCC 13.3.0. Runtime binding checks every installed original MACS `.py`/`.pxd`, its script body except the interpreter directive, exact compiled-module names and loaded Python/package prefix. It fingerprints compiled modules before/after calls; this is not independent authentication of their source or all loaded libraries.

MACS2 2.2.7.1's unchanged setup reads `float(sys.version[:3])`, which misinterprets Python 3.11 as 3.1. Its malformed dependency declaration also requires the older build tools. The existing [runtime evidence guide](SIGNAC_PEAK_RUNTIME_DEVELOPMENT.md) preserves the failed setup attempts, source archives, licenses, final exact builds and independent original calculations. No upstream MACS scientific/build source was patched. The private Python extraction omits its unused terminal database explicitly; omission receipts and the complete original archive remain retained.

A fresh clone lacks these private homes. An administrator may provide a compatible permitted Linux environment, then set `macs2_home` to its absolute home containing `bin/python`, `bin/macs2`, `pyvenv.cfg` and `lib/python3.9/site-packages/MACS2`. Keep the interpreter's base installation available when the virtual environment uses symlinks. A mismatched edition is FAIL2, not automatic replacement.

For this developer checkout, the retained setup helpers can build new **private synthetic reference runtimes** where downloading/installing is allowed. They are not a general cross-platform installer. In Linux, from the repository root, inspect their `--help`, then use **new** output paths:

```bash
python3 tools/literature/intake_macs2_reference.py --out build/literature-review/signac-runtime/peaks-source-intake-1
python3 tools/literature/setup_macs2_reference.py --allow-download --allow-install --out build/literature-review/signac-runtime/peaks-private-user-NEW
```

The intake command is needed only if `peaks-source-intake-1` does not exist. Do not overwrite an existing intake or installation. The setup helper expects that exact retained intake, a Linux x86-64 environment with GCC/G++ and ordinary process utilities, and access to the recorded official source/wheel archives. Follow its `WARNINGS.md`, `SKIP3.json`/`FAILURE.json`, logs and package-identity receipts. Point requests to `.../peaks-private-user-NEW/<MACS_VERSION>` only after successful setup and profile admission. If requirements or permissions are unavailable, keep SKIP3 and skip dependent analysis. Never use privileged installation or alter shared/frozen libraries to bypass restrictions.

## Request and population semantics

All requests are closed UTF-8 JSON objects with `schema_version: 1`. Unknown fields are refused. Relative paths resolve against the request's directory; output must be new. Call modes are `POOLED` and `GROUPED`; `VERIFY` accepts only `schema_version`, `mode` and `analysis`.

Both call modes require `source_profile`, `macs2_version`, `macs2_home`, declared `genome_build`, positive numeric `effective_genome_size`, `sources` and `out`. There is **no inferred human genome size**. `sources` contains 1–256 objects with `fragments` and, for GROUPED only, optional `map`. Complete original plain/gzip inputs are byte-captured and checked; no input modification or automatic prefix sampling occurs. Genome build is a declaration, not reference FASTA validation.

POOLED uses **every supplied record**, including barcodes outside an external object's QC selection. It refuses unused maps/group options. `name` defaults to `macs2`. GROUPED requires `cell_groups`, a literal TSV headed `cell<TAB>group`, in declared population order. Optional per-source map TSVs have `cell<TAB>barcode`, mapping object cell IDs to raw source barcodes. Without a map, identity mapping is explicit. Mappings and population selection follow the [native Linux Signac split profile](SIGNAC_PEAK_PREPARATION_DEVELOPMENT.md); ambiguous, duplicate or invalid identities are refused. The complete source scanner rejects a zero-record input before launching MACS; original MACS also fails on the independent empty-input control. No sample namespace or QC choice is inferred. `combine_peaks` defaults to true; GROUPED refuses an ignored `name`.

Selected grouped data-line bytes retain original line endings, read support, source/file order and duplicate rows. A missing final newline can join records across appended sources; this behavior is preserved and warned about. The frozen native preparation profile uses Linux source bytes on all JVM platforms; original Windows C++ newline translation is a separately documented difference.

## Peak model options

| Request setting | Behavior |
| --- | --- |
| `format` | `BED` by default; explicit `BEDPE` uses full fragment intervals. Five-column BED uses forward-strand tag starts. Neither mode multiplies fragment rows by the fifth read-support field. |
| `extsize`, `shift` | BED only: `--nomodel`, extension 200 and shift −100 by default. Omitted shift is `-extsize/2`; an odd extension requires an explicit integer shift because original MACS refuses the fractional default. BEDPE rejects unused extension/shift fields. |
| `broad` | Defaults false. Broad mode retains broadPeak/gappedPeak/XLS; narrow mode retains narrowPeak/summits/XLS. |
| `qvalue`, `pvalue` | Mutually exclusive positive probabilities ≤1. When omitted, original MACS defaults apply and remain recorded in logs/spreadsheets. |
| `keep_duplicates` | Explicit `all`, `auto` or positive integer; omitted means original MACS default. This policy is separate from read support. |
| `broad_cutoff` | Positive probability ≤1; requires broad mode. |
| `call_summits`, `no_lambda` | Boolean original flags. Broad mode refuses `call_summits`. |
| `min_length`, `max_gap`, `small_local`, `large_local`, `fold_enrichment_cutoff` | Explicit bounded original numeric options. `max_gap` can be zero; other provided values are positive. Original source validation still applies. |
| `bedgraph`, `SPMR` | Preserve original pileup/control bedGraphs. SPMR requires bedgraph output. It changes bedGraph units; consult retained original headers/logs before using values. |

Only typed options become process arguments. Arbitrary commands, shell strings, control-file options, model substitutions and generic peak-threshold fallbacks are unsupported. Literal names with quotes/shell metacharacters remain data in a fixed argument vector and indexed file transport.

## Outputs, native geometry and verification

`peaks.tsv` retains every source peak with its source-normalized group/name, exact source statistic strings, one-based closed coordinates, strand and relative summit. Original source narrow/broad files remain zero-based BED in `macs/g#/`. Unknown sequence/genome metadata remains unknown. The relative summit is retained as the original offset, not converted into an absolute coordinate. Scores are not clipped to BED display bounds. Explicit `NA`, `NaN` and `Inf` source kinds are retained where accepted by the strict source domain.

POOLED creates `regions.tsv`. GROUPED creates `regions-g#.tsv` for every job and, when combined, `combined-peaks.tsv` plus `regions.tsv`. These three-column region files use the native FeatureMatrix header `chromosome<TAB>start<TAB>end` and **one-based closed** coordinates. Do not convert starts again. Match the declared assembly, chromosome names, sample mappings and sorted fragment requirements of the [fragment workflow](SIGNAC_FRAGMENTS_DEVELOPMENT.md).

Native sequence ranks follow Seqinfo 1.2.0 and grouped unknown-genome interval combination follows GenomicRanges 1.64.0, including context-sensitive X/Roman/numeric ordering, sequence-level union order, adjacency, membership order and combination warnings. These versions describe the retained reference profile, not every historical Bioconductor environment. The source-control matrix includes integer-overflow ordering and empty-list behavior.

The capture includes original/captured/effective requests, original input bytes/hashes, bundled sources/licenses, native preparation summaries/split bytes, runtime binding, commands/process limits, complete source outputs/logs, public tables, diagnostics/warnings, transaction manifests and a capture seal. VERIFY reruns native preparation and geometry without MACS or R. It checks exact allowed file and directory populations, including unexpected empty directories. File hashes provide integrity, not signatures. The evidence explicitly demonstrates both rejected resealed corruptions and the coordinated-statistics forgery limit.

## Capacities and validation

Defaults are a 3,600-second per-process wall limit, 4 GiB per-process address space, 3,600 CPU seconds, 2 GiB sampled aggregate RSS, 16 owned processes, 64 threads, 10,000 accounted files, 1 GB captured inputs, 2 GB prepared/output bytes, 2 million cells/peak rows, 128 groups, 1 billion fragment rows and 250 GB decoded bytes **per fragment source**. Limits are explicit and bounded in the request. Increase only as justified by inputs and the operating environment; also provide a suitable Java heap. Linux hard process limits cover address space, CPU, individual file size and open files. Wall/workspace/process/thread/RSS checks are periodic; sampled aggregate RSS is not a hard aggregate limit. Java preparation/output memory is bounded by declared row/byte capacities and heap. A failed limit admits no partial result.

Focused validation and frozen evidence are recorded in [signac-peaks-development/VALIDATION.json](signac-peaks-development/VALIDATION.json). Original independent controls, real typed calls, three-JVM tests/verification, literal examples, failure/skip/recovery checks and limitations are retained there. The parent native preparation, MACS reference, Seurat differential and canonical executable artifacts remain preserved. This scope does not establish all paper-specific settings, biological accuracy or a complete Signac integration.
