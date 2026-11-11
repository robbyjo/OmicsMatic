# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render one scoped MTAG admission while preserving all401 other tool objects exactly."""
import argparse
from collections import Counter
import json
import subprocess
import sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from setup_mtag_reference import BASE


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026';before={r['id']:r for r in json.loads((data/'tool-inventory.json').read_bytes())};assert len(before)==402 and before['mtag']['implementation_status']=='missing'
    for name in ['tool-inventory.json','summary.json','README.md','WARNINGS.md']:(out/(name+'.before')).write_bytes((data/name).read_bytes())
    with (out/'stdout').open('xb') as so,(out/'stderr').open('xb') as se:r=subprocess.run([sys.executable,str(ROOT/'tools/literature/render_review.py')],cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
    save(out/'process.json',dict(exit_code=r.returncode));assert r.returncode==0,'Inspect retained complete renderer logs'
    after={r['id']:r for r in json.loads((data/'tool-inventory.json').read_bytes())};assert set(after)==set(before)
    assert all(after[n]==v for n,v in before.items() if n!='mtag'),'An unrelated tool object changed'
    tool=after['mtag'];assert tool['implementation_status']=='upstream-adapter-validated' and tool['root_command']=='mtag-tools' and tool['supported_source_versions']==['1.0.8']
    assert tool['source_roles_reviewed']==5 and not tool['canonical_adapter_available'] and not tool['analysis_installs_software']
    summary=json.loads((data/'summary.json').read_bytes());counts=dict(Counter(r['implementation_status'] for r in after.values()));assert counts==summary['implementation_status_counts']
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['existing-adapter'],counts['missing'])==(250,402,2475,19,79,3,245)
    assert (data/'WARNINGS.md').read_bytes().startswith(b'## MTAG: warnings and required actions')
    readme=(data/'README.md').read_text(encoding='utf8');assert '98 new scoped integrations' in readme and '101 including three existing adapters' in readme and '245 named tools still missing' in readme
    save(out/'COMPLETED.json',dict(status='PASS_MTAG_CATALOG_RENDER401_OTHER_TOOL_OBJECTS_UNCHANGED',papers=250,tools=402,observations=2475,
        new_scoped_integrations=98,including_existing=101,missing=245,unchanged_other_tool_objects=401,whole_goal_complete=False,
        source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/render_review.py',ROOT/'tools/literature/mtag_catalog.py']},
        rendered_files={p.relative_to(ROOT).as_posix():pin(p) for p in [*[data/n for n in ['tool-inventory.json','summary.json','README.md','WARNINGS.md','SHA256SUMS']],ROOT/'site/literature-review.html',ROOT/'site/literature-warnings.html']}))
    print('PASS_MTAG_CATALOG_RENDER401_OTHER_TOOL_OBJECTS_UNCHANGED;98 new/101 including existing;245 missing')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable all-tool-object preservation and full catalog counts')
    main()
