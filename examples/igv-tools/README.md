# Audited original IGV example

This example uses synthetic reference, annotation, signed signal, copy-number segments and tagged/filtered BAM reads. It contains no patient data. Supply the development executable JAR and an admitted original IGV runtime:

```powershell
python examples/igv-tools/run-workflow.py --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe" --jar build/literature-review/igv-candidate-3/jlinalg-0.3.7.jar --home build/omics-igv-runtime-v2 --out "build/igv example"
```

Where permitted, prepare the private runtime separately:

```powershell
python tools/literature/install_igv.py --home build/omics-igv-runtime-v2 --cache build/igv-archive-cache --java "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe" --allow-install --allow-download
```

**Read `WARNINGS.md` and its Required action first.** Exit3 skips an unavailable dependency;exit2 retains failed inputs/output under `partial/`. Existing homes, archives and output directories are preserved. Never re-seal a changed runtime. Installation needs a full Java21 JDK,Windows x64 and usable Windows graphics/fonts. The runtime and temporary argument carriers need ASCII paths;data and output paths can contain Unicode.

Successful output contains unmodified original PNGs,relative-path original sessions,full native reference/feature/scalar/missing/read/count comparisons and `FIGURE_LEGEND.md`. The original ruler can label an actual600-base half-open interval as601bp;use the native audit coordinates. Read filters,quality,overlapping mates,SEG coordinate convention/raw units and scales are explicit. Raw MM/ML tags are audited;this example does not interpret methylation or call variants/allele fractions. Automatic CNA cohort-frequency/custom synteny summaries remain unfinished. Reopening a session interactively can use different global preferences;the retained PNGs and ledger are the verified execution.

`data/provenance.json` authenticates every synthetic fixture and the original pyBigWig ramp provenance.
