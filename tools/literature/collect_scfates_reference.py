# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture complete original CPU arithmetic and tree/root/pseudotime observations as primitive data only."""
import argparse,hashlib,importlib,inspect,json,os,sys,traceback,warnings
from pathlib import Path

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--dependencies',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute();assert out==out.resolve() and not out.exists();out.mkdir(parents=True);assert sys.flags.dont_write_bytecode and sys.flags.safe_path and sys.flags.no_user_site and not sys.flags.ignore_environment and not sys.flags.optimize and os.environ['PYTHONHASHSEED']=='0';sys.path.insert(0,str(a.dependencies))
 import numpy as np
 import pandas as pd
 import scFates as scf
 import simpleppt as spp
 import anndata as ad
 from scipy import sparse
 from sklearn.metrics import pairwise_distances
 assert scf.__version__=='1.2.5' and spp.__version__=='1.1.7' and np.__version__=='1.26.4' and pd.__version__=='2.2.3'
 class Writer:
  def __init__(self,home):self.home=home;self.objects=home/'objects';self.objects.mkdir(exist_ok=True)
  def array(self,value):
   value=np.asarray(value)
   if value.dtype.kind not in 'biuf':return dict(kind='object-array',dtype=str(value.dtype),shape=list(value.shape),values=self.clean(value.tolist()))
   dtype=value.dtype.newbyteorder('<');assert dtype.itemsize in [1,2,4,8];raw=np.ascontiguousarray(value.astype(dtype,copy=False)).tobytes(order='C');sha=hashlib.sha256(raw).hexdigest();file=self.objects/(sha+'.bin')
   if file.exists():assert file.read_bytes()==raw
   else:
    with file.open('xb') as stream:stream.write(raw)
   return dict(kind='binary-numeric-array',dtype=dtype.str,shape=list(value.shape),path='objects/'+file.name,bytes=len(raw),sha256=sha)
  def dtype(self,value):
   if isinstance(value,pd.CategoricalDtype):return dict(name=str(value),categories=self.clean(value.categories.tolist()),ordered=value.ordered)
   return dict(name=str(value))
  def clean(self,value):
   if value is pd.NA:return dict(kind='pandas-NA')
   if isinstance(value,pd.DataFrame):return dict(kind='dataframe',index=self.clean(value.index.tolist()),index_names=self.clean(value.index.names),columns=self.clean(value.columns.tolist()),column_names=self.clean(value.columns.names),dtypes=[self.dtype(d) for d in value.dtypes],column_data=[self.array(value.iloc[:,i].to_numpy()) for i in range(len(value.columns))])
   if isinstance(value,pd.Series):return dict(kind='series',index=self.clean(value.index.tolist()),name=self.clean(value.name),dtype=self.dtype(value.dtype),data=self.array(value.to_numpy()))
   if isinstance(value,pd.Index):return dict(kind='index',values=self.clean(value.tolist()))
   if sparse.issparse(value):
    csr=value.tocsr();return dict(kind='csr',shape=list(csr.shape),data=self.array(csr.data),indices=self.array(csr.indices),indptr=self.array(csr.indptr))
   if isinstance(value,np.ndarray):return self.array(value)
   if isinstance(value,np.generic):return self.clean(value.item())
   if isinstance(value,float) and not np.isfinite(value):return dict(kind='nonfinite-scalar',value='NaN' if np.isnan(value) else '+Inf' if value>0 else '-Inf')
   if isinstance(value,dict):return {str(k):self.clean(v) for k,v in value.items()}
   if isinstance(value,(list,tuple)):return [self.clean(v) for v in value]
   if value is None or isinstance(value,(bool,int,float,str)):return value
   raise TypeError('Uncaptured original data type: '+str(type(value)))
  def save(self,name,value):
   path=self.home/name;assert not path.exists();path.write_text(json.dumps(self.clean(value),ensure_ascii=True,allow_nan=False,separators=(',',':'))+'\n',encoding='utf8')
 def raw_save(path,value):path.write_text(json.dumps(value,ensure_ascii=True,allow_nan=False,indent=2)+'\n',encoding='utf8')
 modules=[importlib.import_module(n) for n in ['simpleppt.ppt','simpleppt.utils','scFates.tools.graph_fitting','scFates.tools.root','scFates.tools.pseudotime']];functions=[(m,n,v,v.__code__) for m in modules for n,v in inspect.getmembers(m,inspect.isfunction) if v.__module__==m.__name__];original_ppt=spp.ppt;original_core=importlib.import_module('simpleppt.utils');rng=np.random.default_rng(20261108);arithmetic=[];workflows=[]
 state=dict(status='FAIL2_ORIGINAL_SCFATES_REFERENCE_FAMILY',implementation_credit=False,whole_goal_complete=False,namespace_patched=False,user_pickle_loaded=False,analysis_admitted=False)
 try:
  # Actual original compiled Numba functions, not their .py_func substitutes.
  for k in [1,2,3,7,8,17,65,129,257,3000]:
   for sigma in [.0005,.5,2.0]:
    name='weights-k'+str(k)+'-sigma'+str(sigma);home=out/name;home.mkdir();w=Writer(home);dist=rng.uniform(0,10,(5,k));dist[0]=0;dist[1]=1000;dist[-1,0]=-0.0;R=dist.copy();original_core.process_R_cpu(R,sigma);kernel=R.copy();sums=R.sum(axis=1);original_core.norm_R_cpu(R,sums);w.save('CASE.json',dict(id=name,method='assignments',distances=dist,sigma=sigma,kernel=kernel,row_sums=sums,assignments=R,source_zero_rows=int(np.sum(sums==0)),source_nan_mapped_to_zero=int(np.sum(sums==0))*k));arithmetic.append(dict(id=name,method='assignments'))
  for name,old,new in [('signed-coordinate-cancellation',[[1.,3.]],[[2.,2.]]),('zero-norm-zero-change',[[0.,0.]],[[0.,0.]]),('zero-norm-nonzero-change',[[1.,2.]],[[0.,0.]]),('overflowed-literal-squares',[[0.,0.]],[[1e200,1e200]]),('ordinary-three-nodes',rng.normal(size=(3,4)),rng.normal(size=(3,4)))]:
   home=out/name;home.mkdir();w=Writer(home);old=np.asarray(old,float);new=np.asarray(new,float)
   with np.errstate(all='ignore'):error=np.max(np.sqrt((new.T-old.T).sum(axis=0)**2)/np.sqrt((new.T**2).sum(axis=0)))
   w.save('CASE.json',dict(id=name,method='convergence',previous_nodes=old,new_nodes=new,error=error));arithmetic.append(dict(id=name,method='convergence'))
  for name,n,k,d,zero,weights,constant in [('conditional-small',9,4,3,False,False,False),('conditional-weighted',17,7,4,False,True,False),('conditional-zero-assignments',11,5,3,True,False,False),('score-zero-coordinate-variance',10,4,3,False,False,True),('conditional-3000-nodes',7,3000,2,False,False,False)]:
   home=out/name;home.mkdir();w=Writer(home);X=rng.normal(size=(n,d));F=rng.normal(size=(k,d));R=rng.uniform(.1,1,(n,k));R/=R.sum(axis=1)[:,None]
   if zero:R[0,:]=0;R[1,0]=0
   if constant:X[0]=1;F[0]=1
   W=rng.uniform(-.2,2,(n,d)) if weights else None;B=np.zeros((k,k),dtype=np.uint8)
   for j in range(k-1):B[j,j+1]=1;B[j+1,j]=1
   lam=200.;sigma=.0005;L=np.identity(k)*B.sum(axis=0)-B;M=L*lam+np.identity(k)*R.sum(axis=0);rhs=np.dot(X.T*(np.ones_like(X.T) if W is None else W.T),R).T;dist=pairwise_distances(F)
   with np.errstate(all='ignore'):scores=[np.sum((1-original_core.cor_mat_cpu(F.T,X.T))*R)/n,sigma/n*np.sum(R*np.log(R)),lam/2*np.sum(dist*B)]
   w.save('CASE.json',dict(id=name,method='conditional',points=X,nodes=F,assignments=R,weights=W,adjacency=B,sigma=sigma,lam=lam,system=M,right_hand_side=rhs,pre_update_distances=dist,scores=scores));arithmetic.append(dict(id=name,method='conditional'))
  # Complete original calls preserve different source stages, RNG and all data-only outputs.
  class Observer:
   def __init__(self,w):self.w=w
   def trace(self,frame,event,value):
    if event=='call' and frame.f_code is original_ppt.__code__:frame.f_trace_lines=False;frame.f_trace_opcodes=False;return self.local
    return None
   def local(self,frame,event,value):
    if event=='return' and value is not None:
     keys=['X_cpu','W','old_F','F_mat_cpu','R','B','L','M','d','score','sigma','lam','metric','nsteps','err_cut','err','i','co_nodes','Nodes'];self.w.save('observed-final-ppt-step.json',dict(locals={k:frame.f_locals[k] for k in keys if k in frame.f_locals},returned=vars(value)))
    return self.local
  def run(name,n=180,nodes=20,sigma=.1,lam=1.,steps=15,maps=1,jobs=1,weighted=False,init=False,auto_root=False,paper=False,line_curve=False,expected_error=None):
   home=out/name;home.mkdir();w=Writer(home);case=dict(id=name,cells=n,nodes=nodes,sigma=sigma,lam=lam,nsteps=steps,err_cut=.00001,tree_seed=11,global_seed=8223,pseudotime_seed=27,n_map=maps,n_jobs=jobs,weights=weighted,supplied_initialization=init,automatic_root=auto_root,declared_paper_parameter_control=paper,paper_reproduced=False,expected_error=expected_error)
   if nodes==3000 or line_curve:
    t=np.linspace(0,1,n);X=np.column_stack([t+.2,.3*t+.01+.05*t*t,.1*t+.03+.005*np.sin(np.pi*t)])
   else:
    t=np.tile(np.linspace(0,1,n//2),2);branch=np.repeat([-1,1],n//2);X=np.column_stack([4*t,branch*t**1.2,.2*t])+rng.normal(0,.003,(n,3))
   cells=['cell-'+str(i).zfill(5) for i in range(n)];adata=ad.AnnData(np.empty((n,0)),obs=pd.DataFrame(index=cells));adata.obsm['coordinates']=X.copy();adata.obs['root_signal']=np.linspace(0,1,n);weight_rep=None
   if weighted:adata.obsm['input_weights']=rng.uniform(.25,1.5,X.shape);weight_rep='input_weights'
   initialization=pd.DataFrame(X[np.linspace(0,n-1,min(nodes,10)).astype(int)]) if init else None;w.save('input.json',dict(cells=cells,coordinates=X,weights=adata.obsm['input_weights'] if weighted else None,initialization=initialization,root_signal=adata.obs['root_signal']));np.random.seed(case['global_seed']);w.save('rng-before.json',np.random.get_state());observer=Observer(w);seen=[];print('START',name,flush=True)
   try:
    with warnings.catch_warnings(record=True) as seen:
     assert sys.gettrace() is None;sys.settrace(observer.trace)
     try:scf.tl.tree(adata,Nodes=nodes,use_rep='coordinates',ndims_rep=None,weight_rep=weight_rep,method='ppt',init=initialization,ppt_sigma=sigma,ppt_lambda=lam,ppt_metric='euclidean',ppt_nsteps=steps,ppt_err_cut=case['err_cut'],device='cpu',plot=False,seed=case['tree_seed'],copy=False)
     finally:sys.settrace(None)
     w.save('tree-return.json',dict(ppt=adata.uns['ppt'],graph=adata.uns['graph'],assignments=adata.obsm['X_R']));scf.tl.root(adata,'root_signal' if auto_root else 0,tips_only=auto_root,min_val=auto_root,copy=False);w.save('root-return.json',adata.uns['graph']);scf.tl.pseudotime(adata,n_jobs=jobs,n_map=maps,seed=case['pseudotime_seed'],copy=False);w.save('pseudotime-return.json',dict(obs=adata.obs,graph=adata.uns['graph'],pseudotime_list=adata.uns['pseudotime_list'],assignments=adata.obsm['X_R']));assert expected_error is None,'Expected original error was absent';case.update(status='complete-original-workflow-only',returned_nodes=adata.obsm['X_R'].shape[1],zero_assignment_rows=int(np.sum(adata.obsm['X_R'].sum(axis=1)==0)))
   except Exception as failure:
    w.save('failed-current-state.json',dict(obs=adata.obs,graph=adata.uns.get('graph'),assignments=adata.obsm.get('X_R')));case.update(status='original-error',error_type=type(failure).__name__,error=str(failure));(home/'original-error.traceback').write_text(traceback.format_exc(),encoding='utf8');assert expected_error==type(failure).__name__,(name,expected_error,type(failure).__name__)
   finally:sys.settrace(None);w.save('rng-after.json',np.random.get_state());case['original_warnings']=[dict(type=v.category.__name__,message=str(v.message)) for v in seen]
   assert all(m.__dict__[name] is function and function.__code__ is code for m,name,function,code in functions);raw_save(home/'CASE.json',case);workflows.append(case);print('COMPLETE',name,case['status'],flush=True)
  run('tree-root-project-likelihood');run('weighted-coordinates',weighted=True);run('supplied-initialization',nodes=10,init=True);run('automatic-tip-root',auto_root=True);run('probabilistic-mappings',maps=3);run('parallel-probabilistic-mappings',maps=3,jobs=2);run('two-node-tree',nodes=2);run('paper-200-node-settings',n=480,nodes=200,sigma=.5,lam=100.,steps=3,paper=True,expected_error='IndexError');run('paper-200-node-curved-coordinate-control',n=1000,nodes=200,sigma=.5,lam=100.,steps=10,paper=True,line_curve=True);run('paper-3000-node-settings',n=3200,nodes=3000,sigma=.0005,lam=200.,steps=1,paper=True);run('source-too-many-nodes',nodes=200,expected_error='ValueError');run('source-zero-iterations',steps=0,expected_error='UnboundLocalError');raw_save(out/'arithmetic-cases.json',arithmetic);state.update(status='PASS_COMPLETE_ORIGINAL_SCFATES_CPU_ARITHMETIC_AND_WORKFLOWS_ONLY',arithmetic_cases=arithmetic,workflows=workflows,successful_original_workflows=sum(c['status']=='complete-original-workflow-only' for c in workflows),expected_original_errors=sum(c['status']=='original-error' for c in workflows),original_namespace_functions_unchanged=True)
 except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
 raw_save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2
if __name__=='__main__':
 if not __debug__:raise RuntimeError('Never disable original reference checks')
 raise SystemExit(main())
