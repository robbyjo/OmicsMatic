# Java selection of supplied doublet scores

`org.jlinalg.singlecell.DoubletScoreThreshold` implements a strict numeric
cutoff on already computed scores. The HICAT convenience method uses the
factual `score > 0.3` rule reported in P059, PMC10719114. This independent Java
code contains no source-derived HICAT scoring implementation. To compute
HICAT scores, use the separate [original runtime adapter](../scrattchhicat109/README.md).

```java
double[] scores = {0.2, 0.3, Math.nextUp(0.3), Double.NaN};
var rows = DoubletScoreThreshold.hicat109ReportedCutoff(scores);
// retainedRows(): [0, 1]; removedRows(): [2]; unknownRows(): [3]
```

Import the class from `org.jlinalg.singlecell`. General cutoffs use
`select(scores, cutoff)`; `classify(score, cutoff)` returns RETAIN, REMOVE or
UNKNOWN. Equality retains the row. NaN in either operand produces UNKNOWN;
infinities follow ordinary numeric ordering. UNKNOWN is a missing comparison,
not a retained cell. No score-range restriction, imputation or identifier repair
is applied.

All positions are zero-based and preserve input order. The input values remain
unchanged. Returned arrays are defensive copies; callers retain the original
names/metadata and use the returned positions to select them. No upstream
runtime, install or download is needed for this Java operation on supplied
scores. It does not calculate standard DoubletFinder pANN or HICAT scores and
adds no separate family credit.

The complete R 4.2.3 oracle has 1,041 IEEE-754 input bit patterns, 17 cutoffs,
17,697 comparisons and 17 full original-row selections. It includes adjacent
representable values around 0.3, signed zeros, subnormals, infinities and
multiple NaN/NA patterns. The standalone harness passed on Windows Java 25
and Linux Java 21, compiled with `--release 17`. Input bits, row ordering and
defensive copies were also checked. The permanent JUnit test consumes the
frozen R resource rather than constructing expected decisions with Java.

To regenerate and compare the complete R oracle where prerequisites are
permitted:

```sh
python3 -B tools/literature/doublet_score_threshold/qualify.py \
  --rscript /permitted/R/bin/Rscript --out /results/NEW-threshold-qualification
```

R needs `jsonlite`; Java needs `javac` and `java`. This validator installs
nothing. Missing/forbidden prerequisites produce SKIP3; FAIL2 admits no
qualification. Preserve all records, correct the named cause and retry NEW
paths. The Java selector itself has no dependency on R or jsonlite.
