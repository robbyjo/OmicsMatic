# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Render and validate the declared catalog delta; retain every failed/provisional render attempt."""
import argparse,json,subprocess,sys,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def refresh_manifest(data,original):
 hashes={p.name:pin(p)['sha256'] for p in data.iterdir() if p.is_file() and p.name!='SHA256SUMS'};order=[r.split('  ',1)[1] for r in original.decode().splitlines()];assert len(order)==len(set(order)) and set(order)<=set(hashes);order+=sorted(set(hashes)-set(order));(data/'SHA256SUMS').write_bytes(''.join(hashes[n]+'  '+n+'\n' for n in order).encode())

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--prepared',required=True,type=Path);a=p.parse_args();out=a.out.resolve();prepared=a.prepared.resolve();assert out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);data=ROOT/'docs/literature/omics-2023-2026';baseline=prepared/'before';state=dict(status='FAIL2_PALANTIR_CATALOG_RENDER',whole_goal_complete=False);files=[ROOT/'tools/literature/render_review.py',ROOT/'tools/literature/check_review_ui.cjs',ROOT/'tools/literature/reviewed-uses.json',ROOT/'tools/literature/palantir_catalog.py',ROOT/'tools/literature/palantir-named-software-registry.json',data/'papers.json'];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
 for name in ['tool-inventory.json','summary.json','README.md','WARNINGS.md']:(out/('before-'+name)).write_bytes((data/name).read_bytes())
 try:
  command=[sys.executable,'-B',str(ROOT/'tools/literature/render_review.py')]
  with (out/'render.stdout').open('xb') as f,(out/'render.stderr').open('xb') as g:run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=f,stderr=g,timeout=1200,creationflags=subprocess.CREATE_NO_WINDOW)
  save(out/'render.process.json',dict(command=command,exit_code=run.returncode));assert run.returncode==0,'Read retained render.stderr and preserve this failed attempt';old={t['id']:t for t in json.loads((baseline/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes())};new={t['id']:t for t in json.loads((data/'tool-inventory.json').read_bytes())};extra={'palantir','scfates','stereo-saw','stereopy','dorothea'};assert set(new)-set(old)==extra and all(old[k]==new[k] for k in old);assert new['palantir']['implementation_status']=='upstream-adapter-validated' and all(new[k]['implementation_status']=='missing' for k in extra-{'palantir'});counts={s:sum(t['implementation_status']==s for t in new.values()) for s in ['native-subset-validated','upstream-adapter-validated','missing']};assert counts=={'native-subset-validated':17,'upstream-adapter-validated':69,'missing':255};assert len(new)==400;summary=json.loads((data/'summary.json').read_bytes());readme=data/'README.md';readme.write_bytes(readme.read_bytes().replace(b'\r\n',b'\n'));assert all(pin(ROOT/n)==v for n,v in pins.items());refresh_manifest(data,(baseline/'docs/literature/omics-2023-2026/SHA256SUMS').read_bytes());state.update(status='passed-Palantir-and-four-missing-definitions-catalog-render',new_tool_ids=sorted(extra),unchanged_tool_rows=len(old),source_pins=pins,artifact=new['palantir']['development_candidate'],artifact_sha256=new['palantir']['development_candidate_sha256'],new_scoped_integrations=86,including_existing_adapters=89,implementation_status_counts=counts,named_tools=400,observations=2465,reviewed_roles=988,supporting_pointers=340,summary=summary)
 except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
 finally:
  for name in ['tool-inventory.json','summary.json','README.md','WARNINGS.md']:(out/('after-'+name)).write_bytes((data/name).read_bytes())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2
if __name__=='__main__':raise SystemExit(main())
