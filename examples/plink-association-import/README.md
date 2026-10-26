# Original PLINK association-report import

This Java API example imports existing additive, biallelic PLINK1/2 report files.
Supply plain UTF-8 text; decompress gzip/Zstd reports explicitly before import.
The runtime launcher and public `plink-tools` workflow are still being developed;
this example does not run GWAS or establish a new upstream-adapter badge.

Use a source build containing `org.jlinalg.genetics.PlinkAssociations`. The previous
canonical scoring JAR does not include this new development API. Compile the
example with the development executable JAR on the classpath, then supply:

```text
AssociationImportExample REPORT.tsv EXPECTED_VARIANTS.bim plink2 linear 128
```

Engine choices are `plink1` and `plink2`; models are `linear`, `logistic`, `firth`
and `firth-fallback`. Firth modes require PLINK2. The declared BIM variant family
must match the report exactly. Unknown, duplicate, omitted or non-additive rows
fail; discard or quarantine any partial stdout if an exception occurs. The cohort
sample count bounds per-variant observed sample counts. This example caps metadata
at 128 MiB and 100,000 variants; the library exposes its own explicit input bounds.

Effects follow the original output A1/OMITTED pair. They are not automatically
flipped to BIM A1. Binary `beta` is the logarithm of the **reported rounded OR**,
not a recovered full-precision coefficient. PLINK1's absent standard errors remain
blank; the reader never reconstructs them by dividing rounded beta/statistic.

**Inspect status and upstream_error before inference.** Non-dot PLINK2 ERRCODE
suppresses all canonical estimates even when original OR/P fields are finite.
The raw textual fields remain available through `Row.rawFields()`. Missing fields
are unreportable. Raw P=0 has unresolved significance and remains blank in the
canonical probability/log-P fields; no arbitrary floor is substituted. A finite
original NEG_LOG10_P can remain valid when P itself is below double precision.
Positive decimal P text such as `1.88901e-465` also retains its log P-value; it
is distinct from an original printed zero. Use the log-P and status explicitly
when the ordinary probability field is blank. Prefer log P-values for very small
probabilities, including representable subnormals, to retain printed resolution.

Frozen tests cover 768 actual original synthetic report rows, including 40 of 96
PLINK2 linear effects on BIM A2, ordinary separation errors and finite provisional
Firth outputs marked UNFINISHED. The source input design, command lines, original
hashes and report files are retained in the test-resource provenance. This is
report import, not an independent Firth solver or a replay of a paper's analysis.
