# Original GSEA counterparts and native checks

These are development/qualification programs, not a project analysis command.
They do not register tools or add paper-review/catalog credit.

- `OriginalGseaProbe.java` calls the unchanged original parser, ranking,
  permutation and empirical-statistics APIs and exports full float objects.
  Unlike the original EDB vector exporter, it does not round them to four
  decimals. It never imports project arithmetic.
- `OriginalPrimitiveProbe.java` exports deterministic original score walks and
  supplied-null family statistics, including original substitutions,
  nonfinite outputs and exceptions. Its seed is 934035. It never imports
  project arithmetic. Rejected source behavior is retained rather than fixed.
- `CheckNativeBroadGsea.java` and `CheckNativeBroadPrimitives.java` compare
  native results with those full-precision objects and check explicit refusal
  boundaries. The latter uses original public hit weights to distinguish
  source-normalized mass from undefined/substituted weighting.
- `PackagedNativeChecks.java` requires the native production class to load
  from the named exact normal JAR, with original GSEA classes absent from its
  classpath. It runs both checkers. It does not execute the original runtime.

Use owned NEW cwd/home/output paths. The original programs require an exact
separately prepared GSEA runtime/dependency classpath. Do not add GSEA to the
project's production classpath or silently use adjacent editions. Original
GSEA initialises an application handler before parser/cache access; the
probe explicitly mirrors that normal constructor initialization.

The checked profiles are local unchanged-source builds of 4.3.2, 4.3.3 and
4.4.0. The two 4.3 tags share a source commit, but each explicit version is
recorded separately. They do not establish identical vendor distributions or
historical author hosts. Source/dependency/license records and original
conditions remain retained. No MSigDB collection or registration was used.

Read the [native guide](../../../docs/vignettes/gsea-native-statistics.md),
[saved qualification](../../../docs/literature/gsea-native-development/README.md)
and [next work](../../../docs/literature/GSEA_NATIVE_CHECKPOINT_2026-11-20.md)
before continuing. The exact qualified programs are fingerprinted; add new
worker/orchestration code separately rather than rewriting saved reference
sources or changing old proof hashes.
