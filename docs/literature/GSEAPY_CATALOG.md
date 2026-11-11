# GSEApy catalog integration

The current catalog registers the scoped original-class workflow described in
[GSEAPY_EXECUTION.md](GSEAPY_EXECUTION.md). It adds one upstream adapter badge;
the native local-statistics and ranked helpers are components of that same
integration, not extra tool counts. The canonical executable is unchanged.

There are **88 new scoped integrations** (17 native Java subsets and 71 upstream
adapters), **91 including three existing adapters**, and **253 missing tools**.
The frozen 250-paper cohort has 400 observed tools, 2,465 observations, 993
reviewed roles and 344 supporting pointers. The larger goal remains active.

Five GSEApy reviews are appended and one existing GSEApy review is refined;
the prior version is retained in the exact archived catalog. All other 987
reviews and 399 tool inventory rows remain unchanged.

| Paper | Reported use | Interpretation retained |
| --- | --- | --- |
| P009 | GSEApy 1.0.4 Enrichr, GO Biological Process 2023, mouse | Separate up/down TISSUE lists and all-imputed-gene background; distinguish the separate topGO background. |
| P053 | Enrichr, GO Biological Process 2021 | Keep comparison-specific expressed, ABC-linked or tested-gene backgrounds; edition is unreported. |
| P062 | GSEA through GSEApy 1.1.8 | Callable, ranking, library, permutations and input matrix remain unspecified. |
| P079 | Reported gene-set variation and separate enrichment analyses | Edition and gsva-versus-ssgsea callable remain unresolved; neither patient results nor supplementary settings are inferred. |
| P098 | GSEApy 1.1.3, reported two-tailed hypergeometric enrichment | Original local statistics uses a one-sided upper tail. Preserve the unresolved method discrepancy. |
| P161 | GSEApy 1.1.2 EnrichR, WikiPathway 2023 Human | Preserve expressed-gene background; adjusted P < 0.05 selects the input genes, not an inferred enrichment correction family. |

Runtime analyses use explicitly supplied local libraries. Package editions do
not freeze historical remote Enrichr servers or gene-set contents. The six
source reviews do not imply that the tested synthetic workflows reproduce the
papers. Read every role's implementation-scope note in the catalog.

**Require exit 0 AND `analysis-status.json result_admitted=true`.** Read
`WARNINGS.md`, `native-audit.json`, original stdout/stderr, source errors and
process receipts. **SKIP3 skips dependent analysis** if exact requirements or
installation permission are unavailable. Prepare a separate permitted runtime
or retain the skip. **FAIL2 admits no case from the whole batch, including
earlier completed cases.** Preserve all failed/provisional output, correct the
named cause and retry NEW paths. RUN installs nothing. Cell Ranger is excluded.

The existing statistics, foundation and execution proofs remain immutable.
Their historical metadata checks intentionally require the catalog inputs from
those checkpoints. After this catalog extension, run their unchanged scientific
gates with the exact archived metadata view:

```sh
python -B tools/literature/gseapy_current_evidence.py
```

Only four historical catalog inputs are supplied from the pinned scFates
archive: papers, reviews, inventory and summary. Every scientific source,
runtime, numerical verifier, artifact and parent proof resolves to the live
workspace and retains its original strict checks. This historical workflow
verification grants no new catalog credit; the current catalog proof separately
checks the new roles, availability, source relationships, rendered pages and
warnings. Do not refresh old scientific hashes or treat a historical missing
badge as the current catalog state.
