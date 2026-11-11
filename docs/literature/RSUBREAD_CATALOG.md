# Rsubread catalog integration

The catalog credits the independently validated original bulk-counting workflow:
**90 new scoped integrations** (17 native Java subsets, 73 upstream adapters),
**93 including three existing adapters**, and **251 tools still missing**.
These are bounded workflows, not full packages or whole-paper reproductions.
The canonical executable and 18 unrelated worktree files remain unchanged.

The fixed cohort remains 250 papers: Nature 71, Nature Genetics 104 and AJHG 75,
from 2023-10-23 through 2026-10-23. It contains 400 observed tools from 438
definitions and 2,465 paper/tool observations. Six Rsubread uses now have primary
reviews: three existing reviews are refined and three are appended, giving
1,003 reviewed uses and 349 supporting references. All 997 other reviews,
399 other availability rows, paper identities, source anchors and observed-tool
memberships remain exact.

| Paper | Source-bound Rsubread use and restriction |
| --- | --- |
| P013 | mm9 UCSC-gene GTF featureCounts, single-/paired-end choices per sample, then positional cbind; DESeq2 is separate. Edition/full flags are unreported. |
| P029 | Yeast featureCounts, reporting Rsubread 2.23.2 and R 4.4.1 after BCL Convert/DRAGEN RNA. Source commit/binary and complete counting settings remain unidentified. |
| P133 | Article RNA settings and paired-end ATAC shared-peak counting are distinguished. Linked ATAC code calls Rsubread::featureCounts on a SAF-style data.frame, but leaves fragment-check/read-pair settings and edition unspecified. Its later sample rename is separate. |
| P139 | Generic loading of processed RNA-seq data into R; no function/edition/annotation/flags are identified. This is not evidence of a featureCounts call. |
| P172 | Exon counts after STAR; API, edition and feature/gene aggregation remain unreported. The article's literal 15 bp paired-end statement is retained. |
| P223 | featureCounts/junction reporting with custom GENCODE transcript annotations; no standalone juncCounts function is established. seqinr, Salmon/edgeR and nanopore steps are separate. |

Use [RSUBREAD_WORKFLOWS.md](RSUBREAD_WORKFLOWS.md). Exact original Windows profiles
provide 2.26.0/R4.6.1 and selected unchanged 2.23.2/R4.4.1 counting. Preserve the
unresolved paper revision among 22 upstream commits reporting 2.23.2, original
development/compiler warnings and all earlier failures. No paper edition or
flag is silently selected from an incomplete source statement.

**Require exit 0 AND root COMPLETED.json analysis_admitted=true for the whole
batch. SKIP3 skips dependent work; FAIL2 admits no earlier case.** Read original
logs, warnings, complete RDS objects, input mappings and process receipts. Private
GPL package/source export requires explicit permission; analysis installs
nothing. Linux counting/binding skips, while frozen native verification is
portable. Double-storage GeneID interpretation remains unsupported. Metadata
integrity and native structural checks do not independently recount alignments,
parse the original GTF with R semantics, establish junction annotations or
authenticate execution. Cell Ranger remains excluded.

The execution proof records 58 complete separate-original comparisons per
compiler artifact, real runtime/failure/cancellation/process controls and
copyable beginner examples. The catalog checkpoint adds a complete 250-source
audit, unchanged predecessor scientific gates, exact metadata-only inverse
descriptors, and desktop/mobile checks of cards, roles, warnings and counts.
The seven-path compatibility allowlist is unchanged; no scientific implementation,
old source hash, package binding or original runtime result is refreshed.

Run `python tools/literature/verify_rsubread_catalog.py` to check the separately
sealed catalog checkpoint. The full 250-paper implementation goal remains active.
