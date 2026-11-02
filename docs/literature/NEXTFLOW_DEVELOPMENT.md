# Nextflow development checkpoint

Nextflow is still **missing** in the public tool catalog and is excluded from
the 63 newly implemented tool integrations. This checkpoint contains native
runtime admission, parameter binding, process supervision, and completion/trace
audits. The workflow capture and typed recipe classes are development code.

The three managed Linux x86_64 engine profiles are exact Nextflow 21.04.1,
22.10.6, and 24.04.4. Runtime setup requires separate, explicit installation and
download authorization. The setup installer is `tools/literature/install_nextflow.py`;
its successful synthetic health check does not admit a user analysis.

Retained validation is in `nextflow-trace-foundation/VALIDATION.json`,
`nextflow-runtime-development/VALIDATION.json`, and
`nextflow-native-admission/VALIDATION.json`. These checkpoints retain original
engine controls, recovered retries, ignored failures, and exact source hashes.
Read each report's scope and platform conditions before using its counts.

**User workflow execution remains unfinished.** Public command routing, complete
declared-output admission, stable-session resume, and portable analysis bundle
verification must pass before Nextflow can be promoted. Original paper pipeline
dependencies and settings require separate admission.

**Required recovery:** if a runtime or dependency is unavailable or installation
is forbidden, skip the dependent analysis and report SKIP3 with the missing
dependency and setup action. Invalid input, changed evidence, process or capacity
failure must produce FAIL2. Preserve failed attempts and use new output paths.
An engine exit of zero, a successful setup, or a partial output family is
insufficient to claim a completed analysis.
