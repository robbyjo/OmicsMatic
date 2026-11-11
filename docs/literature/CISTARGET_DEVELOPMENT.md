# Native cisTarget ranked feature workflow

This development workflow computes recovery curves, area under the curve (AUC), normalized enrichment scores (NES) and exact leading-edge targets from a complete motif-ranking database. Native Java also reports missing signature features and joins explicitly supplied motif-to-TF annotations. It requires no upstream installation for TSV analysis.

**Catalog integration remains pending.** The published catalog still records 92 new scoped integrations, 95 including existing adapters, and 249 missing tools. cisTarget remains missing there. Six existing paper roles have new review drafts; a seventh resource use in P145 was missed because the Methods spell it “cis-target.” These drafts and the new observation are archived without changing the catalog or claiming whole-paper reproduction.

## Run the supplied example

Run these PowerShell commands from the repository root with Java 17 or later. Use the separate development JAR together with the recorded project dependency; the canonical executable is unchanged.

```powershell
$cisTargetCp = 'docs/literature/cistarget-development/cistarget-workflow-javac21.jar;build/cli/omicsmatic-0.3.7.jar'
java -cp $cisTargetCp org.jlinalg.cli.CisTargetWorkflowCli --help
java -cp $cisTargetCp org.jlinalg.cli.CisTargetWorkflowCli `
  --settings examples/cistarget-ranked-features/settings.json `
  --rankings examples/cistarget-ranked-features/rankings.tsv `
  --signature examples/cistarget-ranked-features/signature.tsv `
  --annotations examples/cistarget-ranked-features/annotations.tsv `
  --out build/cistarget-example-1
```

The output parent must already exist. Choose a new output directory on every run. On Linux or macOS, replace the classpath separator `;` with `:` and use shell-appropriate line continuation.

The example is a numerical toy with four motifs and a deliberately permissive NES threshold 0. Its m0 AUC is 7/15 under the Python profile. It includes one missing signature feature and one unmatched annotation to demonstrate the disposition reports. Use a complete, appropriate motif collection and justified thresholds for real analysis.

## Choose the source profile explicitly

The implementation follows the GPL [ctxcore0.2.0](https://pypi.org/project/ctxcore/0.2.0/) and archived [RcisTarget1.29.0](https://bioconductor.org/packages/3.22/bioc/html/RcisTarget.html) functions. These are declared reference editions; the selected papers do not establish those exact historical implementations.

The Python profile uses the contiguous float64 reduction order from [NumPy 1.26.4](https://github.com/numpy/numpy/blob/v1.26.4/numpy/core/src/umath/loops_utils.h.src). The adapted reduction retains the [original NumPy BSD notice](CISTARGET_NUMPY_LICENSE.txt), also included in the development JARs. R mean calculation uses a correction pass; the two conventions remain explicit.

| Setting or behavior | CTXCORE_0_2_0 | RCISTARGET_1_29_0_ICISTARGET |
| --- | --- | --- |
| auc_parameter | Fraction of all database features | Absolute raw-rank bound |
| Rounded AUC count K | Round fraction times database size, ties to even | Round absolute bound, ties to even |
| Integration cutoff | K minus 1 | K |
| AUC denominator | K times sum of present feature weights | K times number of present features |
| Recovery coordinates | 0 through recovery_bound minus 1 | 1 through recovery_bound minus 1 |
| Background SD | Population SD | Sample SD |
| Weights | Explicit finite values with positive finite sum | Every supplied weight must equal1 |
| Raw rank domain | Integral 0 through N minus 1 | Integral 0 through N, plus explicit Inf |

Raw ranks are used literally. No rank-base conversion is inferred. Both profiles use the complete supplied motif population for AUC/NES normalization and recovery-background estimation. Exact leading-edge membership includes features tied at the selected raw rank; the TSV presents targets in signature order. Original Python rank order and R lexical display order remain distinct in the evidence.

Every settings key is required; unknown keys are refused. `minimum_auc` and `minimum_nes` are finite thresholds. `strict_auc` and `strict_nes` choose `>` when true and `>=` when false. Thresholds apply after complete-background calculations. `minimum_present_fraction` must lie in (0,1] and controls the required fraction of signature identifiers present in the database. `write_recovery_curves` controls the optional full curve/background files.

For example, P047 reports strict AUC>0.01 and NES>3. Those motif-selection thresholds are different from `auc_parameter`, which controls the integration range and is not supplied by that statement. Its DMR selection and TF expression/methylation filters remain separate inputs and postfilters.

## Supply the input files

`--rankings` is UTF-8 TSV. The first header is exactly `motif`; all remaining headers are the complete database feature identifiers. Each motif is one row. Preserve every motif because filtering rows changes the normalization population. All cells, including unselected feature columns, are validated. Duplicate identifiers, ragged rows, missing ranks, out-of-domain values and malformed/nonfinite numbers are refused. Only the R profile accepts the literal `Inf` rank. Native capacity is at most 100,000 motifs, 1,000,000 database/signature features and 250,000,000 selected-rank or recovery cells; exceeding it fails without truncation.

`--signature` has exactly `feature` and `weight` headers. Each feature appears once. Missing database features receive explicit dispositions and do not enter AUC normalization. Signature order determines target presentation. Negative Python weights retain source arithmetic and produce a warning; they are not clipped or made positive. R requires unit weights for every supplied feature, including absent ones.

`--annotations` has exactly `motif`, `tf` and `evidence` headers. Supply already curated organism/collection-matched links. A header-only table explicitly requests no annotations. Exact duplicate annotation rows are refused. Multiple distinct evidence labels are retained as distinct links. Unknown motifs receive dispositions and create no TF link. The workflow does not infer orthology, motif similarity or regulatory causality.

## Convert a Feather ranking database

The optional converter uses PyArrow17.0.0 and supports Feather V1/V2 files with `genes_vs_motifs.rankings.feather` or `regions_vs_motifs.rankings.feather` orientation. It exports every motif and feature, preserves raw zero-based integer ranks and refuses score databases, transposed matrices, null cells and unsupported schemas.

Where installation is permitted, create a private Python environment:

```powershell
py -3.12 -m venv .venv-cistarget
& ./.venv-cistarget/Scripts/python.exe -m pip install pyarrow==17.0.0
& ./.venv-cistarget/Scripts/python.exe -I tools/literature/cistarget_feather_to_tsv.py `
  --database D:/data/my.genes_vs_motifs.rankings.feather `
  --max-cells 50000000 --out build/cistarget-conversion-1
```

Set `--max-cells` to an explicit capacity appropriate to the complete source matrix. Large full exports can consume substantial memory and disk. No conversion installs software automatically. Use the admitted `rankings.tsv` as `--rankings` in native Java, and retain the conversion receipt with the native run so the source Feather and TSV hashes remain traceable.

**SKIP3 means the required conversion runtime is unavailable. Skip the conversion and every dependent Feather-based analysis.** If permitted, supply PyArrow17.0.0 in a new private environment and retry new output paths. If installation is forbidden, use an already available complete TSV database or leave that analysis skipped. `WARNINGS.md` and `COMPLETED.json` name the problem and required action. Conversion alone admits no enrichment analysis.

## Read results and failures

Require exit 0, `run.json` status `PASS_NATIVE_CISTARGET_WITH_WARNINGS`, and `enriched-motifs.tsv` before interpreting results. Read `WARNINGS.md` on every run.

| File | Meaning |
| --- | --- |
| all-motifs.tsv | Every background motif, its AUC/NES, exact leading rank/recovery/excess and threshold disposition |
| enriched-motifs.tsv | Admission table containing only motifs passing both explicit thresholds |
| leading-targets.tsv | All motifs' leading features in signature order, with raw rank, weight and selection flag |
| tf-targets.tsv | Supplied TF annotations joined to leading targets of selected motifs |
| signature-dispositions.tsv | Present and missing signature features, without identifier repair |
| annotation-dispositions.tsv | Matched and unmatched supplied motif annotations |
| database-features.tsv | Complete original database feature order |
| recovery-curves.tsv and recovery-background.tsv | Optional complete numerical curves and background moments |
| run.json | Source profile, counts, input/result SHA-256 bindings and admission status |

**NaN NES is undefined enrichment, not a valid null test.** A flat or undefined motif-background SD retains NaN and prevents selection; it is never replaced with 0. Zero selected motifs is an ordinary selection outcome only after checking every motif's status. Missing features and unmatched annotations require compatibility review before interpretation.

**SOURCE ROUNDING WARNING means finite NES values arise from rounding on identical AUCs.** NumPy's reduction can give a tiny positive SD for an exactly constant AUC vector, depending on its length. Those original finite values are preserved with a prominent warning. They are rounding artifacts, not biological enrichment; inspect the complete motif table rather than interpreting the threshold-selected rows alone.

**FAIL2 admits no result.** Correct the named input, setting, numerical or capacity problem and retry a new directory. Failed partial files remain under `partial/`; preserve them. Existing completed outputs are refused and remain unchanged. Hashes establish byte integrity, not authenticity or biological validity.

DNA scoring/Cluster-Buster, interval overlap, genome conversion, DMR selection, TF expression/methylation filtering, SCENIC/SCENIC+ fitting, AUCell, score-database analyses and R approximate/smoothed leading edges remain separate. The separately licensed pycisTarget wrapper is not installed or executed by this workflow.

## Validation and scope

Two Java 17-compatible compiler builds were exercised on Windows JDK21, Windows JDK25 and Linux JDK21:

- 112 numerical control families per build/JVM, totaling 672 full recovery/AUC/NES/leading-target comparisons. These comprise the original 64 mixed-profile families plus 48 Python pairwise, constant-positive, nearly flat and one-point recovery families, with twelve executed analytic/refusal contract suites.
- 16 complete TSV families per build/JVM, totaling 96 file flows and 60 actual CLI refusals. Every motif, curve, background value, target membership, disposition and supplied annotation join is checked.
- The original complete five-motif, 22,284-feature ctxcore Feather fixture, preserved unchanged, with lossless V1/V2 exports and six native runs against original public Feather import/enrichment functions. The positive numerical signature deliberately selects source-ranked genes; it is not a biological result.
- An actual missing-runtime converter execution returns SKIP3, and an actual capacity failure returns FAIL2, both without an admitted TSV.
- The literal documented example passes on all six build/JVM combinations, including its analytic AUC and byte-identical TSV results.

The Python reference uses installed original ctxcore0.2.0 functions with NumPy 1.26.4. The R reference executes unchanged RcisTarget1.29.0 function definitions under R 4.6.1; the full R package is not installed, and S4/database/annotation wrapper parity is not claimed. The initial R mean/SD failure and the unpublished checkpoint preceding the NumPy reduction correction remain preserved. Only the corrected builds and expanded controls establish this checkpoint.

Run the frozen evidence check with:

```powershell
python tools/literature/cistarget_development_evidence.py
```

Frozen verification checks stored integrity and numerical relationships. It does not execute Java/Python/R analysis, independently refit current user data, authenticate execution, reproduce a paper or complete the 250-paper goal. See [the evidence manifest](cistarget-development/VALIDATION.json) for exact source/artifact hashes and the preserved control populations.
