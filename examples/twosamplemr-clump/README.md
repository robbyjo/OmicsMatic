# Synthetic original local-clumping examples

Read the [adapter guide and recovery instructions](../../docs/literature/TWOSAMPLEMR_LOCAL_CLUMPING.md)
and [scoped qualification evidence](../../docs/literature/TWOSAMPLEMR_CLUMP_CHECKPOINT_2026-11-21.md).
These three RUN recipes have been executed with the exact named runtimes.

The panel has eight variants and 36 synthetic samples: 27 founders and nine
children. It is not a biological cohort or an ancestry reference. `provenance.json`
pins all input bytes. The BED data are SNP-major; keep the paired BIM/FAM intact.

Edit `rscript`, `r_libraries` and `plink_executable` in each RUN recipe to your
exact permitted local installation. The example paths under `C:/omics-runtime/`
are placeholders, not bundled software. Keep the profile and named executable
SHA256. Choose NEW outputs outside retained captures and use a space-free
resolved output path. Missing exact prerequisites produce SKIP3 with recovery.

From a built repository root:

```powershell
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.TwoSampleMrClumpCli --request examples/twosamplemr-clump/recipes/basic.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.TwoSampleMrClumpCli --verify examples/twosamplemr-clump/out/basic
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.TwoSampleMrClumpCli --request examples/twosamplemr-clump/recipes/replay.json
```

`multiple-groups.json` demonstrates independent source grouping.
`numeric-looking-ids.json` deliberately pairs a numeric-ID BIM with matching
literal instrument IDs; original R parsing can drop leading zeros and produce
an empty selection. Inspect its prominent `SOURCE_INDEX_COERCION` warning, raw
PLINK report, parsed keys and decisions rather than repairing the output.

Every success is a selection with warnings, not an MR estimate. Read
`WARNINGS.md` and `analysis-status.json` before using the two result TSVs.
FAIL2 admits no complete result; preserve the cause/partials and retry corrected
inputs in NEW output. See the guide for singleton bypasses, default opt-ins,
resource policy, capacities and the precise offline-verification limits.
