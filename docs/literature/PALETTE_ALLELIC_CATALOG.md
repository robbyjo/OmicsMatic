# Palette and allelic-series catalog integration

**Current catalog: 250 papers, 432 named software families, 2,671 observations and 1,132 individually reviewed paper-family uses.** There are 106 new scoped integrations: 20 native Java subsets and 86 upstream workflows, or 109 including three existing adapters. **267 families remain missing.** The original implementation objective and full semantic review remain open. The fixed cohort contains 71 Nature, 104 Nature Genetics and 75 AJHG papers from 2023-10-23 through 2026-10-23; no extension was needed.

This successor appends two families and two individually reviewed uses. All 430 previous family records, 2,669 observations and 1,130 reviews are preserved. A fresh scan checks the identities and uncompressed XML hashes of all 250 papers. P054 contributes one named palette paragraph; P204 contributes 26 named allelic-series paragraphs. These 27 anchors support two paper-family observations, not 27 distinct analyses. No historical scientific source, verifier, original result or component qualification is rewritten.

## Start with native palette colors

The wesanderson entry gains **one native-subset credit**. It provides the exact bounded scalar palette behavior qualified against original 0.3.7: 24 immutable palette vectors, discrete/continuous colors, attributes and complete saved-result replay. Native scalar colors do not implement R graphical devices or general R-language vector/coercion rules. The separate original R provider remains available under its documented prerequisites.

From the repository root, with the tested Java 21 or 25 executable on PATH, use new output directories:

```powershell
java -jar docs/literature/wesanderson037-workflow-development/wesanderson037-native-windows21-development.jar palettes --out build/my-new-palette-list
java -jar docs/literature/wesanderson037-workflow-development/wesanderson037-native-windows21-development.jar generate --name GrandBudapest1 --n 4 --type discrete --out build/my-new-palette-colors
java -jar docs/literature/wesanderson037-workflow-development/wesanderson037-native-windows21-development.jar verify --bundle build/my-new-palette-colors --out build/my-new-palette-replay
```

The palette and four colors above are examples, not recovered author settings. Inspect `colors.tsv`, `attributes.tsv`, `analysis-status.json`, `WARNINGS.md` and the complete manifest. VERIFY replays the saved native computation; it is not a new original R fit or producer authentication. The [component guide](WESANDERSON037_WORKFLOW_DEVELOPMENT.md) documents the exact operations, original source quirks, capacities, original R installation and platform-specific artifacts. The [workflow README](../../tools/literature/wesanderson037/README.md) shows an optional source build.

P054, [The Microflora Danica atlas of Danish environmental microbiomes](https://pmc.ncbi.nlm.nih.gov/articles/PMC12823411/), reports wesanderson 0.3.7 for MFOD1 ontology colors. Its primary paragraph does not specify the palette, color count, category mapping or discrete/continuous call. The original reference R4.5.3 also differs from the reported R4.2.3 to R4.4.0. The separately named viridisLite 0.4.2 continuous gradients retain a separate role. No supplementary plotting-code audit or paper reconstruction is claimed.

The retained qualification has 6,108 complete original API cases, 224,445 returned colors and 3,397 retained original failures on each of three Java platforms. Each platform also has 72 CLI cases and 63 complete saved replays. Two original private installations, nine setup refusals and actual Windows/Linux offline native rebuilds are retained. Earlier incorrect-operation-order comparisons remain failures in the evidence archive. The component's original zero-credit/pre-admission statements describe that earlier checkpoint; this successor records the later scoped admission without modifying it.

## Choose the AllelicSeries candidate explicitly

**The reported AllelicSeries 1.1.2 edition remains missing. The tested 0.1.1.2 candidate receives zero catalog credit.** Do not remove the leading `0.` or treat the editions as interchangeable. This follows the same catalog policy as the separate Rphenograph candidate.

The [P204 paper](https://pmc.ncbi.nlm.nih.gov/articles/PMC12808971/) uses COAST-SS for quantitative lipid-trait allelic-series analysis, with UKB in-sample LD and borrowed LD in later analyses. Its upstream inputs and missing-LD choices are separate from this adapter. The method's binary/discrete-trait calibration and broader ancestry modeling remain unresolved; the reported performance benchmark is not a guarantee for this project.

The candidate supplies unchanged original R exports, including full COAST/COASTSS/SKAT-O, plus a single-process batch adapter. Native Java supplies selected summary burden components and utilities; it does not replace the full original COAST/SKAT-O implementation. The candidate entry point is Python source requiring the rest of the repository and a ready private Linux/WSL R4.5.3 provider. It is not a standalone JAR or a redistributed R prefix.

1. Follow the [provider instructions](../../tools/literature/allelicseries0112/README.md) to create a new permitted private runtime library. Installation/download flags authorize only the selected upstream sources in that setup command. Missing or forbidden prerequisites skip dependent analysis.
2. Follow [the typed batch-input example](ALLELICSERIES0112_BATCH.md). Establish the same variant/effect-allele order for effects, SEs, MAFs, annotations and LD before saving named gene argument lists. Identifiers, whitespace, case, version suffixes and ordering are preserved. The adapter supplies no silent alignment, gene grouping, annotation or LD repair.
3. Run from Linux/WSL using actual provider/input paths and a new output path:

```bash
python3 tools/literature/allelicseries0112/batch.py \
  --provider build/my-ready-original-provider \
  --function COASTSS --arguments-rds my-new-gene-requests.rds \
  --seed 97744 --maximum-calls 25000 --allow-analysis \
  --out build/my-new-allelic-series-batch
```

The seed, capacity and input filename are explicit example choices. Capacity is not a performance promise. Read every original warning, full typed return, admission reason and batch index. Quoted TSVs support literal identifiers containing tabs/newlines; RDS objects remain authoritative for types and attributes. A shared RNG stream follows input order. Changing order or partitioning stochastic requests can change results.

The retained candidate qualification compared 1,026 calls with an independent unchanged original package: 1,022 full returned objects and four original errors, with identifiers, inputs, conditions and RNG states compared. All 16 single-call exports, eight batch refusal controls, five scalar/real-zero checks and lossless-table controls passed. Native summary burden methods matched 539 original cases and 2,623 values on Windows Java21/25 and Linux Java21. Historical failures, complete original source archives and independent controls remain retained in the two checkpoint archives. This is candidate qualification, not evidence of reported-edition identity, biological validity, a full native port or paper reproduction.

## Warnings, skips and recovery

**SKIP3 skips the dependent analysis.** Read the named cause and required actions. Supply missing source, Java compiler/runtime, private R provider, display or runtime dependencies only where permitted. Use the version-specific guide and retry a new output directory. Native palette generation requires Java and no R installation. Original AllelicSeries execution requires its qualified Linux/WSL provider. Do not bypass forbidden installation. Cell Ranger and Cell Ranger ARC remain excluded.

**FAIL2 admits no complete result.** Retain the complete original object/error, every condition, input, status, process log, index and warning. Correct the actual cause and retry a new path. Never relabel a failed/partial batch as complete or omit failed genes. Successful individual returns remain retained within a failed batch. A confirmed low-level scalar zero before association computation, or an ambiguous zero, is retained but unadmitted. A probability actually computed as zero with explicit positive SEs is preserved. Review and justify or correct every original warning. Hashes establish integrity, not authenticity or biological correctness.

Earlier ImageJ/Fiji/plugin distinctions and intensity-measurement restrictions still apply: histogram matching must not be used for intensity quantification. See [the previous image-analysis integration](IMAGEJ_CATALOG_INTEGRATION.md). Historical totals and pre-admission component records remain explicitly historical. The canonical executable and root CLI dispatch are unchanged.

## Verify this cumulative catalog

```powershell
python tools/literature/palette_allelic_catalog/verify.py --out build/literature-review/monocle-runtime/my-new-palette-allelic-check
python tools/literature/palette_allelic_catalog/verify.py --rescan-sources --out build/literature-review/monocle-runtime/my-new-palette-allelic-source-check
python tools/literature/palette_allelic_catalog/test_catalog.py
```

The source rescan requires the exact 250 cached XMLs under `build/literature-review/xml`; missing/forbidden inputs produce a prominent SKIP3 for that dependent gate. The normal gate checks saved census and qualification evidence without new original R execution or installation. It reconstructs the exact eight previous metadata inputs and runs the unchanged predecessor gate while scientific files stay live. It also verifies the exact two-review inverse delta, complete saved palette controls, all 20,940 AllelicSeries checkpoint members, current source pins, literal reported editions and cumulative registry. No scientific-source overlay is allowed.

[Current validation](omics-2023-2026/palette-allelic-catalog-validation.json), [role coverage](omics-2023-2026/palette-allelic-role-coverage.json), [all-source census](palette-allelic-catalog/CENSUS.json) and [exact predecessor archive](palette-allelic-catalog/catalog-before.zip) provide the admission evidence. Full semantic review and implementation remain active; 1,539 observations still lack individual source-role review.
