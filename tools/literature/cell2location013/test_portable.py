#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check literal identities, categorical order, sparse arrays, cycles and every saved NMF model."""
from pathlib import Path
import argparse,json
import anndata as ad
import numpy as np
import pandas as pd
from scipy import sparse
import torch
from Portable import CompleteGraph

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original-return',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
 root=out/'literal-controls';root.mkdir();g=CompleteGraph(root)
 values=np.array([np.nan,np.inf,-np.inf,-0.,0.,np.nextafter(1.,2.)],dtype=np.float64)
 labels=np.array(['NA','β','', 'type_B','type_A','type_Γ'])
 category=pd.Categorical(['type_A','type_B','type_Γ'],categories=['type_B','type_A','type_Γ'],ordered=True)
 matrix=sparse.csr_matrix(np.array([[0,2,0],[4,0,1]],dtype=np.int64));cycle={'literal NA':'NA'};cycle['self']=cycle
 fixture=dict(nonfinite=values,labels=labels,category=category,counts=matrix,cycle=cycle,booleans=[True,False,np.bool_(True)],integers=np.array([-(2**63),2**63-1],dtype=np.int64))
 g.add(fixture,['complete']);g.write('INDEX.json');doc=json.loads((root/'INDEX.json').read_bytes());assert len(doc['nonfinite_arrays'])==1 and not doc['nonfinite_values_repaired'] and not doc['opaque_values']
 arrays=[np.load(root/r['file'],allow_pickle=False) for r in doc['records'] if r['kind']=='array']
 restored=next(x for x in arrays if x.shape==values.shape and x.dtype==values.dtype);assert np.array_equal(restored,values,equal_nan=True) and np.signbit(restored[3]) and not np.signbit(restored[4])
 assert any(x.dtype==labels.dtype and np.array_equal(x,labels) for x in arrays)
 assert any(x.dtype==matrix.data.dtype and np.array_equal(x,matrix.data) for x in arrays)
 assert any(x.dtype.kind in 'iu' and np.array_equal(x,category.codes) for x in arrays)
 assert any(r['kind']=='reference' for r in doc['records']) and [r['value'] for r in doc['records'] if r['kind']=='bool']==[True,False,True]
 snapshots=out/'independent-stage-snapshots';snapshots.mkdir();stages=CompleteGraph(snapshots);shared=np.array([1.0]);stages.add(shared,['before']);shared[0]=2.;stages.add(shared,['after']);stages.write('INDEX.json');stage_index=json.loads((snapshots/'INDEX.json').read_bytes());assert stage_index['schema_version']==2 and stage_index['snapshot_roots']==[['before'],['after']]
 assert [r['kind'] for r in stage_index['records']]==['array','array'] and [np.load(snapshots/r['file'],allow_pickle=False)[0] for r in stage_index['records']]==[1.0,2.0]
 # Inspect every original factor/restart and the entire returned AnnData, not selected matrices.
 returned=torch.load(a.original_return,map_location='cpu');assert isinstance(returned,tuple) and len(returned)==2 and isinstance(returned[1],ad.AnnData)
 target=out/'all-original-returned-objects';target.mkdir();full=CompleteGraph(target);full.add(returned,['complete-original-return']);full.write('INDEX.json')
 index=json.loads((target/'INDEX.json').read_bytes());models=returned[0];assert set(models)=={'n_fact2','n_fact3'}
 model_rows=[r for r in index['records'] if r['kind']=='sklearn.decomposition._nmf.NMF'];assert len(model_rows)==4
 for n,x in models.items():assert len(x['mod'].results)==len(x['mod'].models)==2
 for r in index['records']:
  if r['kind']=='array':
   x=np.load(target/r['file'],allow_pickle=False);assert list(x.shape)==r['shape'] and str(x.dtype)==r['dtype']
 result=dict(status='PASS_LITERAL_NONFINITE_SPARSE_CATEGORICAL_CYCLE_INDEPENDENT_STAGES_AND_ALL_ORIGINAL_NMF_RETENTION',independent_mutable_stage_snapshots=True,original_factor_settings=2,original_restarts_per_factor=2,complete_original_sklearn_models=len(model_rows),original_return_graph_records=len(index['records']),nonfinite_arrays=index['nonfinite_arrays'],opaque_values=index['opaque_values'],nonfinite_values_repaired=False,new_tool_credit=0,whole_goal_complete=False)
 (out/'COMPLETED.json').write_bytes((json.dumps(result,indent=2,ensure_ascii=False)+'\n').encode('utf8'));print(result['status'],flush=True)
if __name__=='__main__':main()
