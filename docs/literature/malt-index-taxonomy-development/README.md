# Native MALT taxonomy-index development evidence

See [the command guide](../MALT_NATIVE_INDEX_TAXONOMY.md) for building, mapping and replay. This operation derives a separately labelled index from a complete unclassified index and an explicitly selected accession namespace. It adds no whole-tool catalog credit.

- [VALIDATION.json](VALIDATION.json): pinned archive and source closure; six JUnit tests, three complete original alignment controls, raw RMA oracle equality and complete native geometry/aligned-FASTA checks.
- [controls.zip.xz](controls.zip.xz):294 logical evidence files in200 deduplicated objects, including full source/derived indices, all16,856 queries and all original/native SAM/RMA/aligned-FASTA outputs. Each file is limited to128 MiB and the unique evidence payload to512 MiB. No upstream executable, whole paper, protected GATK program or container image is included.
- [SAVED_REPLAY.json](SAVED_REPLAY.json): the public rebuild/replay passed on Windows JDK21/25 and Linux JDK21. It recomputes the full native mapping and checks captured source-oracle equality; it does not refit upstream alignment or authenticate the unsigned producer.
- [PREREQUISITE_CONTROLS.json](PREREQUISITE_CONTROLS.json): missing-JDK SKIP3, visible required actions and untouched existing output.
- [FAILED_HEADER_TAG_REVIEW.json](FAILED_HEADER_TAG_REVIEW.json): the original failed header-tag-only attempt, its harness assertion error and the corrected read-only observation of three zero taxonomy IDs. That attempt receives no positive mapping credit.

Read [WARNINGS.md](WARNINGS.md) before interpreting any output. The original0.5.2 accession-map path, whole MALT/HOPS/MaltExtract roles and the full250-paper implementation goal remain incomplete.

The [initial evidence bundle](initial-evidence/controls.zip.xz) and its original reports remain retained as historical evidence. [ASSEMBLY_CORRECTION.json](ASSEMBLY_CORRECTION.json) explains the added pre-mutation path guard. [NESTED_OUTPUT_REFUSAL.json](NESTED_OUTPUT_REFUSAL.json) verifies the guard and proves every corrected derived-index byte equals the index used in the original alignment/RMA controls. Those controls were retained without unnecessary refits; current compilation, derivation, all six tests and three public replays were rerun.
