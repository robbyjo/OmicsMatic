# Scoped DIA-NN validation

This checkpoint covers the original local Linux DIA-NN 1.8, 1.8.1 and 2.0.2 workflows described in the [command guide](../DIA_NN.md). It adds one upstream integration to the 250-paper catalog. It does not reproduce an entire software package, source paper or author cohort.

The controls use two complete distinct Bruker TDF runs from PXD065380, a limited 1,704-record UniProt reference, fresh original prediction/raw fitting and original MBR. The 1.8 control explicitly uses Robust LC. Both Windows and Linux passed 74 focused JUnit tests with no skips, three full saved readbacks and 18 corruption/refusal checks each. See [QUALIFICATION.json](QUALIFICATION.json) for exact scope and the producing JAR hash.

The public checkpoint contains source and hash/count metadata. Original scientific inputs/outputs, runtime binaries, process logs, licensed archives and cleanup receipts stay private. The literature HTML reports remain withheld from Pages. No tag or release is created by this checkpoint. Use the command guide to analyse your own permitted inputs; the commands below are for the retained validation evidence.

## Audit retained evidence

With the exact private evidence available, run from the source checkout:

```shell
python -B tools/literature/verify_diann_workflows.py \
  --root /absolute/checkout \
  --private-root /absolute/checkout/build/diann-882 \
  --source-context /absolute/checkout/build/diann-882/diann-final-context-1/source
```

For a historical checkpoint after later catalog changes, use its archived entrypoint and declarations:

```shell
python -B /absolute/checkpoint/source/tools/literature/verify_diann_workflows.py \
  --root /absolute/checkpoint/source \
  --source-context /absolute/checkpoint/source \
  --artifact-root /absolute/current-checkout \
  --private-root /absolute/retained-diann-evidence
```

**Warnings and required actions:** SKIP3 means this evidence audit is skipped because named files or access are unavailable; supply the exact permitted evidence or retain the skip. FAIL2 admits no successful audit after source, producer, namespace, content or scientific-consistency drift. Preserve the attempt, restore the exact files and run a new audit. Do not refresh old expected hashes to match changed files. Saved capture verification separately requires each capture's exact producing JAR and does not refit the original model.

Original fragmentless-candidate omissions and limited-run QuantUMS optimisation recommendations remain retained. Other formats/modes, historical author settings, PAV/empirical-library construction, PTM localisation, private cloud operations and downstream models require separate qualification. The full 250-paper goal remains in progress; MaxQuant runtime is skipped while its tools remain pending, and Cell Ranger is excluded.
