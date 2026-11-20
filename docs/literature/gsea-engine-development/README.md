# Native GSEA engine: saved development qualification

**The Java library checkpoint is implemented. The project file workflow and
global command remain unfinished; no new GSEA catalog credit is claimed.**
Read the [handover and remaining work](../HANDOVER_2026-11-20_GSEA_ENGINE.md).

The normal executable JAR is source-built and checked on Windows Java 21/25 and
Linux Java 21. Each host checks 468,270 fields from nine original 10,000-permutation
compositions and 200,469 fields from 423 admitted option/boundary cases, plus 201
strict refusals. The frozen corpus has 208 cases for each of the original 4.3.2,
4.3.3 and 4.4.0 source profiles. Each original sweep compares stock and audited
exported objects before classifying a case. These synthetic comparisons do not
establish whole-tool, author-data, biological or deployment equivalence.

`QUALIFICATION.json` pins checkpoint source bytes, archives and the exact producer.
`checks.zip` retains build/test logs, packaged checks, original sweep process
records, previous source versions and local orchestration scripts. Absolute
paths in historical logs/scripts describe this machine; do not execute them
without inspecting the handover. `complete-null-references.zip` holds synthetic
inputs and the full original output objects for nine long-permutation cases.
The option/boundary corpus is the committed test resource
`src/test/resources/org/jlinalg/enrichment/broad-gsea-engine-originals.zip`.

```powershell
python -B tools/literature/verify_broad_gsea_engine_checkpoint.py
```

The reader checks saved integrity and expected qualification counters. It does
not launch models, download software, establish authenticity against coordinated
replacement or prove the unfinished file workflow. The producer JAR, original
runtimes and full private evidence are ignored build artifacts: transfer them
separately if moving machines. GitHub source publication is not their backup.
