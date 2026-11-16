#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete original-edition adapter controls against saved direct references."""
from pathlib import Path
import argparse,hashlib,json,os,signal,subprocess,sys,time,traceback
import numpy as np

def sha(path):
    h=hashlib.sha256()
    with Path(path).open('rb') as stream:
        for raw in iter(lambda:stream.read(1048576),b''):h.update(raw)
    return h.hexdigest()

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')

def properties(path):
    return dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith('#'))

class Suite:
    def __init__(self,args):
        self.args=args;self.out=args.out.resolve();self.out.mkdir(parents=True,exist_ok=False)
        self.jar=args.jar.resolve();self.runtime=args.runtime.resolve();self.reference=args.reference.resolve();self.records=[]
        self.jar_sha=sha(self.jar);self.driver_sha=sha(__file__)
        (self.out/'as-run-qualify_adapter.py').write_bytes(Path(__file__).read_bytes())
        save(self.out/'ARTIFACT.json',dict(jar=str(self.jar),sha256=self.jar_sha,driver_sha256=self.driver_sha,full_adapter_qualification_claimed=False))
    def execute(self,name,arguments,expected=0,timeout=1100):
        folder=self.out/name;folder.mkdir();command=['/usr/bin/java','-Xmx512m','-jar',str(self.jar),'pops-original-tools',*arguments]
        start=time.monotonic();env=os.environ.copy()
        with (folder/'stdout').open('xb') as so,(folder/'stderr').open('xb') as se:
            child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=so,stderr=se,env=env,start_new_session=True)
            timed_out=False
            try:code=child.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                timed_out=True;os.killpg(child.pid,signal.SIGTERM)
                try:code=child.wait(timeout=20)
                except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);code=child.wait()
        record=dict(name=name,command=command,pid=child.pid,exit_code=code,expected_exit=expected,timed_out=timed_out,seconds=round(time.monotonic()-start,3))
        save(folder/'PROCESS.json',record);self.records.append(record);print(json.dumps(record),flush=True)
        assert code==expected and not timed_out,(name,code,expected,'Inspect retained consoles')
        assert sha(self.jar)==self.jar_sha and sha(__file__)==self.driver_sha,'Qualification code/artifact changed while running'
        return folder
    def request(self,name,fields,expected=0):
        config=dict(profile='pops-original-v1',runtime=str(self.runtime),genome_build='synthetic-control',feature_set_id='source-control-not-author-features',target_source_id='synthetic-not-biological-validation',java_heap_mb='1024',python_address_space_mb='8192',wall_ms='900000',max_input_bytes='1073741824',max_work_bytes='2147483648',max_files='2000')
        config.update(fields);request=self.out/(name+'.properties')
        assert all(not any(c in str(v) for c in ['\r','\n','\\']) for v in config.values())
        request.write_text(''.join(k+'='+str(v)+'\n' for k,v in sorted(config.items())),encoding='utf-8')
        bundle=self.out/(name+'-bundle');self.execute(name,['--request',str(request),'--out',str(bundle)],expected)
        assert (bundle/'COMPLETED.properties').is_file()==(expected==0),name
        if expected:assert (bundle/'FAILURE.txt').is_file()
        return bundle
    def replay(self,name,bundle):
        replay=self.out/(name+'-bundle');self.execute(name,['--verify',str(bundle),'--out',str(replay)])
        assert (replay/'COMPLETED.properties').is_file()
        for family in ['results','audit']:
            def inventory(root):return {p.relative_to(root).as_posix():sha(p) for p in root.rglob('*') if p.is_file()}
            assert inventory(bundle/family)==inventory(replay/family),(name,family)
        return replay
    def complete(self,summary):
        assert sha(self.jar)==self.jar_sha and sha(__file__)==self.driver_sha
        save(self.out/'COMPLETED.json',dict(status='PASS_'+self.args.suite.upper().replace('-','_')+'_BOUNDED_ADAPTER_CONTROLS',artifact_sha256=self.jar_sha,processes=self.records,summary=summary,full_adapter_qualification_claimed=False,whole_goal_complete=False))

def legacy(s):
    r=s.reference/'v01-complete-22-chromosomes'
    cfg=dict(edition='0.1',task='select-predict',features_text=str(r/'features.tsv'),magma_prefix=str(r/'magma'),gene_location=str(r/'gene-location.tsv'),control_features=str(r/'control.features'),chromosomes='all')
    bundle=s.request('all-22',cfg)
    assert (bundle/'results/selected.features').read_bytes()==(r/'selected.features').read_bytes()
    for c in range(1,23):
        for suffix,name in [('coefs','coefficients.tsv'),('results','scores.tsv')]:
            assert (bundle/('results/chr-%02d/'%c)/name).read_bytes()==(r/('original.%d.%s'%(c,suffix))).read_bytes(),(c,name)
    s.replay('all-22-replay',bundle)
    s.complete(dict(target_genes=528,prediction_genes=528,chromosomes=22,direct_reports_byte_identical=45,full_requested_set=True,subsampled=False))

def legacy_edges(s):
    r=s.reference/'v01-complete-22-chromosomes'
    cfg=dict(edition='0.1',task='predict',features_text=str(r/'features.tsv'),magma_prefix=str(r/'magma'),gene_location=str(r/'gene-location.tsv'),control_features=str(r/'control.features'),chromosomes='1')
    one=s.request('single-selected',dict(cfg,selected_features=str(r/'one.features')),2)
    assert 'TypeError' in (one/'runtime-logs/predict-01.stderr').read_text()
    zero=s.request('empty-selected-control-only',dict(cfg,selected_features=str(r/'zero.features')))
    assert 'control-only' in (zero/'WARNINGS.md').read_text()
    assert (zero/'results/chr-01/scores.tsv').read_bytes()==(r/'control-only.1.results').read_bytes()
    assert (zero/'results/chr-01/coefficients.tsv').read_bytes()==(r/'control-only.1.coefs').read_bytes()
    s.replay('control-only-replay',zero)
    selection=s.request('selection-only',dict(edition='0.1',task='select',features_text=cfg['features_text'],magma_prefix=cfg['magma_prefix']))
    assert properties(selection/'COMPLETED.properties')['fit_performed']=='false'
    assert (selection/'results/selected.features').read_bytes()==(r/'selected.features').read_bytes()
    s.complete(dict(single_selected_preserves_original_TypeError=True,empty_selected_is_control_only=True,selection_only_is_not_a_fit=True))

def compare_munge(bundle,prefix):
    actual=bundle/'results';expected={p.name[len(prefix.name)+1:]:p for p in prefix.parent.glob(prefix.name+'.*') if p.name.endswith('.rows.txt') or '.cols.' in p.name or ('.mat.' in p.name and p.suffix=='.npy')}
    assert expected
    for suffix,p in expected.items():
        q=actual/('features.'+suffix);assert q.is_file(),q
        if suffix.endswith('.npy'):
            a=np.load(str(p));b=np.load(str(q));assert a.shape==b.shape and np.array_equal(a,b,equal_nan=True),(p,q)
        else:assert p.read_bytes()==q.read_bytes(),(p,q)

def munge(s):
    r=s.reference/'munger'
    for policy in ['raise','ignore','mean','zero']:
        bundle=s.request(policy,dict(edition='0.2',task='munge',gene_annotation=str(r/'inputs/annotation.tsv'),feature_directory=str(r/'inputs/raw'),nan_policy=policy,chunk_columns='2'),2 if policy=='raise' else 0)
        if policy=='raise':assert 'Missing genes in feature matrix' in (bundle/'runtime-logs/munge.stderr').read_text()
        else:
            compare_munge(bundle,r/policy/'original');assert properties(bundle/'COMPLETED.properties')['fit_performed']=='false'
            if policy in ['ignore','mean']:assert 'NaNs' in (bundle/'WARNINGS.md').read_text()
            if policy=='mean':s.replay('mean-replay',bundle)
    s.complete(dict(explicit_policies=['raise','ignore','mean','zero'],all_missing_mean_stays_nan=True,preparation_only=True))

def lasso(s):
    counts=[]
    for name in ['v02-lasso','v02-lasso-stress']:
        r=s.reference/name;reference=json.loads((r/'STATUS.json').read_bytes())
        bundle=s.request(name,dict(edition='0.2',task='fit',gene_annotation=str(r/'annotation.tsv'),feature_prefix=str(r/'features'),feature_chunks=str(reference['feature_chunks']),target_type='custom',scores=str(r/'scores.tsv'),method='lasso',p_cutoff='1.01',save_matrix_files='true'))
        for suffix,destination in [('coefs','coefficients.tsv'),('preds','predictions.tsv'),('marginals','marginals.tsv'),('traindata','training-data.tsv'),('matdata','prediction-data.tsv')]:
            assert (bundle/'results'/destination).read_bytes()==(r/('original.'+suffix)).read_bytes(),(name,suffix)
        s.replay(name+'-replay',bundle);counts.append(dict(name=name,target_genes=reference['target_genes'],prediction_genes=reference['prediction_genes'],input_features=reference['input_features'],direct_reports_byte_identical=5))
    s.complete(dict(controls=counts,random_seed=42,genuine_replay=True,subsampled=False))

def official_munge(s):
    assert s.args.upstream is not None
    u=s.args.upstream.resolve();annotation=u/'example/data/utils/gene_annot_jun10.txt';raw=u/'example/data/features_raw';prefix=u/'example/data/features_munged/pops_features'
    source_before={str(p):sha(p) for p in [annotation,*raw.iterdir(),*prefix.parent.glob(prefix.name+'.*')] if p.is_file()}
    save(s.out/'OFFICIAL-INPUTS-BEFORE.json',source_before)
    bundle=s.request('official-complete',dict(edition='0.2',task='munge',gene_annotation=str(annotation),feature_directory=str(raw),nan_policy='raise',chunk_columns='500',genome_build='official-tutorial-build-not-independently-validated',feature_set_id='complete-selected-upstream-example',target_source_id='feature-preparation-no-target-analysis'))
    compare_munge(bundle,prefix);report=json.loads((bundle/'audit/summary.json').read_bytes())
    assert report['prepared_genes']==18383 and report['prepared_features']==508 and report['prepared_chunks']==2 and report['remaining_nan_values']==0 and report['remaining_infinite_values']==0
    s.replay('official-complete-replay',bundle)
    assert all(sha(p)==v for p,v in source_before.items())
    s.complete(dict(prediction_genes=18383,features=508,chunks=2,complete_official_raw_feature_files=len(list(raw.iterdir())),prepared_values_match_complete_upstream_example=True,genuine_replay=True,subsampled=False,preparation_only=True))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--jar',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--reference',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--suite',choices=['legacy','legacy-edges','munge','official-munge','lasso'],required=True);p.add_argument('--upstream',type=Path);a=p.parse_args();s=None
    try:
        s=Suite(a);{'legacy':legacy,'legacy-edges':legacy_edges,'munge':munge,'official-munge':official_munge,'lasso':lasso}[a.suite](s);return 0
    except Exception as error:
        if s is not None:save(s.out/'FAILURE.json',dict(status='FAIL_ADAPTER_QUALIFICATION_NO_FULL_ACCEPTANCE',reason=repr(error),traceback=traceback.format_exc(),processes=s.records))
        traceback.print_exc();return 2

if __name__=='__main__':raise SystemExit(main())
