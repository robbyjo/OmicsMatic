# Original APBS solver and native electrostatic grids

The current development candidate `build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar`
has SHA-256 `b1c26fa467769d265115e9d5101f91eaa432da2df906e3a679086f68bfeb49ea`.
It includes `apbs-tools`,Java-only `electrostatic-grid`,typed original
[PDB2PQR preparation](PDB2PQR_WORKFLOWS.md) and actual
[PyMOL potential surfaces](POTENTIAL_SURFACE_WORKFLOWS.md). The canonical a104
executable is unchanged. The earlier solver-only proof remains in
[apbs-adapter-validation.json](apbs-adapter-validation.json);the combined107-test
gate,14 preparation comparisons,6 surface workflows and fullJava25 Unicode chain
are recorded separately in [apbs-workflow-validation.json](apbs-workflow-validation.json).

**Warnings and recovery are required reading.** Every completed analysis writes
`WARNINGS.md`, `analysis-status.json`, `session.json` and a hashed `manifest.yaml`.
Missing, changed, unsupported or forbidden runtime dependencies **skip the solver
analysis with exit3**. Invalid inputs, timeouts, original-process failures or
invalid scientific output return2 and quarantine partials. A successful original
exit code alone does not admit scientific output. Correct the named cause and
rerun to a new directory. Existing results and failed attempts are preserved.

**Required installation action:** on the tested Windows x86_64 host, where allowed:

```powershell
python tools/literature/install_apbs.py --home build/private-apbs-3.5.0 --cache build/apbs-cache --allow-install --allow-download
java -jar build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar apbs-tools --options-file examples/apbs-tools/sphere.json --home build/private-apbs-3.5.0 --out build/new-sphere
```

The installer authenticates the official release zip and all608 original files,
retains the upstream license and runs a real finite-difference smoke calculation.
The extra license notice makes609 authenticated installed files. A matching
existing home can be verified/reused without installation permission. The cache
must contain the exact `APBS-3.5.0.Windows.zip` when downloading is forbidden.
Corrupt archives and changed homes are preserved and rejected. If installation
is forbidden or this exact profile is unavailable, **skip the dependent analysis**.
An administrator can supply an admitted private home. No registration or personal
details are required by this public archive; no global PATH/package changes occur.

The [official APBS3.5.0 release](https://github.com/Electrostatics/apbs/releases/tag/v3.5.0)
Windows executable is **32bit x86**, tested on Windows x86_64. The complete private
release supplies its dependency DLLs; Windows System32/kernel ABI remains a host
boundary. Analysis restricts PATH and uses one CPU thread. Version/help return13
in this original build; this expected informational protocol is never accepted
as scientific success. Solver runs require0 plus valid scientific output. The
downloaded Linux archive and alternate builds are not admitted by this Windows
profile. PDB2PQR is not needed when a suitable explicitly charged PQR is supplied.

`apbs-tools` requires the charge-model provenance,Angstrom lengths,LPBE/NPBE,
boundary (`sdh`,`mdh` or `zero`),three grid counts,lengths and center,solute and
solvent dielectrics,probe radius,surface density,temperature and salt molarity.
It uses `mg-manual`,molecular surface (`mol`),cubic spline charges (`spl2`),
`swin0.3`,total-energy calculation and no forces. Each node count is33,65,97 or129;
lengths are positive and at most1000 Angstrom. These conservative resource limits
apply to this32bit profile. No focusing,finite-element calculation,periodic
boundary or arbitrary APBS script is exposed. Positive salt requires an explicit
ion radius and adds equal-concentration +1/-1 species; zero salt forbids the
radius option. Grid coverage includes every atom's radius,probe and one cell of
margin. Inspect the original convergence logs and perform domain/refinement
sensitivity checks for the intended use; finite output is not biological validation.

PQR coordinates/radii are Angstrom and charges are elementary-charge units.
The native reader preserves supplied atom order,chemical identities and charges,
rejects duplicate identities/nonfinite values/multiple models and retains valid
zero-radius atoms. It does not infer elements,force field,pH,protonation,repair,
alternate conformers or a charge preparation. Choose and prepare these upstream
explicitly. In the real1CRN PARSE fixture,236 atoms have zero radii,including
charged hydrogens; they must not be discarded or assigned replacement radii.

`potential.dx` is audited as one complete regular scalar field,withz varying
fastest. Node counts,origin and deltas must agree with the request within original
DX serialization precision. Every scalar must be finite. APBS3.5.0's all-atomic-
charges-zero control exits0 but writes250,047 nonfinite interior values; the
adapter returns2 and quarantines the map and original logs. This is distinct
from a neutral molecule with nonzero atomic charges: the net-neutral1CRN fixture
runs successfully. Separately supplied finite zero/constant grids remain valid
inputs to the native sampler.

Potential units are kT/e at the declared temperature. The native conversion to
volts uses exact SI2019 k_B/e; it does not claim historical APBS constant parity.
The raw reported total energy includes grid self-energy and **is not solvation,
binding or interaction free energy**. A physically matched reference and a
separately validated subtraction protocol are required for such an interpretation.
For the single radius2 Angstrom charge,solute dielectric2/solvent80,no salt,
the independent Born prediction is-169.327588889 kJ/mol:

| Nodes per axis | Matched solvent-minus-reference energy,kJ/mol | Relative Born error |
|---|---:|---:|
|33|-176.353909803|4.1495%|
|65|-170.380805622|0.6220%|
|129|-169.970824515|0.3799%|

This supports the tested refinement trend at the stated finite domain; it is not
a proof that an arbitrary protein calculation is converged. Constants use exact
SI elementary charge/Avogadro values and
[CODATA2022 vacuum permittivity](https://physics.nist.gov/cgi-bin/cuu/Value?ep0).

The native sampler needs only Java:

```powershell
java -jar build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar electrostatic-grid --input build/new-sphere/potential.dx --length-units angstrom --value-units kt-per-e --temperature 298.15 --points examples/apbs-tools/points.tsv --outside missing --out build/new-sphere-samples
```

Declare `angstrom` or `nanometer`; nanometers multiply **both** grid geometry and
point coordinates by10. Scalar values are unchanged. Declare `arbitrary` or
`kt-per-e`; the latter requires an explicit positive temperature in kelvin.
Point TSV header is exactly `point_id`, `x`, `y`, `z` separated by tabs. IDs must
be unique. Trilinear sampling supports general nonsingular affine deltas and
negative axes. Closed boundaries admit at most32 ulps of reported index roundoff.
There is no extrapolation. Choose `error` or `missing` explicitly for outside
points; missing rows keep `inside=false` and blank value/volts. Zero is a valid
sample. Float-tagged arrays are rounded to binary32 before promotion; double
arrays are parsed as binary64. Node statistics are arithmetic node summaries,
not volume integrals or energies. Byte,node and point bounds are explicit.

An ASCII-named UTF8 JSON object of string options supports Unicode Windows paths;
direct flags override recipe fields. Relative paths resolve from the current
working directory. The unchanged public examples were tested as recipes.

The earlier solver candidate89bae745 validation comprises48 focused tests/5suites,strict Java17 compilation,Javadoc,
executable packaging and48 standalone tests on each of Java21 and Java25.
Ten actual packaged solver cases match all6,013,002 nodes and raw energies from
independent original invocations. Charge reversal is exact at274,625 nodes;
double-charge maps/energy obey linear/quadratic scaling within original decimal
rounding. Five solver failure/skip controls and three native packaged cases
exercise output rejection,missing runtimes,bounds and sampling. Twelve actual
installer/runtime controls cover permissions,allowed public download,reuse,
corrupt cache,changed/unlisted/linked homes,hostile environment and owned timeout
termination. Two additional unchanged public solver recipes passed on Java21/25, and a Java25 native recipe passed with Unicode source/output paths. All2422 prior observation objects and585 prior reviews are preserved. The catalog history fix passed two regression tests;the source extractor passed37 tests. No mocks establish scientific correctness. See
[apbs-adapter-validation.json](apbs-adapter-validation.json) for exact evidence.

Source roles and their unstated settings remain explicit in
[APBS_SOURCE_REVIEW.md](APBS_SOURCE_REVIEW.md). See
[examples](../../../examples/apbs-tools/README.md) and command `--help` for inputs.
