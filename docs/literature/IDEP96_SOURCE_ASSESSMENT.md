# Legacy iDEP.96: source assessment and implementation boundary

**This is a source-audit milestone, not an iDEP implementation or scientific analysis. It adds zero integration credit.** The qualified catalog at this checkpoint has 122 scoped integrations, including 21 native Java methods; iDEP remains missing. Its runtime, complete scientific workflow and legacy-edition license assessment remain open.

P050, [PMC10781626](https://pmc.ncbi.nlm.nih.gov/articles/PMC10781626/), reports DESeq2 and default settings within the literal `iDEP.96` web interface. The author source commit, exact deployed dependency versions, complete effective defaults and original interface state are not reported. The adjacent TPMCalculator step is separate. The [catalog review](TPMCALCULATOR_CATALOG_INTEGRATION.md) preserves those unknowns.

## Pinned source and findings

The official [legacy repository](https://github.com/iDEP-SDSU/idep/tree/07644431f55b5a9f7f9ff6e630e8d3f360eeca91/shinyapps/idep96) retains a dedicated `shinyapps/idep96/` directory. The project assessment pins commit `07644431f55b5a9f7f9ff6e630e8d3f360eeca91` and checks each selected file against both its SHA-256 and the Git blob identity returned by the official repository tree. This is a selected project comparison source, not a recovered author commit. Its UI literal is `iDEP.96`; its server literal is `iDEP 0.96`. Both are recorded without rewriting the paper's token.

R's parser inspected the three original R files without evaluating any original expression. The current audit records 306 selected bindings, 226 selected calls, 119 UI input definitions and all 65 server reactive assignments. Complete private captures retain the original bytes, parsed expressions and tables; the public evidence contains identities and derived findings rather than redistributing legacy source.

| Source finding | Consequence for implementation |
| --- | --- |
| Server and standalone-helper `detectGroups` functions differ. | Sample-group behavior must follow the server path used by the interface, including sample-information matching and fallback behavior. |
| Server and standalone-helper `DEG.DESeq2` functions differ. | The standalone script is insufficient evidence for the interface's scientific workflow. The server sets a 200-sample limit and a 50-comparison limit; it also defines a 30-sample threshold for default method selection. These are source settings, not recovered paper settings. |
| The UI FDR control starts at `0.1`, while the server function's formal argument defaults to `0.05`. | A bare call using function defaults does not establish the UI's supplied cutoff. The source UI also starts with a twofold cutoff, minimum 0.5 CPM in one library, gene-median imputation and ID conversion enabled. Actual effective settings can depend on reactive updates. |
| The server input path disables CSV/TSV quoting and comment parsing, uppercases identifiers, removes spaces and quote characters, and handles duplicates according to variability. | Raw input bytes and original identifiers must remain captured, and source transformations must be explicit. A generic CSV reader or silent identifier repair would change behavior. |
| The standalone core file contains top-level installers. | Unconditional sourcing could install packages before analysis. Setup and analysis must remain separate; analysis must not execute that installer. |

The source parser also retains the complete calls and unevaluated UI/default expressions. It does not run reactive updates, infer an effective model from initial UI declarations, evaluate dynamic model expressions, fit DESeq2, query annotation databases, launch Shiny or establish biological accuracy. Its RNG state remains unchanged.

## Run the static audit

Use an existing permitted Rscript and the complete selected source files plus their intake profile. The profile records `repository_commit` and `members`, each containing `bytes`, `sha256` and `git_blob_sha`. Hashes establish integrity, not source authenticity or permission. The recorded official intake provides the provenance for this project assessment.

```powershell
python -B tools/literature/idep96/source_audit.py --source D:/permitted/selected-source --profile D:/permitted/SOURCE-READBACK.json --rscript "C:/Program Files/R/R-4.6.1/bin/Rscript.exe" --out D:/results/NEW-static-audit
```

The source root contains `shinyapps/idep96/server.R`, `iDEP_core_functions.R` and `ui.R`; include every member named by the profile. The output's parent must exist, and the output must be new and separate from the input source. The audit captures complete source bytes privately, invokes only the project parser through `Rscript --vanilla`, records the full command, streams and exit, and rechecks source/recipe identities before completion. Its parser uses base R packages only. No application package is installed or loaded, and no scientific result is admitted.

**SKIP3 means the static audit could not run because a named permitted source or Rscript prerequisite is missing.** Supply that prerequisite and retry new output paths. If access or installation is forbidden, retain the skip. **FAIL2 means no complete audit is admitted**, including malformed R, changed identities, parser failures and recording failures after parsing returned. Preserve partials, inspect `FAILURE.json` and complete `process.stdout` / `process.stderr`, resolve the named cause and use new output paths. Existing outputs are preserved. A quarantined `COMPLETED-UNADMITTED-*.json` is historical evidence only. Audit completion is not analysis completion.

Eight actual parser controls exercise nonexecution canaries, default-argument calls, missing source/runtime prerequisites, changed hashes, malformed R, existing output preservation, recording failure after a successful parser return, and revocation after completion promotion. The canary sources contain writes, installers and throwing functions; none executes. The controls use project-owned synthetic code, not an iDEP analysis.

```powershell
python -B tools/literature/idep96/test_source_audit.py --rscript "C:/Program Files/R/R-4.6.1/bin/Rscript.exe" --out build/NEW-idep-parser-controls
python -B tools/literature/idep96/freeze_source_audit.py
```

The saved [assessment qualification](idep96-source-assessment/QUALIFICATION.json) distinguishes public artifact readback from complete private-source/record readback. Use the verifier's `--private-base` option with the original permitted evidence directory to check every private file. A public-only check does not establish that those private captures are present or unchanged.

## Work required before iDEP admission

The [current Golem repository](https://github.com/gexijin/idepGolem#license) states noncommercial terms and additional local-installation conditions. Those current terms are not evidence of the license attached to these legacy files. No license declaration was found in the six selected legacy files inspected. The original [2018 software paper](https://doi.org/10.1186/s12859-018-2486-6) provides source/database availability, but does not recover the paper's executed snapshot. Resolve the applicable legacy-source/runtime permissions before distributing or qualifying that runtime; this assessment neither grants permission nor substitutes the current Golem application.

The implementation must preserve the actual server path: count-file reading/filtering/transforms, optional sample information, original identifier conversion and its database snapshot, model/reference-level/comparison selection, DESeq2 results and calls, complete intermediate/returned objects, conditions and RNG behavior. UI progress can consume RNG, so bypassing it must not be described as a full interface-state replay. Reference data, quote/progress inputs, package versions and caller choices must be explicit project inputs when the original author state is unknown.

A future headless workflow needs independent comparison with the unchanged server path, complete saved-input replay, full process/error recording, permitted dependency setup, capacity controls and actual packaged checks. A generic DESeq2 helper, matching helper-function signature or successful static parse does not satisfy those requirements. The runtime analysis remains unqualified and receives no credit here. The full 250-paper implementation and semantic-review goal remains open.
