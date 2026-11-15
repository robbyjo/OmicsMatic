# R Arrow 12.0.1 catalog integration

P059's existing R Arrow use now has a source workflow for local Parquet chunk
reads and lazy Dataset queries with explicit caller choices. At this checkpoint
the catalog has **115 new scoped integrations: 20 native Java subsets and 95
upstream workflows**, or 118 including three existing adapters. **264 named
families remain missing.** All 250 papers, 438 families, 2,679 observations and
1,180 reviewed uses remain; 1,499 observations await review. The full goal is open.

P059 (PMC10719114, DOI 10.1038/s41586-023-06812-z) reports R Arrow **12.0.1** for
sparse expression matrices stored in Parquet chunks of **50,000 cells by 500
genes**, and queries of chunked pairwise cluster DEG results. Primary paragraphs
87 and 91 establish those roles. Exact Arrow calls, the storage schema,
memory-map settings and query options are unreported. The primary review,
reported settings, uncertainties, anchors and source identities are unchanged.
Only the implementation coverage note changes in one observation and one review.

The selected workflow reads an ordered list of caller-declared Parquet files
into complete R data frames, or opens a local Dataset and applies explicit lazy
`filter`, `select`, `arrange` and `collect` steps. It retains original classes,
attributes, column/row order, integer64 precision and NA/NaN/Inf behavior.
It does not reconstruct the authors' sparse-matrix encoding, infer their query
choices or reproduce a biological result. PyArrow and PacBio Arrow are separate
interfaces. HICAT, BIGCAT and PyArrow remain missing.

## Setup, execution and recovery

Use the copyable commands and request examples in the
[standalone workflow guide](../../tools/literature/arrow1201/README.md).
Its pending-admission label is a byte-preserved record of the earlier component
checkpoint. [Current catalog status](../../tools/literature/arrow1201/CATALOG_STATUS.md)
and this guide establish the later coverage; its operational commands still apply.
Run inside a permitted Linux/WSL environment:

```sh
python3 -B tools/literature/arrow1201/install_runtime.py \
  --manager /path/to/permitted/pixi --allow-install --allow-download \
  --cache-dir /path/to/permitted/cache --out /path/to/NEW/arrow-install

python3 -B tools/literature/arrow1201/run.py \
  --runtime /path/to/arrow-install/provider \
  --request /trusted/request.rds --assets /local/parquet-assets \
  --out /path/to/NEW/arrow-analysis
```

When downloads are forbidden, omit `--allow-download`, supply
`--source-cache /path/to/exact-original-archive` and a complete permitted package
cache, and use a NEW installation output. Without permission to install, or
when an exact prerequisite is unavailable, **SKIP3 skips dependent analysis**.
The installer and analysis runner do not substitute another source edition.

**FAIL2 admits no complete result**, including a recording failure after a real
original return. Inspect `WARNINGS.md`, `FAILED.json`, `FAILURE.json` or
`FINALIZATION_ERROR.json`, the exit code and raw consoles. Success requires exit
0, `COMPLETED.json`, the complete original-file manifest and no failure marker.
A leftover RDS is not success. Preserve original inputs, complete RDS values and
conditions, raw logs, identifiers, NA/NaN/Inf and all available partials. Correct
the named permission, dependency, input or filesystem cause and retry NEW paths.
Use trusted RDS inputs. Analysis performs no download or installation, and does
not silently collect a Dataset when its filter is unsupported.

## Source build and evidence

The required **libarrow 12.0.1 h32ed672_2_cpu** recipe declares no source patches.
The earlier same-version **h0de2578_50_cpu** candidate declares four backports
and is blocked before R executes. Its old qualification label is overridden by
`docs/literature/arrow1201-workflow-validation/INVALIDATION.json`; its original
archive and proof remain retained. Do not refresh old hashes to admit it.

The official Apache source archive was independently downloaded and checked:
20,172,604 bytes and 6,889 regular files, SHA-256
`3481c411393aa15c75e88d93cf8315faf7f43e180fe0790128d3840d417de858`.
All 1,619 CRAN R source files and 13 loaded I/O/query functions were checked.
The C++ binary was not independently rebuilt. Its reported Git ID identifies
the conda-forge packaging commit; the upstream Arrow tag commit is recorded
separately. The selected R 4.2.3/dplyr 1.1.4 profile is a project environment,
not a recovered author environment. LZO is unavailable in this build;
LZO-dependent input requires a separately permitted and qualified runtime.

Thirteen original controls produced ten complete returns and three original
failures, including a real 50,000-by-500 tabular fixture. The wide fixture is a
project schema. Complete returns/RNG and numeric payloads match, including
signed zero and NA/NaN distinctions. Two unchanged comparator controls were
accepted and twelve altered objects rejected. Download-enabled and cache-only
installations, five real policy/recording tests and an actual UID-65534 denied
subtree test passed.

**The complete missing-column trace differs by caller context.** Twelve controls
have identical complete conditions. For the missing-column error, only its full
`trace` field differs; other fields, attributes, field order and error
class/message/call agree. Both complete errors remain retained. No field is
deleted or coerced to manufacture equality. Live Dataset/Table C++ pointer and
execution-context identity are not persisted. Raw/native diagnostic totals are
unavailable, never assumed zero. Review original conditions and every console.

The [984-member frozen qualification](arrow1201-pristine-workflow-validation/QUALIFICATION.json)
and checkpoint replay check every member and repeat all complete saved
comparisons without running new Parquet analysis or fitting models. This is
scoped tool coverage, not whole-package, biological or paper reproduction.

## Cumulative catalog audit

```sh
python3 -B tools/literature/arrow_catalog/catalog.py \
  --verify --rescan-sources \
  --runtime /permitted/arrow-provider \
  --prior-runtime /permitted/mapping-provider \
  --doubletfinder-runtime /permitted/DoubletFinder-provider \
  --out build/literature-review/monocle-runtime/NEW-arrow-catalog-check
```

On Windows add `--wsl-distro NAME`. This retains all 476 cumulative definitions
and replays every preceding scientific gate. Only eight exact catalog metadata
paths may be viewed at their preceding state. The exact inverse changes one
coverage note in reviewed-use metadata; scientific overlays are forbidden.
No prior scientific program or evidence hash changes. Use the historically
qualified legacy CLI in an isolated checkout when a predecessor requires it;
preserve another active development build. Development HTML is not a live-site
publication claim.

Other restrictions remain: Cell Ranger/ARC stay excluded by user request;
DoubletFinder SCT convergence and reported 3.0 remain unresolved, as do modified
HICAT 1.0.9, BIGCAT, PyArrow's unestablished operation and earlier recorded
runtime/source/stack/figure limitations. Mapping retains its prepared-reference
scope and private noncommercial dependency restrictions. The full semantic
review and 250-paper implementation objective remain incomplete.
