# SingleR annotation: releases, commands and recovery

This project supplies complete original SingleR annotation for explicitly
selected releases **1.2.4, 1.8.1, 2.2.0, 2.6.0 and 2.8.0**. It uses unchanged
source-built upstream R packages through a Python source entry point. The
project's native Java toolbox remains available separately. These controls
qualify the stated annotation scope; they establish no whole-package validity,
biological accuracy or reproduction of a paper.

**P233's literal `2.41` is unresolved. The dependent analysis is skipped.**
Do not change that spelling to 2.4.1 or silently choose another release.
Obtain original source/version clarification before claiming that paper's
SingleR analysis. The source reading records the uncertainty prominently.

## Run an annotation

Use an explicitly permitted Linux/WSL environment, permitted Pixi executable
and **NEW** private installation path. This downloads only when authorized by
the explicit flag:

```sh
python3 -B tools/literature/singler_workflows/install_runtime.py \
  --edition 2.8.0 --manager /permitted/pixi \
  --allow-install --allow-download \
  --cache-dir /permitted/package-cache --out /private/NEW-singler-install
```

For offline installation, omit `--allow-download` and supply both a complete
`--cache-dir` and exact `--source-cache`. Do not run installation where it is
forbidden. An unavailable requirement causes SKIP3 and skips dependent analysis;
an installation completion marker alone is not an annotation result. The
installer's `COMPLETED.json` names the resulting provider, a sibling ending in
`-provider`. Do not modify or refresh a completed provider in place.

Supply original query/reference expression objects and their label vector.
Genes occupy rows and cells/reference samples occupy columns. Make the trusted
request with the selected runtime, using the complete example in the
[workflow guide](../../tools/literature/singler_workflows/README.md#create-a-trusted-request).
References and labels are explicit caller inputs: analysis downloads no online
reference and supplies no replacement for an author's reference snapshot.

```sh
python3 -B tools/literature/singler_workflows/run.py \
  --edition 2.8.0 --runtime /private/NEW-singler-install-provider \
  --request /trusted/request.rds --out /results/NEW-annotation
```

Read `original/complete-original-return.rds`, the complete original conditions,
RNG and raw consoles. A **final public-runner `COMPLETED.json`** admits that
recorded analysis. A successful R process or leftover RDS does not. RDS requests
are trusted executable R inputs; this adapter is not an untrusted-code sandbox.
For separate train/classify arguments, the guide exposes original training and
classification in the **same process**. Saved external pointers remain records;
reading them in another process does not restore a live native index.

## Source-specific uses

| Paper | Reported use and limits |
| --- | --- |
| [P051](https://doi.org/10.1038/s41586-025-09807-0) | Per-dataset NeuMap annotation against Immgen without a release at that anchor; a separate 2.8.0/ImmGen context; another 2.6.0 context with the literally described MouseRNAseq ImmGen references via celldex 1.14.0. The human-neutrophil subsection and reference wording are preserved. |
| [P082](https://doi.org/10.1038/s41588-024-01999-x) | Spearman-based matching to healthy bone-marrow CITE-seq references, with 30,672 RNA profiles and 25 antibodies. A study inventory declares 2.2.0; its duplicate under Associated Data is not another analysis or evidence for hidden arguments. |
| [P192](https://doi.org/10.1016/j.ajhg.2024.10.001) | KOBS 1.2.4 uses prior references; RYSA 1.8.1 separately uses the Emont adipose atlas. |
| [P233](https://doi.org/10.1016/j.ajhg.2025.07.018) | Literal 2.41 remains unresolved. The privately read author notebook has HPCA and Blueprint/ENCODE main/fine calls, saved pruned labels, observed filters using unpruned labels and separate Seurat PBMC mapping. Its actual executed commit, release and reference snapshots are not established. The notebook is not executed or redistributed. |

The comparison environments and synthetic references are project choices.
Do not borrow releases, labels, reference objects, caller options or biological
decisions across analyses.

Three additional P051 software families are cataloged **as missing**: celldex
reference retrieval, SPOTlight 1.0.3 seeded-NMF spatial deconvolution with a
predicted neutrophil fraction **>=10%**, and BD Rhapsody analysis-pipeline
processing (local 1.9.1 plus a separate unversioned context). Companies named
Celldex/Spotlight Therapeutics, an ordinary use of “spotlight” and instrument-only
Rhapsody descriptions do not establish extra software uses. ImmGen is a
biological reference. None of these three families receives implementation
credit from the SingleR adapter. Cell Ranger/ARC remain excluded by user request.

## Warnings and required action

**SKIP3 means the dependent analysis was skipped.** Read the named missing or
forbidden exact source, reference, runtime, Linux, cache, download or installation
requirement. Supply that permitted requirement and retry NEW writable paths.
The unresolved 2.41 release requires source clarification; a different release
does not resolve it. A skip is not an annotation or empty biological result.

**FAIL2 means no complete result is admitted.** Preserve all original and partial
files. Read `WARNINGS.md`, `FAILURE.json`, `original/R-status.json`, complete
conditions and both raw consoles. Native crashes can prevent later RNG/status
recording; absent records are unknown, never zero. Resolve the stated original
input or filesystem cause and retry NEW paths. Recording failures after an
original return also admit no complete result.

Original errors and warnings remain: label-count mismatch, nonoverlapping genes,
unsupported bare gene vectors, 1.2.4's unsupported `restrict`, scalar-center
deprecation warnings and warning-as-error behavior. The qualified 2.8.0 custom
assay case fails looking for `logcounts` despite supplied assay arguments. Check
the exact original release's interface; no implicit assay extraction, identifier
repair, reference substitution or warning-policy fallback is performed.
See the [full warning table](singler-workflow-development/WARNINGS.md).

The first 1.2.4 dependency binary crashed on finite row medians as well as
annotation. That failed environment is retained and unqualified. The current
profile rebuilds the **same unchanged DelayedMatrixStats 1.10.0** against the
selected matrixStats 0.62.0, restoring original method delegation without
changing algorithms or versions. GenomeInfoDbData is installed explicitly;
package-manager completion alone did not install this required data package.

## Evidence and verification

The [workflow qualification](singler-workflow-validation/QUALIFICATION.json)
contains complete records for 250 original/worker comparisons: 232 complete
returns and 18 original errors, 50 controls per release. Whole objects include
scores, labels, attributes, captured environments and function bodies, conditions
and RNG. Comparison uses whole version-3 serialization; fields and environments
are never discarded. Two unchanged copies passed and six scientific/environment
changes were rejected. A captured S4 delegate change was rejected even with
unchanged method code. Seventeen actual Linux public-runner guards, three
Windows prerequisite guards, both public installation branches and both complete
installed-runtime analyses passed. These checks claim no ZIP-envelope resealing
or live pointer identity.

For complete record readback without R or installation:

```sh
python3 -B tools/literature/singler_validation/records.py \
  --out build/NEW-singler-record-readback
```

For fresh synthetic original replay, provide all five permitted exact providers:

```sh
python3 -B tools/literature/singler_validation/replay.py \
  --runtime 1.2.4=/permitted/provider-124 \
  --runtime 1.8.1=/permitted/provider-181 \
  --runtime 2.2.0=/permitted/provider-220 \
  --runtime 2.6.0=/permitted/provider-260 \
  --runtime 2.8.0=/permitted/provider-280 \
  --out build/NEW-singler-fresh-replay
```

Fresh replay regenerates all complete requests and makes 250 new original calls,
comparing both captured original channels, five public successes, both installed
analyses and retained original outcomes from post-return recording failures.
It explicitly supplies the captured synthetic Annoy directory to the original
constructor so entire request bytes, including that field, can be checked.
The initial directory-mismatch failure and both complete requests are retained;
read the [regeneration disclosure](singler-workflow-development/INPUT-REGENERATION.md).
Saved training pointers are retained but internal live-index identity is not
claimed. No runtime/reference installation occurs during replay.

The cumulative catalog check reads the admitted complete evidence, checks all
479 registry definitions and exact predecessor/source identities, and optionally
rescans all 250 original XMLs. It performs **no fresh original scientific calls**:

```sh
python3 -B tools/literature/singler_catalog/catalog.py \
  --verify --rescan-sources --out build/NEW-singler-catalog-check
```

Preceding scientific programs, verifiers and evidence remain unchanged relative
to the qualified main 96fc65d3. Their earlier scientific replay is not claimed to
have been repeated by this new integrity check. To repeat earlier scientific
computations, use each preceding workflow's documented verifier and original
permitted providers. The exact eight predecessor catalog metadata files are
retained; an inverse applies only to reviewed-use metadata, never scientific code.

The resulting catalog has **118 scoped new integrations (20 native Java and 98
upstream), 121 including three existing adapters, and 264 missing families**.
It has 441 named families, 2,682 observations, 1,187 reviewed uses and 1,495 uses
still awaiting review. The full semantic review and implementation goal remains
open. This local qualification does not establish a GitHub push or deployment.
