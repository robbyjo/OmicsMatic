# Main synchronization: native geNorm and Sambamba checkpoints

**Warning: these are scoped development checkpoints. Complete current-tree
catalog source qualification remains pending, and this merge adds no catalog
integration credit. The 250-paper implementation objective remains open.**

This merge preserves current main's v0.4.0 publication and release corrections
at `23a863eb5add7af8d649741f334ebb9cd84d5662` and incorporates the native geNorm
checkpoint `f0f9514fd6019f890dbb55fdfe82f3c494fb6b2b`, original Sambamba source/BAM
checkpoint `e5169e744f05100fa27592eb5fe82b60c16cebef`, and execution checkpoint
`16a63095025eaea50bcb4eb19a58c7b394f67cce`. Existing main files, release tags,
published assets, build settings, workflows and website sources are preserved.

The [native geNorm guide](GENORM_NATIVE.md) describes quantity/log2 inputs,
stability, normalization and complete captured-input replay. P040's Cq wording
and exact reported software identity remain unresolved; no paper-specific
credit is admitted. Original reference states, discrepancies and failed attempts
remain retained.

The [Sambamba execution guide](SAMBAMBA_EXECUTION_PROFILE.md) describes exact
0.6.6/0.8.2 Linux runtimes, marking/removal, captured-input replay and mandatory
warnings. Before integration, 32 original-runtime cases, 32 genuine replays,
96 complete BAM comparisons and five actual process-limit/termination controls
passed. The [saved validation record](sambamba-execution-development/VALIDATION.json)
links byte-pinned sources and an archive containing the raw execution records.

Fresh integrated checks passed: all 28 focused Java tests, library/executable
JAR builds, `checkWebsite`, both standalone packaged help commands, and saved
public/private geNorm and Sambamba evidence readbacks. The packaged Sambamba
implementation class bytes match those used for the recorded fixture matrix.
The integrated JAR also completed fresh original marking, removal and genuine
replay in a Linux JVM. A native Windows attempt returned the expected `SKIP3`,
retained an actionable failure notice, and produced no accepted result. The
whole Java suite and complete scientific pipeline were not rerun for this merge.

Sambamba BAI outputs remain unqualified collateral: use a separately qualified
indexer before any index-dependent downstream analysis. Adversarial resealed
bundle, late-recording failure, spill and additional thread controls remain
pending. Bounds and source discrepancies must not be silently repaired or
relaxed. Missing prerequisites require skipping the dependent analysis until
the named permitted runtime is supplied; an analysis failure admits no result.
Read each run's `WARNINGS.md`, `FAILURE.txt` and raw phase consoles.

The [earlier main synchronization warning](MAIN_SYNC_20261115_STATUS.md) and
[source-transition boundaries](APPLICATION_SOURCE_TRANSITION_20261115.md) remain
applicable. Required action before claiming complete current-tree catalog
qualification: separately review the release and subsequent source transition,
complete the outstanding controls in new outputs, and preserve historical
guards and evidence. Do not refresh old verifier identities to bypass a refusal.
