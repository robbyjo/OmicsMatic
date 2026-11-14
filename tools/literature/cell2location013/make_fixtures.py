#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create complete declared synthetic count inputs; infer no paper parameters or biological result."""
from pathlib import Path
import argparse,json
import anndata as ad
import numpy as np
import pandas as pd
from scipy import sparse

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False)
 rng=np.random.default_rng(641302);n_ref,n_spatial,n_genes=48,16,24;types=['type_B','type_A','type_Γ'];genes=['gene_α_'+str(i+1) for i in range(n_genes)];genes[3]='NA'
 signatures=rng.gamma(2,1.5,size=(n_genes,3))+1
 for k in range(3):signatures[k*8:(k+1)*8,k]+=10
 labels=np.arange(n_ref)%3;batch=np.arange(n_ref)//24;mean=signatures[:,labels].T*np.where(batch[:,None]==0,.8,1.2)
 counts=rng.negative_binomial(12,12/(12+mean)).astype(np.int64)
 obs=pd.DataFrame(index=['reference_β_'+str(i+1) for i in range(n_ref)])
 obs['cell_type']=pd.Categorical([types[k] for k in labels],categories=types,ordered=True);obs['sample']=pd.Categorical(['sample_α' if k==0 else 'sample_β' for k in batch],categories=['sample_β','sample_α']);obs['chemistry']=pd.Categorical(['chem_1' if i%2==0 else 'chem_2' for i in range(n_ref)]);obs['literal_unused']='NA'
 ref=ad.AnnData(sparse.csr_matrix(counts),obs=obs,var=pd.DataFrame(index=genes));ref.write_h5ad(out/'reference.h5ad')
 abundance=rng.gamma(2,1.7,size=(n_spatial,3))+.5;spot_mean=abundance@signatures.T;sp_counts=rng.negative_binomial(20,20/(20+spot_mean)).astype(np.int64)
 sobs=pd.DataFrame(index=['spot_β_'+str(i+1) for i in range(n_spatial)]);sobs['sample']=pd.Categorical(['sample_α' if i<8 else 'sample_β' for i in range(n_spatial)],categories=['sample_β','sample_α']);sobs['literal_unused']='NA'
 spatial=ad.AnnData(sparse.csr_matrix(sp_counts),obs=sobs,var=pd.DataFrame(index=genes));spatial.obsm['spatial']=np.array([[4+(i%4)*10,4+(i//4)*10] for i in range(n_spatial)],dtype=np.float64)
 spatial.uns['spatial']={name:dict(images=dict(hires=np.zeros((48,48,3),dtype=np.uint8)),scalefactors=dict(tissue_hires_scalef=1.,spot_diameter_fullres=4.),metadata=dict(source='explicit synthetic blank image; not histology')) for name in ['sample_α','sample_β']};spatial.write_h5ad(out/'spatial.h5ad')
 parameters=dict(schema_version=1,source='Explicit synthetic integration fixture; not author data or settings.',reference_cells=n_ref,spatial_locations=n_spatial,genes=n_genes,cell_types=types,seed=770031,reference_epochs=32,spatial_epochs=64,posterior_samples=20,N_cells_per_location=8,detection_alpha=20,scale_elbo=None,nmf_factors=[2,3],nmf_restarts=2,nmf_iterations=2000,all_reference_cells_used=True,all_spatial_locations_used=True,all_genes_used_in_models=True,convergence_or_biological_accuracy_claimed=False)
 (out/'parameters.json').write_text(json.dumps(parameters,indent=2,ensure_ascii=False)+'\n',encoding='utf8');np.savez(out/'generating-parameters-not-fitted-results.npz',reference_signatures=signatures,spatial_abundances=abundance)
 print('READY_COMPLETE_SYNTHETIC_CELL2LOCATION013_COUNTS_NOT_ANALYSIS',flush=True)
if __name__=='__main__':main()
