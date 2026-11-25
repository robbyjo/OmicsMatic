# MetaXcan and PredictDB paper roles

The audit checked all250 frozen article XML files, classified25 broad text
matches and reviewed two matching back-matter structures. Eight existing
MetaXcan-family observations cover six papers. Two explicit PredictDB training
uses are cataloged separately. Source hashes and paragraph locations are in
[CENSUS.json](metaxcan-source-audit/CENSUS.json) and
[ROLES.json](metaxcan-source-audit/ROLES.json); article texts remain private.

| Paper | Reported use | Available scope and remaining work |
| --- | --- | --- |
| [P019, Nature](https://doi.org/10.1038/s41586-026-10836-6) | Ancestry/cell-type PredictDB model training; S-PrediXcan summary TWAS in Python2 | Native summary association is available. Release is unreported; Python2 is not an edition. PredictDB training remains missing. Individual prediction and adjusted logistic analyses do not name a MetaXcan entry point. |
| [P074, Nature Genetics](https://doi.org/10.1038/s41588-025-02417-6) | PredictDB v7 nested-CV elastic-net training of aortic-valve models; S-PrediXcan within MetaXcan0.7.4 for aortic stenosis | Native summary association is available against an explicitly pinned internal0.7.4 source commit. The exact author commit and training commands are unknown. Training remains separate. Both raw name-near-version fields are retained. |
| [P090, Nature Genetics](https://doi.org/10.1038/s41588-024-01952-y) | S-MulTiXcan0.7.3,49GTExv8 MASH-R tissues, DCM summary GWAS | Native joint summary association supports explicit products and all four source cutoffs. The author cutoff/product construction is unreported and remains unresolved. |
| [P203, AJHG](https://doi.org/10.1016/j.ajhg.2024.09.001) | Attributes population-model training to MetaXcan, then combines models with schizophrenia summary GWAS | Native summary association is available. The pinned MIT author repository supplies model databases but no trainer. The training attribution remains unresolved. |
| [P221, AJHG](https://doi.org/10.1016/j.ajhg.2026.03.008) | Author R/glmnet models and S-PrediXcan with MESA covariance; separate bacon/omnibus processing | Native summary association accepts the deposited five-column covariance. The literal author wrapper selects an absent model column and fails; its R trainer fails parsing. Corrected author inputs/trainer are required. No source was patched. |
| [P239, AJHG](https://doi.org/10.1016/j.ajhg.2025.06.014) | S-PrediXcan summary GPGE for MASLD and drug-indication traits,48GTExv7 tissues | Native summary association supports beta/SE. Edition is unreported. GPGE wording does not establish individual patient expression prediction. coloc, IPA and TwoSampleMR are separate. |

P095 describes a PrediXcan-style elastic-net comparator implemented through
scikit-learn. P238 discusses S-PrediXcan as an alternative method. Neither is
promoted to an executed MetaXcan use. Code availability, reference links,
repeated results and downstream consumption do not create extra execution credit.

One scoped native family receives credit for S-PrediXcan/S-MulTiXcan summary
association; PrediXcan and MultiXcan labels are aliases with no extra family
credit. This does not cover every mode named by those projects. The separately
named PredictDB training provider remains missing: SQLite input parsing and a
generic elastic-net kernel do not implement nested cross-validation, training
QC, export or the author pipeline.

**Warnings and required action:** obtain corrected and licensed training
sources before attempting training; supply matching model/GWAS/covariance
identifiers and explicitly declared study settings for association. Keep
unreported editions/settings unresolved. Read the
[workflow guide](METAXCAN_WORKFLOWS.md) and each capture's warning/audit files.
`SKIP3` means the named prerequisite is unavailable and the dependent analysis
was skipped. `FAIL2` means no complete result: preserve the attempt, fix the
reported cause and use new paths. No study data or complete historical analysis
was reproduced; the full250-paper implementation goal remains open.
