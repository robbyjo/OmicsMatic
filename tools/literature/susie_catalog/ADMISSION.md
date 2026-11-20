# Guarded SuSiE cumulative admission

This owner plans, inspects, applies and freshly verifies the supplied-input
original-susieR workflow family and 20 source roles. It preserves all 1,483
prior reviews, adds P073/P203 and removes the exact unreviewed P235 proper-name
false positive. Result reuse, hosting and historical/author gaps remain explicit.
This admission adds no global dispatcher, scientific engine change or release.

Use Python 3.11+ without `-O`, exact permitted XML/P160 PDF/P114 author-code
inputs and **three separate clean historical checkouts**. Scanpy uses
`0e20bb5017303382a78e041ff8810139232e8336`; Enrichr uses
`bd26330459c35c5ec6e5be904d349280bb353f6e`; COLOC uses
`29486e3f7bf83f534324f21c2d2bd917ad35b134`. The unchanged original Scanpy
owner executes in its exact checkout and invokes the unchanged Enrichr/COLOC
owners in their separate exact checkouts. Their ignored
`build/literature-review` cache links must point to the permitted original
cache. Do not overlay source, reset checkouts or refresh earlier hashes.

From this active checkout in PowerShell, use NEW outputs:

```powershell
$env:PYTHONPATH = 'tools/literature'
$susieInputs = @('--primary-cache','D:/projects/JLinAlg/build/literature-review/xml','--supplement-pdf','build/seurat-catalog-1/supplement-P160-2/SUPPLEMENT.pdf','--author-archive','build/plink-high-impact-1/P114-author-code-1/EAS_SCZ_rCNV_analysis.zip','--historical-checkout','D:/projects/JLinAlg/build/sc-susie-1','--enrichr-checkout','D:/projects/JLinAlg/build/sc-enrichr-1','--coloc-checkout','build/enrichr-catalog-admission-1/coloc-predecessor-checkout')
python -B -m susie_catalog.catalog --plan @susieInputs --out build/susie-plan-NEW
python -B -m susie_catalog.catalog --inspect --plan-dir build/susie-plan-NEW --out build/susie-inspect-NEW
python -B -m susie_catalog.catalog --apply --plan-dir build/susie-plan-NEW @susieInputs --out build/susie-apply-NEW
python -B -m susie_catalog.catalog --verify @susieInputs --out build/susie-verify-NEW
python -B -m unittest susie_catalog.test_admission
```

Apply requires the exact inspected source/preview revision and unchanged
predecessor bytes. It repeats source/qualification/original-owner checks,
retains before bytes/manifest/registry/source, installs transactionally and
checks the final metadata. Verify repeats the real owner chain, fresh
250-source census and saved scientific qualification; it does not refit R
models. Do not reapply completed admission. The only shared source change is
the append of 20 reviewed-use records, with an exact JSON inverse.

Expected results must be derived from the actual formal proof: 138 scoped
integrations, 1,503 reviewed and 1,201 pending out of 2,704 observations.
Reported edition matches do not establish historical author dependencies or
whole-paper parity. The standalone entry point remains separate from native
`susie`; global `susie-tools` and Linux R scientific qualification remain open.

**SKIP3:** name and restore the exact permitted missing/forbidden prerequisite,
otherwise keep dependent work skipped. **FAIL2:** preserve raw inputs,
original objects, partials and diagnostics; correct the named invalid/stale
source, plan, evidence, numeric/convergence/capacity/process cause and retry
NEW output. Do not silently repair data/IDs/alleles/thresholds or source
editions, and do not refresh historical hashes. Both literature reports stay
withheld from Pages. The full 250-paper implementation/review goal is open.
