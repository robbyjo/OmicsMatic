# MALT DNA screening: catalog integration scope

The scoped MALT entry covers a complete declared DNA index-to-alignment
operation: original0.5.2 seed building, an **explicit native accession-mapping
alternative**, original BlastN fitting, complete SAM/RMA/aligned-read capture,
native scientific audits and exact full index handoff. It also provides portable
saved verification. The current status is recorded in
[tool-inventory.json](omics-2023-2026/tool-inventory.json); this does not grant
whole-tool, whole-paper or biological credit.

Start with [MALT052_WORKFLOW.md](MALT052_WORKFLOW.md) for copyable installation,
build, input-list, run and verification commands. Use the standalone development
JAR and its exact adjacent HTSJDK5.0.0 dependency. The released/canonical
executable and root dispatcher are unchanged.

## What the paper reports

[P002, Ancient genomes reveal a deep history of Treponema pallidum in the
Americas](https://doi.org/10.1038/s41586-024-08515-5), contains two distinct MALT
roles. Main Methods paragraph29 describes the eager/HOPS screen against dated
2017 references and does not report its MALT version. Supplementary section3.2
separately reports MALT0.5.2 for constructing a91-spirochete custom index and
screening36214 datasets. Its reported settings include BlastN/SemiGlobal,
identity90%, up to25 alignments per query and100000 per reference. The
superscript citation61 does not turn the version into0.5.261.

The declared0.5.2 implementation supplies these DNA index/build/screening
operations. It does not infer the main pipeline version or recover unreported
settings. Read the [source-bound role assessment](MALT_HOPS_PAPER_ROLES.md).
Original observation text, supplementary anchors and earlier implementation
scope notes remain retained; current availability is recorded separately.

## Select the method explicitly

**Use `--method native-accession-alternative`.** The original0.5.2 accession-map
path remains unqualified. Original MALT constructs the unclassified seed index;
Java creates a separate mapped index after checking all references and mapping
rows. Neither input identifiers nor an index are silently repaired. Keep
`index-build/native/index` and `index-build/derived/index`.

Select `--profile CUSTOM052` or `--profile STANDALONE` explicitly. CUSTOM052
represents the reported SemiGlobal/90%/25/100000 screen, together with the
declared0.5.2 assignment defaults documented by the workflow. STANDALONE is a
different Local profile. Inspect saved settings when overriding options.

**The historical2017/2021/91-genome reference collections and complete study
query populations are not reconstructed.** Supply the complete matching
references and accession mappings before attempting a historical reproduction.
Software controls use three mitochondrial references and cannot substitute for
those databases. The130GiB/16-worker request is representable as
`--heap-mib 133120 --threads 16`; validation does not claim to execute it.

## Validation and limits

The [complete workflow checkpoint](malt052-workflow-development/README.md)
retains five original build/alignment productions across four full populations.
The first four evaluate16862 queries and122 alignments; each uses all three
references/52244 bases. The current coordinator freshly produces population A
and verifies all earlier results. The first producer source remains captured;
later changes improve a diagnostic, Linux root-path support and inventory
traversal without changing the frozen scientific components.

All39 focused/adjacent tests pass on Windows JDK21 and25. Saved replay covers
four populations on Windows21 andLinux21 and population A on Windows25;
Linux25 execution is untested. Ten resealed contradictions are specifically
rejected, including a changed index handoff despite updated hashes. Three
prerequisite/capacity checks launch no original analysis. The public replay
restores all1317 logical evidence files, verifies all five workflows and rejects
all ten contradictions. Failed validation attempts remain retained.

Use `tools/literature/malt052_workflow/verify.py --help` for the complete restore
and replay command. This repeats no original index/alignment fitting. Saved
consistency checks and SHA256 provide no producer authentication or biological
truth. Full MALT protein/other modes, nf-core/HOPS equivalence, author environment
and whole-paper reproduction remain outside this scoped operation.

## Prominent failure and recovery rules

**Missing or forbidden required installation/runtime/execution: SKIP3 the
dependent analysis.** Supply the exact permitted Linux fitting provider or
modern-Java reader provider named in the diagnostics. If installation or
execution is forbidden, retain the skip and omit dependent analysis. Never use
an unrelated version or fallback method to get a passing run.

**FAIL2: no final result is admitted.** Keep `FAILURE.json`,
`WARNINGS_FAILURE.md`, both stage stderr logs and all original inputs/outputs.
Resolve the named format, identity, resource, process or scientific/integrity
problem, then retry a **new** directory. Complete-input capacities refuse rather
than truncate. A completed index can remain independently verifiable after a
later alignment failure. Empty, skipped or insufficient alignments never prove
biological absence.

The complete250-paper goal remains active: this entry does not complete the
remaining implementations or the paper-wide semantic completeness review.
