# GCTA development evidence

**Unfinished development, zero new catalog credit.** Read the
[current handover](../HANDOVER_2026-11-20_GCTA.md) and
[source-role notes](SOURCE-NOTES.md) before continuing.

`synthetic-evidence.zip` preserves generated inputs, original Windows/Linux
GCTA 1.94.1 outputs and failures, five initial native captures per JVM and the
probe Java source. `SYNTHETIC-INVENTORY.json` gives each member's size/SHA-256.
`PROTOTYPE.json` pins the archive, native Java source and the nine actual
Windows-JDK-21 probe class files for cross-host runtime checks. No paper full
text, author genotype data or original executable is included.

`SOURCE-LOCATORS.json` retains all-250 source identities, candidate paragraph
locations and hashes without primary-paper paragraph prose. These are research
findings, not canonical reviews. `historical-research-scripts.zip` preserves the
16 original machine-specific orchestration helpers; inspect their absolute paths
and use fresh output destinations before reuse. Do not rerun an old driver
against its existing output directory.

From the project root, with a permitted Python 3 and JDK 21 or later:

```powershell
python -B tools/literature/gcta_development/verify_initial_grm.py --out build/gcta-resume-check-1 --java "C:/path/to/jdk/bin/java.exe" --javac "C:/path/to/jdk/bin/javac.exe"
```

The output must be new. This recompiles only the prototype and saved probe,
then compares five cases against captured original float32 GRM/count files
and ordered composite IDs. It performs no download, original GCTA execution,
normal-project build, CLI registration or catalog mutation. Passing means
185,280 exact fields for these cases only. It is not full-tool qualification.

If a host has Java but no compiler, the exact pinned Windows-JDK-21 probe
classes can be transferred from the saved successful compile and selected
explicitly:

```powershell
python -B tools/literature/gcta_development/verify_initial_grm.py --out build/gcta-runtime-check-1 --java "C:/path/to/java.exe" --compiled-classes "C:/path/to/source-built/classes"
```

The complete nine-class byte inventory and native source must match the profile;
arbitrary compiled classes are refused. In WSL use Linux paths and
`--java /usr/bin/java`. Linux `/usr/bin/javac` was absent during handover, so
Linux source compilation was SKIP3; the runtime check passed using these pinned
classes. Supply a permitted JDK to perform an independent Linux compile.

`FRESH-CHECKS.json` and `fresh-check-diagnostics.zip` record the final three-JVM
probe checks. The normal producer build, edge/refusal JUnit corpus, model
semantics, file workflows and catalog admission remain open. The fastGWA
768-variant failure is retained and requires a larger new fixture, not a
success relabel or automatic threshold change. See the handover for all exact
SKIP3/FAIL2 recovery steps.
