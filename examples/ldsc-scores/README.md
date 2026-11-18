# Native LD-score SDK demonstration

The complete synthetic PLINK files and coordinate table are original-reference
case L018. This example uses every declared FAM sample, explicit selected BIM
IDs/order, coordinate window 10, requested chunk size 1 and strict MAF > 0.
It does not represent a study reference panel or author GWAS analysis.

Build current source and use Java 17+ from the repository root:

```powershell
.\gradlew.bat :executableJar
java -cp build/cli/omicsmatic-0.4.0.jar examples/ldsc-scores/LdscLdScoresExample.java
```

Standard output contains the complete retained-variant L2 table. Standard error
contains warnings and full/common annotation-count summaries. Exceptions mean
the calculation failed; do not consume partial console output as a complete
result. This is an SDK example, with no workflow completion/admission marker.
Use the [API guide](../../docs/vignettes/ldsc-ld-scores.md) for real integration,
required input/provenance retention, limits and failure actions. The published
0.4.0 JAR predates this source addition.
