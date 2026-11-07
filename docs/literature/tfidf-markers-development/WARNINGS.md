# TF-IDF marker component: warnings and required actions

**SoupX ambient RNA integration is unfinished.** The new Java component provides supplied-cluster marker statistics only. It is not counted as an implemented SoupX tool. The catalog remains76 new scoped integrations,255 missing. Read [the guide](../TFIDF_MARKERS_DEVELOPMENT.md) before analysis. The canonical/released executable, prior scientific proofs, existing libraries and unrelated local work remain unchanged.

**Require exit0 and result_admitted=true.** Review WARNINGS.md, analysis-status.json, provenance.json, complete statistics.tsv and selected markers.tsv. Declare expression units, cutoff, N and FDR. No clustering, normalization, paper settings, ID repair or missing-value omission is inferred.

**A selected marker can have q>=FDR.** The original top-N branch can choose such genes; selected_outside_fdr and a prominent warning expose this behavior. ALL returns every strictly passing gene. Zero-expression genes retain Inf IDF/NaN TF-IDF in complete statistics.

**Cutoff arithmetic requires review.** Native/original double calculations can fall on a different side of an exact mathematical boundary. The two-cell/two-gene control has exact rational q=1; captured R/Java enriched-gene q values lie slightly below1. Original and native strict FDR=1 return markers; original17-digit q-as-cutoff calls are retained separately. Near-cutoff warnings use max(64 ulps,3e-12 relative), without changing native classification. Inspect the exact chosen upstream runtime when that classification matters.

**FAIL2 means no admitted result.** Preserve inputs, diagnostics and unadmitted-partial-results. Correct the named input, work/table capacity, heap or storage cause and retry NEW paths. Invalid request syntax may fail before a bundle exists; stderr provides actions. Native execution installs nothing and needs Java only.

**Original references are separate.** Source1.6.2 comes from CRAN; a failed1.3.0 CRAN URL returned404 before recovery of an official GitHub source candidate. Version labels do not establish paper commits. Current R4.6.1/Matrix1.7-5/Seurat5.6.0 is not a historical paper environment. Both private installs and original controls passed with unchanged preexisting libraries. If exact dependencies or installation are unavailable, preserve SKIP3 and skip dependent original analysis; do not silently upgrade, patch original functions or substitute versions. Initial preparation/storage failures, original warnings and rounded reference displays remain in the archive; final controls retain17-digit strings and original RDS objects.

The7 primary-role drafts preserve manual ALB/Csf1r roles, secondary validation versus correction, explicit P145 overrides, and separate downstream analyses. They remain unapplied until ambient workflows are ready. No biological validation, complete package/paper reconstruction, release or whole-goal completion is claimed. Cell Ranger remains excluded.
