# Historical worktree overlays retained for handover

**This archive preserves earlier drafts. It is not the active implementation,
a runnable distribution or a scientific qualification. Do not extract it over
the current checkout.** Resume with the [current handover](../HANDOVER_2026-11-19_FINAL.md).

`OVERLAYS.json` inventories 2,248 nonignored dirty files from 21 preserved
worktrees at main commit `29486e3f7bf83f534324f21c2d2bd917ad35b134`. All 21
worktree HEADs are already ancestors of that main. The inventory records
original paths, HEADs, SHA-256, byte counts and Git blob identifiers.

- 1,866 file byte sequences exactly match main and remain available in Git.
- `historical-overlays.zip` saves all other 382 file variants verbatim, under
  `worktrees/wNN/<original-relative-path>`: 288 newline-only and 94 different
  variants. The aliases map to original worktrees in `OVERLAYS.json`.
- Sixty of the 94 different variants match earlier main-history blobs at the
  same path. Thirty-four did not match that lookup; preservation does not
  assert that their content was absent from all other archives or paths.

CRC and every archived payload's SHA-256/length were checked. All 2,248 original
file hashes were rechecked without changing their worktrees. The archive also
preserves older MALT fixture ZIPs whose raw reference, query and output
payloads already occur unchanged in the current public fixture; the current
fixture additionally records the later source discrepancy. Retained older
catalogs and source files must not replace the current qualified versions.

Verify this compact archive without extracting or executing its payloads:

```powershell
python -B docs/literature/handover-20261119/verify.py
```

The verifier needs Python 3.11+ and only the standard library. It checks the
inventory/archive hashes, exact member set, CRC and all 382 saved payloads.
It does not claim to rerun scientific models or restore private prerequisites.
SHA-256 provides integrity, not a signature against coordinated replacement.

To inspect an old draft, verify first and copy only the explicitly selected
member into a NEW scratch directory. Compare it with its original HEAD and
current main. Validate any useful change independently before proposing it
for current production code. No bulk overlay or automated restore is provided.

Ignored builds, runtimes, full-text XML/PDF and private evidence are outside
this archive. Follow the handover's separate transfer checklist. The actual
working copies remain intact. These preservation files are not deployed by
the website workflow and create no release.
