# Native Rsubread matrix evidence

The small `rsubread-count-matrix-api.jar` is a Java library with six native
classes and the GPL license. It requires only a JDK; it does not run Rsubread.
Read [the component guide](../RSUBREAD_MATRIX_COMPONENT.md) for API examples,
source controls, prominent warnings and the unfinished workflow boundary.

```sh
python -B tools/literature/rsubread_matrix_evidence.py
```

This verifies the complete archive, source snapshots and packaged API. It does
not rerun R fits or certify biological accuracy. To export exact frozen
reference observations without claiming a current runtime or refit:

```sh
python -B tools/literature/rsubread_matrix_evidence.py --archive-only --export-controls build/literature-review/rsubread-runtime/NEW-CONTROLS
```

The guide provides the JDK-only replay command. `PORTABLE_REPLAY.json` records
the tested export/rebuild/replay path: javac25 reproduced all six classes and
the complete API JAR byte for byte, and both frozen R reference families passed.
No prior project JAR or current R installation was on that replay classpath.

`controls.zip` retains the complete current source/reference/control data and
explicit earlier diagnostics. R/Rtools/unpacker installers and private base
runtimes/toolchains are referenced by their exact download and file hashes;
they are not redistributed here. Earlier failed receipts remain failed.

Rsubread remains missing in the catalog at this checkpoint. This component does
not grant full-tool, runtime-supervision, independent read-assignment, junction
counting, paper-reproduction or whole-goal credit.
