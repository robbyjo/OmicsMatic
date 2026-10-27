# APBS solver and native grid examples

Run from the repository root with the validated development candidate at
`build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar` (SHA-256 begins
`b1c26fa4`). The canonical executable is unchanged. Every output directory must
be new. Use the same commands with Java21 or Java25.

Where installation is permitted, install the original Windows profile explicitly:

```powershell
python tools/literature/install_apbs.py --home build/private-apbs-3.5.0 --cache build/apbs-cache --allow-install --allow-download
java -jar build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar apbs-tools --options-file examples/apbs-tools/sphere.json --home build/private-apbs-3.5.0
java -jar build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar apbs-tools --options-file examples/apbs-tools/protein.json --home build/private-apbs-3.5.0
java -jar build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar electrostatic-grid --input build/apbs-sphere/potential.dx --length-units angstrom --value-units kt-per-e --temperature 298.15 --points examples/apbs-tools/points.tsv --outside missing --out build/apbs-sphere-samples
```

Omit `--allow-download` when the exact original archive is already cached.
APBS3.5.0's Windows executable is32bit x86 and runs on the tested Windows x86_64
host. It needs neither Python nor registration. Analysis never installs software.
Missing, changed or forbidden dependencies skip the solver analysis with exit3.
Input, process, timeout or output-audit failures return2; partial scientific
outputs are retained under `partial/`. Read the prominent `WARNINGS.md` and its
required action before interpretation. Native `electrostatic-grid` needs Java
alone and can inspect a complete finite map on other Java-supported platforms.

The single charge is a synthetic numerical example. Its raw total energy is
about1318.208548 kJ/mol. This includes grid self-energy and is **not solvation
free energy**. A matched reference with solvent dielectric2 gives about1488.589354
kJ/mol. Their difference is about-170.380806 kJ/mol; the analytic Born value is
about-169.327589 kJ/mol. Grid refinement/domain checks remain necessary.

The supplied 1CRN PQR is the unchanged output of a real original PDB2PQR3.8.0
preparation recorded in `provenance.json`. It has642 atoms,net charge0 and236
zero-radius atoms. Charged zero-radius PARSE hydrogens are retained. The flags
disable debumping and hydrogen optimization; no declared-pH/PROPKA titration was
requested. These are explicit fixture choices and do not establish a preferred
biological model. The NPBE example adds symmetric monovalent salt at0.15M.

Typed original PDB2PQR preparation and actual PyMOL map-colored surfaces are now
available in this development candidate;see the full example below.
The example does not reconstruct either source paper's unstated parameters.
An ASCII-named UTF8 JSON options recipe supports Unicode Windows paths; direct
flags override recipe strings. Relative recipe paths are resolved from the
current working directory. See [APBS_WORKFLOWS.md](../../docs/literature/omics-2023-2026/APBS_WORKFLOWS.md).

## Complete fixed 1CRN example

The unchanged local1CRN PDB has the SHA-256 recorded in provenance.json. No PDB
download occurs during analysis. After explicitly admitting all three runtimes,
run from Windows with Linux Java/PyMOL available in the named WSL distribution:

```powershell
python examples/apbs-tools/run-workflow.py --jar build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar --pdb2pqr-home build/private-pdb2pqr-3.8.0 --apbs-home build/private-apbs-3.5.0 --pymol-home /absolute/admitted-pymol-home --wsl-distro YOUR_DISTRO --out build/new-electrostatic-workflow
```

Use --java for an explicit Windows Java executable. JAR and --recipe-root paths
must be ASCII because Windows Java launcher argv can garble Unicode;input/output
paths inside UTF8 JSON recipes may be Unicode. The runner translates Windows
paths into WSL paths and retains the actual recipes and cross-stage SHA chain.
The fixed model choices are in prepare-parse.json,protein.json and surface.json.
The first recipe uses forcefield protonation,not a declared pH. Source-PQR radii,
including zero-radius charged hydrogens,are applied explicitly for the display.

**Read the top-level and every stage WARNINGS.md.** Exit3 skips an unavailable
stage and all dependent analyses;exit2 marks a failure and skips dependent stages.
Completed upstream bundles and failed partials remain. No automatic installation,
model substitution or old-output overwrite occurs. Correct the named issue and
rerun to a new output directory. The unit-labeled surface/figure.svg embeds the
unchanged original surface PNG. Keep the raw potential.dx and short-token
render-grid.dx;the latter prevents original PyMOL's20-character parser from
dropping long numeric exponents. Every loaded float32 value remains audited.

The full public runner passed on Windows Java25,WSL Linux Java21 and Unicode
output paths;prepared-PQR and solver-map hashes agree across every stage. Two
real missing-runtime cases verified downstream skipping. Earlier failed WSL
backslash path translation is retained separately. This controlled example
does not establish a preferred biological model,convergence for other inputs or
the unstated settings of either source paper. See
[preparation](../../docs/literature/omics-2023-2026/PDB2PQR_WORKFLOWS.md),
[surfaces](../../docs/literature/omics-2023-2026/POTENTIAL_SURFACE_WORKFLOWS.md) and
[exact validation](../../docs/literature/omics-2023-2026/apbs-workflow-validation.json).
