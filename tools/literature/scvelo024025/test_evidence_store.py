#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay a complete original population through bounded archives and refuse damaged stores."""
from pathlib import Path
import argparse,copy,json,sys,zipfile
sys.dont_write_bytecode=True
from EvidenceStore import EvidenceStore,pin,rawpin
from compare_complete import compare

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();source=a.reference.resolve();out=a.out.resolve()
 if out.is_relative_to(source) or source.is_relative_to(out):raise ValueError('NEW archive test output must not overlap original results')
 out.mkdir(parents=True,exist_ok=False);paths=sorted(source.rglob('*'));files={};dirs={'example':dict(mode=0o755)};objects={}
 for path in paths:
  if path.is_symlink():raise ValueError('Complete original regular-file population required')
  name='example/'+path.relative_to(source).as_posix()
  if path.is_dir():dirs[name]=dict(mode=0o755)
  elif path.is_file():
   row=pin(path);files[name]=dict(mode=0o644,**row);objects.setdefault(row['sha256'],path)
  else:raise ValueError('Unexpected original file type')
 before={p.relative_to(source).as_posix():pin(p) for p in paths if p.is_file()}
 index=dict(schema_version=2,storage='sha256-objects-in-complete-bounded-zips',files=files,links={},directories=dirs,special_nodes={},object_archives={})
 archives=[zipfile.ZipFile(out/('objects-'+str(i).zfill(4)+'.zip'),'x',compression=zipfile.ZIP_DEFLATED) for i in [1,2]]
 try:
  for i,(digest,path) in enumerate(sorted(objects.items())):
   part=i%2;archives[part].writestr('objects/'+digest,path.read_bytes());index['object_archives'][digest]='objects-'+str(part+1).zfill(4)+'.zip'
 finally:
  for archive in archives:archive.close()
 expected={p.name:pin(p) for p in out.glob('objects-*.zip')};(out/'INDEX.json').write_text(json.dumps(index,indent=2),encoding='utf8')
 cases=[]
 with EvidenceStore(out,index) as store:
  summary=store.verify(expected);result=compare(store.path('example'),store.path('example'),'0.2.4','stochastic')
  assert result['complete_result_files']==453 and result['arrays']==422 and result['elements']==510521
  selected=store.materialize('example/original-calls.json',out/'selected-original');assert selected.read_bytes()==(source/'original-calls.json').read_bytes()
  cases.append('complete-original-scientific-population-and-selected-materialization')
 def refuse(name,change):
  mutated=copy.deepcopy(index);change(mutated)
  (out/(name+'.json')).write_text(json.dumps(mutated,indent=2),encoding='utf8')
  try:
   with EvidenceStore(out,mutated) as store:store.verify(expected)
  except (ValueError,KeyError):cases.append(name)
  else:raise AssertionError('Altered complete evidence accepted: '+name)
 refuse('missing-logical-file',lambda d:d['files'].pop('example/original-calls.json'))
 refuse('missing-object-route',lambda d:d['object_archives'].pop(next(iter(d['object_archives']))))
 refuse('cross-kind-link-collision',lambda d:d['links'].update({'example/original-calls.json':dict(target='/etc/passwd',mode=0o777)}))
 refuse('escaping-logical-path',lambda d:d['files'].update({'../escape':next(iter(d['files'].values()))}))
 refuse('escaping-archive-route',lambda d:d['object_archives'].update({next(iter(d['object_archives'])):'../objects-0001.zip'}))
 damaged=dict(expected);damaged['objects-0001.zip']=rawpin((out/'objects-0001.zip').read_bytes()+b'controlled damage')
 try:
  with EvidenceStore(out,index) as store:store.verify(damaged)
 except ValueError:cases.append('exact-archive-byte-identity-refusal')
 else:raise AssertionError('Altered complete archive identity accepted')
 assert before=={p.relative_to(source).as_posix():pin(p) for p in paths if p.is_file()}
 result=dict(status='PASS_COMPLETE_ORIGINAL_REPLAY_THROUGH_MULTIPART_STORE_AND_SIX_REFUSALS',controls=len(cases),refusals=6,cases=cases,complete_original_comparison=result,store=summary,original_files_unchanged=True,new_tool_credit=0,whole_goal_complete=False)
 (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(result['status'],flush=True)
if __name__=='__main__':main()
