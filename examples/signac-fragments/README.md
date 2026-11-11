# Native Signac fragment examples (development)

These are small **synthetic data**, with invented chromosomes and barcodes. They are not a reference genome or a biological QC recommendation. Run from the repository root. Use the locally built development JAR; the canonical release JAR does not contain this workflow.

```powershell
$fragmentJar = 'build/literature-review/signac-runtime/fragment-workflow-build-3/omicsmatic-0.3.7-signac-fragment-workflow-development.jar'
java -Xmx512m -cp $fragmentJar org.jlinalg.cli.SignacFragmentsRequestCli --request examples/signac-fragments/feature-request.json
java -Xmx512m -cp $fragmentJar org.jlinalg.cli.SignacFragmentsRequestCli --request examples/signac-fragments/count-request.json
java -Xmx512m -cp $fragmentJar org.jlinalg.cli.SignacFragmentsRequestCli --request examples/signac-fragments/qc-request.json
java -Xmx512m -cp $fragmentJar org.jlinalg.cli.SignacFragmentsRequestCli --request examples/signac-fragments/binary-request.json
java -cp $fragmentJar org.jlinalg.cli.SignacFragmentsRequestCli --request examples/signac-fragments/verify-request.json
```

The first request creates `build/signac-fragments-feature-demo-NEW`. The others create separate `count`, `qc` and `binary` demo directories. Every analysis writes `WARNINGS.md`, a status, captured inputs and checksums. Verification recomputes from those captured inputs. The binary request consumes the feature request's matrix, so run it after the feature request. Existing output directories are refused: edit each request's `out` to a new directory for another run, and update downstream input/verify paths accordingly. Do not delete an earlier result to make a command succeed.

**Read warnings before using tables.** The example includes a chromosome absent from the fragments and a QC cell `Z` with no fragments. Its explicit map gives `Z` a `NaN` ratio; an unmapped missing cell would instead receive `NA`. Neither is replaced by zero. Fragment regions use **one-based closed** coordinates; the fragment file uses **zero-based half-open BED** coordinates. `genome_build` records your declaration, without certifying assembly compatibility.

No R installation is needed for these four native operations. An optional next step uses the separately validated Signac LSI adapter and its exact R dependencies:

```powershell
java -Xmx512m -cp $fragmentJar org.jlinalg.cli.SignacLsiRequestCli --request examples/signac-fragments/lsi-request.json
```

Edit the R and library paths inside that UTF-8 JSON for your system. Missing dependencies produce **SKIP3**; retain the skipped run and install/provide the named runtime only where permitted, then rerun into a new directory. See the [fragment guide](../../docs/literature/SIGNAC_FRAGMENTS_DEVELOPMENT.md) and [LSI guide](../../docs/literature/SIGNAC_LSI_DEVELOPMENT.md). Full Signac integration remains incomplete.
