# Saved limma workflow development handover

This packet preserves saved receipts and helper sources for the
[current handover](../HANDOVER_2026-11-21_LIMMA_WORKFLOWS.md).
It adds no catalog credit and does not qualify the complete live normal-JAR
workflow. CHECKS.json pins current source and every other packet file.

Run `python -B tools/literature/verify_limma_workflow_handover.py` from the
repository root. The reader verifies saved bytes only, without R or a new fit.
The ordinary Gradle focused test command is
`gradlew.bat :test --tests org.jlinalg.cli.LimmaWorkflowTest`.
The development class can be inspected using
`java -cp PATH_TO_NORMAL_JAR org.jlinalg.cli.LimmaWorkflowCli --help`.
There is no global limma-tools registration yet.

The first normal build's two failed harness assertions remain preserved;
the corrected build passes all 69 focused tests. The synthetic fixture ZIP
contains 45 saved original-runtime analyses. Independent stock code matched
every compared complete original return exactly; totals are in CHECKS.json.
Full independent reference RDS files and failed/private run directories remain
local and require separate checksummed transfer. A GitHub clone is insufficient
for a complete machine handover.

Helpers are exact historical source snapshots with fixed Windows paths and
exclusive output directories. Inspect dependencies and create new attempt names
before reuse. Do not execute them blindly or refresh older source hashes.
The current packet-builder snapshot records its invocation before these packet
files existed; it is provenance rather than an idempotent installer.
