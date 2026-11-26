# ASCAT numerical components in Java

`ascat-objective` builds consecutive segment runs and calculates the complete distance grid used by ASCAT 2.5.2 and 3.2.0. It accepts **already aligned, segmented autosomal logR and BAF**. It does not select a valid model or estimate tumor purity, ploidy or copy number. ASCAT remains pending in the tool catalog until an appropriate complete upstream workflow is qualified.

These GPL-3.0 components adapt `make_segments` and `create_distance_matrix` by Peter Van Loo and the ASCAT contributors. Source commits, full namespace function hashes and synthetic reference populations are recorded in [SOURCE.json](../src/test/resources/org/jlinalg/genetics/ascat/SOURCE.json). The upstream sources are [ASCAT 3.2.0](https://github.com/VanLoo-lab/ascat/tree/4445969adf5956d0aa1f4b6e393cf31d8a34fb95) and [ASCAT 2.5.2](https://github.com/VanLoo-lab/ascat/tree/fdf9325ea39402f30046f8b9e1ac254ffb4fd67d).

Generate a request template, replace the synthetic values with the actual experiment, and choose a new output directory:

```powershell
java -jar omicsmatic-0.4.1.jar ascat-objective --example > objective-request.json
# Edit objective-request.json before continuing.
java -jar omicsmatic-0.4.1.jar ascat-objective --request objective-request.json --out objective-result
java -jar omicsmatic-0.4.1.jar ascat-objective --verify objective-result
```

PowerShell 7 preserves UTF-8 when redirecting this output. In Windows PowerShell 5.1, use `| Set-Content -Encoding utf8 objective-request.json`; its UTF-8 BOM is accepted. Keep the exact producing JAR with the capture. Java 21 is required; R and additional software are not needed for this native command.

Each `loci` entry is `[segmented_logR, segmented_BAF]` in the original aligned order. Record the actual reference build, ID matching, autosome selection and segmentation in `preparation`; the command cannot verify those biological prerequisites from numeric pairs. Supply the actual platform's positive `gamma` and every coordinate of the positive, increasing `psi` and `rho` axes. The template axes are illustrative. No paper's settings are inferred. Rho can extend to 1.05, matching the original search domain.

The component supports finite logR and BAF in `[0,1]`. Use JSON `null` for a missing value. A pair with either value missing is omitted before adjacent runs are compared, matching the source function. Equal values across a missing pair can therefore belong to one run. Signed zero compares equal and the first run's values are retained. Empty/all-missing populations fail. Infinite values, R vector recycling and unaligned/mismatched vectors are outside the supported domain.

The distance calculation compares **unweighted allele sums over all segments** to select one allele vector, then uses run lengths to weight its distance from nonnegative rounded copy values. It does not choose the smaller allele separately for each segment. Exactly balanced BAF `0.5` receives weight `0.05`. Rounding uses ties to even; native finite sums are compensated. Tests compare all 58,176 synthetic distance cells and 22 segments against the complete original namespace functions in both upstream versions. The original reference results agree byte for byte between the two versions. Native/R floating-point comparison uses absolute tolerance `2e-11` plus relative tolerance `2e-12`; saved Java replay compares its entire output exactly.

The capture retains original request bytes, `segments.tsv`, `distance.tsv`, `OUTPUT-AUDIT.json`, `WARNINGS.md` and file/producer hashes. `--verify` checks the complete saved file population and recalculates every segment and grid cell. It does not access the original request path. Hashes establish integrity, not authenticity or biological validity. A minimum grid value is not an admitted ASCAT solution: the original model's validity filters and remaining analysis steps are essential.

The components were also checked against all 100 samples of the unmodified ASCAT 3.2.0 example: 575,002 aligned autosomal locus pairs, 4,892 segment runs and 969,600 complete original-function objective cells. The upstream example completed correction, segmentation, model fitting and QC with zero failed samples. That reference run is not an OmicsMatic execution adapter. The source fitting routine can subsequently replace some grid values above purity 1 with `1e20` during fallback searches; the native component returns the original unmodified objective. Its numerical tests do not substitute for those fitting rules.

**Legacy runtime limitation:** ASCAT 2.5.2's complete example failed during segmentation under R 4.6.2 with `'length = 3' in coercion to 'logical(1)'`. Its complete `make_segments` and `create_distance_matrix` functions produced the retained numerical references successfully. Do not use that R 4.6.2 installation to claim a successful legacy pipeline. A permitted compatible R environment and a fresh successful complete run are required before admitting legacy fitting. The failed attempt is retained; the original package source was not patched.

**Warnings, errors and required actions:**

- Exit `0` requires `COMPLETED.json` and successful verification. This admits only the numerical component.
- Exit `3` (`SKIP3`) means the input or exact producing JAR is unavailable. Restore the permitted prerequisite and skip the dependent calculation until it is available.
- Exit `2` (`FAIL2`) means invalid/changed input, overflow, cancellation or a capacity limit prevented a complete result. Retain the failed attempt and its partial outputs, inspect the named error, correct its cause and retry with a new output directory. Do not use partial tables.
- Request size is limited to 64 MiB. Explicit row, cell, segment/cell-operation, output-table-byte and cooperative wall/cancellation limits apply to complete populations. They do not guarantee JVM memory availability. Raising a limit must reflect available permitted resources; reducing the input to obtain success changes the analysis.
- Full allele counting, normal-panel selection, correction, segmentation, purity/ploidy optimization, sex-chromosome handling, copy calls and QC require a separately validated upstream workflow. If its software, references, licence or installation permission are unavailable, warn prominently and skip that dependent analysis. This command downloads and installs nothing.

Java callers can use `org.jlinalg.genetics.AscatObjective.segments`, `distance` and `grid`. The latter two accept explicit scientific parameters; cancellation-aware overloads do not return partial results. For example:

```java
var segments = AscatObjective.segments(
    new double[] {-.55, -.55, 0, 0},
    new double[] {1, 1, .5, .5});
double objective = AscatObjective.distance(segments, .55, 2, .7);
```

To reproduce the synthetic command-line comparisons with your built executable, run the retained verifier with explicit executable paths and a new output directory:

```powershell
python tools/literature/verify_ascat_objective.py --java 'C:/path/to/java.exe' --jar 'C:/path/to/omicsmatic-0.4.1.jar' --out 'C:/path/to/new-verification' --controls
```

The verifier retains six complete captures, six fresh replays, every original numerical comparison and nine controlled refusals. The source-reference generator is [reference_components.R](../tools/literature/ascat/reference_components.R); run it separately in each unmodified installed ASCAT namespace, passing `3.2.0` or `2.5.2` as its sole argument. No upstream installation is performed by either verifier.
