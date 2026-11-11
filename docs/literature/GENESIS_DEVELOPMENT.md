# GENESIS development status

**GENESIS remains unimplemented in the toolbox catalog.** A private runtime and function inspection are available for development; neither admits a project analysis. SNPRelate KING coefficients and ordinary PCA do not implement GENESIS PC-AiR, PC-Relate or relatedness-adjusted models.

The citation-normalized review found five papers in the fixed 250-paper collection:

| Paper | Reviewed use | Remaining specification |
|---|---|---|
| P128 | Relatedness/population structure and a kinship-adjusted polygenic-score association; version 2.16.1 | Exact functions and the referenced supplementary model details |
| P130 | PC-AiR for ten ancestry components; version 2.26.0 | Exact input population and full function settings |
| P142 | GLMM association with a sparse PC-Relate kinship random effect | Package version, exact functions and model settings |
| P191 | PC-AiR in non-European samples after SNPRelate KING-robust | Exact GENESIS version and settings; European PLINK PCA and SAIGE GRMs remain separate |
| P219 | Twenty components after specified variant/region selection | Exact GENESIS function and version; PC-AiR is not inferred |

Primary paragraph hashes and reviewed roles are in [the corrected role supplement](omics-2023-2026/snprelate-genesis-primary-roles-v2.json). That frozen census snapshot retains its preparatory status; [source-role coverage](omics-2023-2026/snprelate-role-coverage.json) records the subsequent catalog application. Ordinary biological uses of the word “genesis” are excluded.

The inspected official release is **GENESIS 2.42.0, GPL-3**, using Windows R 4.6.1. The original source archive and official Windows binary were checked separately. A new private library installed 98 packages, with downloaded archives, versions, licenses and installed-file hashes retained. The existing R distribution and other private libraries were preserved. This current release does not reproduce the papers' reported 2.16.1 or 2.26.0 versions.

To prepare a new development runtime where installation and downloads are allowed:

```powershell
python tools/literature/intake_genesis_runtime.py --out build/literature-review/genesis-runtime/my-intake-1 --allow-download
python tools/literature/setup_genesis_reference.py --intake build/literature-review/genesis-runtime/my-intake-1 --out build/literature-review/genesis-runtime/my-runtime-1 --allow-download --allow-install
```

These are source-checkout preparation helpers, not an analysis command. Prefer existing admitted private prerequisites. Compiled dependencies require compatible official Windows binaries in this profile. Pure R/data dependencies can use source packages without adding a compiler. The helper does not install a new toolchain to work around a missing compiled binary.

**Missing or forbidden prerequisites: SKIP3 and skip the dependent analysis.** Read `WARNINGS.md`, `COMPLETED.json` and the named cause. Supply the exact permitted prerequisites and retry new private paths; otherwise leave the analysis skipped. Do not substitute another package, version, model, ordinary PCA or a repaired kinship matrix. Setup success alone does not admit a result.

Remaining work includes complete original controls, explicit identity/relatedness/model contracts, native methods where feasible, supervised original execution, full saved-result verification and clear result dispositions. No public GENESIS execution adapter or whole-paper reproduction is claimed by this checkpoint.
