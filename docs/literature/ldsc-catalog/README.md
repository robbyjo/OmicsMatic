# LDSC catalog admission

**Current result:** one scoped native family admitted, for 128 new scoped
integrations (24 native Java, 104 upstream). The 250-paper goal remains open.
Use the [integration guide](../LDSC_CATALOG_INTEGRATION.md) for commands, source
roles, restrictions and required actions. Read [WARNINGS.md](WARNINGS.md) before
using a result.

This checkpoint reviews 44 source candidates and retains 42 roles. It adds
P019/P092, removes comparison-only P151, keeps discussion-only P177 excluded and
labels P220/P245 as reused resources. All 250 primary identities and XML hashes
were checked. All 1,219 prior reviews and unrelated records are preserved.

The scientific implementation was qualified in the separate
[native RG/Hsq evidence](../ldsc-rg-development/README.md). Its original
fingerprints and failed attempts are retained. This admission checks the saved
qualification, archive and current scientific source bindings; it does not
execute the scientific runtime again.

| Evidence | Purpose |
|---|---|
| `catalog-before.zip` | Exact eight predecessor metadata files from Git `118f202c848bdfe2cf61e07a5f27cbd37912935b`. |
| `PREVIOUS-SHA256SUMS` | Previous current-inventory checksum file, retained verbatim. |
| `REGISTRY.json` | All 485 cumulative definitions, with only LDSC changed. |
| `verifier-source.zip` | Exact catalog verifier, curated roles and boundary tests used for admission. |
| `checks.zip` | Admission plan, process records, ten passing tests, complete verification, snapshots and indexed read-back evidence. |
| `VALIDATION.json` | Closure counts, source guards and archive digest. |

The current [source-role census](../omics-2023-2026/ldsc-source-role-coverage.json),
[admission record](../omics-2023-2026/ldsc-catalog-validation.json) and reversible
review-file journal are separately checksummed in the current catalog manifest.
The manifest has 794 entries, including nine preserved parent evidence entries;
all were checked with no missing, extra or mismatched entries.

The bundle contains pointers, hashes and paraphrased summaries, not private
full-paper reading extracts. No historical result hashes are refreshed. The
local derived HTML is updated; website publication, a release and a remote push
are outside this checkpoint.
