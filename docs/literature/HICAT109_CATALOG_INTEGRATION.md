# HICAT 1.0.9 catalog integration

This admission covers the selected original modified-DoubletFinder scoring
workflow in P059 and a separate Java strict-score selector. A verified admission
records **117 scoped new integrations: 20 native Java subsets and 97 upstream
workflows**, or 120 including three existing adapters, with **262 named families
still missing**. The 250 papers, 438 families, 2,679 observations, 1,180 reviewed
uses and 1,499 unreviewed uses are unchanged. It adds one upstream family and
zero native families. The full research and implementation goal remains open.

The authoritative admission is `omics-2023-2026/hicat109-catalog-validation.json`
after the complete verification command below passes. This guide alone does
not establish an admission or a live-site deployment.

## Paper use and uncertainty

P059, [PMC10719114](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719114/), DOI
10.1038/s41586-023-06812-z, reports modified DoubletFinder in HICAT 1.0.9 and
removal of cells with scores **strictly greater than 0.3**. All four anchors
remain: paragraph 85 is the current method; 87, 88 and 91 identify the HICAT
predecessor of BIGCAT and do not establish additional current HICAT analyses.
The 62 housekeeping genes describe a separate QC score, not an established
doublet-scoring gene panel.

The selected unchanged `doubletFinder` function is from prepublication commit
`24a7cd41cc6bdca0b624dc0a3cb74fbc9f75b9e4`, labelled 1.0.9. The authors' actual
commit, exact function, gene panel, caller options, seed and environment remain
unknown. The selected source, R 4.2.3 dependency profile and synthetic controls
are project choices. No primary reported setting, edition, uncertainty, source
hash or paper identity changes. Only the existing observation/review coverage
note changes.

## Installation, execution and Java API

The [workflow guide](../../tools/literature/scrattchhicat109/README.md) supplies
exact install flags, offline cache filenames, trusted RDS constructor recipes
and runnable commands. Use Linux or permitted WSL and the exact private source
profile. The installer builds unchanged original HICAT and RANN privately and
audits complete installed namespaces, native DLL paths and runtime inventories.

```sh
python3 -B tools/literature/scrattchhicat109/install_runtime.py \
  --manager /permitted/pixi --allow-install --allow-download \
  --cache-dir /permitted/package-cache --out /private/NEW-hicat-install

python3 -B tools/literature/scrattchhicat109/run.py \
  --runtime /private/completed-provider --request /trusted/request.rds \
  --out /results/NEW-analysis
```

Where downloads are forbidden, omit `--allow-download` and provide both the
exact original source cache and a complete permitted package cache. Where
installation is forbidden, use an existing exact permitted provider or skip
the dependent analysis. The analysis command never downloads or installs
software. RDS inputs must be trusted; this is not a code sandbox.

The [Java API](../../tools/literature/doublet_score_threshold/README.md) applies
the reported cutoff to already computed scores, preserving original row order.
Equality is retained, and NaN in either comparison operand yields UNKNOWN.
UNKNOWN is not a retained cell. The Java code computes no HICAT score and
contains no source-derived native HICAT scoring implementation. This source
adapter and API are not a root Java CLI subcommand. Standard DoubletFinder
pANN and BIGCAT marker-doublet detection remain separate methods.

## Prominent warnings and required action

**SKIP3 skips dependent analysis.** Read the named missing or forbidden exact
source, runtime, cache, installation, download or Linux requirement. Provide
that permitted requirement and retry NEW writable paths. A skip is not a
complete result or zero doublets.

**FAIL2 admits no complete result**, including a recording failure after an
original return. Only the final public-runner `COMPLETED.json` admits that
recorded operation. Preserve every original/partial file. Read `WARNINGS.md`,
`FAILURE.json`, `original/R-status.json`, the complete original conditions and
all raw consoles. For source errors, `error.message` in the original status
contains the unmodified R message. Resolve the named input or filesystem cause
and retry NEW paths. No source repair, identifier correction or runtime fallback
occurs; a leftover RDS or successful R process is insufficient.

Default plotting is FALSE. Original plotting, dimension-drop, too-few-neighbour,
missing-gene, nonfinite-input and warning-as-error failures remain failures.
The dense zero-count-column control fails while its sparse counterpart returns.
An expected-error comparison passing supplies no result for that input. Original
help and native compiler warnings remain retained; unavailable native diagnostic
totals are not zero.

**Upstream source/runtime stay private.** HICAT DESCRIPTION labels GPL-3 while
LICENSE contains different Allen Institute terms with a noncommercial
redistribution restriction. Both are preserved without resolving the labels.
The current intended use is educational, research or nonprofit. The project
bundles its own adapter, independent comparison rule, source identities and
synthetic scientific records; it bundles no original source tree, archive,
installed library or runtime binary. Complete native compilation consoles remain
private because they contain source excerpts; their whole-file hashes and paths
are recorded. Complete scientific objects, analysis conditions, RNG and raw
analysis consoles remain retained in the public numeric/control bundle.

## Evidence and cumulative verification

The [workflow qualification](scrattchhicat109-workflow-validation/QUALIFICATION.json)
and [replay instructions](../../tools/literature/hicat109_validation/README.md)
cover 26 original controls: 14 complete returns and 12 original failures.
Whole original values, names, conditions/errors and RNG are compared, including
10,001 real cells, dense/dgC/dgT inputs and source failure boundaries. Both public
install branches and actual installed analyses passed, along with 15 Linux
public-runner guards and a Windows prerequisite skip. The packaged Java class
matches 17,697 literal R comparisons and 13,824 successful original scores on
Windows and Linux, and the permanent project JUnit test passed.

The full logical bundle has 1,337 records, 636 unique payloads and 36 qualified
source identities. Its original 368 scientific/provider RDS files contain no
detected upstream HICAT function bodies, saved environments or external pointers.
Two unchanged copies were accepted and six paired fabricated scientific copies
rejected after logical inventory resealing. Those semantic tests do not claim
ZIP-envelope resealing. Complete Windows readback and existing-output refusal
preserved every reconstructed record.

```sh
python3 -B tools/literature/hicat109_catalog/catalog.py \
  --verify --rescan-sources \
  --hicat-runtime /private/hicat-provider \
  --runtime2023 /private/bigcat-2023-provider \
  --runtime2022 /private/bigcat-2022-provider \
  --arrow-runtime /private/arrow-provider \
  --mapping-runtime /private/mapping-provider \
  --doubletfinder-runtime /private/DoubletFinder-provider \
  --out build/literature-review/monocle-runtime/NEW-hicat-catalog-check
```

On Windows add `--wsl-distro NAME`. Use an isolated linked Git worktree and
the historically qualified legacy CLI for preceding gates. Preserve other
development builds. The audit verifies all 476 cumulative definitions and every
preceding scientific gate, rescans all 250 primary sources and makes 26 fresh
original synthetic HICAT calls. It also checks both Java oracles. It installs
nothing. The exact eight predecessor metadata files may be viewed at their
previous state; scientific overlays are forbidden. The earlier six-literal-help-
line fixture remains confined to the isolated worktree and restores the complete
current CLI source afterward; its old code and hashes are unchanged.

HICAT predecessor clustering, whole-package behavior, manual biological decisions
and paper reproduction remain unqualified. PyArrow remains separate and missing.
All earlier BIGCAT, mapping, Arrow and DoubletFinder limits remain retained.
Cell Ranger/ARC remain excluded by user request. Publication requires a separate
authorized push; this guide does not establish one.
