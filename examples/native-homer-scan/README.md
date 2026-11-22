# Native HOMER fixed-motif example

Use `homer-scan` in the development JAR with the example motif and FASTA,
`--strands both --offset midpoint --alphabet mask-n --duplicate-ids error`,
and a new output directory. The first sequence has two forward AC sites and
one reverse site whose forward-oriented text is GT. Its midpoint anchors are
-3, 0 and 3. The second sequence is retained with zero scored sites.

This is a supplied-matrix score example; no enrichment P/q/E value, discovery
or biological binding probability is calculated. Java is the only dependency.
Read `WARNINGS.md`, the input/model/sequence/count ledgers and
[the workflow contract](../../docs/literature/omics-2023-2026/HOMER_NATIVE_SCANNING.md).
Failures return 2 and retain quarantined partials; correct the named cause and
rerun to a new directory. Broader HOMER integration remains unfinished.
