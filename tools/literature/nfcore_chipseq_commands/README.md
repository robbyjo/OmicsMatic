# Packaged ChIP-seq routes

Read the [command guide](../../../docs/literature/NFCORE_CHIPSEQ_COMMANDS.md).
The exact 1.2.1/2.0.0 source workflows retain their existing typed requests,
scientific checks and legacy Java entry points. The root dispatcher now calls
methods that return an exit code instead of terminating its host process.

`validate_cli.py` exercises actual packaged help, refusal, setup-export and
read-only complete saved-result paths. Run it with `--help` for its inputs.
It does not run a pipeline or refit a model. Use the edition-specific preparers
and the normal packaged command for real pipeline execution.

The checkpoint reader checks source/evidence integrity and scope. Missing
required private evidence or producer files produce SKIP3; changed data or
expanded claims produce FAIL2. Preserve all failures and old expected hashes.
Raw traces, private runtimes, complete result trees and cleanup receipts remain
private. The public checkpoint contains hashes, bounded summaries and counts.

The nf-core family and the full 250-paper implementation goal remain open;
packaging existing scoped workflows does not earn another family credit.
