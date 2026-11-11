# Original MACS2 peak-calling references for Signac

Original **MACS2 2.2.7.1, 2.2.9 and 2.2.9.1** were built unchanged in separate private Linux environments and each completed nine real peak-calling controls. This supplies original expected outputs for the pending typed Signac `CallPeaks` workflow. The [native grouped-fragment preparation](SIGNAC_PEAK_PREPARATION_DEVELOPMENT.md) is implemented separately.

**These are synthetic original-runtime references, not an admitted Java CallPeaks interface or user analysis.** The complete request/capture/output/verification workflow, source-compatible group reduction and public integration remain unfinished. Signac still receives no catalog credit. Totals remain **70 new scoped integrations, 73 including existing adapters, and 258 missing named tools**. The full 250-paper goal remains active; Cell Ranger stays excluded.

## Required warnings and recovery

| Condition | Required action |
| --- | --- |
| **SKIP3: required installation or execution unavailable/forbidden** | Skip dependent peak analysis. Provide the exact named permitted private runtime and its dependencies; preserve logs and choose new paths. Do not substitute generic threshold peaks or another MACS edition. Setup does not install during an analysis. |
| **FAIL2: source/input/process/integrity failure** | Admit no Java/user result. Preserve every partial table and raw log, correct the named cause and repeat into a new directory. A successful process exit alone does not validate downstream geometry, statistics, identities or biology. |
| **Valid empty peak table** | The low-signal control completes with zero peaks. This differs from an empty input file, which fails in the original MACS runtime. Do not turn a failed source call into a negative result. The original retained Signac parser separately accepts empty output. |
| **BED versus BEDPE** | Five-column BED fragments are read as forward-strand tags at their start positions; the source wrapper adds `--nomodel --extsize 200 --shift -100`. BEDPE uses whole-fragment intervals and omits those wrapper options. These are different models; choose explicitly rather than switching modes merely to obtain results. |
| Read support | Changing only the fifth fragment field leaves the tested peak table unchanged. It does not multiply observations. Exact duplicate lines are a separate issue governed by MACS's declared duplicate policy. |
| Grouped calling | Per-group source files and pooled files produce different peak sets. Preserve selected cells, per-source barcode maps and assembly/settings. The source reduction's `peak_called_in` order and sequence-level ordering require explicit validation before implementing the complete adapter. |

## Reference environments and source preservation

The references use Linux x86_64, private CPython **3.9.20**, numpy **1.24.4**, Cython **0.29.37**, pip **21.3.1**, setuptools **57.5.0** and wheel **0.37.1**. MACS extensions were built with GCC **13.3.0**; the downloaded CPython interpreter itself reports its original Clang build identity. Exact package/script/module fingerprints and all process logs are retained. These choices do not reconstruct the papers' full historical environments or authenticate the entire host/shared-library closure.

The official PyPI source archives are checked against the published SHA256 values. Installed Python/header source files match their unchanged distribution files. Setuptools changes only the interpreter directive in the installed `macs2` script; its remaining bytes match the original script. The compiled extensions are retained privately, with fingerprints and original source/build evidence. No MACS scientific or setup source was patched.

The first four private setup attempts remain retained. They exposed case-only aliases in CPython's unused terminal database on Windows-backed storage, an ambiguous compiled/universal Cython wheel choice, copied-interpreter shared-library lookup, and an unavailable default clang compiler. The successful fifth attempt records the exact terminal-database members omitted from the extracted Python home, prefers the platform wheel, uses symlinked private venv interpreters, and explicitly selects installed GCC. The complete original Python archive is preserved. MACS2 2.2.7.1's `float(sys.version[:3])` setup check misreads Python 3.11 as 3.1, and its malformed original numpy requirement needs the recorded older packaging toolchain; neither upstream source issue was silently edited.

All installation/download work used explicit prior user authorization and new private workspace paths. The extracted Python home is shared read-only by the three private venvs; their dependencies are installed separately. These developer scripts depend on recorded workspace intake and are **not a general portable end-user installer**. Preserve source/runtime homes and prior attempts; do not run reference scripts on a study dataset and assume workflow admission.

## Actual original controls

Each edition runs the same synthetic 6,600-record population, split into two source files with declared cell groups. Eight calls succeed: pooled BED, equivalent gzip BED, altered read-support values, pooled BEDPE, broad peaks, group A, group B and a stringent low-signal case with no significant peaks. A ninth empty-input call returns the original nonzero status. Thus the matrix contains **27 actual calls: 24 successful source outputs, including three valid empty results, and three expected source failures**.

Pooled narrow/BED, pooled BEDPE and broad calls each return three peaks; group A and B each return two. Gzip and altered-support peak tables match their edition's pooled BED table byte-for-byte. BED and BEDPE tables differ, demonstrating that the mode distinction is exercised. Raw peak files, peak spreadsheets, summits/gapped peaks where generated, exact commands, stdout/stderr, package fingerprints and original failure traces are retained. No Java statistical replay, biological-accuracy certificate or complete paper reconstruction is implied.

The [checkpoint](signac-peak-runtime-development/VALIDATION.json), [warnings](signac-peak-runtime-development/WARNINGS.md) and archive preserve all reference calls and setup receipts. Full runtime-home binaries remain in the local private paths identified by the checkpoint; their published source archives and installed fingerprints are retained. Hashes establish integrity, not signatures or authentic execution. The native preparation checkpoint, canonical executable and catalog remain separate. No root command, additional catalog credit, full project test suite, release, GitHub push or deployment is claimed.

Primary sources: [Signac's CallPeaks contract](https://stuartlab.org/signac/1.13.0/reference/callpeaks), [MACS2 2.2.7.1](https://pypi.org/project/MACS2/2.2.7.1/), [MACS2 2.2.9](https://pypi.org/project/MACS2/2.2.9/) and [MACS2 2.2.9.1](https://pypi.org/project/MACS2/2.2.9.1/).
