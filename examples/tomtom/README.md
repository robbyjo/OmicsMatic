# Original Tomtom motif comparison

This development example uses three synthetic DNA motifs as both query and target.
It produces nine reported pairs per mode and tests complete workflow handling. **It
does not reproduce a paper or establish biological significance.** Original Tomtom
warns that fewer than 50 target motifs give unreliable P-value calibration.

Use a development JAR containing `tomtom-tools`; the released/canonical JAR has not
been updated by this work. Run these commands in Linux x86_64 Java 17+ and Python
3.12+ (inside WSL on Windows). Replace `/ABS/omicsmatic.jar` and `/ABS/checkout`.
Keep output parents present and use new paths for every attempt.

```sh
OMICS_JAR=/ABS/omicsmatic.jar
CHECKOUT=/ABS/checkout
java -jar "$OMICS_JAR" tomtom-tools --export-runtime-setup /var/tmp/tomtom-kit-NEW
cd /var/tmp/tomtom-kit-NEW
python3 -I -B ./setup_tomtom.py --report /var/tmp/tomtom-setup-NEW --archive /ABS/meme-5.5.9.tar.gz --allow-build --research-nonprofit
```

Read `RUN-SETUP.txt` and `UPSTREAM-COPYING` first. The build uses the exact official
5.5.9 archive. For a separately permitted download, omit `--archive` and add
`--allow-download`. The helper does not install host packages. If a needed compiler,
library, Perl module, permission or download is unavailable, **skip the dependent
analysis** and complete only permitted setup. Read all retained build logs.

Copy the exact `home` and `build_evidence` from the successful setup report into
these two variables; the compiled original home embeds its location and cannot
simply be moved. `COMPLETED.json` in the setup report is not analysis completion.

```sh
MEME_HOME=/EXACT/home-5.5.9
BUILD_EVIDENCE=/EXACT/build-evidence
python3 -I -B "$CHECKOUT/examples/tomtom/make_recipes.py" --out /var/tmp/tomtom-recipes-NEW
java -Xmx1g -jar "$OMICS_JAR" tomtom-tools --recipe /var/tmp/tomtom-recipes-NEW/text.json --home "$MEME_HOME" --build-evidence "$BUILD_EVIDENCE" --out /var/tmp/tomtom-text-NEW
java -Xmx1g -jar "$OMICS_JAR" tomtom-tools --recipe /var/tmp/tomtom-recipes-NEW/full.json --home "$MEME_HOME" --build-evidence "$BUILD_EVIDENCE" --out /var/tmp/tomtom-full-NEW
java -Xmx1g -jar "$OMICS_JAR" tomtom-tools --verify /var/tmp/tomtom-text-NEW
java -Xmx1g -jar "$OMICS_JAR" tomtom-tools --verify /var/tmp/tomtom-full-NEW
```

Each command captures unchanged complete inputs, checks fresh current-host runtime
health and retains the original process. TEXT uses the original `-text` result;
FULL_REPORT requires the original Perl/XML report capability and retains XML, TSV
and HTML. The verifier checks the HTML's entire scientific display payload against
the XML. It never silently changes output mode or invents another original run.

Read `WARNINGS.md`, `OUTPUT-AUDIT.json`, `PAIR-LEDGER.tsv`, `metadata/producer.json`
and all files in `process/`. Original stderr is retained even after exit 0. Every
declared query–target pair is listed; unreported numeric values are **NA**, never
zero or inferred biological absence. Do not recalculate q-values from the reported
best matches; the original tested orientation family is larger.

Require the **overall** `COMPLETED.json` and successful `--verify`. You can copy the
whole bundle and verify it on Windows using Windows Java and the copied path;
verification never opens the old producer paths or creates a runtime ticket.
Hashes establish integrity, not authenticity or biological accuracy.

**SKIP3** means a named dependency/permission/capability is unavailable: skip the
analysis. **FAIL2** means invalid input, integrity, process, capacity, cancellation
or finalization failure: no result is admitted. Read `FAILURE.json` and the named
stage/log, preserve all partials and the native worker path recorded in warnings,
correct the cause and retry with new paths. Declared native limits are not a Java
heap guarantee; allocate enough permitted memory for the complete model/result
population. The synthetic limits are deliberately small.

Supported inputs are complete DNA minimal/learned MEME/STREME text and supported
5.5.9 XML. HTML input, source-truncating identifiers, source duplicate/pruned
populations and other unsupported source cases produce a named skip. Settings,
background and capacities are explicit recipe fields; arbitrary flags and historical
version substitution are unavailable. Cell Ranger remains excluded.
