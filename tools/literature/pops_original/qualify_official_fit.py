#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare complete official MAGMA/PoPS fitting with an independent original invocation."""
from pathlib import Path
import argparse,importlib.util,json,os,signal,subprocess,sys,time,traceback

spec=importlib.util.spec_from_file_location('original_adapter_qualification',Path(__file__).with_name('qualify_adapter.py'))
q=importlib.util.module_from_spec(spec);spec.loader.exec_module(q)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--upstream',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();s=None
    try:
        a.suite='official-fit';s=q.Suite(a);own_sha=q.sha(__file__);(s.out/'as-run-qualify_official_fit.py').write_bytes(Path(__file__).read_bytes())
        u=a.upstream.resolve()/'example/data';annotation=u/'utils/gene_annot_jun10.txt';features=u/'features_munged/pops_features';magma=u/'magma_scores/PASS_Schizophrenia';controls=u/'utils/features_jul17_control.txt'
        paths=[annotation,controls,*features.parent.glob(features.name+'.*'),Path(str(magma)+'.genes.out'),Path(str(magma)+'.genes.raw')];before={str(p):q.sha(p) for p in paths};q.save(s.out/'INPUTS-BEFORE.json',before)
        setup=q.properties(s.runtime/'runtime.properties');source=Path(setup['sources'])/'0.2/pops.py';source_sha=q.sha(source);assert source_sha=='35e5af04748d6477e47a8a99a941de086dab39bc829655218a4817242507e3fc'
        direct=s.out/'direct-original';direct.mkdir();prefix=direct/'original'
        command=['/usr/bin/prlimit','--as=17179869184','--',setup['python'],'-I','-B',str(source),'--gene_annot_path',str(annotation),'--feature_mat_prefix',str(features),'--num_feature_chunks','2','--magma_prefix',str(magma),'--control_features_path',str(controls),'--method','ridge','--random_seed','42','--feature_selection_p_cutoff','0.05','--use_magma_covariates','--use_magma_error_cov','--project_out_covariates_remove_hla','--feature_selection_remove_hla','--training_remove_hla','--no_save_matrix_files','--verbose','--out_prefix',str(prefix)]
        env=os.environ.copy()
        for key in ['PYTHONPATH','PYTHONHOME','PYTHONSTARTUP','LD_PRELOAD','LD_AUDIT','LD_LIBRARY_PATH','OPENBLAS_CORETYPE']:env.pop(key,None)
        for key in ['OPENBLAS_NUM_THREADS','OMP_NUM_THREADS','MKL_NUM_THREADS','NUMEXPR_NUM_THREADS','BLIS_NUM_THREADS']:env[key]='1'
        started=time.monotonic()
        with (direct/'stdout').open('xb') as so,(direct/'stderr').open('xb') as se:
            child=subprocess.Popen(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True);timed_out=False
            try:code=child.wait(timeout=900)
            except subprocess.TimeoutExpired:
                timed_out=True;os.killpg(child.pid,signal.SIGTERM)
                try:code=child.wait(timeout=20)
                except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);code=child.wait()
        record=dict(command=command,exit_code=code,timed_out=timed_out,seconds=round(time.monotonic()-started,3),source_sha256=source_sha,address_space_bytes=17179869184)
        q.save(direct/'PROCESS.json',record);assert code==0 and not timed_out and 'ConvergenceWarning' not in (direct/'stderr').read_text(errors='replace')
        assert q.sha(source)==source_sha and all(q.sha(path)==value for path,value in before.items())
        bundle=s.request('complete-official',dict(edition='0.2',task='fit',genome_build='official-tutorial-build-not-independently-validated',feature_set_id='complete-official-508-features',target_source_id='official-MAGMA-schizophrenia-tutorial-not-author-analysis',gene_annotation=str(annotation),feature_prefix=str(features),feature_chunks='2',target_type='magma',magma_prefix=str(magma),control_features=str(controls),method='ridge',p_cutoff='0.05',python_address_space_mb='16384',max_input_bytes='8589934592',max_work_bytes='17179869184'))
        for suffix,dest in [('coefs','coefficients.tsv'),('preds','predictions.tsv'),('marginals','marginals.tsv')]:assert (bundle/'results'/dest).read_bytes()==(direct/('original.'+suffix)).read_bytes(),dest
        report=json.loads((bundle/'audit/summary.json').read_bytes());assert report['target_genes']==16980 and report['all_feature_count']==508 and report['feature_selection_count']==258 and report['models'][0]['prediction_genes']==18383,report
        replay=s.replay('complete-official-replay',bundle);s.replay('complete-official-replay-of-replay',replay)
        assert q.sha(source)==source_sha and q.sha(__file__)==own_sha and all(q.sha(path)==value for path,value in before.items())
        s.complete(dict(target_genes=16980,prediction_genes=18383,input_features=508,selected_features=258,direct_original_reports_byte_identical=3,genuine_replay=True,genuine_replay_of_replay=True,subsampled=False,source_modified=False,paper_author_environment_established=False));return 0
    except Exception as error:
        if s is not None:q.save(s.out/'FAILURE.json',dict(status='FAIL_COMPLETE_OFFICIAL_ORIGINAL_ADAPTER_QUALIFICATION',reason=repr(error),traceback=traceback.format_exc(),processes=s.records))
        traceback.print_exc();return 2

if __name__=='__main__':raise SystemExit(main())
