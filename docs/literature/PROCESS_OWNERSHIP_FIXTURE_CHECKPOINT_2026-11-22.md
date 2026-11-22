# Atomic fixture follow-up and current handover entry

The [full handover](HANDOVER_2026-11-22_PROCESS_SAFETY.md) retains the remaining
tool work, private transfer paths, user decisions and process incident. An
unrelated older process may have been terminated by an earlier superseded test;
its application and recovery remain unknown. Pre-fix external-worker JARs remain
unsafe to run. This follow-up changes only a Java test fixture and documentation.
The corrected production JAR is byte-identical to the preceding safety build.

A separate GenomicRanges branch build passed all22 workflow contracts and13
simulated ownership tests, but its live fixture saw an empty newly created PID
file. That test failure is retained. This was a ready-file publication race,
not a new ownership incident. The fixture now writes a private pending file and
atomically renames it before another process reads it. It still signals only its
own original Process objects or verified retained handles. Fresh normal Java21
and Java25-runtime receipts record the actual checks after this test-only fix.

The source checkpoint is append-only. Original `STATUS.json`, receipts and
reader remain unchanged and correspond to baseline commit
`83ff263457facdd07307f2c2b671032c8764f579`. Their source pins intentionally do not
describe the later fixture/documentation. Use **`STATUS-v2.json` and the current
reader** for this source checkout:

```powershell
python -B tools/literature/process_ownership_qualification/verify_handover.py
```

This checks saved bytes and receipts without launching a worker. It does not
reconstruct private runtimes, rerun original omics analyses, qualify Linux process
cleanup or replace release assets. Compile with JDK21: the retained strict JDK25
compilation failure from an existing dangling documentation comment is unresolved.
The successful Java25 check uses JDK21 compilation and a Java25 test launcher.

The GenomicRanges draft stays on `codex/genomicranges-runtime-handover-20261122`.
It has no family credit or full upstream workflow qualification. Complete its
fresh corrected-producer packaged matrix and controls before admission. Current
totals remain148 scoped families,1661 reviewed/1062 pending uses; the full250-paper
implementation objective is unfinished. Private raw captures/runtimes and the
historical export blocked for potentially sensitive content remain local.
