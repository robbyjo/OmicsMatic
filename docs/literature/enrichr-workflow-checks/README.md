# Saved Enrichr workflow development handover

**Normal source-build tests passed; normal-JAR live/example qualification and
cumulative Enrichr catalog admission remain open.** No additional family credit,
release, historical package parity or whole-goal completion is claimed. Start
with the [handover](../HANDOVER_2026-11-19_ENRICHR_WORKFLOW.md).

From the repository root, run Python 3 against a NEW output directory:

```powershell
python -B tools/literature/verify_enrichr_workflow.py --out build/enrichr-handover-readback-new
```

This checks saved source pins, every evidence archive member, normal-JAR test
XML (42 tests on three JVMs), the separate composed-reference proof, 25 pairs
of original/annotated class payloads, and explicit dispatcher/annotation
compatibility with all older ABF/API source and archive pins. It performs no
fresh JVM, service or scientific execution. Keep `READBACK.json`,
`QUALIFICATION.json` and `evidence.zip` together. SHA-256 proves integrity
against these saved manifests; it is not a digital signature.

The archive contains synthetic test evidence, logs, exact before-source,
annotation class pairs and private-helper source copies for resuming work.
The nested native Linux synthetic case ZIP records a symlink in topology
metadata; no link is automatically restored. Full live downloaded library
data and large candidate JARs remain local; see the handover's transfer paths.
Their saved artifact identities are distinct and must stay distinct.

The old strict ABF/API source readers remain unchanged and refuse the reviewed
current-source transition. This new reader checks only the exact three-line
dispatcher addition and exact inline SOURCE-only annotation inverses, plus
every other original pin and archive payload. It never updates old hashes or
scientific source lookups. Admission must still resolve the cumulative
transition through the existing guarded process.

**SKIP3:** restore the exact named missing/unreadable source or packet file;
skip dependent analysis until restored. **FAIL2:** preserve the diagnostic,
inspect changed/schema/CRC/source evidence, restore the exact reviewed bytes
and retry into a NEW output. Do not recalculate a saved hash merely to pass.
Earlier failed strict-build and Linux archival attempts are retained and
labelled in the evidence. They are not erased by later successful attempts.
