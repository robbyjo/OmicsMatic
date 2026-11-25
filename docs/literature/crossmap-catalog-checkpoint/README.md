# CrossMap catalog checkpoint

The current catalog has **162 scoped integrations: 30 native Java and 132 upstream**;
165 include three existing adapters. Its 250 papers contain 2,724 observations,
with 1,772 reviewed uses and 952 pending. CrossMap adds one upstream family and
three reviews, refining the existing P242 review. The full goal remains active.

See the [commands and four paper roles](../CROSSMAP_WORKFLOWS.md). Qualification
covers original 0.5.2/0.6.3 BED, VCF and BigWig conversion; 26 complete captures
passed Windows/Linux readback. The original studies, unreported editions/options,
Python 3.7 and other CrossMap formats remain unqualified.

**Warnings and action:** SKIP3 skips the dependent analysis when a named permitted
runtime or prerequisite is unavailable. Install separately only where allowed;
otherwise retain the skip. FAIL2 admits no complete result. Preserve originals
and failed/partial outputs, inspect the warnings, source stderr and record ledger,
correct the named cause and use new output paths. Cell Ranger remains excluded.

For a full readonly closing check, use absolute paths:

```sh
python3 -B /absolute/source/tools/literature/crossmap_catalog_checkpoint.py \
  --out /absolute/private-evidence/NEW-receipt \
  --private-root /absolute/private-evidence \
  --producer /absolute/exact-producing.jar \
  --predecessor-producer /absolute/exact-predecessor.jar \
  --java /absolute/java --cache /absolute/250-primary-XML-cache \
  --artifact-root /absolute/retained-project
```

The reader first checks the exact source, metadata and checksum file, then
reconstructs the 877-entry checksum manifest from its retained predecessor.
It checks the private evidence and all 26 saved contracts without rerunning the
original Python conversions. Raw traces and runtimes stay private.

An archived source context carries its own eight versioned metadata files and
checksum manifest. The explicitly supplied artifact root provides unchanged
historical objects, each checked against that archived manifest. It supplies
no replacement for missing versioned metadata. This avoids duplicating the
1.2 GB of retained catalog objects. Keep the immutable context, exact JARs and
private evidence. Never update expected hashes to accept changed old results.
The literature reports remain withheld from Pages; no release is published.
