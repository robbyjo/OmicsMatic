# Warnings and required actions

**No result is usable solely because a table exists.** Require process exit 0,
`COMPLETED.json` and `analysis.json` with `result_admitted=true`. Read each run's
`WARNINGS.md`. Preserve captured originals, failures and the tested executable.

**SKIP3 — dependent analysis skipped.** A required format, capability or
permitted file access is unavailable. Supply the specific supported complete
FCS/Gating-ML export or permitted accessible path, then retry new output. A
proprietary workspace is not converted by guessing. Optional upstream reference
comparisons need the named permitted packages; if installation/retrieval is
unavailable, skip the dependent comparison and retain the warning.

**FAIL2 — no complete result admitted.** Preserve the complete source and
partial output. Read `FAILURE.txt` and correct its specific definition,
numerical domain, metadata, integrity or whole-population capacity problem.
Retry new output. Do not drop events, clip negative signals, replace nonfinite
values, normalize identifiers, repair covariance or modify the original gates
to force admission. The failing source event and evaluated gate are reported
where available. A zero parent population has an undefined percentage, written
as an empty cell rather than an invented zero.

**Formatting and amplifier policies are scientific provenance.** Strict
numeric/TEXT decoding is the default in the API. The command requires explicit
choices. ASCII numeric-space padding and trailing TEXT padding are separate
options; original bytes/values remain unchanged and the choice is recorded.
Legacy zero-log-minimum-as-one is an explicit interpretation, not a recovered
paper setting. There is no automatic time-in-seconds or calibration step.

**Compensation is an explicit model.** Multiple or legacy original-file
descriptions remain unresolved unless a supported description is selected.
Any dependent `FCS` gate then causes SKIP3. Singular square spectra cause FAIL2.
Rectangular OLS pseudoinverse is not NNLS or a proprietary unmixing algorithm;
rank deficiency means individual signals are not identifiable and is warned.
An explicitly inverted matrix does not establish the rank of its original
spectrum. Never silently transpose, normalize or substitute a matrix.

**Full standard-suite and vendor equivalence are unqualified.** The original
ISAC archive has an invalid covariance definition, five transformed-polygon
bitmap contradictions, a missing/orphan filename pair and a separate summary
ID typo. FlowUtils has three retained inclusive-boundary discrepancies. Read
`README.md`, `REFERENCE_AUDIT.json` and the original retained artifacts. No
source/expected file is rewritten and no full 80-gate pass is claimed.

**Scope still pending:** FlowJo/FACSDiva workspace import, instrument
acquisition/control, paper-specific gates/units/settings, descriptive intensity
statistics, robust SD, cell-cycle models and plugin workflows. Neither family
gets implementation credit for this shared-method checkpoint. The native
command itself requires no external software install.
