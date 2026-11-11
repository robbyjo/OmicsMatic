# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render the two-package catalog correction while preserving400 prior tool objects exactly."""
import argparse
import json
import subprocess
import sys
from collections import Counter
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta,verify_source
BASE=ROOT/'build/literature-review/snprelate-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    data=ROOT/'docs/literature/omics-2023-2026'
    name='tools/literature/render_review.py';file=ROOT/name;before=file.read_bytes();anchor=b'if tool_id not in {"diamond",'
    assert before.count(anchor)==1;after=before.replace(anchor,b'if tool_id not in {"snprelate", "diamond",')
    row=inverse(before,after);row['path']=name;assert reverse_delta(after,row)==before
    descriptor=data/'snprelate-renderer-note-shared-source-compatibility.json';assert not descriptor.exists()
    save(descriptor,dict(schema_version=1,status='passed-dispatch-catalog-only-source-deltas',tool_id='snprelate',
        scope='Preserve the explicit SNPRelate validation note instead of replacing it with a generic note. Exact prior renderer retained; no scientific edit.',paths=[row]))
    (out/'renderer.before.py').write_bytes(before);file.write_bytes(after);verify_source(ROOT,data,name,row['before_sha256'])
    prior={r['id']:r for r in json.loads((BASE/'catalog-apply-1/tool-inventory.json').read_bytes())};assert len(prior)==400
    # The legacy FUMA verifier requires a consistent *current* paper observation
    # total during rendering. The first attempt saw the preceding2466 total.
    # Update just that global count before rendering; all scientific checks remain.
    summary_file=data/'summary.json';raw=summary_file.read_bytes();summary=json.loads(raw)
    assert summary['distinct_named_tools']==400 and summary['paper_tool_observations']==2466
    (out/'summary.before.json').write_bytes(raw);summary['paper_tool_observations']=2475;save(summary_file,summary)
    with (out/'stdout').open('xb') as so,(out/'stderr').open('xb') as se:
        run=subprocess.run([sys.executable,str(file)],cwd=ROOT,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
    save(out/'process.json',dict(exit_code=run.returncode));assert run.returncode==0,'Inspect retained renderer logs'
    current={r['id']:r for r in json.loads((data/'tool-inventory.json').read_bytes())};assert len(current)==402 and set(current)-set(prior)=={'snprelate','genesis'}
    assert all(current[k]==value for k,value in prior.items()),'Unrelated tool object changed'
    counts=dict(Counter(r['implementation_status'] for r in current.values()));summary=json.loads(summary_file.read_bytes())
    assert counts==summary['implementation_status_counts']
    assert (summary['papers'],summary['distinct_named_tools'],summary['paper_tool_observations'],counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['existing-adapter'],counts['missing'])==(250,402,2475,19,77,3,247)
    assert current['snprelate']['papers']==['P130','P170','P191','P216'] and current['snprelate']['source_roles_reviewed']==4
    assert current['genesis']['papers']==['P128','P130','P142','P191','P219'] and current['genesis']['implementation_status']=='missing'
    assert (data/'WARNINGS.md').read_bytes().startswith(b'## SNPRelate: warnings and required actions')
    save(out/'COMPLETED.json',dict(status='PASS_SNPRELATE_CATALOG_RENDER400_PREVIOUS_TOOL_OBJECTS_UNCHANGED',papers=250,tools=402,observations=2475,
        new_scoped_integrations=96,including_existing=99,missing=247,unchanged_prior_tools=400,new_package_observations=9,
        source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),file,ROOT/'tools/literature/snprelate_catalog.py']},
        rendered_files={p.relative_to(ROOT).as_posix():pin(p) for p in [*[data/n for n in ['tool-inventory.json','summary.json','README.md','WARNINGS.md','SHA256SUMS']],ROOT/'site/literature-review.html',ROOT/'site/literature-warnings.html']},
        initial_stale_global_summary_render_failure_retained=True,whole_goal_complete=False))
    print('PASS_SNPRELATE_CATALOG_RENDER400_PREVIOUS_TOOL_OBJECTS_UNCHANGED')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete prior tool preservation')
    main()
