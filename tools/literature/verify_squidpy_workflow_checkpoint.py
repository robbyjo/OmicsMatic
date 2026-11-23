"""Read actual saved Squidpy qualification; no Python/Java analysis is rerun."""
from pathlib import Path
import argparse,hashlib,io,json,xml.etree.ElementTree as ET,zipfile


def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def verify(root,jar=None):
    q=root/'docs/literature/squidpy-workflow-qualification';status=json.loads((q/'STATUS.json').read_bytes())
    assert status['status']=='PASS_SQUIDPY_ORIGINAL_WORKFLOW_SCOPED_CHECKPOINT'
    assert status['new_family_credit']==status['new_reviews']==0
    assert not any(status[k] for k in ('whole_tool_qualified','whole_paper_reproduced','whole_goal_complete'))
    for name,expected in status['source_code'].items():
        path=Path(name);assert not path.is_absolute() and '..' not in path.parts
        assert pin((root/path).read_bytes())==expected,name
    index_raw=(q/'EVIDENCE.json').read_bytes();archive_raw=(q/'evidence.zip').read_bytes()
    assert pin(index_raw)==status['evidence_index'] and pin(archive_raw)==status['evidence_archive']
    index=json.loads(index_raw)['files']
    with zipfile.ZipFile(io.BytesIO(archive_raw)) as archive:
        names=archive.namelist();assert len(names)==len(set(names))
        assert set(names)=={'objects/'+v['sha256'] for v in index.values()}
        for info in archive.infolist():
            assert info.file_size<80_000_000
            assert hashlib.sha256(archive.read(info)).hexdigest()==info.filename[8:]
        def raw(name):
            value=archive.read('objects/'+index[name]['sha256']);assert pin(value)==index[name],name
            return value
        def obj(name):return json.loads(raw(name))
        normal=obj('normal/RESULT.json');assert normal['exit_code']==0 and normal['source_unchanged'] and normal['producer']==status['producer']
        expected_suites={'SquidpyWorkflowAuditTest':4,'SquidpyWorkflowContractsTest':14,'SquidpyWorkflowDispatchTest':2,'SquidpyWorkflowNpyTest':3,
                         'FusionAssociationTest':5,'SpatialStatisticsTest':7,'SquidpyAutocorrelationTest':728}
        suites=[ET.fromstring(raw(name)) for name in index if name.startswith('normal/tests/') and name.endswith('.xml')]
        assert {s.attrib['name'].split('.')[-1]:int(s.attrib['tests']) for s in suites}==expected_suites
        assert all(int(s.attrib[k])==0 for s in suites for k in ('errors','failures','skipped'))
        assert sum(int(s.attrib['tests']) for s in suites)==status['normal_tests']==763
        packaged=obj('packaged/RESULT.json');assert packaged['tests_per_JVM']==763 and packaged['producer']=={k:status['producer'][k] for k in ('bytes','sha256')}
        for host,version,operating_system in [('windows21','21.','Windows'),('windows25','25','Windows'),('linux21','21.','Linux')]:
            assert obj('packaged/'+host+'.RESULT.json')['exit_code']==0
            text=raw('packaged/'+host+'.stdout').decode('utf-8');assert 'Actual Java runtime: '+version in text and '; OS: '+operating_system in text
            report=[json.loads(line[7:]) for line in text.splitlines() if line.startswith('RESULT ')]
            assert report==[dict(tests=763,passed=763,failed=0,skipped=0,production_classes_from_tested_jar=True)]
        payload=obj('normal/PAYLOADS.json');assert payload['previous_payloads']==22871 and payload['modified']==['org/jlinalg/cli/JLinAlgCli.class']
        assert all(n.startswith(('org/jlinalg/cli/SquidpyWorkflow','org/jlinalg/omics/squidpy-workflow/')) for n in payload['added'])
        outcomes=[];replays=0
        for version,count in [('1.1.2',23),('1.2.2',25),('1.8.4',25)]:
            result=obj('cases/'+version+'/RESULT.json');progress=obj('cases/'+version+'/PROGRESS.json')
            assert progress==result['cases'] and len(progress)==count and all(r['exit_code']==r['expected'] for r in progress)
            outcomes.extend(progress);replays+=sum(r['case'].endswith('-replay') for r in progress)
        assert len(outcomes)==status['packaged_cases']==73 and replays==11
        assert {code:sum(r['exit_code']==code for r in outcomes) for code in (0,2,3)}=={0:60,2:6,3:7}
        independent=obj('independent/RESULT.json');assert independent['status']=='INDEPENDENT_ORIGINAL_API_COMPARISONS_PASS'
        assert independent['comparisons']==len(independent['cases'])==status['independent_original_comparisons']==66
        assert sum(r['status']=='MATCH_ORIGINAL_EXCEPTION_NO_RESULT' for r in independent['cases'])==3
        for version,returned,raised in [('1.1.2',12,2),('1.2.2',15,1),('1.8.4',16,0)]:
            reference=obj('oracles/'+version+'/RESULT.json')
            assert reference['returned']==returned and reference['raised']==raised and not reference['draft_operations_or_Java_imported']
            assert len(reference['calls'])==returned+raised
        controls=obj('controls/RESULT.json');assert len(controls['cases'])==status['offline_controls']==22
        assert controls['valid_cases']==6 and controls['contradictions']==5 and controls['verifiers']==2
        assert all(c['exit_code']==c['expected'] for c in controls['cases'])
        runtime=obj('runtime-controls/RESULT.json');assert len(runtime['cases'])==status['runtime_controls']==4
        assert all(c['exit_code']==2 and not c['original_call_started'] for c in runtime['cases'])
        hosts=obj('verification-hosts/RESULT.json');assert hosts['checks']==len(hosts['cases'])==44 and all(c['exit_code']==0 for c in hosts['cases'])
        examples=obj('examples/RESULT.json');assert len(examples['cases'])==8 and examples['actual_current_RUN']==4 and examples['actual_current_REPLAY']==1
        assert all(c['exit_code']==0 for c in examples['cases']) and examples['original_1_1_2_reader_explicitly_skipped']
        for version in ('1.1.2','1.2.2','1.8.4'):
            transport=obj('transport/'+version+'/RESULT.json');assert len(transport['cases'])==9
            assert transport['export_operations_source_sha256']==status['source_code']['tools/literature/squidpy_workflow/operations.py']['sha256']
        invalidated=obj('invalidated/INVALIDATED-MATRIX-EXPORTS.json');assert len(invalidated['files'])==16 and invalidated['native_prepared_graph_qualification_affected'] is False
        assert all(r['nul_bytes']>0 and r['invalidated_for_matrix_transport_qualification'] for r in invalidated['files'])
        roles=obj('primary/ROLES.json');assert roles['papers_checked']==250 and len(roles['roles'])==6 and roles['new_family_credit']==roles['new_reviews']==0
    producer_checked=False
    if jar is not None:
        assert pin(jar.read_bytes())=={k:status['producer'][k] for k in ('bytes','sha256')}
        producer_checked=True
    return dict(status=status['status'],source_files=len(status['source_code']),logical_files=len(index),normal_tests=763,
                packaged_tests_per_JVM=763,packaged_cases=73,independent_original_comparisons=66,offline_controls=22,runtime_controls=4,
                previous_payloads_preserved=22870,dispatcher_only_modified=True,invalidated_earlier_matrix_exports=16,
                actual_producer_checked=producer_checked,Python_or_Java_or_network_executed=False,new_family_credit=0,new_reviews=0,
                catalog_admission_complete=False,whole_goal_complete=False)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path);args=parser.parse_args()
    try:print(json.dumps(verify(Path(__file__).resolve().parents[2],args.jar),indent=2))
    except Exception as error:raise SystemExit('FAIL2: saved Squidpy qualification check failed: '+str(error)) from error
