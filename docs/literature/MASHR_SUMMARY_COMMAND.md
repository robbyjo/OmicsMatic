# Verified native mash summary command

`org.jlinalg.cli.MashSummaryCli` is a standalone Java entry point for significance
summaries, posterior-mean sharing and optional nominal-gated marginal sign
products. It captures a complete native mash bundle and the summary request,
replays the native fit/prediction, calculates all summaries, and verifies the
captured calculations again before admitting a summary bundle.

This command extends the earlier [library checkpoint](MASHR_SHARING.md). It uses
the retained posterior tables after complete native semantic verification.
It needs Java and the validated development JAR; it runs no external R process
and inherits no global user/project YAML configuration. The canonical executable
under `build/cli` is unchanged. This standalone entry point is separate from the
root `mashr-tools` command. Study routes, remaining paper-role review and P024's
unresolved formal joint test remain open. The catalog still records 61 completed
scoped integrations and marks mashr missing.

## Run a complete synthetic example

From the repository root in PowerShell, use the artifact identified by
[the validation proof](mashr-summary/VALIDATION.json). All directories below must
be new. Preserve earlier attempts and choose new names when retrying.

```powershell
$summaryJava = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
$summaryProof = Get-Content -LiteralPath docs/literature/mashr-summary/VALIDATION.json -Raw | ConvertFrom-Json
$summaryJar = (Resolve-Path $summaryProof.artifact.path).Path
& $summaryJava -Xmx128m -jar $summaryJar mashr-tools --no-config --export-example build/summary-native-example-1
if ($LASTEXITCODE -ne 0) { throw 'Native example export failed.' }
& $summaryJava -Xmx128m -jar $summaryJar mashr-tools --no-config --input build/summary-native-example-1/fit-input --out build/summary-native-fit-1
if ($LASTEXITCODE -ne 0) { throw 'Native fit failed; read its WARNINGS.md.' }
& $summaryJava -Xmx128m -cp $summaryJar org.jlinalg.cli.MashSummaryCli --export-template build/summary-native-fit-1 --out build/summary-request-1
if ($LASTEXITCODE -ne 0) { throw 'Summary template export failed.' }
& $summaryJava -Xmx128m -cp $summaryJar org.jlinalg.cli.MashSummaryCli --native-bundle build/summary-native-fit-1 --input build/summary-request-1/input --out build/summary-result-1
if ($LASTEXITCODE -ne 0) { throw 'Summary not admitted; read stderr and WARNINGS.md.' }
& $summaryJava -Xmx128m -cp $summaryJar org.jlinalg.cli.MashSummaryCli --verify build/summary-result-1
if ($LASTEXITCODE -ne 0) { throw 'Summary verification failed.' }
```

If the identified JAR is absent, skip these commands until the exact artifact or
an independently validated build is provided. A version string alone does not
prove that a release contains this command. The example contains synthetic data.
For an original-preparation result, supply its verified `native-analysis` child
as `--native-bundle`; the parent R preparation bundle is a different contract.

To exercise marginal sign products on this same synthetic native fit:

```powershell
New-Item -ItemType Directory -Path build/summary-composite-request-1 -ErrorAction Stop | Out-Null
Copy-Item -LiteralPath examples/mashr-summary/composite-request/settings.tsv,examples/mashr-summary/composite-request/significance-conditions.tsv,examples/mashr-summary/composite-request/groups.tsv,examples/mashr-summary/composite-request/nominal-p.tsv -Destination build/summary-composite-request-1
& $summaryJava -Xmx128m -cp $summaryJar org.jlinalg.cli.MashSummaryCli --native-bundle build/summary-native-fit-1 --input build/summary-composite-request-1 --out build/summary-composite-result-1
if ($LASTEXITCODE -ne 0) { throw 'Composite summary not admitted; read WARNINGS.md.' }
& $summaryJava -Xmx128m -cp $summaryJar org.jlinalg.cli.MashSummaryCli --verify build/summary-composite-result-1
if ($LASTEXITCODE -ne 0) { throw 'Composite summary verification failed.' }
```

The example nominal P values are explicit synthetic controls. **Use actual
nominal P values for real data. They are not derived from posterior LFSR.**

## Declare the complete request

Every file is UTF-8 TSV. Requests are flat directories containing exactly the
files used by the selected mode; keep README files outside them.

`settings.tsv` has the header `key<TAB>value` and these required keys:

| Key | Values |
| --- | --- |
| schema_version | `1` |
| lfsr_threshold | Explicit probability, such as `0.05`. |
| significance_boundary | `strict` or `inclusive`, for significance selection/counts. |
| sharing_factor | Probability in `[0,1]`, such as `0.5`. |
| sharing_transform | `identity` or `absolute`. |
| composite_mode | `off` or `marginal_sign_product`. |

`significance-conditions.tsv` has the single header `condition` followed by the
unique conditions to count, one per line. Each name must match the native
condition identity exactly. The template explicitly selects all conditions.
Count selection can differ from the all-condition pairwise output. Pairwise
selection always uses the upstream strict `LFSR < threshold`, even when
significance counts explicitly use an inclusive boundary.

For `composite_mode=marginal_sign_product`, additionally supply settings
`nominal_p_threshold`, canonical nonnegative integer `minimum_nominal`, and
`score_threshold`. The gates use strict nominal `P < nominal_p_threshold` in at
least `minimum_nominal` grouped conditions, followed by strict product
`> score_threshold`. Groups can overlap; conditions within a group cannot repeat.

`groups.tsv` has `group<TAB>condition`, one membership per row. Group order is
the order of first appearance. `nominal-p.tsv` has `feature` followed by every
native condition in its exact order, then every native target feature in its
exact order. Feature IDs such as `0001` remain strings. All nominal values must
be finite probabilities. Unknown names, reordered rows/columns, missing values,
duplicate memberships and unused files fail admission.

## Read and verify the results

Only top-level `COMPLETED.json`, exit0 and successful `--verify` admit the scoped
result. The captured `native/` bundle has its own narrower completion marker;
it does not admit the summary. `session.json` describes an unadmitted producer
stage, while the top-level marker records successful final admission.

| File | Contents |
| --- | --- |
| results/significance.tsv | Every target's minimum selected LFSR, significant-condition count and selection state. |
| results/significant-ids.tsv | Selected feature IDs ranked by minimum LFSR, with stable input-order ties. |
| results/pairwise.tsv | Every ordered condition pair; selected/shared/undefined counts, status and fraction. |
| results/marginal-sign-products.tsv | When enabled: each feature/group's product, log product, state, nominal count, eligibility and threshold decision. |

**An empty selection or an undefined selected ratio is `NA`, not zero sharing.**
Pairwise ratios and both magnitude bounds follow the literal upstream strict
definition. Selected zero/zero ratios remain in the denominator and make the
estimate undefined. Factor zero retains source division behavior; factor one
keeps strict bounds. Fractions are mirrored from the upper triangle.

**Marginal sign products are not joint probabilities, Pr(nonzero), or proof of
matching effect directions.** `EXACT_ZERO` has log product `-Inf`.
`UNDERFLOW_TO_ZERO` retains a finite log product. Eligibility and threshold
decisions remain separate. Unsupported `formal_composite`, `joint_nonzero` and
`posterior_samples` requests produce SKIP3; no sign-product fallback is selected.

Verification needs only the captured bundle, Java and permitted scratch space.
It replays the complete native source and every summary. Identities, counts,
fractions, rounded products, discrete states and choices match exactly. Only
finite nonpositive log products use absolute `1e-12` plus relative `1e-12`
tolerance. Native replay retains its separately documented numerical tolerances.
Verification leaves the source unchanged, including on failure. Successful and
failed summary scratch is retained; its path is printed in stdout/stderr. Place
Java's temporary directory outside source/request/output evidence trees.

## Limits and recovery

Print the complete default ceiling schema with:

```powershell
& $summaryJava -cp $summaryJar org.jlinalg.cli.MashSummaryCli --default-limits
```

Supply that complete JSON object through `--limits FILE` when explicitly changing
permitted capacities. It includes native replay ceilings, summary dimensions,
cells/work/owned-storage bounds, request bytes, complete output bytes and entry
count. A captured bundle cannot raise the current verifier's ceilings; supply
sufficient explicit current ceilings when verifying a larger admitted request.
Storage planning must also fit half the current Java heap. Capture and result
writes respect the complete output budget; no prefix is admitted. These are
admission limits, not an operating-system sandbox.

**SKIP3:** the requested method is unavailable. Keep the dependent analysis
skipped and choose an explicitly supported mode or a separately validated method.
**FAIL2:** invalid input, integrity, capacity, numerical, cancellation or
finalization failure. Preserve inputs, partial output, rejected completion
markers and diagnostics. Correct the named cause or explicitly raise permitted
limits/heap, then retry into a new output directory. Empty/undefined cells and
underflow states generate prominent warnings; they must not become biological
nonreplication claims. SHA-256 provides integrity, not authenticity, biological
accuracy or whole-paper reproduction.
