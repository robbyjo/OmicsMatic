# MEME Suite programs, source roles and runtime development

**These new programs remain missing integrations.** A source build or original
process exit code is not a validated project workflow. The existing FIMO adapter
and native motif scanner retain their separate scopes. The catalog has 65 new
scoped integrations (68 including the three pre-existing adapters); none of the
programs below is credited by this development checkpoint.

## Correct program and resource identities

The unchanged 250-paper cohort now records 387 distinct tools and 2,442 paper-tool
observations. Seven distinct programs were added to the registry; two incorrect
assignments were removed. All 793 previous reviewed uses remain intact. Twenty-two
new reviews bring the total to 815, with 248 supporting paragraph pointers.
See [the source-role proof](omics-2023-2026/meme-program-role-coverage.json).

| Program or resource | Actual source role | Boundary |
| --- | --- | --- |
| MEME / MEME Suite | P133 de novo discovery; P155 names MEME 4.9.1; P013 combines several programs | Edition/command choices remain unknown where unstated. A newer source build does not prove historical equivalence. |
| MEME-format models | P007 downloaded and selected JASPAR matrices for FIMO | A file format does not establish a MEME discovery run. |
| MEME-ChIP, DREME, CentriMo | P013 discovery pipeline and central-enrichment checks | Preserve the historical pipeline tokens. Do not replace DREME with STREME or run only MEME and call the pipeline reproduced. |
| Tomtom | Seven roles: five comparisons and two reused SCENIC+ motif-cluster resources | P047/P061 describe clustering by the resource authors, not new Tomtom execution in these papers. Similarity does not establish occupancy. |
| STREME | P235 uses suite 5.5.4 for H3-associated intronic-VNTR k-mers | H3 does not establish a three-base motif width. Adjacent repeat-finder parameters are not STREME options. |
| HyPhy | P017 BUSTED; P066 aBSREL, BUSTED-MH and MEME | P066 MEME tests codon selection; it is unrelated to motif elicitation. The reported aBSREL edition is not propagated to the other models. |
| BWA-MEME | P113 names version 1.0.4 for mm10 EU-seq alignment | The BWA 0.7.x adapter does not supply this distinct implementation. The paper does not spell out the supplementary soft-clipping flag. |
| FIMO within MEME Suite | P215/P217 occurrence scans | The existing FIMO reviews remain unchanged. Do not transfer HOMER E-values or discovery settings to occurrence P/q tests. |

The P133 author-linked ATAC repository was captured at commit
`3aeb503348e52b1f0b88d47839427380250c0ea5`. Its eleven ATAC scripts contain no
MEME/Tomtom command and do not resolve the missing options. The original scripts
remain research inputs in the ignored development cache. This capture does not
authenticate historical execution, module files or external input resources.

## Original runtime preparation

The official MEME Suite 5.5.9 archive has SHA-256
`0406fb7b1dc27f6aab3d6d3a29ecdf617bbdd946690cfa97ca2129bc210bfd12`.
The user confirmed educational/research/nonprofit use under the
[official terms](https://meme-suite.org/meme/doc/copyright.html). No upstream
scientific source is incorporated into Java by this work.

Explicit development preparation in the isolated omics WSL distribution installs
permitted build/report dependencies. Analysis will not invoke that preparation.
Package plans reject upgrades and removals; downloaded package hashes, original
build logs, source inventories and every previous attempt remain retained.

The first build failed to link `-lz`. After permitted `zlib1g-dev` preparation,
the second build installed 884 files and preserved all 615 original C/header
files unchanged. MEME, Tomtom, STREME and CentriMo version probes passed.

The first sixteen original control processes exposed missing
`XML::Parser::Expat` and Ghostscript report dependencies. MEME/Tomtom exited 1;
STREME/CentriMo exited 0 despite failed HTML conversion. **All sixteen families
remain unadmitted** while full-family and native numerical checks are pending.
Partial models/tables are preserved, not repaired into completed results.
The permitted `libxml-parser-perl` and `ghostscript` packages were prepared for a
new reference configuration, because the original build records the graphics
helper path at configure time. The third build again preserves all 615 original
C/header files. All sixteen repeated original processes now exit 0: six MEME
fits (OOPS/ZOOPS/ANR, each forward/both strands), six Tomtom comparisons
(Euclidean/Kullback/Pearson, each forward/both strands), two STREME discoveries
and two CentriMo positional-enrichment analyses. These captured raw families
remain unadmitted while native numerical/output audits are pending.
Tomtom's three-motif target fixture retains the original small-database and
q-value-estimation warnings; it does not calibrate significance on a full database.
Required future gates include complete report validation,
unchanged runtime/input evidence, native audits, Java execution/admission,
packaged tests, documented examples and catalog/browser closure.

## Required warning and recovery behavior

**SKIP3 — dependent analysis skipped:** an exact required program, helper,
resource, build/install/download permission or supported capability is missing
or forbidden. Name it explicitly. Complete setup only where permitted;
otherwise leave the dependent analysis skipped. Optional report output must be
chosen explicitly; a failed report helper cannot silently count as a complete
requested output family.

**FAIL2 — no result admitted:** input, identity, process, output, numerical,
capacity, cancellation or finalization checks fail. Preserve raw outputs, every
warning and all failed attempts. Correct the named cause and use new paths.
Never treat a partial model, empty comparison, missing HTML report or exit code
alone as a completed scientific workflow.

MEME-ChIP/DREME support, paper-specific unknown settings and historical editions
remain open. Reused synthetic controls do not certify biological calibration,
production scale or whole-paper reproduction. Cell Ranger stays excluded. No
release, push, deployment or whole-goal completion is claimed.
