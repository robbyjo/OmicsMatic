"""Original public Squidpy function calls; preserves undefined original outputs."""
from pathlib import Path
import argparse,hashlib,json,sys,time,traceback,warnings
parser=argparse.ArgumentParser();parser.add_argument('--inputs',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--expected',required=True);args=parser.parse_args();B=args.out;B.mkdir()
import numpy as np
import pandas as pd
import anndata as ad
import scipy.sparse as sp
import squidpy as sq
import importlib.metadata as metadata
assert metadata.version('squidpy')==args.expected
inputs=args.inputs.read_bytes();spec=json.loads(inputs);assert spec['synthetic_inputs_only']
rows=[];conditions=[];started=time.time()
for graph in spec['graphs']:
 n=graph['n'];names=list(graph['features']);expr=np.array([graph['features'][x] for x in names],dtype=np.float64).T
 for mode in ['moran','geary']:
  for transform in [False,True]:
   for two_tailed in [False,True]:
    data=ad.AnnData(expr.copy());data.var_names=names;g=sp.csr_matrix((np.array(graph['weights'],dtype=np.float64),np.array(graph['columns'],dtype=np.int32),np.array(graph['row_pointers'],dtype=np.int32)),shape=(n,n));data.obsp['declared']=g
    original_args=dict(connectivity_key='declared',genes=names,mode=mode,transformation=transform,n_perms=None,two_tailed=two_tailed,corr_method=None,copy=True,n_jobs=1,show_progress_bar=False)
    with warnings.catch_warnings(record=True) as messages:
     warnings.simplefilter('always');frame=sq.gr.spatial_autocorr(data,**original_args)
    source_order=list(frame.index)
    for gene,row in frame.iterrows():
     record=dict(graph=graph['name'],feature=gene,mode=mode,row_l1_normalized=transform,two_tailed=two_tailed,
      source_row_order=source_order,score=float(row['I' if mode=='moran' else 'C']),variance=float(row['var_norm']),p_value=float(row['pval_norm']),
      point_statistic_finite=bool(np.isfinite(row['I' if mode=='moran' else 'C'])))
     rows.append(record)
    conditions.append(dict(graph=graph['name'],mode=mode,row_l1_normalized=transform,two_tailed=two_tailed,warnings=[str(w.message) for w in messages]))
def encode_number(value):
 if isinstance(value,float) and not np.isfinite(value):return 'NaN' if np.isnan(value) else 'Inf' if value>0 else '-Inf'
 if isinstance(value,list):return [encode_number(x) for x in value]
 if isinstance(value,dict):return {k:encode_number(v) for k,v in value.items()}
 return value
(B/'ORIGINAL-ROWS.json').write_text(json.dumps(encode_number(rows),indent=2)+'\n');(B/'WARNINGS.json').write_text(json.dumps(conditions,indent=2)+'\n')
assert args.inputs.read_bytes()==inputs
packages={x.metadata['Name']:x.version for x in metadata.distributions()};source=Path(sq.__file__).parent
sources={p.relative_to(source).as_posix():dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in source.rglob('*.py')}
result=dict(status='ORIGINAL_PUBLIC_SQUIDPY_SPATIAL_AUTOCORR_CALLED',squidpy=args.expected,python=sys.version,packages=packages,
 inputs=dict(bytes=len(inputs),sha256=hashlib.sha256(inputs).hexdigest()),original_source_files=sources,
 finite_statistic_rows=sum(r['point_statistic_finite'] for r in rows),undefined_statistic_rows=sum(not r['point_statistic_finite'] for r in rows),
 source_undefined_probability_cells_retained_not_admitted=True,native_implementation_executed=False,source_patched=False,new_family_credit=0,seconds=time.time()-started)
(B/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:result[k] for k in ['status','squidpy','finite_statistic_rows','undefined_statistic_rows','seconds']}),flush=True)
