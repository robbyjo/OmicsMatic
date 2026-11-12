# MALT and HOPS source roles in the fixed paper cohort

A complete scan of the 250 pinned full texts found explicit MALT/HOPS software use in P002, [Ancient genomes reveal a deep history of Treponema pallidum in the Americas](https://pmc.ncbi.nlm.nih.gov/articles/PMC11964931/). Its roles must remain distinct:

| Tool / role | Source use and boundary |
| --- | --- |
| MALT within eager screening | Metagenomic alignment against the stated 2017 bacterial/viral database; the extra eukaryotic selection is unspecified. The main text does not report a MALT version. |
| MALT custom TPA screen | Supplement section 3.2 reports **0.5.2**, a 2021 index with 91 spirochete genomes, BlastN/SemiGlobal, identity 90%, and up to 100,000 alignments per reference. This is separate from eager screening. |
| HOPS | Candidate assessment from MALT output and damage-related profiles. Low-read candidates can remain unassessable. |
| MaltExtract | Supplement section 3.3 names target-species RMA extraction, followed by manual assessment and a separate screen against 7,508 bacterial genomes. Its version is unreported. |

The [original supplement](https://media.springernature.com/full/springer-static/esm/art%3A10.1038%2Fs41586-024-08515-5/MediaObjects/41586_2024_8515_MOESM1_ESM.docx) separates version 0.5.2 from superscript citation 61; flattening those runs produces the misleading string `0.5.261`.

The current [0.4.1 development adapter](MALT_ALIGNMENT.md) does not implement the custom 0.5.2 role or its per-reference limit. Supply and qualify the required provider, complete dated references/mapping and inputs before that dependent analysis; otherwise skip it with explicit recovery instructions. A small software-control database cannot substitute for either historical database.

Two other literal matches were excluded: P110 uses malt extract as a growth-medium ingredient; P214 mentions an author's surname. Neither establishes software use. These findings identify catalog additions; applying them to the global catalog and qualifying all reported roles remain open.

## Version-specific provider warning

**MALT 0.5.2 database building and custom-screening analysis remain unqualified and skipped.** The [provider intake](malt-hops-paper-roles/MALT052_PROVIDER_INTAKE.json) pins the complete public package against the official repository SHA-256. The package includes Java 14.0.2, while its declared dependency is Java >=11. An initial Windows Java21 help probe timed out after60 seconds; it establishes no compatibility or analysis result. Its initial harness did not preserve child logs before that timeout, and the failed attempt remains explicitly labelled. No owned provider process remains.

Required action: qualify the permitted exact bundled Linux Java14.0.2 runtime with durable process diagnostics, then validate the version-specific database-building/screening calls against the paper's complete dated references and mappings. Qualify MaltExtract separately. If any prerequisite cannot be installed or supplied, skip the affected analysis. The [source-role review](malt-hops-paper-roles/SOURCE_ROLE_REVIEW.json) and [formatted version boundary](malt-hops-paper-roles/SUPPLEMENT_VERSION_BOUNDARY.json) retain primary-source pointers without copying complete paper paragraphs.
