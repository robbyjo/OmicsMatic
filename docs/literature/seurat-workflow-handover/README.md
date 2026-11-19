# Original Seurat workflow prototype snapshot

**Exploratory synthetic runs only. The broader production Java adapter is
unfinished.** This packet awards no catalog or family credit.

`prototype-evidence.zip` preserves the exact two R prototype attempts,
synthetic counts/metadata, original RDS objects, graphs, embeddings, cluster
tables, stdout/stderr, session information, runtime probe, source URL/hash
index and historical developer helpers. The full downloaded upstream source
files, installed runtimes and private article paragraphs are excluded.
`SNAPSHOT.json` indexes every archived payload and pins the archive bytes.

The first attempt failed during report serialization after exporting one
profile. Its partial outputs and failure explanation remain under
`prototype-1/`. In `prototype-2/`, all four per-profile records report
`PASS_ORIGINAL_PROTOTYPE`; `RESULTS.json` agrees. This demonstrates successful
original-R exploration on the recorded synthetic input, not independent
numerical validation, a qualified wrapper, historical author reproduction or
biological analysis. The saved scripts contain machine-specific paths and
branch checks and should not be run blindly.

Verify from the repository root with Python 3.11+, without R or installation:

```powershell
python -B tools/literature/seurat_workflow_prototype/verify.py
```

It checks exact archive membership, hashes, saved outcome agreement and the
retained failure. It does not execute R, unpickle/read RDS, authenticate the
original host, or establish model correctness. Read the
[handover](../HANDOVER_2026-11-19.md) for the next implementation steps,
source-behavior pitfalls, warning policy and local assets to retain.
