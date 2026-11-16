#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run all saved original ridge/OLS, selection and covariance-format controls."""
from pathlib import Path
import argparse,concurrent.futures,importlib.util,json,traceback

spec=importlib.util.spec_from_file_location('original_adapter_qualification',Path(__file__).with_name('qualify_adapter.py'))
q=importlib.util.module_from_spec(spec);spec.loader.exec_module(q)

def translate_reference(root):
    command=json.loads((root/'PROCESS.json').read_bytes())['command'];assert Path(command[1]).name=='pops.py';options={};i=2
    while i<len(command):
        key=command[i];assert key.startswith('--') and key not in options;i+=1;values=[]
        while i<len(command) and not command[i].startswith('--'):values.append(command[i]);i+=1
        options[key]=values
    known={'--gene_annot_path','--feature_mat_prefix','--num_feature_chunks','--method','--out_prefix','--save_matrix_files','--verbose','--magma_prefix','--y_path','--y_covariates_path','--y_error_cov_path','--feature_selection_p_cutoff','--feature_selection_max_num','--feature_selection_fss_num_features','--subset_features_path','--control_features_path','--project_out_covariates_chromosomes','--feature_selection_chromosomes','--training_chromosomes'}
    assert set(options)<=known,('Untranslated original options',set(options)-known)
    def scalar(key):assert len(options[key])==1;return options[key][0]
    def path(key):
        value=root/Path(scalar(key)).name;assert value.is_file(),value;return str(value)
    cfg=dict(edition='0.2',task='fit',gene_annotation=path('--gene_annot_path'),feature_prefix=str(root/Path(scalar('--feature_mat_prefix')).name),feature_chunks=scalar('--num_feature_chunks'),method=scalar('--method'),save_matrix_files='true' if '--save_matrix_files' in options else 'false')
    if '--magma_prefix' in options:cfg.update(target_type='magma',magma_prefix=str(root/Path(scalar('--magma_prefix')).name))
    else:cfg.update(target_type='custom',scores=path('--y_path'))
    for original,field in [('--y_covariates_path','score_covariates'),('--y_error_cov_path','error_covariance'),('--subset_features_path','subset_features'),('--control_features_path','control_features')]:
        if original in options:cfg[field]=path(original)
    for original,field in [('--feature_selection_p_cutoff','p_cutoff'),('--feature_selection_max_num','max_selected_by_p'),('--feature_selection_fss_num_features','forward_steps')]:
        if original in options:cfg[field]=scalar(original)
    for original,field in [('--project_out_covariates_chromosomes','projection_chromosomes'),('--feature_selection_chromosomes','selection_chromosomes'),('--training_chromosomes','training_chromosomes')]:
        if original in options:assert options[original];cfg[field]=','.join(options[original])
    return cfg,command

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--prepared',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--workers',type=int,default=3);a=p.parse_args();s=None
    try:
        assert 1<=a.workers<=3;a.suite='original-profile-matrix';s=q.Suite(a);own_sha=q.sha(__file__);(s.out/'as-run-qualify_matrix.py').write_bytes(Path(__file__).read_bytes())
        prepared=a.prepared.resolve();names=json.loads((prepared/'CASES.json').read_bytes());assert len(names)==19 and len(names)==len(set(names))
        before={str(p):q.sha(p) for name in names for p in (prepared/name).iterdir() if p.is_file()};q.save(s.out/'INPUTS-AND-DIRECT-REFERENCES-BEFORE.json',before)
        def case(name):
            r=prepared/name;params=q.properties(r/'params.properties');cfg,command=translate_reference(r)
            failure=params['expect_failure']=='true';bundle=s.request(name,cfg,2 if failure else 0)
            q.save(s.out/name/'ORIGINAL-OPTION-BINDING.json',dict(reference_command=command,translated_request_fields=cfg,all_recorded_options_accounted_for=True))
            if failure:return dict(name=name,expected_source_failure=True,no_completion_marker=True)
            reports={}
            for suffix,dest in [('coefs','coefficients.tsv'),('preds','predictions.tsv'),('marginals','marginals.tsv')]:
                actual=bundle/'results'/dest;original=r/('original.'+suffix);identical=actual.read_bytes()==original.read_bytes()
                reports[dest]=dict(adapter_sha256=q.sha(actual),original_sha256=q.sha(original),byte_identical=identical)
                assert identical,(name,dest,'Source discrepancy retained; do not repair values or loosen checks silently')
            replay=name in ['ols-fss-gls','ridge-controls-subset','ridge-custom-gls']
            if replay:s.replay(name+'-replay',bundle)
            result=dict(name=name,expected_source_failure=False,direct_reports=reports,genuine_replay=replay);q.save(s.out/name/'DIRECT-COMPARISON.json',result);return result
        results=[];errors=[]
        with concurrent.futures.ThreadPoolExecutor(max_workers=a.workers) as pool:
            futures={pool.submit(case,name):name for name in names}
            for future in concurrent.futures.as_completed(futures):
                try:results.append(future.result())
                except Exception as error:errors.append(dict(case=futures[future],reason=repr(error),traceback=traceback.format_exc()))
        if errors:q.save(s.out/'CASE-FAILURES.json',errors)
        assert not errors,errors
        assert q.sha(__file__)==own_sha and all(q.sha(path)==value for path,value in before.items())
        assert sum(r['expected_source_failure'] for r in results)==2
        s.records.sort(key=lambda r:r['name']);s.complete(dict(cases=sorted(results,key=lambda r:r['name']),successful_original_cases=17,expected_original_failures=2,source_reports_per_success=3,genuine_replays=3,whole_original_profile_matrix=True,subsampled=False));return 0
    except Exception as error:
        if s is not None:q.save(s.out/'FAILURE.json',dict(status='FAIL_ORIGINAL_PROFILE_MATRIX_QUALIFICATION',reason=repr(error),traceback=traceback.format_exc(),processes=s.records))
        traceback.print_exc();return 2

if __name__=='__main__':raise SystemExit(main())
