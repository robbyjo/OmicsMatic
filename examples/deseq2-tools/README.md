# Main-command DESeq2 examples

Use the [current guide](../../docs/literature/DESEQ2_TOOLS.md) for the build and private-runtime setup. From the repository root after building:

```powershell
$deseqJar = 'build/literature-review/deseq2-runtime/public-tools-development/omicsmatic-0.3.7-deseq2-tools-development.jar'
java -jar $deseqJar deseq2-normalize --request examples/deseq2-tools/normalize.json
java -jar $deseqJar deseq2-normalize --request examples/deseq2-tools/frozen-means.json
java -Xmx512m -jar $deseqJar deseq2-tools --request examples/deseq2-tools/paired.json
java -Xmx512m -jar $deseqJar deseq2-tools --request examples/deseq2-tools/shrinkage.json
java -Xmx512m -jar $deseqJar deseq2-tools --request examples/deseq2-tools/lrt.json
java -Xmx512m -jar $deseqJar deseq2-tools --request examples/deseq2-tools/verify-paired.json
```

The first two requests need Java only; the other analyses require the permitted exact R 4.6.1/DESeq2 1.52.0 private runtime. Requests share frozen synthetic tables with sibling directories; keep those directories when copying examples. Update `rscript`/`r_libraries` for your machine. Each `out` is a new folder under `build/deseq2-tools-example-*`. Existing outputs are refused; change `out` and corresponding VERIFY `analysis` together for another attempt. Relative request paths are resolved against the JSON file, not the shell directory.

Read each capture's `WARNINGS.md` and `analysis-status.json`. **SKIP3:** supply the named requirement only where allowed; otherwise skip dependent analysis. **FAIL2:** no result admitted; retain partial files/logs, correct the named cause and use NEW paths. No automatic installation, rounding or identifier repair occurs. Native normalization does not fit differential expression. VERIFY is Java-only and checks structural consistency without refitting statistical models or authenticating coordinated fabricated captures. These are synthetic controls, not paper reproduction or a released build.
