# nf-core source setup and primary audit

This package verifies original source setup and source-role coverage. It does
not admit the nf-core family, execute its analyses, or complete the 250-paper
goal. Reuse the existing ATAC/ChIP-seq and generic engine components; their
historical evidence and pinned producers remain separate.

Read [the source setup guide](../../../docs/literature/NFCORE_SOURCE_WORKFLOWS.md).
The packaged Java command exports the exact Python helper and seven-edition
source lock. The installer performs no package-manager, container, reference,
engine or upstream-code execution.

From the repository root, recheck the public checkpoint and all 250 cached XMLs:

```sh
python tools/literature/verify_nfcore_source_checkpoint.py \
  --cache /absolute/primary-xml-cache --jar /absolute/exact-producer.jar
```

The primary cache contains `PMCNUMBER.xml.gz` files already bound to the frozen
paper hashes. Missing inputs produce SKIP3; changed inputs or expanded claims
produce FAIL2. For source refusal controls, use a NEW output:

```sh
python tools/literature/nfcore_workflows/validate_primary.py \
  --cache /absolute/primary-xml-cache --out /absolute/new-source-controls
```

`validate_sources.py` exercises the packaged installer and Java reader against
all seven exact original archives. Its usage is in the guide. Keep runtime
homes, caches, failed fixtures and raw process receipts private. The public
checkpoint contains source identities, bounded summaries and scoped counts.
Preserve prior checkpoint source contexts and producers; never update their
expected hashes to accept a changed source tree or executable.
