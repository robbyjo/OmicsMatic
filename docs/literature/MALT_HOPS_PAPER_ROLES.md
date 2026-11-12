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

Two other literal matches were excluded: P110 uses malt extract as a growth-medium ingredient; P214 mentions an author's surname. Neither establishes software use. The three tool additions are now applied to the catalog, retaining all prior observations and reviews. MALT and HOPS have main-JATS anchors; MaltExtract has a separately typed supplementary DOCX anchor. Qualifying all reported operations remains open.

## Version-specific provider warning

**Historical MALT 0.5.2 analyses remain unadmitted.** The [provider intake](malt-hops-paper-roles/MALT052_PROVIDER_INTAKE.json) pins the complete public package against the official repository SHA-256. The package includes Java 14.0.2, while its declared dependency is Java >=11. An initial Windows Java21 help probe timed out after60 seconds; it establishes no compatibility or analysis result. Its initial harness did not preserve child logs before that timeout, and the failed attempt remains explicitly labelled. The bundled Linux Java14 runtime subsequently passed version/help checks. Three original build/alignment controls and complete Java geometry/counter/aligned-FASTA audits passed; this does not qualify RMA classification or a production 0.5.2 adapter.

**Accession-map classification is still unqualified.** Isolating mapping from organism-name headers produced taxonomy IDs `0, 0, 0` for both tested versioned and unversioned two-column maps. The same outcome persisted with neutral header descriptions and taxon-name parsing explicitly disabled. The initial named-header control cannot establish that the supplied accession map was used. Preserve those outputs; do not infer successful map application from exit zero or from a populated index built with organism names.

Required action: establish a complete positive accession-mapping control with a permitted validated provider or explicitly declared mapping method before admitting that dependent taxonomic analysis. Validate RMA/native assignments and the version-specific controller, then provide the paper's complete dated references, mappings and query population. Qualify MaltExtract separately. If any prerequisite cannot be installed or supplied, keep the affected analysis skipped. The [source-role review](malt-hops-paper-roles/SOURCE_ROLE_REVIEW.json) and [formatted version boundary](malt-hops-paper-roles/SUPPLEMENT_VERSION_BOUNDARY.json) retain primary-source pointers without copying complete paper paragraphs.

The effective naive LCA coverage in this exact 0.5.2 build is 80%. Its help displays 100% for the weighted-mode option, which is parsed only when weighted LCA is enabled. The constructor and writer call path retain 80% for the default naive path. Neither the help display nor these small software controls establish the authors' exact environment or historical biological results.
