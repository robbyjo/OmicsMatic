# Unqualified FUSION workflow development snapshot

**HANDOVER ONLY: this adapter is unfinished. No new tool-family credit, canonical
review, supported production command, release or complete-paper reproduction.**
Production `src/` stays at qualified commit
`833864a156d85578241f436e51128f16d0f97cf0`. The 16 exact draft files are saved under
`source/`, including the proposed dispatcher. `draft.patch` restores those same
changes; this directory is outside the Gradle production source/resource sets.
The qualified native allele/prepared-association component remains available.

Read [the current handover](../../../docs/literature/FUSION_WORKFLOW_HANDOVER_2026-11-22.md)
before resuming. `MANIFEST.json` inventories this snapshot, machine-specific
helper scripts and selected **excerpts** of synthetic development evidence.
These excerpts are not complete independently verifiable workflow captures.
Full captures, private R installations, compiled classes/JARs, downloaded
binaries and raw primary literature must be transferred separately.

From the repository root, check the saved bytes without executing R or installing
anything:

```powershell
python -B tools/development/fusion-workflow-20261122/verify_snapshot.py
git apply --check tools/development/fusion-workflow-20261122/draft.patch
```

To resume implementation in a clean checkout of the recorded baseline, inspect
the patch and apply it only after the check passes:

```powershell
git apply tools/development/fusion-workflow-20261122/draft.patch
```

If newer work changes the same files, reconcile the patch deliberately. Never
overwrite another owner's working files or refresh historical verifier hashes
to make old evidence appear current. The saved `helpers/` are exact historical,
machine-bound orchestration scripts; inspect paths, profiles and output-directory
assumptions before adapting them. They are not portable installation commands.

**Known FAIL2:** the Linux all-model training attempt ended with original R exit
1 because `tmp/train.param.txt` was absent at `weights.bslmm`. No complete result
was admitted. The request starts with BLUP, which also uses that function; the
failure does not establish that the later BSLMM model ran. The GEMMA subprocess
root cause is unresolved. Preserve this attempt and debug in NEW output.

**Compatibility boundary:** the successful top1/estimated-heritability probe
explicitly enabled the modern GCTA startup-probe shim. GCTA 1.94.1 returns 1 for
no arguments; the shim accepts only that exact diagnostic/banner at startup and
forwards all actual analysis arguments/statuses. This opt-in path still needs
positive/negative forwarding controls and prominent runtime warnings before
qualification. The legacy bundled GCTA segfaulted and is not a usable fallback.

**SKIP3:** a required exact permitted runtime/package/tool is missing or cannot be
installed: skip its dependent analysis and name the prerequisite and remedy.
**FAIL2:** invalid input, protocol, capacity, timeout or upstream failure: retain
logs/partial files, admit no result, fix the cause and retry NEW output. Preserve
NA/NaN/Inf, IDs, settings and original no-results. File hashes prove integrity,
not authenticity, statistical validity or independent refitting.

Original scripts are GPL-3.0, pinned to
[`gusevlab/fusion_twas` 9346c122](https://github.com/gusevlab/fusion_twas/tree/9346c1222bffbb34499fa7a8e23c1b701b55cb05).
Their unchanged LICENSE is included at
`source/src/main/resources/org/jlinalg/omics/fusion-9346c122/original/LICENSE`.
The current upstream revision is not an inferred historical paper edition.
