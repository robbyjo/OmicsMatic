# Complete Rphenograph 0.99.1 development evidence

**P040 reports version 0.1.1; its package identity remains unresolved. This is an explicitly selected 0.99.1 candidate, not the reported edition or a paper reproduction. No new catalog badge is awarded. The full goal remains open.**

Read the [workflow, Java API, exact request fields, installation and recovery guide](../RPHENOGRAPH099_WORKFLOW_DEVELOPMENT.md). The standalone [JDK21-built JAR](../rphenograph099-workflow-development.jar) and [JDK25-built JAR](../rphenograph099-workflow-development-jdk25.jar) target Java17. Their checksums are in `ARTIFACTS.json`. Neither bundles the R runtime or modifies the main executable.

`controls.zip` contains 1,790 complete logical files, 31,142,541 bytes, stored as 558 unique content objects. Every object and logical path is pinned in `VALIDATION.json`; `SHA256SUMS` covers the public evidence files. Hashes establish integrity, not producer authentication.

| Qualification | Retained evidence |
| --- | --- |
| Native integer-neighbor coefficients | All 863 original C++ cases and 105,921 returned rows; 637,388 assertions on each of Windows JDK21, Windows JDK25 and Linux JDK21. |
| Complete original clustering | Five complete controls: iris, prepared signed protein features with k40, identical tied rows, an empty-graph square and the original k1 failure. Three candidate clusterings are admitted; the other two remain unadmitted. |
| Independent complete comparisons | Every full/used neighbor index, raw coefficient, graph attribute, vertex, edge and all six community fields for all four returned models. Strict state identity and bytewise equal saved public graph/community state. Complete original model RDS bytes differ and are retained; raw-model bytewise identity is not claimed. |
| Packaged checks | 27 on Windows JDK21, 27 on Windows JDK25 and 32 on Linux JDK21: 86 total, including six actual Linux invalid-input refusals before original process launch. |
| Installation | Actual local-source and fixed-commit download/private-source installations against the existing exact dependency base; all 32 namespace versions and three Rphenograph function bodies checked. A fresh dependency-base download is not claimed. |
| Refused installation | Eight actual permission, missing-source/base, corruption and overlap refusals. No compiler or analysis launched. |
| Public restoration/rebuild/replay | Three complete public bundle restorations, current-source builds, coefficient comparisons and saved-component/refusal checks: 80 packaged checks. No original fitting or RDS loading repeated. |

`QUALIFICATION.json` retains the actual original and packaged results. `REPLAYS.json` records final public replays. `RETAINED_FAILURES.json` documents the original failures, raw model-byte discrepancy, unresolved version and retained early build metadata. Earlier complete controls and draft artifacts remain under `history/` and `builds/` in the archive. `DOCUMENTATION_CLARIFICATION.json` and `previous-request-documentation.zip` retain the initial guide/validation/replay receipts before naming all eight capacity fields explicitly; no scientific source or result changed.

`PRESERVATION.json` records unchanged original unfinished files and the canonical main JAR. `CURRENT_CATALOG_CHECK.json` confirms the existing catalog gate still passes: 103 integrations, 407 named tools and 245 missing tools. Its historical Rphenograph intake snapshot remains unchanged; the current candidate execution is documented here separately.

Replay from the repository root:

```powershell
python tools/literature/rphenograph099/verify.py --java-bin "$env:JAVA_HOME/bin" --out build/new-rphenograph099-replay
```

**Required action:** missing or forbidden runtime/compiler/install/execution produces SKIP3 and skips dependent work. FAIL2 admits no clustering: retain diagnostics, original outputs and `WARNINGS.md`, correct the named cause and retry NEW paths. Original k1 fails `Not a matrix.`; the square's empty graph, `NaN` modularity and four unassigned cells are preserved. Do not repair either outcome silently, substitute an edition, or infer biological validity from these controls.
