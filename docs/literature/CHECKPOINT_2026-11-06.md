# Literature implementation checkpoint: 6 November 2026

The catalog contains 250 papers: 71 from Nature, 104 from Nature Genetics and 75
from the American Journal of Human Genetics. The retained search window is
23 October 2023 through 23 October 2026. No extension was needed.

There are 387 distinct named tools and 2,446 paper/tool observations. Current
validated support comprises **72 new scoped integrations: 16 native Java
implementations and 56 upstream adapters**. Including three existing adapters,
75 tools have validated support. Another 256 named tools remain missing;
47 have method overlap, seven have lookup support, one has export support and
Cell Ranger is excluded at the user's request. These categories sum to 387.

**The implementation goal remains in progress.** Integration credit covers the
operations, versions, inputs and limitations described in each tool's guide.
It does not establish whole-package reproduction, whole-paper reproduction or
biological accuracy. Paper-wide semantic completeness review remains open.

The source checkpoint adds the completed Nextflow, Salmon, Tomtom, bigWig,
StringTie, chromVAR, DESeq2, Signac/Seurat and HTSeq work and their retained
validation evidence. Separate native MEME site/scoring and STREME statistical
components have passed their scoped checks; they receive no additional catalog
implementation credit.

The approved MAFFT verifier metadata refresh is already reflected in the saved
source hashes. The current offline verifier passes: 57 protocols, 35 completed
alignments, 13 expected skips and nine expected failures across six editions.
The original evidence archive is retained. This verification does not rerun an
aligner or establish biological alignment accuracy.

**Required action before analysis:** read the current
[warnings and recovery instructions](omics-2023-2026/WARNINGS.md) and the relevant
tool guide. Install or download dependencies only where permitted. An unavailable
or forbidden required dependency means **SKIP3: skip the dependent analysis**.
An input, integrity, process, capacity or finalization error means **FAIL2: no
result is admitted**. Preserve logs and partial evidence, correct the reported
cause and retry using the documented new output/session paths. A success from a
different operation or an older attempt does not clear the failed attempt.

The newer MEME discovery execution adapter remains unfinished and local. Its
latest dependency inspection failed because `/etc/resolv.conf` changed during
the complete host census; that attempt admits no scientific result. Existing
validated MEME components and Tomtom support retain their separate scopes.
Unrelated Study changes are also preserved locally outside this checkpoint.

This source push does not publish a release, replace the canonical executable or
deploy the website. Previously released JARs do not acquire these additions from
a source push. Use the current source and the version-specific development
instructions linked from the [catalog](omics-2023-2026/catalog.md).
