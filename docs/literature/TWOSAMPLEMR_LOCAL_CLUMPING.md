# Original local clumping with TwoSampleMR

This standalone adapter selects instruments using original `clump_data` and
local PLINK1.9 with a supplied BED/BIM/FAM reference. It retains the complete
inputs, original R objects and RNG endpoints, raw PLINK artifacts, row decisions
and warnings. It produces a selection, not an MR estimate. It does not infer
the reference ancestry or substitute a different LD calculation.

**Qualified scope:** 707 focused tests per Windows Java21/25 and Linux Java21,
21 successful normal-JAR selections, explicit failure controls, original-return
comparisons and real replays/examples. Read the [checkpoint and exact evidence](TWOSAMPLEMR_CLUMP_CHECKPOINT_2026-11-21.md).
Historical paper editions,
Linux original-R execution, global command registration and catalog admission
remain open. The older [development handover](HANDOVER_2026-11-21_LOCAL_CLUMPING.md)
retains the preceding incomplete drafts and failures.

## Runtime and source identity

This profile names **64-bit Windows R4.6.1**, TwoSampleMR **0.7.14** commit
`c08ab8a67b457d8ab87dcb7f6d33d8c8452e4cc6`, ieugwasr **1.2.1** commit
`ce5d0c07c40f1f9cdafff40bb6a644a3913c9ad6` and the official Windows
**PLINK1.9 build dated 27 Sep 2026**. The required executable SHA256 is
`efc55270aba7216e4fcebdf781ee04e9fa556c2fe5be1f71c836ba73930a6b6b`.
Use exact permitted installations and a declared private R library. The earlier
[workflow runtime notes](TWOSAMPLEMR_WORKFLOWS.md) describe the installed profile
and dependency limitations. Installed runtimes are separate from the JAR.

The adapter checks 152 original TwoSampleMR function bodies/formals and seven
ieugwasr definitions. Two ieugwasr functions temporarily receive observation
prefixes containing their original statement bodies unchanged; both bindings
are restored after the call. Copied upstream source and licenses are packaged
with the adapter. See the pinned [ieugwasr source](https://raw.githubusercontent.com/MRCIEU/ieugwasr/ce5d0c07c40f1f9cdafff40bb6a644a3913c9ad6/R/ld_clump.R).
This is local clumping; JWT authentication and an online reference service are
not used by this path.

`source_resources:"original-defaults"` explicitly accepts original PLINK defaults.
No additional `--threads` or `--memory` flags are injected; thread-limit environment
variables are unset for the owned worker. Plan capacity accordingly. A separately
qualified profile is needed to change that upstream calling convention.

## Run a synthetic example

The [examples](../../examples/twosamplemr-clump/README.md) include eight synthetic
variants and 36 samples. Edit the recipe's `rscript`, `r_libraries` and
`plink_executable` to exact local installations. Keep the named SHA256 and
profile. Paths in JSON resolve relative to the JSON file. Choose NEW output
outside a retained capture, with **no spaces in the resolved output path**;
this R version fails when its owned temporary path contains a space.

```powershell
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.TwoSampleMrClumpCli --request examples/twosamplemr-clump/recipes/basic.json
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.TwoSampleMrClumpCli --verify examples/twosamplemr-clump/out/basic
java -cp build/cli/omicsmatic-0.4.0.jar org.jlinalg.cli.TwoSampleMrClumpCli --request examples/twosamplemr-clump/recipes/replay.json
```

REPLAY verifies its parent and runs the same scientific request in NEW output,
retaining a parent-seal link. Runtime path overrides are optional; scientific
thresholds and reference inputs are not overridden by REPLAY. VERIFY is offline:
it checks the complete inventory, packaged sources, literal intake/reference
structure, original-call/PLINK-artifact bindings and returned-row projections.
It does not independently refit every LD likelihood or reproduce all R table
type inference, and a checksum is not authentication of coordinated replacement.

## Inputs and parameters

`operation` is `CLUMP`. Supply all four files: `instruments`, `reference_bed`,
`reference_bim` and `reference_fam`. The instrument TSV needs `SNP`; ordinary
requests should also supply `id.exposure` and `pval.exposure` or `pval.outcome`.
Extra metadata and duplicate rows are retained. SNPs are exact nonmissing tokens
without whitespace. The reference requires complete paired SNP-major BED data,
six-field BIM/FAM rows and unique FID/IID sample identities. Relevant ambiguous
duplicate BIM variant IDs are refused. No population pruning, sorting, allele
repair, arbitrary duplicate selection or implicit `format_data` is performed.

Declare `clump_kb`, `clump_r2`, `clump_p1`, `seed`, `pop`,
`reference_population`, `preparation_note`, exact runtimes and resource policy.
The reference-population declaration records user provenance; a local panel's
ancestry is not determined by the `pop` API label. Optional explicit opt-ins are
`allow_source_defaults.generated_ids` and `allow_source_defaults.pvalues`, both
false by default. The latter permits original missing P values and the `.99`
default when neither P-value column exists; it does not invent association tests.

Capacities cover input/output bytes, instrument rows/columns/entries, reference
samples/variants/genotype entries, conservative work and wall time. Exceeding a
limit fails the attempt with preserved evidence; it never silently truncates
the data or reduces an analysis parameter.

## Outputs, warnings and recovery

Always inspect **WARNINGS.md** and **analysis-status.json** first. Successful
exports are `results/clumped.tsv` and `results/decisions.tsv`. The latter records
every input row, source group/P value, group size, retention and source rule.
`audit.json`, `prepared.json`, `worker/report.json`, full R objects, RNG endpoints
and copied `worker/ld-calls/*/artifacts-*/` PLINK input/log/report files retain
the provenance needed for review. Intermediate deletions by upstream occur
only after the observer copies their original bytes.

- **Singleton bypass:** original ieugwasr returns a one-row group without PLINK,
  including a variant absent from the reference or above `clump_p1`. Retention
  is not proof of LD independence or reference membership.
- **Identifier coercion:** original R may parse numeric-looking PLINK SNP tokens
  as numbers, dropping leading zeros and excluding literal input identifiers.
  Inspect both raw reports and source-parsed keys. Retain the source outcome or
  document a consistent explicit input/reference mapping and rerun NEW output.
- **P-value precedence/formatting:** exposure P values take precedence over
  outcome P values. Original text formatting can make distinct P values tie.
  Original tie behavior and warnings remain; no higher precision is substituted.
- **Completed empty selection:** zero retained variants is an admitted filtering
  outcome with no downstream MR estimate. Do not replace instruments silently.
- **FAIL2: NO COMPLETE RESULT:** invalid data/design, changed inputs, integrity,
  numeric/process/timeout or capacity failure. Preserve raw errors, inputs and
  partials; correct the named cause and retry NEW output. PLINK rejection of
  `r2=1`, `kb=0` and unsupported chromosome codes is retained.
- **SKIP3: DEPENDENT ANALYSIS SKIPPED:** supply the named exact permitted software
  or input, or retain the skip. Downloads/private installation are allowed where
  permitted. If installation/execution is actually prohibited, explain the
  restriction and skip the affected analysis. Absence is not an installation ban.

R seed records original RNG behavior, including generated IDs; it does not set
PLINK's wall-clock seed. This bounded source qualification is not biological
validation, whole-tool parity or reproduction of unqualified historical editions.
