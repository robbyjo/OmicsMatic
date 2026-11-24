# edgeR normalization and differential models

Build current source with JDK 21. The released 0.4.0 binary predates these
commands. From the repository root on Windows:

```powershell
.\gradlew.bat --no-daemon executableJar
java -jar build/cli/omicsmatic-0.4.0.jar edger-normalize --help
java -jar build/cli/omicsmatic-0.4.0.jar edger-tools --help
```

Use `./gradlew` on a suitable Unix build host. The saved qualification covers
Windows Java 21/25 and Linux Java 21. Original model fitting was qualified on
Windows R 4.6.1 only; Java-only verification is available on all three JVMs.

## Native matrix normalization: no R runtime

This command offers TMM, TMMwsp, RLE, upper-quartile or none, followed by the
explicit requested raw/log CPM or RPKM transform. Select the source edition
literally: 3.32.1, 3.36.0, 3.40.2, 3.42.4 or 4.10.7. Supply prepared counts,
literal identities and the required library/gene-length sidecars; do not relabel
TPM, log expression or corrected-contact data as raw counts.

The checked-in synthetic example writes only NEW build outputs:

```text
java -jar build/cli/omicsmatic-0.4.0.jar edger-normalize --request examples/edger-native/request.json
java -jar build/cli/omicsmatic-0.4.0.jar edger-normalize --verify build/NEW-edger-native-cpm
java -jar build/cli/omicsmatic-0.4.0.jar edger-normalize --request examples/edger-native/replay.json
```

Use a new destination before repeating RUN/REPLAY. Request paths are resolved
relative to the request JSON. VERIFY recomputes saved native arithmetic and
checks the complete capture; REPLAY writes a new capture from saved inputs.
Read [the native guide](../literature/EDGER_NORMALIZATION.md) for schemas,
edition arithmetic, limits and the log-RPKM example. No upstream subprocess
or automatic model-fitting fallback is used by the native command.

## Original prepared-count models: exact R prerequisite

`edger-tools` offers original filtering/normalization, dispersion estimation,
exact tests, GLM likelihood-ratio tests and explicit legacy/adjusted QL models.
Supported development profiles are `edgeR-3.40.2-R4.6.1`,
`edgeR-3.42.4-R4.6.1` and `edgeR-4.10.7-R4.6.1`. The last profile supports the
explicit adjusted QL branches. Declare raw versus estimated count units,
group levels, numeric design and coefficient/contrast hypotheses yourself.
These profiles do not recreate historical author R/dependency hosts.

Copy the [model example](../../examples/edger-models/README.md) request to a
new filename in the same example directory. Replace only its `rscript` and
`r_libraries` placeholders with your permitted exact R 4.6.1 executable and
private library directories. Keep the selected edgeR profile consistent with
the installed original package. Select a NEW `out` for your analysis, then run:

```text
java -jar build/cli/omicsmatic-0.4.0.jar edger-tools --request examples/edger-models/request-local.json
java -jar build/cli/omicsmatic-0.4.0.jar edger-tools --verify examples/edger-models/output-NEW
```

The verify path above assumes the example's unchanged `out: "output-NEW"`;
use the actual output path if you changed it. REPLAY uses the documented
control request and a NEW destination outside the original capture, including
real filesystem aliases. The standalone classes `org.jlinalg.cli.EdgeRWorkflowCli`
and `org.jlinalg.cli.EdgeRNormalizationCli` remain available with identical flags.
Read [the model/file guide](../literature/EDGER_MODELS.md) before interpreting
the results. Dependencies and conditions are recorded; full historical
dependency/author-host equivalence is not established.

Model VERIFY checks captured integrity, deterministic intake, exported structure
and selected CPM/adjustment/effect arithmetic. It executes no R, independently
refits no model, does not parse binary RDS contents and does not authenticate
coordinated replacement. The complete original R objects are retained for
separate original-runtime investigation and replay.

## Warnings and required actions

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the named exact permitted input,
R/package/profile or retain the skip. Downloads/private installs are allowed
where your operating environment permits. When installation is forbidden,
keep the dependent analysis skipped and report what must be supplied. Full old
3.32.1/3.36.0 inference packages fail under the qualified R 4.6 host; their
selected CPM kernels are not full providers. Exact 3.20.1/3.22.3/3.38.4 model
profiles are unsupported. Do not select an adjacent edition automatically.

**FAIL2: NO COMPLETE RESULT.** Read `WARNINGS.md`, `analysis-status.json`,
`worker/report.json`, `worker/warnings.txt` and logs when present. Preserve
original counts, design/contrasts, model objects, conditions and partials.
Fix the named data/schema, numerical, convergence, source/integrity, capacity
or process cause and retry NEW output. An exposed iteration exhaustion remains
a failure even when a stock test table is finite. Do not change a scientific
model or refresh hashes just to obtain a pass. Two real old-edition null fits
are retained as refusals despite `failed=FALSE`; current 4.10.7 convergence on
that input is a separate explicit-edition result.

These components do not expose the reported power dispersion trend or
`sumTechReps`; they do not perform CLAW/csaw/Hi-C preprocessing, limma/voom
inference, PEER/RINT, author cohort preparation or whole-paper reproduction.
See [the 17-role catalog scope](../literature/EDGER_CATALOG_INTEGRATION.md).
The full 250-paper implementation/review goal remains unfinished.

<!-- SCIENTIFIC-CITATIONS:START -->
## Scientific citations

These are the primary sources for the methods used in this workflow. Cite the relevant paper as well as OmicsMatic when reporting results.

- [Mark D. Robinson, Davis J. McCarthy, and Gordon K. Smyth (2010) — edgeR: a Bioconductor package for differential expression analysis of digital gene expression data](../CITATIONS.md#robinson-edger-2010) — [PMID: 19910308](https://pubmed.ncbi.nlm.nih.gov/19910308/) · [PMCID: PMC2796818](https://pmc.ncbi.nlm.nih.gov/articles/PMC2796818/)

[Search the complete scientific bibliography](https://robbyjo.github.io/OmicsMatic/citations.html).
<!-- SCIENTIFIC-CITATIONS:END -->
