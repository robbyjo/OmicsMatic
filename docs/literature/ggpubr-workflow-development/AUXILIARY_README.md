# Additional frozen-verifier evidence

This appendix was added after the original scientific checkpoint. The base VALIDATION.json, source pins, complete original results and five base archive parts remain unchanged. VERIFIER_BOUNDARIES.json records one passing baseline and four expected refusals. The additional verifier archive retains all control records and lossless representations of the complete actually tested negative ZIP inputs.

The missing-plot and forged-admission ZIPs reuse unchanged byte slices of the full base archive plus literal bytes. The delta recipes reconstruct their exact recorded bytes and every original part hash, without rendering, recompressing, altering scientific source or depending on a zlib version. The private materialized negative archives were also retained; no scientific input/result byte is discarded.

Run both checks from the checkout with NEW output paths:

```bash
python3 tools/literature/ggpubr_workflow/verify.py --rescan-sources --out build/literature-review/monocle-runtime/NEW-ggpubr-main-check
python3 tools/literature/ggpubr_workflow/verify_boundaries.py --out build/literature-review/monocle-runtime/NEW-ggpubr-boundary-check
```

The verifier reassembles archive parts internally; do not unzip individual parts. Missing evidence returns SKIP3 and skips dependent verification. Contradictions return FAIL2 and admit no qualification. Retain the complete parts, inventories, raw conditions/errors, all earlier failed attempts and required-action reports. These checks execute no new original R analysis or installation. The frozen development catalog inputs precede later catalog admissions; use a later cumulative catalog gate for its documented metadata-only historical replay.
