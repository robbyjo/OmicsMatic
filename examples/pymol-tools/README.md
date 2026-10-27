# Original PyMOL workflows

These development examples require an admitted **Linux Ubuntu 24.04 x86_64** private open-source PyMOL 3.1.0 runtime and Linux Java. On Windows, run Java inside WSL. Read the [setup, schema and limitations](../../docs/literature/omics-2023-2026/PYMOL_WORKFLOWS.md) first.

```sh
java -jar build/cli/jlinalg-<version>.jar pymol-tools \
  --home /absolute/admitted/pymol-home \
  --recipe examples/pymol-tools/measure-render.json --out build/pymol-measure-001
```

- `measure-render.json`: explicit C-alpha distance and CPU ray-traced cartoon.
- `edit-rotamer.json`: original backbone-independent GLN conformation 1, a declared maximum backbone shift, and a mutant view. This is a conformation edit, not a stability calculation.
- `align.json`: two copies of the same structure exercise original `align`. Supply an independently reviewed target for a scientific comparison. `super` is a distinct operation with different defaults and correspondences; do not merely relabel an `align` result.

`1CRN.pdb` is the unmodified [RCSB coordinate file](https://files.rcsb.org/download/1CRN.pdb), SHA-256 `42199a30a0701864a2a5cc76cd7f35cc544cd0e65fbcf63e03c166543249b811`. PDB archive data are provided under [wwPDB's CC0 usage policy](https://www.wwpdb.org/about/usage-policies). The examples are diagnostic fixtures and carry no new biological claim.

**Required review:** `WARNINGS.md`, `session.json`, `original-results.json`, `native-audit.json`, figures and final atom/bond ledgers. Exit 3 skips unavailable/changed/forbidden dependencies; exit 2 fails input, process or audit checks and quarantines partials. Existing output directories are preserved.

**Export limitation:** original mmCIF output rounds coordinates to 0.001 Angstrom, rounds B factors/occupancy to 0.01 and omits bond categories. Keep `final-*.json` as the authoritative full-precision edited graph. APBS is not provided by these recipes.
