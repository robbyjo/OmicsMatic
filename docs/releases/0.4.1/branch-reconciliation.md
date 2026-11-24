# v0.4.1 branch reconciliation

All local and remote branch tips inventoried on 2026-11-24 are included in the release ancestry. PR #2 is integrated with its source changes. Most feature branches were already ancestors of main.

Nine November 22 recovery commits explicitly label their payloads historical or unfinished and instruct against replacing current main. A file-level audit found no paths absent from current main in these snapshots. Their history is merged using the current tree; newer qualified implementations, evidence and catalogs retain their current bytes. The original branch refs and payloads remain reachable.

The obsolete v0.3.1 branch has patch-equivalent MAD winsorization and syntax work already in main. Its useful pipeline-parser test is carried forward; its old version and documentation do not replace current release metadata.

| Historical branch | Tip | Same paths | Different paths |
| --- | --- | ---: | ---: |
| `codex/handover-20261122-owner01` | `152cb84efd2972a3e8c96db65e821f7cfa2a28d1` | 259 | 21 |
| `codex/handover-20261122-owner04` | `dcef4643d8c97499a5fb3a044696599ccb064b61` | 1513 | 343 |
| `codex/handover-20261122-owner29` | `f13f17816afbfa078403a8dfecd36687a4f75bab` | 0 | 4 |
| `codex/handover-20261122-owner30` | `02e7a12fe2605899e8b1d5c065d27a61277c7844` | 0 | 4 |
| `codex/handover-20261122-owner31` | `62404a9628bca2d3560983f64c7b5e3ebbb1275a` | 3 | 1 |
| `codex/handover-20261122-owner41` | `838eb0fa089ba3e9cc2f70ddaac8193adad39edb` | 14 | 3 |
| `codex/handover-20261122-owner43` | `da785772264f9d50e036b34c3a2627c2a0f15ce5` | 4 | 3 |
| `codex/handover-20261122-owner45` | `0348be27572a4262efff162ecb4261e3976159e7` | 21 | 6 |
| `codex/handover-20261122-owner59` | `691a39bc6fc72a6aa6355aef7641dda426a4d5a7` | 1 | 5 |
| `codex/release-v0.3.1` | `c26b72c55e43f3eb5f89505d92a3a83aee9de5b5` | 0 | 16 |

The complete file/blob comparison is retained locally in `build/release-v0.4.1-20261124/historical-branch-audit.json`. Uncommitted work in the shared development checkout is preserved and is outside this release.
