"""Read a saved native-component checkpoint; never claim to rerun upstream fits."""
from pathlib import Path
import argparse, hashlib, io, json, xml.etree.ElementTree as ET, zipfile


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def verify(root, jar=None):
    checkpoint=root/'docs/literature/squidpy-native-qualification'
    status=json.loads((checkpoint/'STATUS.json').read_bytes())
    assert status['full_workflow_adapter_present'] is False
    assert status['new_family_credit']==status['new_reviews']==0
    for name,expected in status['source_code'].items():
        relative=Path(name)
        assert not relative.is_absolute() and '..' not in relative.parts
        assert pin((root/relative).read_bytes())==expected, name
    index_raw=(checkpoint/'EVIDENCE.json').read_bytes()
    assert pin(index_raw)==status['evidence_index']
    index=json.loads(index_raw);archive_raw=(checkpoint/'evidence.zip').read_bytes()
    assert pin(archive_raw)==status['evidence_archive']
    with zipfile.ZipFile(io.BytesIO(archive_raw)) as archive:
        names=archive.namelist();assert len(names)==len(set(names))
        assert set(names)=={'objects/'+v['sha256'] for v in index['files'].values()}
        objects={name:archive.read(name) for name in names}
    files={name:objects['objects/'+expected['sha256']] for name,expected in index['files'].items()}
    for name,expected in index['files'].items(): assert pin(files[name])==expected, name
    result=json.loads(files['normal/RESULT.json']);assert result['exit_code']==0 and result['source_unchanged']
    suites=[ET.fromstring(files['normal/tests/'+name]) for name in (
        'TEST-org.jlinalg.squidpy.SquidpyAutocorrelationTest.xml',
        'TEST-org.jlinalg.fusion.FusionAssociationTest.xml',
        'TEST-org.jlinalg.spatial.SpatialStatisticsTest.xml')]
    assert [int(s.attrib['tests']) for s in suites]==[728,5,7]
    assert all(int(s.attrib[k])==0 for s in suites for k in ['failures','errors','skipped'])
    packaged=json.loads(files['packaged/RESULT.json'])
    assert packaged['tests_per_JVM']==728 and not packaged['full_workflow_adapter_present']
    assert packaged['producer']==result['producer']==status['producer']
    for host,version,operating_system in [('windows21','21.','Windows'),('windows25','25','Windows'),('linux21','21.','Linux')]:
        assert json.loads(files['packaged/'+host+'.RESULT.json'])['exit_code']==0
        output=files['packaged/'+host+'.stdout'].decode('utf-8')
        assert 'Actual Java runtime: '+version in output and '; OS: '+operating_system in output
        lines=[s for s in output.splitlines() if s.startswith('RESULT ')]
        assert len(lines)==1 and json.loads(lines[0][7:])==dict(tests=728,passed=728,failed=0,skipped=0,native_loaded_from_producer_jar=True)
    payload=json.loads(files['packaged/PAYLOADS.json'])
    assert payload['previous_payloads_byte_identical']==22863 and not payload['previous_payloads_modified']
    assert len(payload['added'])==8 and all(n.startswith(('org/jlinalg/squidpy/','META-INF/licenses/SQUIDPY-')) for n in payload['added'])
    examples=json.loads(files['examples/RESULT.json']);assert len(examples['cases'])==6
    assert all(c['exit_code']==c['expected'] for c in examples['cases'])
    fixture_raw=(root/'src/test/resources/org/jlinalg/squidpy/autocorrelation-original.zip').read_bytes()
    with zipfile.ZipFile(io.BytesIO(fixture_raw)) as archive:
        provenance=json.loads(archive.read('PROVENANCE.json'))
        assert len(archive.namelist())==11
        for name,expected in provenance['files'].items(): assert pin(archive.read(name))==expected
        assert provenance['synthetic_inputs_only'] and not provenance['native_implementation_used_to_generate_expected']
        for edition in ['1.1.2','1.2.2','1.8.4']:
            original=json.loads(archive.read(edition+'/RESULT.json'))
            assert original['squidpy']==edition and original['original_calls']==80
            assert original['original_returned']==64 and original['original_raised']==16
            assert not original['source_patched'] and not original['native_implementation_executed']
            calls=json.loads(archive.read(edition+'/ORIGINAL-CASES.json'))
            assert len(calls)==80 and all(c['expression_dtype']=='float64' for c in calls)
    producer_checked=False
    if jar is not None:
        raw=jar.read_bytes();assert pin(raw)=={k:status['producer'][k] for k in ['bytes','sha256']}
        producer_checked=True
    return dict(status='PASS_SQUIDPY_NATIVE_PREPARED_GRAPH_COMPONENT_CHECKPOINT',
        source_files=len(status['source_code']),evidence_files=len(files),normal_tests=740,
        packaged_tests_per_JVM=728,packaged_JVMs=3,original_public_calls=240,
        original_calls_returned=192,original_calls_raised=48,previous_payloads_byte_identical=22863,
        actual_producer_checked=producer_checked,original_or_Java_analysis_reexecuted=False,
        full_workflow_adapter_present=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,default=Path(__file__).resolve().parents[2])
    parser.add_argument('--jar',type=Path)
    args=parser.parse_args()
    print(json.dumps(verify(args.root,args.jar),indent=2))


if __name__=='__main__':
    main()
