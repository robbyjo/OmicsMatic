# Monocle3 1.3.3 prepared-graph development controls

See [the workflow guide](../MONOCLE133_GRAPH_DEVELOPMENT.md) for complete input
formats, explicit root selection, runtime requirements, capacities, skipped
analysis, failures, and recovery.

`VALIDATION.json` identifies the exact source, candidate artifacts, complete
synthetic control populations, original runtime calls, saved-model checks,
native comparisons, prerequisite skips and scientific/capacity refusals.
`controls.zip` retains complete saved original graph controls and their inputs,
models, RNG, root declarations and diagnostics. These are software controls,
not paper embeddings or recovered biological roots. Checksums are integrity
records, not producer signatures. No new whole-tool or catalog credit is
granted by this component; the full 250-paper goal remains open.

The archive stores 652 unique byte objects. The verifier restores all 4,039
logical evidence files with their original bytes; shared source and input files
are stored once. The earlier flat preview archive is retained privately.

Portable saved comparison:

```powershell
python tools/literature/monocle133/verify.py `
  --java-bin 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' `
  --out build/literature-review/monocle-runtime/new-saved-graph-check
```

This repeats no original fitting and reloads no RDS. Run original analysis only
with the separately installed qualified Linux R/Monocle runtime and complete
prepared inputs. Preserve failed/skipped attempts and read their warnings.
