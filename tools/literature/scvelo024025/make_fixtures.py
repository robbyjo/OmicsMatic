#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Generate explicit original-scVelo synthetic kinetics; no author data or parameter inference."""
from pathlib import Path
import argparse,json,sys,traceback
sys.dont_write_bytecode=True
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute();out.mkdir(exist_ok=False)
 try:
  import numpy as np,pandas as pd,scvelo as scv
  if scv.__version__!='0.2.5':raise ValueError('Generate this fixed candidate fixture using exact original scVelo0.2.5')
  adata=scv.datasets.simulation(n_obs=160,n_vars=12,noise_model='gillespie',alpha=8,beta=0.5,gamma=0.3,t_max=20,random_seed=882341)
  adata.obs_names=['NA']+['cell_β_'+str(i).zfill(3) for i in range(1,160)]
  adata.var_names=['NA','gene_Γ']+['gene_'+str(i).zfill(2) for i in range(2,12)]
  adata.obs['state']=pd.Categorical(['state_B']*53+['state_A']*53+['state_Γ']*54,categories=['state_Γ','state_B','state_A'],ordered=False)
  t=adata.obs['true_t'].to_numpy();adata.obsm['X_umap']=np.column_stack([t,np.sin(t/3)])
  adata.uns['fixture_provenance']='Synthetic original scVelo0.2.5 Gillespie simulation; caller-provided two-dimensional coordinates, not a fitted UMAP. No human data, author parameters or paper reproduction.'
  if adata.shape!=(160,12):raise ValueError('Complete declared synthetic population differs')
  adata.write_h5ad(out/'kinetics.h5ad')
  result=dict(status='GENERATED_FIXED_ORIGINAL_SCVELO_SYNTHETIC_INPUT_NOT_ANALYSIS',generator_version=scv.__version__,cells=160,genes=12,seed=882341,literal_first_cell='NA',literal_first_gene='NA',categorical_order=list(adata.obs['state'].cat.categories),coordinates='caller-supplied synthetic curve, not fitted UMAP',counts='original Gillespie spliced/unspliced outputs',paper_reproduced=False,new_tool_credit=0)
  (out/'FIXTURE.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf8');print(result['status'],flush=True);return 0
 except BaseException as error:
  (out/'FAILURE.json').write_text(json.dumps(dict(status='FAIL2_FIXTURE_INCOMPLETE',cause=repr(error),traceback=traceback.format_exc()),indent=2),encoding='utf8');print(traceback.format_exc(),flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
