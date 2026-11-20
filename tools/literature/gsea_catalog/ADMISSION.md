# Guarded native GSEA catalog admission

Run with Python 3.11+ without `-O`, in an isolated checkout of the declared
baseline. Source extraction needs the exact 250 XML files and the P077
supplementary PDF identified in `docs/literature/gsea-catalog-sources/AUXILIARY.json`.
Use its recorded pypdf version for exact page witnesses. Supply/install permitted
dependencies or retain the affected fresh audit as SKIP3; never substitute a PDF
or refresh an old hash. The complete original P096 scripts are public licensed
source evidence and are never executed by this owner.

The predecessor checkout must be clean at
`0de612c6182b87d42a1d574af5f06a99630a0924`. Every output must be NEW:

```powershell
python -B tools/literature/close_gsea_catalog.py --plan --primary-cache PATH/EXACT-XML --p077-supplement PATH/EXACT-PDF --historical-checkout PATH/EXACT-EDGER-CHECKOUT --out build/NEW-plan
python -B tools/literature/close_gsea_catalog.py --inspect --plan-dir build/NEW-plan --primary-cache PATH/EXACT-XML --p077-supplement PATH/EXACT-PDF --out build/NEW-inspect
python -B tools/literature/close_gsea_catalog.py --apply --plan-dir build/NEW-plan --primary-cache PATH/EXACT-XML --p077-supplement PATH/EXACT-PDF --out build/NEW-apply
python -B tools/literature/close_gsea_catalog.py --verify --primary-cache PATH/EXACT-XML --p077-supplement PATH/EXACT-PDF --out build/NEW-fresh-verify
```

Omit both source arguments for a saved-evidence verification after admission;
that mode does not re-read primary XML/PDF. The unchanged previous owner runs
only in its exact old checkout. No source overlays, scientific-code inverses,
historical runtime refits or complete-paper reproduction are implied.

The global dispatcher was qualified before this stage and remains byte exact.
Its three-line inverse is recorded alongside the 28-review append-only delta.
Old reviews, raw inputs, complete failures, original/source objects and retained
qualification packets must remain intact. SKIP3 names the prerequisite to supply;
FAIL2 admits no complete result and names the cause to correct in NEW output.
