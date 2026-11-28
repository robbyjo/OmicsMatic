# Native CADD1.6 consequence hierarchy

`org.jlinalg.genetics.CaddConsequenceHierarchy` selects and propagates scores among consecutive annotation rows using the original CADD1.6 consequence tiers. It complements the existing native linear scoring and PHRED calibration components. No original source code, model parameters or reference tracks are bundled.

**Full prediction from raw VCF remains pending.** This library requires already scored annotation rows and does not generate annotations, impute features, identify pathogenic variants or reproduce a paper's filtering recipe. The complete GRCh38 reference archive has passed its official checksum; extraction and full workflow qualification remain separate work.

```java
var options = new CaddConsequenceHierarchy.Options(
    true, false, false, // original --all, --noheader, --PHRED conventions
    new CaddConsequenceHierarchy.Limits(
        1_000_000_000L, 100_000_000L, 1_000_000, 1_000_000));
var statistics = CaddConsequenceHierarchy.transform(inputReader, outputWriter, options);
```

Supply explicit capacities for the entire input, complete consecutive group, individual line and group row count. Character capacities count UTF-16 code units. The method streams the input and retains a whole group only when `allAnnotationLines` requires it. It does not close caller-owned streams. To preserve original Python2 byte semantics, wrap streams with ISO-8859-1 readers and writers; this treats every byte as opaque and round-trips UTF-8 bytes without transcoding. Callers choosing another character encoding must bind that choice separately.

Groups use the first five literal tab-separated fields and consecutive order. Repeated variants in different groups remain separate. The four source consequence tiers precede numeric score ranking; the first numeric tie wins. Comparisons preserve NaN, infinity and signed-zero behavior instead of applying a generic sorted maximum. Returned statistics prominently identify nonfinite inputs and selected groups. Such values remain unassessable for dependent biological interpretation.

In single-row mode the selected row can borrow the first available Grantham/PolyPhen/SIFT tuple from another row of its group. In all-row mode every annotation row retains its own fields, and the selected row's score suffix is propagated. Source header scanning, default column positions, RawScore precedence over the PHRED convention, first ties, trailing ASCII whitespace and header line endings are preserved. This includes unusual source header behavior: scanning stops at RawScore even if Consequence occurs later. The library does not silently repair malformed scores, absent GPS columns, identifiers or column order.

An exception means **FAIL2: no complete result**. Any prefix already written is incomplete; retain it with diagnostics and do not admit it as a successful analysis. Correct the named schema, numerical, I/O or capacity cause and retry a new output. Missing exact permitted upstream data or runtimes require **SKIP3: skip dependent analysis**, with the missing prerequisite and restoration steps shown to the user. Do not reduce rows, models or references to obtain a passing result. The method returns statistics only after processing the complete stream.

Validation compares all 168 cases against the unchanged original Python2 program: 116 successful cases and 52 source refusals. Windows and Linux match every output byte, including partial output preceding refusal, and agree on native statistics. The corpus includes both complete published prediction streams, all eight combinations of the three source flags, dense controls, every consequence tier, GPS borrowing, literal keys, nonfinite/tied values, source header quirks, malformed scores and opaque header bytes. Twelve Java contracts also cover explicit capacity refusal.

Using complete original imputed inputs, both original assembly-specific models and complete conversion tables, the native linear scoring, hierarchy and PHRED chain reproduces every published field in all 30 annotation rows on each host: 18 GRCh37 rows with 1,029 features and 12 GRCh38 rows with 1,028 features. Annotation and feature imputation remain upstream prerequisites. These comparisons do not establish identical hierarchy decisions for every possible floating-point boundary; the linear component's documented numerical envelope still applies.

See the [qualification](literature/cadd-hierarchy-validation/QUALIFICATION.json), [handover](literature/cadd-hierarchy-validation/HANDOVER.md) and [linear scoring guide](cadd-linear-scoring.md). This expands the existing CADD family with zero additional family, paper-review or observation credit. The 250-paper implementation goal remains active.

Behavioral references: [original hierarchy](https://github.com/kircherlab/CADD-scripts/blob/d0a49aeabba01b75d1f0d1061a2f20c1404d1337/src/scripts/max_line_hierarchy.py), [complete workflow](https://github.com/kircherlab/CADD-scripts/blob/d0a49aeabba01b75d1f0d1061a2f20c1404d1337/Snakefile), [original score calibration](https://github.com/kircherlab/CADD-scripts/blob/d0a49aeabba01b75d1f0d1061a2f20c1404d1337/src/scripts/appendPHREDscore.py).
