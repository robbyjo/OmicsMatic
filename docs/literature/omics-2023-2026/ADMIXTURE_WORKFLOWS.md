# ADMIXTURE fitting, supervised references and projection

**Development command:** `admixture-tools` runs pinned original ADMIXTURE with native Java input, identity, likelihood and output checks. Use the exact candidate named in `admixture-adapter-validation.json`. The canonical executable and release are unchanged. This is a bounded original-tool integration; historical versions and complete paper pipelines require separate reconstruction.

**Dependency rule:** if the required runtime cannot be installed or executed, **skip the dependent analysis**. Analysis never installs software. Exit 3 means an unavailable, unsupported, forbidden or initially changed dependency; exit 2 means invalid input, process/timeout failure, an input/runtime change during analysis, or a failed numerical audit. Both retain a prominent `WARNINGS.md`, `analysis-status.json` and `session.json`. Failed/skipped intermediates are quarantined in `partial/`; canonical result tables are withheld. Existing output directories are preserved.

## Private runtime setup

The current profile runs original 1.4.0 natively on Linux x86_64. The explicitly selected legacy 1.3.0 profile uses pinned private QEMU 8.2.2 with CPU `qemu64`; legacy is unsupported upstream. On Windows run **Linux Java inside WSL**. A Windows JVM reports a dependency skip.

Run setup from a Linux shell, substituting absolute paths:

```sh
python3 tools/literature/install_admixture.py \
  --profile linux-x86_64-1.4.0 \
  --home /ABS/NEW_PRIVATE_HOME --cache /ABS/ARCHIVE_CACHE \
  --report /ABS/NEW_SETUP_REPORT --allow-install --allow-download
```

Use `--allow-install` only where installation is permitted. Omit `--allow-download` for offline setup and supply repeated `--archive-dir /ABS/ARCHIVE_DIRECTORY` arguments. A verified existing home can be reused without either permission flag. Changed/unmanaged homes and corrupt caches are retained; choose new paths after investigating them. Legacy setup selects `linux-x86_64-1.3.0-qemu8.2.2` and needs `dpkg-deb` to extract the additional pinned package. No global OS package, kernel setting or binary-format handler is installed or changed.

Admission verifies archive/member hashes and an actual deterministic K=1 fit, complete matrices and an independent binomial likelihood. Help/version output alone is insufficient. Runtime inventory and host/kernel identity are rechecked before and after analysis. Upstream binaries and copyright notices remain private; they are not redistributed by this project.

## Recipes and execution

Copy the [recipe templates](../../../examples/admixture/README.md) and replace every review statement and scientific choice. Paths resolve relative to the recipe. For a synthetic demonstration, `python3 examples/admixture/make_demo.py NEW_DIRECTORY` generates all five runnable recipes and genotypes without external packages.

```sh
java -Xmx4g -jar /ABS/CANDIDATE.jar admixture-tools \
  --recipe /ABS/fit.json --home /ABS/ADMITTED_PRIVATE_HOME \
  --out /ABS/NEW_FIT_OUTPUT --timeout 3600
```

An ASCII-named UTF8 JSON `--options-file` can carry string-valued `recipe`, `home`, `out` and `timeout` options, including Unicode paths; direct flags override it. Recipe/options hashes describe the exact bytes parsed. Select an adequate Java heap and machine resources for the declared bounds; the wrapper does not reserve upstream process memory or certify physical capacity.

Every recipe declares version, operation, PLINK prefix, assembly, organism/marker/allele/LD/sample/reference/ploidy review, K, seed, threads, global model ploidy and genotype coding, optimizer/acceleration, major/minor stopping rules, CV folds, bootstrap replicate/block counts, penalty, printed-precision policy and resource ceilings. There is no automatic K/seed selection, replicate alignment or hidden scientific default. Plan separate K/seed runs and document any aggregation or model-selection rule.

The optimizer can use `block` or `EM`, with explicit `none`, `qn1`–`qn20` or `sqs1`–`sqs20` acceleration. Stopping rules distinguish `delta` from `iterations`; a fixed stop is not convergence certification even when upstream prints “Converged”. Printed point-delta compatibility uses decimal precision intervals without a fixed absolute tolerance floor. CV retains the original held-out-genotype error; bootstrap retains original SE and signed bias. Their calibration is not independently established by checking a point-estimate likelihood.

## Input and reference identity

Use variant-major BED with complete six-column BIM/FAM and unique marker IDs. This interface requires positive numeric contig names and resolved, distinct allele labels. Sample identity is the exact FID/IID pair. All-missing samples/markers, invalid dimensions, inconsistent coding and exceeded bounds fail. No filtering, allele flip, coordinate change or biological ploidy conversion is inferred.

Original P counts **BIM allele 2**; native BED calls count allele 1 and are explicitly complemented for the likelihood check. BIM labels do not establish biological REF/ALT or strand. Diploid hard calls use model ploidy 2. Globally haploid input uses homozygous 0/2 coding with ploidy 1; doubled pseudohaploid input can instead explicitly use diploid weighting. Raw tetraploid dosage and mixed sex/chromosome ploidy are not implemented by this recipe.

Supervised operation requires a tab-separated `fid`, `iid`, `population` file containing every input sample exactly once. `-` explicitly marks unknown references. Exactly K distinct reference populations are required. Safe internal aliases prevent supplied labels from becoming commands; all known Q rows must agree on component identity and the original numerical floor. Original component order is recovered from those anchors rather than an alphabetical assumption. Labels are caller metadata, not inferred social identities.

Projection requires a completed typed `fit`/`supervised` bundle, matching K, version, assembly and exact BIM bytes, with hash-bound P/component metadata. Targets may have new IDs. `projection_ploidy_policy=require-same-model` requires the same coding/weighting; `explicit-target-model` permits a separately reviewed target model such as haploid targets against learned diploid allele frequencies. Genotypes are never automatically converted. Projected P must remain byte-identical to the training matrix.

## Results, warnings and recovery

`Q.tsv` maps original component values to exact FID/IID pairs. `P.tsv` records marker position and counted/other BIM alleles. `component-labels.tsv` keeps anonymous columns or caller reference labels separate. Raw matrices, original filenames and stdout/stderr are retained. Fitted training bundles include `model.json` for projection. Bootstrap adds `Q-standard-errors.tsv` and signed `Q-bias.tsv`.

`native-audit.json` records complete genotype counts, original scalars, native likelihood/objective bounds, stopping diagnostics and precision limitations. Original parameter files use six decimals; central reconstructed likelihoods are diagnostic, not exact fits. Q is never renormalized. Printed zero can make a lower likelihood bound negative infinity; the default rejects this, while an explicit retention policy requires a prominent warning. Likelihood compatibility does not certify a global optimum, uncertainty calibration, ancestry interpretation or whole-paper parity.

**Observed runtime problems:** direct legacy 1.3.0 fitting failed on the validated WSL kernel at a disabled `vsyscall`; the explicit emulated profile avoids changing host settings. Original 1.4.0 bootstrap crashed when its default block of 100,000 exceeded 1,200 markers. This interface requires an explicit positive block smaller than the retained marker count. Choose a scientifically appropriate block for the ordered marker set and linkage structure; no automatic retry changes it. Versions also use different penalized-output filenames, which are retained and checked.

**Required action after any skip/error:** read the top-level warnings and session, inspect the named files and original logs under `partial/`, correct the dependency, permission, data, resource or numerical cause, and rerun to a new output directory. Preserve prior attempts. Do not use retained partial matrices as completed analysis results. Reported versions `1.23` and `1.3` are not silently upgraded to an available profile.

See [runtime intake and historical failures](ADMIXTURE_RUNTIME_STATUS.md), [native Java API](NATIVE_ANCESTRY_MIXTURE.md), [paper-specific source roles](ADMIXTURE_SOURCE_REVIEW.md), the exact candidate proof and each run's warnings. Cell Ranger remains user-excluded.

Primary upstream references: [official downloads](https://dalexander.github.io/admixture/download.html), [official manual](https://dalexander.github.io/admixture/admixture-manual.pdf), and [QEMU user-mode documentation](https://www.qemu.org/docs/master/user/main.html).
