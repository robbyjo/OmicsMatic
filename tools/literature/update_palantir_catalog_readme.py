# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replace a stale current-status heading with verified catalog totals, retaining the entire old document unchanged as history."""
import argparse,hashlib,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from render_palantir_catalog import refresh_manifest

CURRENT='''# Literature-driven omics toolbox: current status

The fixed cohort contains **250 papers,400 named tools,2,465 paper-tool observations,988 reviewed uses and340 supporting paragraph pointers**. Current scoped availability is **86 new integrations:17 native subsets and69 upstream adapters**, or **89 including three existing adapters**. **255 tools remain missing.** Paper-wide semantic completeness and the full implementation goal remain open. Five newly recognized names add four missing entries; Palantir receives the one new adapter credit.

Read the [current catalog update](../PALANTIR_CATALOG.md), [Palantir setup/workflow guide](../PALANTIR_WORKFLOW_DEVELOPMENT.md), [catalog validation](palantir-catalog-validation.json), [summary](summary.json) and [tool inventory](tool-inventory.json). Eight source-bound Palantir/scFates/SAW/Stereopy/DoRothEA roles are appended to the prior980 reviews. Raw paper identities, all prior observations and all prior reviews are retained. Original source versions and unreported paper choices remain explicit.

**Warnings and recovery:** read [WARNINGS.md](WARNINGS.md) and every analysis status. Missing or forbidden dependencies mean **SKIP3: skip dependent analysis**. **FAIL2 admits no atomic result, including earlier completed outputs.** Preserve originals and provisionals, resolve the named cause and retry NEW paths. Separate permitted setup is explicit; RUN installs nothing. Original Palantir clipping can reduce fate-row mass while entropy retains its preclip meaning. Native audits do not independently refit trajectories. Cell Ranger remains excluded.

Run current read-only verification from the project root:

```powershell
python -I -B tools/literature/verify_palantir_catalog.py
python -I -B tools/literature/palantir_workflow_evidence.py
```

The source-pointer audit additionally needs the local original XML/extraction cache. The original three-year window remains2023-10-23 through2026-10-23; no extension was needed. The canonical executable is unchanged. No release or website deployment is claimed; unfinished literature pages remain excluded from Pages.

## Retained historical documentation

All text below is preserved byte for byte from the previous document. Its counts and statements labelled current are historical and are superseded by the status above.

<details>
<summary>Previous status documents and evidence links</summary>

'''

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/palantir-runtime');out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';path=data/'README.md';raw=path.read_bytes();assert raw.startswith(b'# Literature-driven omics toolbox: current status') and b'390 named tools' in raw[:1000] and b'400 named tools' not in raw[:1000];(out/'before-README.md').write_bytes(raw);manifest=(data/'SHA256SUMS').read_bytes();updated=CURRENT.encode()+raw+b'\n</details>\n';assert updated[len(CURRENT.encode()):-len(b'\n</details>\n')]==raw;path.write_bytes(updated);save(data/'palantir-readme-evolution.json',dict(schema_version=1,status='passed-exact-data-README-documentation-evolution',path=path.relative_to(ROOT).as_posix(),before=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()),after=pin(path),historical_document_retained_verbatim=True,scientific_source_exception_added=False));refresh_manifest(data,manifest);save(out/'COMPLETED.json',dict(status='passed-updated-current-data-README-with-complete-historical-document',document=pin(path),previous_document=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()),source_pin=pin(Path(__file__)),whole_goal_complete=False));print('Updated current counts and guide links; complete prior README retained verbatim')
if __name__=='__main__':main()
