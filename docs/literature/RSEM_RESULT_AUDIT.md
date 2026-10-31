# Native RSEM result-pair audit

`org.jlinalg.sequence.RsemResults` now audits complete ordinary original RSEM isoform/gene result pairs. It supports EM-only, posterior-mean and posterior-mean/credibility-interval schemas from releases 1.2.28, 1.3.0, 1.3.1, 1.3.2 and 1.3.3. **The protected public fitting workflow, alignment admission and portable runtime setup remain unfinished. RSEM still counts as missing in the toolbox inventory.**

The later [alignment/count milestone](RSEM_ALIGNMENT_ADMISSION.md) corrects the count reader's earlier `nIsoMulti` invariant and supersedes the earlier result-audit source-validation record for current count admission. All earlier arithmetic/result fixtures remain retained and are rerun in the expanded focused suite. Native alignment semantics and source-linked decoding are now tested; protected runtime/reference/fitting admission remains unfinished.

Start with the [runnable Java-only example](../../examples/rsem-native-audit/README.md). It audits a bundled original synthetic result and retains every consumed file hash. Build current source with a JDK providing Java 17 or newer. If Java is unavailable and installation is forbidden, skip the dependent audit; supply a permitted JDK 17+ on another host before running it.

## Inputs and complete populations

Use the [reference/count/model/theta readers](RSEM_FILE_READERS.md) first. Supply the exact original release, requested posterior mode, `--append-names` selection and explicit population/file capacities. Keep original intermediate files when running the original tool. A rounded abundance table alone cannot supply the saved model or theta.

```java
var result = RsemResults.audit(
        Path.of("work/sample.isoforms.results"),
        Path.of("work/sample.genes.results"),
        reference, counts, fit,
        RsemAbundance.Release.V1_3_3,
        RsemResults.Posterior.INTERVAL, false,
        new RsemResults.Limits(1_000, 1_000, 1_000, 100_000,
                1_000_000, 5_000_000, 1_000_000));
```

Here `reference`, `counts` and `fit` are the complete parsed objects from the file-reader example. Capacities are illustrative small-workflow budgets. Exceeding one rejects the complete audit instead of dropping rows. Input is strict UTF-8 with original LF line endings. Truncation, extra rows, changed identities, inconsistent groups, invalid fields and exceeded capacities fail with an `IOException`; preserve partial output, correct the named cause and rerun to a NEW destination.

The optional `Sink` receives every original row with canonical raw identifiers, associated members, exact display labels and every numeric token/bound. Column order is available through `isoformColumns(actualPosterior)` and `geneColumns(actualPosterior)`. Commas inside names/IDs are never used to split a gene membership field: its entire string is compared with the known ordered reference join. Sink output remains partial until the complete audit returns; a later source/global-family or destination failure prevents a completed `Audit`.

## What is checked

The audit checks every header, row and numeric field; reference identities/order/lengths; gene membership and complete populations; effective lengths against saved fragment-model precision; EM TPM/FPKM against saved polished-theta precision; gene count/expression sums; EM-TPM-weighted gene lengths; zero-expression fallbacks; and within-gene EM/posterior TPM percentages. Complete expected-count and posterior mean-count families cannot exceed the original alignable read/fragment population. Read types retain their original units; fragments are not doubled into reads.

Posterior standard deviations remain separate quantities. Gene standard deviation is not the sum of isoform standard deviations. Credibility endpoints retain their own six-significant-digit format, order and quartile-variation bounds; endpoints are not summed and a mean is not required to lie inside an interval. Posterior mean counts can be zero while posterior TPM is positive because the original posterior procedure includes its prior. Expected counts are not reconstructed from TPM or theta.

Model fields use ten significant digits, theta/mappability fifteen, ordinary result fields two decimal places and interval fields six significant digits. The auditor propagates conservative outward binary64 bounds through the original effective-length/expression arithmetic and combines them with source serialization bounds. It preserves the original below-one transcript effective-length cutoff and the strict `1e-300` gene-aggregation boundary. If saved precision straddles that boundary, the remaining alternatives are retained and reported as ambiguous. The bounds establish compatibility; they do not recover hidden precision, certify a unique formatter tie or establish the correctness/convergence of a fitted EM/Gibbs model.

For no alignable groups, original 1.3.2/1.3.3 produce empty model/theta files and EM-only tables even when posterior output was requested. Effective lengths equal raw transcript lengths; gene lengths/effective lengths use the unweighted raw-length fallback, with true zero expression/counts. `requestedPosterior` remains visible while `actualPosterior` is `NONE`. No learned model or posterior inference is invented. Earlier releases cannot complete this original path.

## Validation and remaining work

The earlier result-audit milestone passed 89 focused tests against its exact built JAR on Windows Java 21/25 and Linux Java 21, with zero skips, strict Java 17 compilation and private Javadoc. Those tests include 668 original-function abundance vectors per JVM, complete model/reference/count readers, 59 captured original fitting result pairs, 334 isoform rows, 236 gene rows and 3,944 fitting-result numeric fields per JVM. Those pairs contain 55 learned states and four explicit no-alignment states. That milestone preserved every predecessor JAR member; the later alignment milestone intentionally corrects the count-reader class and records its exact payload difference.

Separate unchanged-original writer probes supply explicitly constructed theta/count inputs; these are not fitted EM parameters. Twenty production-optimized probes span all five releases, and four strict-arithmetic controls use 1.3.3. All 24 additional pairs are audited, covering 144 isoform rows, 96 gene rows and 1,344 numeric fields. They demonstrate that TPM printed as zero can still imply weighted gene lengths and a nonzero isoform percentage.

In the production 1.3.3 caller-threshold case, a separate expression calculation returns exactly `1e-300`, while `WriteResultsEM` recomputes the quantities and emits gene length `800.00`. The strict control's corresponding threshold case emits `0.00`. The control uses separately constructed parameters and a separately declared compiler profile; it does not replace the production results or prove the writer's hidden TPM equals the caller diagnostic. Saved precision leaves both cases ambiguous. `writer-provenance.json` preserves the caller diagnostics, actual writer fields and compiler flags. The failed C++11 probe is retained; the unchanged original headers compile with GNU C++98.

The three fixture archives retain 510 checksummed original scientific output files in total; whole-file SHA-256 pins also protect case metadata and manifests. Corruption tests cover model-incompatible expression/effective lengths, identifiers and comma joins, posterior schemas, counts, covariance, EM-based lengths, interval order/format, missing/extra rows, invalid numerics, capacities and sink failures. The bundled native example is separately compiled and run against the same exact JAR, including existing-output and altered-input refusals on all three JVM profiles. See [the validation record](RSEM_RESULT_AUDIT_VALIDATION.json) for source/fixture pins and precise evidence.

These are synthetic method checks. Alignment-to-reference/runtime closure, protected original reference preparation and fitting execution, typed public commands, runtime setup and packaged public fitting examples still require completion. Genome/aligner execution, allele-specific/pRSEM workflows and biological or whole-paper equivalence are not inferred from this native audit.

Pinned original serialization: [WriteResults.h](https://github.com/deweylab/RSEM/blob/e4dda70e90fb5eb9b831306f1c381f8bbf71ef0e/WriteResults.h), [EM.cpp](https://github.com/deweylab/RSEM/blob/e4dda70e90fb5eb9b831306f1c381f8bbf71ef0e/EM.cpp).
