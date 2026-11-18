# Unfinished LD-score reference-preparation workflow

**Development artifact only. Not compiled, tested or qualified. Do not use
this draft for scientific analysis.** It preserves work in progress at the
18 November 2026 handover. The executable production sources remain at the
qualified API commit `a39ac83c4720be26cf94a06f0e069770af7d3ff9`.

See [the current handover](../HANDOVER_2026-11-18_LD_SCORES.md) for the full
project state, priorities, warnings and evidence locations.

## Saved files and safe resumption

- `implementation.patch` contains the proposed changes to `LdscLdScores.java`
  and its test, plus the new `LdscReferenceCli.java`.
- `exact-draft-sources.zip` contains the exact original bytes of all three
  files, preserving any line endings. Every member was read back and checked.
- `DRAFT.json` records original and base hashes, archive and patch hashes,
  scope and known issues. The patch was applied to the exact base in an
  isolated repository and all three source files compared after Git line
  ending normalization. The ZIP is authoritative for raw bytes.

Start from a clean checkout of this handover on a fresh `codex/` branch. Read
the patch, then run from the repository root:

```powershell
git apply --check docs/literature/ldsc-reference-preparation-draft/implementation.patch
git apply docs/literature/ldsc-reference-preparation-draft/implementation.patch
```

Do not apply to a dirty or scientifically changed source tree. A mismatch
needs review against the recorded base, rather than forcing or rewriting the
historical qualification. Preserve this original draft artifact; make new
implementation edits in source and qualify them separately.

## Intended command boundary

The proposed standalone command accepts `--request JSON`; `--default-limits`
prints the intended complete numeric/BED/I/O limits. It captures a declared
one-chromosome hard-call BED/BIM/FAM population, explicit reference table,
sample selection and print selection. It plans to emit filter and mapping
audits, exact chunk graphs, full-precision LD-score tables, standard compressed
LD-score/M files, binary overlap matrices, model/provenance records and checked
admission markers. There is no root dispatcher registration.

The draft's exact 20 request keys are:

```text
schema_version analysis bed_prefix reference_table sample_table
print_variants print_policy annotation_mode annotations coordinate_units
window chunk_size minimum_maf allow_whole_reference limits out
data_provenance sample_provenance coordinate_provenance annotation_provenance
```

The intended `analysis` is `ld-scores`; annotation modes are `unpartitioned`,
`binary` and `continuous`. Coordinate units are `cm`, `bp` or
`retained-snp-index`. In the last mode the literal coordinate field is
`retained-index`; indices are derived after filtering. Real population,
ancestry, assembly, map, relatedness and annotation provenance must be supplied
and checked by the user; the command cannot infer them from filenames.

The reference table is the contribution population in exact selected BIM
order. `print_variants` only controls output rows. `print_policy` is intended
to distinguish `all-retained`, upstream-like `source-membership`, and strict
`require-retained` behavior; missing print IDs must remain explicit in the
audit. Full and common-variant annotation counts and overlaps use the declared
retained contribution population, irrespective of printing.

The proposed whole-reference guard inspects the actual source chunk graph,
including fallback expansion, before pair accumulation. It is deliberately
stronger than a nominal-window-only upstream guard. Refusal should retain
captured inputs and preflight diagnostics as an unadmitted component.

## Required corrections and validation

1. Remove the duplicate create-new write of `model.json`, placing the sole
   write after print-policy metadata. Correct the incomplete help text.
2. Compile the SDK and command. Run the proposed tenth SDK test proving that
   count-vector/overlap storage is admitted before genotype reads. Recheck all
   37 original oracle models on Windows and Linux. The only intended SDK
   diagnostic change is the more complete memory estimate.
3. Build meaningful command tests and actual packaged launches for transport
   of the complete reference population, successful output, all failure/skip
   states and limit controls. Validate SNP-index coordinates against unchanged
   original upstream behavior. Check print masks, source identity and runtime
   integrity before and after computation. Include mixed scopes, literal-ID
   errors, invalid UTF-8, stale/missing backends, existing outputs and capacity
   exhaustion without silent truncation.
4. Execute complete public examples on both platforms. Add a new source-bound
   workflow qualification and archive, preserving the previous API record and
   its guards unchanged. Compare old/new math independently of memory bytes.

Until those steps are complete there is **no qualified reference-preparation
CLI** and **no additional integration credit**. The existing source-bound API
and earlier prepared LDSC commands retain their documented, bounded scope.

For a first compile/test pass after reviewing and correcting the draft:

```powershell
.\gradlew.bat --no-daemon --no-parallel :test --tests org.jlinalg.genetics.LdscLdScoresTest :executableJar
```

That pass alone is insufficient to qualify the command. Applying the draft
will correctly cause the old exact-source LD-score verifier to reject the
changed SDK and test fingerprints. Keep that historical evidence unchanged;
close a separately named qualification for the new sources.
