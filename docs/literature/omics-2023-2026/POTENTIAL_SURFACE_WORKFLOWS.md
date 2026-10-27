# Actual map-colored molecular surfaces

`potential-surface` runs original Open-Source PyMOL3.1.0 to render a supplied
PQR and scalar DX field. Native Java checks every loaded atom/coordinate/charge,
radius policy,every float32 field value/order,coordinate frame,ramp and actual
settings. It creates an opaque original `surface.png` plus a standalone
`figure.svg` with that unchanged PNG embedded and a physical-unit color legend.

Use development candidate
`build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar`,SHA-256
`b1c26fa467769d265115e9d5101f91eaa432da2df906e3a679086f68bfeb49ea`.
The canonical a104 executable is unchanged. The earlier solver and general
PyMOL validation records retain their original artifact identities and scope.

**DEPENDENCY SKIPS AND FAILURES ARE PROMINENT.** Missing,changed,unsupported or
forbidden runtimes return3 and skip rendering. Input,timeout,original-process or
native-audit failures return2. Scientific partials are quarantined; failed/skipped
top-level figures are absent. Read `WARNINGS.md`,`analysis-status.json`,
`session.json`,`original-surface.json`,`native-audit.json` and original logs.
Correct the named cause and rerun to a new directory. Analysis never installs.

**Required runtime action:** use the admitted Ubuntu24.04 x86_64/CPython3.12
private PyMOL runtime. From Windows,run Linux Java inside WSL. The installer
requires the exact locally built validated PyMOL wheel,its dependency cache and
system GL/PNG/XML/NetCDF libraries. Where permitted,run:

```sh
python3 tools/literature/install_pymol.py --home NEW_PRIVATE_HOME --cache CACHE --report NEW_REPORT --reference-wheels VERIFIED_WHEELHOUSE --allow-install
```

Add `--allow-download` only for permitted missing public dependency wheels.
The installer does not fetch/substitute an unknown or commercial PyMOL build or
install OS packages. See [the runtime guide](PYMOL_WORKFLOWS.md) for the wheel
provenance and required libraries. If installation/dependencies are unavailable
or forbidden,**skip rendering** until an admitted runtime is supplied. Changing
sealed package,base-Python or linked-library files invalidates admission.

For individual commands,all JSON path values must be meaningful on the executing
host. Run from the repository root; direct flags override JSON strings. For
example,in the admitted Linux/WSL environment:

```sh
/usr/bin/java -jar build/literature-review/apbs-workflow-candidate-3/jlinalg-0.3.7.jar potential-surface --options-file examples/apbs-tools/surface.json --home /absolute/admitted-pymol-home --out build/new-surface
```

`surface.json` expects `build/apbs-preparation/prepared.pqr` and
`build/apbs-protein/potential.dx`. Use [the complete example runner](../../../examples/apbs-tools/README.md)
to prepare,solve and render with explicit Windows/WSL path translation.

**THE SURFACE DEFINITION MUST BE CHOSEN.** PyMOL's PQR loader preserves charges
but assigns its own display radii. `surface-radii=source-pqr` explicitly applies
the supplied force-field radii,including valid zeros. `pymol` deliberately keeps
the original display radii and warns that they can differ from solver radii.
Neither policy is silently substituted. PQR lacks an element/bond graph; original
PyMOL display inference is recorded,not independently certified as chemistry.

Choose excluded or accessible solvent surface,probe radius,quality and sampling
location. `surface` evaluates surface vertices; `probe-offset` evaluates one probe
radius along the original surface normal. These are separate scientific choices.
The complete grid must cover every displayed atom/radius/probe/offset in the
same coordinate frame. No alignment,charge preparation or PB solve is inferred.
This bounded renderer accepts positive axis-aligned DX deltas. The separate
Java `electrostatic-grid` sampler also supports general affine grids.

Declare Angstrom coordinates,`kt-per-e`,`volt` or `arbitrary` values,and an
explicit positive temperature only for kT/e. No values are converted implicitly.
An explicit volts-conversion control uses SI k_B*T/e. Choose three increasing
ramp levels,three RGB colors,background,dimensions,framing buffer and optionally
a proper18-number camera view. Levels must remain increasing in float32.
The legend retains an asymmetric middle position and labels units/temperature.
Lighting affects apparent RGB. Image content/framing checks are QC heuristics,
not biological validation; review the actual figure.

**DX PARSER AND PRECISION:** original3.1.0 uses a20-character ASCII numeric
token buffer. Long mantissas can lose their exponent without an upstream error.
An actual long-decimal volts map exposed84,565 incorrect nodes in an earlier
retained failed attempt. The adapter retains untouched raw `potential.dx` and
writes `render-grid.dx` using shortest round-trip float32 tokens that fit the
original parser. Every loaded value still must exactly match the float32-rounded
raw source via a full little-endian field hash. No audit tolerance was loosened.
Original float32 underflow/rounding is measured and reported; keep both maps.
Spacing that collapses to zero and values that overflow float32 are rejected.

Six actual packaged Linux workflows cover both radius policies,accessible and
excluded surfaces,probe offset,custom RGB/background,explicit volts input with
an asymmetric ramp,finite-zero arbitrary data,Unicode paths and explicit camera.
They audit1,647,750 complete field nodes and11,556 atom phases. Seven actual
failure/skip controls include missing runtime,frame mismatch,empty clipping,
late NaN,float32 overflow/collapsed ramp and owned timeout termination. Three
native Windows surface attempts skip3 with Linux recovery; the Windows-only
preparation runtime likewise skips on Linux. The combined107 tests pass with
strict Java17 compilation,Javadoc,packaging and standalone Java21/25 execution.
An earlier independent original render and typed adapter matched all1,020,000
RGBA body pixels in their common1200x850 region; this is a controlled example,
not general rendering equivalence. Exact evidence and retained failure history:
[apbs-workflow-validation.json](apbs-workflow-validation.json).

**Interpretation:** a color map visualizes the supplied model. It does not
establish a protonation state,pKa,charge validity,convergence,biological effect or
experimental agreement. Raw APBS total energy includes self-energy and is not
solvation or binding free energy. Source papers P013/P026 leave their preparation
and solver parameters unstated; this workflow does not reconstruct them.
