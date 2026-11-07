# Native TSS enrichment and gene activity (development)

These inputs are **synthetic**, with invented genes, chromosome labels and fragment endpoints. They are not a reference annotation or a recommendation for biological QC cutoffs. Run from the repository root with the local development candidate; the canonical release JAR does not contain these workflows.

```powershell
$annotationJar = 'build/literature-review/signac-runtime/annotation-workflow-build-1/omicsmatic-0.3.7-signac-annotation-workflow-development.jar'
java -Xmx512m -cp $annotationJar org.jlinalg.cli.SignacAnnotationsRequestCli --request examples/signac-annotations/fast-request.json
java -Xmx512m -cp $annotationJar org.jlinalg.cli.SignacAnnotationsRequestCli --request examples/signac-annotations/profile-request.json
java -Xmx512m -cp $annotationJar org.jlinalg.cli.SignacAnnotationsRequestCli --request examples/signac-annotations/derived-request.json
java -Xmx512m -cp $annotationJar org.jlinalg.cli.SignacAnnotationsRequestCli --request examples/signac-annotations/gene-request.json
java -cp $annotationJar org.jlinalg.cli.SignacAnnotationsRequestCli --request examples/signac-annotations/verify-request.json
```

The requests create separate `build/signac-tss-fast-demo-NEW`, `signac-tss-profile-demo-NEW`, `signac-derived-tss-demo-NEW` and `signac-gene-activity-demo-NEW` directories. Read each `WARNINGS.md` and status before using results. Verification recomputes from captured inputs and checks result tables, selected genes/TSSs, warnings and metadata. Existing destinations are refused: choose a new `out` and update dependent paths for another run, preserving earlier attempts.

**Warning: fast and profile TSS scores can differ in the original source.** Both retain the legacy endpoint offsets and center windows. Missing-chromosome chunks, trimmed windows and single-row profiles can fail. Do not switch modes or discard problematic TSSs silently to obtain a result. The profile example writes an explicit row index because source profile cell names can repeat across files.

Gene activity unions annotations per gene ID and counts fragment lines over gene bodies plus promoters. It does not measure RNA expression. Gene names can be duplicated when different genes have the same coordinates; inspect warnings before assigning counts to genes. Annotation names may be blank, in which case gene IDs supply the activity names. Coordinates in the position/annotation TSVs are **one-based closed**; fragments use **zero-based half-open BED**. `genome_build` records a declaration and does not certify assembly compatibility.

The TSS examples include the unobserved synthetic cell `Z`. The gene example explicitly selects `D,B,A,C` for the downstream demonstration. That choice is recorded in `gene-cells.tsv`; the workflow performs no automatic cell QC exclusion.

All four native requests need **no R installation**. Optional LSI on the gene matrix requires the separately validated exact R runtime:

```powershell
java -Xmx512m -cp $annotationJar org.jlinalg.cli.SignacLsiRequestCli --request examples/signac-annotations/lsi-request.json
```

Edit R executable/library paths in that UTF-8 JSON for your environment. Missing dependencies produce **SKIP3**: provide/install the named runtime only where permitted, preserve the skipped attempt and use a new output directory. If installation is forbidden or unavailable, skip dependent LSI. See the [annotation guide](../../docs/literature/SIGNAC_ANNOTATION_DEVELOPMENT.md) and [LSI guide](../../docs/literature/SIGNAC_LSI_DEVELOPMENT.md). Full Signac integration remains incomplete.
