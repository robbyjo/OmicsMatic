#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Refuse missing scientific cases, changed model values and attempts to hide literal identifiers."""
from pathlib import Path
import argparse,copy,json,shutil,subprocess,sys,time,uuid
import numpy as np
from compare_complete import encoded,load,semantic_key

def save(p,v):p.write_bytes((json.dumps(v,indent=2,ensure_ascii=False)+'\n').encode('utf8'))

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--left',type=Path,required=True);p.add_argument('--right',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();left=a.left.resolve();right=a.right.resolve();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);cases=[]
 def execute(name,folder,expected):
  target=out/(name+'-comparison');cmd=[sys.executable,str(Path(__file__).with_name('compare_complete.py')),'--left',str(left),'--right',str(folder),'--out',str(target)]
  start=time.time();r=subprocess.run(cmd,capture_output=True,timeout=120);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr)
  assert r.returncode==expected,(name,r.returncode,r.stdout[-2000:],r.stderr[-2000:])
  if expected:assert not (target/'COMPLETED.json').exists() and (target/'COMPARISON_ERROR.json').is_file() and (target/'WARNINGS.md').is_file()
  else:assert load(target/'COMPLETED.json')['array_records']==647
  cases.append(dict(id=name,exit_code=r.returncode,expected_exit=expected,elapsed_seconds=time.time()-start))
 def fixture(name):
  f=out/(name+'-negative-input');f.mkdir();shutil.copytree(right/'arrays',f/'arrays')
  for n in ['parameters.json','input-identities.json']:shutil.copyfile(right/n,f/n)
  doc=load(right/'COMPLETE-NUMERICAL-INDEX.json');index={encoded(r['key']):r for r in doc['records']}
  for row in doc['records']:
   k=semantic_key(row['key'],index)
   if k[:3]==['colocation','complete-original-return',0] and k[-2:]==['export_args','path']:row['value']=str(f/'colocation')
  save(f/'NEGATIVE_CONTROL_ONLY.json',dict(source=str(right),reason='Explicit cloned negative test input, not another original analysis; only caller output-location metadata is relocated before the named mutation.'))
  return f,doc,index
 execute('complete-positive',right,0)
 f,d,index=fixture('changed-original-model-array');row=next(r for r in d['records'] if r['kind']=='array' and semantic_key(r['key'],index)[:2]==['reference','live-state'] and np.load(f/r['file'],allow_pickle=False).dtype.kind=='f');x=np.load(f/row['file'],allow_pickle=False);old=x.tobytes();x.flat[0]=np.nextafter(x.flat[0],np.inf,dtype=x.dtype);assert x.tobytes()!=old;np.save(f/row['file'],x,allow_pickle=False);save(f/'COMPLETE-NUMERICAL-INDEX.json',d);execute('changed-original-model-array',f,2)
 f,d,index=fixture('literal-NA-not-repaired');row=next(r for r in d['records'] if r['kind']=='str' and r.get('value')=='NA' and r['key'][0]=='separate-original-selected-gene-identifiers-not-applied-to-models');row['value']='Na';save(f/'COMPLETE-NUMERICAL-INDEX.json',d);execute('literal-NA-not-repaired',f,2)
 f,d,index=fixture('omitted-original-NMF-model');model=next(r for r in d['records'] if r['kind']=='sklearn.decomposition._nmf.NMF');prefix=model['key'];d['records']=[r for r in d['records'] if r['key'][:len(prefix)]!=prefix];save(f/'COMPLETE-NUMERICAL-INDEX.json',d);execute('omitted-original-NMF-model',f,2)
 f,d,index=fixture('changed-NMF-iterations');row=next(r for r in d['records'] if r['kind']=='int' and semantic_key(r['key'],index)[-1]=='n_iter_');row['value']=str(int(row['value'])+1);save(f/'COMPLETE-NUMERICAL-INDEX.json',d);execute('changed-NMF-iterations',f,2)
 f,d,index=fixture('human-identifier-looking-like-UUID');identities=load(f/'input-identities.json');identities['reference_cells'][0]=str(uuid.uuid4());save(f/'input-identities.json',identities);save(f/'COMPLETE-NUMERICAL-INDEX.json',d);execute('human-identifier-looking-like-UUID',f,2)
 f,d,index=fixture('invalid-generated-UUID');row=next(r for r in d['records'] if semantic_key(r['key'],index)[-2:]==['uns','_scvi_uuid']);row['value']='not-a-generated-UUID';save(f/'COMPLETE-NUMERICAL-INDEX.json',d);execute('invalid-generated-UUID',f,2)
 f,d,index=fixture('wrong-output-path');row=next(r for r in d['records'] if semantic_key(r['key'],index)[-2:]==['export_args','path']);row['value']=str(f/'unrelated-path');save(f/'COMPLETE-NUMERICAL-INDEX.json',d);execute('wrong-output-path',f,2)
 f,d,index=fixture('extra-undeclared-array');shutil.copyfile(next((f/'arrays').iterdir()),f/'arrays/extra.npy');save(f/'COMPLETE-NUMERICAL-INDEX.json',d);execute('extra-undeclared-array',f,2)
 f,d,index=fixture('legacy-global-snapshot-schema');d['schema_version']=1;d.pop('snapshot_scope');d.pop('snapshot_roots');save(f/'COMPLETE-NUMERICAL-INDEX.json',d);execute('legacy-global-snapshot-schema',f,2)
 execute('missing-complete-original-input',out/'missing-original',3)
 result=dict(status='PASS_COMPLETE_ORIGINAL_COMPARISON_AND_TEN_REFUSAL_CONTROLS',controls=len(cases),cases=cases,all_original_scientific_arrays_preserved=True,original_sources_modified=False,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],flush=True)
if __name__=='__main__':main()
