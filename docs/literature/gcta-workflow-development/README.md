# Saved GCTA workflow development checkpoint

**Qualification and catalog admission remain unfinished.** Start with the
[handover](../HANDOVER_2026-11-20_GCTA_WORKFLOWS.md) for the next actions,
warnings, exact producer and private-transfer paths.

`PROFILE.json` pins the current source and exact normal producer.
`INVENTORY.json` lists every member of `evidence.zip`, with SHA-256 and byte
length. The archive retains synthetic current inputs/outputs, complete
successful and failed captures, raw source stdout/stderr, old prototype code
owners, build/test receipts, model audits and the original research scripts.
These are selected saved observations, not whole-tool qualification.

No participant data are included. Original executable/DLL/runtime archives,
Gradle caches, the approximately 196 MB producer JAR, primary paper full text
and the complete private research history are **not** this archive. Transfer
those separately with checksums. Do not delete originals after creating this
selected public archive or assume GitHub is a complete workstation backup.

The historical scripts preserve exact machine-specific paths and old attempt
names. Inspect them and adapt into NEW output directories before execution;
blind reruns can fail on existing outputs. The saved reader never executes
them, installs software, launches Java or reruns a model:

```powershell
python -B tools/literature/gcta_workflows/verify_checkpoint.py --out build/gcta-saved-check-new
```

To also verify the exact producer's integrity, add
`--jar C:/absolute/path/to/the/captured/omicsmatic-0.4.0.jar`. Omitting it records
an explicit producer SKIP3 within the saved-check receipt. A wrong supplied
JAR is FAIL2; do not substitute a newly rebuilt JAR or refresh an old hash.

The reader verifies all archive members, successful capture seals and the saved
build/model/packaged/cross-host/replay receipts. It checks that 56 model captures
were complete, eight deliberate failures and two edition skips were retained,
and all 16 packaged tests completed per JVM. Saved bytes are evidence of those
observations; this is not a new original-model execution, independent refit,
cryptographic authentication, or admission to the 250-paper catalog.

The first independent REML audit's expected-Fisher covariance mismatch is
retained. Source inspection identifies average-information REML instead;
source-correct independent covariance follow-up remains open. Earlier failed
build, missing-prefix original association and 1/2-coded binary attempts are
also retained. Do not relabel these historical attempts as passes.
