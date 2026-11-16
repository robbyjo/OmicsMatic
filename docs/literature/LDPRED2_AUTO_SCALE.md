# LDpred2-auto scale control and current command

**Source build required.** The current command is:

```powershell
.\gradlew.bat executableJar
java -jar build/cli/omicsmatic-0.4.0.jar ldpred2-tools --help
java -jar build/cli/omicsmatic-0.4.0.jar ldpred2-tools --request request.properties --out NEW-analysis
java -jar build/cli/omicsmatic-0.4.0.jar ldpred2-tools --verify NEW-analysis --out NEW-verification
```

For original execution, run Java inside a permitted Linux x86-64 environment.
Native Java inf/grid also work on Windows. Published v0.4.0 downloads lack these
development methods. [Command qualification](LDPRED2_COMMAND.md) and the frozen
[v2 schema/runtime guide](LDPRED2_WORKFLOWS_V2.md) describe the same analysis code.
Their earlier absence-of-alias/eight-variant-only statements are historical.
This separate record extends selected original 1.12.4 auto to the tested scale;
it does not change the old proofs or claim all editions were tested at this size.

The unmodified selected bigsnpr 1.12.4 public function was independently run on
1.1 million variants, 2.2 million stored LD entries and three initial causal
fractions (0.01, 0.9, 0.1). It used the original defaults: 500 burn-in and 200
retained iterations, MLE enabled, sparse follow-up disabled, sign jumps enabled,
correlation shrinkage one and default bounds. Default reporting retains zero
sampled-coefficient columns. The adapter used those same defaults, then compared
complete original R objects, every exported effect/path/posterior and RNG state.
Genuine recomputation repeated the actual workflow and reproduced the complete
scientific results. Original R had an 8 GiB address-space limit.

**Finite output is not convergence.** All three source chains returned finite
results, but their estimated causal fractions differed (approximately 0.036,
0.901 and 0.110). Preserve those differences. The adapter has no automatic
convergence filter, chain selection or chain averaging. A root completion marker
records successful computation and recording; it does not establish MCMC
convergence, scientific suitability or biological validity. Inspect parameter
trajectories in each model's `paths.tsv` and the full original objects. Establish
chain stability and a justified selection policy before interpreting or combining
weights. If further analysis is justified, use new outputs and retain these runs;
do not silently remove a difficult chain or adjust inputs to force agreement.

The data are a Gaussian summary-statistic simulation with population pair LD,
not a sampled GWAS cohort or the paper's LD resource. Pair correlation is 0.25;
the simulated genetic variance is 0.3, causal probability 0.1 and effective sample
size 100,000. Frequency variation and LD-correlated noise are retained. Data and
model seeds are distinct and recorded. No variants were thinned and no LD
dependencies were split to fit. These are software capacity/parity controls,
not tests of realistic chromosome-wide LD or recovery of true genetic parameters.

The selected source reports version 1.12.4 but is not established as the paper
author's exact commit/binary. The other supported edition, 1.12.2, retains its
separate small original controls; native inf/grid have separate 1.1 million
variant controls. No full-paper replication is claimed.

An original auto request needs the standard captured summary/CSC inputs,
`engine=original`, `method=auto`, an adopted `runtime`, a `heritability` initialization,
`seed`, and a `models` table headed `model`, `p_init`. Omitting optional sampler
settings uses the documented source defaults captured in `effective.properties`.
Defaults used in this software control are not inferred author settings for a
paper with unreported parameters. The LDpred2 family does not imply LDpred1 support.

**SKIP3:** skip dependent analysis if a required permitted runtime/helper cannot
be supplied. Install only where allowed; otherwise leave it skipped. No runtime,
edition or algorithm is silently substituted during analysis.

**FAIL2:** retain inputs, original objects, raw consoles and provisional outputs.
Correct the named input/process/capacity/recording cause and retry new paths.
Source nonfinite candidates have no accepted weights; never repair them. A
collection with zero accepted candidates fails. Never refresh saved fingerprints
to make a historical bundle pass a changed implementation.

Actual scale controls can be regenerated with
`tools/literature/ldpred2_auto_scale/qualify.py --help` in the permitted Linux
environment. Saved checks and their source identities are in
[the scale evidence](ldpred2-auto-scale-development/STATUS.json). Catalog
admission, exact author settings and the remaining 250-paper tool families are
separate work.
