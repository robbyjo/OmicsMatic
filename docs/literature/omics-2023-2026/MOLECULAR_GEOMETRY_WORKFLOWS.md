# Native molecular geometry

`molecular-geometry` measures distances,angles and signed torsions,or fits an
unweighted proper rotation and translation to supplied coordinate pairs. It runs
in Java and needs no PyMOL installation. Atom/sequence matching,PyMOL align/super,
outlier rejection,scale,reflection,periodic images,force fields and biological
interpretation remain separate operations.

Use the validated development executable
`build/literature-review/molecular-geometry-candidate-1/jlinalg-0.3.7.jar`, SHA256
`798d3a08b64107eb7a7679359902efae3591f6ae5a89075f8e9a428698cc32b3`.
The canonical a104 executable is unchanged. No new full-project gate or release
is claimed. The public PyMOL integration remains pending; these native methods
do not make its rendering,alignment,editing or APBS workflows available.

```
java -jar build/literature-review/molecular-geometry-candidate-1/jlinalg-0.3.7.jar molecular-geometry --operation measure --input MEASUREMENTS.tsv --units angstrom --out NEW_DIRECTORY
java -jar build/literature-review/molecular-geometry-candidate-1/jlinalg-0.3.7.jar molecular-geometry --operation fit --input PAIRS.tsv --units angstrom --degeneracy error --out NEW_DIRECTORY
```

`--units angstrom|nanometer` is required;units are never inferred. Nanometer
coordinates multiply by10. All output coordinates/distances are Angstrom,and
angles are degrees. Use a common Cartesian frame for a measurement. The methods
do not expand crystal/biological assemblies or account for periodic boundaries.

For a fit,use this exact header with literal tabs:

```
pair_id	mobile_x	mobile_y	mobile_z	target_x	target_y	target_z
```

Every row is one explicitly matched pair. Unique nonblank IDs and their order are
retained. At least3 and at most1,000,000 pairs are allowed;converted coordinate
absolute values must be at most1e9. `--degeneracy error|allow-nonunique` is required.
A relative largest-eigenvalue gap<=1e-12 makes a rotation nonunique or numerically
unresolved. Explicitly allowing it emits a prominent warning;the returned pose
must not be interpreted as uniquely identified. Planar noncollinear points can
identify a rotation. Collinear or collapsed inputs cannot identify one uniquely.

For measurements,use this exact header with literal tabs:

```
measurement_id	kind	ax	ay	az	bx	by	bz	cx	cy	cz	dx	dy	dz
```

`kind` is distance,angle or dihedral. Distance uses AB,angle uses ABC,and signed
torsion uses ABCD. Unused trailing coordinate fields must be empty. Zero-length
arms or undefined collinear torsions fail;they are not silently reported as0.
The examples contain actual TSV headers and required empty fields.

`--max-rows` defaults to1,000,000 and `--max-bytes` to268435456. Limits bound accepted
input/work,not every JVM allocation. Lower them for constrained hosts. A UTF8JSON
`--options-file` contains known option:string values;direct options override it.
On Windows,use an ASCII recipe filename for Unicode data/output paths.

Successful output includes `results.tsv`, `session.yaml`, `analysis-status.json`
and prominent `WARNINGS.md`. Fits also retain every original/target/fitted pair
and residual in `fit-pairs.tsv`;the session records rotation and translation with
the column-vector convention `target=R*mobile+t`. Residuals use centred coordinates
to reduce cancellation;exported absolute coordinates can differ slightly through
rounding when offsets are very large. This is not an uncertainty estimate.

**Errors require action.** Exit2 retains diagnostic files and moves scientific
partials under `partial/`,with no successful top-level result table. Read the
named cause;correct units,IDs,pairs,geometry or resource settings,and rerun to a
new directory. Existing directories are preserved. Exit0 may still have warnings
requiring review. There is no external dependency to install or skip for this command.

Validation includes18 native and10 command tests on each of Java21/25. The focused
source gate passed28 tests with no failures/errors/skips,strict Java17 compilation,
Javadoc and executable packaging. Native arithmetic matched128 actual original
PyMOL3.1.0 measurement cases and96 explicit-pair fits covering1335 pairs. Sixteen
actual packaged workflows and12 controls on Java21/25 checked1140 numeric values
and90 fit pairs. Original measurements/coordinates use float32 storage;declared
comparison tolerances are retained in the fixture provenance. Bitwise equality,
PyMOL align/super matching/refinement and historical/commercial-version parity
are not claimed. See `native-geometry-validation.json` and the
[synthetic examples](../../../examples/molecular-geometry/README.md).

The proper rigid-fit method follows [Horn1987](https://doi.org/10.1364/JOSAA.4.000629).
PyMOL source/reference evidence is described in [the source review](PYMOL_SOURCE_REVIEW.md).

Reproduce the packaged checks with Python's standard library and Java:

```
python tools/literature/verify_molecular_geometry_workflows.py --jar build/literature-review/molecular-geometry-candidate-1/jlinalg-0.3.7.jar --out NEW_ASCII_REPORT_DIRECTORY
```

Repeat `--java JAVA_EXECUTABLE` to check several JVMs. The verifier checks the
frozen original fixture hashes, then runs the packaged native command. It does
not install or execute PyMOL. Full actual-original collection and private source
build evidence are retained separately.
