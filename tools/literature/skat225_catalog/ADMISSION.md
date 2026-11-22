# Original SKAT2.2.5 catalog protocol

This credits one upstream provider and one SKAT-O alias after the actual prepared
workflow qualification. It preserves all250 paper identities,2723 observations,
1661 prior reviews and444 unrelated inventory rows. Fifteen reviewed SKAT/SKAT-O
uses keep their original source/provider/comparator distinctions. No new paper
reviews,matched P246 command,whole-tool or whole-paper reproduction is claimed.

The current registry provider is `skat225_catalog.model.registry`. Read
[the integration scope](../../../docs/literature/skat225-qualification/INTEGRATION.md).

Use Python3.11+ without `-O`,a complete checkout including Git base abee077,and
NEW output for every phase. A shallow clone needs that exact history fetched
before the catalog reader can reconstruct its predecessor. No old hash is
refreshed. The qualified JAR and installed runtimes are private transfer items.

```powershell
python tools/literature/skat225_catalog/catalog.py --verify --out build/skat-catalog-verify
```

That checks the recorded qualification,source/archives,exact retained baseline,
deterministic metadata,registry,embedded report and full849-entry manifest. It
does not run an association analysis,refit R models or revalidate a new runtime.
To also recheck the actual original producer,receipts and154 runtime file hashes:

```powershell
python tools/literature/skat225_catalog/catalog.py --verify --out build/skat-catalog-verify-actual --checks-root PATH/TO/skat-qualification-1 --jar PATH/TO/EXACT-QUALIFIED.jar
```

Both extra arguments are required together. Missing files mean SKIP3 and an
explicit prerequisite;integrity/input/numerical/capacity contradictions mean
FAIL2 with no complete result. Preserve failed evidence and retry NEW output.
Download/install privately where allowed;otherwise retain the dependent skip.

For a not-yet-admitted exact predecessor,`--plan` requires `--checks-root`,`--jar`
and NEW `--out`. It checks the qualified packet and actual producer/runtime,
preserves exact eight-file metadata-before bytes and the847-entry old manifest,
and writes a complete preview. Canonical files stay untouched.

`--inspect --plan-dir PLAN` rechecks the source,qualification,actual receipts,
current predecessor and exact preview. `--apply` requires the same actual
arguments and plan. The common tested transaction installer preflights every
owned write,checks for concurrent changes and preserves failed/new/foreign
evidence on rollback. It then validates the installed849-entry after-state.
Existing admission owners are refused;never overwrite or reapply them.

Historical development readers belong to their exact frozen owners. The new
qualification binds unchanged scientific source;the catalog admission is a
separate metadata transition. Private runtimes/JARs/full source data require
separate transfer. Cell Ranger stays excluded and unfinished reports remain off
Pages. The complete250-paper implementation objective remains unfinished.
