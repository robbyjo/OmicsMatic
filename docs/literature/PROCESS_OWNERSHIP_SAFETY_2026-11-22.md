# Process ownership safety correction

**Latest source context:** use the [atomic fixture follow-up and current verifier](PROCESS_OWNERSHIP_FIXTURE_CHECKPOINT_2026-11-22.md).
The original packet/reader below remain pinned to their preceding baseline.

**Action required: do not run external-tool workflows with a pre-fix JAR.**
Retain old JARs unchanged for historical evidence and known offline verification.
Build corrected source into a NEW build directory; do not overwrite a qualified
historical producer or reinterpret its source hashes. No release asset is
replaced by this source commit. Native-only calculations do not launch these
workers, but the same old fat JAR can contain affected external-worker commands.

## Observed incident and uncertainty

During a superseded GenomicRanges qualification attempt on November 22, an
unrelated older process was included in the worker cleanup set and **may have
been terminated**. The owned root PID21480 started at 13:59:06.330 UTC that day;
the incorrectly included PID17380 started at 15:58:11.547 UTC on November20.
The incident was around08:59 Eastern on November22. The failed capture recorded
the older process as no longer alive after cleanup. Individual termination
signals were not logged, so the exact signal outcome is unproven. The unrelated
application/job name and any lost work could not be recovered from the available
process/event records. No unknown application was restarted.

The audit found this one older candidate among 162 current-task captures with
process records. This is the scope of that audit, not proof that every earlier
workflow was unaffected. The failed capture remains unadmitted. All raw evidence
and superseded matrices are retained. Historical numerical results are not
automatically invalidated by this process-lifetime defect; their own admission,
integrity and scientific checks remain separate.

If an application/analysis stopped around that time, identify its name, logs and
checkpoint before recovery. Do not kill a currently reused PID or restart an
unknown job based on the historical PID number. The user impact question remains
pending; publication of this fix does not establish recovery.

## Cause and correction

The inherited guard trusted `ProcessHandle.descendants()`. A Windows process can
retain a parent PID after its parent exits. When that PID is reused, the JDK21
global descendant traversal can associate the older process with the new root.
Local OpenJDK21 `ProcessHandleImpl` source confirmed that its global traversal
does not establish the root birth ordering that immediate-child enumeration uses.

`OwnedProcesses` now traverses immediate child edges and requires a live root,
known creation times, a child no older than its immediate parent, and a matching
live parent PID **and creation time**. Unverifiable edges are excluded. Retained
handles check the observed creation identity before liveness/termination and
retain the original native handle rather than resolving a PID again to signal.
Already observed owned children can still be closed after the root exits.

The patch replaces 149 global descendant calls across 99 production Java files.
It also fixes two recorded-PID termination/liveness helpers and 11 Linux cleanup
sites that previously resolved a PID after checking its procfs birth identity.
Those11 sites now resolve once before validation and signal that same native
handle. Existing Linux session/cwd/procfs policies remain otherwise unchanged.
SKAT's producer class fingerprint now includes the new helper and nested classes.
Scientific R workers, input semantics and catalog counts are unchanged.

This is periodic process observation, **not OS job containment**. Very short
lived or escaped children can be missed; unknown creation identities are refused.
The source audit and focused Windows tests do not qualify every external tool or
establish Linux runtime behavior. Upstream scientific workflows must be
requalified with the corrected producer before making new workflow claims.

## Validation and evidence

Thirteen simulated graph tests run without any OS workers/signals. They cover
the observed older orphan, deeper birth ordering, reused PID/parent identities,
unknown creation times, exited roots, retained children, cycles and duplicates.
A separate integration fixture starts its own root/child and an older independent
process; the owned child closes while the independent fixture survives. Fixture
cleanup uses only its original `Process` objects and verified retained handles.
The initial focused run passed 14 tests. The final normal-build receipts in
`process-ownership-safety-20261122/` give the actual broader totals and JAR pins.
Existing SKAT callback/timeout/orphan controls exercise the corrected shared path;
no upstream omics analyses are rerun as part of this safety checkpoint.

Private incident evidence:

`D:/projects/JLinAlg/build/gr-2/build/genomicranges-2/process-ownership-incident-1/`

Failed capture:

`D:/projects/JLinAlg/build/gr-2/build/genomicranges-2/packaged-matrix-1/320/subset-FALSE/run/`

Complete safety development evidence and isolated builds:

`D:/projects/JLinAlg/build/ps-1/build/process-ownership-1/`

These directories and historical JARs remain local and need private transfer for
machine handover. Public SHA-256 receipts establish byte integrity, not signatures,
biological validity, complete process containment or a release qualification.


**Build limitation:** strict compilation with JDK25 currently fails before tests
because the pre-existing `cytometry/internal/stanford/Transform.java` documentation
comment triggers `dangling-doc-comments` under `-Werror`. Use **JDK21 to compile**.
That failed attempt is retained with zero executed tests. The successful secondary
check compiles with JDK21 and explicitly runs27 ownership/SKAT controls on JDK25;
it is a runtime check, not a successful JDK25 compilation claim. The primary
JDK21 normal build passes761 focused tests, zero failures/skips. No Linux runtime
qualification or upstream scientific rerun is claimed.

Corrected primary development JAR (not a release):

`D:/projects/JLinAlg/build/ps-1/build/process-ownership-1/handover-1/project-build/cli/omicsmatic-0.4.0.jar`

SHA-256 `0150620db7e6e72b8b6171127216662ba262258efa4ef8076a89ccefa87c2d19`.

Verify saved source/receipt bytes without launching any worker:

```powershell
python -B tools/literature/process_ownership_qualification/verify_checkpoint.py
```
