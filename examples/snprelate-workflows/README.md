# Synthetic SNPRelate inputs

`inputs.zip` contains two project-generated BED/BIM/FAM cohorts: `mixed/cohort` (10 samples,129 variants) and `rank/cohort` (32 samples,129 variants). They exercise missing calls, QC, chromosome selection, Unicode FIDs, repeated IIDs in different families and literal `NA`/`NaN` identifiers. These are synthetic software controls, not participant data or biological findings.

Use a new extraction/output path. Follow [SNPRELATE_INTEGRATION.md](../../docs/literature/SNPRELATE_INTEGRATION.md) for the explicit original-runtime setup and complete request. The SNPRelate profile uses numeric GDS row keys with original ID sidecars; it never silently repairs or overwrites the supplied BED/BIM/FAM inputs.
