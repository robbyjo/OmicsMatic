# Native QTLtools fdensity API example

Read [the guide](../../docs/literature/QTLTOOLS_FDENSITY.md). Build current source;
the previously released JAR does not contain this class. With a JDK17+ and the
new source-built normal executable:

```powershell
java -cp build/cli/omicsmatic-0.4.0.jar examples/qtltools-fdensity/QtltoolsFdensityExample.java
```

Expected output:

```text
-20 -11 0
-10 -1 0
0 9 2
10 19 0
```

Two annotation rows overlap the first QTL, and one overlaps the second QTL.
The positive bin counts two QTL presences. Inputs are explicit zero-based BED
intervals; only minus strand reverses relative positions. The library preserves
the selected source's small-tree input-order guard and does not reorder callers'
lists. It does not infer variants, roots, assemblies or significance.

This example calls the library API, not a complete QTLtools workflow. The native
component needs Java only. Original-runtime verification has separate pinned
dependencies; missing/prohibited installation must skip that dependent reference
analysis with an explicit reason and recovery path. No family credit is added.
