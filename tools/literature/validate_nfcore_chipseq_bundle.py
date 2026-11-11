# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify a complete typed workflow result on three JVMs and retain complete deliberate-failure derivatives."""
import argparse,hashlib,json,shutil,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_nfcore_engine import BASE,JAVA,JAVA25,DISTRO,linux

def replace(path,raw):
 # Separate complete derivative copies are used; original producer and admitted result are never edited.
 path.write_bytes(raw)

def refresh(root):
 manifest=json.loads((root/'manifest.json').read_bytes());files={};directories=[]
 for p in root.rglob('*'):
  name=p.relative_to(root).as_posix()
  if name in ['manifest.json','CANDIDATE.json','COMPLETED.json']:continue
  assert not p.is_symlink()
  if p.is_dir():directories.append(name)
  elif p.is_file():files[name]=pin(p)
 manifest.update(files=files,directories=directories);save(root/'manifest.json',manifest)
 for name in ['CANDIDATE.json','COMPLETED.json']:
  value=json.loads((root/name).read_bytes());value['manifest']=pin(root/'manifest.json');save(root/name,value)

def mutate(root,name):
 def edit(path,key,value):
  p=root/path;obj=json.loads(p.read_bytes());obj[key]=value;save(p,obj)
 if name=='extra_file':(root/'unexpected.txt').write_bytes(b'UNDECLARED\n');return
 if name=='candidate_scope':edit('CANDIDATE.json','biological_accuracy_certified',True);return
 if name=='publication_census':
  p=root/'workflow.json';obj=json.loads(p.read_bytes());obj['complete_published_files']+=1;save(p,obj)
 elif name=='biological_claim':edit('workflow.json','biological_accuracy_certified',True)
 elif name=='wrong_implementation':edit('workflow.json','implementation',dict(bytes=1,sha256='0'*64))
 elif name=='wrong_image':edit('workflow.json','original_image',dict(bytes=1131143168,sha256='0'*64))
 elif name=='read_mode':edit('request.json','read_mode','single')
 elif name=='recipe_changed':
  p=root/'engine-recipe.json';obj=json.loads(p.read_bytes());obj['parameters']['macs_gsize']='hs';save(p,obj)
 elif name=='process_ledger_missing':(root/'processes/engine/process.json').unlink()
 elif name=='live_child':edit('processes/engine/process.json','owned_live_after_cleanup',[dict(pid=123,startTicks=456)])
 elif name=='failed_component_process':edit('processes/annotation-QC/process.json','exit_code',2)
 elif name=='false_differential_not_requested':
  report=json.loads((root/'workflow.json').read_bytes());save(root/'audits/differential/NOT_REQUESTED.json',dict(reason='original-replicates-and-multiple-groups-condition-false',negative_finding=False,engine_manifest=report['engine_manifest']))
 elif name=='other_engine_component':
  p=root/'audits/annotation-QC/audit.json';obj=json.loads(p.read_bytes());obj['original_engine_manifest']=dict(bytes=1,sha256='0'*64);save(p,obj);v=pin(p);edit('audits/annotation-QC/COMPONENT_AUDIT_COMPLETED.json','audit',v);q=root/'workflow.json';obj=json.loads(q.read_bytes());next(x for x in obj['source_components'] if x['component']=='annotation-QC')['audit']=v;save(q,obj)
 elif name=='late_original_object':
  p=max((root/'engine/objects').iterdir(),key=lambda x:x.stat().st_size)
  with p.open('r+b') as f:f.seek(-1,2);v=f.read(1);f.seek(-1,2);f.write(bytes([v[0]^1]))
 else:raise AssertionError(name)
 refresh(root)

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--result',type=Path,required=True);p.add_argument('--jar21',type=Path,required=True);p.add_argument('--jar25',type=Path,required=True);a=p.parse_args();out=a.out.resolve();result=a.result.resolve();assert out.is_relative_to(BASE) and not out.exists() and not result.is_relative_to(out);out.mkdir();state=dict(status='FAIL2_TYPED_BUNDLE_CONTROLS',new_tool_credit=0,scientific_analysis_executed=False)
 try:
  guards={p.relative_to(result).as_posix():pin(p) for p in result.rglob('*') if p.is_file()};jars={c:Path(getattr(a,'jar'+c)).resolve() for c in ['21','25']};artifacts={c:pin(p) for c,p in jars.items()};checks=[];refusals=[]
  def check(label,selected,compiler,runtime,expected):
   jar=jars[compiler]
   if runtime=='linux21':cmd=['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx2048m','-jar',linux(jar),'--verify',linux(selected)]
   else:cmd=[str((JAVA if runtime=='windows21' else JAVA25)/'java.exe'),'-Xmx2048m','-jar',str(jar),'--verify',str(selected)]
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:r=subprocess.run(cmd,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=1800)
   save(out/(label+'.process.json'),dict(command=cmd,exit_code=r.returncode));assert r.returncode==expected,(label,r.returncode,(out/(label+'.stderr')).read_text(encoding='utf8')[-2000:]);return dict(case=label,compiler=compiler,runtime=runtime,exit_code=expected,passed=True,stdout=pin(out/(label+'.stdout')),stderr=pin(out/(label+'.stderr')))
  for compiler in ['21','25']:
   for runtime in ['windows21','windows25','linux21']:checks.append(check(compiler+'-'+runtime,result,compiler,runtime,0))
  negatives=['extra_file','candidate_scope','publication_census','biological_claim','wrong_implementation','wrong_image','read_mode','recipe_changed','process_ledger_missing','live_child','failed_component_process','false_differential_not_requested','other_engine_component','late_original_object']
  for name in negatives:
   derivative=out/'derivatives'/name;shutil.copytree(result,derivative);mutate(derivative,name);refusals.append(check(name,derivative,'21','windows21',2))
  wrapper=out/'latest-state';selected=wrapper/'results/000001';shutil.copytree(result,selected);save(wrapper/'latest.json',dict(attempt='000001',status='COMPLETED'));checks.append(check('latest-completed',wrapper,'21','windows21',0));save(out/'latest-completed-pointer.json',dict(attempt='000001',status='COMPLETED'));save(wrapper/'latest.json',dict(attempt='000002',status='FAIL2'));refusals.append(check('latest-failed-no-fallback',wrapper,'21','windows21',2));checks.append(check('explicit-retained-success',selected,'21','windows21',0))
  assert {p.relative_to(result).as_posix():pin(p) for p in result.rglob('*') if p.is_file()}==guards and all(pin(jars[c])==v for c,v in artifacts.items());state.update(status='PASS_COMPLETE_TYPED_BUNDLE_CONTROLS_ONLY',checks=checks,refusals=refusals,artifacts=artifacts,complete_original_result_files=len(guards),complete_original_result_bytes=sum(v['bytes'] for v in guards.values()),original_result_unchanged=guards,scientific_outputs_not_rerun=True,hashes_establish_authenticity=False)
 except Exception as error:state.update(cause=repr(error),traceback=traceback.format_exc())
 save(out/'COMPLETED.json',state);print(state['status'],state.get('cause',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable complete bundle and original-preservation checks')
 raise SystemExit(main())
