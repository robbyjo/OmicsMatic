# Native prepared Monocle 3 pseudotime example

This example uses the new **Java API component**. It needs Java 17 or later and the separate development JAR. It needs no R runtime. The canonical/released JAR lacks this API.

The caller supplies cell coordinates, principal-point coordinates, explicit principal roots and the exact projected cell graph. This example uses a small synthetic graph. It does not learn a trajectory from expression data.

From the project root in PowerShell, after building the candidate described in [the development guide](../../docs/literature/MONOCLE3_PSEUDOTIME_DEVELOPMENT.md):

```powershell
$monoJar = (Resolve-Path 'build/literature-review/monocle-runtime/native-build-1/omicsmatic-0.3.7-monocle3-pseudotime-development.jar').Path
javac -encoding UTF-8 -cp "$monoJar" -d build/monocle-pseudotime-example examples/monocle3-pseudotime/MonoclePseudotimeExample.java
java -cp "$monoJar;build/monocle-pseudotime-example" MonoclePseudotimeExample
```

Expected output:

```text
Principal root Y_1 selects nearest cell: cell-b
Cell order: [cell-a, cell-b, cell-c]
Pseudotime: [3.0, 0.0, 5.0]
```

`cell-b` is nearest to the principal root. Its graph distance is zero; the other distances use the supplied edge weights, 3 and 5. They are not Euclidean distances from the principal point.

**Warnings and recovery:** print or otherwise prominently retain every `result.warnings()` entry. `Inf` can indicate a disconnected cell or overflowed path sum; retain it and inspect the graph/root population. An `IllegalArgumentException` admits no result. Preserve the exact inputs, correct the named coordinate/identifier/edge/root/budget cause, and retry. Do not impute missing values, repair identifiers, rescale overflowed distances or invent roots silently.

**Full Monocle analysis is still unavailable through this component.** It does not supply graph learning, preprocessing, gene models, Monocle 2, Cicero or the Python `py-monocle` wrapper. Do not treat this example as a substitute for those paper workflows or as evidence of whole-paper reproduction.
