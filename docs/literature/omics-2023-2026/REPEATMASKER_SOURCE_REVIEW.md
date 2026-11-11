# RepeatMasker: eleven paper-use descriptions

**Eleven source roles verified; declared tool operations are available in development source.** Three precomputed-annotation reuse roles and eight search/masking roles retain their distinct settings and limitations. The catalog has 703 reviewed uses and 203 supporting paragraph pointers across 250 papers, 379 named tools and 2,428 primary observations.

Counts are **54 new integrations, 57 including existing adapters and 265 missing**. Read the [public workflow and recovery guide](REPEATMASKER_PUBLIC_WORKFLOW.md), [scoped validation](repeatmasker-validation.json) and [per-role restrictions](repeatmasker-role-coverage.json). Unsupported reported editions must remain skipped unless an exact supported profile is supplied; none is silently substituted. This is not whole-paper reconstruction.

An annotation track does not establish a new RepeatMasker program run. Historical releases are not silently replaced by a current profile. Unreported settings and resource identities need explicit review. In particular, a paper mentioning default Dfam does not establish Dfam 3.7; a RepBase-dependent workflow requires an appropriately licensed local resource, otherwise leave that analysis skipped. Downstream classification, assembly curation, transcript filtering and separate software remain separate operations.

Paragraph indexes below refer to the frozen full-text XML extraction. They are not publisher HTML anchors. The [source validation record](repeatmasker-source-validation.json) contains the exact XML and paragraph hashes. The [review rows](../../../tools/literature/repeatmasker-use-reviews.json) retain the earlier P164 object and its explicit Methods revision.

## P007 — precomputed resource reuse

[DOI](https://doi.org/10.1038/s41586-026-11053-x) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13668659/) · reported version: **not reported**.

Excluded candidate human substitutions overlapping low-complexity, simple and satellite RepeatMasker regions downloaded from UCSC, alongside separate TRF and ENCODE region filters.

**Scope and remaining choices:** This is reuse of repeat annotations, not evidence of running RepeatMasker. Keep the UCSC resource/build and class definitions explicit; genotype-frequency filters and sequence-synthesis constraints are separate.

Primary catalog paragraph 31; SHA-256 `2999e1e61877529f7b8e9569bd1c246e9fa750d2bb1cbad51256f3a082fac5a2`.

## P011 — precomputed resource reuse

[DOI](https://doi.org/10.1038/s41586-026-11048-8) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13645644/) · reported version: **not reported**.

Intersected amplicon junction coordinates with TA/AT-rich repeats from the UCSC RepeatMasker track using BEDTools 2.31.1 and each dataset's hg19 or hg38 reference.

**Scope and remaining choices:** The motif examples do not define a complete selection rule or junction-to-BED projection. RepeatMasker search execution is not reported; require explicit motif/resource/build and interval choices rather than guessing them.

Primary catalog paragraph 64; SHA-256 `d3d7cbdb12c841726e540fb2671c42352a0f39fecd93da8ce10cb4a0ad7eb127`.

## P033 — extended allele search and classification

[DOI](https://doi.org/10.1038/s41586-023-06842-7) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10733147/) · reported version: **4.1.2-p1**.

Scanned extended variant alleles with RepeatMasker 4.1.2-p1 using -species human -gff -s -norna -nolow, then classified mobile elements with inclusive 75% consensus and variant-coverage thresholds.

**Scope and remaining choices:** The six-type classification and complete/fragment labels are downstream operations. The reference library release, grouping of split insertion records and overlap/extent conventions require review; do not substitute current4.2.6 or treat -nolow as merely an output-row filter.

Primary catalog paragraph 49; SHA-256 `b30b1545351da298f3f1af3cf91b4f9816d6277b18cda632fde20a6a6fa6818f`.

## P043 — precomputed resource reuse

[DOI](https://doi.org/10.1038/s41586-023-06824-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10719105/) · reported version: **not reported**.

Used HOMER with UCSC mm10 refGene and RepeatMasker annotations to annotate transposable-element overlap at cCREs before separate mixture-model analyses.

**Scope and remaining choices:** The paragraph supports annotation reuse, not a new RepeatMasker search. HOMER annotation and R mixtools2.0.0 model P-values are separate; import the exact resources and declare overlap and subclass definitions.

Primary catalog paragraph 60; SHA-256 `3af05361793387acad85628982566d64b238bafd3b0cf16c60492556ad367ee5`.

## P101 — library homology search

[DOI](https://doi.org/10.1038/s41588-023-01571-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10703678/) · reported version: **4.0.9**.

Annotated plant-genome repeats with RepeatMasker 4.0.9 using -e rmblast -div 40 -norna after RepeatModeler2.0.1 repeat identification and classification.

**Scope and remaining choices:** MAKER2, protein-coding gene prediction and transcript assembly are separate. Retain the generated repeat library and the exact historical engine/software choices; printed divergence values do not expose all internal search precision.

Primary catalog paragraph 35; SHA-256 `c55c77a96ef7517b8bd678bbff6f001825b9525e05b670dd40995b389c87275d`.

## P109 — library homology search

[DOI](https://doi.org/10.1038/s41588-024-01967-5) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11631756/) · reported version: **4.1.2**.

Used RepeatMasker 4.1.2 for homology-based TE annotation across29haplotypes with nonredundant repeat catalogs built using RepeatModeler2.0.4 and EDTA2.0.1.

**Scope and remaining choices:** Library generation, catalog combination and gene annotation are separate operations. Full RepeatMasker flags and exact library construction are unstated; a supplied-library workflow requires explicit provenance and assembly declarations.

Primary catalog paragraph 27; SHA-256 `b10dee046ccb2eefa3869f3a1ad0481409d1cad0ad2bf7719ae63749aee00162`.

## P110 — library homology search

[DOI](https://doi.org/10.1038/s41588-025-02409-6) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12695648/) · reported version: **4.1.2-p1**.

Generated repeat annotations for species lacking them by mapping RepeatModeler2.0.2a consensus repeat libraries against genomes using RepeatMasker4.1.2-p1.

**Scope and remaining choices:** RepeatModeler2 included its LTR-finding module and is separate from RepeatMasker. Species with existing annotations are not automatically new search runs; complete flags and resource releases remain unspecified.

Primary catalog paragraph 58; SHA-256 `ee273b14ee57e081da6c0d8e1be28a53afcb678569d6868362e37fb7debb8acf`.

## P164 — species search and contig interpretation

[DOI](https://doi.org/10.1038/s41588-025-02367-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC12597820/) · reported version: **4.1.5**.

Used RepeatMasker4.1.5 with the default Dfam library in Mus musculus mode to annotate repeats in T2T mouse assemblies, including centromeric repeats supporting placement of unplaced telomeric contigs.

**Scope and remaining choices:** Methods paragraph40 resolves the version/species/default-Dfam choice, but not the exact Dfam release. Targeted BLAST validation, Hi-C/MashMap placement and manual assembly curation remain separate; an official bundled Dfam3.7 resource does not establish the study's exact library identity.

Primary catalog paragraph 37; SHA-256 `67f247b7a5f5b590b5ce1383118607ba18e4244429f9ff463cc8573f9f6975cf`.
Supporting paragraph 40; SHA-256 `369b442445d8c6e8eae039d8e7590249abd4291ca09316fe94dc91317d8e5c64`.

## P171 — library masking

[DOI](https://doi.org/10.1038/s41588-026-02678-9) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447107/) · reported version: **4.1.1**.

Masked plant genomes with RepeatMasker4.1.1 using an EDTA-fragment library whose unknown TE sequences were classified with TERL, then merged and deduplicated annotations to obtain TE annotations and softmasked assemblies.

**Scope and remaining choices:** The softmasked product is described for the combined workflow; the exact RepeatMasker masking flag and merge rules are not provided. EDTA and TERL inference remain separate and the library provenance must be explicit.

Primary catalog paragraph 36; SHA-256 `50b033273877d2e117d34f7c8fb62b05777d9a7763ee36dd131a20908feea073`.

## P198 — simple repeat masking

[DOI](https://doi.org/10.1016/j.ajhg.2024.09.005) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC11568759/) · reported version: **4.1.5**.

Masked simple repeats in Y-chromosome assemblies with RepeatMasker4.1.5 before separate tandem-repeat masking with TRF4.09 and progressiveMauve alignment for clock-model comparisons.

**Scope and remaining choices:** The paragraph does not specify -noint or its repeat library. TRF, multiple alignment, window/gap filtering, tree-puzzle likelihood tests and R multiple-testing correction are separate; do not infer their settings as RepeatMasker options.

Primary catalog paragraph 18; SHA-256 `86dbadfd372bd545e5e15886e66294c20d21100c954ca1c8362049b4441079b4`.

## P248 — homology search and transcript filtering

[DOI](https://doi.org/10.1038/s41588-026-02682-z) · [Primary full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC13447089/) · reported version: **4.1.2-p1**.

Annotated genome repeats using RepeatMasker4.1.2-p1 with homology evidence from RepBase20180823 after RepeatModeler2.0.3 library construction; downstream gene annotation filtered transcripts overlapping repeats by more than20%.

**Scope and remaining choices:** The exact library combination and RepeatMasker flags are not given. RepBase requires an appropriately licensed local resource; RepeatModeler, gene prediction and strict transcript-overlap filtering remain separate. Transcript span versus exon-union denominators are not specified.

Primary catalog paragraph 43; SHA-256 `6e413cb59f116f49368d799bea6077eecce578593ecd5227be0c58b01eb7dc0d`.
