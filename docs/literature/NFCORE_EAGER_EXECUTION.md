# Eager execution development and native read filtering

**The complete eager adapter and its paper roles remain unvalidated.** These
changes add a tested native Java BAM helper and original-source execution
controls. They do not add catalog-tool credit, reproduce the authors' environment
or admit biological conclusions. The [source audit](NFCORE_EAGER.md) retains the
three distinct paper uses, provider conflicts and historical database requirements.

**If a required installation, runtime or complete input is missing or forbidden,
skip its dependent analysis.** Retain the original inputs, failed attempt and
warnings; supply the exact prerequisite or use an allowed environment before
retrying. Do not replace failed or unassessable output with a negative finding.

## Native BAM helper

`org.jlinalg.workflow.NfcoreEagerReadFilter.filterBam` implements the original
`bin/filter_bam_fragment_length.py` rule in Java. Despite the original helper's
name, its threshold is **query sequence length, including soft clips**. It does
not use template length, inferred fragment length, aligned span or MAPQ. The
original workflow performs MAPQ filtering in a separate samtools step.

```java
var result = NfcoreEagerReadFilter.filterBam(
    Path.of("original.bam"), Path.of("new.filtered.bam"),
    30, true, 10_000_000);
```

The arguments after the paths are minimum query length, whether to include
unmapped records, and the complete input-record capacity. `false` excludes
unmapped reads before applying the length threshold. The original integer parser
allows negative thresholds; the Java rule preserves that behavior. Records retain
their original order, names, flags, tags, sequences, qualities and header fields.
Compression/encoding may differ from pysam. Strict BAM/CRC reading applies.

The output must be a **new path**. Existing files and the original input are not
overwritten. Exceeding the record capacity refuses the operation; it does not
admit a truncated result. A partial new BAM remains for diagnosis after failure.
A successful zero-record filter count does not establish a negative biological
finding or permission to use that output in another analysis.

This method uses the project's existing HTSJDK dependency and requires Java 17
or newer. It needs no Python, samtools, Conda, container or downloaded upstream
runtime. The source-derived helper is available separately from the original
pipeline, whose source and Python helper remain unchanged in execution controls.

## Build and run the development CLI

On this Windows development workspace, the builder uses the Java 21/25 locations
configured in `tools/literature/validate_nfcore_engine.py` and the previously
published, checksummed development dependency JAR. Choose a **new** build path:

```powershell
python tools/literature/build_nfcore_eager_source.py --compiler 21 --out build/literature-review/nfcore-runtime/my-eager-build-1
java -cp build/literature-review/nfcore-runtime/my-eager-build-1/nfcore-eager244-source-control.jar org.jlinalg.cli.NfcoreEagerFilterCli --input original.bam --out new.filtered.bam --minimum-length 30 --include-unmapped true --maximum-records 10000000
```

The builder copies the dependency classes into a separate development JAR; it
does not replace the released executable. If those configured JDK locations are
unavailable, configure an allowed matching development environment first. No
analysis or installation occurs during this build. The generated Java 17-compatible
CLI works on the tested Windows Java 21/25 and Linux Java 21 runtimes. Run its
`--help` for the complete required options. Exit 2 means no result was admitted;
read the explicit error and required action. No index is created by the helper.
Sort/index separately when required for region-based downstream access, retaining
the original filtered output and declaring the extra operation.

## What was checked

The original pinned eager 2.4.4 Python/pysam helper produced 36 complete reference
populations: both original default-workflow mapped BAMs and a separate 12-record
boundary fixture, each tested with six length thresholds and both unmapped modes.
The boundary fixture covers soft/hard clips, TLEN signs, absent sequence,
secondary/supplementary/duplicate flags and retained NaN/Inf auxiliary values.
Both Java compiler builds matched every header field and complete ordered SAM
record on Windows Java 21/25 and Linux Java 21: **216 complete comparisons**.
Another **24 refusal controls** checked existing outputs, same-path input/output,
capacity and malformed input. Optional SAM tag order alone was canonicalized.

The final packages retain the original MIT notice. Their executable classes match
the corresponding tested filter builds exactly, and both final CLIs were compared
again using the original provider. A focused regression test also compares all
36 original BAM populations without requiring an upstream runtime:

```powershell
./gradlew.bat :test --tests org.jlinalg.cli.NfcoreEagerReadFilterTest
```

The byte-pinned fixture and its `SOURCE.json` are under
`src/test/resources/org/jlinalg/workflow/nfcore-eager-filter`. Its mapped BAMs
derive from every record in the complete upstream test FASTQ files at
[commit a3a3dc4](https://github.com/nf-core/test-datasets/tree/a3a3dc48261dbdc0aba8e36272471c353fc62487/testdata/Mammoth).
Their upstream names include `tengrand`; no additional local subsampling occurred.

## Original workflow controls and warnings

The exact eager 2.4.4 source and current pinned original image ran with managed
Nextflow 21.04.1. The complete default control finished **26 tasks**; both final
compiler builds resumed its actual cache with **24 cached and two completed
tasks** each. The selected MALT/HOPS control finished **32 tasks**, including
MAPQ-37 filtering, DeDup, MALT, MaltExtract and the original HOPS postprocessing.
Both controls retain both upstream sample rows and all three original FASTQ files.
They are software controls, with source-specific scientific contracts still open.

The first MALT/HOPS attempt failed: DeDup 0.12.8 rejected an unlabelled single-end
read, retried four times, and the original workflow aborted concurrent MALT.
That full failure is retained. A **new control request explicitly selected
`dedup_all_merged=true`**, an original supported mode. It retains the literal read
names and every input record. This changes the declared deduplication
interpretation; it is not an inferred paper setting, a repaired identifier or a
substituted deduplicator. An actual study must choose and document the correct
mode for its read model before analysis.

The MALT control database and taxon list come from the pinned upstream test
repository. HOPS taxonomy files use a fixed commit of the official resource branch
named by the original MaltExtract executable. These resources are **not the
paper's historical 2017 database**. MALT input queries, alignment-bearing RMA
records, alignment counts and filtered MaltExtract counts are distinct populations
and must not be relabelled as interchangeable read totals.

The original provider emits missing-index diagnostics when its sequential
`until_eof` comparisons open unindexed helper outputs. All records are still
compared; no index or repaired sort order is fabricated. Graphviz is unavailable
in the control environment, so the optional rendered execution DAG is missing.
Provide permitted Graphviz for that diagram; task/output completion does not
claim diagram completeness. Locale warnings, raw version/help exits and all
other source diagnostics remain retained. Full capture genotyping, ancestry,
provider discrepancy resolution, scientific contracts and broader nf-core family
admission remain outstanding.
