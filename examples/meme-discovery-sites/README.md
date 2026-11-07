# Native MEME discovery result checks

**Component only. This example does not run MEME, reproduce EM fitting, derive
motif significance or admit a complete discovery analysis.** It audits a retained
synthetic original 5.5.9 result against its independently recorded inputs and
settings. No external MEME installation is needed for these Java checks.

From the repository root with JDK 17 or later:

```powershell
$memeSitesJar = 'build/literature-review/meme-runtime/discovery-sites-build-2/omicsmatic-0.3.7-meme-discovery-sites-development.jar'
javac -cp $memeSitesJar -d build/meme-sites-example-classes examples/meme-discovery-sites/Example.java
java -cp "build/meme-sites-example-classes;$memeSitesJar" Example build/meme-sites-example-output
```

The output directory must be **new**. On Linux use `:` instead of `;` in the
classpath. The example reads the pinned test fixture, saves the exact original
FASTA/XML, and checks every contributing word and oriented flank, weighted
probability matrix, prior-smoothed integer score, position P value and complete
sequence diagram. It prints the audited populations and warnings. Producer paths
inside the XML remain inert, so portable verification does not need the original
Linux temporary directories.

The example uses one fixed unweighted canonical-DNA fixture. Its tiny FASTA reader
is not a production parser. To use the library on independently prepared data,
construct `MemeDiscoverySites.Input` records and an `Expected` object with the
original occurrence model, strand setting, widths, prior, seed, exact edition and
resolved background. Supply all original records in their original order. The
component does not infer these choices from the fitted output. Legacy 4.9.1 has
no command-line `-seed` option, and its patch level is not identified by XML alone.

Discovery P values use the source's **100-point** lattice and best orientation;
this differs from FIMO scanning. Sequence statistics include every scorable
motif, even when it has no displayed hit. Original ordering, strict overlap
replacement, approximations and binary64 underflow are preserved. Read the
[development guide](../../docs/literature/MEME_DISCOVERY_SITES_DEVELOPMENT.md)
before interpreting results.

**SKIP3** means an edition, ambiguity/alias conversion or other source behavior
needs its own audit. Preserve the original data and use a matching implementation;
do not silently change the input or edition. **FAIL2** means no component receipt
was admitted: inspect the input, settings and capacities, then retry in a new
directory. A later full discovery workflow also needs its original runtime and
report helpers; if installing or executing them is forbidden or unavailable,
report the exact missing dependency and skip that analysis.

The canonical release JAR is unchanged. Full report/process/runtime admission
and catalog credit remain pending.
