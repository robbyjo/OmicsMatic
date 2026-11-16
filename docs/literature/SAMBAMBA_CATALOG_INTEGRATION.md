# Sambamba: catalog integration and remaining gaps

**Build current source. The frozen published v0.4.0 assets do not contain the
new command.** This is a scoped original-runtime adapter, with native Java
auditing and existing native indexing support. It is not whole-tool or paper
reproduction. The full 250-paper goal remains open.

The supported operations are original 0.6.6/0.8.2 duplicate marking/removal on
an explicit coordinate-sorted BAM, with complete captured-input auditing and
genuine runtime replay. Use a permitted Linux JVM and the exact pinned runtime.
The explicit request has 20 mandatory fields; it does not infer author settings
or read automatic project configuration.

```text
./gradlew executableJar
java -jar build/cli/omicsmatic-0.4.0.jar sambamba-tools --help
java -jar build/cli/omicsmatic-0.4.0.jar sambamba-tools --request /trusted/request.properties --out /results/NEW_ANALYSIS
java -jar build/cli/omicsmatic-0.4.0.jar sambamba-tools --verify /results/NEW_ANALYSIS --out /validation/NEW_REPLAY
```

On Windows, run `gradlew.bat executableJar` to build, then run original analyses
in the explicitly selected permitted Linux environment with Linux paths. A
native Windows analysis returns `SKIP3`. The [command guide](SAMBAMBA_TOOLS.md)
and [streaming workflow guide](SAMBAMBA_STREAMING_WORKFLOW.md) describe runtime
setup, exact downloads, all request fields, resource limits and result selection.
Choose `action=mark` to retain duplicate-flagged alignments or `action=remove`
to select the removal output. Genuine verification invokes the original tool;
metadata/checksum readback alone is insufficient for a new result.

## Source uses

| Paper | Source finding | Implemented coverage |
| --- | --- | --- |
| P082, [Nature Genetics](https://doi.org/10.1038/s41588-024-01999-x), PMC11631769 | Lists 0.6.5; operation unreported. | **Unsupported declared edition; no operation or paper-specific analysis is inferred.** |
| P110, [Nature Genetics](https://doi.org/10.1038/s41588-025-02409-6), PMC12695648 | 0.6.6 removed duplicate ChIP-seq alignments after fastp/Bowtie2 and before downstream coverage/insert-size and MACS3 analyses. | Source-defined removal profile; author flags, executable build, resource settings and complete input library/read-group metadata remain unreported. |
| P198, [AJHG](https://doi.org/10.1016/j.ajhg.2024.09.005), PMC11568759 | 0.8.2 marked duplicates after SAMtools combined donor PAR alignments across haploid cells, before FreeBayes. Alignment used hg38 with chrY PAR masked. | Marking profile; no physical removal or cell/UMI grouping rule is inferred. The doublet-barcode filtering order and full author flags remain unestablished. |

The complete frozen primary corpus was checked, including software declarations
outside method sections. P082 was missed because an inline citation joined the
name to `62`. Duplicate copies of that paragraph are one observation. The
selected author README and all eight `.Rmd` files at repository commit
`34dba322b1be72b40ca4c0e76fe31fada5668f66` did not resolve the operation; the
[bounded read-only search record](sambamba-catalog-integration/AUTHOR-CODE-REVIEW.json)
states exactly which files were checked. No author code or clinical data was
executed or ported for that search.

This transition adds one missed observation and two reviews, updates the
existing P110 review, and changes only the Sambamba family’s implementation
status. All 1,196 unrelated reviews and all other tool definitions/observations
remain unchanged; the exact original review bytes can be reconstructed from
the saved inverse journal. Counts become 123 new scoped integrations
(21 native, 102 upstream), 126 including existing adapters, and 264 missing
families. There are 446 families, 2,691 observations and 1,199 reviewed uses;
1,492 uses and paper-wide semantic completeness remain open.

## Warnings and required actions

**0.6.5:** no matching validated profile is provided. The real CLI refusal is
retained. Do not substitute 0.6.6. Skip the dependent analysis until the needed
operation and matching runtime profile are qualified.

**0.6.6 score overflow:** the unchanged 50,000-base quality-30 control fails the
original checked score conversion. Preserve this error and admit no analysis.
Use a separately qualified permitted edition/tool for a new analysis, explicitly
recording the change, or skip. Do not trim reads or change qualities to obtain
success. The same input passes the checked 0.8.2 profile.

**Original index metadata:** the tested 0.6.6 sidecars with unplaced tails have
inconsistent reference metadata. Preserve the original BAM/index and refusal.
Generate a separate index using the existing native Java `hts-tools --engine
native --method sam-index` command, audit the exact pair with `BamBaiAudit.audit`,
and explicitly select the audited index in a separately qualified downstream
workflow. No sidecar is silently repaired or substituted. Original workflow
receipts retain their collateral-index status.

**SKIP3 / FAIL2:** supply missing permitted prerequisites where installation is
allowed; otherwise skip dependent analysis and retain the named cause/action.
Any analysis, verification or recording failure admits no complete result.
Read `WARNINGS.md`, `FAILURE.txt` and raw phase consoles; retry new paths after
correcting the cause. Earlier exclusions and license restrictions still apply,
including the user’s Cell Ranger/ARC exclusion.

The [streaming evidence](sambamba-streaming-workflow-development/VALIDATION.json)
covers actual original runs/replays and four-million-record 64-MiB controls;
[index evidence](BAM_BAI_QUERY_QUALIFICATION.md) covers exact pairs and mapped
queries. Large raw BAMs, full ledgers and candidate JARs are retained locally,
not in public metadata. Saved record readers do not repeat science or establish
upstream executable authenticity. Existing original failures remain retained.

## Cumulative catalog verification

```text
python -B tools/literature/sambamba_catalog/catalog.py --verify --parent-checkout /trusted/clean-1a9ff82 --baseline-checkout /trusted/clean-357c9301 --rescan-sources --out build/NEW_SAMBAMBA_CATALOG_CHECK
```

Use the exact clean historical checkouts, primary cache and setup described in
the [separate application source transition](APPLICATION_SOURCE_TRANSITION_20261116.md).
The unchanged original parent verifier runs in its historical checkout. Only
the exact eight predecessor metadata byte strings are reconstructed for this
catalog transition; actual scientific sources and Git results are never
overlaid. The new consumer declares exact added source/evidence files, without
excluding an existing source. It validates its actual updated catalog separately.
The old standalone gates retain their original scope and refusal behavior.

The original streaming record reader requires the catalog to remain unchanged.
It correctly refused the initial unpublished catalog candidate after the
metadata transition. That candidate, refusal and browser records are preserved
locally under `build/sambamba-catalog/initial-candidate-artifacts` and
`build/git-publish/20261116-all-main-2/initial-candidate-preserved` in the parent
workspace. The new catalog consumer separately checks every immutable public
streaming file and archive member, actual scientific source pins, preserved v1
sources, and the exact eight historical metadata byte strings recovered by the
catalog inverse. It does not change or call a replacement for the historical
reader, claim that reader accepts the changed catalog, or repeat science.
Controls reject an altered historical metadata view. The revised candidate
also uses text for the existing per-paper workflow display and retains structured
qualification in a separate field, so unsupported editions remain readable.

The [admission proof](omics-2023-2026/sambamba-catalog-validation.json) records the
precise populations, source/evidence identities and scientific qualification
scope. Catalog verification checks source, metadata and preserved public
records; it does not repeat upstream analysis. P082’s unresolved operation,
unsupported edition and complete paper environments remain unqualified.
