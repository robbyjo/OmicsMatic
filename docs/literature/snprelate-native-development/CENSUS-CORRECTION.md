# Correction to the earlier source-name census

The frozen native KING/QC evidence and Java components remain valid. Their earlier paper-role list is incomplete: it reported three SNPRelate papers and missed P170, which uses SNPRelate for genotype PCA in two perturbation cohorts after separate PLINK pruning.

In the raw JATS paragraph text, citation numbers immediately followed `SNPRelate`, defeating the original name-boundary check. The corrected audit uses the existing citation-normalized `match_text` while retaining hashes of the unchanged raw primary paragraphs. It rechecks all250 fixed papers and identifies **four SNPRelate papers: P130, P170, P191 and P216**. GENESIS remains five actual software-use papers; the two ordinary-word matches remain excluded.

The reviewed correction is [snprelate-genesis-primary-roles-v2.json](../omics-2023-2026/snprelate-genesis-primary-roles-v2.json). It retains its preparatory status as a frozen census snapshot. The prior role file and proof are retained as historical evidence and must not support a claim of complete SNPRelate source-role coverage.

The subsequent [SNPRelate workflow integration](../SNPRELATE_INTEGRATION.md) validates original KING, LD, GRM and exact/randomized PCA separately from the earlier native component. Current application of all nine SNPRelate/GENESIS package reviews is recorded in [source-role coverage](../omics-2023-2026/snprelate-role-coverage.json). The correction alone grants no integration credit; the separately validated workflow supplies one scoped SNPRelate adapter. GENESIS remains unimplemented.
