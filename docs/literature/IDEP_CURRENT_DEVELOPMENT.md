# Current iDEP: selected source, private runtime and original smoke test

**The user selected the newest iDEP for project implementation instead of the legacy iDEP.96 runtime.** This checkpoint pins current source, establishes a private Windows runtime and records one successful original scientific smoke test. **A runnable project workflow, its full qualification and iDEP catalog admission remain pending. No integration credit is added here.** The catalog remains at 122 scoped integrations: 21 native Java and 101 upstream workflows.

P050's reported interface identity remains literally `iDEP.96`. Its author source commit, DESeq2 version and complete effective settings remain unknown. The current implementation target is a separate project choice; it does not rewrite the paper's reported version or reproduce the historical study. The [legacy source assessment](IDEP96_SOURCE_ASSESSMENT.md), including its server/helper discrepancies and zero-credit boundary, is retained as a historical milestone. Legacy-runtime permission clarification is no longer the prerequisite for the user-selected current target.

## Selected current version

As checked on 15 November 2026, the official default branch reports **idepGolem 2.4.4**, commit [`60479c4375ea07bc930dc40b3c85230856f4ad50`](https://github.com/gexijin/idepGolem/tree/60479c4375ea07bc930dc40b3c85230856f4ad50), committed 14 October 2026. The maintainers' [latest published release](https://github.com/gexijin/idepGolem/releases/tag/v2.4.0) is the older `v2.4.0`, published 9 November 2025. The project selects the newer default-branch source by its exact commit, without sorting or normalizing the authors' version strings. This is a dated source selection, not a claim that the commit will remain newest.

All 336 regular files in the complete selected archive match the file sizes and Git blob identities retrieved from the official Git tree. SHA-256 identities are retained as well. No original algorithms or package metadata were patched. Integrity checks do not authenticate authorship.

The selected [README](https://github.com/gexijin/idepGolem/blob/60479c4375ea07bc930dc40b3c85230856f4ad50/README.md#license) states CC BY-NC 3.0 noncommercial terms, says local installation is free for nonprofit organizations, and asks private institutions to contact the maintainer. The project's previously authorized academic noncommercial research intent is recorded. **Other intended uses must meet the applicable terms before installation or analysis.** The original DESCRIPTION file still contains a placeholder License field; it was preserved rather than silently repaired. Original source, dependency binaries and private scientific returns are not redistributed in this project checkpoint.

## Runtime and observed failures

The new private runtime uses Windows R 4.6.1 and DESeq2 1.52.0. The dependency check found 25 of 62 direct imports already available. Setup installed the missing dependencies into new private libraries and reused existing libraries as read-only inputs.

The first attempt stopped with three required imports unavailable: `biclust`, `hgu133plus2.db` and `ggalt`. Its full original warnings and partial installation remain retained. Resolution used the cached, original Rtools45 GCC 14.3.0 toolchain, original CRAN biclust 2.0.3.1 source, the original current ggalt 0.8.0 maintainer source, and the required Bioconductor annotation packages. These are explicit project dependency choices, not recovered author versions. The second attempt installed and loaded the unchanged iDEP package successfully.

The first scientific smoke attempt reached iDEP's DESeq2 dispatcher but returned an original character-valued error because `BiocManager` was absent, despite not being declared among the package's direct Imports. That returned error and all intermediate objects remain retained; no result was admitted. Installing BiocManager in a separate private overlay resolved that prerequisite. Earlier preparation failures, involving BOM-aware source parsing and an overridden duplicate source definition, remain retained too.

The first setup verified every existing library file in its seven-library baseline unchanged: 30,438 files. The second checked 12,240 existing runtime-code, metadata and data files across eight libraries; documentation, headers and test/example trees were outside that second check. All installations targeted new private libraries. Those scopes must not be conflated.

**SKIP3 means a required permitted runtime, package, reference or installation prerequisite is unavailable; skip the dependent analysis.** Supply the named permitted requirement and retry new paths. If installation or access is forbidden, retain the skip. **FAIL2 admits no complete result**, including returned upstream error strings, failed original computation, changed inputs or recording failures. Preserve complete consoles, original objects and partials; resolve the named cause before a new attempt. Setup completion or successful namespace loading is not an admitted analysis.

## Successful original smoke test and its limits

The successful smoke test runs the installed original package through `input_data`, `convert_data`, `pre_process` and `limma_value` with the DESeq2 method. It uses the upstream synthetic 200-gene, nine-sample count fixture and produces the three pairwise comparisons between its A, B and C groups. All original return objects, intermediate data, conditions and before/after RNG states are retained in a complete private RDS.

The test compares 260 source definitions covering 259 active namespace function names. Each active function's body and formal arguments matches at least one original definition in the pinned source. One earlier definition is overridden by another original definition; that fact is recorded rather than mislabeled as a scientific source modification.

**Original conditions were retained.** DESeq2 converted character design variables to factors and automatically substituted a local dispersion fit when its parametric trend was not well captured. The project did not patch the fit type or suppress those conditions to obtain a pass. The smoke test explicitly disables identifier conversion for its synthetic identifiers. It does not qualify mapping/database workflows, the complete GUI, full input compatibility, an independent statistical implementation, biological accuracy or the authors' results.

The [checkpoint qualification](idep244-current-development/QUALIFICATION.json) binds the selected source metadata, complete original process records and private capture identities. Public readback checks the saved metadata and consoles. It does not freeze every installed dependency file, rerun DESeq2 or certify a reusable project analysis bundle. A final dependency lock, typed request protocol, complete input/result capture, saved-input replay, failure controls, reference/mapping cases and packaged project-runner checks are still required before admission.

```powershell
python -B tools/literature/idep_current_records.py
```

Use `--private-base` with the original permitted evidence directory to check all indexed private source/return records as well. Private readback excludes installed `R-lib` trees; their verification scopes are stated in the setup receipts above. The current checkpoint adds zero iDEP or native-method credit. The full 250-paper semantic-review and implementation goal remains open.
