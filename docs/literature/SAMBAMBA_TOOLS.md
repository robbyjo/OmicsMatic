# Sambamba duplicate marking and removal

**Warnings:** this command invokes the exact permitted original Sambamba
runtime. Missing prerequisites skip analysis (`SKIP3`); an execution or recording
failure admits no result (`FAIL2`). Generated original BAI sidecars are not
automatically accepted downstream. Original 0.6.6 has observed metadata and
quality-score failures; recovery is described below. Current-tree catalog
admission and the full 250-paper objective remain in progress.

The normal project entry point delegates to the unchanged streaming-v2
workflow, with identical arguments, result recording and genuine original
runtime replay:

```text
java -jar build/cli/omicsmatic-0.4.0.jar sambamba-tools --help
java -jar build/cli/omicsmatic-0.4.0.jar sambamba-tools --request /absolute/path/request.properties --out /absolute/path/NEW_RUN
java -jar build/cli/omicsmatic-0.4.0.jar sambamba-tools --verify /absolute/path/NEW_RUN --out /absolute/path/NEW_VERIFICATION
```

Run the analysis and verification commands in a permitted **Linux JVM**, using
Linux paths. A native Windows JVM skips this original-runtime profile. The
[streaming workflow guide](SAMBAMBA_STREAMING_WORKFLOW.md) describes setup,
the pinned download helper, limits, all 20 mandatory request fields and result
selection. Start with the [request example](../../examples/sambamba-streaming/request.properties)
and supply explicit values for the intended analysis. This entry point takes
its settings from that explicit request; it bypasses automatic project
configuration, as do the other captured-request original-runtime profiles.
There is no `--no-config` argument for this profile.

Choose `action=mark` to retain alignments with duplicate flags, or
`action=remove` to select the removal output. They are separate operations;
SAMtools or Picard output is not a substitute for original Sambamba. Both
original editions are byte-pinned, and replay actually invokes the original
runtime on captured inputs. A metadata-only readback does not establish a new
analysis or genuine runtime replay.

## Uses in the selected papers

| Paper | Reported edition and use | Limits on what the source establishes |
| --- | --- | --- |
| P110, [Nature Genetics](https://doi.org/10.1038/s41588-025-02409-6), PMC12695648 | 0.6.6 removed duplicate ChIP-seq alignments after fastp trimming and Bowtie2 alignment, before downstream coverage/insert-size visualization and MACS3 peak analysis. | Exact subcommand, complete flags, executable build, resource settings and complete read-group/library metadata are unreported. |
| P198, [AJHG](https://doi.org/10.1016/j.ajhg.2024.09.005), PMC11568759 | 0.8.2 marked duplicates after donor-level PAR alignments were combined across haploid cells, before FreeBayes calling. Alignment used hg38 with chrY PAR masked. | Marking is distinct from removal. No cell/UMI grouping rule, full flags or exact binary build is inferred. The paragraph does not establish the order of doublet-barcode filtering relative to duplicate marking. |

Project-selected parameters and original binary assets do not recover those
unreported author settings or reproduce either paper's complete analysis.
The [source-role candidates](../../tools/literature/sambamba_workflows/source-roles.json)
retain the primary paragraph and XML identities; they are separately labelled
and do not silently replace frozen paper-use reviews.

## Failure recovery and indexes

**0.6.6 score overflow:** the unchanged 50,000-base, quality-30 control exceeds
the original checked 16-bit score conversion, and both mark/remove attempts
fail. Keep those failures. Choose a separately qualified permitted edition or
tool for a new analysis, recording the edition change, or skip the affected
analysis. Do not trim reads or alter qualities to force success. The same
unchanged control passes the tested 0.8.2 mark/remove profile.

**0.6.6 index metadata:** indexes for the tested placed-reference data followed
by an unplaced tail have inconsistent reference metadata. Retain the original
pair and refusal. Generate a separate index with the existing native Java
`hts-tools --engine native --method sam-index` command, audit that exact BAM/BAI
pair with `BamBaiAudit.audit`, and explicitly select the audited index in a
separately qualified downstream workflow. Never replace a sidecar implicitly
or promote an old result receipt.

The [BAM/BAI query qualification](BAM_BAI_QUERY_QUALIFICATION.md) documents the
51-pair, 30,070-query and actual 64-MiB large-file controls. Its positive scope
does not override a failed original analysis or qualify every index consumer.
Read each run's `WARNINGS.md`, `FAILURE.txt` and raw phase consoles. Supply the
named permitted prerequisite where possible; otherwise skip its dependent
analysis and retain the exact cause and required action.
