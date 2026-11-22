# ACAT component handover evidence

Read [the handover](../HANDOVER_2026-11-22_ACAT_SKAT.md) first.
`QUALIFICATION.json` pins the final 500-test normal build, three packaged JVMs,
source inputs, unchanged catalog bytes and the explicit members of validation.zip.
The archive retains original artificial R oracle output, transcripts, test reports,
the working example and exact orchestration source. Saved qualification is not a
new model execution. The earlier 499-test producer, first compile failure and
pre-fix replay alias failure are retained with their historical scope.

`historical-root-variants/` inside the ZIP contains 23 exact older shared-root
variants. They are development evidence, not replacements for current main.
To inspect them, extract only to a NEW separate directory; never restore the
archive over a live checkout. The other 286 root changes match the baseline main
snapshot. ROOT-VARIANTS.json records all 309 file comparisons.

Private full text, bundled real SKAT data, RDS, runtime executables and JARs are
excluded. PRIVATE-TRANSFER.json gives exact local locations and the preceding
transfer inventory. Preserve all evidence, including failed and null results.
No private machine-to-machine transfer or full-goal completion is claimed.

Run `python -B tools/literature/acat091/verify_checkpoint.py` from the repository
root to read back the frozen evidence. Optional `--jar PATH` hashes the exact
qualified local JAR without executing it. Later changes require checking this
checkpoint on its exact commit; never refresh its pins to accept a changed tree.
