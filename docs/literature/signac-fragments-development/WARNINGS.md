# WARNINGS / REQUIRED ACTIONS

UPSTREAM_KEEP_ALL_CHUNK_BEHAVIOR — Original Signac 1.17.1 FeatureMatrix can retain only first-chunk counts when keep_all_features is true and multiple chunks are used. Native output reproduces it and warns. Review the intended analysis: retain the default false or choose a justified single chunk with process_n at least each source's local feature count. Do not silently repair historical results.

FAIL2 — Invalid input, sort/coordinate/mapping violations, undefined ECDF, corrupt gzip, exceeded bounds and altered captures do not admit successful native results. Retain failed folders and logs, correct the named cause, and rerun to NEW directories. Partial files in failed folders are diagnostic only; existing destinations are refused.

LEGACY QC — Strict mononucleosomal lengths are >147 and <294; nucleosome-free lengths are <147. NaN, NA and Inf are distinct, retained states. Inspect mappings, sampling and zero denominators; do not impute zero or treat an infinite ratio as passing QC. This deprecated legacy Signac metric is not TSS enrichment or the newer ATACqc implementation. Entirely undefined ECDF fails.

PREFIX_ONLY — max_lines is per file, includes records outside the whitelist and does not certify unread tail/compression integrity. Set 0 for full-file scanning where needed. FeatureMatrix counts whole fragment lines, not PCR/read-support weights or TSS endpoints. Raw multi-file counts pool matching barcodes; review sample barcode reuse.

STORED_ZERO_BINARIZED_TO_ONE — Every stored sparse slot becomes1, including explicit zeros, matching original BinarizeCounts. If a positive-count indicator is intended, remove stored zeros explicitly and record that input transformation first.

SKIP3 — Native fragment operations require no R installation. Optional downstream LSI requires the exact pinned R runtime and working native libraries. If missing, install/provide the named permitted runtime and rerun into NEW output, or retain the skip when installation is forbidden/unavailable. Never substitute an unvalidated SVD method or pretend skipped analysis completed.

UTF-8 / ASSEMBLY — Keep the request filename/path ASCII on the tested Windows launchers and put exact Unicode data paths/IDs inside UTF-8 JSON/tables. Regions are one-based closed, fragments are BED zero-based half-open. The required genome_build label records a declaration, not assembly compatibility certification. Review chromosome names and input provenance.

DEVELOPMENT SCOPE — Historical package binaries were not executed. No TSS enrichment, gene activity, motifs/chromVAR, peak links, differential accessibility, full Signac integration or biological QC decision is certified. The catalog remains68 new integrations (71 including existing),259 missing; Signac receives no implementation credit yet. The canonical executable and prior evidence stay unchanged. No release, deployment, push or whole-goal completion occurred.
