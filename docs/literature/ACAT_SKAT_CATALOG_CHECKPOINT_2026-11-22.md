# ACAT catalog checkpoint and remaining work

The native ACAT 0.91 prepared-column combination component now has **one native
catalog family**, and 22 ACAT/SKAT/SKAT-O uses are individually reviewed. This
closes 21 pending uses and recovers the omitted P127 SKATO observation. Current
totals are **147 scoped families (27 native + 120 upstream), 1,661 reviewed uses
and 1,062 pending**, with 250 papers, 446 labels, 485 provider definitions and
2,723 observations. Including three existing adapters gives 150. There are 258
missing labels, 27 method overlaps and two zero-credit aliases. **The full goal
remains unfinished.**

Read the [paper crosswalk](ACAT_SKAT_CATALOG_INTEGRATION.md),
[native command guide](ACAT091_NATIVE.md),
[working example](../../examples/acat091/README.md) and
[admission protocol](../../tools/literature/acat_skat_catalog/ADMISSION.md).

## What is actually qualified

The native scientific source and exact JAR are unchanged from the actual clean
`build/sa-1` owner at main commit `f66c8e5e082d9a0f4fbc4c8bc22df705f325867a`.
Its qualification has 500 focused tests per normal build and packaged Windows
Java 21, Windows Java 25 and Linux Java 21 JVM, including 274 actual original
ACAT matrix cases and 192 original R column sum/mean pairs. This catalog work
rechecks the actual immutable owner's reader, source and JAR; it refits no model.
The JAR remains 197,362,611 bytes, SHA-256
`9965571a5f24f6d701b4da693d20b8c44e58c4359da54dd932476dc1ee19fd7b`.

Current catalog validation passes 32 model/transaction tests plus four actual
source-refusal cases per host. Independent Windows/Linux audits agree on all
250 XML/DOI identities, 19 candidates, 95 candidate paragraphs, one table hash
and three complete P190 supplement pages. Installed readback passes on both
hosts with the full 847-member manifest. Styled preview and installed browser
checks cover all 22 rows, coverage filters, counts and prominent warning/recovery
text at desktop/mobile sizes. Owned HTTP helpers close. Linux Python uses a
recorded Windows Git bridge solely to read Windows-owned worktree metadata;
scientific source and extraction behavior are unchanged.

All 1,639 preceding review records retain their exact serialized prefix. The
2,701 unrelated observations, 443 other inventory rows, 482 other provider
definitions, all 250 ordered paper identities and preceding manifest members
are preserved. Three spelling patterns now distinguish SKAT, SKATO/SKAT-O and
ACATV/ACATO within their existing families. No new label or duplicate family is
created by a spelling change. Current registry provider:
`acat_skat_catalog.model.registry`.

## Boundaries that determine the next implementation

P090 mentions SKAT/SKAT-O as comparators, not established executed runs. P106
uses Meta-SAIGE; P127 uses REMETA and combines rho-grid p-values with ACAT rather
than the original minimum-p rule. P152 and P207 use REGENIE, with P207 explicitly
version 3.0 and a four-test GENE_P distinct from canonical six-component ACAT-O.
P154 uses SEAK 0.4.3 and a separate REGENIE comparison. P204 reconstructs a
COAST-SS SKAT-type component from summary statistics. Keep those provider models,
calibrations and masks rather than substituting a standalone R fit.

P131 explicitly reports ACAT 0.91; the native component covers declared p-value
combination, not its FUSION/GenomicSEM workflow. P190 supplement establishes
uniform-weight Cauchy aggregation and simulation context without an edition.
P220 separates fresh SAIGE container 1.1.9 testing from downloaded AoU/Genebass
summaries; bibliography 45 is not part of the version. P221 combines only when
at least two models exist, and its signed-Z direction cannot be inferred from
unsigned p-values. P246 explicitly reports SKAT R 2.2.5 but omits the method/rho
arguments for the stated SKAT-O call. P176's expanded SKAT acronym does not
establish another independent test. Cauchy-only P123/P147/P210 do not establish
a named ACAT package; preserve P210's CDF-versus-survival-tail wording discrepancy.

## Warnings and exact recovery actions

**SKIP3: the dependent analysis or source audit did not run.** Supply the named
exact permitted executable, Java/R library, input/index, XML/PDF or audit
dependency. Download/private installation is authorized where allowed. If
installation is prohibited, retain the skip and clearly name the affected
analysis and prerequisite. The maintainer PDF audit uses pypdf 6.21.4; ordinary
native ACAT needs no R or PDF library. Cell Ranger remains excluded.

**FAIL2: no complete result is admitted.** Preserve raw, null, failed, partial
and superseded evidence and logs; resolve the named input, numerical, capacity,
process or integrity cause and retry NEW output. Do not repair NaN/Inf, endpoints,
weights or IDs silently, invent paper options, refresh old hashes or overwrite
evidence. Read `analysis-status.json` and `WARNINGS.md`. One unadmitted ACAT column
withholds the batch export. VERIFY can confirm a failed capture's consistency
without admitting its result. Numeric zero is not infinite biological significance;
completed null results do not establish positive association.

The first source freeze refused a SKAT-O-only support paragraph for the separate
SKAT package role; the unsupported link was removed. Initial Windows tests exposed
an optional historical field, and native Linux Git could not read the Windows
worktree pointer. Exact failed source/logs are retained. Passing earlier controls
and preview remain historical after notice-spacing changes; final NEW attempts
qualify current source. The earlier ACAT replay-alias failure and old 499-test
producer remain unadmitted historical evidence on sa-1. No scientific values or
historical hashes were changed to turn these failures into successes.

## Resume and priorities

Current owner: `D:/projects/JLinAlg/build/sa-2`, branch
`codex/acat-skat-catalog-20261122`, based on `f66c8e5e...` before publication.
Current evidence: `build/acat-skat-catalog-1` under that owner. Publication receipts:
`D:/projects/JLinAlg/build/git-publish/20261122-acat-skat-catalog-109/PUBLISHED.json`.

```powershell
python -B tools/literature/close_acat_skat_catalog.py --verify --out build/NEW-readback
```

Add exact primary-cache/supplement-pdf paths for a fresh source audit and the
retained `--checks-root` for actual immutable-owner rechecks. Frozen earlier
readers run on their actual unchanged owners or exact commits, not refreshed
hashes on the latest tree.

Next implement a typed standalone SKAT upstream adapter: explicit prepared
phenotype/covariate/genotype inputs, continuous/binary null model, method
(`davies` or `optimal.adj`), weights, missingness/MAF filters, imputation,
adjustment, RNG and numerical-fallback admission. Capture full original null and
result RDS, exclusions, warnings and process ownership; qualify real global
RUN/VERIFY/REPLAY from the normal packaged JAR. Existing private SKAT 2.2.5
installation and eight original synthetic fits are probes, not that adapter.
Recover P246 supplementary settings. Treat SKAT-O within the same provider as
an operation/alias rather than automatically crediting a second package family.
Then continue FUSION, GenomicRanges, g:Profiler and other high-impact gaps, plus
the remaining 1,062 individual use reviews and missed-tool recovery.

**Before machine retirement, transfer private artifacts separately.** The
[transfer inventory](acat-skat-catalog-admission/PRIVATE-TRANSFER.json) links the
exact native JAR, primary XML/PDF, extractor dependency, renders and complete
private/failed evidence to earlier runtime inventories. Preserve all 65 preceding
worktrees and this 66th owner, including the older dirty root and deliberate test
links. No private transfer is claimed. Both unfinished reports stay excluded from
Pages. There is no release, explicit Pages publication, whole-tool, whole-paper
or full-goal completion.
