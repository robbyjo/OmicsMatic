# Original SKAT development checkpoint

**Development only. There is no standalone Java SKAT command yet.**
Read [the handover](../../../docs/literature/SKAT_WORKFLOW_HANDOVER_2026-11-22.md)
before resuming implementation. The Java contracts, intake and process classes
are unwired drafts; the R worker is a prototype. Do not grant catalog credit from
these files or treat prototype results as paper reproduction.

Check the handover source and evidence hashes without R:

```powershell
python -B tools/literature/skat_workflow_development/verify_handover.py
```

The public `evidence.zip` listed in the handover directory contains artificial
controls, original worker/RNG/null/result captures, logs and failed attempts.
Extract it to a NEW private directory. `controls/` contains the original
independent direct-call controls; no participant data are included. Alternatively
create fresh controls with the exact private R profile:

```powershell
& 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' --vanilla `
  tools/literature/skat_workflow_development/direct_original_controls.R `
  'D:/PRIVATE/skat-library' 'D:/PRIVATE/NEW-direct-controls'
```

Then exercise the public development harness, with every output directory NEW:

```powershell
python -B tools/literature/skat_workflow_development/run_original_prototypes.py `
  --rscript 'C:/Program Files/R/R-4.6.1/bin/Rscript.exe' `
  --r-library 'D:/PRIVATE/skat-library' `
  --controls 'D:/PRIVATE/NEW-direct-controls' `
  --out 'D:/PRIVATE/NEW-worker-prototypes'
```

This launches the real original package; it is not a mock. It requires the exact
package versions in the handover and SOURCE.json. The controlled prototype
harness has a timeout for its direct R process; production owned-child handling
still needs qualification in Java. On failure retain the entire attempt. The
worker's startup fingerprint handshake precedes any model fit. The bounded
metadata read retry handles transient Windows sharing errors; it does not alter
scientific inputs or values. The harness supplies explicit policies allowing
single-variant substitution and an untested monomorphic set solely for these
artificial controls; users' future production policies must remain explicit.

The first set in each case is checked against separate direct-original results.
The remaining sets and helper replay are not an independent complete scientific
oracle. There is no Linux R qualification, full-method qualification, functional
Java RUN/VERIFY/REPLAY, release or whole-goal completion in this checkpoint.

Missing/prohibited prerequisites mean **SKIP3**; supply the named exact runtime,
package or input, or retain the skip. Data/numerical/process/integrity/capacity
failures mean **FAIL2**, no complete result, and a NEW attempt after resolving
the cause. Never overwrite historical evidence or edit old verifier hashes.
