# Independent STREME enrichment checks

**Component only. This example does not run STREME or admit motif discovery.** It
checks fixed-margin sequence counts with an exact inclusive right-tail Fisher
calculation. Training values are selection scores. Without a held-out population,
the original test-table values are sentinels, not significance evidence.

From the repository root, using JDK 17 or later and the separately validated
development artifact:

```powershell
$stremeJar = 'build/literature-review/meme-runtime/streme-enrichment-build-1/omicsmatic-0.3.7-streme-enrichment-development.jar'
javac -cp $stremeJar -d build/streme-enrichment-example examples/streme-enrichment/Example.java
java -cp "build/streme-enrichment-example;$stremeJar" Example
```

The no-argument example uses prepared counts from the synthetic original-runtime
controls, not a paper dataset. It prints exact fractions, binary64 probabilities
and finite log10 values. The exact fraction and log remain meaningful if the
binary64 probability underflows to zero.

To check all reported Fisher fields and the final reported-family E-value
multiplier in an original XML file:

```powershell
java -cp "build/streme-enrichment-example;$stremeJar" Example --xml 5.5.4 C:/data/streme.xml
```

Use the **actual producer version**: 5.5.4 or 5.5.9. On Linux, replace the classpath
separator `;` with `:`. Original producer paths inside the XML remain inert text.
This audit checks arithmetic and printed precision; it does not check the motif
matrix, site scores, threshold selection, sequence partition, source execution or
complete output family. Read every warning on stderr.

**SKIP3** means the supplied edition or objective needs another audit. Retain the
result and use the matching implementation; do not relabel binomial or
central-distance results as Fisher. **FAIL2** means malformed, changed,
inconsistent or excessive input produced no receipt. Inspect the original input
and capacities, correct the cause, and rerun. This Java component needs no
external installation. A later discovery workflow still needs its original
runtime; where installing it is forbidden or unavailable, skip that analysis and
report the missing dependency and recovery action.

The canonical release JAR is unchanged. Full discovery execution and catalog
credit remain pending.
