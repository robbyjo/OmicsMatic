# Native normal fitting for supplied omics backgrounds

The native Java workflow fits an unweighted normal distribution by analytical maximum likelihood. It returns the complete observation count, mean, population variance, SD, log likelihood, AIC and BIC. An optional BACKGROUND operation tests every supplied score using an explicitly selected tail and ordinary Benjamini–Hochberg adjustment. It retains every row and reports whether the caller's strict/inclusive cutoff is met; it never interprets that flag as a retention instruction.

This implements a **normal-fitting subset** relevant to the recovered fitdistrplus use in P043 ([primary paper](https://doi.org/10.1038/s41586-023-06824-9), PMC10719105, Methods paragraph51, SHA-256 `41db6565b9c28d56ef328ce411a63865d3f6a88dfa070f1c94457546ac40598e`). The paper fits a normal distribution to shuffled co-accessibility scores and then applies BH/FDR. Its function, estimation method, package version and shuffle implementation are unspecified. The sentence about filtering out scores significantly larger than background is retained as written; this workflow neither reverses it nor chooses which rows to retain.

## Warnings and practical differences

**The estimator is explicitly analytical `normal-mle`.** The captured original fitdistrplus1.2-6 default normal MLE uses a numerical optimizer initialized from the mean and population SD. The Java method solves the unweighted likelihood directly. It does not replay that optimizer, user starts, Hessian, covariance, weighted/censored fits, bootstrap, other distributions or all estimation methods. The original package source and all controls remain preserved.

**Original numerical outcomes differ in important cases.** In the captured runtime the default MLE fails on tiny-scale and nearly constant data; the analytical method returns a finite positive-variance solution. For all-zero data the original optimizer returns SD0.1 with convergence0 and covariance warnings, while original moment matching returns SD0 and infinite likelihood. The native method refuses zero variance because a finite positive-SD MLE does not exist there. These outcomes are not repaired, relabeled as agreement or silently substituted. Full original objects, warnings and errors are retained. Thirty-five ordinary original MLE fits and37 positive-variance moment fits match the native analytical estimates and likelihood within the stated numerical tolerances.

**Background suitability is not certified.** The supplied vector's shuffle, sampling, independence and normality remain caller responsibilities. No shuffle or permutation is generated. No missing values are omitted. Original Cicero `NA` connections cannot be silently removed or converted to zero to make a normal fit run. Provide the intended complete finite analysis population with an independently justified provenance, or retain the failure.

**Linear tail underflow remains visible.** Ordinary p-values and BH q-values can underflow to zero. Finite log p/log q values are retained separately, and negative-infinite log tails are labeled `-Inf` when they are outside representable range. The cutoff flag uses ordinary q-values, matching the declared linear BH procedure. Log q is diagnostic and does not silently replace that rule. Numeric zero does not prove literally zero probability. Data order, values and IDs are never repaired.

## Build and run

From the repository root, with an installed Python and JDK21:

```powershell
python tools/literature/build_normal_fit.py --out build/literature-review/fitdistrplus-runtime/my-normal-build
$jar = 'build/literature-review/fitdistrplus-runtime/my-normal-build/omicsmatic-0.3.7-normal-fit-development.jar'
java -Xmx256m -cp $jar org.jlinalg.cli.NormalFitToolsCli --request examples/normal-fit/request.json
```

The builder requires the validated Cicero prediction development parent recorded in `docs/literature/cicero-prediction-development/VALIDATION.json`. On a fresh checkout, reconstruct the earlier documented development parents first. It adds the new Java classes with Java17 compatibility and preserves every parent entry. The canonical/released executable is unchanged; this class-based development command is not a new root CLI route. Installed R libraries and development JARs are not distributed in Git.

The example has two synthetic background observations −1 and1, giving mean0, population variance1 and SD1. It tests five scores with an upper tail, BH and strict q<0.001. Every row remains; only far-upper meets the declared cutoff. This illustrates numeric underflow and emits a prominent precision warning. It does not reproduce P043 or establish biological significance.

Require exit0 and `analysis-status.json` `result_admitted=true`. Inspect `WARNINGS.md`, `provenance.json`, `results/fit.tsv`, and `results/tests.tsv` when present. Inputs and the exact parsed request remain captured under `inputs/`.

## Closed request and input contract

Use `--help` for the complete fields. Both modes require schema_version1, mode `FIT` or `BACKGROUND`, estimator `normal-mle`, samples, value_units and a **new** output path. Relative paths resolve against the request. The unquoted literal UTF-8 sample TSV has exactly `observation_id`, `value`; BACKGROUND tests have exactly `test_id`, `value`. IDs are unique, nonempty and literal. `NA` is allowed as an ID, while NA/NaN/Inf values are refused. No trimming, ID remapping, missing-value omission, imputation, winsorization or rescaling occurs. Numeric tokens remain captured and tested input tokens are echoed unchanged.

BACKGROUND additionally requires tests, background_origin (`caller-supplied-shuffled-coaccessibility` or `caller-supplied-reference`), tail (`upper`, `lower` or `two-sided`), adjustment `BH`, cutoff strictly between0 and1, and cutoff_rule `strict` or `inclusive`. These are explicit user choices, not inferred paper settings. FIT refuses background-only fields. All tests form one complete correction family; no per-sign or selected-score filtering occurs before BH.

At least two finite observations and a positive, finite, representable population variance are required. The variance denominator is n. Overflow, zero/underflowed variance, nonfinite densities and unrepresentable fit summaries fail; no alternative algorithm is silently chosen. Default bounds are 128MiB total inputs, one million observations, one million tests, 100million native operations and1GiB generated fit/test tables. Parser maxima are 512MiB inputs, ten million observations/tests, ten billion operations and128GiB tables. Captured inputs and auxiliary metadata need additional storage. Logical bounds are not OS quotas; use a suitable permitted Java heap and storage.

**FAIL2 admits no result**, including an earlier completed fit if test-table writing later fails. Scientific partials remain under `unadmitted-partial-results`. Preserve the complete attempt, read the named cause in WARNINGS/status/stderr, correct it and retry NEW paths. Invalid request syntax can fail before a bundle exists; stderr still provides recovery instructions. Native analysis needs no external runtime, so unavailable R does not prevent this Java workflow. Reproducing the original reference capture separately needs the exact permitted R/package profile; its probe reports SKIP3 when unavailable and installs nothing.

## Evidence

The [sealed evidence](normal-fit-development/VALIDATION.json) contains all43 original fitting populations, 86 original MLE/MME calls, 36 tail/BH families (468 queried values), full original R objects and warnings, before/after hashes of25,602 unchanged library files, native fixtures and every packaged success/failure. Five original function bodies/formals match the official source before and after the reference run. Native checks run on Windows Java21/25 and Linux Java21; no tests are skipped.

The 51 packaged controls exercise every fitting population, all three tails, complete BH families, literal IDs, input/row/operation/output capacities and late atomic rejection. The portable checker independently recomputes analytical fit summaries and every BH row and compares the captured original R tail families. It does not authenticate execution, independently replay an optimizer or validate a paper's biology.

```powershell
python tools/literature/normal_fit_evidence.py
```

The complete250-paper catalog and semantic review remain in progress. Source roles and implementation counts are maintained separately from this method evidence. Cell Ranger remains excluded and the py-monocle license decision remains pending.
