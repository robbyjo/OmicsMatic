# Typed original ChIP-seq 1.2.1 development profile

Read [the complete workflow, setup requirements, warnings and recovery steps](../NFCORE_CHIPSEQ121.md). The executable runs the original pinned source workflow and requires complete selected source-consistency audits. The full nf-core family and 250-paper goal remain open; no extra catalog-tool or biological credit is claimed. The released executable is unchanged.

Run `python tools/literature/verify_nfcore_chipseq_checkpoint.py` from the repository root. The evidence retains both compiler executions, original annotation/QC populations, request and portable-bundle controls, missing-prerequisite/capacity failures, the earlier missing packaged entrypoint and the host Java spawn-helper failure. Large original FASTQs/BAMs and private runtimes remain local. Copy complete result directories and require successful `--verify`; read WARNINGS.md even after success.
