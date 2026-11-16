#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-only
"""Bind original CLI reports to captured inputs; never fit or alter a model."""
from pathlib import Path
from types import SimpleNamespace
import csv,importlib.util,json,math,shutil,sys
import numpy as np
import pandas as pd

def read_control(root):
    lines=(root/'control.tsv').read_text(encoding='utf-8').splitlines();assert lines.pop(0)=='key\tvalue';result={}
    for line in lines:
        k,v=line.split('\t',1);assert k not in result;result[k]=v
    return result
def load_source(path,name):
    spec=importlib.util.spec_from_file_location(name,path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
def table(path):return pd.read_csv(path,sep='\t',dtype=str,keep_default_na=False)
def tokens(path):return np.loadtxt(str(path),dtype=str).flatten().tolist()
def literal(values,maximum):
    result=[str(v) for v in values]
    assert len(result)==len(set(result)),'Duplicate source identifiers'
    assert all(v and len(v)<=maximum and not any(c.isspace() or ord(c)<32 for c in v) for v in result),'Unqualified source identifier'
    return result
def save(path,value):path.write_text(json.dumps(value,indent=2,sort_keys=True,allow_nan=False)+'\n',encoding='utf-8')
def copy(source,target):target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
def array_audit(root,c,ids,names,matrix):
    ids=literal(ids,int(c['max_identifier_chars']));names=literal(names,int(c['max_identifier_chars']))
    assert len(ids)<=int(c['max_prediction_genes']) and len(names)<=int(c['max_selected_features']),'Complete prediction/selected-feature capacity exceeded; increase resources without dropping genes/features'
    assert matrix.shape==(len(ids),len(names)) and len(names)>0 and len(ids)>0
    np.savetxt(str(root/'audit/features.rows.txt'),np.asarray(ids),fmt='%s');np.savetxt(str(root/'audit/features.cols.txt'),np.asarray(names),fmt='%s')
    np.save(str(root/'audit/features.npy'),np.asarray(matrix,dtype=np.float64))
    return ids,names
def model(name,scores,coefs,score_column,features,predictions,**extra):return dict(id=name,score_file=scores,coefficient_file=coefs,score_column=score_column,features=features,prediction_genes=predictions,**extra)
def modern(root,c):
    source=load_source(root/'upstream/0.2/pops.py','selected_original_pops02')
    for suffix,name in [('preds','predictions.tsv'),('coefs','coefficients.tsv'),('marginals','marginals.tsv')]:copy(root/('raw/original.'+suffix),root/'results'/name)
    if c['save_matrix_files']=='true':
        for suffix,name in [('traindata','training-data.tsv'),('matdata','prediction-data.tsv')]:copy(root/('raw/original.'+suffix),root/'results'/name)
    coefs=table(root/'results/coefficients.tsv');assert coefs.columns.tolist()==['parameter','beta']
    expected={'ridge':'RidgeCV','linreg':'LinearRegression','lasso':'LassoCV'}[c['method']];assert coefs.iloc[0].tolist()==['METHOD',expected]
    skip=1 if c['method']=='linreg' else 3
    if skip==3:
        assert coefs.iloc[1,0]=='SELECTED_CV_ALPHA' and coefs.iloc[2,0]=='BEST_CV_SCORE'
        assert math.isfinite(float(coefs.iloc[1,1])) and math.isfinite(float(coefs.iloc[2,1]))
    names=literal(coefs.parameter.iloc[skip:].tolist(),int(c['max_identifier_chars']));weights=np.asarray(coefs.beta.iloc[skip:],dtype=np.float64)
    assert np.isfinite(weights).all() and len(names)>0,'Source coefficients are nonfinite or no model features exist'
    prefix=str(root/c['feature_prefix']);nchunks=int(c['feature_chunks']);rows=tokens(Path(prefix+'.rows.txt'));columns=[];mats=[];all_names=[]
    for i in range(nchunks):
        cols=tokens(Path(prefix+'.cols.%d.txt'%i));all_names.extend(cols);keep=[j for j,name in enumerate(cols) if name in set(names)]
        data=np.load(prefix+'.mat.%d.npy'%i,mmap_mode='r');assert data.ndim==2 and data.shape==(len(rows),len(cols))
        columns.extend(cols[j] for j in keep);mats.append(data[:,keep])
    assert literal(all_names,int(c['max_identifier_chars']))==all_names and len(all_names)<=int(c['max_features'])
    assert columns==names,'Original coefficient/matrix feature order differs'
    matrix=np.hstack(mats);rows,names=array_audit(root,c,rows,names,matrix)
    scores=table(root/'results/predictions.tsv');assert scores.ENSGID.tolist()==rows
    marginal=table(root/'results/marginals.tsv');assert marginal.iloc[:,0].tolist()==all_names
    assert set(marginal.loc[marginal.selected=='True'].iloc[:,0])==set(names)
    if c['target_type']=='magma':targets=pd.read_csv(root/(c['magma_prefix']+'.genes.out'),delim_whitespace=True)[['GENE','ZSTAT']].rename(columns={'GENE':'ENSGID','ZSTAT':'Score'})
    else:targets=pd.read_csv(root/c['scores'],sep='\t')[['ENSGID','Score']]
    assert len(targets)<=int(c['max_target_genes']) and not targets.ENSGID.duplicated().any()
    annot=source.read_gene_annot_df(str(root/c['gene_annotation']));ids=targets.ENSGID.values
    role_flags={'projection':'project_out_covariates_gene','selection':'feature_selection_gene','training':'training_gene'};counts={}
    projected=c['target_type']=='magma' and c['use_magma_covariates']=='true' or c['target_type']=='custom' and 'score_covariates' in c
    for role,column in role_flags.items():
        chromosomes=annot.CHR.unique().tolist() if c[role+'_chromosomes']=='all' else c[role+'_chromosomes'].split(',')
        mask=source.get_gene_indices_to_use(ids,annot,chromosomes,c[role+'_remove_hla']=='true');counts[role+'_mask_genes']=int(np.sum(mask));selected=set(str(x) for x in ids[np.asarray(mask)])
        if role=='projection' and not projected:continue
        assert column in scores and [v=='True' for v in scores[column]]==[gene in selected for gene in rows],'Source annotation/chromosome/HLA mask differs'
    score_y={str(g):float(v) for g,v in targets[['ENSGID','Score']].values}
    for i,gene in enumerate(rows):
        raw=scores.Y.iloc[i]
        if gene not in score_y:assert raw=='','Prediction-only target was invented'
        else:assert math.isfinite(float(raw)) and math.isclose(float(raw),score_y[gene],rel_tol=1e-12,abs_tol=1e-12)
    return dict(models=[model('fit','predictions.tsv','coefficients.tsv','PoPS_Score',len(names),len(rows),source_method=expected,coefficient_name_column='parameter',coefficient_value_column='beta',coefficient_metadata_rows=skip)],target_genes=len(targets),feature_selection_count=len(names),all_feature_count=len(all_names),cv_score_definition='unavailable' if c['method']=='linreg' else 'positive-fivefold-mse' if c['method']=='lasso' else 'negative-leave-one-out-mse',**counts)
def legacy(root,c):
    selected=root/'raw/selection.features' if c['task']=='select-predict' else root/c['selected_features'] if c['task']=='predict' else root/'raw/selection.features'
    if c['task']!='predict':copy(root/'raw/selection.features',root/'results/selected.features')
    selected_names=tokens(selected);literal(selected_names,int(c['max_identifier_chars']))
    if c['task']=='select':return dict(models=[],feature_selection_count=len(selected_names),selection_only=True)
    source=load_source(root/'upstream/0.1/pops.predict_scores.py','selected_original_pops01')
    args=SimpleNamespace(gene_loc=str(root/c['gene_location']),features=str(root/c['features_text']),selected_features=str(selected),control_features=str(root/c['control_features']))
    frame=source.munge_features(args);names=frame.columns.values[2:].tolist();rows=frame.ENSGID.astype(str).tolist()
    raw_loc=pd.read_csv(root/c['gene_location'],sep='\t',dtype=str,keep_default_na=False);raw_features=pd.read_csv(root/c['features_text'],delim_whitespace=True,dtype=str,keep_default_na=False,usecols=['ENSGID'])
    assert set(rows)<=set(raw_loc.ENSGID) and set(rows)<=set(raw_features.ENSGID),'Original parser coerced gene identifiers; no identifier repair is admitted'
    rows,names=array_audit(root,c,rows,names,frame.values[:,2:].astype(float));models=[]
    chromosomes=list(range(1,23)) if c['chromosomes']=='all' else [int(v) for v in c['chromosomes'].split(',')]
    for chrom in chromosomes:
        name='chr-%02d'%chrom;out=root/'results'/name;copy(root/('raw/prediction.%d.results'%chrom),out/'scores.tsv');copy(root/('raw/prediction.%d.coefs'%chrom),out/'coefficients.tsv')
        scores=table(out/'scores.tsv');coefs=table(out/'coefficients.tsv');expected=frame.ENSGID[frame.CHR==chrom].astype(str).tolist()
        assert len(expected)>0 and scores.ENSGID.tolist()==expected,'No or mismatched original prediction genes for requested chromosome'
        assert coefs.columns.tolist()==['Feature','beta_hat'] and coefs.Feature.tolist()==names
        assert np.isfinite(np.asarray(coefs.beta_hat,dtype=np.float64)).all() and np.isfinite(np.asarray(scores.Score,dtype=np.float64)).all()
        models.append(model(name,name+'/scores.tsv',name+'/coefficients.tsv','Score',len(names),len(expected),source_method='original-0.1-chromosome-held-out-RidgeCV-Pearson',coefficient_name_column='Feature',coefficient_value_column='beta_hat',coefficient_metadata_rows=0,gene_ids=expected))
    return dict(models=models,feature_selection_count=len(selected_names),control_only=len(selected_names)==0,cv_score_definition='held-out-Pearson-selected-alpha-not-exported-by-original-0.1')
def munge(root,c):
    raw=root/'raw';rows=tokens(raw/'features.rows.txt');literal(rows,int(c['max_identifier_chars']));assert len(rows)<=int(c['max_prediction_genes'])
    copy(raw/'features.rows.txt',root/'results/features.rows.txt');matrices=sorted(raw.glob('features.mat.*.npy'),key=lambda p:int(p.name.split('.')[-2]));names=[];nan=0;infinite=0
    for i,path in enumerate(matrices):
        assert path.name=='features.mat.%d.npy'%i,'Noncontiguous original munger chunk ordinals'
        cols=tokens(raw/('features.cols.%d.txt'%i));matrix=np.load(str(path),mmap_mode='r');assert matrix.ndim==2 and matrix.shape==(len(rows),len(cols));names.extend(cols)
        assert len(names)<=int(c['max_features']),'Prepared feature count exceeds declared capacity'
        for j in range(matrix.shape[1]):nan+=int(np.isnan(matrix[:,j]).sum());infinite+=int(np.isinf(matrix[:,j]).sum())
        copy(path,root/'results'/path.name);copy(raw/('features.cols.%d.txt'%i),root/'results'/('features.cols.%d.txt'%i))
    literal(names,int(c['max_identifier_chars']))
    return dict(models=[],preparation_only=True,prepared_genes=len(rows),prepared_features=len(names),prepared_chunks=len(matrices),nan_policy=c['nan_policy'],remaining_nan_values=nan,remaining_infinite_values=infinite)
def main():
    root=Path(sys.argv[1]).resolve();c=read_control(root);(root/'audit').mkdir();(root/'results').mkdir()
    if c['edition']=='0.1':result=legacy(root,c)
    elif c['task']=='munge':result=munge(root,c)
    else:result=modern(root,c)
    result.update(profile='original-pops-audit-v1',edition=c['edition'],task=c['task'],model_fitting_performed=c['task'] in ['fit','predict','select-predict'],numerical_model_source_modified=False)
    result['result_members']=sorted([p.relative_to(root/'results').as_posix()+('/' if p.is_dir() else '') for p in (root/'results').rglob('*')])
    save(root/'audit/summary.json',result);print(json.dumps(dict(status='PASS_ORIGINAL_OUTPUT_BINDING_NOT_INDEPENDENT_STATISTICAL_REFIT',models=len(result['models']))),flush=True)

if __name__=='__main__':main()
