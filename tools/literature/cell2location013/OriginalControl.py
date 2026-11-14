#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise unchanged original reference, spatial, posterior/save-load and complete NMF-wrapper populations."""
from pathlib import Path
import argparse,contextlib,hashlib,io,json,os,random,traceback
import anndata as ad
import cell2location as c2l
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import pyro
import scvi
import torch
from Portable import CompleteGraph

def analyze(inputs,out):
 p=json.loads((inputs/'parameters.json').read_bytes());out.mkdir(parents=True,exist_ok=False);graph=CompleteGraph(out);records=graph.records;complete=graph.add
 (out/'parameters.json').write_bytes((inputs/'parameters.json').read_bytes());torch.set_num_threads(1);scvi.settings.seed=p['seed'];pyro.clear_param_store()
 ref=ad.read_h5ad(inputs/'reference.h5ad');spatial=ad.read_h5ad(inputs/'spatial.h5ad');assert ref.shape==(p['reference_cells'],p['genes']) and spatial.shape==(p['spatial_locations'],p['genes']) and list(ref.var_names)==list(spatial.var_names)
 def save_json(name,value):(out/name).write_text(json.dumps(value,indent=2,ensure_ascii=False,allow_nan=False)+'\n',encoding='utf8')
 def snapshot(model,name):
  state=dict(module=model.module.state_dict(),pyro_param_store=pyro.get_param_store().get_state());torch.save(state,out/(name+'-complete-live-state.pt'));complete(state,[name,'live-state']);complete(model.history,[name,'complete-training-history'])
 save_json('input-identities.json',dict(reference_cells=list(ref.obs_names),spatial_locations=list(spatial.obs_names),genes=list(ref.var_names),cell_type_categories=list(ref.obs['cell_type'].cat.categories),reference_batches=list(ref.obs['sample'].cat.categories),spatial_batches=list(spatial.obs['sample'].cat.categories)))
 try:
  from cell2location.utils.filtering import filter_genes
  selected=filter_genes(ref,cell_count_cutoff=5,cell_percentage_cutoff2=.03,nonz_mean_cutoff=1.12);complete(np.asarray(selected),['separate-original-selected-gene-identifiers-not-applied-to-models']);plt.savefig(out/'original-filter-plot.pdf');plt.close('all')
  c2l.models.RegressionModel.setup_anndata(ref,labels_key='cell_type',batch_key='sample',categorical_covariate_keys=['chemistry'])
  model=c2l.models.RegressionModel(ref);model.train(max_epochs=p['reference_epochs'],batch_size=ref.n_obs,train_size=1,lr=.002,use_gpu=False,enable_progress_bar=False,logger=None,enable_checkpointing=False)
  assert len(model.history['elbo_train'])==p['reference_epochs']
  ref=model.export_posterior(ref,sample_kwargs=dict(num_samples=p['posterior_samples'],batch_size=ref.n_obs,use_gpu=False));snapshot(model,'reference');complete(model.samples,['reference','complete-posterior-samples']);complete(ref,['reference','complete-exported-anndata'])
  model.save(out/'reference-model',overwrite=False,save_anndata=True);ref.write_h5ad(out/'reference-posterior.h5ad');loaded=c2l.models.RegressionModel.load(out/'reference-model',use_gpu=False)
  complete(loaded.module.state_dict(),['reference','loaded-complete-module-state'])
  factors=list(ref.uns['mod']['factor_names']);signatures=ref.varm['means_per_cluster_mu_fg'][['means_per_cluster_mu_fg_'+x for x in factors]].copy();signatures.columns=factors
  assert list(signatures.index)==list(spatial.var_names) and factors==p['cell_types'];signatures.to_csv(out/'complete-reference-signatures.tsv',sep='\t',float_format='%.17g');complete(signatures,['reference','complete-derived-signatures'])
  c2l.models.Cell2location.setup_anndata(spatial,batch_key='sample');mapping=c2l.models.Cell2location(spatial,cell_state_df=signatures,N_cells_per_location=p['N_cells_per_location'],detection_alpha=p['detection_alpha'])
  mapping.train(max_epochs=p['spatial_epochs'],batch_size=spatial.n_obs,train_size=1,lr=.002,use_gpu=False,scale_elbo=p['scale_elbo'],enable_progress_bar=False,logger=None,enable_checkpointing=False)
  assert len(mapping.history['elbo_train'])==p['spatial_epochs']
  spatial=mapping.export_posterior(spatial,sample_kwargs=dict(num_samples=p['posterior_samples'],batch_size=spatial.n_obs,use_gpu=False));snapshot(mapping,'spatial');complete(mapping.samples,['spatial','complete-posterior-samples']);complete(spatial,['spatial','complete-exported-anndata']);mapping.save(out/'spatial-model',overwrite=False,save_anndata=True);spatial.write_h5ad(out/'spatial-posterior.h5ad')
  loaded=c2l.models.Cell2location.load(out/'spatial-model',use_gpu=False);complete(loaded.module.state_dict(),['spatial','loaded-complete-module-state'])
  class Tee(io.StringIO):
   def write(self,value):original.write(value);original.flush();return super().write(value)
  import sys
  original=sys.stdout;captured=Tee()
  with contextlib.redirect_stdout(captured):
   colocation=c2l.run_colocation(spatial,model_name='CoLocatedGroupsSklearnNMF',train_args=dict(n_fact=p['nmf_factors'],n_restarts=p['nmf_restarts'],n_iter=p['nmf_iterations'],sample_name_col='sample'),model_kwargs=dict(init='random',random_state=0,alpha=.01,nmf_kwd_args=dict(tol=.000001)),export_args=dict(path=str(out/'colocation'),run_name_suffix='-complete-synthetic',top_n=3,plot_extension='pdf',plot_histology=False))
  (out/'original-colocation.stdout').write_text(captured.getvalue(),encoding='utf8');torch.save(colocation,out/'complete-original-colocation-return.pt');complete(colocation,['colocation','complete-original-return'])
  assert set(colocation[0])=={'n_fact'+str(x) for x in p['nmf_factors']}
  for factor in colocation[0].values():
   mod=factor['mod'];assert len(mod.results)==p['nmf_restarts'] and len(mod.models)==p['nmf_restarts']
   for result in mod.results.values():
    for matrix in result['post_sample_means'].values():assert np.isfinite(matrix).all()
  complete({name:dict(n_iter={k:m.n_iter_ for k,m in x['mod'].models.items()},maximum_iterations=x['mod'].n_iter) for name,x in colocation[0].items()},['colocation','complete-original-iteration-status'])
  if 'Some error in plotting' in captured.getvalue():raise ValueError('Original colocation wrapper caught a plotting failure; all partial results/logs retained, no complete population admitted')
  complete(random.getstate(),['caller-rng','python']);complete(np.random.get_state(),['caller-rng','numpy']);complete(torch.get_rng_state(),['caller-rng','torch'])
  graph.write('COMPLETE-NUMERICAL-INDEX.json');save_json('COMPLETED.json',dict(status='COMPLETE_ORIGINAL_CELL2LOCATION013_REFERENCE_SPATIAL_AND_NMF_SYNTHETIC_WORKFLOW',reference_cells=ref.n_obs,spatial_locations=spatial.n_obs,genes=spatial.n_vars,cell_types=factors,numerical_records=len(records),nonfinite_array_records=len(graph.nonfinite),opaque_value_records=len(graph.opaque),whole_tool_qualified=False,paper_reproduced=False,convergence_claimed=False,new_tool_credit=0,whole_goal_complete=False));return 'ALL_DECLARED_ORIGINAL_CELL2LOCATION013_STAGES_RETURNED'
 except BaseException as error:
  failure=dict(status='FAIL2_ORIGINAL_WORKFLOW_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),new_tool_credit=0,whole_goal_complete=False)
  try:save_json('FAILURE.json',failure)
  except BaseException as recording_error:print('FAIL2: could not write FAILURE.json; retain full console:',repr(recording_error),failure,flush=True)
  try:graph.write('PARTIAL-NUMERICAL-INDEX.json')
  except BaseException as recording_error:print('FAIL2: could not write partial comparison index; retain raw originals and full console:',repr(recording_error),flush=True)
  raise

if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--inputs',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();print(analyze(a.inputs.resolve(),a.out.resolve()),flush=True)
