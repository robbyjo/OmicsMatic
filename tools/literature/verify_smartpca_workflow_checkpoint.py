# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify saved native smartpca workflow evidence; no original C is rerun."""
from pathlib import Path,PurePosixPath
import argparse,csv,hashlib,io,json,re,sys,zipfile

ROOT=Path(__file__).resolve().parents[2]
FOLDER='docs/literature/smartpca-workflow-qualification'

def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def verify(root=ROOT,jar=None):
    assert __debug__ and sys.version_info>=(3,11),'Use Python3.11+ without -O'
    folder=root/FOLDER;state=json.loads((folder/'STATUS.json').read_bytes())
    assert state['status']=='PASS_NATIVE_PREPARED_SMARTPCA_RUN_VERIFY_REPLAY'
    assert state['workflow_qualified'] and not any(state[k] for k in ('catalog_admitted','global_dispatch_integrated','FST_implemented','whole_tool_qualified','whole_paper_reproduced','whole_goal_complete'))
    assert state['new_family_credit']==state['new_reviews']==0
    for name,wanted in state['source_code'].items():assert pin((root/name).read_bytes())==wanted,'Changed qualified source: '+name
    index_raw=(folder/'EVIDENCE.json').read_bytes();archive_raw=(folder/'evidence.zip').read_bytes()
    assert pin(index_raw)==state['evidence_index'] and pin(archive_raw)==state['evidence_archive']
    index=json.loads(index_raw)['files']
    with zipfile.ZipFile(io.BytesIO(archive_raw)) as archive:
        names=archive.namelist();assert len(names)==len(set(names)) and set(names)=={'objects/'+v['sha256'] for v in index.values()}
        assert sum(i.file_size for i in archive.infolist())<=536_870_912
        for info in archive.infolist():assert info.file_size<=33_554_432 and re.fullmatch(r'objects/[0-9a-f]{64}',info.filename) and pin(archive.read(info))['sha256']==info.filename[8:]
        def raw(name):
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in name
            value=archive.read('objects/'+index[name]['sha256']);assert pin(value)==index[name],name;return value
        def obj(name):return json.loads(raw(name))
        for name,wanted in state['source_code'].items():assert index['qualified-source/'+name]==wanted
        normal=obj('normal-build-3/RESULT.json');assert normal['exit_code']==0 and normal['source_unchanged'] and normal['producer']==state['producer']
        assert normal['normal_tests']==dict(tests=1892,failures=0,errors=0,skipped=0)
        payloads=obj('normal-build-3/PAYLOADS.json');assert payloads['previous_payloads']==22971 and payloads['modified']==[]
        assert set(payloads['added'])=={'org/jlinalg/cli/SmartpcaToolsCli.class','org/jlinalg/cli/SmartpcaWorkflowAudit.class'}
        build_sources=obj('normal-build-3/SOURCE-INPUTS.json')
        for name,wanted in state['source_code'].items():
            if name.startswith('src/'):assert build_sources[name]==wanted,'Qualified source differs from actual normal producer build: '+name
        fixture=root/'src/test/resources/org/jlinalg/genetics/smartpca-original-analysis-reference.zip'
        with zipfile.ZipFile(fixture) as originals:
            references={d['case']:d for n in originals.namelist() if n.endswith('/reference.json') for d in [json.loads(originals.read(n))]}
        finite={k:v for k,v in references.items() if v['expected_native']=='COMPARE_FINITE_SOURCE_OUTPUTS'};assert len(references)==18 and len(finite)==16
        profiles={'windows21':'protocol-windows21-3','windows25':'protocol-windows25-2','linux21':'protocol-linux21-1'};commands=0
        for host,run in profiles.items():
            result=obj(run+'/COMPLETED.json');assert result['status']=='PASS_PACKAGED_SMARTPCA_PROTOCOL_WITH_ORIGINAL_COMPARISONS'
            assert result['producer']=={k:state['producer'][k] for k in ('bytes','sha256')} and result['new_original_C_calls']==0
            calls=result['commands'];assert len(calls)==(75 if host=='windows21' else 93) and len({r['label'] for r in calls})==len(calls)
            for call in calls:
                assert call['exit_code']==call['expected_exit'] and obj(run+'/commands/'+call['label']+'/RESULT.json')==call
                started=obj(run+'/commands/'+call['label']+'/STARTED.json');assert started['pid']>0 and started['command'][4]=='org.jlinalg.cli.SmartpcaToolsCli'
            commands+=len(calls);assert len(result['original_comparisons'])==16
            for case,reference in finite.items():
                capture=run+'/cases/'+case+'/capture/';actual=obj(capture+'native-result.json');k=reference['options']['components']
                expected=[float(v) for v in reference['original_eval'].split()]
                assert len(expected)==len(actual['all_eigenvalues']) and all(abs(a-b)<=.50001e-6 for a,b in zip(expected,actual['all_eigenvalues']))
                source={}
                for line in reference['original_evec'].splitlines():
                    if line.strip() and not line.lstrip().startswith('#'):
                        fields=line.split();source[fields[0]]=[float(v) for v in fields[1:1+k]]
                rows=list(csv.DictReader(io.StringIO(raw(capture+'coordinates.tsv').decode()),delimiter='\t'));byid={r['sample_id']:r for r in rows}
                assert len(byid)==len(rows)==actual['coordinate_rows']
                basis=[rows[i]['sample_id'] for i in actual['final_basis_indices']]
                for axis in range(k):
                    sign=1 if sum(float(byid[name]['PC'+str(axis+1)])*source[name][axis] for name in basis)>=0 else -1
                    for name,values in source.items():assert abs(sign*float(byid[name]['PC'+str(axis+1)])-values[axis])<=.50001*10**(-reference['original_coordinate_decimal_places'])
                for name,row in byid.items():
                    if name not in source:assert row['source_status'] in ('REMOVED_OUTLIER','INSUFFICIENT_NONMISSING_MARKERS') and row['coordinate_fields_admitted']=='false'
            for case in set(references)-set(finite):
                capture=run+'/cases/'+case+'/capture/';assert capture+'COMPLETED.json' not in index and obj(capture+'analysis-status.json')['results_admitted'] is False
            for label,diagnostic in [('control-resealed-coherent-false-coordinate','Finite scientific value differs'),('control-resealed-coherent-false-options','Finite scientific value differs'),('control-resealed-coherent-false-replay-parent','Integer count/index differs'),('control-resealed-false-producer-backend','Producer kind/backend/provenance contradicts')]:
                assert diagnostic in raw(run+'/commands/'+label+'/stderr').decode()
            timeout=obj(run+'/bad-actual-deadline/compute-process.json');assert timeout['deadline_exceeded'] and timeout['owned_thread_stopped'] and not timeout['calculation_returned']
        assert commands==261
        hosts=obj('host-checks-1/COMPLETED.json');assert len(hosts['hosts'])==3 and hosts['producer']=={k:state['producer'][k] for k in ('bytes','sha256')}
        assert all(h['workflow_methods']==15 and h['component_entrypoints']==2 and h['example_run_verify_commands']==6 and h['all_exit_codes_zero'] for h in hosts['hosts'])
        assert len(hosts['predecessor_readers'])==3 and all(r['exit_code']==0 for r in hosts['predecessor_readers'])
        roles=obj('source-roles-1.json');assert roles==obj('source-roles-linux-1.json') and roles['papers_checked']==250 and len(roles['roles'])==6 and roles['new_family_credit']==roles['new_reviews']==0
        metadata=obj('catalog-snapshot/METADATA.json');assert metadata['before']==metadata['after'] and len(metadata['before'])==9
        for name,wanted in metadata['before'].items():assert index['catalog-snapshot/'+name]==wanted
        # Historical catalog hashes are retained in the packet, not imposed on later catalog admissions.
        assert obj('normal-build-1/RESULT.json')['exit_code']!=0
        assert obj('protocol-windows21-1/CHECKER-FAILURE.json')['status']=='RETAINED_PROTOCOL_CHECKER_FAILURE_NOT_NATIVE_FAILURE'
        assert obj('protocol-windows25-1/CHECKER-FAILURE.json')['status']=='RETAINED_RELATIVE_BASELINE_CHECKER_FAILURE'
    if jar is not None:assert pin(jar.read_bytes())=={k:state['producer'][k] for k in ('bytes','sha256')},'Use exact qualified producer JAR'
    return dict(status=state['status'],logical_evidence_files=len(index),normal_tests=1892,previous_payloads_unchanged=22971,packaged_protocol_commands=261,
        workflow_methods_per_JVM=15,example_commands=18,original_unique_finite_cases=16,original_finite_comparisons_across_profiles=48,new_original_C_calls=0,
        fresh_all250_source_audit_profiles=2,frozen_predecessor_readers=3,workflow_qualified=True,FST_implemented=False,global_dispatch_integrated=False,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path);args=parser.parse_args()
    try:print(json.dumps(verify(jar=args.jar),indent=2))
    except Exception as error:raise SystemExit('FAIL2: saved smartpca workflow verification failed: '+repr(error)+'. Restore the exact published checkpoint/source commit and producer; retain altered/failed evidence. Do not refresh saved hashes to bypass this failure.') from error
