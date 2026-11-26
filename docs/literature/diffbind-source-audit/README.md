# DiffBind source-role review

This source review supports the implementation work. **It gives no runtime or tool-family credit.** An executable workflow needs separate scientific qualification.

The name and alias screen covers all 250 original XMLs in the frozen 2023-10-23 through 2026-10-23 cohort. It finds five body contexts across four papers and two reference pointers outside the body. It also retains the RNA-seq design cross-reference used by P046. Full paragraphs and raw article XML remain private; `CENSUS.json` and `ROLES.json` retain identities, anchors, reviewed summaries and original catalog fields.

| Paper | Reported version | Reported DiffBind use |
| --- | --- | --- |
| P020 | 3.0.15 | ChIP-seq differential peaks; absolute log2FC > 1.5 and adjusted P < 0.05. The inference engine is not stated here. |
| P046 | 3.4.11 | Consensus peaks and normalization followed by DESeq2 differential accessibility, FDR < 0.01. The linked RNA-seq section describes a design with multiple factors, including genotype or individual. |
| P155 | Unreported | Differential binding; citation 42 points to the package page from 2011 and does not fix a release or full recipe. |
| P163 | 3.12.0 | DESeq2 when n > 2, FDR 0.1, PCA/heatmap checks and a null comparison using randomized pairs. The n = 1 custom-script branch is separate. |

The implementation candidate is the original [DiffBind 3.24.0 release](https://bioconductor.org/packages/3.24/bioc/html/DiffBind.html) on a new private R 4.6.2 runtime. This choice does not establish an author edition. The earlier source 3.20.0 intake is retained separately. A source download, installed library or passing source audit does not admit a scientific analysis. Designs, contrasts, count/normalization settings and complete inputs must remain explicit.

Run the read-only source check from its matching source revision:

```sh
python3 -B tools/literature/diffbind_primary.py \
  --root /absolute/source --cache /absolute/private-original-XML-cache
```

**Warnings and required actions:** SKIP3 means a required permitted source/cache is unavailable; restore the named original input or skip the dependent audit. FAIL2 means changed data, source identities or inconsistent scope; preserve the attempt, investigate and use a new context. Do not refresh historical hashes, replace cohort papers, infer a missing version/engine/formula, or count a package citation as an exact author recipe. Runtime setup and scientific qualification are still pending. The literature HTML reports remain withheld from Pages.
# Separate implementation qualification

This intake remains a source-only record: its availability fields do not award
implementation credit. The separately validated current workflow is described
in the [DiffBind guide](../../diffbind-tools.md) and
[qualification](../diffbind-validation/QUALIFICATION.json). Applied catalog
qualifications preserve the original author versions and missing recipe details.

