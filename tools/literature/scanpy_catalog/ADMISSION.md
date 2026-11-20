# Guarded Scanpy cumulative admission

This driver admits one bounded upstream workflow family and appends 21 source
reviews for the 22 existing Scanpy observations. All 1,462 previous review
objects, including P061, remain exact. The three explicit native matrix APIs
receive no duplicate family credit. Full author workflows, historical editions
and unqualified branches remain gaps in every role's coverage.

Use Python 3.11+ without `-O`. Exact permitted XML, P160 PDF and P114 author-code
inputs are required. Supply **two separate clean historical checkouts**:
Enrichr `bd26330459c35c5ec6e5be904d349280bb353f6e` and COLOC
`29486e3f7bf83f534324f21c2d2bd917ad35b134`. The unchanged Enrichr owner runs in
its own checkout and invokes the unchanged COLOC owner in the other. Both need
their ignored `build/literature-review` cache link to the original-root cache.
Use a short checkout path on Windows and command-local `core.longpaths=true`
and `core.autocrlf=false` at creation; preserve the first failed long-path
attempt. Never reset an existing checkout, overlay source or refresh old pins.

From the active repository root on this machine:

```powershell
$env:PYTHONPATH = 'tools/literature'
$scanpyXml = 'D:/projects/JLinAlg/build/literature-review/xml'
$scanpyPdf = 'build/seurat-catalog-1/supplement-P160-2/SUPPLEMENT.pdf'
$scanpyAuthor = 'build/plink-high-impact-1/P114-author-code-1/EAS_SCZ_rCNV_analysis.zip'
$scanpyEnrichr = 'D:/projects/JLinAlg/build/sc-enrichr-1'
$scanpyColoc = 'build/enrichr-catalog-admission-1/coloc-predecessor-checkout'
python -B -m scanpy_catalog.catalog --plan --primary-cache $scanpyXml --supplement-pdf $scanpyPdf --author-archive $scanpyAuthor --historical-checkout $scanpyEnrichr --coloc-checkout $scanpyColoc --out build/scanpy-plan-NEW
python -B -m scanpy_catalog.catalog --inspect --plan-dir build/scanpy-plan-NEW --out build/scanpy-inspect-NEW
python -B -m scanpy_catalog.catalog --apply --plan-dir build/scanpy-plan-NEW --primary-cache $scanpyXml --supplement-pdf $scanpyPdf --author-archive $scanpyAuthor --historical-checkout $scanpyEnrichr --coloc-checkout $scanpyColoc --out build/scanpy-apply-NEW
python -B -m scanpy_catalog.catalog --verify --primary-cache $scanpyXml --supplement-pdf $scanpyPdf --author-archive $scanpyAuthor --historical-checkout $scanpyEnrichr --coloc-checkout $scanpyColoc --out build/scanpy-verify-NEW
python -B -m unittest scanpy_catalog.test_admission
```

Plan and inspect validate a concrete deterministic preview without installing
metadata. Apply requires that inspected source/preview revision, repeats actual
original-owner, source and saved-qualification checks, and guards concurrent
edits. Before bytes, original manifest, registry, evidence and verifier source
are retained. The only shared-source delta appends the 21 reviewed-use records;
its inverse restores the exact prior JSON bytes. No scientific engine changes.
Verify repeats original-owner/source/readback checks against actual metadata.
Do not reapply a completed admission; inspect requires the unapplied predecessor.

The expected catalog is 137 scoped integrations, 1,483 reviewed and 1,220
pending uses; derive actual success from the completed proof. Fifteen tested
upstream operations do not qualify all 29 development operation names. Saved
proof checks do not rerun scientific models. Native float64 profiles are
explicit, versioned choices rather than a fallback or whole-package parity.

**SKIP3:** an exact permitted dependency is absent or forbidden; skip its
dependent analysis/audit, name the item to restore and retry NEW output where
allowed. **FAIL2:** invalid/stale source, plan, data, model arithmetic, integrity,
capacity or process failure; no complete result. Preserve inputs/partials and
diagnostics, correct the cause and retry NEW output. Never silently repair IDs,
dtype, thresholds, populations, NaN/Inf or algorithms. Check native `usable()`.
Historical marker-verifier skips remain explicit. Both literature reports stay
withheld from Pages; no release is created. Full implementation/review is open.
