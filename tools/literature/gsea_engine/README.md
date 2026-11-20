# Native GSEA engine checkpoint

This is a native Java library checkpoint for prepared, literal identifiers and
finite float32 inputs. It implements ranking, source randomization, observed and
null scores, and meandiv empirical statistics for the declared source profiles.
It has no vendor executable dependency. Start with the
[current handover](../../../docs/literature/HANDOVER_2026-11-20_GSEA_ENGINE.md).

`OriginalEngineProbe.java` observes original GSEA APIs. Its package intentionally
matches the original template package to use protected constructors; it does not
modify the original code. Each case compares the exported objects from stock and
audited scoring tables. The audit detects original fallback or invalid weights.
Native refusal of such a branch is explicit, rather than substituting a value.
The reference probe requires the exact privately built original runtime and is
not a project runtime prerequisite. Preserve its full diagnostics.

`CheckEngineSweep.java` compares native output with the complete option/boundary
corpus: rank identities, float score bits, double ranking metrics, member order,
score profiles, hit indices, every null ES and family statistics. The JUnit test
checks the same frozen original corpus from its checksummed resource. The corpus
covers three separately built source profiles, 208 cases per profile. This is a
bounded synthetic qualification, not whole-tool or author-data reproduction.

`CheckNativeEngine.java` checks all nine original 10,000-permutation compositions.
`PackagedEngineChecks.java` additionally requires the production classes to load
from the declared normal executable JAR. Neither checker requires GSEA binaries.

Run the focused regression tests from a fresh checkout with the normal project
build setup:

```powershell
.\gradlew.bat :test --tests org.jlinalg.enrichment.BroadGseaEngineTest --tests org.jlinalg.enrichment.BroadGseaStatisticsTest
```

Read saved qualification without executing Java, Gradle or a vendor runtime:

```powershell
python -B tools/literature/verify_broad_gsea_engine_checkpoint.py
```

The file workflow, RUN/VERIFY/REPLAY, global dispatcher registration, complete
source-role crosswalk and guarded catalog admission remain unfinished. Source
size-filter quirks, positive-class orientation, order and explicit options must
be preserved in that future workflow. Do not claim a ready end-user command.
