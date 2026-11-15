# Original iDEP 2.4.4 count-workflow development

**Development only. This workflow adds no catalog credit.** Full catalog
integration remains subject to the separate source-baseline review described in
[the main merge notice](MAIN_MERGE_20261115_STATUS.md). Independent qualification
and broader controls remain in progress. A recorded original return establishes
neither biological accuracy, complete iDEP GUI coverage nor paper reproduction.

The user selected the newest iDEP after the legacy-source assessment. This
adapter calls unchanged **iDEP 2.4.4**, commit
`60479c4375ea07bc930dc40b3c85230856f4ad50`, with Windows R 4.6.1 and DESeq2
1.52.0. The [selected upstream source](https://github.com/gexijin/idepGolem/tree/60479c4375ea07bc930dc40b3c85230856f4ad50)
is newer than published release v2.4.0. Neither identifies the unreported
deployment behind P050's literal **iDEP.96**; that literal and uncertain author
defaults remain unchanged in the catalog.

The selected upstream README gives noncommercial terms; its DESCRIPTION license
field is a placeholder. Source and installed binaries remain separate private
prerequisites. This adapter redistributes neither. See the preserved assessment
in [IDEP_CURRENT_DEVELOPMENT.md](IDEP_CURRENT_DEVELOPMENT.md). Academic,
educational/nonprofit use has already been selected for this project.

## Prepare an existing permitted runtime

There is no public automatic installer for this development workflow yet.
Use a permitted Windows R 4.6.1 installation, the complete exact original source,
and installed iDEP 2.4.4 / DESeq2 1.52.0 with their dependencies. BiocManager and
jsonlite are also required; the original method uses BiocManager while
constructing script text. List intended library directories in priority order,
one absolute path per line, in `libraries.txt`.

From the project root, choose NEW canonical output paths:

~~~powershell
python -B tools/literature/idep244/adopt_runtime.py `
  --rscript "C:/Program Files/R/R-4.6.1/bin/Rscript.exe" `
  --libraries-file "C:/analysis/libraries.txt" `
  --source "C:/analysis/original-idep244-source" `
  --out "C:/analysis/NEW-idep-provider"
~~~

The audit installs/downloads nothing. It checks all 336 source members and
compares all 259 active original function names with their 260 source
definitions, retaining an original duplicate definition. It fingerprints the
supplied R/library trees before and after inspection, then locks complete
selected Depends/Imports package folders, loaded namespaces and R executable,
configuration and module folders. LinkingTo-only build headers do not add runtime
packages independently. Other dependency versions are recorded; they are not
recovered author versions or independently authenticated binaries. New providers
are audited records, not automatic qualification of every dependency combination.
Provider `COMPLETED.json` admits a runtime audit, **not expression analysis**.

If a required exact source, software, installation or download cannot be supplied
where permitted, skip the dependent analysis. Supply the named permitted
prerequisite and retry NEW paths; do not silently substitute another release.

## Record original counts, preprocessing and DESeq2

Copy [the complete request](../../examples/idep244-workflow/request.json), and set
`expression` to your original counts file. Request paths are resolved from the
directory where Python is invoked. The example's filtering, cutoffs, RNG, locale
and limits are explicit **project example choices**, not inferred P050 defaults.
Duplicate/unknown JSON keys and non-JSON constants are refused. There is no
arbitrary R-expression or installer field.

~~~powershell
python -B tools/literature/idep244/run.py `
  --runtime "C:/analysis/NEW-idep-provider" `
  --request "C:/analysis/request.json" `
  --out "C:/analysis/NEW-count-analysis"
~~~

The original stages are `input_data`, optionally `get_idep_data` and `convert_id`,
then `convert_data`, `pre_process` and the GUI's `limma_value` DESeq2 dispatcher.
Inactive FPKM options are explicitly fixed in the counts-only call. Original
identifier cleanup, duplicates, file-format handling and sample-name
transformations are preserved. No data are silently repaired. With null model
factors, the original method infers groups from sample names, such as
`Control_1`, `Control_2`, `Case_1` and `Case_2`.

For an uploaded design, the original GUI orientation is **one row per factor and
one column per sample**, with factor names in the first column. Supply its
path in `design`. Select original parsed names, for example
`"factors": ["Condition"]` and
`"comparisons": ["Condition: Case vs. Control"]`.
Reference levels use `["Condition:Control"]`. Interactions and block factors are
passed unchanged. The original method ignores a design when factors are null.
Original collinear-model and character error returns remain errors.

**This is a headless namespace-function workflow.** In the actual all-zero
control, original code attempted a Shiny notification without a reactive
session and raised "attempt to apply non-function." The error and its full
Shiny stack are preserved as FAIL2. An empty DEG result is not admitted.
Use the permitted original GUI for notification-dependent inputs; a separately
qualified automated GUI/session workflow remains unfinished.

`counts_transform` retains the original 1/log-normalized, 2/VST and 3/rlog
branches. A failing transformation is not replaced. The source's 500-sample
limit and original 500-comparison behavior remain intact. Inspect original
messages and `exp_type`; a returned matrix does not promise all possible
comparisons were calculated. Returned R script text, including embedded
installation suggestions, is preserved and **never executed**.

## Use supplied mapping databases

Replace `mapping` with this object, choosing actual intended values:

~~~json
{
  "mode": "database",
  "multiple_map": "max_sd",
  "reference_dir": "C:/analysis/supplied-idep-reference",
  "organism_id": "244",
  "max_sample_ids": 100
}
~~~

`244` is an illustrative **internal orgInfo row ID**, not an inferred taxonomy
ID. Supply the actual reference ID. The complete directory needs
`demo/orgInfo.db`, `demo/demo_data_info.csv` and the selected `db/FILE` named by
its unique orgInfo row. All supplied files are copied within input/file limits;
links, path escapes and partial captures are refused. Metadata is preflighted
read-only before the original loader can enter its automatic-download branch.
Analysis requests no installation/download. **Networking is not isolated by
this Windows adapter; use trusted inputs and databases.**

Original sampling/RNG consumption, first-match behavior, many-to-one aggregation
and unmatched-ID retention remain intact. `multiple_map` accepts the source's
`mean`, `median`, `sum`, `max` or `max_sd`. A null original mapping return is
retained; original conversion then keeps unconverted IDs, and a prominent
adapter notice states that mapping did not succeed. Reference hashes establish
integrity, not authenticity or the authors' reference snapshot.

## Inspect and replay

Read root `COMPLETED.json` and `WARNINGS.md`, full `worker.stdout` /
`worker.stderr` and `original/R-status.json`. Each complete original stage
return is in `original/stages/`, including failed NULL/character returns.
Successful full values are in `original/complete-original-returns.rds`; failed
envelopes, complete conditions and caller stacks are retained separately.
Classes, attributes, IDs, NA/NaN/Inf and complete RNG states are not flattened
into summaries. Inputs, typed settings, engine code and runtime identities
are captured. An RDS or zero R exit alone is insufficient for admission.

~~~powershell
python -B tools/literature/idep244/verify.py `
  --bundle "C:/analysis/NEW-count-analysis" `
  --out "C:/analysis/NEW-count-replay"
~~~

Replay copies captured inputs into a NEW directory, calls original functions
again and compares every stage return, condition and full RNG state. The saved
bundle remains unchanged. Only the known first timestamp header of
`original_returns$limma_value$expr` may differ. Raw scripts remain intact; all
other script text is compared. Individual stage files must agree with the
whole-return envelope. Caller context and namespace audits are compared. Hashes
alone do not establish computational correctness. Exact engine/provider
versions are required; no reference-authenticity claim is made.

## Limits, failures and required action

The Windows launcher starts suspended, joins an owned job, and resumes only
after assignment. Time/file/recording-size overruns stop its R descendants.
Other processes are not targeted. File/byte limits are monitored and can
overshoot between checks; they are not OS disk quotas. `vector_heap_mib` limits
R's vector heap, not total process/RSS or Windows job memory. Limits never
authorize truncating inputs or scientific results.

The controller uses Windows [suspended process creation](https://learn.microsoft.com/en-us/windows/win32/procthread/process-creation-flags)
and [job objects](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-jobobject_extended_limit_information).
If R reports that a requested heap limit is below current startup usage and
ignores it, the runner refuses analysis. Increase the explicit heap budget and
retry a NEW output path. Complete provenance, the manifest and the final marker
must also fit the file/byte budget; a successful R return does not override it.

In the synthetic controls, the original parametric dispersion fit fell back
to a local fit. Its message is retained in the main warning report. The
adapter does not change fit settings to suppress that message. Review original
diagnostics for each dataset before interpreting the result.

**SKIP3** names an unavailable prerequisite and skips dependent analysis.
Supply that permitted exact prerequisite and retry NEW paths. **FAIL2**
admits no complete result for original errors, crashes, limits or recording
failures, including failures after computation. Keep all inputs and partials;
resolve the named cause in `FAILURE.json`, original status and raw consoles,
then retry NEW paths. Failure after completion promotion invalidates its marker
and preserves the invalidated record. If failure recording is unavailable,
the console says `FAIL2_RECORDING_UNAVAILABLE`: retain that console and choose
a writable output. Never refresh evidence hashes to accept a changed computation.
