# PLINK relatedness development checkpoint

**UNQUALIFIED DEVELOPMENT. No new catalog credit or whole-tool/paper claim.**
Read [the handover](../HANDOVER_2026-11-19_PLINK_RELATEDNESS.md) for completed
work, pending acceptance checks, exact local paths and prominent recovery steps.

`checkpoint.zip` retains final build metadata, both pilot summaries, all final
synthetic captures (including three failures), synthetic inputs, setup outcome
records and original machine-specific helper scripts. `MANIFEST.json` pins every
member, candidate JAR and the ten source/resource files. It contains no upstream
executables/archives, large JARs, credentials or full paper text. Absolute paths
in original request/process metadata are retained as historical evidence.

The exact build-4 candidate passed 16 small original-program expectations:
13 successes with Java VERIFY, and three expected FAIL2 attempts. This is a
pilot checkpoint. Robustness, Linux, replay, scale and full source review are
unfinished; do not treat it as the final qualification packet.

[READBACK.json](READBACK.json) records actual Windows Java verification after
relocation: 13 captures accepted, three failed attempts refused, and all 497
extracted checkpoint members unchanged. No original PLINK program ran during
this readback. Linux verification remains pending.

With Python 3.11+, Java 17+ and the exact locally retained candidate JAR, run
from the authoritative project worktree (PowerShell):

```powershell
python tools/literature/verify_plink_relatedness_handover.py `
  --project . `
  --jar build/plink-relatedness-development-1/build-4/omicsmatic-0.4.0.jar `
  --java 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe' `
  --out build/plink-relatedness-handover-readback-NEW
```

Choose a new `--out` each time. This extracts only the pinned packet into that
new folder and runs Java VERIFY for all 16 captures, expecting refusal for
the three failed attempts. It does not run PLINK or install software. The
original captures remain unchanged. Logs, progress and completion stay in the
new output. Missing/different candidate JAR: restore its exact handover bytes,
or qualify a new build separately; do not alter these pins to force success.

The source can be built with the project's normal Gradle setup, but that
full build was not executed for this checkpoint. Do not claim a regenerated
artifact equals the exact pinned isolated candidate without checking its bytes.
