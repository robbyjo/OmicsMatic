# PRSice: paper uses and available workflows

The catalog links five PRSice uses to the validated original 2.3.5 adapter and
its native Java score audit. This adds one upstream family and four reviews;
P150 was already reviewed and receives a refinement without duplicate credit.
Totals become **161 scoped families (30 native + 131 upstream), 1,769 reviewed
uses and 955 pending uses** across the unchanged 250-paper corpus. There are
still 254 missing labels and 16 method overlaps. The full goal is unfinished.

**Warnings and required actions:** current release 0.4.1 assets do not contain
this adapter; build current source. Read the [complete workflow guide](../PRSICE_WORKFLOW.md)
for installation, complete generated requests, policies and output interpretation.
Exit **3 / SKIP3** skips dependent analysis when the exact permitted runtime,
producer, inputs or capability are unavailable. Install only where allowed;
if installation/execution is forbidden, retain the skip. Exit **2 / FAIL2**
admits no complete result: preserve partial output, read `WARNINGS.md`,
`FAILURE.md`, scientific audit, sample/variant ledgers and original logs, resolve
the named cause and retry **NEW** output. Keep captures, binaries and raw traces
private; retain exact producer JARs and immutable source contexts.

## What the papers used

| Paper | Reported PRSice use | Declared details and remaining scope |
| --- | --- | --- |
| [P076: BridgePRS](https://doi.org/10.1038/s41588-023-01583-9) | PRSice-meta after population GWAS meta-analysis; comparison with BridgePRS and PRS-CS methods | Choose between population LD panels using target-population test prediction; distinct GWAS, optimization/test and validation data. METAL meta-analysis and panel/model selection remain separate. Generic `PRSice-2 v.2` does not identify a patch. |
| [P115: MDD phenotype integration](https://doi.org/10.1038/s41588-023-01559-9) | PRSice v2 scoring for held-out tenfold prediction from imputed-phenotype/MTAG and external GWAS | Regenerate GWAS in each 90% training split; predict the held-out 10%; use 20 PCs and genotyping array. External INFO >0.9 and MAF >5% are strict eligibility conditions. Original CLI minimum filters are inclusive: use explicit preselection for strict inequalities. Patch, complete commands and threshold choices remain unresolved. |
| [P128: multiancestry Alzheimer PRS](https://doi.org/10.1038/s41588-026-02722-8) | Ancestry-specific C&T, and applying LDpred2/PRS-CS/PRS-CSx posterior weights | Reported clumping r2 values 0.1/0.2/0.3, windows 100/250/500 kb, P range 5e-8 to 0.5. The paper's “without clumping parameters” wording for posterior-weight scoring does not establish exact historical flags. Five-subset model selection, posterior inference, combination, scaling and APOE exclusion remain separate. No patch edition is established by `PRSice2`. |
| [P147: liability-threshold prediction](https://doi.org/10.1038/s41588-025-02370-4) | PRSice version 2 scores from LTPI/LTFH/GWAS/SoftImpute analyses for target/nontarget prediction and PRS pleiotropy | Tenfold 90/10 GWAS/prediction splits. Fold construction, GWAS estimation, pleiotropy evaluation, exact patch and author commands remain separate or unqualified. |
| [P150: metabolic syndrome](https://doi.org/10.1038/s41588-024-01933-1) | Additional PRSice-2 comparison of European MetS GWAS scores in KoGES | Detailed choices/results are referred to the Supplementary Note and Table 39. Patch, full commands and supplement-level thresholds/clumping remain unresolved. This comparison is distinct from the main PRS-CS workflow. |

The original near-name version fields remain `null`, `2`, `null`, `2`, `null`
in that order. Contextual literal versions and all 17 eligible source anchors
are retained separately. Availability of 2.3.5 does not establish that any paper
used that patch. Source-role review does not imply whole-paper completeness.

## Use the implementation

```powershell
.\gradlew.bat executableJar
java -jar build/cli/omicsmatic-0.4.1.jar prsice-tools --help
python tools/literature/prsice_workflow/make_example.py --runtime-home D:/private/prsice-2.3.5 --out D:/private/prsice-example
java -jar build/cli/omicsmatic-0.4.1.jar prsice-tools --request D:/private/prsice-example/target-clump-score.json
```

Use `CLUMP`, `SCORE` or `PREDICT` with explicit choices. A declared reference
panel supplies original LD clumping; Java does not substitute Pearson LD.
Prepared posterior weights can be applied with no clumping, explicit allele
effects and a P=1 **inclusion marker**, which is not association evidence. The
adapter does not estimate those weights or infer their scale. `PREDICT` fits
single-trait association; it does not create folds or prove held-out accuracy.

**Original conventions matter.** Quantitative `-9` is observed. Standardization
uses source masks that differ from fitted complete cases; unknown outcomes can
remain in control standardization. Read sample ledgers and explicit policies.
Original GLM convergence is not exposed. Reference MAF=0/missingness=1 leaves
the original reference-count cache inactive and can cause target fallback;
the adapter skips an unfulfilled REFERENCE-frequency request. Keep the skip,
choose TARGET explicitly, or justify active reference QC; no choice changes
automatically. Original score and fit exports have 9 and 6 significant digits.

## Evidence and limits

The [workflow checkpoint](prsice-workflow-checkpoint/STATUS.json) records 38 Java
tests on each platform, 50 workflow checks and 13 scoring-matrix checks per
platform, 31,590 score comparisons, 40 independent R fit rows and eight summary
checks, four portable inspections and five executed examples. Original runtime
checks and private evidence stay distinct from catalog source reviews. All
9,702 protected evidence files, failed attempts and exact producers survived
the workflow's finishing cleanup. The [catalog admission](prsice-catalog-admission/README.md)
records the guarded metadata transition, full primary census and exact delta.

No historical patch equivalence, original cohort reanalysis, whole-tool
equivalence, convergence certificate or whole-paper reproduction is claimed.
Cell Ranger remains excluded by the user. The literature pages remain withheld
from deployment by the unchanged Pages workflow.
