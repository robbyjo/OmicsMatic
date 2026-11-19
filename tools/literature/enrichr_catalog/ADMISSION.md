# Enrichr cumulative admission

This driver proposes/adopts one current-service upstream adapter, 23 new
individual reviews and three source-supported observations. It preserves all
1,439 earlier review objects, including P061/P163. The 25 roles distinguish
23 enrichment analyses, P055's library resources and P182's unresolved host.
Historical clients/libraries, Julia recalculation and complete author workflows
are not qualified by the current service workflow.

Use Python 3.11+ without `-O`. Restore the exact permitted primary XML cache,
P160 PDF and P114 author archive. Supply a separate clean Git checkout of
`29486e3f7bf83f534324f21c2d2bd917ad35b134`; the original unchanged COLOC
owner runs there against its own files and metadata. Do not reset another
worktree or overlay scientific source. That checkout needs its permitted
`build/literature-review` cache link as described in the preceding source
transition/COLOC guides. Original verification outputs are NEW ignored build
directories in that checkout; raw process consoles are also retained locally.

From the active repository root, the paths used on this machine are:

```powershell
$env:PYTHONPATH = 'tools/literature'
$enrichrXml = 'D:/projects/JLinAlg/build/literature-review/xml'
$enrichrPdf = 'build/seurat-catalog-1/supplement-P160-2/SUPPLEMENT.pdf'
$enrichrAuthor = 'build/plink-high-impact-1/P114-author-code-1/EAS_SCZ_rCNV_analysis.zip'
$enrichrPrevious = 'build/enrichr-catalog-admission-1/coloc-predecessor-checkout'
python -B -m enrichr_catalog.catalog --plan --primary-cache $enrichrXml --supplement-pdf $enrichrPdf --author-archive $enrichrAuthor --historical-checkout $enrichrPrevious --out build/enrichr-plan-NEW
python -B -m enrichr_catalog.catalog --inspect --plan-dir build/enrichr-plan-NEW --out build/enrichr-inspect-NEW
python -B -m enrichr_catalog.catalog --apply --plan-dir build/enrichr-plan-NEW --primary-cache $enrichrXml --supplement-pdf $enrichrPdf --author-archive $enrichrAuthor --historical-checkout $enrichrPrevious --out build/enrichr-apply-NEW
python -B -m enrichr_catalog.catalog --verify --primary-cache $enrichrXml --supplement-pdf $enrichrPdf --author-archive $enrichrAuthor --historical-checkout $enrichrPrevious --out build/enrichr-verify-NEW
python -B -m unittest enrichr_catalog.test_admission
```

Plan/inspect preserve an exact reviewable proposal. Apply requires an inspected
plan, repeats all source/predecessor/qualification checks and uses the existing
staged metadata transaction with concurrent-edit guards. The before archive,
exact appended-review inverse and cumulative manifest are retained. Verify
checks current metadata and repeats fresh original-owner/source/readback
checks. Do not reapply an already completed admission.

**SKIP3:** exact permitted dependency/cache/service unavailable; skip dependent
analysis/audit, restore the named item and retry NEW output. **FAIL2:** changed
source/evidence/schema/stale plan or another failure; no complete admission.
Retain inputs, partials and errors, correct the cause and retry NEW output.
Historical marker-verifier skips remain explicit. Never refresh old hashes,
overlay scientific source or silently alter IDs, P values, inequalities or
algorithms. Both literature reports remain withheld from Pages; no release.
