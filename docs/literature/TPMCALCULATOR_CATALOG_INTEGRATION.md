# Native TPMCalculator catalog admission

This checkpoint admits one source-defined native Java TPMCalculator workflow. The catalog now contains **250 papers, 446 named families, 2,690 observations and 1,197 reviewed uses**; **1,493 uses await semantic review**. It records **122 scoped new integrations: 21 native subsets and 101 upstream workflows**, or 125 including three existing adapters. **265 families remain missing.** The full literature and implementation goal remains open.

The corpus remains 71 Nature, 104 Nature Genetics and 75 AJHG papers published within 2023-10-23 through 2026-10-23. Its window was sufficient for 250 papers and was not extended. The extra family is the previously omitted iDEP interface, which remains missing and receives no runtime or implementation credit. Removing TPMCalculator from missing and adding iDEP leaves the missing count unchanged.

The earlier [component](TPMCALCULATOR_NATIVE_DEVELOPMENT.md) and [workflow](TPMCALCULATOR_NATIVE_WORKFLOW.md) qualifications are immutable historical milestones. Their zero-credit statements describe those milestones. This separate admission establishes the new scoped credit without rewriting their source files, scientific tests, evidence or earlier failures.

## What P050 reports

P050, [In vitro production of cat-restricted Toxoplasma pre-sexual stages](https://doi.org/10.1038/s41586-023-06821-y), [PMC10781626](https://pmc.ncbi.nlm.nih.gov/articles/PMC10781626/), describes the following separate steps in its RNA-seq methods paragraph:

| Named software | Reported use | Facts that remain unknown |
| --- | --- | --- |
| TPMCalculator | Transcript quantification and normalization. | Version, caller options, exact GTF and output selection. |
| iDEP.96 | Web interface for differential expression using DESeq2 and default settings. | Exact deployed source, normalized release identity, DESeq2 version and complete effective defaults. |
| Subread 2.0.1 / featureCounts | Alignment to ToxoDB-49 T. gondii ME49 and separate gene read counts. | No additional TPMCalculator settings follow from these steps. |

The paragraph reports three biological replicates per condition, strand-specific paired-end 2x150 bp libraries and BioProject PRJNA921935. These are source context, not recovered TPMCalculator options. The alignment option string is retained literally as `subread-align -d 50 -D 600–sortReadsByCoordinates`; no missing separator or Unicode dash is silently repaired. The iDEP token is retained literally as `iDEP.96`, without converting it to `0.96` or inferring an executed source commit. A separate source/edition/license/runtime assessment is still required before iDEP implementation.

The all-250 primary XML census finds each of these two family names only in P050 paragraph45. Each XML hash and DOI is checked against the fixed corpus. The review stores section/index/hash locators and specific usage summaries; full article text remains in the original private cache under its own license. Two reviews are appended, the existing TPMCalculator observation is refined, and one iDEP observation is added. Every prior review, unrelated observation, paper identity, exclusion and unrelated family is preserved.

## Run, inspect and recover

Follow the copyable JSON request, build, run and verify examples in the [native workflow guide](TPMCALCULATOR_NATIVE_WORKFLOW.md). The standalone entry point is `org.jlinalg.cli.TpmCalculatorCli`; use the self-contained `build/cli/omicsmatic-0.3.7.jar`, or the library JAR plus its exact declared HTSJDK 5.0.0 dependency. The root command dispatcher does not register this standalone workflow.

**Read `WARNINGS.md`, `WARNINGS.json` and `products/CONDITIONS.json` before interpretation.** Original modeling/counting quirks, separate mate counts, missing versus present-zero data, nonfinite values, unsigned coordinates, GNU gene order and single-precision bridge TPM remain retained. Source tables use six significant digits; raw IEEE state retains the full returned values. The selected comparison is original tag0.0.4, whose executable prints0.0.3. It is not an inferred author version. Tag0.0.6 quantification-source equivalence is source inspection, not an executed0.0.6 binary qualification.

**SKIP3 skips dependent analysis** when a named permitted input, exact implementation/dependency, Java executable or output prerequisite is unavailable. Supply the named permitted prerequisite and retry a new output directory. If installation or access is forbidden, retain the skip. Analysis installs/downloads nothing and invokes no original quantification program; its worker is Java.

**FAIL2 admits no complete result**, including malformed inputs, capacity/runtime failures, input changes, replay mismatches and recording failures after native computation returns. Preserve originals and partials. Inspect `FAILURE.json`, `failure-trace.txt`, `WARNINGS.md` and complete `worker/stdout.bin` / `worker/stderr.bin` when present. Resolve the stated cause and retry new output paths. Only final `COMPLETED.json` with `operation: "run"` and `result_admitted: true` admits an analysis. A worker return or leftover product does not. Existing output directories are preserved and rejected. Verification records a replay and does not admit another analysis.

The bundle retains complete GTF/BAM bytes, request/options, file identities, implementation/dependency hashes, all32 state fields, raw IEEE values, conditions, warnings, Java launch/termination and complete streams. Verification reopens captured inputs and freshly recomputes with the matching recorded implementation, then compares semantic state and every source table. Original external input locations are not required. Keep the matching application artifact for long-term replay. Hash integrity does not authenticate authorship or establish biological accuracy. Declared heap, wall, workspace, file and metadata limits are operating capacities; they are not OS memory/disk quotas.

## Evidence and catalog checks

The unchanged [workflow qualification](tpmcalculator-workflow-development/QUALIFICATION.json) binds 141 focused tests, 64 original table cases plus complete captured-input replays on each Windows Java25/Linux Java21 host, nine public failure controls per host, five cross-host replays each direction and five self-contained-application replays per host. There are50,049 original C++ numeric-formatting controls. The57 original full-state cases compare all32 fields, exact integer/presence inventories and nonfinite classifications, with a64-ULP finite floating tolerance. Source table bytes match exactly in the qualified cases.

Complete indexed public synthetic records retain actual native results, original controls, process/worker returns, full input captures, full consoles, deliberately failed public calls and replay artifacts. The earlier component archive retains original build failures and resolved native comparison failures. Catalog verification reads every archived payload, rechecks original table comparisons and validates process/replay/failure records through the unchanged scientific evidence verifier. It does not launch fresh scientific analysis or turn a saved integrity check into a new experiment.

```powershell
python -B tools/literature/tpmcalculator_catalog/catalog.py --verify --rescan-sources --out build/NEW-tpm-catalog-check
python -B tools/literature/tpmcalculator_catalog/test_catalog.py
.\gradlew.bat checkWebsite --no-daemon --console=plain
```

Use a new output directory for each catalog invocation. A missing required cached primary XML is SKIP3; a contradiction, changed qualified source/evidence, malformed record or wrong transition is FAIL2. Read the named cause in the new output's `FAILURE.json`, preserve all existing evidence and supply the permitted original prerequisite. Do not refresh a hash to conceal changed scientific code.

The catalog gate preserves all eight exact predecessor metadata files, verifies the entire predecessor chain, and permits an exact byte inverse only for appended review metadata. Scientific sources and verifiers cannot be overlaid. Current tables, embedded website data and prominent warnings share the same counts. The integration test suite and desktop/mobile browser checks verify the new counts, usage summaries, native credit, missing iDEP status, warnings and recovery commands. Local browser checks do not publish the site.

This evidence establishes the declared native comparison scope. It does not establish broad input compatibility, whole-tool validity, biological accuracy or the authors' historical results. Cell Ranger/ARC remain excluded by user request; prior license/runtime restrictions remain in their own guides.
