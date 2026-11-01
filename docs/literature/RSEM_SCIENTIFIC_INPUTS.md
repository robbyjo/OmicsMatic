# RSEM scientific input protection development

**RSEM public fitting remains unfinished. A protected input copy is not an admitted alignment or fitted result.** This internal Java layer prepares exact copies and audits ordinary reference files. It does not run a decoder, repair alignments, fit expression, or install software.

The [validation record](RSEM_SCIENTIFIC_INPUT_VALIDATION.json) pins the exact source and candidate. The earlier [source-build controls](RSEM_SOURCE_SETUP_VALIDATION.json) validate five original releases separately. All members of that earlier candidate remain byte-identical in the new package; only the scientific-input snapshot classes were added.

## What is protected

`RsemInputs.prepare` copies the supplied alignment without changing any bytes or inferring its format from its filename. SAM, BAM and CRAM must ultimately pass their own complete decoding and semantic checks. An arbitrary binary file can be copied successfully and still be invalid for analysis; the snapshot record explicitly leaves scientific alignment admission and public fitting false.

The reference prefix requires `.ti`, `.seq` and `.grp`. The complete native reference reader checks transcript identities and order, sequences, lengths, masks and gene grouping. Genome-derived references also require their complete `.chrlist` dictionary. Available `.idx.fa`, `.n2g.idx.fa` and `.transcripts.fa` companions are copied and pinned; their presence does not imply a completed FASTA/index lookup audit. Allele-specific `.gt` or `.ta` companions require a separate contract and are refused.

CRAM magic requires an explicitly supplied local, uncompressed FASTA and its `.fai`; both are copied and pinned. A leading FASTA marker is only an initial format check. Complete FASTA/index consistency, header lookup closure, reference identity and source-linked decoding remain required before scientific admission. No download, cache substitution or reference reconstruction occurs in this layer.

Original sources may have spaces or Unicode in their canonical paths. Copies use fixed names in a NEW owned directory. Actual original RSEM execution will additionally require its supported shell-safe paths. No sequence names, reference versions, alignment records, qualities, mates or auxiliary tags are normalized by copying.

Source hashes are rechecked after copying and whenever the live prepared context is verified. Native reference evidence must also match the bytes actually copied: a reference parsed from changed bytes cannot bind merely because a file was restored later. Source companion absence states are retained. Staged bytes, metadata and complete directory membership are checked; even a new empty directory is detected. Links, nonregular files, changed original sources and changed staged contents are refused. These integrity checks are neither signatures nor an OS sandbox.

## Limits, warnings and recovery

Explicit limits cover each complete file, total copied bytes, all owned members, encoded metadata and the native reference populations. The operation reserves space for diagnostics and never samples or truncates scientific inputs to fit a budget.

On failure, retain the owned directory and `INPUTS_FAILED.txt`. The file names the cause and required action. Correct the missing or inconsistent reference member, unavailable local CRAM reference, changed source, link, permission or capacity problem; then retry to a NEW directory. Existing output directories are preserved. A required compiler/runtime that cannot be installed or executed means the dependent analysis must remain skipped. This native copy stage supplies no public installation or fitting command.

## Current evidence and next work

Nineteen snapshot tests cover exact binary copying, complete transcript and genome references, actual consumed-byte binding after file restoration, original/staged/metadata changes, unexpected directory membership, appearing allele companions, source changes during copying, capacity failures, existing-directory preservation, cancellation, CRAM reference requirements and actual Linux links. Linux Java 21 passes the full 190-test RSEM suite. Windows Java 21/25 each pass 171 with 19 explicit Linux-only skips. All 111 existing native science tests pass on every profile; strict Java 17 compilation and private Javadoc pass.

Next, bind source-linked decoding to these immutable snapshots; audit all physical records and decoder metadata; retain original binary input for fitting; resolve complete CRAM reference lookup; and connect typed fitting requests to complete count/model/result and output-membership audits. Then implement permitted public setup, CLI and runnable end-to-end examples. The [original RSEM alignment requirements](https://deweylab.github.io/RSEM/rsem-calculate-expression.html) and exact historical source behavior must remain distinct from generic SAM validation.

RSEM remains missing in the toolbox inventory. The full 250-paper implementation and semantic review remains active.
