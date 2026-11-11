# Fresh original fastp filter/cutting controls

`native-controls.zip` contains 124 fresh original Linux runs: 31 configurations
on each of fastp 0.22.0, 0.23.2, 0.25.0 and 1.0.1. Each protocol has all 256 input
reads, explicit options, raw passing and failed FASTQ, JSON/HTML reports, stdout,
stderr and the actual command/exit receipt. All processes were terminal when the
archive was captured. There are 31,744 read evaluations.

SHA-256: `4b527a29bc067732d8bb4d6cb007077c7a43dca89572b105fb2eeb0543dfdd76`.

The original official binaries are downloaded separately using the pinned
`tools/literature/fastp-runtime-lock.json`; executables are not included here.
The source audit and collector are retained in the native development evidence
archive referenced by `docs/literature/FASTP_NATIVE_DEVELOPMENT_VALIDATION.json`.

This matrix explicitly disables adapter trimming, poly-G trimming and duplicate
evaluation, uses one worker and isolates global/quality cutting, final max_len,
quality/N/length/complexity predicates. It is not a full-pipeline or whole-paper
equivalence claim. Every surviving sequence and quality, output identity/order
and failed-read disposition is compared with the native Java implementation.
The initial native test reader misspelled the original lowercase
`failed_too_many_n_bases` tag; failed evidence is retained separately.
