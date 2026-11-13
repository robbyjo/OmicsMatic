# Original Monocle3 1.3.1 prepared gene models

Read the [workflow guide](../MONOCLE131_MODELS_DEVELOPMENT.md) for input formats,
private runtime setup, strict selection rules, capacities, skipped analysis,
failure diagnostics and recovery. This is a standalone development component.
The full 250-paper implementation goal remains open; no new Monocle or Hmisc
whole-tool badge is granted here.

`VALIDATION.json` pins the implementation, complete archive layout and live
original-model qualifications. The archive restores 6,896 logical evidence
files from 1,059 unique byte objects. It includes complete synthetic inputs,
original Monocle source, model/RNG objects, coefficient tables, size factors,
process receipts, historical collector failures and original non-OK model
statuses. RDS objects were reloaded and checked during the original runtime
calls. Original runtimes and JDistlib are separately provisioned dependencies.

`AUTHOR_CODE_REVIEW.json` records the P085 author-code observations and source
hashes. It does not redistribute or execute the notebooks. The paper's active
Seurat assay, exact transformation, saved inputs and executed source commit
remain unresolved. Synthetic controls do not reproduce the study.

`Hmisc-CREDITS.md` describes the selected original Pearson primitive and its
GPL terms. Complete original primitive source and compiler receipts are in the
archive. The Windows reference DLL/compiler are not bundled. The paper's Hmisc
patch is unknown; this is not full Hmisc qualification.

Portable replay:

```powershell
python tools/literature/monocle131_models/verify.py `
  --java-bin 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' `
  --jdistlib-jar build/dependencies/jdistlib-all-0.10.2.jar `
  --out build/literature-review/monocle-runtime/new-model-controls
```

Alternatively use `--download-jdistlib` where permitted. Building/replaying
uses the exact adjacent `jdistlib-all-0.10.2.jar`; keep that filename and use
only SHA256 `8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81`.
The verifier checks every saved object and replays all 46 portable coefficient,
Pearson and refusal controls. It repeats no R fitting, reloads no RDS, performs
no study-data selection and authenticates no producer. Hashes establish
integrity, not signatures. Require `result_admitted=true` for an original RUN;
portable audits alone never admit one. Read warnings first and retain failures.
