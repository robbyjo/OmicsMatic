#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare every retained scientific graph value; enumerate generated identity/path/time exceptions."""
from pathlib import Path
import argparse,hashlib,json,re,sys,traceback,uuid
import numpy as np
OWNED_OUT=None
ACTION='SKIP3 skips dependent comparison when exact permitted complete original inputs are unavailable. Supply those prerequisites and retry NEW paths. FAIL2 admits no complete comparison after a scientific value, literal identity, full graph population or integrity mismatch. Retain both complete original runs, every raw object/array, graph, warning/error, partial comparison and full console; correct the cause without dropping cases and retry NEW paths. Only explicitly identified generated scvi UUIDs, original elapsed-time strings and exact caller output paths are metadata exceptions. This is original-runtime integration repeatability, not independent statistical validation, paper reproduction or a finished execution adapter.'

def load(p):return json.loads(p.read_bytes())
def encoded(v):return json.dumps(v,ensure_ascii=False,separators=(',',':'))
def pin(p):
 b=p.read_bytes();return dict(bytes=len(b),sha256=hashlib.sha256(b).hexdigest())

def semantic_key(key,index):
 result=[];i=0
 while i<len(key):
  prefix=key[:i];node=index.get(encoded(prefix))
  if node and node['kind'] in ['builtins.dict','collections.OrderedDict'] and key[i]=='item' and i+1<len(key):
   literal=index[encoded(prefix+['keys',key[i+1]])]
   if literal['kind'] not in ['str','int','bool']:raise ValueError('Unsupported dictionary key type; compare complete raw graph rather than omit keys')
   result.append(literal['value']);i+=2
  else:result.append(key[i]);i+=1
 return result

def metadata_exception(key,left,right,left_root,right_root):
 if len(key)>=2 and key[-2]=='uns' and key[-1] in ['_scvi_uuid','_scvi_manager_uuid']:
  assert left['kind']==right['kind']=='str'
  for row in [left,right]:assert str(uuid.UUID(row['value']))==row['value']
  return 'Original scvi-generated manager/object UUID; both untouched raw values retained.'
 if key[:3]==['colocation','complete-original-return',0] and len(key)==6 and key[3] in ['n_fact2','n_fact3'] and key[-2:]==['export_args','path']:
  assert left['kind']==right['kind']=='str' and left['value']==str(left_root/'colocation') and right['value']==str(right_root/'colocation')
  return 'Only the exact caller-selected output directory; both raw original paths retained.'
 if key[:3]==['colocation','complete-original-return',0] and len(key)==5 and key[3] in ['n_fact2','n_fact3'] and key[-1]=='run_time':
  for row in [left,right]:assert row['kind']=='str' and re.fullmatch(r'[0-9]+(?:\.[0-9]+)? min',row['value'])
  return 'Original elapsed wall time; both raw timing strings retained.'
 return None

def main():
 global OWNED_OUT
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--left',type=Path,required=True);p.add_argument('--right',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();left=a.left.resolve();right=a.right.resolve();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
 OWNED_OUT=out
 docs=[load(x/'COMPLETE-NUMERICAL-INDEX.json') for x in [left,right]]
 for root,doc in zip([left,right],docs):
  assert doc['schema_version']==2 and doc['snapshot_scope']=='independent-top-level-stage-values' and not doc['opaque_values'] and not doc['nonfinite_values_repaired']
  parameters=load(root/'parameters.json')
  assert sum(r['kind']=='sklearn.decomposition._nmf.NMF' for r in doc['records'])==len(parameters['nmf_factors'])*parameters['nmf_restarts'],'Incomplete original factor/restart model population'
  keys={encoded(r['key']) for r in doc['records']}
  for model in ['reference','spatial']:
   for component in ['live-state','complete-training-history','complete-posterior-samples','complete-exported-anndata','loaded-complete-module-state']:assert encoded([model,component]) in keys,('Missing complete original stage',model,component)
  for component in ['complete-original-return','complete-original-iteration-status']:assert encoded(['colocation',component]) in keys
  declared={r['file'] for r in doc['records'] if r['kind']=='array'};assert declared=={p.relative_to(root).as_posix() for p in (root/'arrays').rglob('*') if p.is_file()}
 assert docs[0]['nonfinite_arrays']==docs[1]['nonfinite_arrays'],'Nonfinite value declarations differ'
 assert docs[0]['snapshot_roots']==docs[1]['snapshot_roots'],'Complete independently recorded stage population differs'
 index=[{encoded(r['key']):r for r in d['records']} for d in docs]
 assert all(len(i)==len(d['records']) for i,d in zip(index,docs)) and set(index[0])==set(index[1])
 arrays=[];metadata=[];scalars=0;elements=0
 try:
  for l in docs[0]['records']:
   r=index[1][encoded(l['key'])];logical=semantic_key(l['key'],index[0]);assert logical==semantic_key(r['key'],index[1])
   reason=metadata_exception(logical,l,r,left,right)
   if reason:metadata.append(dict(key=logical,left=l,right=r,reason=reason));continue
   assert l==r,('Complete graph metadata/scalar differs',logical,l,r)
   if l['kind']=='array':
    lp=left/l['file'];rp=right/r['file'];x=np.load(lp,allow_pickle=False);y=np.load(rp,allow_pickle=False)
    assert list(x.shape)==l['shape'] and str(x.dtype)==l['dtype'] and x.shape==y.shape and x.dtype==y.dtype and x.tobytes(order='C')==y.tobytes(order='C'),('Complete original array differs',logical)
    arrays.append(dict(key=logical,file=l['file'],shape=list(x.shape),dtype=str(x.dtype),elements=x.size,left=pin(lp),right=pin(rp),equal_at_tolerance_zero=True));elements+=x.size
   else:scalars+=1
  assert load(left/'input-identities.json')==load(right/'input-identities.json') and (left/'parameters.json').read_bytes()==(right/'parameters.json').read_bytes()
  result=dict(status='PASS_ALL_ORIGINAL_CELL2LOCATION013_GRAPH_VALUES_AT_TOLERANCE_ZERO',graph_records=len(docs[0]['records']),array_records=len(arrays),array_elements=int(elements),other_graph_records=scalars,metadata_exceptions=metadata,arrays=arrays,all_declared_numerical_arrays_checked=True,all_original_NMF_models_and_restarts_checked=True,opaque_objects_omitted=False,nonfinite_values_repaired=False,pdf_byte_parity_claimed=False,opaque_pickle_byte_parity_claimed=False,paper_reproduced=False,new_tool_credit=0,whole_goal_complete=False)
  (out/'COMPLETED.json').write_bytes((json.dumps(result,indent=2,ensure_ascii=False)+'\n').encode('utf8'));print(result['status'],'arrays='+str(len(arrays)),'elements='+str(elements),flush=True)
 except BaseException as error:
  (out/'FAILURE.json').write_bytes((json.dumps(dict(status='FAIL2_COMPLETE_COMPARISON_UNADMITTED',cause=repr(error),matched_arrays=arrays,metadata_exceptions=metadata),indent=2,ensure_ascii=False)+'\n').encode('utf8'));raise
if __name__=='__main__':
 try:main()
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
  status='SKIP3_COMPLETE_ORIGINAL_COMPARISON_UNAVAILABLE' if code==3 else 'FAIL2_COMPLETE_COMPARISON_UNADMITTED'
  if OWNED_OUT is not None:
   for name,raw in [('COMPARISON_ERROR.json',(json.dumps(dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION),indent=2,ensure_ascii=False)+'\n').encode('utf8')),('WARNINGS.md',(status+'\n'+repr(error)+'\n'+ACTION+'\n').encode('utf8'))]:
    try:(OWNED_OUT/name).write_bytes(raw)
    except BaseException as recording:print('FAIL2: could not write',name,repr(recording),'retain full console',flush=True);code=2
  print(status,repr(error),'\n'+ACTION,flush=True);raise SystemExit(code)
