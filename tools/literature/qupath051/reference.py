#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute the full six-image control directly through the unchanged original launcher."""
from pathlib import Path
import argparse,json,os,re,shutil,subprocess,time,traceback
from provider import verify_provider,private_environment,save,pin
from make_fixtures import create
HERE=Path(__file__).resolve().parent
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--provider',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  for protected in [HERE,a.provider.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Reference output overlaps protected source/provider')
  out.mkdir(parents=True,exist_ok=False);owned=True;provider=a.provider.resolve();ready=verify_provider(provider);create(out/'inputs');(out/'results').mkdir();snapshot=out/'source-snapshot';snapshot.mkdir()
  for name in ['OriginalControl.groovy','make_fixtures.py','reference.py']:shutil.copyfile(HERE/name,snapshot/name)
  sources={n:pin(HERE/n) for n in ['OriginalControl.groovy','make_fixtures.py','reference.py']};inputs={p.name:pin(p) for p in (out/'inputs').iterdir()}
  save(out/'BEFORE.json',dict(sources=sources,input_files=inputs,provider_ready=pin(provider/'READY.json')));env,prefs=private_environment(out);save(out/'PREFERENCES.json',prefs)
  command=[ready['launcher'],'script','--args',str(out),str(snapshot/'OriginalControl.groovy')];start=time.time()
  with (out/'original.stdout').open('xb') as so,(out/'original.stderr').open('xb') as se:
   child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0);save(out/'original.live.json',dict(command=command,pid=child.pid,started_unix=start));code=child.wait(timeout=300)
  row=dict(command=command,exit_code=code,elapsed_seconds=time.time()-start);calls.append(row);save(out/'original.process.json',row)
  log=(out/'original.stdout').read_text(encoding='utf8')+(out/'original.stderr').read_text(encoding='utf8')
  if code or re.search(r'\[ERROR\s*\]',log) or log.count('qupath.ScriptCommand - Script result: QUPATH051_COMPLETE_SYNTHETIC_CELL_POPULATION')!=1:raise ValueError('Original reference did not complete; preserve original logs and complete fixture population')
  settings=json.loads((out/'inputs/settings.json').read_bytes());expected={'complete-summary.json'}|{r['id']+suffix for r in settings['jobs'] for suffix in ['.raw.qpdata','.qpdata','.measurements.tsv','.objects.geojson']}
  if {p.name for p in (out/'results').iterdir()}!=expected:raise ValueError('Incomplete original reference outputs')
  summary=json.loads((out/'results/complete-summary.json').read_bytes())
  if [r['detections'] for r in summary]!=[16,0,8,16,16,16]:raise ValueError('Unexpected complete synthetic original cell population; do not drop cases or change original inputs')
  verify_provider(provider)
  if any(pin(HERE/n)!=v for n,v in sources.items()) or any(pin(out/'inputs'/n)!=v for n,v in inputs.items()):raise ValueError('Reference code/inputs changed')
  result=dict(status='PASS_DIRECT_ORIGINAL_QUPATH051_SIX_COMPLETE_CELL_CONTROLS',images=6,detections=72,complete_original_files=25,sources=sources,inputs=inputs,output_files={p.name:pin(p) for p in sorted((out/'results').iterdir())},calls=calls,
   synthetic_UUID_assignment_explicit=True,raw_original_hierarchies_retained=True,source_binary_rebuilt=False,paper_reproduced=False,new_tool_credit=0,whole_goal_complete=False)
  save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;result=dict(status='SKIP3_QUPATH_REFERENCE_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_QUPATH_REFERENCE',cause=repr(e),traceback=traceback.format_exc(),calls=calls,analysis_admitted=False)
  if owned:save(out/'FAILURE.json',result)
  print(result['status'],repr(e),flush=True);return code
if __name__=='__main__':raise SystemExit(main())
