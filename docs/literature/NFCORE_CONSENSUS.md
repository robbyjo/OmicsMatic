# Native original ChIP-seq 1.2.1 consensus peaks

**Component milestone: full nf-core integration remains pending, with zero additional tool credit.** This adds a usable Java consensus calculation and an audit of the original pipeline's complete consensus outputs. The catalog remains at 99 new scoped integrations (19 native methods and 80 adapters), with 244 tools still missing. All 250 papers are collected; the broader implementation and paper-role review are unfinished.

Read the [captured-workflow guide](NFCORE_CAPTURED_COMPONENTS.md) for the original runtime, input capture, initial native FASTQ/BAM/FRiP checks and remaining scientific requirements. The legacy/released JAR and root CLI are unchanged. This component uses a separate development JAR.

## Important source convention and required actions

**The original workflow writes BED's zero-based Start directly into SAF.** The original featureCounts output consequently reports interval length as `End - Start + 1`. This component preserves the original value. It does not increment Start or relabel these outputs as a corrected coordinate conversion. If a one-based conversion is needed, declare a separate analysis and retain the original results. The warning appears in `--help`, stderr and every audit's `WARNINGS.md`.

Read **both the selected original attempt's `WARNINGS.md` and the component audit's `WARNINGS.md`**. A complete component audit does not admit a whole-pipeline scientific result. Original runtime/image admission, MACS calling/annotation, featureCounts, differential analysis, library-complexity and other QC contracts still require completion. Preserve failed attempts. For FAIL2, admit no component result, inspect the named file or discrepancy and correct it using a new output path. Wrong peak kind now names the missing output path and explains how to check the original branch and selected attempt.

The Java computation requires no external aligner or peak-calling installation. Original upstream analysis still requires its exact permitted runtime/dependencies. If those are unavailable or installation is forbidden, SKIP3 means skip dependent analysis and complete permitted setup first; component success cannot bypass that requirement.

## Native calculation

`org.jlinalg.workflow.NfcoreChipseq121Consensus` independently computes the operation used by nf-core/chipseq 1.2.1's original GNU sort, BEDTools 2.29.2 `mergeBed`, and `macs2_merged_expand.py`:

1. Read every broadPeak/narrowPeak row with strict UTF-8 and explicit whole-input capacities. Preserve source identifiers, raw numeric tokens and peak occurrences.
2. Sort contig/start with the original C/C.UTF-8 ordering and GNU sort's whole-line tie break. Sort sample columns by Python's Unicode scalar ordering, including supplementary characters.
3. Merge overlapping and bookended intervals transitively **before** replicate filtering.
4. Derive the source sample from each peak name by removing the final two underscore-separated parts. Require agreement with its declared file identity. Group samples using the source's final-underscore rule and require the explicitly selected positive replicate threshold within each group. Repeated peaks from one sample do not count as additional replicates.
5. Retain the original merged boundaries and total peak-occurrence count, including peaks from groups that fail the threshold. Retain qualified samples and their values in the original collapsed order. Assign retained intervals `Interval_1`, `Interval_2`, etc.
6. Count exact membership combinations and reproduce descending frequency/sample-tuple ordering. Keep relative narrowPeak summit values unchanged.

The API returns immutable `Plan`, `Row` and per-sample peak lists. It does not call MACS2 or claim to validate peak-calling statistics or biological accuracy. Nonfinite values, inconsistent sample identities, zero-length geometry and exceeded capacities are explicit refusals. Original data remain unchanged. Empty peak sets and a threshold retaining no intervals are supported; neither establishes biological absence.

```java
var limits = NfcoreChipseq121Consensus.Limits.defaults();
var kind = NfcoreChipseq121Consensus.Kind.BROAD;
var inputs = List.of(
    new NfcoreChipseq121Consensus.PeakSet("IP_R1",
        NfcoreChipseq121Consensus.read(Path.of("IP_R1_peaks.broadPeak"), kind, limits)),
    new NfcoreChipseq121Consensus.PeakSet("IP_R2",
        NfcoreChipseq121Consensus.read(Path.of("IP_R2_peaks.broadPeak"), kind, limits)));
var plan = NfcoreChipseq121Consensus.compute(inputs, kind, 1, limits);
for (var row : plan.rows()) {
    // row.start()/end() are the original zero-based, half-open merged geometry.
    // row.qualifyingPeaks() retains complete ordered source occurrences.
}
```

## Complete original-output audit

Verify the checkpoint from the repository root:

```powershell
python tools/literature/verify_nfcore_consensus_checkpoint.py
```

Use an already complete captured ChIP-seq 1.2.1 session or standalone attempt. A plain results folder or partial export is insufficient. Replace paths, declare the actual original peak kind and positive replicate threshold, and use a new output directory with an existing parent, separate from the original producer and selected bundle:

```powershell
java -Xmx1024m -jar docs/literature/nfcore-consensus-development/nfcore-consensus-javac21.jar --engine-result D:\results\complete-original-attempt --peak-type broadPeak --min-replicates 1 --out D:\results\new-consensus-audit
```

The audit checks the complete immutable bundle, all 85 locked original workflow files, the exact reference dictionary, checked IP/control graph, every eligible antibody's original peak population, and every boolean, BED, SAF and intersection row. It verifies the selected result again before publishing. It respects the original branch condition and infers no unreported author settings. Require exit zero, `COMPONENT_AUDIT_COMPLETED.json`, and inspection of `audit.json` and warnings. Existing outputs are never reused or overwritten.

## Evidence and limits

Twelve complete original-tool cases run against both compiler builds on Windows Java 21/25 and Linux Java 21. They cover transitive/bookended merges, filtering after a connecting peak, repeated occurrences, full-line tie breaks, combination ordering, Unicode, narrow summits, empty results, and expected refusals of original source outputs with inconsistent identity/nonfinite/zero-length data.

The real captured control uses **all 3,357 peaks and all 1,472 resulting consensus intervals**. Replaying the original tools reproduces all four captured output files byte-for-byte. Both packaged builds produce matching audits on all four tested compiler/JVM combinations; six negative checks cover wrong threshold/kind and existing-output protection. Native class bytes in the packaged artifacts match the original-tool-tested classes.

[Checkpoint files](nfcore-consensus-development/README.md) include the two JARs, source snapshots, complete peak/control tables, original scripts/notices, earlier diagnostic versions and test reports. The large original workflow attempt, reads, image and work/cache trees remain locally retained and are not required to rerun the small native comparison cases. Repeating the full captured-result CLI audit requires that complete original attempt. The checkpoint verifier checks frozen bytes and stated evidence; it does not repeat scientific analysis or authenticate execution history.
