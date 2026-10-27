# Original PyMOL operations with native audits

`pymol-tools` is a development adapter for a pinned local build of **open-source PyMOL 3.1.0**, with typed recipes and independent Java audits. It runs rendering, point measurements, original `align`/`super`, explicit bond changes, torsion changes and explicit backbone-independent library mutations. The [paper source review](PYMOL_SOURCE_REVIEW.md) distinguishes the actual uses, historical versions and unresolved details. This adapter does not reproduce every reviewed role: Interactive manual modeling and paper-specific reconstruction choices still need separate work. A separately validated typed [PDB2PQR/APBS/potential-surface workflow](POTENTIAL_SURFACE_WORKFLOWS.md) is now available in development candidate b1c26fa4;the earlier30-test general PyMOL evidence below retains its own artifact identity.

**Dependency and installation boundary:** use Linux Java and CPython 3.12 in Ubuntu 24.04 x86_64. On Windows, run inside an allowed WSL environment. Native Windows execution skips this dependency. Analysis never installs software. If installing or using the dependency is forbidden, or an exact admitted build is unavailable, skip the analysis and retain its diagnostics.

The [runtime lock](../../../tools/literature/pymol-runtime-lock.json) pins the unmodified original source commit, required MMTF headers, five wheel hashes and upstream license notices. The PyMOL wheel is a **local validated build**, not a public upstream binary. It is not distributed in the repository. Supply the exact wheel from the permitted validated cache; do not substitute an unknown PyPI or commercial build. A newly rebuilt wheel needs its own reviewed profile and scientific validation. The current workstation retains the wheel under `build/literature-review/pymol-runtime/admitted-cache/`.

Where installation is allowed, run this from Linux in the checkout, choosing fresh paths:

```sh
python3 tools/literature/install_pymol.py \
  --home "$PWD/build/private-pymol-home" \
  --cache "$PWD/build/pymol-cache" \
  --report "$PWD/build/pymol-setup-001" \
  --reference-wheels "$PWD/build/literature-review/pymol-runtime/admitted-cache" \
  --allow-install
```

Add `--allow-download` only if downloading missing pinned public dependency wheels is permitted. The local PyMOL wheel cannot be downloaded by this installer. The installer does not install operating-system packages or modify global Python. Required original shared libraries must already be available in the allowed environment. For a missing system dependency, ask the environment administrator to provide it or use an allowed private Ubuntu environment; otherwise keep the analysis skipped. Both installer and CLI emit prominent warnings and recovery actions. Existing verified homes can be reused without install/download flags; changed or unmanaged homes are preserved and rejected, never silently repaired or resealed.

```sh
java -jar build/cli/jlinalg-<version>.jar pymol-tools \
  --home "$PWD/build/private-pymol-home" \
  --recipe examples/pymol-tools/measure-render.json \
  --out build/pymol-analysis-001
```

`JLINALG_PYMOL_HOME` may supply the home. Options include `--timeout 600`, `--threads 1`, `--max-atoms 25000` and `--max-bytes 67108864`. The atom bound covers the complete combined sources across **all** states before upstream loading, not just the chosen state. At most eight structures, 32 steps, 100,000 source atoms and one million atom-ledger entries are supported. Split larger jobs into explicitly reviewed smaller workflows. Paths inside recipes are relative to the recipe; use Linux paths when running in WSL.

## Recipe and coordinate contract

A UTF-8 JSON object has `schema_version: 1`, `structures` and `steps`. Every structure requires `id`, `path`, `format` (`pdb` or `cif`), `state` and `cif_namespace` (`author` or `label`; PDB requires `author`). State means the **one-based input ordinal**: PDB MODEL serials 5 and 9 are states 1 and 2. The original full source is hashed and staged; the chosen state is copied into a normal single-state object. A native parser independently checks atom identity, serial IDs, coordinates, B factors and occupancy. Alternate conformers remain present. Biological assemblies, symmetry mates and coordinate units are not inferred.

Selections require `structure`, plus optional exact `chain`, `segi`, `resi`, `resn`, `name`, `alt` and `element` values. Each field is a string or an explicit list; fields combine with AND, list entries with OR. For example `{"structure":"model","chain":"A","resi":["1","2A"],"name":"CA","alt":""}`. Element symbols are matched without case, for example C, Cl, Br, Mg and Se. Atom names, chains, residue identifiers and all other fields remain exact. There are no raw selection expressions, user Python, PML, saved sessions or command execution. Measurement/edit atom selectors must each resolve to exactly one atom, so ambiguous alternate conformers fail. Coordinates are Angstrom and angles are degrees.

PDB input requires explicit element fields and decimal atom serials. Multiple/mixed coordinate blocks, duplicate chemical identities, incomplete CIF loops and missing identifiers in the declared namespace fail with a named action. CIF author atom/residue identifiers fall back to label identifiers only if the preferred **column is absent**; missing preferred cells are rejected. A missing segment label becomes an empty segment. These restrictions are deliberate audited input boundaries, not a claim that upstream PyMOL rejects all other files.

## Typed operations

| Operation | Required fields and behavior |
| --- | --- |
| `measure` | `kind: distance\|angle\|dihedral`, `atoms` (2/3/4 exact-one selectors). Original values are checked against native geometry; undefined native geometry fails. |
| `align` / `super` | `mobile`, `target` on distinct objects. Optional `cutoff`, `cycles`, `gap`, `extend`, `max_gap`, `max_skip`; effective original parameters, the seven-value original summary and post-rejection inlier correspondences are retained. `align` defaults gap/extend to -10/-0.5; `super` to -1.5/-0.7 and its original structural defaults. Both default to cutoff 2 and five refinement cycles. Native checks verify inlier RMSD and one proper rigid motion of the entire mobile object. These are distinct original methods, not aliases for a fixed-pair fit. |
| `bond` / `unbond` | Two exact-one `atoms` in one object. `bond` has order 1–3 (default 1). Adding an existing edge or removing an absent edge fails. Native checks require exactly that graph change and unchanged coordinates. No geometry optimization or chemical validity is inferred. |
| `set_dihedral` | Four distinct `atoms` in one object and `degrees` in [-180,180]. Native before/after torsions and all bond-length changes are checked. |
| `mutate` | Whole `residue` selector, canonical three-letter `to`, `library: backbone-independent`, explicit `rotamer`, `hydrogens: none\|auto`, and `max_backbone_shift` in [0,1] Angstrom. One explicit conformation is applied; no automatic best-packing or energy minimization claim. ALA/GLY use original canonical fragments without side-chain torsion libraries. Covalently linked side chains require an explicit preceding unbond step. Native checks verify canonical heavy atoms, unaffected residues, outside bonds, backbone links and the declared maximum shift. |
| `render` | Explicit `layers` (selection, cartoon/sticks/surface/spheres/lines/ribbon representation and RGB color), `focus`, Angstrom `buffer`, `width`, `height` and RGB `background`; optional original 18-number `view`. Runs CPU ray tracing, retains the actual view and checks PNG dimensions/nonblank pixels. Inspect each figure for clipping, camera choice, color semantics and the intended biological scope. |

The [runnable examples](../../../examples/pymol-tools/README.md) use CC0 1CRN coordinates. A low RMSD, selected rotamer or attractive image does not establish functional equivalence or variant pathogenicity. B factors are not automatically interpreted as pLDDT. The original mutation wizard may reassign serials and reset B factors/occupancy of the constructed residue; before/after ledgers retain those changes.

## Outputs, warnings and failures

Every successful bundle retains source hashes, the admitted recipe, before/after atom and bond ledgers, original summaries/correspondences, `original-results.json`, `native-audit.json`, `session.json`, `manifest.yaml`, `analysis-status.json`, images and `WARNINGS.md`. The runtime seal covers installed files/links, the base interpreter/stdlib, external shared libraries and upstream notices. Each original child uses an empty HOME, isolated Python, a new empty alternate bytecode-cache prefix and bounded threads/timeout. Runtime identity is checked before and after execution.

**Export limitation:** the [original mmCIF writer](https://github.com/schrodinger/pymol-open-source/blob/v3.1.0/layer3/MoleculeExporter.cpp) rounds coordinates to 0.001 Angstrom and B factors/occupancy to 0.01 and does not write explicit bond categories. Final `*.cif` files undergo an independent coordinate/identity check at that declared precision. **Keep `final-*.json` as the authoritative full-precision edited atom/bond graph. Reloading the mmCIF can infer a different bond graph.**

- **Exit 0:** original execution and native audits completed; review warnings and figures before use.
- **Exit 3:** original dependency is missing, changed, unsupported or unavailable; analysis skipped. Supply an admitted permitted runtime or keep it skipped. The CLI never treats an installation/version probe as a successful analysis.
- **Exit 2:** input, original process, timeout, numerical audit or output failure. Partial scientific files are under `partial/`, with errors and required actions at the bundle root. Do not use partials as successful results. Correct the named issue and rerun to a fresh directory.

This is a development workflow. Candidate packaging, original-runtime runs and native regression evidence must be reported separately from a release. It does not establish parity with every historical or commercial PyMOL edition cited in the papers. APBS requires a separately validated electrostatics solver, charges/protonation choices and grid contract; a colored molecular surface is not an APBS analysis.

## Development validation

The [validation record](pymol-adapter-validation.json) identifies development candidate `pymol-candidate-4` (SHA-256 `09e4be51caf00fa252d8c37685e2cd043015a49875eadf131e96b0d0974985b2`). The focused gate passed 30 tests, strict Java 17 compilation, Javadoc and executable packaging. All 30 standalone tests passed on Java 21 and 25. Fourteen actual Linux packaged workflows and five failure controls checked all 20 canonical mutation targets, multi-letter element symbols and three unchanged public recipes with relative Unicode paths. Native Windows produced six explicit dependency skips and no scientific PyMOL result. Superseded candidates and failed checks remain retained in build evidence.

Reproduce the actual packaged workflow checks in an admitted Linux environment:

```sh
python3 tools/literature/verify_pymol_workflows.py \
  --jar build/literature-review/pymol-candidate-4/jlinalg-0.3.7.jar \
  --home /absolute/admitted/pymol-home --out build/pymol-verification-001
```

This verifier never downloads or installs. Read its top-level and per-case warnings. Installation tests separately verified explicit flags, permitted public downloads, complete corrupt/linked cache rejection, unchanged seals, valid poisoned bytecode isolation and actual owned timeout termination. These are development tests, not a public release or whole-paper result.
