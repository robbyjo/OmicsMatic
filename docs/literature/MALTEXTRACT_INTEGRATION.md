# MaltExtract target extraction and original summary/damage output

The scoped original1.7 workflow implements the output-producing MaltExtract
operations reported in P002 supplementary section3.3: target-species extraction
from RMA6 and retention of the original summary/damage tables for manual review.
The paper does not report the MaltExtract version, filters or resources. The
profiles here are deliberate software choices, not recovered author settings.
Manual biological assessment and the separate7508-genome MALT rescreen remain
separate operations. This is a scoped upstream adapter, not whole-package,
whole-paper or biological validation.

Use the separate [JDK21-built development JAR](maltextract17-integration/maltextract17-workflow-jdk21.jar)
or [JDK25-built JAR](maltextract17-integration/maltextract17-workflow-jdk25.jar).
Both target Java17 bytecode; use the qualified JDK21 or JDK25 application runtimes.
The canonical/released executable is unchanged and has no admitted root command
for this workflow. A source build is also available:

```text
python tools/literature/maltextract17_workflow/build.py --java-bin /path/to/jdk21/bin --out /path/to/NEW-build
python tools/literature/maltextract17_workflow/setup.py --out /native/linux/path/NEW-runtime
java -Xmx3g -jar docs/literature/maltextract17-integration/maltextract17-workflow-jdk21.jar --help
```

For complete input formats and copyable fit/verification commands, read the
[workflow guide](maltextract17-workflow-development/README.md). Supply complete
classified RMA6 files, complete literal ncbi.map/ncbi.tre resources, a target-name
file and a `sample_id`/`rma6` TSV. Select profile and provenance explicitly and
use a NEW native-Linux output directory. The exact GPL HOPS0.35 MaltExtract1.7
program and complete OpenJDK8.0.144 JRE remain external; setup changes no system
Java or Conda environment. Saved verification on Windows/Linux needs only the
exact1.7 reader JAR and modern application Java, without original Java8 fitting.

**Missing, forbidden or unavailable runtime/install means SKIP3 the dependent
analysis.** Invalid/incomplete input, exceeded capacity, source failure or
inconsistent output means FAIL2 and no admitted result. Inspect the named cause,
retain every failed attempt and rerun a NEW directory after resolving it. Original
SEVERE output errors with exit0 were observed and are refused.

**Stock sampling is not reliably described by the original flag.** In the
complete10001-read control, `source-default` examined10000, reported
`downSampling? = Off`, and destacked to1 retained read. Its full-population
interpretation is skipped. If full-population extraction is intended, explicitly
choose `complete-default` or `complete-def-anc`; these disable sampling,
destacking and duplicate removal and therefore change the method. The complete
default control retained all10001 reads.

Every original summary, damage, coverage, distribution, FASTA, BLAST and log file
is retained unchanged. A portable flat member index preserves literal Linux
filenames. Native checks reproduce names, source-specific descendants, complete
stored counts, extraction counters and complete-profile FASTA multisets.
Original NaN/Inf cells remain literal; ambiguous table interpretation is skipped.
The damage denominator uses a distinct source-length>=20 gate. Review the
original statistical outputs manually before scientific interpretation.

Qualification includes seven complete original productions,516 byte-identical
scientific-file comparisons with independent originals,76027 descendant cases,
16 complete-map name cases and21 focused tests without failures or skips. Full
saved replay passed on Windows JDK21/25 and Linux JDK21, with8 rehashed metadata
refusals on each platform. Five failed attempts remain retained. See the
[workflow validation](maltextract17-workflow-development/VALIDATION.json),
[portable receipts](maltextract17-workflow-development/PORTABLE_REPLAYS.json) and
[catalog admission](omics-2023-2026/maltextract17-adapter-validation.json).

Saved replay checks consistency and fixed source-control oracles. It does not
repeat original fitting, independently recalculate arbitrary damage/coverage
cells, authenticate a producer or establish biological validity. Historical
development records retain their original zero-credit/pending notices; the
separate catalog-admission record governs the later scoped badge. The full
250-paper implementation and paper-wide semantic-completeness review remain open.
