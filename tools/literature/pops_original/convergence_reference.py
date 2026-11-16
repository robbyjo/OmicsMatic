#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise unchanged original LassoCV on an ill-conditioned cancellation design."""
from pathlib import Path
import argparse,hashlib,json,os,signal,subprocess,sys,time,traceback
import numpy as np
import pandas as pd

SOURCE_SHA='35e5af04748d6477e47a8a99a941de086dab39bc829655218a4817242507e3fc'
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=None
    try:
        source=a.original.resolve();assert sha(source)==SOURCE_SHA
        a.out.resolve().mkdir(parents=True,exist_ok=False);out=a.out.resolve();(out/'as-run-convergence_reference.py').write_bytes(Path(__file__).read_bytes())
        rng=np.random.RandomState(171171);n=240;nr=n+3;nf=180;noise=rng.normal(size=(nr,nf));common=rng.normal(size=(nr,1));x=10000*common+noise
        y=15*(noise[:n,0]-noise[:n,1])+.05*rng.normal(size=n)
        ids=np.array(['SYN_CANCEL_%05d'%i for i in range(nr)]);cols=np.array(['F%04d'%i for i in range(nf)])
        pd.DataFrame(dict(ENSGID=ids,CHR=1+np.arange(nr)%3,TSS=1000000+1000*np.arange(nr))).to_csv(out/'annotation.tsv',sep='\t',index=False)
        order=rng.permutation(n);pd.DataFrame(dict(ENSGID=ids[:n][order],Score=y[order])).to_csv(out/'scores.tsv',sep='\t',index=False)
        np.savetxt(str(out/'features.rows.txt'),ids,fmt='%s');chunks=0
        for first in range(0,nf,43):
            np.save(str(out/('features.mat.%d.npy'%chunks)),np.asfortranarray(x[:,first:first+43]));np.savetxt(str(out/('features.cols.%d.txt'%chunks)),cols[first:first+43],fmt='%s');chunks+=1
        env=os.environ.copy()
        for key in ['OPENBLAS_NUM_THREADS','OMP_NUM_THREADS','MKL_NUM_THREADS','NUMEXPR_NUM_THREADS','BLIS_NUM_THREADS']:env[key]='1'
        for key in ['PYTHONPATH','PYTHONHOME','PYTHONSTARTUP','LD_PRELOAD','LD_AUDIT','LD_LIBRARY_PATH','OPENBLAS_CORETYPE']:env.pop(key,None)
        command=['/usr/bin/prlimit','--as=8589934592','--',sys.executable,'-I','-B',str(source),'--gene_annot_path',str(out/'annotation.tsv'),'--feature_mat_prefix',str(out/'features'),'--num_feature_chunks',str(chunks),'--y_path',str(out/'scores.tsv'),'--feature_selection_p_cutoff','1.01','--method','lasso','--random_seed','42','--out_prefix',str(out/'original'),'--save_matrix_files','--verbose']
        start=time.monotonic()
        with (out/'original.stdout').open('xb') as so,(out/'original.stderr').open('xb') as se:
            child=subprocess.Popen(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True);timed_out=False
            try:code=child.wait(timeout=600)
            except subprocess.TimeoutExpired:
                timed_out=True;os.killpg(child.pid,signal.SIGTERM)
                try:code=child.wait(timeout=15)
                except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);code=child.wait()
        stderr=(out/'original.stderr').read_text(errors='replace');warnings=stderr.count('ConvergenceWarning');record=dict(command=command,exit_code=code,timed_out=timed_out,seconds=round(time.monotonic()-start,3),convergence_warnings=warnings)
        save(out/'PROCESS.json',record);assert code==0 and not timed_out,'Original source failed; this does not establish warning-with-finite-output coverage'
        coef=pd.read_csv(out/'original.coefs',sep='\t');scores=pd.read_csv(out/'original.preds',sep='\t')
        finite=bool(np.isfinite(coef.beta.iloc[3:].astype(float)).all() and np.isfinite(scores.PoPS_Score).all())
        assert sha(source)==SOURCE_SHA
        save(out/'REFERENCE.json',dict(status='ORIGINAL_LASSO_WARNING_WITH_FINITE_OUTPUTS' if warnings else 'NO_CONVERGENCE_WARNING_COVERAGE_NOT_ESTABLISHED',source_sha256=SOURCE_SHA,source_modified=False,default_iterations_unchanged=True,source_process=record,finite_raw_coefficients_and_scores=finite,target_genes=n,prediction_genes=nr,input_features=nf,feature_chunks=chunks,expected_adapter_exit=2 if warnings else None,expected_adapter_admission=False if warnings else None,scientific_interpretation='Synthetic numerical failure control; no biological claim',whole_goal_complete=False))
        print(json.dumps(dict(status='SOURCE_CONVERGENCE_CONTROL_RECORDED',convergence_warnings=warnings,finite_outputs=finite)),flush=True)
        return 0 if warnings and finite else 2
    except Exception as error:
        if out is not None:save(out/'FAILURE.json',dict(reason=repr(error),traceback=traceback.format_exc()))
        traceback.print_exc();return 2

if __name__=='__main__':raise SystemExit(main())
