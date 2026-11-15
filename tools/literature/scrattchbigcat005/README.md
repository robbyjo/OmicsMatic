# Native BIGCAT 0.0.5 marker-doublet component

`org.jlinalg.singlecell.scrattch.ScrattchBigcat005` implements the original
`find_doublet_by_marker` operation for a plain dense double matrix, character
marker groups and one numeric threshold. It runs entirely in Java.

**This is one component. Full BIGCAT workflow qualification and catalog admission
remain unfinished.** This component does not establish whole-tool or paper
reproduction. The catalog's integration count is unchanged by this development
checkpoint.

The source is the unchanged GPL-3 snapshot
[`bbfe5277de63c65a5342e43f3e1eeb1015755fc7`](https://github.com/AllenInstitute/scrattch.bigcat/tree/bbfe5277de63c65a5342e43f3e1eeb1015755fc7),
dated October 3, 2023, version 0.0.5. That is a project-selected public source,
not an established author Git commit. P058's edition remains unknown; P059
reports version 0.0.5. The original DESCRIPTION and all 41 source-file identities
are retained in the reference archive and `native-source-profile.json`.

## Java example

```java
import org.jlinalg.singlecell.scrattch.ScrattchBigcat005;

var means = new ScrattchBigcat005.DenseMatrix(
    new double[][] {{5, 0, 5}, {0, 5, 5}}, 3,
    new String[] {"g1", "g2"}, new String[] {"A", "B", "mixed"});
var result = ScrattchBigcat005.findDoubletByMarker(
    means, new String[][] {{"g1"}, {"g2"}}, 3.5);
// result.matrix().values() is {{5, 5}}.
// The original drops the single selected cluster's name before transposing.
```

The overload taking only `DenseMatrix` uses the source's eight marker groups
and threshold 3.5. It requires the exact source marker names: Slc32a1, Slc17a7,
Slc17a6, Sox10, Opalin, Aqp4, Ly6c1, Slc6a13, Kcnj8, Acta2 and C1qc.

The explicit column count represents zero-row matrices. Null names represent
absent names; a null string element represents a missing R name. The five-argument
matrix constructor can explicitly retain a `dimnames` attribute whose two
components are absent. A null marker group represents R `NULL`; an empty string
array represents `character(0)`. Those index types can fail differently in the
original. Marker names, duplicates, and group order remain literal. Group labels
do not affect this function's returned matrix.

## Errors, missing values, and required action

**Do not interpret an error or an unexpected empty shape as a clean negative
doublet result.** The method throws `OriginalMarkerException` with the original
message and supplies no result on an original input failure. Malformed Java
matrix shapes throw ordinary argument exceptions.

| Observed behavior | What the caller must do |
| --- | --- |
| `subscript out of bounds` | Supply all requested genes with exact matching row names, or explicitly choose the intended marker groups. Missing, empty and NA marker names are not repaired. |
| `no 'dimnames' attribute for array` | Provide genuine matrix row names before using character marker indices. Even `character(0)` can require the attribute. |
| `'x' must be an array of at least two dimensions` | The source does not handle zero or one input cluster columns, or an empty group list, through this function. Use an appropriate separate analysis for those cases; do not duplicate a cluster to bypass the error. |
| `requiresNonFiniteReview()` is true | Inspect the returned values and source inputs. An unknown group comparison can select an entirely missing column. NA, NaN, infinities and literal missing names are not silently discarded or relabeled. |
| One selected cluster or one marker | Inspect both output dimensions. The source drops dimensions before transposing, so a one-marker negative result is 1-by-0, while a single selected cluster loses its cluster name. |

Existing selected numeric values retain their raw double bits. Missing values
inserted by unknown logical indexing use R's missing-real payload. The Java
exception is not an R condition object; full R conditions remain in the saved
reference. Arbitrary R attributes, sparse matrices, vector thresholds and
non-character marker types are outside this typed API.

## Direct source comparisons

The frozen test reference contains 753 actual upstream calls under R 4.2.3 and
matrixStats 1.3.0: 303 complete returns and 450 original errors. The test compares
7,168 returned numeric values by raw bits, complete supported dimnames and shapes,
and exact original error messages. The saved R objects additionally retain the
full conditions, arguments, original function and unchanged RNG states. These
are synthetic method controls, not biological validation.

```powershell
.\gradlew.bat test --tests org.jlinalg.singlecell.scrattch.ScrattchBigcat005Test --no-daemon
```

`src/test/resources/scrattchbigcat005-native-marker-reference.zip` contains the
complete saved reference, a checksummed member manifest, original source archive,
and the exact generator and input-type exporter. The exporter recovers `NULL`
versus `character(0)` from saved original inputs without rerunning the operation.

`reference-pixi.toml` and `reference-pixi.lock` describe the selected Linux
dependency environment for regenerating controls. They are project choices;
the authors' environment is unknown. A compatible original BIGCAT installation
is required only to regenerate the controls, not to use the Java component.
With that installation active, run the two scripts into new directories:

```bash
Rscript --vanilla tools/literature/scrattchbigcat005/GenerateMarkerReference.R \
  /absolute/path/to/unchanged/source-original /absolute/new/reference
Rscript --vanilla tools/literature/scrattchbigcat005/ExportMarkerGroupTypes.R \
  /absolute/path/to/unchanged/source-original /absolute/new/group-types \
  /absolute/new/reference/complete-original-reference.rds
```

If the operating environment forbids installing the reference runtime, skip
control regeneration and report the cause. Java usage and the frozen Java tests
need no R installation. Preserve original failures and partial development
evidence; none is an admitted paper-analysis result.
