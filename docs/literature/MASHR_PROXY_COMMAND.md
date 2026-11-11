# Run the reported independent marginal composite proxy

`org.jlinalg.cli.MashSummaryCli` now supports the explicitly named
`independent_marginal_proxy` mode on a complete verified native mash posterior
bundle. It captures the source and request, replays the native model, calculates
every requested score, and verifies the complete result before admission.
Standalone `--verify` repeats both native and summary replay without reopening
the producer's original paths. Execution requires Java only.

**The independence assumption is required.** This reported dreamlet calculation
multiplies marginal sign-based proxies. It does not integrate the joint mash
posterior, establish biological absence/specificity, guarantee a bound under
arbitrary dependence or produce a calibrated frequentist P value. See the
[paper-role audit](mashr-composite/role-audit.json) and
[native method guide](MASHR_COMPOSITE.md) for source semantics and unresolved
paper inputs. The full dreamlet package and the papers' exact binary editions
are not certified by the standalone original-function comparisons.

## Run the complete synthetic example

From the repository root in PowerShell, select the candidate recorded in the
[public-command validation](mashr-proxy-command/VALIDATION.json):

```powershell
$proxyJava = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
$proxyProof = Get-Content -LiteralPath docs/literature/mashr-proxy-command/VALIDATION.json -Raw | ConvertFrom-Json
$proxyJar = (Resolve-Path $proxyProof.artifact.path).Path
if ((Get-FileHash -LiteralPath $proxyJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $proxyProof.artifact.sha256) { throw 'Candidate hash mismatch; retain the file and inspect the validation record.' }

& $proxyJava -Xmx128m -jar $proxyJar mashr-tools --no-config --export-example build/mashr-proxy-native-example-1
if ($LASTEXITCODE -ne 0) { throw 'Native example export failed; inspect its diagnostic.' }
& $proxyJava -Xmx128m -jar $proxyJar mashr-tools --no-config --input build/mashr-proxy-native-example-1/fit-input --out build/mashr-proxy-native-result-1
if ($LASTEXITCODE -ne 0) { throw 'Native fit failed; skip dependent summaries and inspect WARNINGS.md.' }

New-Item -ItemType Directory -Path build/mashr-proxy-request-1 -ErrorAction Stop | Out-Null
Copy-Item -Path examples/mashr-proxy/request/*.tsv -Destination build/mashr-proxy-request-1 -ErrorAction Stop
& $proxyJava -Xmx128m -cp $proxyJar org.jlinalg.cli.MashSummaryCli --native-bundle build/mashr-proxy-native-result-1 --input build/mashr-proxy-request-1 --out build/mashr-proxy-result-1
if ($LASTEXITCODE -ne 0) { throw 'Proxy analysis was not admitted; inspect its prominent diagnostic and WARNINGS.md.' }
& $proxyJava -Xmx128m -cp $proxyJar org.jlinalg.cli.MashSummaryCli --verify build/mashr-proxy-result-1
if ($LASTEXITCODE -ne 0) { throw 'Replay failed; retain the bundle and named scratch directory.' }
```

Every output path must be new. A successful result has exit0 and a valid
top-level `COMPLETED.json`; require successful `--verify` before dependent
analysis. Read `WARNINGS.md`, `native/WARNINGS.md`, `session.json` and the complete
result table. The example uses synthetic A/B data, including empty-pattern
identities. It does not reconstruct the papers' cohorts or disease contrasts.

The development JAR is identified by path and SHA-256 in the proof; it is not a
new release. If unavailable, skip the example until a validated current-source
artifact is built/provided. Older released0.3.7 executables lack these commands.
The canonical `build/cli` executable is unchanged. A new isolated source build
uses `tools/literature/mashr-development.init.gradle` and a fresh
`-PmashrDevelopmentOut=...` directory; its artifact needs its own validation.

## Prepare a request for real data

Use `--export-template VERIFIED_NATIVE_BUNDLE --out NEW_DIRECTORY` to export
the exact native condition list. The exporter initially selects summaries with
composites off. Edit the request deliberately before analysis. In proxy mode,
the input directory must contain exactly these four UTF-8 TSV files:

| File | Required content |
| --- | --- |
| `settings.tsv` | `key/value` header and all nine keys shown in the example: base summary settings, mode, assumption, score threshold and score boundary. Unknown, repeated or unconsumed keys fail. |
| `significance-conditions.tsv` | `condition` header, then unique exact native names for significance counts. This separate selection does not define the proxy patterns or change strict pairwise source selection. |
| `patterns.tsv` | `pattern/mode` header. Declare every unique pattern once, in desired output order, with `all` or `at_least_one`. At least one pattern is required. |
| `selectors.tsv` | `pattern/role/condition` header. Each row names a declared pattern, `include` or `exclude`, and an exact native condition. Rows retain occurrence order; repeated selectors are retained and reported. |

For example, to request an effect in A and absent in B, declare `only-A/all` and
give `only-A/include/A` and `only-A/exclude/B` selector rows, separated by tabs.
Unlisted conditions are ignored. There is no implicit complement, regex
expansion, name/version normalization or inferred group membership. A pattern
with no selector rows is valid: empty ALL inclusion contributes1, empty
AT_LEAST_ONE contributes0, and empty exclusion contributes1. Header-only
`selectors.tsv` is valid. An empty pattern family is rejected.

`proxy_assumption` must be `independent_marginal_proxies`. `score_boundary` must
be `strict` or `inclusive`; `score_threshold` is an explicit number in [0,1].
The input scale is the captured native LFSR: `p=1-LFSR`. Complete finite
posteriors are required, with missing probabilities rejected. Upstream
effect/SE imputation remains the separately recorded native input policy; the
proxy command does not interpret an untested effect as biological absence.

The source calculations are `prod(p[include])*prod(1-p[exclude])` for ALL, or
`(1-prod(1-p[include]))*prod(1-p[exclude])` for AT_LEAST_ONE. Repeated selectors
multiply repeatedly. Include/exclude overlap retains the literal source proxy
and is reported; it does not describe a logically consistent joint event.

P024 reports all eight disease contrasts and score>=0.05. Its inspected
downstream code separately removes shared genes at>=0.01 before correlations;
the composite-producing CSV lineage remains unresolved. P104 explicitly uses
>0.5 and reports a stricter0.8 cutoff whose exact equality boundary is not
established by the inspected methods/caller. Supply actual matrices, selectors
and unreported choices; these are not automatically reconstructed.

## Interpret outputs and failures

`results/independent-marginal-proxies.tsv` retains every feature/pattern pair,
including source score, included/excluded source factors, stable log diagnostic,
state, the explicit threshold decision, missing/rounded-marginal/repeated-selector
counts, overlapping conditions and numerical loss. `session.json` records the
method, assumption, scale, missing policy, boundary and diagnostic totals.

**Source scores define threshold decisions.** Stable logs diagnose arithmetic
loss without replacing the source score. States distinguish exact zero,
source rounding to zero, underflow to zero and defined scores. A rounded zero
with a finite log is retained. Inspect all diagnostics and empty/undefined
pairwise sharing cells; NA sharing is not zero or biological nonreplication.
Replay requires exact identities, scores, factors, states, counts and decisions;
only finite nonpositive log diagnostics permit absolute1e-12 plus relative1e-12
tolerance. SHA-256 establishes integrity, not authenticity.

**SKIP3 — ANALYSIS SKIPPED:** unnamed `formal_composite`, joint-posterior and
sample-based methods remain unsupported. Skip that requested dependent analysis.
Choose the explicit proxy only if it is the intended scientific method, or
provide a separately validated method. No automatic proxy fallback is used.

**FAIL2 — NO RESULT ADMITTED:** invalid identities, malformed UTF-8, probabilities,
selectors/settings, integrity, process, capacity, cancellation or finalization
errors prevent admission. Preserve inputs, partial evidence and diagnostics;
correct the named cause and retry with new paths. Failed replay names retained
scratch and leaves its source bundle unchanged. A failed final census revokes
the completion marker. Do not consume a partial table as successful output.

Use `--default-limits` for current ceilings and `--limits JSON` to request permitted
changes. Complete inputs, selectors, pattern metadata, family work/storage,
capture bytes and result bytes are admitted before success; owned storage is
constrained to half the Java heap. Empty-pattern workloads still consume family
capacity. Captured limits cannot raise the current verifier's ceilings. Limits
and cancellation are application checks, not an OS sandbox.

## Evidence scope

The command validation records all477 candidate tests per JVM, including37 new
command tests and the previous30 original-function cases, on Windows Java21/25
and Linux Java21 with128MiB heaps. Public protocols additionally cover exact
original-source score/threshold comparisons, copied cross-platform bundles,
producer-path independence, actionable SKIP3/FAIL2 and verification of prior
v1 summary bundles. Synthetic controls do not certify biological accuracy or
whole-paper reproduction. Catalog promotion and study registration remain
separate work.

Only `MashSummaryCli.java` was extended among the prior scientific sources.
The old source snapshot, failed class-metadata expectation, previous proofs and
artifacts are retained. Old current-source pin checks describe their historical
snapshots; use the new public-command proof for this extended source. Native
fit, covariance preparation, sharing and composite-math sources are unchanged.
