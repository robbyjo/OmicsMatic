# GCTA model-export evidence

This is a development/model-export qualification checkpoint. Read the
[checkpoint and warnings](../GCTA_MODEL_CHECKPOINT_2026-11-20.md) first.
mtCOJO, older editions, global registration and catalog admission remain open.

`PROFILE.json` pins the declared current source inputs and exact normal producer.
`INVENTORY.json` maps every retained logical evidence path to its byte length
and SHA-256. `evidence.zip` stores one byte-exact blob per unique SHA-256 under
`blobs/`. Duplicate fixture/input/parent copies share storage without changing
any restored bytes or overwriting their original files. The logical inventory
keeps all available successful, failed, partial and superseded states separate.

The archive contains selected synthetic outputs, complete captured original
exports used in regression controls, independent covariance math, build/test
receipts, mutated inverse controls, process/termination observations, pinned
upstream source, and historical orchestration scripts. No participant data,
original EXE/DLL/runtime archive, Gradle cache or 196 MB producer JAR is included.
The complete private history, primary full text, old owners, runtimes and JARs
still require a separate verified machine transfer.

The reader checks saved bytes and receipts without launching Java, installing
software or executing a model:

```powershell
python -B tools/literature/gcta_workflows/verify_models.py --out build/gcta-model-saved-new
```

Add `--jar C:/absolute/path/to/the/exact/captured/omicsmatic-0.4.0.jar` for
producer integrity. Omission records producer SKIP3, while a wrong supplied JAR
is FAIL2. Never rebuild and substitute a new hash for a missing old producer.
Use `--restore` to restore all raw logical files under the new receipt
directory's `evidence` child. Files are never executed. Research scripts and
requests retain old machine-specific paths: inspect and adapt them to NEW
outputs before any reuse.

Baseline regression failures, the first Java-target build failure, the earlier
Fisher mismatch, setup/reader assertion mismatches and actual exhausted original
fits are retained. Corrected readback records distinguish a harness's guessed
error substring from the actual scientific refusal; old logs are not rewritten.
SHA-256 provides integrity, not a signature. A saved-reader pass is not a fresh
analysis, independent original refit, biological validation or catalog admission.
