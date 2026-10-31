# Original RepeatMasker synthetic example

Follow the [public workflow guide](../../docs/literature/omics-2023-2026/REPEATMASKER_PUBLIC_WORKFLOW.md)
for the complete preparation, admission, search and verification commands.
Use a JAR built from current source and an explicitly admitted Linux runtime.
Validation of the complete historical profile is still in progress.

`prepare_inputs.py` extracts checked synthetic fixtures from that JAR and writes
a complete recipe and options file in a NEW directory. It does not install
software or run an analysis. Optional `--map-query-ids --gzip-query` exercises
explicit UTF-8 header/case mapping and compressed input. Read every warning and
require successful completed-bundle verification before using any result.
