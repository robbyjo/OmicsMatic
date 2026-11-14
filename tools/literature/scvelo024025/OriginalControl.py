#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise original stochastic/PAGA and dynamical/latent-time workflows; candidate only."""
from pathlib import Path
import argparse,hashlib,json,pickle,random,sys,traceback,warnings
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--input',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--version',choices=['0.2.4','0.2.5'],required=True);p.add_argument('--mode',choices=['stochastic','dynamical','both'],default='both');a=p.parse_args();out=a.out.absolute();out.mkdir(exist_ok=False);graph=None;stages=[];conditions=[];figures=[];old_show=warnings.showwarning;exit_code=2
 def show(message,category,filename,lineno,file=None,line=None):
  conditions.append(dict(kind='warning',category=category.__module__+'.'+category.__qualname__,message=str(message),filename=filename,line=lineno));return old_show(message,category,filename,lineno,file,line)
 warnings.showwarning=show
 try:
  import matplotlib;matplotlib.use('Agg')
  import matplotlib.pyplot as plt,numpy as np,pandas as pd,scvelo as scv
  from Portable import CompleteGraph
  if scv.__version__!=a.version:raise ValueError('Exact requested original edition required')
  random.seed(908821);np.random.seed(908821);scv.settings.verbosity=3;scv.settings.figdir=str(out/'plots');scv.settings.file_format_figs='svg'
  (out/'complete-stages').mkdir();graph=CompleteGraph(out/'complete-stages');source=a.input.read_bytes();(out/'original-input.h5ad').write_bytes(source)
  adata=scv.read(out/'original-input.h5ad')
  if adata.shape!=(160,12) or adata.obs_names[0]!='NA' or adata.var_names[0]!='NA':raise ValueError('Literal IDs/complete input population differ')
  (out/'original-calls.json').write_text(json.dumps(dict(version=a.version,seed=908821,preprocessing=dict(min_shared_counts=0,n_top_genes=None),moments=dict(n_neighbors=30,n_pcs=8),recover_dynamics=dict(var_names='all',max_iter=15,n_jobs=1,return_model=True),paga=dict(groups='state',use_time_prior=False),scope='Caller-selected synthetic integration options, not inferred author defaults or convergence.'),indent=2),encoding='utf8')
  def record(name,data,result=None):
   with (out/(name+'-original.pkl')).open('xb') as f:pickle.dump(dict(adata=data,returned=result),f,protocol=5)
   graph.add(data,[name,'AnnData']);graph.add(result,[name,'returned']);graph.add(np.random.get_state(),[name,'numpy_random']);graph.add(random.getstate(),[name,'python_random'])
   data.write_h5ad(out/(name+'.h5ad'));graph.write('COMPLETE-STAGES.json');stages.append(dict(name=name,cells=data.n_obs,genes=data.n_vars));print('retained stage',name,data.shape,flush=True)
  def plot(func,data,filename,**kwargs):
   if '/' in filename or '\\' in filename:raise ValueError('Original plot save argument must be a basename within the declared figure directory')
   before=set((out/'plots').glob('*'));result=func(data,basis='umap',show=False,save=filename,**kwargs);created=set((out/'plots').glob('*'))-before
   if len(created)!=1:raise ValueError('Exactly one original figure file must be retained for each declared plot')
   path=created.pop()
   if not path.resolve().is_relative_to(out.resolve()) or path.is_symlink():raise ValueError('Original figure escaped its declared output directory')
   raw=path.read_bytes()
   if not raw:raise ValueError('Original figure is empty')
   figures.append(dict(file=path.relative_to(out).as_posix(),bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()));return result
  record('01-input',adata)
  result=scv.pp.filter_and_normalize(adata,min_shared_counts=0,n_top_genes=None);record('02-normalized',adata,result)
  result=scv.pp.moments(adata,n_neighbors=30,n_pcs=8);record('03-moments',adata,result)
  if a.mode in ['stochastic','both']:
   stochastic=adata.copy();result=scv.tl.velocity(stochastic,mode='stochastic');record('04-stochastic-velocity',stochastic,result)
   result=scv.tl.velocity_graph(stochastic,n_jobs=1);record('05-stochastic-graph',stochastic,result)
   result=scv.tl.velocity_pseudotime(stochastic);record('06-stochastic-pseudotime',stochastic,result)
   result=scv.tl.paga(stochastic,groups='state',use_time_prior=False);record('07-stochastic-paga',stochastic,result)
   result=scv.tl.velocity_embedding(stochastic,basis='umap');record('08-stochastic-embedding',stochastic,result)
   for kind,func in [('graph',scv.pl.velocity_graph),('arrows',scv.pl.velocity_embedding),('stream',scv.pl.velocity_embedding_stream)]:
    result=plot(func,stochastic,'stochastic-'+kind+'.svg',color='state');record('09-stochastic-plot-'+kind,stochastic);plt.close('all')
  if a.mode in ['dynamical','both']:
   dynamical=adata.copy();model=scv.tl.recover_dynamics(dynamical,var_names='all',max_iter=15,n_jobs=1,return_model=True);record('10-dynamical-recovery',dynamical,model)
   result=scv.tl.velocity(dynamical,mode='dynamical');record('11-dynamical-velocity',dynamical,result)
   result=scv.tl.velocity_graph(dynamical,n_jobs=1);record('12-dynamical-graph',dynamical,result)
   result=scv.tl.latent_time(dynamical);record('13-dynamical-latent-time',dynamical,result)
   result=scv.tl.velocity_embedding(dynamical,basis='umap');record('14-dynamical-embedding',dynamical,result)
   result=plot(scv.pl.velocity_embedding_stream,dynamical,'dynamical-stream.svg',color='latent_time');record('15-dynamical-stream',dynamical);plt.close('all')
  if len(figures)!=dict(stochastic=3,dynamical=1,both=4)[a.mode]:raise ValueError('Complete declared figure population differs')
  result=dict(status='COMPLETED_ORIGINAL_SCVELO_CANDIDATE_STAGES_NOT_ADAPTER_QUALIFICATION',version=a.version,mode=a.mode,stages=stages,figure_files=figures,all_figure_files_beneath_output=True,input_sha256=hashlib.sha256(source).hexdigest(),delivered_warnings=len(conditions),log_condition_count=None,condition_counts_complete=False,warning_review_required=True,comparison_performed=False,provider_qualified=False,convergence_claimed=False,paper_reproduced=False,new_tool_credit=0)
  (out/'COMPLETED.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf8');print(result['status'],flush=True);exit_code=0
 except BaseException as error:
  record=dict(status='FAIL2_ORIGINAL_SCVELO_CANDIDATE_INCOMPLETE',cause=repr(error),traceback=traceback.format_exc(),completed_stages=stages,all_partial_outputs_retained=True,new_tool_credit=0)
  (out/'FAILURE.json').write_text(json.dumps(record,indent=2,ensure_ascii=False)+'\n',encoding='utf8');print(traceback.format_exc(),flush=True);exit_code=2
 finally:
  try:
   if graph is not None:graph.write('COMPLETE-STAGES.json')
  except BaseException:
   exit_code=2;error=traceback.format_exc();print('FAIL2 final recorder error\n'+error,flush=True);(out/'RECORDER_ERROR.txt').write_text(error,encoding='utf8')
  (out/'delivered-warnings.json').write_text(json.dumps(conditions,indent=2,ensure_ascii=False)+'\n',encoding='utf8');warnings.showwarning=old_show
  (out/'WARNINGS.md').write_text('Candidate original run only; no adapter qualification, comparison, convergence or paper reproduction. Preserve every warning, complete process stdout/stderr, raw graph, original objects and partial outputs. Log condition totals remain unavailable, never zero. A failure admits no complete result; correct its cause and retry NEW paths without dropping stages.\n',encoding='utf8')
 return exit_code
if __name__=='__main__':raise SystemExit(main())
