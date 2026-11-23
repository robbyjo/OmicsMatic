# SCENIC catalog checkpoint

This packet records one admitted upstream family and five reviewed paper roles.
Read [the checkpoint guide](../SCENIC_CATALOG_CHECKPOINT_2026-11-23.md),
`STATUS.json`, and `READBACK.json` for the exact scope and limits.

`FILES.json` maps 294 logical paths to 158 unique byte sequences in
`evidence.zip`, stored as `objects/<sha256>`. It retains the first failed browser
selector and first preview, the corrected current-source preview and actual
installed checks, two-host tests and refusals, fresh primary-source checks and
the known website failure. `OMISSIONS.json` lists private primary XML and isolated
browser profiles excluded from publication. Full source articles, original
author data, executable JARs and installed runtimes are not in this packet.

From the repository root:

```bash
python tools/literature/verify_scenic_catalog_checkpoint.py
```

Optional `--jar` checks the exact retained executable; `--cache` rechecks all
250 frozen primary XML files. The reader reconciles the current catalog and
scientific source against the unchanged original workflow packet. It supplies
exactly four archived predecessor catalog files to that historical reader,
retaining its original source and hashes. This input scope is explicit in every
verification receipt. It performs no new original statistical fit and supplies
integrity, not authorship authentication. A full Git history containing the
recorded predecessor commit is required for predecessor-byte checks.

Current totals are 155 scoped families (29 native and 126 upstream), 1,702
reviewed and 1,020 pending uses, 250 papers and 2,722 positive observations.
P070's reported pySCENIC 0.10.3 analysis stays SKIP3 under the available 0.12.1
profile. SCENIC+, P153 review and the full toolbox objective remain unfinished.

`PUBLICATION.json` records the admission commit and exact public-byte readback.
`CLEANUP.json` records removal of only the three closed isolated browser profiles;
all screenshots, consoles, failed checks, source, exact JARs and runtimes remain.
`finalization.zip` and `FINALIZATION-FILES.json` retain exact helper sources and
publication, successful deployment, live-page and cleanup receipts. Browser
profile contents, per-file private cache hashes and worktree inventories are
excluded. Both literature reports still return404 on Pages; the33 existing
scientific-citation validation failures remain unresolved.
