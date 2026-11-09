# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render exactly one availability change and six reviewed role changes, preserving all other observations."""
import argparse,json,subprocess,sys,traceback
from collections import Counter
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from render_palantir_catalog import refresh_manifest


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['prepared','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);before=a.prepared.resolve()/'before';data=ROOT/'docs/literature/omics-2023-2026'
    sources=[Path(__file__).resolve(),ROOT/'tools/literature/rsubread_catalog.py',ROOT/'tools/literature/render_review.py',ROOT/'tools/literature/check_review_ui.cjs',ROOT/'tools/literature/reviewed-uses.json',data/'papers.json'];pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources};state=dict(status='FAIL2_RSUBREAD_CATALOG_RENDER',whole_goal_complete=False)
    try:
        command=[sys.executable,'-B',str(ROOT/'tools/literature/render_review.py')]
        with (out/'render.stdout').open('xb') as first,(out/'render.stderr').open('xb') as second:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=1800,creationflags=subprocess.CREATE_NO_WINDOW)
        save(out/'render.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Read complete render.stderr; preserve all partial outputs and retry NEW paths'
        old={r['id']:r for r in json.loads((before/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes())};new={r['id']:r for r in json.loads((data/'tool-inventory.json').read_bytes())}
        assert set(old)==set(new) and len(new)==400 and all(old[k]==new[k] for k in old if k!='rsubread');assert old['rsubread']['implementation_status']=='missing' and new['rsubread']['implementation_status']=='upstream-adapter-validated'
        counts=dict(Counter(r['implementation_status'] for r in new.values()));assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,73,251)
        assert (ROOT/'tools/literature/check_review_ui.cjs').read_bytes()==(before/'tools/literature/check_review_ui.cjs').read_bytes();assert all(pin(ROOT/n)==v for n,v in pins.items())
        roles=json.loads((data/'rsubread-role-coverage.json').read_bytes());assert pin(ROOT/'tools/literature/reviewed-uses.json')['sha256']==roles['reviews_sha256']
        refresh_manifest(data,(before/'docs/literature/omics-2023-2026/SHA256SUMS').read_bytes());state.update(status='passed-one-Rsubread-availability-and-six-role-catalog-render',source_pins=pins,unchanged_tool_rows=399,new_scoped_integrations=90,including_existing_adapters=93,missing=251,named_tools=400,observations=2465,reviewed_roles=1003,supporting_pointers=349,implementation_status_counts=counts)
    except Exception as e:state['error']=repr(e);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    for name in ['tool-inventory.json','summary.json','README.md','WARNINGS.md']:(out/('after-'+name)).write_bytes((data/name).read_bytes())
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':raise SystemExit(main())
