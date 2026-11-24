# Genomic SEM foundation checkpoint

Original-source runtime foundation only: **no Java adapter, new catalog credit,
historical-edition equivalence or paper reproduction**. The catalog remains 159
scoped families (30 native / 129 upstream), 1,760 reviewed uses and 964 pending.
See [the guide](../GENOMICSEM_SOURCE_RUNTIME.md) for commands, exact source roles,
compatibility constraints, warnings and remaining integration work.

`QUALIFICATION.json`, `REFUSALS.json` and `TESTS.json` contain selected aggregate
facts from completed local runs. Full logs, synthetic RDS results, failed
attempts, installation/runtime bindings and preservation/cleanup receipts stay
local. Counts alone are not an independent statistical reanalysis.

```powershell
python tools/literature/verify_genomicsem_foundation.py
```

Optionally add `--cache` for a fresh 250-XML/DOI audit, `--runtime` to check the
staged private source/library files, and `--qualification` to check the original
saved synthetic result closure. This reader never refits the models. The
separate qualifier performs fresh upstream fits. Hashes establish integrity,
not authenticity or host attestation. Optional runtime paths refer to the
original Windows installation and are checked from Windows.

Historical checkpoints keep their original source hashes. Run their unmodified
readers from the pinned Git revisions or retained physical source contexts;
this checkpoint does not refresh old hashes after .gitattributes changes.

Boundary test commands (test fixtures are retained for explicit cleanup):

```powershell
$env:PYTHONPATH=(Resolve-Path tools/literature).Path
$env:OMICS_GENOMICSEM_TEST_OUT='D:\omics-checks\genomicsem-boundaries-1'
python -B -m unittest genomicsem.test_runtime -v
```
