#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Guard independently retained mutable stages, literal IDs and unrepaired nonfinite values."""
from pathlib import Path
import argparse,json,sys
sys.dont_write_bytecode=True
def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.absolute();out.mkdir(exist_ok=False)
 import anndata as ad,numpy as np,pandas as pd
 from scipy import sparse
 from Portable import CompleteGraph
 graph=CompleteGraph(out);array=np.array([1.0,-0.0,np.nan,np.inf,-np.inf]);graph.add(array,['before']);array[0]=7;graph.add(array,['after'])
 cycle=[];cycle.append(cycle);graph.add(cycle,['cycle1']);graph.add(cycle,['cycle2'])
 data=ad.AnnData(X=sparse.csr_matrix([[0.,1.],[2.,0.]]));data.obs_names=['NA','cell_β'];data.var_names=['NA','gene_Γ'];data.obs['state']=pd.Categorical(['B','A'],categories=['Γ','B','A'],ordered=True);graph.add(data,['adata'])
 data.write_h5ad(out/'original-categorical.h5ad');loaded=ad.read_h5ad(out/'original-categorical.h5ad');graph.add(loaded,['h5ad-reloaded'])
 graph.write('COMPLETE-STAGES.json');doc=json.loads((out/'COMPLETE-STAGES.json').read_bytes());rows=doc['records']
 first=next(r for r in rows if r['key']==['before']);last=next(r for r in rows if r['key']==['after']);x=np.load(out/first['file'],allow_pickle=False);y=np.load(out/last['file'],allow_pickle=False)
 assert x[0]==1 and y[0]==7 and first['kind']==last['kind']=='array'
 assert np.signbit(x[1]) and np.isnan(x[2]) and np.isposinf(x[3]) and np.isneginf(x[4])
 assert len(doc['nonfinite_arrays'])==2 and all((r['nan'],r['positive_infinity'],r['negative_infinity'])==(1,1,1) for r in doc['nonfinite_arrays'])
 assert next(r for r in rows if r['key']==['cycle1',0])['target']==['cycle1']
 assert next(r for r in rows if r['key']==['cycle2',0])['target']==['cycle2']
 assert any(r['kind']=='str' and r['value']=='NA' for r in rows)
 assert any(r['kind']=='str' and r['value']=='cell_β' for r in rows)
 categorical=next(r for r in rows if r['kind']=='pandas.Categorical');assert categorical['ordered']
 reloaded=next(r for r in rows if r['kind']=='pandas.Categorical' and r['key'][0]=='h5ad-reloaded');assert reloaded['ordered'] and reloaded['ordered_scalar_type']==type(loaded.obs['state'].cat.ordered).__module__+'.'+type(loaded.obs['state'].cat.ordered).__qualname__
 assert any(r['kind']=='scipy.sparse.csr_matrix' for r in rows)
 assert not doc['nonfinite_values_repaired'] and not doc['opaque_values']
 result=dict(status='PASS_INDEPENDENT_MUTABLE_STAGES_CYCLES_LITERAL_IDENTIFIERS_CATEGORICALS_SPARSE_AND_NONFINITE_RETENTION',control_groups=8,h5ad_categorical_boolean_regression=True,stage_roots=doc['snapshot_roots'],new_tool_credit=0)
 (out/'COMPLETED.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf8');print(result['status'],flush=True)
if __name__=='__main__':main()
