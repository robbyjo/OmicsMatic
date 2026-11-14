# Exact synthetic DoubletFinder requests

**SCT warning:** this fixture produces 3,706 original R warnings, including 12
iteration-limit warnings. Its output is retained for source comparison;
convergence is not qualified. Read the generated `WARNINGS.md` before using any
output. No source warning is suppressed and no fractional artificial count is
rounded to make a test pass.

The archive contains all inputs and complete original expectations for ten
successful source executions and two expected original errors. It uses 160
synthetic cells and 300 synthetic genes. The literal cell identifier `NA` remains
a string. No real study data or runtime binaries are included.

Extract to a new directory; this command performs no installation or analysis:

```sh
python3 -B examples/doubletfinder206/prepare.py --out /path/to/NEW-examples
```

Provision the exact permitted runtime using the
[workflow guide](../../tools/literature/doubletfinder206/README.md). Then run a
complete log-normalized Seurat example:

```sh
python3 -B tools/literature/doubletfinder206/run.py \
  --runtime /path/to/NEW-install/provider \
  --request /path/to/NEW-examples/requests/lognorm-default-pN.rds \
  --out /path/to/NEW-fit
```

Compare the entire original returned Seurat object, including every slot,
attribute, closure and numeric/NA bit pattern:

```sh
/path/to/NEW-install/runtime/.pixi/envs/default/bin/Rscript --vanilla \
  tools/literature/doubletfinder206/CompareOriginal.R \
  /path/to/NEW-install/runtime/library \
  /path/to/NEW-examples/expected/lognorm-default-pN/complete-original-return.rds \
  /path/to/NEW-fit/original/complete-result.rds \
  /path/to/NEW-comparison.json
```

Other requests cover annotations, reuse-pANN classification, SCT, the complete
parameter sweep, summaries with and without ground truth, pK tables/plots with
and without AUC, and homotypic annotation arithmetic. `factor-annotations-error`
and `old-reuse-FALSE-error` must return FAIL2, retaining the original errors.
The second demonstrates the current original API's `reuse.pANN=NULL` initial-run
convention; the legacy `FALSE` example is not silently translated.

Each RDS request contains an original argument list and explicit R RNG kind/state.
These are trusted serialized inputs and require the declared R packages.
The supplied preprocessed objects retain fixed original command timestamps.
Regenerated preprocessing has new timestamps; use the frozen requests for these
whole-object comparisons. Original condition objects and per-execution timing
messages are retained separately; console or wall-clock-message equality is not
claimed. No paper, whole-package or biological replication is established.
