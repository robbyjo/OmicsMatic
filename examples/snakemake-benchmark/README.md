# Synthetic supplied benchmark example

These are hand-written format examples, not measured timings or biological results.
Two explicitly labelled illustrative runs each contain an alignment job and a
counting job. All four files and all rows must appear in `jobs.tsv`.

Build current source and run from the repository root:

```powershell
.\gradlew.bat executableJar --no-daemon --max-workers=2 --console=plain
java -jar build/cli/omicsmatic-0.4.0.jar snakemake-tools benchmark --request examples/snakemake-benchmark/request.properties --out build/NEW-snakemake-benchmark
java -jar build/cli/omicsmatic-0.4.0.jar snakemake-tools benchmark --verify build/NEW-snakemake-benchmark --out build/NEW-snakemake-benchmark-replay
```

Expected recorded CPU sums are 0.30 and 1.20 seconds; maximum job RSS is 32.25 and
40.00 MiB. These are arithmetic checks on invented records. Summed wall time is
5.75 and 7 seconds, not measured pipeline elapsed time. Maximum job RSS does not
represent concurrent pipeline peak memory.

Require `COMPLETED.properties`, read `WARNINGS.md`, and verify into a new output
directory. A failure admits no complete result. Retain failed attempts and retry
new paths after correcting the named cause. The native command requires Java
17+ and the built JAR; it requires no Snakemake or Python installation. See the
[full guide](../../docs/literature/SNAKEMAKE_BENCHMARKS.md).
