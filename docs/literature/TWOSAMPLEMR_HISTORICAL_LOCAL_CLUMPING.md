# Original0.6.8 local clumping

Use standalone `org.jlinalg.cli.TwoSampleMrHistoricalClumpCli` with the
[examples](../../examples/twosamplemr-historical-clump/README.md). There is no
global CLI subcommand yet. The [checkpoint](TWOSAMPLEMR_HISTORICAL_CLUMP_CHECKPOINT_2026-11-21.md)
defines the actual qualification and remaining work.

The sole profile is
`TwoSampleMR0.6.8-ieugwasr1.2.1-PLINK1.9-20260927-R4.6.1-Windows64`.
It uses the original tagged0.6.8 clump_data,149 function guards and declared
present-day dependencies. It does not substitute0.7.14 or recreate historical
dependencies/API panels. Supply explicit Rscript, ordered R libraries and PLINK
path/hash. User/site R libraries are excluded. The exact PLINK executable SHA256
is`efc55270aba7216e4fcebdf781ee04e9fa556c2fe5be1f71c836ba73930a6b6b`.

Supply literal instruments and the complete paired SNP-major BED/BIM/FAM.
The adapter checks dimensions, sample/variant identity and declared capacities,
captures all four inputs as one stable set and retains them without sorting,
subsetting, deduplication or ID repair. The reference-population declaration is
recorded, not inferred or verified from genotypes.

Declare clump_kb,clump_r2,clump_p1,pop,reference_population,seed and
preparation_note explicitly. `source_resources:"original-defaults"` preserves
original resource behavior: no extra PLINK thread/memory flags are injected.
`allow_source_defaults` separately opts into generated IDs and missing P columns.
The source accepts clump_p2 but does not forward it to ieugwasr; the adapter does
not offer it as an effective independent threshold. Inspect the selected source
and captured effective request instead of assuming every documented argument
affects selection.

Original grouping, duplicate instrument rows, default P values, single-row
bypasses, P-value formatting and R report parsing are preserved. A single-row
group can retain a variant absent from the reference or above clump_p1. Numeric
SNP tokens can lose leading zeros in original R parsing, affecting the retained
set; original keys and raw reports remain available. A complete empty selection
is valid selection output and supplies no downstream MR estimate. R's explicit
seed does not control PLINK's wall-clock seed.

Results include `results/clumped.tsv`, `results/decisions.tsv`, original RDS
objects, source/runtime metadata and raw PLINK inputs/reports/logs copied before
original cleanup. Read WARNINGS.md,analysis-status.json,worker/report.json and
each source log before interpretation. The original TwoSampleMR namespace is
unchanged; the two observed ieugwasr bindings are restored after every call.

VERIFY needs the matching JAR but no R execution. It checks capture/source
integrity, literal reference/intake structure and source input/report/selection
projections. It does not independently refit LD likelihoods or make hashes into
signatures. REPLAY reruns the original workflow in a NEW sibling output using
the captured inputs, seed and declared exact runtime. Parent files are preserved.
Output aliases, nesting inside another capture and output paths with spaces
are refused for this Windows R4.6.1 profile.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Restore the named exact runtime, package,
PLINK or input; privately install/download when permitted. If installation or
execution is prohibited, retain the skip and explain the required user action.
An adjacent edition or alternate binary is not silently used.

**FAIL2: NO COMPLETE RESULT.** Preserve raw inputs, original returns/errors,
partials and diagnostics. Correct the named data/design, numerical, integrity,
capacity or process cause, then use NEW output. PLINK rejects r2=1,kb=0 and
unsupported chromosome codes in retained controls; these are explicit failed
attempts, not repaired inputs. Owned worker/child processes are cleaned on
timeout. Original R execution is qualified only on Windows4.6.1; Linux Java
verification of saved captures is a separate claim.
