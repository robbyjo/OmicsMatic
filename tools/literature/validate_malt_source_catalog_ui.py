# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check retained source/scientific UI assertions and explicitly typed supplementary evidence."""
from pathlib import Path
import argparse,concurrent.futures,json,os,subprocess,sys,time,urllib.error,urllib.request
from setup_deseq2_reference import ROOT,pin,save
from validate_htseq_catalog_ui import NODE,EDGE


def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);out=p.parse_args().out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/nfcore-runtime');out.mkdir(parents=True)
 scripts=[(n,'check_malt_previous_'+n+'_ui.cjs') for n in ['general','genesis','mtag','scdrs']]+[('malt','check_malt_sources_ui.cjs')];paths=[Path(__file__),*[ROOT/'tools/literature'/n for _,n in scripts],ROOT/'site/literature-review.html',ROOT/'site/literature-warnings.html',*[ROOT/'docs/literature/omics-2023-2026'/n for n in ['papers.json','tool-inventory.json','summary.json']]];guards={p.relative_to(ROOT).as_posix():pin(p) for p in paths};server=None;state=dict(status='FAIL2_MALT_SOURCE_CATALOG_BROWSER_CHECKS',whole_goal_complete=False)
 try:
  assert (NODE/'bin/node.exe').is_file() and EDGE.is_file(),'Required action: provide the permitted Node/Playwright and Edge prerequisites before this dependent UI check.'
  with (out/'server.log').open('xb') as log:
   url='http://127.0.0.1:8768/literature-review.html'
   try:
    with urllib.request.urlopen(url,timeout=3) as response:served=response.read()
   except urllib.error.URLError:
    server=subprocess.Popen([sys.executable,'-m','http.server','8768','--bind','127.0.0.1','--directory',str(ROOT/'site')],stdin=subprocess.DEVNULL,stdout=log,stderr=log,creationflags=subprocess.CREATE_NO_WINDOW);save(out/'server.live.json',dict(pid=server.pid,port=8768));deadline=time.monotonic()+30
    while time.monotonic()<deadline:
     try:
      with urllib.request.urlopen(url,timeout=1) as response:served=response.read()
      break
     except urllib.error.URLError:assert server.poll() is None;time.sleep(.1)
    else:raise RuntimeError('Owned local server failed to start')
   assert served==(ROOT/'site/literature-review.html').read_bytes()
   def run(item):
    label,name=item;home=out/label;home.mkdir();env=dict(os.environ,JLINALG_REVIEW_UI_OUT=str(home));command=[str(NODE/'bin/node.exe'),str(ROOT/'tools/literature'/name),str(NODE/'node_modules'),str(EDGE)]
    with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=900,creationflags=subprocess.CREATE_NO_WINDOW)
    save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,label+' browser check failed; inspect retained stderr'
   with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:list(pool.map(run,scripts))
   general=json.loads((out/'general/ui-validation.json').read_bytes());genesis=json.loads((out/'genesis/COMPLETED.json').read_bytes());mtag=json.loads((out/'mtag/mtag-ui-verification.json').read_bytes());scdrs=json.loads((out/'scdrs/scdrs-ui-verification.json').read_bytes());malt=json.loads((out/'malt/COMPLETED.json').read_bytes())
   assert len(general['checks'])==299 and len(genesis['checks'])==18 and len(mtag['checks'])==11 and len(scdrs['checks'])==12 and len(malt['checks'])==10
   assert all(pin(ROOT/n)==v for n,v in guards.items());state.update(status='PASS299_GENERAL18_GENESIS11_MTAG12_SCDRS10_MALT_TYPED_SOURCE_BROWSER_CHECKS',source_pins=guards,retained_predecessor_checks=340,new_typed_source_checks=10,prior_scientific_scope_assertions_preserved=True,remote_mutations=False)
 except Exception as e:state.update(cause=repr(e))
 finally:
  if server is not None:server.terminate();server.wait(timeout=15)
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS') else 2


if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable source, scope or byte checks')
 raise SystemExit(main())
