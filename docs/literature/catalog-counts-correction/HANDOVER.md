# Catalog aggregate correction

The current totals remain **250 papers, 186 scoped families (37 native, 149 upstream), 1,940 reviewed uses and 801 pending uses across 2,741 observations**. There are 453 named labels, 492 provider definitions and 246 missing labels. No family, source-review or observation credit is added by this correction.

One `summary.json` aggregate field reported 148 upstream families while its complete implementation-status inventory and the published 186 = 37 + 149 totals already reported 149. The corrected summary and embedded report now agree. Original paper, tool, review, scientific source, runtime and result bytes are unchanged. The exact previous metadata and checksum list remain retained in this folder.

The current effective registry is `docs/literature/catalog-counts-correction/REGISTRY.json`; its definitions are identical to the preceding pbmm2 registry. Run `python -B tools/literature/catalog_counts_correction.py` to verify the complete forward correction. The checksum family contains 935 entries. Earlier admission checkers represent their historical snapshots; use this latest checker for the current metadata state.

Future forward admissions must call `catalog_count_invariants.validate` on their **own proposed and saved final metadata**, in addition to replaying their predecessor. The invariant checks inventory status counts, native/upstream/scoped arithmetic, existing adapters, alias exclusion, unique observations/reviews and pending-use arithmetic. The preserved stale snapshot is a negative control and is rejected by this guard.

The full implementation goal remains active. The pbmm2 workflow/source qualifications, three original 1.17 legacy discrepancies and its completed cleanup are unchanged. CADD raw-VCF annotation/prediction remains pending while its existing full original annotation archive transfer continues. Preserve the live job and require its complete original checksum before extraction; never reduce reference tracks/models or restart a live/indeterminate job. Cell Ranger remains excluded; MaxQuant remains pending with runtime analyses skipped. No release or literature Pages deployment is requested.

This metadata-only correction creates no compiler outputs, installed runtimes, download caches or temporary checkouts. Its new files and before-state archive are retained provenance. The private post-publication receipt records the zero-removal cleanup assessment and foreign-worktree preservation.
