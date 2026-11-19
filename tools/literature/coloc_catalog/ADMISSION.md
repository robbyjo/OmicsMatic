# COLOC cumulative admission driver

This driver admits the qualified native ABF scope and explicitly identifies the
existing native stable BF alternative. It appends 26 source reviews while
preserving 1,413 old reviews/P190, all 250 identities, 2,700 observations and
unrelated families. It does not claim literal native BF/R parity, original
author execution, historical-edition/full-package parity or whole-goal completion.

Run from the repository root with Python 3.11+ and without `-O`. Restore the
exact permitted primary XML cache, P160 supplementary PDF and P114 author-code
archive first. The private paths used for this project are:

```powershell
$colocXml = 'D:/projects/JLinAlg/build/literature-review/xml'
$colocPdf = 'build/seurat-catalog-1/supplement-P160-2/SUPPLEMENT.pdf'
$colocAuthor = 'build/plink-high-impact-1/P114-author-code-1/EAS_SCZ_rCNV_analysis.zip'
```

The modules use the established literature Python import root. Execute actual
modes with `PYTHONPATH=tools/literature` (PowerShell below):

```powershell
$env:PYTHONPATH = 'tools/literature'
python -B -m coloc_catalog.catalog --help
python -B -m coloc_catalog.catalog --plan --primary-cache $colocXml --supplement-pdf $colocPdf --author-archive $colocAuthor --out build/coloc-plan-NEW
python -B -m coloc_catalog.catalog --inspect --plan-dir build/coloc-plan-NEW --out build/coloc-inspect-NEW
python -B -m coloc_catalog.catalog --apply --plan-dir build/coloc-plan-NEW --primary-cache $colocXml --supplement-pdf $colocPdf --author-archive $colocAuthor --out build/coloc-apply-NEW
python -B -m coloc_catalog.catalog --verify --primary-cache $colocXml --supplement-pdf $colocPdf --author-archive $colocAuthor --out build/coloc-verify-NEW
python -B -m unittest coloc_catalog.test_admission
```

On Linux use `PYTHONPATH=tools/literature python3 -B -m ...` and native paths.
Choose NEW output directories for every attempt. Plan/inspect write previews
and evidence without canonical mutation. Inspect preserves a concrete reviewed
plan; apply fresh-checks all prerequisites and requires identical plan results.
It uses the established staged metadata transaction, before ZIP, exact review
inverse and cumulative SHA-256 manifest. Verify rechecks actual predecessor
stages, sources, qualifications, current metadata, compatibility and manifest.

**SKIP3:** exact permitted source/runtime/validation dependency missing or
unreadable; skip the dependent audit/analysis, name what to restore and leave
the skip visible until resolved. **FAIL2:** changed evidence, invalid schema,
stale plan or another failure; no complete admission/result. Preserve raw
inputs, partials and diagnostics, correct the cause and retry new outputs.
Never refresh old hashes, overlay historical scientific source or repair
identifiers, source inequalities, nonfinite values or algorithms.

Full old marker-verifier SKIP3 gaps and retained PLINK fixture failures remain
explicit. Source/qualification replay here does not refit upstream R/PLINK
models or reproduce author papers. Both literature reports remain withheld
from Pages. An authorized main push touching `site/**` may deploy the rest of
the site; follow hosted workflow/live verification while preserving exclusions.
No release is created by this driver.
