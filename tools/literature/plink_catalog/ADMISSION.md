# Cumulative PLINK-family admission

This separate layer admits supported PLINK, PLINK 2 and KING scopes after the
immutable source proposal and component qualification. It preserves the old
1,339-review prefix, adds 74 reviews/four observations and credits three scoped
upstream adapters, with no additional native-family credit. It checks the
actual Seurat/METAL/LDAK predecessor stage bytes and qualifications, source
bindings, existing skips, exact review inverse and cumulative manifest.

Read [the role/scope crosswalk](../../../docs/literature/PLINK_CATALOG_INTEGRATION.md)
and [component warnings](../../../docs/literature/plink-component-qualification/README.md).
The original source-proposal files and their hashes remain unchanged.

From the project root with Python 3.11+, full Git predecessor history, exact
private source cache/author archive and the retained Seurat P160 supplement:

```powershell
$sourceArgs = @('--primary-cache','D:/projects/JLinAlg/build/literature-review/xml',
  '--author-archive','build/plink-high-impact-1/P114-author-code-1/EAS_SCZ_rCNV_analysis.zip',
  '--supplement-pdf','build/seurat-catalog-1/supplement-P160-2/SUPPLEMENT.pdf')
python -B tools/literature/plink_catalog/test_admission.py
python -B tools/literature/plink_catalog/catalog.py --plan @sourceArgs --out build/plink-admission-plan-NEW
python -B tools/literature/plink_catalog/catalog.py --inspect --plan-dir build/plink-admission-plan-NEW --out build/plink-admission-inspect-NEW
python -B tools/literature/plink_catalog/catalog.py --apply @sourceArgs --plan-dir build/plink-admission-plan-NEW --out build/plink-admission-apply-NEW
python -B tools/literature/plink_catalog/catalog.py --verify @sourceArgs --out build/plink-admission-verify-NEW
```

Every output must be new. Plan/inspect do not apply metadata. Apply requires
the exact inspected plan, checks fresh source/evidence again and refuses
different or already-admitted metadata. Successful apply writes eight metadata
and report files plus source coverage, proof, review compatibility and manifest.
The shared tested installer rolls back only owned unchanged writes; concurrent
edits survive and failed new files are retained. Admission artifacts and
attempt diagnostics are kept if publication/checking fails; do not delete or
reuse them as a successful admission. Preserve them before a separate retry.

**SKIP3:** dependent work is skipped for a missing/unavailable/forbidden exact
source or prerequisite. Restore the named permitted bytes or leave it skipped.
No original analysis is substituted. **FAIL2:** no complete admission/result;
keep inputs, partials and logs, fix the named identity/source/model/integrity/
capacity cause and retry new paths. Do not use Python `-O`, weaken old guards,
refresh historical hashes, repair IDs/statistics/nonfinite values or silently
reinterpret source editions/inequalities. The two historical full marker
verifiers remain SKIP3 for the missing exact old JAR; their separate retained
source/archive/current-method checks remain distinct.

The retained component gate reads sources and evidence; no PLINK/KING/R
analysis is rerun during admission. Full source auditing requires the private
XML, author archive and exact supplement. These and large runtimes/JARs are
not supplied by GitHub. Linux fresh admission auditing needs a native Git
checkout; portable frozen component readback/restoration is separately tested.
Both literature reports stay withheld from Pages. Main pushes changing site
sources may deploy other site content; no release is created here. The full
250-paper implementation and paper-wide review objective remains unfinished.
