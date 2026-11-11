# Rsubread count-matrix component

This **2026-11-09 development checkpoint** adds native Java handling and column
binding for the count matrices returned by Rsubread. It does not provide the
complete Rsubread analysis adapter. The catalog therefore still records
Rsubread as missing; totals remain 89 new scoped integrations and 252 missing
tools. The canonical executable is unchanged.

## Warnings and required actions

**Use explicit row-name policy.** `REQUIRE_IDENTICAL` rejects different row names
or order. `RETAIN_FIRST_POSITIONAL` reproduces R's positional matrix binding:
it keeps the first matrix's row names and reports differing input indices
(zero-based). Neither policy joins, sorts, intersects or renames genes.
Inspect `differingInputs()` before using positional binding.

**Preserve names and units.** Duplicate names, empty names, the literal string
`NA` and an R missing name are distinct observations. Duplicate column names do
not establish biological replication. Do not silently make names unique or
combine read counts and fragment counts as if they had the same units. The
original source controls include a literal annotation ID `NA` that R parses
as missing; the native component retains that R result and does not reconstruct
the lost original text. Keep the raw annotation and complete original R object.

**Bad count data fail.** Counts must be finite and nonnegative. Integer storage
requires representable nonnegative R integers; double storage retains the exact
represented binary64 bits, including fractional counts. The component does not
repair NA/NaN/Inf, round fractional counts, recover precision already lost by R,
or certify original alignment assignments.

**Setup/reference tools use SKIP3 and FAIL2.** If exact software, storage,
downloads or installation are unavailable or forbidden, skip the dependent
reference work. A failed reference batch is not admitted, including earlier
objects. Preserve warnings, original logs, source archives and partial outputs;
correct the named cause and retry NEW paths. Java API errors are exceptions,
not CLI exit codes. These developer reference helpers are not a production
analysis command for user data.

**Version labels do not identify the paper binary.** P029 reports Rsubread
2.23.2 and R 4.4.1. Twenty-two captured upstream commits share 2.23.2. This
checkpoint selects and records one immutable official commit,
`666b63dc50fa5d0e5cd4c9b7e805b6f6aa2f12f6`, and preserves its development
warnings. It builds unchanged with private R 4.4.1 and GCC13 tools. Its failed
R 4.6.1/GCC14 build is retained. Matching the package/R labels does not resolve
the paper commit, compiler, binary, annotation or missing options.

**Fragment filtering has a source dependency.** The original counter rejects
`checkFragLength=TRUE` when `requireBothEndsMapped=FALSE`. A named
`maxFragLength=100` does not enable filtering by itself. P133 leaves the enabling
flag and `countReadPairs` unstated; do not infer them or silently activate a
filter. The controls retain both inactive and explicitly active cases.

Original R references run on Windows. Linux R analysis remains unavailable in
this checkpoint. The Java component itself passes Windows Java 21/25 and Linux
Java 21 and needs no R runtime after matrices have been supplied.

## Java API

```java
import java.util.List;
import org.jlinalg.sequence.RsubreadCountMatrix;
import org.jlinalg.sequence.RsubreadCountMatrix.Name;
import org.jlinalg.sequence.RsubreadCountMatrix.RowNamesPolicy;
import org.jlinalg.sequence.RsubreadCountMatrix.Storage;

var genes = List.of(Name.literal("gene1"), Name.literal("gene2"));
var single = new RsubreadCountMatrix(Storage.INTEGER, genes,
        List.of(Name.literal("single")), new double[] {5, 2});
var paired = new RsubreadCountMatrix(Storage.INTEGER, genes,
        List.of(Name.literal("paired")), new double[] {7, 3});
var joined = RsubreadCountMatrix.columnBind(
        List.of(single, paired), RowNamesPolicy.REQUIRE_IDENTICAL);
double secondGenePaired = joined.matrix().value(1, 1); // 3
```

Constructor arrays are column-major, as in R. The object copies input arrays
and name lists; returned arrays are copies and name lists are immutable.
Different row counts always fail. Integer/double binding promotes to double
when any input has double storage.

`readWire` and `writeWire` provide the reference bridge's lossless ASCII format:
UTF8-hex names, explicit missing names and binary64 bit patterns. This is a
count-matrix bridge, not complete R-object serialization. Complete original R
objects and their attributes remain in the reference evidence.

`readWire` requires explicit `Limits`. Defaults are 1M rows, 10K columns, 25M
cells, 1MiB per UTF8 name and 64MiB total name bytes. Construction and binding
also need sufficient caller heap. The small controls passed under a 128MiB
Java heap; that is not a large-cohort memory benchmark.

## Rebuild and replay

The [small API JAR](rsubread-matrix-development/rsubread-count-matrix-api.jar)
needs only a JDK. It contains the exact six native classes validated in the
larger development candidate. It does not require a prior project JAR or R.

From the repository root, export frozen reference observations and replay them:

```powershell
python -B tools/literature/rsubread_matrix_evidence.py --archive-only --export-controls build/literature-review/rsubread-runtime/NEW-CONTROL-COPIES
python -B tools/literature/validate_rsubread_standalone.py --api docs/literature/rsubread-matrix-development/rsubread-count-matrix-api.jar --controls2260 build/literature-review/rsubread-runtime/NEW-CONTROL-COPIES/2260 --controls2232 build/literature-review/rsubread-runtime/NEW-CONTROL-COPIES/2232-R441 --out build/literature-review/rsubread-runtime/NEW-REPLAY
```

Supply `--java` and `--javac` if a permitted JDK is not on PATH. This replays
frozen observations; it does not rerun R or generate new scientific results.
`--archive-only` verifies archived bytes and relationships without claiming a
current runtime/refit. Without that flag the evidence verifier also checks the
current source files and packaged API JAR.

Rebuild the library directly from Java source with a JDK17+ compiler:

```powershell
python -B tools/literature/build_rsubread_standalone.py --out build/literature-review/rsubread-runtime/NEW-API-BUILD
```

Then pass the new `rsubread-count-matrix-api.jar` to the same replay command.
The build uses `--release 17`; runtime tests cover Java 21/25 and Linux Java 21.

To generate fresh original R references, use the versioned intake/setup/control
scripts with permitted download/install flags and NEW paths. For R4.4.1:

```powershell
python -B tools/literature/intake_rsubread_r441.py --out build/literature-review/rsubread-runtime/NEW-R441-INTAKE --allow-download
python -B tools/literature/prepare_rsubread_r441.py --intake build/literature-review/rsubread-runtime/NEW-R441-INTAKE --unpacker build/literature-review/rsubread-runtime/NEW-R441-INTAKE/unpacker --out build/literature-review/rsubread-runtime/NEW-R441 --allow-install
```

Locked local archives can be supplied using `--cache` and `--unpacker-cache`
without `--allow-download`. A changed or unavailable download is not silently
accepted. Source recovery, compilation and original control generation remain
separate steps. R4.4.1, Rtools44 and Rtools45 were extracted into workspace
directories without running their installers; existing R libraries and user
Rtools settings were preserved.

The [component proof](rsubread-matrix-development/VALIDATION.json) binds 32
complete original count matrices, 18 successful R bindings, two original
row-count errors, explicit name-policy checks and the same 42 rejection
controls for both reference families on each JVM. Source checks compare the
loaded featureCounts bodies/formals to unmodified official R code. These
checks validate the matrix component; counting, annotation/junction auditing,
runtime supervision and the complete user-facing adapter remain unfinished.

The read-only six-paper draft audit preserves gene/exon/peak/junction roles,
P139's unspecified loading API, P133's missing filter switches and separate
STAR, DRAGEN, DESeq2, Salmon, edgeR and seqinr work. Draft reviews are not yet
applied to the catalog.

References: [Rsubread](https://bioconductor.org/packages/Rsubread/),
[official source repository](https://git.bioconductor.org/packages/Rsubread),
[Rtools44](https://cran.r-project.org/bin/windows/Rtools/rtools44/rtools.html),
[Rtools45](https://cran.r-project.org/bin/windows/Rtools/rtools45/rtools.html).
