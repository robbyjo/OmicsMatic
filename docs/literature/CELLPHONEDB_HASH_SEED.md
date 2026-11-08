# CellPhoneDB: corrected Python hash-seed launcher

**Use runner revision2 when controlled Python hash ordering is required.** The
first-generation runner used Python `-I`, which ignores `PYTHONHASHSEED`. Its
request field therefore did not control interpreter hashing. Retained runs and
their original validation records have not been changed or relabelled.

The additive runner is `org.jlinalg.cli.CellPhoneDbWorkflowCliV2`. It starts the
worker with `-B -P -s`, clears inherited Python configuration, and supplies the
explicit seed. Before importing NumPy, pandas or the original scientific methods,
the worker checks the interpreter flags and writes `worker/python-startup.json`.
Java checks that receipt before any batch is admitted, independently recomputes
the six seed-zero hash probes, and checks the receipt again before final admission.
The original CellPhoneDB 5.0.1 functions, dependency versions, input schema,
scientific options, data-only observations and native numerical audits are reused.

This is an additive development artifact. The canonical published executable and
the existing command dispatcher are preserved. Use the explicit revision2 class
below; running the first-generation class does not acquire this correction.

## Run the corrected version

Build the revision2 artifact using the checked source profile and locally present
parent development artifact:

```powershell
python -B tools/literature/build_cellphonedb_hash_seed.py --out build/literature-review/cellphonedb-runtime/hash-seed-build-NEW
```

Read the new build directory's `COMPLETED.json`. It must say
`PASS_COMPILED_CELLPHONEDB_HASH_SEED_DEVELOPMENT_ONLY`; compilation alone does not
validate a new runtime or admit an analysis. The exact artifact path and SHA-256
are recorded there. If a parent artifact is unavailable, this build fails;
do not substitute a different JAR or update saved hashes.

Export the exact preparation kit with the new artifact:

```powershell
java -cp PATH_TO_REVISION2.jar org.jlinalg.cli.CellPhoneDbSetupCliV2 --export NEW_KIT_DIRECTORY
```

Read the exported `SETUP.md`. Install the pinned runtime on Linux x86_64 only when
permitted. An existing successful exact 5.0.1 setup receipt can be reused without
reinstallation. Set the existing request's `python_hash_seed` to an integer from
0 through 4294967295, retain all explicit scientific options, point `runtime` to
that receipt, and choose a **new** `out` directory. The existing default is 0.

Run inside Linux, including WSL, using Linux paths in the request:

```sh
java -cp /absolute/path/REVISION2.jar org.jlinalg.cli.CellPhoneDbWorkflowCliV2 --request /absolute/path/request.json
```

Require **exit 0 and `analysis-status.json` `result_admitted=true`**. Read
`WARNINGS.md`, `worker/python-startup.json`, `native-audit.json`, input/source
dispositions and all original process logs. The final status records revision2,
the requested seed, the startup audit and the exact receipt hash. Every requested
case must pass before the whole batch is admitted.

## Warnings and recovery

**SKIP3: required runtime, platform, edition or installation permission is
unavailable. Skip the dependent analysis.** Read the named error and setup
warnings. Prepare the exact permitted Linux x86_64 runtime using the separate kit,
then retry new output paths. If installation is forbidden, leave analysis skipped.
RUN never downloads or installs software. Reported 5.1/5.0.0/2.1.4 are not replaced
with 5.0.1.

**FAIL2: no result from the requested batch is admitted**, including earlier
completed cases. Preserve all original and provisional files. Correct the named
request, startup, source, scientific-method, audit, timeout or capacity problem,
then retry new paths. Never repair an old receipt or change verifier hashes to
admit it. A missing, changed or invalid startup receipt fails admission.

Hash ordering and NumPy random generation are separate. Full-method subsampling
still seeds from entropy in the original source, and parallel null calculations
are not promised byte parity across runs. Nonzero Python seeds are verified by
the bound interpreter's effective startup and independent launch baselines; a
native nonzero hash-secret implementation is not claimed. Seed-zero probe hashes
have an additional independent native SipHash13/Unicode check.

Source-generated debug pickles remain opaque outputs and are never deserialized.
No scientific functions are replaced, no identifiers are repaired, and no
normalization, edition fallback or paper parameters are inferred. Supervision
bounds wall time, output and owned process cleanup; it does not impose hard OS
RAM or CPU limits. The scoped catalog remains 85 new integrations (88 including
three existing adapters), with 251 cataloged tools still missing. This correction
adds no tool credit and does not complete the overall 250-paper goal.

## Validation record

See [the revision2 validation](cellphonedb-hash-seed-development/VALIDATION.json)
and its self-contained `controls.zip`. These retain the fresh 19 original-method
cases, exact 17 serial returned/TSV/RNG comparisons, entropy/parallel checks, three
actual JVM audits, repeated boundary-seed launches, corrupted-receipt rejections,
unsafe startup controls, installation skips and failed development attempts.
The first-generation proofs and the
[historical finding](PYTHON_HASH_SEED_KNOWN_ISSUE.md) remain unchanged.

The retained failed attempts include a harness error: a deliberately bytecode-enabled
negative control wrote one cache directory and one 12 KB bytecode file into the
shared runtime. Strict admission rejected the changed tree. Both added members
were preserved in a checked quarantine; no original member changed, and the full
tree was restored to its original inventory without refreshing receipt hashes.
The repeated control directs bytecode into a private cache. All eight public
commands and all twelve producer protocols then passed their expected success,
skip or failure checks on new output paths.
