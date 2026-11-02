# fastp output and report audit development

**fastp still has no public typed analysis command or completed portable analysis
bundle. Do not treat this development checkpoint, an exit-zero process, or a
runtime setup marker as an admitted analysis.** The catalog remains at 60 new
scoped integrations: 13 native methods and 47 upstream adapters. fastp is still
one of the 260 missing named-tool integrations.

This checkpoint extends the frozen [native filtering foundation](FASTP_DEVELOPMENT.md)
and [complete-input/original-stage foundation](FASTP_ORIGINAL_STAGE_DEVELOPMENT.md).
Their reports describe earlier milestones and retain their original evidence.
The current evidence is [FASTP_OUTPUT_AUDIT_DEVELOPMENT_VALIDATION.json](FASTP_OUTPUT_AUDIT_DEVELOPMENT_VALIDATION.json)
and [the source, test, control and audit archive](fastp-output-audit-development-controls.zip).

## What Java checks

The new bounded audit reads all captured inputs and every original main, failed
and requested unpaired FASTQ through physical EOF. Main paired outputs must
retain matching mate order. An external sort preserves every occurrence and
groups original headers without silently discarding duplicates. A bounded
assignment solver writes a complete disposition ledger. Duplicate-header
families with multiple possible assignments are explicitly marked ambiguous;
the selected assignment is a feasible witness, not an established original
occurrence identity. Capacity failures do not return a partial result.

The Java quality cutting and filtering methods constrain the observed reads.
Adapter and poly-tail processing are checked with conservative prefix envelopes;
corrections require a low-quality original base, the matching high-quality
complement in the mate, and consistent possible insert coordinates. These checks
do not independently reproduce the upstream adapter, overlap, poly-tail or
Bloom-filter choices. Correction counters also include changes later trimmed,
filtered or suppressed, so visible changes provide lower bounds.

The independent statistics model recomputes the complete input and main passing
populations: totals, Q20/Q30, source GC counts, integer mean length, every reported
quality/content cycle value, and all 1,024 five-mers. fastp 1.0.1's additional
detailed Q40 field is checked separately; its summary still has no Q40 field.
Source ASCII base buckets and uppercase-only five-mer encoding are retained.
Floating fields are compared at the original six-significant-digit precision.
Unpaired files are reconciled but excluded from the source's main passing
statistics. Auxiliary adapter, correction, duplicate, insert-size and poly-X
reports receive separately counted consistency checks and bounds.

## Source behaviors that affect interpretation

**Missing paired reads are not necessarily duplicates.** In all four audited
editions, when both mates fail, the paired source writes neither mate to any
FASTQ, including the failed output. The audit preserves this omission. With
deduplication enabled, an absent pair can be a source-omitted failed pair or a
suppressed duplicate. The source counters and printed duplicate rate constrain
these totals; they do not establish every absent occurrence's cause. Review
`read-dispositions.tsv` and the report warnings before interpreting missing reads.

The original insert-size histogram uses eligible reads from thread zero, so it
does not describe all input pairs. The 0.22.0 source uses non-atomic duplicate
buffers/counters; multithreaded duplicate-rate reproducibility is not certified.
That warning is a source-code inference, not an observed race in these tiny
controls. Select one thread explicitly when that source limitation matters.
No replacement duplicate algorithm or changed requested accuracy is substituted.

## Validation and resource requirements

The retained corpus contains 57 successful fresh original runs across 0.22.0,
0.23.2, 0.25.0 and 1.0.1. Controls cover adapters, poly-G/X, quality/global cuts,
length caps, literal case, gzip/Phred+64, carried plus headers, all-filtered reads,
orphan policies, correction and deduplication. All 1,560 input reads are accounted
for as 1,362 represented and 198 unrepresented reads. The audit independently
recomputes 383,617 report values and additionally constrains 11,288 fields. The
unrepresented count includes both paired-failure omissions and duplicates.

The synthetic P054 control actually used its reported fastp 0.23.2 flags,
including deduplication accuracy 6. It completed with about 24.05 GiB sampled
RSS, explicit 28 GiB address-space/27 GiB sampled-RSS capacities and no remaining
owned workers. This validates the reported option configuration on synthetic
reads. The paper's barcode positions and full biological workflow remain
unreproduced. **Accuracy 6 requests a 24 GiB Bloom buffer plus overhead: use an
adequate permitted Linux host or skip that analysis. Do not silently lower it.**

The new portable audit suite has 135 checks. Together with the unchanged native,
FASTQ, parameter, runtime and process checks, the actual development JAR passes
327 checks on Linux Java 21 with a 128 MiB Java heap. Windows Java 21 and 25 each
pass 310 checks and explicitly abort 17 Linux-only checks. No scientific original
run is repeated merely to replay the portable audit. The Windows Gradle selection
finds 460 tests, passes 310, and skips 150 platform/original-stage tests; those
skips are not counted as passed original analyses. The separate literature
Python suite passes 573 checks and skips 46 of its 619 tests.

To run the 135 portable audit checks from the repository on a supported build
JDK, without installing fastp:

```powershell
.\gradlew.bat test --tests org.jlinalg.cli.FastpMetricsTest --tests org.jlinalg.cli.FastpSortTest --tests org.jlinalg.cli.FastpJoinTest --tests org.jlinalg.cli.FastpOutputAuditTest
```

The archive retains the initial invalid single-end adapter fixture, the earlier
Windows line-ending fixture failure, the paired-omission/rate audit failures,
and subsequent test compilation/overwrite-fixture failures. Their logs are
historical failed attempts, not successful controls. The corrected audit reuses
the retained original bytes; it does not overwrite or rerun those scientific
outputs. The canonical executable JAR and catalog pages remain unchanged.

## Warnings and required actions

For original execution, use an allowed Linux x86_64 environment with the exact
managed runtime and declared helpers. Follow the existing
[setup instructions](FASTP_DEVELOPMENT.md); downloads and installation require
the installer's explicit flags. An unavailable or forbidden dependency requires
SKIP3 and skipped dependent analysis. Preserve diagnostics and install the exact
permitted dependency, or use a supported host, before retrying in new paths.

Invalid input, integrity changes, process or capacity failures require FAIL2 and
no admitted analysis. Preserve the failed directory, correct the named cause,
raise an explicit capacity only where resources permit, and use a new output
directory. The development audit itself reports exceptions and writes no
analysis `COMPLETED.json`. The next required work is the public CLI and recipe,
full bundle finalization and portable verification, then paper-role/catalog
promotion. No release, site deployment or whole-goal completion is claimed.
