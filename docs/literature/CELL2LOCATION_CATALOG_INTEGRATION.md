# cell2location: original workflow and six paper roles

Use the original **cell2location 0.1.3 Linux/WSL CPU workflow** through `tools/literature/cell2location013/run.py`. It fits reference signatures and spatial abundance with the unchanged upstream models, exports posteriors, uses official save/load and supports the original NMF wrapper. This is a Python source entry point with a separate private environment; it is not a command in the released JAR.

The admission adds **one scoped upstream integration and six individual source reviews**: **110 new integrations, comprising 20 native Java subsets and 90 upstream workflows; 113 including three existing adapters.** The fixed catalog retains 250 papers, 432 named families and 2,671 observations. It has 1,162 reviewed uses, 1,509 awaiting review and 263 missing families. The full review and implementation goal remains open. Every other family, observation and prior review remains unchanged.

## Install and run

Run from the project root in a permitted Linux/WSL x86-64 shell, using NEW paths:

```sh
python3 tools/literature/cell2location013/install.py \
  --allow-install --allow-download \
  --prefix build/my-new-cell2location-runtime \
  --out build/my-new-cell2location-setup

python3 tools/literature/cell2location013/make_example.py \
  --provider build/my-new-cell2location-setup/provider \
  --out build/my-new-cell2location-example

python3 tools/literature/cell2location013/run.py \
  --request build/my-new-cell2location-example/request.json
```

The setup installs a private Python 3.9.25/Torch 2.0.1+cpu/scvi-tools 0.20.3/Pyro 1.8.6 environment. It creates no global Python executable links and runs no model. Use an existing permitted captured provider directly when available. A READY provider is not an analysis result. Analysis installs nothing. Read the [full commands, request contract, source roles and recovery guide](CELL2LOCATION013_DEVELOPMENT.md) before supplying real data or another trusted script.

The complete synthetic example retains all 48 reference cells, 16 locations, 24 genes, three literal cell types and both NMF factor settings with two restarts each. Its short 32/64 training epochs and 20 posterior samples establish integration; they do not infer author parameters, convergence or biological validity. Every original declared output remains retained.

## Warnings and required actions

**P096 reports 0.7a0. Its historical-edition analysis remains skipped because that runtime is not qualified by 0.1.3.** Other unreported editions and author settings stay unknown. Do not treat the example as a paper reproduction or substitute its data, settings, input layers, covariates, aggregation operator or score field for an author's choices.

**SKIP3 skips dependent analysis** when an exact permitted prerequisite, installation/download or execution is unavailable. Supply only permitted requirements and retry NEW paths. Original execution on native Windows skips; use the Linux/WSL profile.

**FAIL2 admits no complete result.** Retain every complete/partial object and result, raw worker report, original warning/error and full console. Correct the actual cause and retry NEW paths without dropping failed cells, genes, locations or factor settings. Incomplete condition totals are unavailable, never zero: `condition_counts_complete` is false, the totals are JSON `null`, and `warning_review_required` is true.

Before using results, read `WARNINGS.md`, `analysis-status.json`, complete `process.stdout` / `process.stderr`, `diagnostics/conditions.json`, raw `diagnostics/worker-status.json`, `SUPERVISOR_ERROR.txt`, `WORKER_DIAGNOSTIC_ERROR.txt` when present, and `MANIFEST.json`. If recording is blocked, keep the complete console and resolve the named filesystem requirement. Trusted scripts run with account permissions; owned worker supervision is not an OS sandbox or total CPU/RSS enclosure.

The example retains 17 delivered upstream warnings and five warning/error log records, including undefined NMF diagnostic divisions, explicit CPU/JAX messages and headless plotting warnings. All retained result arrays were finite, which does not make an undefined diagnostic informative. Review the original plots and conditions. The profile establishes CPU execution, not GPU execution.

## Source roles and qualification limits

All 250 exact primary XML identities were checked. Nine named paragraphs support the six existing cell2location observations, including the methods anchors and separate results/figure context. P100 explicitly reports 0.1.3; choosing it for P005, P008, P070 or P099 is a caller choice because their editions are unreported. P096's reported 0.7a0 remains separate.

P096's paper reports raw reference counts, while the author script's filename contains `log_normalised`; the filename does not establish the actual matrix transformation. The paper describes an NMF range of 5–30, while author Script26 uses `np.arange(5,30)`, generating 5–29. Both remain recorded. P005's printed covariate shorthand is not automatically translated into another API or stage. P099's aggregation operator and exact score field remain unknown. Adjacent image alignment, segmentation, aggregation and biological conclusions remain separate operations.

All 17 independent stage snapshots, 4,658 graph records, 647 arrays and 65,212 elements matched direct upstream execution at tolerance zero. The complete 684-file output population, all NMF models, original conditions and RNG states remain retained. Qualification includes 41 Linux workflow controls, a Windows execution refusal, seven monitor controls, ten setup refusal controls, ten comparator refusals and five frozen-verifier refusals. The full public installer was exercised in a fresh private environment before the complete example was fitted and compared.

An earlier recorder reused references across stages and could miss a later mutation; its scope, all old arrays and a reproducing control remain preserved. The current recorder independently snapshots each stage while retaining cycles/aliases inside it. No original statistical algorithm, failed population or saved proof was rewritten. Opaque pickle/PDF byte identity and generated UUID identity are not claimed. The [component evidence](cell2location013-development/VALIDATION.json) is source-bound; SHA-256 establishes integrity, not authenticity or biological validity.

Verify this admission and all earlier evidence gates without installing or fitting models:

```sh
python3 tools/literature/cell2location_catalog/verify.py \
  --rescan-sources \
  --out build/literature-review/monocle-runtime/my-new-cell2location-catalog-check
```

Earlier gates receive only their exact eight historical catalog metadata inputs. Scientific source, inputs/results, archive contents and verifier definitions remain live and unchanged. Cell Ranger and Cell Ranger ARC remain excluded by user request; rstatix recovery, ggpubr statistical-layer eligibility and all other existing tool restrictions remain explicit.
