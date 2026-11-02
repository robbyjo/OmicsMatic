# Original mashr covariance preparation with native fitting

The repository Python command runs the actual locked mashr 0.2.79 covariance
functions, then passes their unexpanded matrices to the validated Java
`mashr-tools` fitter. It verifies the complete native child and reruns original
preparation before admitting a top-level result. The command currently supports
one exact Windows AMD64 R 4.6.1 profile and CPython 3.11 or newer.

**Development scope:** canonical covariances, uncentered PCA, original Bovy
extreme deconvolution, explicit residual initialization, native plain-EM mixture
fitting and full target posteriors. Sharing/composite hypotheses, paper-specific
input derivation, study routes and the eight paper-role reviews remain open.
The catalog still marks mashr missing; its 61 completed scoped integrations are
unchanged. This bridge does not establish whole-paper reproduction or biological
accuracy.

The [retained validation proof](mashr-original-preparation/VALIDATION.json) and
`mashr-original-preparation/controls.zip` record the original comparisons, expected
failures, rejected resealed alterations, target separation and platform boundary.
Previous failed attempts are retained in that archive as failed evidence.

This checkpoint covers 39 workflow, dependency, corruption, path and example
checks. Nine positive cases compare 65,234 numeric values with independently
executed original R primitives; the maximum absolute difference is below
`4e-13` on these fixtures. Full parent preparation replay has zero numeric
difference. These fixture results do not extend the supported numerical domain
or establish optimizer identity, biological accuracy or complete paper coverage.

## Prepare the private runtime

Use PowerShell from the repository root. Every home, report, input and output
directory below must be new. Preserve previous attempts and choose new names
when retrying. The cache can be reused only with matching locked bytes.

```powershell
$mashPython = 'C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
$mashJava = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
$mashRHome = 'C:/Program Files/R/R-4.6.1'
& $mashPython -I -B tools/literature/install_mashr_runtime.py `
  --r-home $mashRHome `
  --home build/mashr-private-1 --report build/mashr-setup-1 `
  --cache build/mashr-download-cache --allow-install --allow-download
if ($LASTEXITCODE -ne 0) { throw 'Stop: read build/mashr-setup-1/WARNINGS.md before running analysis.' }
```

Change the executable paths for your permitted environment. Base R must already
be installed and match every locked runtime fingerprint; a matching version
string alone is insufficient. This command installs 17 exact CRAN package ZIPs
and a private psutil monitor into the selected home. Base Matrix is supplied by
the admitted R distribution. The command also retrieves psutil's source archive
for provenance. Official URLs, sizes and hashes are in
`tools/literature/mashr-original-runtime-lock.json`.

The installer uses the private package library and monitor without modifying
global R or Python libraries. Omit `--allow-download` for a verified offline
cache. Installation requires `--allow-install`; omitted permission produces
SKIP3. Setup checks exact packages and a two-feature analytic Gaussian example.
Its `COMPLETED.json` admits the runtime only; no analysis has run.

R distribution fingerprints describe an observed installation. They do not
authenticate a vendor signature or prove source-to-binary reproduction. A fresh
machine with a different supported edition requires a separate runtime profile
and validation rather than editing the saved lock to accept it.

## Run the complete example

The bridge currently requires the exact development candidate identified in
`docs/literature/mashr-native-workflow/VALIDATION.json`. In the validated workspace
it is at the path below. The canonical executable under `build/cli` is separate.
If this candidate is absent, stop and obtain or independently validate the exact
artifact; substituting an arbitrary build is rejected with SKIP3.

```powershell
$mashJar = 'build/literature-review/mashr-runtime/public-native-workflow-1/package-4/omicsmatic-0.3.7-mashr-development.jar'
New-Item -ItemType Directory -Path build/mashr-input-1 -ErrorAction Stop | Out-Null
Copy-Item -LiteralPath examples/mashr-preparation/effects.tsv,examples/mashr-preparation/standard-errors.tsv,examples/mashr-preparation/populations.tsv,examples/mashr-preparation/settings.tsv,examples/mashr-preparation/preparation.tsv -Destination build/mashr-input-1
& $mashPython -I -B tools/literature/mashr_preparation.py `
  --runtime build/mashr-setup-1/COMPLETED.json --java $mashJava --jar $mashJar `
  --input build/mashr-input-1 --out build/mashr-result-1
if ($LASTEXITCODE -ne 0) { throw 'No analysis result admitted: read stderr and the result WARNINGS.md.' }
& $mashPython -I -B tools/literature/mashr_preparation.py `
  --runtime build/mashr-setup-1/COMPLETED.json --java $mashJava --jar $mashJar `
  --verify build/mashr-result-1
if ($LASTEXITCODE -ne 0) { throw 'Parent verification did not pass.' }
```

Top-level `COMPLETED.json` plus successful parent verification admits this scoped
result. `preparation/original/source-status.tsv` is an unadmitted preparation
stage. `native-analysis/COMPLETED.json` admits the narrower native child.
`native-analysis/execution/posterior/mean.tsv`, `sd.tsv`, `lfsr.tsv` and
`covariance-column-major.tsv` contain target results; use the adjacent IDs to
interpret every row and condition. Read both parent and child `WARNINGS.md`.

Parent verification requires the exact original Windows R profile. It reruns
trusted repository code, checks every original numeric table, identities and RNG
states, and invokes the native child's complete replay. Successful and failed
verification scratch is retained and named in stdout/stderr. RDS, human-readable
logs and session information are retained supplemental evidence; they are not
executed as analysis inputs or authenticated process history. The numeric ED
likelihood history is checked against replay.

The native child can be copied and separately verified on tested Java 21/25,
including Linux, without R:

```powershell
& $mashJava -Xmx128m -XX:ActiveProcessorCount=2 -jar $mashJar mashr-tools --no-config --verify build/mashr-result-1/native-analysis
```

This verifies the native child only. A Linux request to verify the full original
parent with this Windows profile returns SKIP3 and preserves the producer bundle.

## Declare populations and methods

All input is UTF-8 TSV. Effects and standard errors have the header
`feature<TAB>condition_1<TAB>condition_2...`. Standard errors must be finite and
positive; effects must be finite. Numeric-looking feature IDs remain strings.
Condition names and ordered IDs must match exactly. There is no automatic
P-value conversion, missing-value imputation or prefix selection.

`populations.tsv` uses `feature<TAB>train<TAB>strong<TAB>target` and literal
`0`/`1` flags for every row. Strong rows learn PCA/ED matrices. Training rows fit
the representative mixture and residual model. Target rows receive posteriors.
Changing a row assigned only to targets cannot affect learned matrices or the
training fit. No singleton population is padded or duplicated.

`preparation.tsv` has the `key<TAB>value` header and exactly these keys:

| Key | Example | Meaning |
| --- | --- | --- |
| schema_version | 1 | Exact preparation schema. |
| covariance_mode | canonical_pca_ed | `canonical` or canonical + PCA-seeded Bovy ED. |
| npc | 2 | Number of uncentered source PCs; at least two, at most the conditions and real strong rows for PCA/ED. |
| ed_tolerance | 1e-6 | Original Bovy ED tolerance. |
| ed_max_iterations | 2000 | Original ED iteration cap. |
| seed | 123 | Explicit R random seed; initial/final RNG states are saved. |
| residual_initial | identity | `identity`, `supplied` or original `simple_null`. |
| null_z_threshold | 2 | Strict all-condition null-Z threshold for source simple-null selection. |
| max_rows | 100000 | Full input row capacity. |
| max_conditions | 256 | Full condition capacity. |
| max_input_cells | 16000000 | Effects plus SE cell capacity. |
| max_work | 1000000000000 | Declared conservative preparation-work capacity. |
| max_bytes | 1073741824 | Declared preparation-storage estimate capacity. |

Supply a headerless square `residual.tsv` only for `residual_initial=supplied`.
Original simple-null selection uses the actual alpha-transformed training data
and a strict inequality. Its sample covariance uses the original denominator.
An undefined or non-positive-definite estimate fails with the candidate retained.

`settings.tsv` has the same two-column format. The complete 18-key native example
is supplied in `examples/mashr-preparation/settings.tsv`. This bridge requires
`schema_version=2`, `operation=fit_predict`, `basis=supplied` and
`optimizer=native_em`. The example explicitly sets alpha, posterior pruning,
covariance/numeric tolerances, SE floor, scale grid, normalization, point mass,
prior, weight iteration/gap criteria and residual method/criteria. Native
`residual=simple` additionally requires `null_z_threshold` and `null_correlation`;
it reestimates V under the native simple-null method. For a supplied native prior,
add `prior.tsv`. Unknown or unused files/settings fail admission.

Original PCA seeds ED. The native fit consumes canonical + ED matrices without
adding the PCA matrices again. Original `cov_ed` adds `I/sqrt(Nstrong)` to its
returned matrices; this source behavior is retained and prominently recorded.
ED's internal calibration amplitudes are separate from native mixture weights.
Native normalization, the squared scale grid and the point mass are applied once
under the explicit native settings. Native plain EM is independently checked
against original R likelihood/posterior primitives; it does not claim identity to
the upstream final mixture optimizer.

## Limits and failures

The default Java heap is 128 MiB (`--heap-mib`, allowed 64–16384). The default
input limit is 16 MiB, parent bundle limit 512 MiB, and entry limit 20000.
Override them explicitly with `--input-bytes`, `--bundle-bytes` and `--entries`.
The captured native limits can be supplied with `--native-limits JSON`; see the
[native guide](MASHR_NATIVE_WORKFLOW.md) for its schema and numerical restrictions.
The R preparation work/storage settings above are additional admission checks.

`--process-limits JSON` accepts this complete object:

```json
{
  "wall_seconds": 600,
  "cpu_seconds": 600,
  "rss_bytes": 4294967296,
  "processes": 16,
  "threads": 64,
  "log_bytes": 16777216,
  "workspace_bytes": 268435456,
  "entries": 20000,
  "poll_seconds": 0.1
}
```

The monitor samples owned processes by PID and creation time, including observed
descendants. Limits are sampled bounds, not an OS sandbox or a guarantee that
every short-lived descendant was observed. The changing workspace scan rejects
observed links/junctions while tolerating deleted temporary files. Immutable input,
runtime and output censuses retain strict complete-file/hash checks. A process
failure terminates the observed owned tree and verifies cleanup; unrelated
processes are not terminated. `--scratch-root` selects an existing, separate
verification-scratch parent. No global user/project YAML is inherited by the
native child (`--no-config`).

Input, output, private home, R home and completed setup report trees must be
disjoint. The scratch parent may contain these trees, but must not be inside any
of them. Invalid overlap is rejected before analysis output creation. A runtime
or setup file graph that changes after admission is an integrity failure (FAIL2).

| Outcome | What the user must do |
| --- | --- |
| **SKIP3 — dependent analysis/verification skipped** | Read the named missing or forbidden exact R, package, monitor, Java or artifact capability. Install only where permitted, or keep the dependent analysis skipped. Choose a separately validated profile for another OS/edition. |
| **FAIL2 — no result admitted** | Preserve all source inputs, partial downloads, candidate covariances and complete logs. Correct the named invalid input, checksum, numerical, convergence, resource, cancellation or finalization cause. Retry into new paths. |
| **ED_TERMINATION_UNPROVEN** | Inspect retained RDS, candidate matrices and numeric ED history. Reaching the original iteration limit does not establish convergence. Change only a scientifically justified request and rerun into new paths. |
| **Native weight/residual failure or rollback warning** | Read the complete child fit histories and diagnostics. A rollback retains the preceding accepted model under the declared likelihood rule; it does not establish a global optimum. |

An absent top-level completion marker means there is no admitted parent result.
Never treat partial output or a successful setup/preparation stage as success.
Checksum resealing alone cannot validate changed scientific results: parent and
child verification replay the declared calculations. SHA-256 provides integrity,
not authenticity.
