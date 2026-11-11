# Native Picard library-complexity checks

**Development component only. No additional catalog tool credit, whole nf-core pipeline admission or biological validation is claimed.** Picard already has a scoped upstream adapter in the toolbox. This checkpoint adds native Java duplication fractions, library-size estimates and ROI calculations, plus complete saved ChIP-seq 1.2.1 BAM/output consistency checks. The released OmicsMatic JAR is unchanged.

The small library needs Java 17 or later and no external scientific software. The separate saved-bundle auditor includes its existing HTSJDK dependencies and also runs without R, Picard, Preseq or a container. It requires a complete captured original workflow attempt, including its intermediate marked-BAM objects; published result tables alone are insufficient.

Verify the checkpoint from the repository root:

```text
python tools/literature/verify_nfcore_complexity_checkpoint.py
```

Try the native API with a JDK's `jshell`:

```text
jshell --class-path docs/literature/nfcore-complexity-development/picard-complexity-javac21.jar
```

```java
import org.jlinalg.sequence.PicardLibraryComplexity;
var counts = new PicardLibraryComplexity.Counts(38, 109242, 8, 14502, 0);
var result = PicardLibraryComplexity.calculate(counts);
result.duplicationFraction();
result.estimatedLibrarySize(); // OptionalLong[374171]
result.roi()[0];              // source ROI calculation for 1x sequencing
result.roiAssessable();
```

The five count fields are unpaired reads examined, read **pairs** examined, unpaired duplicates, duplicate **pairs**, and original optical duplicate **pairs**. Supply Picard's final pair counts, after its paired-record counters are divided by two. Do not supply individual paired-read counts here. Optical duplicates must be a subset of paired duplicates, and duplicates must be a subset of examined reads/pairs. Invalid counts or integer-capacity overflow fail explicitly; no values are rounded, clipped or inferred.

The numerical methods follow [Picard 2.23.1's original DuplicationMetrics](https://github.com/broadinstitute/picard/blob/2.23.1/src/main/java/picard/sam/DuplicationMetrics.java), including operation order, 40 bisection iterations, conversion to `long`, and all 100 ROI multipliers. Original source and MIT notices are retained; own API/checking code is GPL-3.0-or-later. The small JAR includes corresponding source and licenses and has no executable entrypoint.

For a complete saved workflow, use the larger executable on Windows or Linux. Replace both example paths; the output parent must exist and the output directory must be new and disjoint from the input and producer workspace.

```text
java -Xmx2g -jar docs/literature/nfcore-complexity-development/nfcore-complexity-javac21.jar --engine-result "/absolute/complete-captured-attempt" --out "/absolute/new-complexity-audit"
```

The auditor verifies the complete engine bundle and all locked original ChIP-seq 1.2.1 source files. It resolves each checked library's exact successful/cached `MERGED_BAM` task and reads the complete captured `.mLb.mkD.sorted.bam` object. It never opens producer work paths. BAM/reference dictionaries, technical read groups and biological sample identities must agree with the complete checked design.

Native counters follow original Picard's category priority and include vendor-QC-failed records according to that source behavior; they do not use FRiP's QC-passing denominator. Every library row must reconcile with the complete BAM. Derived fractions and library-size estimates, every applicable ROI bin and complete duplicate-set histogram sums are checked at source precision. Original multiple-library tables retain separate library rows; no pooled ROI is invented. The published default Preseq curves must retain all 10,000 rows per requested library, the original million-read grid below 10 billion, their zero origin, finite nonnegative values, ordered expected yields and compatible confidence bounds. No curve tail or late row is omitted.

**Warnings and required actions**

- **Optical classification remains original:** the audit checks the original optical counts' bounds, histogram reconciliation and use in the size formula. It does not identify optical clusters or independently repeat duplicate marking. Use the correct original read-name, library and optical-distance conventions when preparing an upstream analysis.
- **Preseq remains original:** the audit checks complete curve structure, consistency and integrity. It does not independently recalculate Preseq's extrapolation or confidence intervals. Inspect the original workflow logs and warnings before interpreting an estimate.
- **An absent estimate stays absent:** empty paired data, no non-optical duplicate evidence, or entirely optical duplicate evidence can yield no Picard size estimate. The API returns `OptionalLong.empty()` and an empty ROI array. The saved audit reports null/absence; it does not invent zero, infinity or a positive estimate.
- **A source zero estimate is different:** zero unique pairs can produce the original size estimate `0` and `NaN` ROI values. Those source values remain visible in the native API; `roiAssessable()` is false. They do not establish a biological zero-size library. No finite positive ROI is substituted.
- **Strict BAM validation applies:** the original upstream pipeline requests lenient Picard validation. A malformed output can therefore be written upstream and fail this component. Inspect the named SAM validation error and preserve the original file. Do not clear flags or repair real inputs silently to force acceptance.
- **FAIL2 / exit 2 means no component result:** read stderr, `FAILURE.json` and `WARNINGS.md` when the output directory was created. Missing objects, incomplete curves, counter/model inconsistencies, malformed BAMs or capacity overruns fail explicitly. Preserve the failed attempt, correct its cause and retry a new directory. The audit never falls back to producer files, another tool or an earlier successful attempt.

The fixed complete-input capacities are one billion BAM records, 64 MiB per metadata table, one million table lines and 1 MiB per line. The complete engine bundle also has its own recorded limits. Actual Java heap availability matters. A capacity failure never selects a smaller sample, partial curve or reduced histogram.

Read `WARNINGS.md` and the audit report even after a successful component marker. This checkpoint closes the scoped duplication/library-complexity consistency checks, while peak annotation, other QC and the full pipeline-family adapter still require work. All source IDs, original counts, missing/nonfinite estimates and failures remain retained. Hashes establish integrity, not execution authenticity or biological accuracy.

The evidence contains 20 original numerical cases, replayed through both compiler builds on Windows Java 21/25 and Linux Java 21; four complete captured six-library replays; a complete 16-record optical/flag control; and 15 deliberate failures. The captured control covers 1,389,840 marked BAM records, 600 ROI bins and 60,000 Preseq rows. The synthetic control covers a real original optical classification, unmapped and secondary/supplementary records, unpaired duplicates and vendor-QC-failed mapped reads. Its earlier malformed SAM is retained as a mandatory negative control; a separately labelled valid derivative retains all 16 records. These are software controls, not cohort-paper data or independent biological validation.

Separate preparation also downloaded and inspected all 33 original image URIs declared by the 50 ChIP-seq 2.0.0 container declarations across 203 locked source files. The private images remain local. This is prerequisite preparation only; that profile still needs runtime/execution/scientific validation. No author engine version or historical image digest is inferred. See [the source/runtime foundation](NFCORE_FOUNDATION.md), [native consensus](NFCORE_CONSENSUS.md) and [original DESeq2 postfit checks](NFCORE_DESEQ126.md).
