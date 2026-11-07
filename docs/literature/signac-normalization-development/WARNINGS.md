# WARNINGS / REQUIRED ACTIONS

SKIP3 — Historical assay wrappers1.6.0/1.7.0/1.9.0 are incompatible with installed SeuratObject5.4.0. Skip their whole-assay execution on this runtime. For historical reproduction provide an isolated compatible SeuratObject/Signac installation and validate it; do not rewrite original code or hide defunct-argument errors. Their extracted matrix normalization/metadata functions passed separately.

Installation warnings — Bioconductor3.23 annotation Windows binary indexes returned404. Required software packages installed and current full Signac1.17.1 loaded. No annotation-dependent analysis was attempted. No repair is needed for native normalization. Before a future genome-dependent analysis install and verify compatible permitted species annotations; if unavailable, name the missing package and skip that analysis.

Native domain — Only nonnegative integer counts, sorted unique CSC coordinates, finite positive scale/IDF and row/column totals<=2^53-1 are admitted. Correct invalid input or use a separately validated route for a different domain. Never round silently. Review named empty-cell/feature QC warnings. Infinite output is retained: skip finite-only downstream analysis, inspect parameters and rerun with a justified correction; never clip silently.

Incomplete workflow — Native normalization and feature selection are not a full Signac integration. LSI/counting/QC/gene activity/motifs/LinkPeaks/differential accessibility and a public typed workflow remain. The new P134 software-use observation remains pending source curation; P007 SignAC is an institutional name. No public workflow, real study analysis, catalog promotion, biological certification, release, deployment, push or completion of the broader goal is claimed.
