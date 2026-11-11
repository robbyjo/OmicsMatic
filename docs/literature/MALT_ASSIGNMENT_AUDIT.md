# MALT assignment audit in progress

**Assignment validation failed and remains unadmitted.** This checkpoint adds a
verified data-export helper and retained source/audit evidence. It implements no
new LCA method and adds zero catalog-tool or biological credit. The full eager
adapter, paper roles and 250-paper implementation goal remain open.

The helper `tools/literature/java/NfcoreMaltTaxonomyExport.java` exported both
complete upstream control libraries from the verified immutable eager screening
bundle: original RMA6 files, SAM files, unmapped FASTQs and six complete database
files. All 14 named members were copied with byte checks, and the complete source
bundle was verified before and after export. No records were sampled and no
taxonomy file was substituted. This export success establishes data integrity,
not correct taxonomic assignment.

**Current failure and required action:** the original Java API audit loaded
1,601,128 taxonomy-map entries and 1,601,131 tree nodes from the database's
`taxonomy.map` and `taxonomy.tre`. The saved original MALT workflow log instead
reports loading `ncbi.map` with 1,703,606 entries and `ncbi.tre` with 1,703,609
nodes. The audit stopped at that discrepancy, before assigning reads. Determine
which exact taxonomy resources the original workflow actually loaded, capture
and verify those resources, and rerun in a **new directory**. Do not lower the
expected count, substitute the later HOPS taxonomy, or admit assignments merely
to obtain a passing check.

The earlier failed launch is also retained: original Java 8 rejected the newer
`ActiveProcessorCount` tuning option before analysis began. A new launch removed
that unsupported option while retaining the original runtime. Its separate
taxonomy failure remains unresolved. Neither failed attempt is a completed
scientific audit.

The saved workflow used MALT 0.4.1, built 24 May 2018, with bundled MEGAN Community
Edition 6.11.5. The original bytecode/API and logs show an unweighted Naive LCA
profile, 80% coverage and a top-score filter. These source details are audit
inputs; no native assignment implementation or equivalence claim follows from
reading them. Official historical source selections are recorded separately.
Their checked-in version strings may differ from nightly-built binaries, so
date proximity is not evidence of compiler-byte identity or an author commit.

To use the export helper, compile it against a compatible verified development
JAR containing `NfcoreEngineBundle` and `NfcoreScientificFiles`, then run its main
class with the verified engine directory and a **new** export directory. The
saved export used Linux Java 21 and the separate eager development artifact.
It does not alter the released JAR or install upstream software. An exception or
partial export without `SOURCE.json` admits no export result; retain it for
diagnosis and retry a new path after resolving the named error.

**If a required dependency or permitted installation is unavailable, skip its
dependent analysis.** Preserve complete inputs and warnings. Supply the exact
prerequisite through permitted setup, or use an allowed environment, before
retrying. Missing, failed or unassessable output is not a negative biological
finding. Cell Ranger remains excluded as requested.

The [checkpoint record](malt-assignment-development/VALIDATION.json) and archive
contain source/API selections, export metadata and the two failed audit receipts.
They contain no upstream executable JARs, images or complete private input/engine
bundle. Their verifier checks saved evidence and compiles the export helper; it
does not rerun or validate taxonomic assignments.

```powershell
python tools/literature/verify_malt_assignment_checkpoint.py --out build/literature-review/nfcore-runtime/my-malt-audit-checkpoint-check-1
```

The existing [screening-read checks](NFCORE_EAGER_SCREENING.md) and
[native HOPS statistics](HOPS_EVIDENCE.md) retain their separately scoped results.
This failed assignment audit does not expand those claims.
