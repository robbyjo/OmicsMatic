# Source-review checkpoint: 22 newly recognized software names

**This amendment adds no implementation badge.** It corrects the catalog to 429 named software families, 2,668 observations and 1,079 individually reviewed uses. Coverage remains 103 new scoped integrations (84 upstream, 19 native); 267 named tools remain missing. The full 250-paper objective and source-semantic review remain open. See the [dated source-review guide](../P040_SOFTWARE_REVIEW.md) and [current catalog index](../omics-2023-2026/README.md).

All 250 exact primary XML sources were searched for the 22 names found during P040's Methods review. This yielded 186 distinct paper-tool observations and 273 named-tool/paragraph matches. Fiji/ImageJ appears in 51 papers, GraphPad Prism in 46 and FlowJo in 27. A DeepCell, Inc. company disclosure is excluded. Two P082 author-inventory statements establish software use without recovering analytical functions. The other 162 new observation summaries remain individually unreviewed; overall 1,589 observations remain without individual use reviews.

`CENSUS.json` contains every paper identity/XML hash and matching paragraph pointer/name. `ADDITIONS.json` contains the 186 new source records and 24 individual use reviews (22 P040, two P082). Full article paragraphs remain in the local XML cache and are not relicensed as project software. `catalog-before.zip` preserves exactly the eight predecessor catalog/review files. Every previous 2,482 observation, 1,055 review, paper identity and unrelated inventory row is unchanged. The current registry retains all 445 original matcher definitions and appends 22; 38 unobserved definitions remain intact.

The Rphenograph inventory now describes the separately qualified **0.99.1** candidate, its complete original clustering/native coefficient controls and retained failures. The reported **0.1.1** identity remains unresolved and missing. No automatic substitution, whole-tool or paper-reproduction credit is claimed. The previous source-review note is displayed as historical. All original scientific sources, verifiers and controls remain live and unchanged; only eight explicitly allowed catalog/review metadata paths can have historical views. The exact review-file inverse permits only the 24-record append.

`CYTOMAPPER_SOURCE.json` records recovered original 1.24.0 source, commit `f3e2850ae4ca6df9810eecf6de1b7755160da7c8`, with 553 member hashes. It is source intake, not runtime qualification. Both gating/visual phenotype review and explicit plotPixels visualization are reported roles. No cytomapper original analysis was executed here.

Validation includes the full 250-source rescan, all 18 adversarial preservation/scope tests, `checkWebsite` and Edge desktop1440/mobile-dark390 report checks. Source roles, literal ImageJ1.54t, missing status, 0.1.1/0.99.1 separation, collapsed historical notes and prominent recovery warnings are checked. Local test servers were closed. These development reports remain excluded from the public Pages deployment; no website was deployed by this amendment.

`PRESENTATION_REVISION.json` and `previous-local-catalog-text.zip` retain the first local text/proof/check/screenshots before spacing numeric/version labels clearly. `GUIDE_SNAPSHOT.json` and `previous-source-guide.zip` retain the guide before explicitly dating its checkpoint. No primary records, counts or scientific results changed during these presentation revisions. `UI_SELECTOR_REPAIR.json` retains the initial test-selector failure: DOM textContent concatenates the tool name and its reported-version label across a line break. The selector was corrected; report data were not changed to make the test pass.

Verify from the repository root:

```powershell
python tools/literature/p040_catalog/verify.py --out build/literature-review/monocle-runtime/NEW-source-check --rescan-sources
```

**SKIP3:** skip only dependent analyses when required permitted source/runtime/install/download/license/execution cannot be supplied. **FAIL2:** retain complete inputs, WARNINGS.md, status, logs and unadmitted models; correct the named cause and retry NEW output. No original fitting, RDS loading, producer authentication or biological validation is performed by this catalog verifier. Older qualification guides preserve their checkpoint totals; the current catalog index states later totals.
