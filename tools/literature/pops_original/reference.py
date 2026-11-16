#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unmodified original PoPS 0.1/0.2 controls, retaining failures and warnings."""
from pathlib import Path
import argparse,csv,hashlib,json,os,resource,subprocess,sys,time
import numpy as np
import pandas as pd

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2,allow_nan=False);f.write('\n')
def execute(folder,name,command,expected=0,timeout=240):
    env=os.environ.copy()
    for key in ['PYTHONPATH','PYTHONHOME','PYTHONSTARTUP','LD_PRELOAD','LD_AUDIT','LD_LIBRARY_PATH']:env.pop(key,None)
    for key in ['OPENBLAS_NUM_THREADS','OMP_NUM_THREADS','MKL_NUM_THREADS','NUMEXPR_NUM_THREADS','BLIS_NUM_THREADS']:env[key]='1'
    def limit():resource.setrlimit(resource.RLIMIT_AS,(8*1024**3,8*1024**3))
    start=time.monotonic()
    with (folder/(name+'.stdout')).open('xb') as so,(folder/(name+'.stderr')).open('xb') as se:p=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,preexec_fn=limit,timeout=timeout)
    stderr=(folder/(name+'.stderr')).read_text(errors='replace');count=stderr.count('ConvergenceWarning:')
    result=dict(command=command,exit_code=p.returncode,expected=expected,seconds=round(time.monotonic()-start,3),address_space_bytes=8*1024**3,convergence_warnings=count,framework_convergence_policy='refuse-any-explicit-ConvergenceWarning; retain unchanged source outputs',source_modified=False)
    save(folder/(name+'.process.json'),result)
    if expected==0:assert p.returncode==0,(name,stderr[-5000:])
    else:assert p.returncode!=0,(name,'Expected source failure did not occur')
    print(json.dumps(dict(stage=str(folder.name)+'/'+name,exit_code=p.returncode,convergence_warnings=count,seconds=result['seconds'])),flush=True)
    return result
def legacy(out,source):
    folder=out/'v01-complete-22-chromosomes';folder.mkdir();rng=np.random.RandomState(80117);per=24;n=22*per;p=9
    ids=np.array(['SYN_POPS01_CHR%02d_%03d'%(c,i) for c in range(1,23) for i in range(per)]);chrom=np.repeat(np.arange(1,23),per)
    x=rng.normal(size=(n,p));y=1.6*x[:,1]-1.2*x[:,2]+.9*x[:,3]+.5*x[:,0]+rng.normal(size=n)*.4
    labels=['C0']+['F%d'%j for j in range(p-1)]
    frame=pd.DataFrame(x,columns=labels);frame.insert(0,'ENSGID',ids);frame=frame.iloc[rng.permutation(n)];frame.to_csv(folder/'features.tsv',sep='\t',index=False)
    loc=pd.DataFrame(dict(ENSGID=ids,CHR=chrom,START=1000000+np.arange(n)*1000,END=1001000+np.arange(n)*1000))
    order=np.concatenate([np.arange(c*per,(c+1)*per)[::-1] for c in range(22)]);loc.iloc[order].to_csv(folder/'gene-location.tsv',sep='\t',index=False)
    pd.DataFrame(dict(GENE=ids,ZSTAT=y)).to_csv(folder/'magma.genes.out',sep=' ',index=False)
    nsnps=rng.uniform(100,300,n);nparam=nsnps*rng.uniform(.2,.85,n);mac=rng.uniform(500,3000,n)
    with (folder/'magma.genes.raw').open('x') as f:
        f.write('# SYNTHETIC original PoPS 0.1 complete-autosome control\n# GENE CHR START END NSNPS NPARAM UNUSED MAC UNUSED CORRELATIONS\n')
        for i in range(n):
            within=i%per;fields=[ids[i],str(chrom[i]),'0','1','%.17g'%nsnps[i],'%.17g'%nparam[i],'0','%.17g'%mac[i],'0']
            fields+=['%.17g'%(.17**d) for d in range(within,0,-1)];f.write(' '.join(fields)+'\n')
    (folder/'control.features').write_text('C0\n');base=[sys.executable,'-I','-B'];records=[]
    records.append(execute(folder,'select',base+[str(source/'0.1/pops.feature_selection.py'),'--features',str(folder/'features.tsv'),'--gene_results',str(folder/'magma'),'--out',str(folder/'selected')]))
    selected=(folder/'selected.features').read_text().split();assert len(selected)>=2,selected
    for c in range(1,23):
        command=base+[str(source/'0.1/pops.predict_scores.py'),'--gene_loc',str(folder/'gene-location.tsv'),'--gene_results',str(folder/'magma'),'--features',str(folder/'features.tsv'),'--selected_features',str(folder/'selected.features'),'--control_features',str(folder/'control.features'),'--chromosome',str(c),'--out',str(folder/'original')]
        records.append(execute(folder,'predict-%02d'%c,command))
        actual=pd.read_csv(str(folder/'original')+'.%d.results'%c,sep='\t');expected=loc.iloc[order];expected=expected[expected.CHR==c].ENSGID.to_list();assert actual.ENSGID.to_list()==expected and np.isfinite(actual.Score).all()
    (folder/'one.features').write_text('F0\n');(folder/'zero.features').write_text('')
    baseflags=['--gene_loc',str(folder/'gene-location.tsv'),'--gene_results',str(folder/'magma'),'--features',str(folder/'features.tsv'),'--control_features',str(folder/'control.features'),'--chromosome','1']
    single=execute(folder,'predict-single-selected',base+[str(source/'0.1/pops.predict_scores.py'),*baseflags,'--selected_features',str(folder/'one.features'),'--out',str(folder/'single')],expected=1)
    assert 'TypeError' in (folder/'predict-single-selected.stderr').read_text()
    empty=execute(folder,'predict-empty-selected-control-only',base+[str(source/'0.1/pops.predict_scores.py'),*baseflags,'--selected_features',str(folder/'zero.features'),'--out',str(folder/'control-only')])
    save(folder/'STATUS.json',dict(status='PASS_UNMODIFIED_ORIGINAL_POPS_01_COMPLETE_22_CHROMOSOMES_AND_SOURCE_EDGE_CASES',target_genes=n,prediction_genes=n,input_features=p,selected_features=selected,chromosomes=list(range(1,23)),normal_processes=len(records),single_selected_source_failure=single,empty_selected_control_only=empty,raw_feature_rows_shuffled=True,prediction_gene_order_reversed_within_chromosome=True,source_byte_hashes={p.name:sha(p) for p in (source/'0.1').glob('*.py')},subsampled=False,biological_data=False))
    return folder
def lasso(out,source,name,n,p,stress=False):
    folder=out/name;folder.mkdir();rng=np.random.RandomState(42081 if not stress else 42082);nrows=n+3
    x=rng.normal(size=(nrows,p))
    if stress:
        common=rng.normal(size=(nrows,1));x=1000*(common+1e-6*x)
    else:x[:,1]=.6*x[:,0]+.8*x[:,1]
    x[:,-1]=1;ids=np.array(['SYN_POPS02_G%05d'%i for i in range(nrows)]);names=np.array(['F%04d'%i for i in range(p)])
    y=1.3*x[:n,0]-.7*x[:n,1]+.25*x[:n,2]+rng.normal(size=n)*.65
    pd.DataFrame(dict(ENSGID=ids,CHR=np.array(['1','2','6']*(nrows//3)),TSS=1000000+1000*np.arange(nrows))).to_csv(folder/'annotation.tsv',sep='\t',index=False)
    order=rng.permutation(n);pd.DataFrame(dict(ENSGID=ids[:n][order],Score=y[order])).to_csv(folder/'scores.tsv',sep='\t',index=False)
    np.savetxt(str(folder/'features.rows.txt'),ids,fmt='%s');chunks=0
    for start in range(0,p,37):
        width=min(37,p-start);np.save(str(folder/('features.mat.%d.npy'%chunks)),np.asfortranarray(x[:,start:start+width]));np.savetxt(str(folder/('features.cols.%d.txt'%chunks)),names[start:start+width],fmt='%s');chunks+=1
    command=[sys.executable,'-I','-B',str(source/'0.2/pops.py'),'--gene_annot_path',str(folder/'annotation.tsv'),'--feature_mat_prefix',str(folder/'features'),'--num_feature_chunks',str(chunks),'--y_path',str(folder/'scores.tsv'),'--feature_selection_p_cutoff','1.01','--method','lasso','--random_seed','42','--out_prefix',str(folder/'original'),'--save_matrix_files','--verbose']
    result=execute(folder,'original',command,timeout=600)
    save(folder/'STATUS.json',dict(status='ORIGINAL_POPS_02_LASSO_STRESS_RECORDED' if stress else 'PASS_ORIGINAL_POPS_02_LASSO_CONTROL',target_genes=n,prediction_genes=nrows,input_features=p,feature_chunks=chunks,source_process=result,expected_framework_admission=result['convergence_warnings']==0,subsampled=False,biological_data=False))
    return folder
def munge(out,source):
    root=out/'munger';root.mkdir();inputs=root/'inputs';inputs.mkdir();raw=inputs/'raw';raw.mkdir()
    pd.DataFrame(dict(ENSGID=['SYN_A','SYN_B','SYN_C'],CHR=[1,2,6],TSS=[1000,2000,3000])).to_csv(inputs/'annotation.tsv',sep='\t',index=False)
    pd.DataFrame(dict(ENSGID=['SYN_C','SYN_A'],B=[3.,1.],ALL_MISSING=[np.nan,np.nan])).to_csv(raw/'b.tsv',sep='\t',index=False)
    pd.DataFrame(dict(ENSGID=['SYN_B','SYN_A','SYN_C'],A=[20.,10.,30.])).to_csv(raw/'a.tsv',sep='\t',index=False)
    records=[]
    for policy in ['raise','ignore','mean','zero']:
        folder=root/policy;folder.mkdir();command=[sys.executable,'-I','-B',str(source/'0.2/munge_feature_directory.py'),'--gene_annot_path',str(inputs/'annotation.tsv'),'--feature_dir',str(raw),'--nan_policy',policy,'--max_cols','2','--save_prefix',str(folder/'original')]
        result=execute(folder,'original',command,expected=1 if policy=='raise' else 0);records.append(dict(policy=policy,**result))
        if policy!='raise':
            chunks=sorted(folder.glob('original.mat.*.npy'));a=np.hstack([np.load(str(p)) for p in chunks]);assert a.shape==(3,3);assert a[0,0]==10 and a[1,0]==20 and a[2,0]==30
            if policy=='mean':assert a[1,1]==2 and np.isnan(a[:,2]).all()
            elif policy=='zero':assert a[1,1]==0 and (a[:,2]==0).all()
            else:assert np.isnan(a[1,1]) and np.isnan(a[:,2]).all()
    save(root/'STATUS.json',dict(status='PASS_ORIGINAL_FEATURE_MUNGER_ALL_NAN_POLICIES_AND_CHUNK_ORDER',processes=records,all_missing_mean_remains_nan=True,nan_policies_are_explicit=True))
    return root
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);source=a.original.resolve()
    expected={'0.1/pops.feature_selection.py':'833c2569dff7e13f0f987534d52849d99270992fbdb46045dbc5dfb4a2b2d4bc','0.1/pops.predict_scores.py':'5b1f7399151863a7aa756c6cd8b367b620067cf89e164703d54392e4f475fb75','0.2/pops.py':'35e5af04748d6477e47a8a99a941de086dab39bc829655218a4817242507e3fc','0.2/munge_feature_directory.py':'3935b638693b4a72add556a1be9bc4e70d08e55305076a23f5cc08c2675c3102'}
    assert all(sha(source/name)==digest for name,digest in expected.items())
    folders=[legacy(out,source),lasso(out,source,'v02-lasso',600,150),lasso(out,source,'v02-lasso-stress',180,600,True),munge(out,source)]
    assert all(sha(source/name)==digest for name,digest in expected.items());save(out/'COMPLETED.json',dict(status='PASS_ORIGINAL_POPS_01_02_REFERENCE_EXECUTIONS',folders=[p.name for p in folders],source_sha256=expected,source_modified=False,author_environment_reproduced=False,whole_goal_complete=False))
    print((out/'COMPLETED.json').read_text(),flush=True)

if __name__=='__main__':main()
