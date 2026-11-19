# Draft Seurat catalog admission

**Checkpoint only: no apply/verify driver or completed cumulative admission.**
The canonical catalog is unchanged. The original RNA workflow is already
qualified; see the [handover](../../../docs/literature/HANDOVER_2026-11-19_SEURAT_CATALOG.md).

- `reviews.py`: 50 individual positive decisions and excluded P083, contextual
  historical editions, overlap and unsupported author roles.
- `P160-SUPPLEMENT.json`: author-PDF identity, retrieval location and seven
  selected page hashes; no full text or PDF distribution.
- `primary.py`: source-only audit of all 250 XML identities and exact PDF
  pages. Requires pinned pypdfium2 5.13.0. Missing prerequisites give SKIP3;
  changed/inconsistent evidence gives FAIL2. Neither admits metadata.
- `model.py`: deterministic in-memory proposed transition of eight documents
  from Git `9574a7b1...`; not a catalog writer or qualification.
- `test_catalog.py`: sixteen frozen-checkpoint checks against the public
  census and exact Git predecessor; no paper re-extraction or model fitting.

From the repository root:

```powershell
python -B tools/literature/seurat_catalog/test_catalog.py
```

The tests expect live metadata at this handover predecessor. Full source audit
invocation and remaining gates are in the handover. Its output directory must
be new. Never weaken the old Methods-only intake to treat P160's table label
as proof. Keep PDF evidence separate from XML supporting anchors, preserve old
source guards/P061/prior reviews, and add only the necessary compatibility
delta after completing predecessor and scientific qualification validation.
