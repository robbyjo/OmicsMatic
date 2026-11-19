# PLINK-family primary-source review

**Source review and tested in-memory proposal only. No catalog writer, family
promotion or completed cumulative admission is provided here.** Read the
[source checkpoint](../../../docs/literature/PLINK_SOURCE_CENSUS_2026-11-19.md).
The canonical catalog stays at 2,696 observations, 1,339 reviewed uses and
131 new scoped integrations until a separate qualified admission is applied.

- `reviews.py`: 96 individual decisions across PLINK (53), PLINK 2 (26) and
  KING (17), seven non-execution contexts and eight proper-name exclusions.
  Twenty-two existing review records are retained verbatim; coverage IDs are
  proposed component overlap, not complete author recipes or family credit.
- `P114-AUTHOR-CODE.json`: exact paper-linked Zenodo archive, selected source
  member/line hashes and read-only source roles. No author code is distributed.
- `primary.py`: fresh audit of all 250 frozen XML/DOI identities, named contexts,
  citation-free edition literals, four missed uses and the exact author archive.
  It reads source only and never executes author scripts or scientific software.
- `model.py`: deterministic proposal for four core metadata documents, preserving
  the 1,339-review prefix, all unrelated observations and implementation statuses.
  It does not render reports, verify the cumulative predecessor/implementation
  qualifications, create compatibility deltas/manifests, or write the catalog.
- `test_primary.py` / `test_model.py`: eight source-bound refusal tests and
  twelve frozen proposal/preservation/refusal tests.
- `reproduce_proposal.py`: checks the pinned source/evidence packet and
  reproduces the four proposed core documents into new output, without Git
  history or private sources; supplying both source inputs also performs a
  fresh primary audit. It never applies the proposal.

From the project root, with Python 3.11+ and the full Git predecessor history:

```powershell
python -B tools/literature/plink_catalog/test_primary.py
python -B tools/literature/plink_catalog/test_model.py
python -B tools/literature/plink_catalog/reproduce_proposal.py --out build/plink-proposal-readback-NEW
python -B tools/literature/plink_catalog/primary.py `
  --source-cache D:/projects/JLinAlg/build/literature-review/xml `
  --author-archive build/plink-high-impact-1/P114-author-code-1/EAS_SCZ_rCNV_analysis.zip `
  --out build/plink-primary-audit-NEW
```

Every output path must be new. Missing exact XML, author archive or Git
predecessor is **SKIP3**: source audit and dependent admission are skipped.
Restore the named original bytes or leave the skip visible. Changed evidence,
unsupported identity/role/edition or failed assertions are **FAIL2**: no catalog
result is admitted; retain the attempt, fix the cause and retry new paths.

The author archive is available from the paper-linked
[Zenodo record](https://zenodo.org/records/20802211); exact download URL and
SHA-256 are in the binding JSON. Supply it privately where downloading is
permitted. The auditor performs no download/install. For a shallow clone,
restore Git commit `119326ac18b46a9a0c2e60441541f9199003e820` before auditing.
Do not use old generic extraction to overwrite the source-aware proposal or
change old source hashes to force a current-stage check through.
