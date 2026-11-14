# DoubletFinder: catalog coverage and required actions

**SCT convergence is not qualified.** The exact synthetic control produced
3,706 original R warnings, including 12 iteration-limit warnings, and 17 messages.
Read complete original conditions and raw consoles. Do not round artificial
counts, suppress warnings or treat matching source output as a converged fit.
Changing an estimator or dependency requires a new runtime/output and new
original controls. Preserve every previous input, result and failed run.

The catalog retains 250 papers: 71 Nature, 104 Nature Genetics and 75 AJHG,
published within the fixed 2023-10-23 through 2026-10-23 window. No extension was
needed to obtain 250. There are 438 named families, 2,679 observations and 1,180
individually reviewed uses; 1,499 uses await review. There are 113 scoped new
integrations (20 native subsets and 93 upstream workflows), or 116 including
three existing adapters. **266 named families remain missing. The full
semantic-review and implementation objective remains open.**

## Run the selected original workflow

This is a Python source entry point with an exact permitted Linux/WSL R provider.
It is not a bundled root Java CLI command. The selected DoubletFinder 2.0.6,
R 4.5.3 and Seurat 5.5.1 environment is a project profile, not a recovered author
environment. The adapter accepts complete trusted RDS requests and executes
unchanged original functions. Do not deserialize untrusted RDS requests.

Follow the [runtime installation instructions](../../tools/literature/doubletfinder206/README.md)
and [complete synthetic example](../../examples/doubletfinder206/README.md).
Installation/download requires the documented explicit flags and permitted
operating-environment access. Analysis performs no installation. A missing or
forbidden prerequisite produces **SKIP3**, and the dependent analysis is skipped.
Native Windows original analysis and installation skip; use a permitted
Linux/WSL provider. Nothing requires installing into a shared R environment.

After provisioning the provider, extract the self-contained inputs and run the
log-normalized example (replace paths with new writable locations):

```sh
python3 -B examples/doubletfinder206/prepare.py --out /path/to/NEW-examples
python3 -B tools/literature/doubletfinder206/run.py \
  --runtime /path/to/NEW-install/provider \
  --request /path/to/NEW-examples/requests/lognorm-default-pN.rds \
  --out /path/to/NEW-fit
```

Read `analysis-status.json`, `WARNINGS.md`, `MANIFEST.json`, all original
conditions and complete stdout/stderr before using any output. **FAIL2 admits
no complete result**, including a failure to record the terminal status after
the original computation. A leftover RDS or figure is not success. If recording
is blocked, retain the full console and `FINALIZATION_ERROR.json` where writable,
resolve the named filesystem cause and retry a **new** writable output directory.
Unknown raw/native diagnostic totals are unavailable, never zero.

The [native Java component guide](DOUBLET_FINDER206_NATIVE_COMPONENTS.md) covers
`DoubletFinder206.modelHomotypic`, `findPk` and `classifyExistingPann`.
Classification uses already supplied scores; these components do not fit a new
doublet model. Their three operations add zero extra native family credits.

## What each paper establishes

| Paper | Source role | Remaining limit |
| --- | --- | --- |
| P053 | Removes the highest-scoring 10% per sample or reaction in two workflows. | Edition, pN, pK, DF PC subset, rounding and ties are unreported. Downstream graph PCs are not inferred DF arguments. |
| P059 | Modified DoubletFinder in scrattch.hicat 1.0.9; removes scores strictly greater than 0.3. | This separate modified workflow remains missing; original 2.0.6 does not qualify it. |
| P096 | Removes DoubletFinder UNION (DoubletDecon INTERSECTION Scrublet). | Paper reports DF 3.0, DoubletDecon 1.1.5 and Scrublet 0.2. DF 3.0 remains unresolved. The earlier Scrublet summary 0.0.2 is corrected, without changing a selected runtime. |
| P124 | Removes doublets before combining donors/treatments. | DF edition and complete options are unreported; guide-RNA thresholds are separate. |
| P192 | After demuxlet singlets, uses pN 0.25 and per-batch pN/pK sweeps to maximize BCmvn. | Edition, DF PCs, actual per-batch pK, expected counts, homotypic handling and ties remain unreported. |

Primary locators and paragraph hashes are in
[reviews.json](../../tools/literature/doubletfinder_catalog/reviews.json).
The complete 250-XML identity/anchor census includes both dot and hyphen package
spellings. Ordinary figure arrows are classified separately from R arrow and
PyArrow. Long primary-article paragraphs stay in the ignored research cache;
published catalog descriptions are paraphrases with source pointers.

P096's [deposited author code](https://zenodo.org/records/13321265) is retained
unchanged in `author-code.zip`, with its publisher checksums, SHA-256 inventory,
record metadata and attribution. Creator: Dorit Hockman; publication date
2024-08-14; DOI 10.5281/zenodo.13321265; **CC-BY-4.0**, not relicensed as GPL.
All 29 scripts are present. They were read and checked, not executed or repaired.
Script_3.R uses legacy `_v3` calls, per-sample PCs 1:10, pN 0.25, `sct=FALSE`,
`reuse.pANN=FALSE` and an unadjusted expected count of `round(0.1*nrow(...))`;
the homotypic adjustment is computed but not passed. Its direct numeric pK
conversion and fixed metadata-column export require source reconciliation.
The selected 2.0.6 probe returns a factor; the author's actual pK type remains
unknown. `_v3` is not proof of an author package edition. Script_5.py retains its
Python syntax error at line 21. Script_6.R formats Scrublet results and does not
establish the creation of the multi-tool Boolean fusion input.

Five new families remain missing: scrattch.hicat, scrattch.bigcat,
scrattch.mapping, R arrow and PyArrow. They add seven observations across
P058/P059/P250 and no implementation credit. BIGCAT's specific clustering,
marker, constellation, mapping and imputation workflows are distinct from
generic Java methods. P058 uses Hierarchical Approximate Nearest Neighbour
mapping but reports no package edition; P059 reports scrattch.mapping 0.2.
R arrow 12.0.1 supports P059's chunked Parquet expression/DEG operations;
existing DuckDB access does not implement that R frontend. P250 only declares
PyArrow 14.0.2 as a development dependency. Its exact analysis operation is
unknown. Other MILTON requirements remain an explicit paper-wide audit queue.

## Verify the current catalog

Use a separate matching checkout for full historical gates. The previous MALT
gate requires its unchanged qualified CLI artifact; do not overwrite another
active development build or refresh old hashes to admit a different JAR.
Supply the exact permitted R provider; this gate installs nothing and fits no
new model. Full R comparison is required; portable archive hashes alone do not
qualify Seurat objects or closures.

```sh
python3 -B tools/literature/doubletfinder_catalog/catalog.py --verify \
  --rescan-sources --runtime /path/to/NEW-install/provider \
  --out build/literature-review/monocle-runtime/NEW-doubletfinder-catalog-check
```

On Windows add `--wsl-distro NAME` and provide the provider's Linux path. The
explicit WSL verification route supports checkouts on D:. For other layouts run
the verifier in Linux. Omitting a required provider skips dependent verification;
it does not produce a completed scientific qualification.

The unchanged native verifier checks 388 original reference cases and all 3,535
exported numeric/classification values. The unchanged R verifier recompares all
22 complete original objects and two PNGs, checks two original error cases and
the retained 18 actual refusal/recording controls. Full predecessor scientific
gates run through exactly eight archived catalog metadata inputs; scientific
source/result overlays are forbidden. Every other prior family, observation
and review stays unchanged except the documented Scrublet summary correction.
These controls establish the stated integration scope, not whole-tool behavior,
independent statistical validity, convergence or paper reproduction.
