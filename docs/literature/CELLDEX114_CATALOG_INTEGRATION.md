# celldex 1.14.0: reference retrieval and catalog scope

The project runs complete unchanged original celldex **1.14.0** reference
retrieval calls in an explicitly locked comparison environment. Start with the
[runtime, trusted-request and cache guide](../../tools/literature/celldex114/README.md)
for copyable commands. This is a Python source entry point using original R
packages, not a root Java CLI subcommand.

P051 [DOI 10.1038/s41586-025-09807-0](https://doi.org/10.1038/s41586-025-09807-0)
reports celldex 1.14.0 and literally describes **MouseRNAseq ImmGen databases**
for reference-based SingleR annotation. Exact reference functions, snapshots,
gene-ID options and the author environment remain unknown. The human-neutrophil
subsection and reference wording remain unchanged. Project tests of
`ImmGenData`, `MouseRNAseqData` and dated references are explicit project
choices. They are not recovered author settings. SingleR annotation and
SPOTlight deconvolution remain separate operations.

## Install and retrieve where permitted

Use permitted Linux/WSL, an existing permitted Pixi executable and NEW paths:

```sh
python3 -B tools/literature/celldex114/install_runtime.py \
  --manager /permitted/pixi --allow-install --allow-download \
  --cache-dir /permitted/package-cache --out /private/NEW-install
```

For offline installation, omit `--allow-download` and provide complete exact
package and source caches. If installation is forbidden, use an existing
completed provider or skip dependent retrieval. Runtime installation retrieves
software; it does not retrieve reference datasets. The completion record names
the provider, a sibling ending in `-provider`.

Use the selected runtime to create the trusted request RDS shown in the workflow
guide. For offline legacy/ontology or Ensembl retrieval, explicitly select the
original Hubs' local options before saving it:

```r
request$hub_options <- list(
  ExperimentHub = list(LOCAL = TRUE),
  AnnotationHub = list(LOCAL = TRUE)
)
saveRDS(request, "/trusted/NEW-local-request.rds", version = 3)
```

These options are recorded caller choices. The original Hub defaults can still
contact remote services even with cached data. The adapter does not silently
change those defaults. `LOCAL=TRUE` requires complete intended Hub cache files,
including database/index and referenced resources.

```sh
python3 -B tools/literature/celldex114/run.py \
  --runtime /private/NEW-install-provider \
  --request /trusted/NEW-local-request.rds \
  --reference-cache /preserved/reference-cache --out /results/NEW-reference
```

Add `--allow-download` only where reference networking is permitted. Without
that flag, the R child runs in a network namespace; unsupported isolation causes
SKIP3. The original seed cache remains unchanged and a complete copy is retained
under the NEW output. Analysis never installs runtime software. RDS requests
are trusted executable inputs, not an untrusted-code sandbox.

## Warnings, results and recovery

**SKIP3 skips dependent retrieval** when a named exact source, runtime,
dependency, reference, network-isolation, download or installation requirement
is unavailable or forbidden. Supply that permitted requirement and retry NEW
paths. **FAIL2 admits no complete result**, including original source errors,
native crashes and post-return recording failures. Read `WARNINGS.md`,
`FAILURE.json`, `original/R-status.json`, complete conditions/RNG and both raw
consoles. Preserve originals/partials, resolve the stated input/filesystem cause
and retry NEW paths. Only final public-runner `COMPLETED.json` admits that
recorded operation; an RDS file or successful R exit is insufficient.

Original R warnings are displayed and written prominently to `WARNINGS.md`.
Ensembl conversion can warn and drop unmapped/duplicate mapped genes under the
original code. The tested mouse conversion reported 2,180 unmapped IDs out of
21,214 requested; that is a test observation, not a universal rate or paper
result. No identifier repair, imputation, warning-policy change or substitute
reference is added. Raw/native diagnostic totals are not inferred from R
warning counts. Original unsupported ontology values/arguments, missing reference
versions and missing local subpaths remain errors without results.

**Lazy returns need their backing files.** Keep the complete owned cache at its
recorded paths. Serialization alone does not embed every lazy file or restore
live external pointers. For portability, explicitly choose the original
`realize.assays=TRUE` behavior before calling `fetchReference`; the adapter does
not silently realize or rewrite returned objects.

## Evidence and verification

The [workflow qualification](celldex114-reference-validation/QUALIFICATION.json)
covers 16 independent original/worker comparisons with explicit LOCAL options:
**12 complete returns and four original errors**. Entire objects, labels, IDs,
conditions and RNG matched, including independently materialized lazy assays.
Earlier offline runs with unchanged Hub defaults had **seven returns and nine
original errors**, including five additional Hub failures. Those complete
records remain retained; matching an error does not establish a successful
reference retrieval. The corrected controls require every intended positive
case to return.

Twelve actual Linux public-runner guards and two Windows prerequisite checks
passed. Both actual public installer branches passed, followed by actual
installed-runtime analyses compared with independent complete original calls.
The download-enabled installed analysis began with an empty cache and retrieved
the original reference. Source/runtime/input tampering, forbidden installation,
missing references/network permission, timeouts, missing status after a zero
exit and post-return recording faults admit no result. Original source and
dependency algorithms are unchanged.

The official 1.14.0 source is from Bioconductor 3.19. R4.4.3 and Bioconductor3.20
dependencies are explicit project comparison choices. The Conda1.16.0 recipe
resolves dependencies; the active target is source-built1.14.0 and its complete
namespace is audited against the original source. This is not an author runtime
recovery or validation of every celldex function.

**All complete biological reference records remain local and unchanged.** The
public index identifies 4,469 retained files, including full original returns,
conditions/RNG, cache/backing files and partials. Full private-file readback
passed. No reference matrix is redistributed or assigned the software's license;
the original software source archives retain their upstream notices. Hashes
establish content identity, not an upstream signature or biological accuracy.

Check source and public receipt integrity without R or installation:

```sh
python3 -B tools/literature/celldex114_validation/records.py \
  --out build/NEW-celldex-record-check
```

In the permitted Linux environment holding the complete retained records, add
`--private-records` to read every indexed private file. Missing exact records
skip that dependent verification; they are not an empty dataset or a new
scientific result. To perform fresh original computations, supply the exact
permitted completed provider and complete reference cache:

```sh
python3 -B tools/literature/celldex114_validation/controls.py \
  --runtime /permitted/completed-provider \
  --reference-cache /preserved/complete-cache --hub-local \
  --out /results/NEW-independent-controls
```

The cumulative catalog check verifies all479 registry definitions, exact
predecessor evidence, workflow receipts and primary source roles. It does not
perform fresh original scientific calls or automatically read all private data:

```sh
python3 -B tools/literature/celldex114_catalog/catalog.py \
  --verify --rescan-sources --out build/NEW-celldex-catalog-check
```

The resulting catalog has **119 scoped new integrations: 20 native Java and
99 upstream; 122 including three existing adapters. 263 families remain missing.**
The 250 papers, 441 families, 2,682 observations, 1,187 reviewed uses and 1,495
unreviewed uses remain unchanged. Only one existing coverage note changes.
P233's SingleR2.41 remains unresolved; Cell Ranger/ARC remain excluded. Earlier
source/license/runtime limitations remain retained. Whole-tool validity,
biological accuracy, paper reproduction and the full goal remain unqualified.
This local qualification does not establish a GitHub push or deployment.
