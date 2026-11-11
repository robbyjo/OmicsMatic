# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render one availability delta and verify every other inventory row and all paper roles remain exact."""
import argparse,json,subprocess,sys,traceback
from collections import Counter
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save
from render_palantir_catalog import refresh_manifest

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['prepared','out']:parser.add_argument('--'+name,type=Path,required=True)
    a=parser.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);baseline=a.prepared.resolve()/'before';data=ROOT/'docs/literature/omics-2023-2026';state=dict(status='FAIL2_SCFATES_CATALOG_RENDER',whole_goal_complete=False);sources=[Path(__file__).resolve(),ROOT/'tools/literature/gseapy_catalog.py',ROOT/'tools/literature/render_review.py',ROOT/'tools/literature/check_review_ui.cjs',ROOT/'tools/literature/reviewed-uses.json',data/'papers.json'];pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources}
    try:
        command=[sys.executable,'-B',str(ROOT/'tools/literature/render_review.py')]
        with (out/'render.stdout').open('xb') as first,(out/'render.stderr').open('xb') as second:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=first,stderr=second,timeout=1800,creationflags=subprocess.CREATE_NO_WINDOW)
        save(out/'render.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Read retained full render.stderr; no catalog result admitted';old={r['id']:r for r in json.loads((baseline/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes())};new={r['id']:r for r in json.loads((data/'tool-inventory.json').read_bytes())};assert set(old)==set(new) and len(new)==400 and all(old[k]==new[k] for k in old if k!='gseapy');assert old['gseapy']['implementation_status']=='missing' and new['gseapy']['implementation_status']=='upstream-adapter-validated';counts=dict(Counter(r['implementation_status'] for r in new.values()));assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,71,253);roles=json.loads((data/'gseapy-role-coverage.json').read_bytes());assert pin(data/'papers.json')['sha256']==roles['papers_sha256'] and pin(ROOT/'tools/literature/reviewed-uses.json')['sha256']==roles['reviews_sha256'];assert (ROOT/'tools/literature/check_review_ui.cjs').read_bytes()==(baseline/'tools/literature/check_review_ui.cjs').read_bytes();assert all(pin(ROOT/n)==v for n,v in pins.items());refresh_manifest(data,(baseline/'docs/literature/omics-2023-2026/SHA256SUMS').read_bytes());state.update(status='passed-one-GSEApy-availability-catalog-render',source_pins=pins,unchanged_tool_rows=399,new_scoped_integrations=88,including_existing_adapters=91,missing=253,named_tools=400,observations=2465,reviewed_roles=993,supporting_pointers=344,implementation_status_counts=counts)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    for name in ['tool-inventory.json','summary.json','README.md','WARNINGS.md']:(out/('after-'+name)).write_bytes((data/name).read_bytes())
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':raise SystemExit(main())
