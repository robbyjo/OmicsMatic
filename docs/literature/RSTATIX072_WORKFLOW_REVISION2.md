# rstatix 0.7.2 workflow: supervisor revision 2

**Use this revision for new original-rstatix analyses. A truncated worker report must never make unknown warning totals look like zero.** The upstream statistical engine remains the unchanged original rstatix 0.7.2 with the qualified private Linux/WSL R 4.5.3 provider. This revision adds no software-family credit, native method, paper review or claim of historical author-runtime equivalence. The full 250-paper review and missing-tool implementation goal remains open.

The earlier [workflow guide](RSTATIX072_WORKFLOW_DEVELOPMENT.md), its entry point and all published source/evidence hashes remain historical snapshots. The old supervisor correctly refused the reproduced interrupted result, but omitted `WARNINGS.md` and `MANIFEST.json` when worker-status JSON was truncated. Its raw failed run is retained in this revision's evidence. A second retained attempt completed the original R warning case but failed in supervision with `FileNotFoundError`; a deterministic control separately reproduced the monitor's pending-child-directory race. The revised monitor tolerates a temporary child disappearing during enumeration. A missing owned root, persistent capacity violation and forbidden symbolic links still fail.

## Run a complete example

Install only permitted prerequisites using the [private original-provider guide](RSTATIX072_DEVELOPMENT.md). A READY provider is not an analysis result. Analysis itself installs nothing. Native Windows original execution is skipped: use permitted native Linux Python/R, including through WSL.

```sh
python3 tools/literature/rstatix072_workflow_v2/make_example.py \
  --provider build/my-ready-rstatix-provider \
  --out build/my-new-rstatix-example
python3 tools/literature/rstatix072_workflow_v2/run.py \
  --request build/my-new-rstatix-example/request.json
```

Use NEW output paths. The example preserves all 24 synthetic subjects and all seven original operations. Existing version `0.7.2` / mode `SCRIPT` requests remain usable: invoke the revision-2 `run.py` with the original exact request and a NEW output directory. Declare complete logical inputs, every expected result, provenance/population, literal required exports/dependencies, explicit execution/trusted-code policies and resource capacities. Do not change a failed population to obtain a passing result. The worker and example R sources are byte-identical to the earlier qualified sources. All original function/formula/grouping, arguments, p-value rounding, conditions and RNG behavior remain original R behavior; native Java Levene stays separate.

## Read warnings and recover

Always inspect `analysis-status.json`, `WARNINGS.md`, `MANIFEST.json`, full `process.stdout` / `process.stderr`, `diagnostics/conditions.json` and the complete original RDS objects before using results. `SUPERVISOR_ERROR.txt` retains the controller traceback when a request, prerequisite, worker run or completion check raises an error. `result_admitted: true` proves the declared structural completion checks; it cannot infer scientific eligibility, author settings or biological validity. Trusted scripts run with account permissions, not in an OS sandbox or total CPU/RSS enclosure.

**Incomplete worker diagnostics:** `condition_counts_complete` is false, `original_warnings`, `original_messages` and `original_errors` are JSON `null`, and `warning_review_required` is true. `WARNINGS.md` says “unavailable”; it never substitutes zero for unknown totals. `WORKER_DIAGNOSTIC_ERROR.txt` explains the unreadable/missing report. Raw malformed JSON and partial results remain unchanged, including after an original process exits zero. Retain every file, inspect the named error, correct the actual cause, and retry a NEW output. Do not repair a saved report or relabel a partial analysis as complete.

**SKIP3 skips dependent analysis** if runtime, provider, input, literal export/package, permission to install/download or execution is unavailable/forbidden. Supply only permitted exact prerequisites and retry NEW paths. P054's printed `wilcoxon_test` remains unavailable in original 0.7.2; `wilcox_test` is not silently substituted. Historical author editions, designs and correction families remain unresolved where the primary paper does not establish them.

**FAIL2 admits no complete result.** All available partial results and raw conditions remain retained. Nonnegative integer condition totals are required; truncated, duplicate-key, nonfinite, nonobject, missing, negative, boolean and floating-point count reports refuse admission. Original `warn=2` semantics remain in effect. If the OS or a blocked path prevents writing a warning/status/inventory file, the controller attempts the other records independently and prints the named failure and required action to its console. A partial or unwritable inventory is not a complete manifest. Keep the complete console log; resolve the named filesystem prerequisite and retry a NEW directory. Filesystem failure cannot guarantee writable diagnostic files.

## Qualification scope

Qualification retains every failed attempt, the 248-call full original control population, direct original-R and predecessor comparisons, all 250 declared result files, 444 returned rows and 5,664 table cells. Full objects, attributes, arguments, original conditions and caller RNG states are compared at tolerance zero; opaque RDS bytes and formula-environment pointer identity are not claimed. It also exercises every previous workflow control plus malformed-worker-report and blocked-recording-path cases, the complete seven-operation public example, and deterministic monitor controls on Windows and Linux. Read `docs/literature/rstatix072-workflow-revision2/VALIDATION.json` and run the revision's verifier for exact admitted populations and source hashes.

The catalog's historical admissions remain unchanged until a separately verified routing update. This component adds **zero** tool-family or paper-review credit. Cell Ranger and Cell Ranger ARC remain excluded by user request.

```sh
python3 tools/literature/rstatix072_workflow_v2/verify.py \
  --out build/literature-review/monocle-runtime/my-new-revision2-verification
```

This reads the retained evidence and unchanged predecessor gates; it installs nothing and executes no new R analysis. Eight exact historical catalog metadata inputs may be replayed for earlier gates. Scientific code, runtime profiles, inputs, results and verifier definitions are never replaced by that replay. A first validation harness used the wrong comparison-JSON field name (`complete_original_calls` instead of the original comparator's `complete_calls`); the failed harness, its completed R comparisons and its diagnostics remain retained. Subsequent qualification uses the original field and the entire original population.
