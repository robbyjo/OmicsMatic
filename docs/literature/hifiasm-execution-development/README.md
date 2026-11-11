# Hifiasm execution evidence

Run from the repository root:

```sh
python -B tools/literature/hifiasm_execution_evidence.py
```

`VALIDATION.json` records the exact size and SHA-256 of every physical part,
each reassembled ZIP, all logical evidence files and the current source files.
ZIPs are split into numbered parts of at most 90,000,000 bytes to stay below
GitHub's per-file limit. Parts are consecutive raw bytes: concatenate them in
the `parts` order recorded in the validation document to reconstruct a ZIP.
The verifier checks both the parts and the reconstructed ZIP before decoding
the content-addressed file map. Do not unzip individual parts.

The execution archive retains the current source builds, runtime bindings,
positive and negative workflow controls and beginner example. The native archive
retains complete read, graph, k-mer and streaming-parental controls. The history
archive retains earlier diagnostics, receipts and producer sources; failed
attempts remain failed. Original Yak package sources without an established
overall redistribution license are excluded. Original local attempts remain
unchanged.

These hashes establish integrity, not authenticity or biological accuracy.
The canonical executable and wider implementation goal remain unchanged.
