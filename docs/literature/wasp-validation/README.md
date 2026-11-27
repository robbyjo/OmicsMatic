# Original standalone WASP mapping qualification

This milestone covers the standalone WASP mapping workflow with original
BWA0.7.17, complete native Java audits, and fresh execution from saved inputs.
It does not finish the 250-paper implementation goal or reproduce an author's
experiment. `QUALIFICATION.json` identifies the exact producer and source context.

The qualification requires 11 complete captures and 11 fresh replays, plus 11
independent executions of the original programs. Every comparison includes all
eight BAM record populations and every generated FASTQ byte. Controls cover
single/paired reads, text and HDF5 inputs, selected monomorphic samples, original
VCF conversion, seeded paired duplicate removal, mixed reads with nonuniform
qualities, explicit chromosome-prefix fallback/all samples, CSI, a deliberately
empty final result, and a nonempty original BWA ALT annotation. Native and
Hornet-regression suites contain 355 tests on each operating system; 14 are
WASP tests and 341 are existing Hornet component regressions.

Ten packaged negative controls require prominent skips/failures and no complete
result: missing request, binding and BAM; unknown HDF samples; record capacity;
an incorrect HDF index cell; a lossy VCF allele; changed saved FASTQ audit;
changed final index; and an additional unsealed saved file. Two additional
original-C controls compare complete converted variants, genotypes, phase and
samples, including the original partial-missing-GT rule.

**Warnings and required action:** Read the [workflow contract](../../wasp-tools.md)
and every original log. Missing/forbidden prerequisites produce **SKIP3** and skip
dependent work. Invalid/changed data, failed QC, source/runtime changes,
cancellation and exhausted capacities produce **FAIL2**, with no complete
result. Preserve all attempts, correct the stated cause and retry new paths.
An empty final BAM skips downstream analysis. Native BAM audits hold records and
population maps in JVM memory; these controls do not establish whole-genome
scale or reserve a heap for an experiment.
Independent comparison readers can report missing optional indexes while
sequentially reading source or intermediate BAMs. Those diagnostics remain
visible; every required final index is checked against the complete record
population. A failed final-index audit admits no complete result.

Original quality/orientation rules, unordered paired CIGAR comparison and GT
masking remain explicit. Lossy C field truncation is refused, and retained
wrong-chromosome coordinate coincidences fail native QC. Original single-read
duplicate removal is not admitted. Hornet's full pipeline, STARsolo built-in
WASP, CHT, exact author revisions/references and whole-paper reproduction remain
separate. An owned merged-BAM index is created before original paired duplicate
removal; its earlier missing-index refusal remains retained.

Public records contain project-authored code, summaries, synthetic controls,
identities and hashes. Original articles, runtime source/binaries and raw process
traces stay private. `source-context.zip` retains the precise verifier/compiler
context. After future shared-source changes, restore that context in an isolated
directory to verify this historical milestone; do not refresh old seals.

```sh
python -B tools/literature/verify_wasp_workflows.py
python -B tools/literature/verify_wasp_workflows.py --evidence /absolute/retained/wasp-897
```

The first command checks source/fixture context. The second also reads retained
private evidence; it does not run fresh inference. Use `wasp-tools --verify` for
a fresh original-runtime replay. Qualification of source code, its producing JAR,
public catalog admission, release assets and website deployment are distinct.
