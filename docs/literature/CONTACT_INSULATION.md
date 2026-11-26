# Prepared contact-map insulation and boundaries

Source-built native workflow with a qualified prepared-table profile. Existing released binaries predate this command; build this source with Java 21 and `./gradlew executableJar` (PowerShell: `./gradlew.bat executableJar`). The generated executable is `build/cli/omicsmatic-0.4.1.jar`. Keep the exact JAR used for each capture. No new release is implied.

`contact-insulation` calculates weighted cis insulation and boundary strengths in Java from complete prepared sparse contact tables. It implements the shared selected numerical behavior of cooltools 0.5.1 and 0.5.4. The inputs, weights, windows and thresholds must be supplied explicitly. No upstream runtime is needed for this prepared-table stage.

Raw Cooler/HDF5 reading, matrix balancing, automatic Li/Otsu thresholds, compartment eigenvectors, dots, ALLCools and complete author pipelines are separate operations. A result from this command covers the declared insulation and fixed-threshold boundary calculation.

## Start with the example request

Use the executable built from this feature's complete source. On PowerShell:

```powershell
java -jar omicsmatic-0.4.1.jar contact-insulation --help
java -jar omicsmatic-0.4.1.jar contact-insulation --example |
    Set-Content -Encoding UTF8 request.json
```

Edit `request.json` before running it. Set `input.bins` and `input.pixels` to canonical absolute paths; on Windows use forward slashes, for example `D:/analysis/bins.tsv`. Replace the preparation description with the actual reference, binning, balancing and export provenance. The example settings are explicit examples, not inferred paper settings.

```powershell
java -Xmx4g -jar omicsmatic-0.4.1.jar contact-insulation --request request.json --out D:/analysis/new-insulation
java -Xmx4g -jar omicsmatic-0.4.1.jar contact-insulation --verify D:/analysis/new-insulation
```

The output path must be new. Read the printed required action, `WARNINGS.md` and `OUTPUT-AUDIT.json`. Require `COMPLETED.json`, no `INCOMPLETE.json` or `FAILURE.json`, and a successful saved verification before using the result. Retain the exact producing JAR and complete capture. Saved replay uses captured inputs and does not access original host paths.

## Exact prepared inputs

Both inputs are uncompressed literal UTF-8 TSV with the exact headers below. Tabs separate fields; quotes are literal. An optional header BOM and CR/LF record delimiters are accepted. Blank rows, invalid UTF-8, unknown columns and malformed values fail rather than being dropped. Complete original bytes are retained in `inputs/`.

Bin header:

```text
bin_id	chrom	start	end	region	weight
```

Each bin ID is its contiguous zero-based input row number. Region names define contiguous bin blocks. Bins within a region use one chromosome label and increasing adjacent coordinates. Interior bins have the declared `bin_width`; the last bin may be shorter. Distinct regions on the same chromosome must not overlap. Weights are finite nonnegative decimal numbers or the exact missing marker `NaN`. Zero is a valid weight. The command does not calculate or repair balancing weights.

Pixel header:

```text
bin1_id	bin2_id	count
```

Pixel IDs refer to the complete global bin axis, with `bin1_id <= bin2_id`. Counts are finite nonnegative decimal numbers. Record order and repeated coordinates contribute as supplied. The command does not sort or consolidate records. Records between different regions are validated, retained and counted separately; they do not contribute to the within-region cis algorithm. Every input record is accounted for.

Each window is an explicit positive integer multiple of `bin_width`. Specify `ignore_diagonals`, `min_distance_bad`, `min_fraction_valid` and a finite nonnegative `strength_threshold`. The threshold comparison is explicitly **`>=`**. No CLI/API default or paper setting is guessed.

## Reading the output

`outputs/insulation.tsv` has one row per original bin per requested window. It includes the original bin ID, coordinates and region, source output ordinal, normalized score, log2 score, valid-pixel denominator, balanced/raw sums, bad-bin marker, distance to a bad bin, eligibility, boundary strength and call. With positive distance masking, the source returns region groups in sorted region-name order. Use `bin_id` to map back to the input axis.

Missing sparse pixels contribute zero. NaN weight pairs are excluded from balanced sums and valid-pixel counts; raw sums retain their stored counts. Median normalization is per region. The original log2 transform maps zero and nonfinite tracks to `NaN`; the requested distance mask then applies. Distances include the implicit edge positions -1 and N, including when no bins are explicitly bad. The valid-pixel threshold spans all supplied regions for each window.

Some normalized scores or boundary strengths can be missing under the selected source behavior. The source's first-global-tie prominence behavior is retained. Missing markers, eligibility and called boundaries are counted separately in the audit. A NaN strength is not a boundary. Derived numeric text is canonical; exact original spelling remains in the captured inputs.

Saved verification checks the complete file/directory population and exact producer, then recomputes every input record and every bin/window result and compares the complete output and audit. Integrity hashes do not establish authenticity or biological validity.

## Warnings and actions

**SKIP3 means the dependent analysis was skipped.** Restore the named permitted source or executable, or arrange an allowed build. If a required installation is forbidden, skip the dependent analysis. No upstream software is installed automatically by this command.

**FAIL2 means no complete result is admitted.** Keep the attempt and correct the named input, capacity, arithmetic, cancellation or consistency problem. Retry with a new output path. Do not drop failing rows, impute weights, alter thresholds or refresh old hashes to make a check pass.

The declared bin, pixel-row, byte and cooperative wall limits cover complete populations. They are not JVM heap guarantees or immediate-interruption guarantees. Provide sufficient heap and resources for the full requested map; an out-of-memory attempt fails rather than admitting a truncated result.

## Paper roles and qualified scope

- [P003](https://doi.org/10.1038/s41586-025-09890-3) describes insulation using 25 kb bins, a 100 kb window and two ignored diagonals, plus separate compartment and loop analyses. Its insulation edition is unreported; dots has a separate 0.5.1 attribution. The native insulation profile does not supply the compartment/loop steps or identify an unreported author command/version.
- [P053](https://doi.org/10.1038/s41586-023-06819-6) reports cooltools 0.5.1, 10 kb bins and a 500 kb insulation window, with boundary strength **>0.1**. Preserve that literal strict inequality. The selected source API and this native profile use **>=**; they must not be silently equated. Its methylation DMR caller is separately attributed to methylpy.
- [P169](https://doi.org/10.1038/s41588-024-01900-w) reports cooltools 0.5.4 for contact-map domain/compartment analyses. This native profile covers the declared insulation and fixed numeric boundary calculation. Exact author window/method correspondence, compartment phasing and preprocessing remain separate.
- [P228](https://doi.org/10.1016/j.ajhg.2025.07.020) attributes compartment eigenvectors to cooltools `eigs-cis`; other TAD operations in the same paragraph belong to HiCExplorer. The native insulation command does not implement its `eigs-cis` step.

The translated numerical components derive from the MIT-licensed [0.5.1 insulation source](https://github.com/open2c/cooltools/blob/c610f43be909ecf89f561f76a211768e00229550/cooltools/api/insulation.py) and [0.5.4 insulation source](https://github.com/open2c/cooltools/blob/9bacc06692842f31049a8efd2d66f48ef3a39d7d/cooltools/api/insulation.py), together with their peak and distance helpers. The complete original copyright/license notice is packaged as `org/jlinalg/omics/contact/COOLTOOLS-LICENSE.txt`.

Validation compared 459 selected-function cases on Windows and Linux, then the complete original bundled reference map: 2,666 bins, 22 chromosomes and 3,307,826 stored pixels. Both editions' original selected functions and actual sparse query code were used; the entire cooltools package and original orchestration were not executed. The normal source build passed 84 focused tests on each host. Full captures, portable replay, seven corrupted-capture refusals, resource refusal and missing-source skip checks passed on both hosts. These are bounded implementation checks, not author-data or whole-paper reproduction.
