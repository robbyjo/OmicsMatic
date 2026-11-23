# Original SCENIC+ unpaired data preparation

This Java adapter calls original SCENIC+ v1.0a2
`process_non_multiome_data` using RNA AnnData and an original cisTopic object with
a selected fitted topic model. It imputes accessibility and forms pseudo-multiome
metacells within shared annotation groups. RNA and ATAC barcodes need not overlap.
This stage joins the separately tested prepared network core; motif/resource
preparation, global dispatch and catalog admission remain unfinished.

## Warnings and actions

- **Use only trusted original cisTopic pickles.** Deserialization executes code.
  The adapter preserves the original format and does not authenticate the fit
  history merely by checking model type or component axes.
- **Private academic runtime and captures:** the user accepted the original
  license for an unmodified private internal academic runtime. Do not redistribute
  the vendor source/runtime, fitted objects, caches or source-bearing traces.
  Permission for a new destination or recipient is required before transfer.
- **Exit 3 / SKIP3:** restore the exact permitted runtime/input named in the
  message, or retain the skipped dependent analysis. This caller does not install
  substitute editions automatically.
- **Exit 2 / FAIL2:** no complete result. Preserve raw, partial and failed output;
  read `FAILURE.md`, `analysis-status.json`, private `worker/report.json` and its
  stdout/stderr; correct the named cause and retry a NEW directory.
- **Source limitations:** per-group cells-per-metacell mappings raise `TypeError`
  in the unchanged original version. Use an explicit supported integer or skip.
  Annotation labels containing the selected separator are refused because the
  original API truncates returned group metadata. Choose an absent separator;
  labels are never rewritten.
- **Scientific choices:** explicitly choose `.raw` or processed `.X`. Missing raw
  data never falls back to processed data. No normalization or annotation is
  inferred. Assay-only groups are omitted by the original shared-group rule;
  the summary records them and retained cell counts.

## Request and invocation

Run on Linux using a complete source-built project JAR. The pinned private profile
uses Python 3.11.8, SCENIC+ v1.0a2 commit
`86fa25e6819919e2a824c14f7fd0d6e01e257ae9`, 228 installed distributions and the
231 metadata-origin bindings exposed after imports. Original 41 SCENIC+ Python
members and 73 Python members in four pinned Git dependencies are checked before
and after execution. See [private-runtime handover](HANDOVER_SCENICPLUS_2026-11-23.md)
for retained local paths and licensing context.

```json
{
  "schema_version": 1,
  "mode": "RUN",
  "profile": "scenicplus-1.0a2-prepare-v1",
  "operation": "UNPAIRED",
  "python": "/absolute/path/private/environment/bin/python",
  "out": "/absolute/path/NEW-preparation",
  "inputs": {
    "rna": "/absolute/path/RNA.h5ad",
    "cistopic": "/absolute/path/TRUSTED-selected-model.pkl"
  },
  "options": {
    "group_key": "cell_type",
    "use_raw": false,
    "nr_metacells": 4,
    "cells_per_metacell": 5,
    "separator": "|",
    "scale_factor": 1000000
  }
}
```

```bash
java -cp /absolute/path/omicsmatic-0.4.0.jar \
  org.jlinalg.cli.ScenicPlusPrepareCli --request request.json
java -cp /absolute/path/omicsmatic-0.4.0.jar \
  org.jlinalg.cli.ScenicPlusPrepareCli --inspect /absolute/path/preparation
```

Paths resolve relative to the request. Every output must be new. `use_raw` is
required. `nr_metacells` is a positive integer, `null` (or omitted) for the original
automatic rule, or an object naming exactly the shared groups, for example
`{"B cell": 4, "T cell": 6}`. Automatic counts preserve the original Python
rounding rule. `cells_per_metacell` is a positive integer, default 10. Scale is a
positive int32, default 1,000,000. The API's fixed seed for each metacell ordinal
is preserved; no user seed is exposed. The returned API does not expose sampling
membership and this adapter does not reconstruct it.

## Outputs and fresh comparison

`worker/out/prepared.h5mu` is the original returned MuData serialized unchanged.
`expression.tsv` and `accessibility.tsv` contain complete returned matrices, and
`scientific.jsonl` records complete typed values for both modalities and their
cell/feature metadata plus joint cell metadata. `field-ledger.tsv` records finite
numeric fields. `SUMMARY.json` records input/output shapes, shared/omitted groups,
retained cells and zero family/review credit. `invocation.json` records the call.
Warnings remain in the private worker report and the top-level warning files.
The prepared matrices can be explicitly supplied to the network adapter; no
automatic whole-pipeline invocation is claimed.

```json
{
  "schema_version": 1,
  "mode": "VERIFY",
  "analysis": "/absolute/path/preparation",
  "out": "/absolute/path/NEW-verification"
}
```

Use the same Java command with this request. `REPLAY` has the same fields and
also performs fresh original computation; neither mode merely hashes the saved
results. They compare complete typed scientific output, both numerical TSVs,
summary, invocation and runtime closure with the read-only parent. H5MU container
bytes are inventoried but are not used as a substitute for scientific comparison.
`--inspect` checks the saved inventory and exact producer; it executes no science.
All hashes establish integrity, not execution authentication.

## Resource scope

Optional `limits` have the same capture/process ceilings as the prepared network
adapter: input 2 GiB, member 2 GiB, output 8 GiB, 50,000 files, 1,800 seconds,
1,000,000 cells or genes, 10,000,000 regions and 100,000,000 matrix values by
default. Ceilings are input 64 GiB, member 32 GiB, output 256 GiB, 1,000,000 files,
86,400 seconds, 10,000,000 cells/genes, 100,000,000 regions and 10 billion matrix
values. These are checked processing/capture limits, not a hard RAM quota.
HDF5 soft/external/virtual data are refused. The trusted pickle format itself is
executable and is not a sandbox. Complete recorded runtime locations are checked
with a 16 GiB / 100,000-file bound, excluding bytecode caches. Process ownership
uses observed start identities; this is not OS containment or an OS-loader
closure claim.
