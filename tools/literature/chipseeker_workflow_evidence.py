# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit complete retained ChIPseeker source/controls/artifacts offline; no refitting or execution authentication."""
import csv
import io
import json
from pathlib import Path,PureWindowsPath
import sys
import zipfile
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import byte_pin,unpack
from catalog_source_integrity import verify_source

PREFIX='build/literature-review/chipseeker-runtime/'
VERSIONS=['1.22.1','1.26.2','1.34.1','1.48.1']


def verify(folder=None):
    folder=Path(folder).resolve() if folder else ROOT/'docs/literature/chipseeker-annotation-development'
    proof=json.loads((folder/'VALIDATION.json').read_bytes())
    assert proof['schema_version']==1 and proof['status']=='passed-scoped-ChIPseeker-native-original-annotation-and-root-workflows'
    for name,digest in [line.split('  ',1)[::-1] for line in (folder/'SHA256SUMS').read_text().splitlines()]:assert pin(folder/name)['sha256']==digest
    assert pin(folder/'controls.zip')==proof['archive'] and pin(folder/'WARNINGS.md')==proof['warnings_document']
    files=unpack(folder/'controls.zip',proof)
    for name,wanted in proof['source_pins'].items():
        assert byte_pin(files['current-source/'+name])==wanted,name
        verify_source(ROOT,ROOT/'docs/literature/omics-2023-2026',name,wanted['sha256'])
    def get(name):return json.loads(files[PREFIX+name])
    def raw_json(logical):return json.loads(files[logical])
    artifacts=proof['artifact_chain'];entries={name:raw_json('artifact-entries/'+name+'.json') for name in artifacts}
    for name,previous,build_folder in [('native','parent','native-build-4'),('files','native','files-build-4'),('annotation','files','annotation-build-3')]:
        build=get(build_folder+'/COMPLETED.json');assert build['artifact']==artifacts[name] and build['parent_artifact']==artifacts[previous]
        assert entries[previous].keys()<=entries[name].keys() and all(entries[name][key]==value for key,value in entries[previous].items())
        added=build.get('added_entries',build.get('additions'));assert set(entries[name])-set(entries[previous])==set(added)
    root=get('root-build-1/COMPLETED.json');assert root['artifact']==proof['artifact']==artifacts['root'] and root['parent_artifact']==artifacts['annotation']
    assert [n for n in entries['annotation'] if entries['annotation'][n]!=entries['root'][n]]==['org/jlinalg/cli/JLinAlgCli.class']
    assert set(entries['root'])-set(entries['annotation'])=={'org/jlinalg/cli/ChipSeekerToolsCli.class'}
    expected_owned={n for n in entries['root'] if n not in entries['parent'] or entries['root'][n]!=entries['parent'][n]}
    assert {n.removeprefix('candidate-owned-entries/') for n in files if n.startswith('candidate-owned-entries/')}==expected_owned
    for n in expected_owned:assert byte_pin(files['candidate-owned-entries/'+n])=={k:v for k,v in entries['root'][n].items() if k!='directory'}
    for name,artifact in artifacts.items():
        path=ROOT/artifact['path']
        if path.exists():
            assert pin(path)=={k:artifact[k] for k in ['bytes','sha256']}
            with zipfile.ZipFile(path) as archive:assert {n:dict(byte_pin(archive.read(n)),directory=archive.getinfo(n).is_dir()) for n in archive.namelist()}==entries[name]
    matrix=get('native-matrix-4/COMPLETED.json');assert matrix['tests_per_JVM']==202 and len(matrix['JVMs'])==3
    for row in matrix['JVMs']:
        assert row['tests']==202 and row['skipped']==row['exit_code']==0
        log=files[PREFIX+'native-matrix-4/'+row['JVM']+'.stdout'].decode('utf-8');assert '202 tests successful' in log and '0 tests failed' in log
    file_matrix=get('files-matrix-4/COMPLETED.json');assert (file_matrix['successful_analyses'],file_matrix['expected_skips'],file_matrix['expected_failures'],file_matrix['original_retained_row_comparisons'])==(9,6,24,246)
    assert all(r['exit_code']==r['expected_exit'] for r in file_matrix['records'])
    branch=get('overlap-order-probe-2/COMPLETED.json');assert branch['native_workflows']==20 and branch['original_calls']==80
    original_nearest=get('nearest-original-3/COMPLETED.json');assert (original_nearest['original_calls'],original_nearest['original_successes'],original_nearest['original_failures'])==(652,644,8)
    original=get('annotation-original-2/COMPLETED.json');assert (original['calls'],original['successes'],original['original_errors'])==(200,152,48)
    returned=errors=triples=0
    core=get('annotation-matrix-3/COMPLETED.json');assert len(core['runs'])==16
    for run in core['runs']:
        version=run['version'];assert version in VERSIONS
        oracle={r['id']:r for r in get('annotation-original-2/'+version+'/controls/ORIGINAL.json')['cases']}
        path=PREFIX+'annotation-matrix-3/'+run['jvm']+'/'+version+'/'+run['kind']+'/'
        process=raw_json(path+'process.json');assert process['exit_code']==run['exit_code']==(0 if run['kind']=='returned' else 2)
        state=raw_json(path+'result/analysis-status.json');worker=raw_json(path+'result/worker/worker-report.json')
        assert len(worker['guarded_source_functions'])>=17 and worker['R'].startswith('R version 4.6.1')
        assert 'LC_COLLATE=C' in worker['locale_after'] and 'LC_NUMERIC=C' in worker['locale_after'] and 'utf8' in worker['locale_after'].lower()
        selected={key for key,value in oracle.items() if (value['source_error'] is None)==(run['kind']=='returned')}
        assert {c['id'] for c in worker['cases']}==selected
        if run['kind']=='returned':
            assert state['result_admitted'] is True and len(selected)==38
            reports=raw_json(path+'result/report.json')['analyses'];assert len(reports)==38 and all(r['native_nearest_audit']=='passed' for r in reports)
            for case_id in selected:
                actual=path+'result/results/'+case_id+'/'
                expected=PREFIX+'annotation-original-2/'+version+'/controls/'+case_id+'/'
                assert raw_json(actual+'original-output.json')==raw_json(expected+'original-output.json')
                nearest=raw_json(expected+'original-nearest.json');assert raw_json(actual+'original-nearest.json')==nearest
                assert raw_json(actual+'analysis-status.json')['result_admitted'] is True
                assert len(list(csv.DictReader(io.StringIO(files[actual+'query-ledger.tsv'].decode('utf-8')),delimiter='\t')))==70
                assert files[actual+'original-csAnno.rds'] and files[actual+'original-cell-kinds.json']
                triples+=len(nearest['index_tokens']);returned+=1
        else:
            assert state['result_admitted'] is False and len(selected)==12
            assert not any(n.startswith(path+'result/results/') for n in files)
            for case in worker['cases']:
                assert case['original_error']['message']==oracle[case['id']]['source_error']['message']=='67 elements in value to replace 69 elements';errors+=1
    assert (returned,errors,triples)==(304,96,20976)
    extended=get('annotation-extended-matrix-3/COMPLETED.json');assert len(extended['runs'])==16
    extra_returned=extra_errors=0
    for run in extended['runs']:
        version=run['version'];base=PREFIX+'annotation-extended-matrix-3/'+version+'/'+run['group']+'/result/'
        worker=raw_json(base+'worker/worker-report.json');state=raw_json(base+'analysis-status.json')
        oracle={r['id']:r for r in get('annotation-extended-original-1/'+version+'/ORIGINAL.json')['cases']}
        for case in worker['cases']:
            expected_error=oracle[case['id']]['source_error']
            if expected_error is None:
                expected=get('annotation-extended-original-1/'+version+'/'+case['id']+'/original-output.json')
                assert raw_json(base+'worker/cases/'+case['id']+'/original-output.json')==expected;extra_returned+=1
            else:
                assert version=='1.34.1' and case['id']=='priority-subset'
                assert case['original_error']['message']==expected_error['message']=="object 'lbs' not found";extra_errors+=1
        if run['expected_exit']==0:assert state['result_admitted'] is True
        else:
            assert state['result_admitted'] is False and any(c['original_error'] is None for c in worker['cases'])
            assert not any(n.startswith(base+'results/') for n in files)
    assert (extra_returned,extra_errors)==(23,1)
    recovery=get('annotation-recovery-4/COMPLETED.json');assert len(recovery['runs'])==16 and not recovery['deliberately_modified_runtime_admitted']
    for row in recovery['runs']:
        assert row['expected_exit']==row['observed_exit']
        process=get('annotation-recovery-4/'+row['name']+'/process.json');assert process['exit_code']==row['expected_exit']
    for name in ['worker-timeout','worker-output-capacity']:
        receipt=get('annotation-recovery-4/'+name+'/result/worker/process-receipt.json')
        assert receipt['owned_process_cleanup'] and all(not p['alive_after_cleanup'] for p in receipt['owned_process_cleanup'])
    refusal=get('annotation-recovery-4/same-version-changed-function-refused/result/worker/worker-report.json')
    assert refusal['cases']==[] and 'Loaded original body/formals mismatch: getNearestFeatureIndicesAndDistances' in refusal['detail']
    original=files['negative-control-only/original/ChIPseeker/R/getNearestFeatureIndicesAndDistances.R']
    assert files['negative-control-only/modified/ChIPseeker/R/getNearestFeatureIndicesAndDistances.R']==original.replace(b'sameStrand = FALSE',b'sameStrand = TRUE')
    unicode_base=PREFIX+'annotation-recovery-4/输入-λ/结果/'
    output=raw_json(unicode_base+'results/annotation/original-output.json')
    assert output['annotations'][0]['peak_id']=='NA' and output['annotations'][1]['peak_id']==output['annotations'][2]['peak_id']=='重复"λ'
    ledger=list(csv.DictReader(io.StringIO(files[unicode_base+'results/annotation/query-ledger.tsv'].decode('utf-8')),delimiter='\t'))
    assert len(ledger)==70 and ledger[1]['peak_id']==ledger[2]['peak_id']=='重复"λ'
    setup=get('reference-setup-4/RESULT.json');assert setup['status']=='INSTALLED_ORIGINAL_CHIPSEEKER_REFERENCE_ONLY' and setup['preexisting_libraries_unchanged'] and setup['preexisting_file_count']==29755
    assert get('reference-setup-4/BASES-BEFORE.json')==get('reference-setup-4/BASES-AFTER.json')
    example=get('root-example-1/COMPLETED.json');assert example['artifact']==proof['artifact'] and (example['original_annotation_runs'],example['native_example_runs'],example['help_routes'],example['unchanged_existing_output_refusals'],example['dependency_skips'])==(1,2,6,2,1)
    actual=get('root-example-1/annotation-example/annotation-result/results/genomic-annotation/original-output.json')
    assert actual==get('annotation-original-2/1.48.1/controls/transcript-promoter--2000-1000/original-output.json')
    assert get('root-example-1/linux-native/nearest-result/analysis-status.json')['result_admitted']
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar'
    if canonical.exists():assert pin(canonical)==proof['canonical_executable_unchanged']
    assert (proof['original_reference_calls'],proof['original_returned_reference_calls'],proof['original_reference_errors'])==(224,175,49)
    for name in ['original_genomic_classification_reimplemented','historical_dependency_environment','native_DLL_source_authentication','execution_authentication','whole_paper_reproduced','biological_validity_certified','full_project_test_suite_run','released','pushed','deployed','whole_goal_complete']:assert proof[name] is False,name
    return proof


if __name__=='__main__':
    try:
        if not __debug__:raise RuntimeError('Never disable complete source/control/artifact checks')
        proof=verify();print(json.dumps(dict(status=proof['status'],core_outcomes=400,extended_outcomes=24,recovery_controls=16,native_tests_per_JVM=202,whole_goal_complete=False)))
    except Exception as error:
        print('CHIPSEEKER EVIDENCE FAIL2: '+str(error)+'\nREQUIRED ACTION: preserve the evidence, correct the named source/archive/control discrepancy and repeat verification. No partial evidence admitted.',file=sys.stderr)
        raise
