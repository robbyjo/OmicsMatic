# Original dense PEER examples

These six synthetic examples cover dense random initialization, an added mean,
known covariates, measurement uncertainty, nondefault Gamma priors and PCA
initialization. Each RUN is followed by a separate Java VERIFY process. No
participant data, executable or installed runtime is included.

Build the current source JAR with `gradlew.bat executableJar` on Windows or
`./gradlew executableJar` on Linux. Its default path is
`build/cli/omicsmatic-0.4.0.jar`. The released 0.4.0 predates this adapter.

On Windows with an existing permitted WSL distribution containing CMake, g++
and Python 3, create a private runtime:

```powershell
python -B tools/literature/build_original_peer.py `
  --out build/NEW-peer-runtime --wsl-distribution YOUR_DISTRO
```

On Linux, run the same builder without `--wsl-distribution`. It downloads the
exact pinned source and uses bundled Eigen/alglib. Use Python 3.11+ without `-O`.
Supply `--archive PATH` to use the exact checksummed archive already available.
Keep the resulting `runtime.json` with its build. The builder retains compiler
logs and emits prominent warnings on a missing prerequisite or failed build.

Run all examples from the same host/transport as that binding:

```text
python -B examples/peer-workflows/run_examples.py --jar build/cli/omicsmatic-0.4.0.jar --runtime build/NEW-peer-runtime/runtime.json --out build/NEW-peer-examples
```

Select Java 17+ with `--java PATH` if needed. The runner writes complete configured
requests and retains every stdout/stderr file. Read RESULT.json, each
analysis-status.json and WARNINGS.md. A skipped/failed example does not become
an admitted result, and dependent verification is skipped explicitly.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Supply the named exact permitted source,
runtime or input and retry new output, or retain the skip if unavailable.
**FAIL2: NO COMPLETE RESULT.** Preserve raw inputs, model objects, histories and
logs; fix the named data/design, numerical/convergence, integrity, capacity or
process cause and retry NEW output. Do not overwrite a retained attempt.

The upstream model stores FP32 matrices. Conversion/underflow counts and maximum
absolute changes are recorded in prepared.json. Latent exports omit fixed known
covariates; full source factors/loadings remain in raw model state. Residuals
are the original joint fitted-model residuals. Factor-count/QTL optimization,
upstream normalization, sparse prior-guided PEER, older editions and complete
author workflows remain outside this prepared dense scope. Read the
[workflow guide](../../docs/literature/PEER_WORKFLOWS.md).
