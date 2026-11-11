# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Portable complete analytical-normal evidence audit; no R, installation or optimizer replay."""
import gzip,json,zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin
from catalog_source_integrity import verify_source,reverse_delta
from validate_normal_fit import audit

BASE='build/literature-review/fitdistrplus-runtime/'


def verify():
    folder=ROOT/'docs/literature/normal-fit-development';data=ROOT/'docs/literature/omics-2023-2026';p=json.loads((folder/'VALIDATION.json').read_bytes())
    assert p['status']=='passed-native-analytical-normal-fit-controls' and p['component_admitted'] and not p['implementation_credit'] and not p['whole_goal_complete']
    for line in (folder/'SHA256SUMS').read_text(encoding='utf8').splitlines():
        sha,name=line.split('  ',1);assert name in ['controls.zip','WARNINGS.md','VALIDATION.json'] and pin(folder/name)['sha256']==sha
    assert pin(folder/'controls.zip')==p['archive'] and pin(folder/'WARNINGS.md')==p['warnings_document'];files=unpack(folder/'controls.zip',p)
    for name,value in p['source_pins'].items():verify_source(ROOT,data,name,value['sha256']);assert byte_pin(files['current-source/'+name])==value
    for name,value in p['parent_proofs'].items():assert pin(ROOT/name)==value
    get=lambda name:json.loads(files[BASE+name]);build=get('native-build-1/COMPLETED.json');validation=get('native-validation-1/COMPLETED.json');example=get('native-example-1/COMPLETED.json');original=get('original-controls-1/COMPLETED.json');controls=get('original-controls-1/controls.json')
    assert all(v['artifact']==p['artifact'] for v in [build,validation,example]);assert original['status']=='captured-original-normal-fit-and-tail-BH-controls' and original['preexisting_libraries_unchanged'] and original['preexisting_files']==25602
    assert files[BASE+'original-controls-1/LIBRARIES-BEFORE.json']==files[BASE+'original-controls-1/LIBRARIES-AFTER.json']
    assert len(controls['fits'])==43 and len(controls['probabilities'])==36 and len(original['profile']['guarded_functions'])==5
    assert original['profile']['source_bodies_and_formals_checked_before_and_after'] and not original['software_installed']
    assert sum(row['native_positive_finite_variance_domain'] for row in controls['fits'].values())==37
    assert not controls['fits']['tiny']['original_methods']['mle']['returned'] and not controls['fits']['near_constant']['original_methods']['mle']['returned']
    assert controls['fits']['constant']['original_methods']['mle']['estimate']['sd']==0.1 and controls['fits']['constant']['original_methods']['mme']['loglik']=='Inf'
    fixture=files['current-source/src/test/resources/org/jlinalg/distributional/normal-fit/original-controls-v1.json.gz'];assert gzip.decompress(fixture)==files[BASE+'original-controls-1/controls.json']
    assert len(validation['JVMs'])==3 and all(j['tests']==86 and j['skipped']==0 for j in validation['JVMs'])
    assert len(validation['protocols'])==51
    totals=[0,0];successes=0
    for row in validation['protocols']:
        root=BASE+'native-validation-1/'+row['id']+'/result/';state=json.loads(files[root+'analysis-status.json']);assert state['result_admitted'] is row['result_admitted'] is (row['exit_code']==0)
        assert any(n.startswith(root+'results/') for n in files) is state['result_admitted']
        assert 'REQUIRED ACTION:' in files[root+'WARNINGS.md'].decode('utf8')
        if row['result_admitted']:
            reference=None
            if row['id'].startswith('background-'):
                tail=row['id'].removeprefix('background-');reference=next(r for r in controls['probabilities'] if float(r['mean'])==0 and float(r['sd'])==1 and r['tail']==tail)
            result=audit({n[len(root):]:raw for n,raw in files.items() if n.startswith(root)},reference);assert all(row[k]==v for k,v in result.items());totals[0]+=result['observations'];totals[1]+=result['tests'];successes+=1
        else:assert state['status']=='FAIL2_NO_RESULT_ADMITTED' and state['cause']
    root=BASE+'native-example-1/result/';result=audit({n[len(root):]:raw for n,raw in files.items() if n.startswith(root)},None);assert result==example['comparison'];totals[0]+=result['observations'];totals[1]+=result['tests'];successes+=1
    assert totals==[p['audited_observation_rows'],p['audited_test_rows']] and successes==41
    assert files[BASE+'native-validation-1/late-table-capacity/result/unadmitted-partial-results/fit.tsv']==files[BASE+'native-validation-1/background-upper/result/results/fit.tsv']
    before=json.loads(files['artifact-parent-entries.json']);after=json.loads(files['artifact-candidate-entries.json']);assert all(after[n]==v for n,v in before.items()) and len(before)==p['unchanged_parent_entries'] and set(after)-set(before)==set(p['added_entries'])
    for name in p['added_entries']:assert byte_pin(files['candidate-owned-entry/'+name])==after[name]
    for label,key in [('parent','parent_artifact'),('candidate','artifact')]:
        path=ROOT/p[key]['path']
        if path.exists():
            assert pin(path)=={k:p[key][k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as z:assert len(z.namelist())==len(set(z.namelist())) and {n:byte_pin(z.read(n)) for n in z.namelist()}==(before if label=='parent' else after)
    delta=json.loads(files['current-source/docs/literature/omics-2023-2026/normal-fit-checkout-shared-source-compatibility.json']);assert len(delta['paths'])==1 and delta['paths'][0]['path']=='.gitattributes';reverse_delta(files['current-source/.gitattributes'],delta['paths'][0])
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==p['canonical_executable_unchanged']
    print('Verified analytical normal fit:86 checks on each of three JVMs,51 packaged protocols, all original outcomes retained, complete fit/BH tables and unchanged parent entries')
    return p


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable scientific/source checks')
    verify()
