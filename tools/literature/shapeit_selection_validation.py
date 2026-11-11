#!/usr/bin/env python3
"""Verify SHAPEIT5 scanner evidence; never admit, install or execute a phaser."""
import hashlib
import io
import json
from pathlib import Path
import zipfile
from catalog_source_integrity import verify_source
from shapeit_development_validation import archive

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'
digest=lambda raw:hashlib.sha256(raw).hexdigest()
SOURCE_PATHS={'.gitattributes','src/main/java/org/jlinalg/genetics/ShapeitCommonVariants.java',
              'src/test/java/org/jlinalg/genetics/ShapeitCommonVariantsTest.java',
              'src/test/resources/org/jlinalg/genetics/shapeit-selection/original-controls.zip',
              'tools/literature/shapeit-reference/SelectionProtocol.cpp','tools/literature/shapeit-reference/MakeSelectionFixture.cpp',
              'tools/literature/shapeit-reference/collect_selection_evidence.py','tools/literature/shapeit_selection_validation.py',
              'tools/literature/test_shapeit_selection_validation.py','tools/literature/catalog_source_integrity.py',
              'tools/literature/shapeit_development_validation.py'} | {
                  prefix+name for prefix in ['LICENSES/','src/main/resources/META-INF/licenses/']
                  for name in ['SHAPEIT4-MIT.txt','SHAPEIT5-MIT.txt','SHAPEIT5-XCF-MIT.txt']}


def verify(root=ROOT,data=DATA):
    if not __debug__:raise RuntimeError('Verification requires normal Python; remove -O or -OO')
    root,data=Path(root),Path(data)
    proof=json.loads((data/'shapeit-selection-validation.json').read_bytes())
    assert proof['schema_version']==1 and proof['status']=='passed-native-common-selection-on-original-joined-streams'
    assert proof['source_commit']=='c34d4db3e99a2f7e23deb727671ae260901a5886'
    assert not proof['public_phasing_adapter_available'] and not proof['whole_paper_reproduced']
    assert not proof['native_join_reconstruction_available'] and not proof['XCF_binary_payloads_validated']
    assert proof['new_tool_integrations']==0
    assert (proof['original_scanner_calls'],proof['complete_scanner_results'],proof['expected_original_failures'],proof['additional_checked_source_boundary_calls'])==(50,36,14,1)
    assert (proof['synchronized_events'],proof['selected_sites'],proof['native_tests_per_JVM'],proof['JVM_profiles'])==(641,336,57,3)
    pins=proof['source_evidence'];assert len(pins)==len(SOURCE_PATHS) and {item['path'] for item in pins}==SOURCE_PATHS
    for item in pins:verify_source(root,data,item['path'],item['sha256'])
    native=proof['native_controls'];raw=(data/native['path']).read_bytes()
    assert len(raw)==native['bytes'] and digest(raw)==native['sha256'];controls=archive(raw,16777216)
    for item in pins:assert digest(controls['source/'+item['path']])==item['sha256']
    matrix=json.loads(controls['matrix/observed.json'])
    assert matrix['status']=='passed57exact-native-package-tests-three-JVMs-and-strict17-javadoc'
    assert set(matrix['profiles'])=={'Windows21','Windows25','Linux21'}
    assert all(row=={'tests':57,'passed':57,'skipped':0} for row in matrix['profiles'].values())
    assert {row['label'] for row in matrix['runs']}=={'strict-Java17','strict-javadoc','compile-package-tests','Windows21','Windows25','Linux21'}
    assert all(row['exit_code']==0 for row in matrix['runs'])
    for path,expected in matrix['source_sha256'].items():
        assert digest(controls['source/'+path])==expected
        verify_source(root,data,path,expected)
    candidate=controls['artifacts/shapeit-native-selection.jar']
    assert digest(candidate)==matrix['candidate_sha256']==proof['native_candidate_sha256']
    with zipfile.ZipFile(io.BytesIO(candidate)) as jar:
        classes=[name for name in jar.namelist() if name.endswith('.class')]
        assert classes and all(name.startswith('org/jlinalg/genetics/ShapeitCommonVariants') for name in classes)
        assert all(int.from_bytes(jar.read(name)[6:8],'big')==61 for name in classes)
        for name in ['SHAPEIT4-MIT.txt','SHAPEIT5-MIT.txt','SHAPEIT5-XCF-MIT.txt']:
            assert jar.read('META-INF/licenses/'+name)==controls['source/src/main/resources/META-INF/licenses/'+name]
    raw=controls['artifacts/original-selection-controls.zip']
    assert digest(raw)==proof['original_controls_sha256']=='3ba2a853841b136e05c72fc54aa4b001125b43b9e4f5d71744291f04206463c3'
    original=archive(raw,8388608);provenance=json.loads(original['provenance.json'])
    assert (provenance['original_scanner_calls'],provenance['complete_scanner_results'],provenance['expected_original_failures'],provenance['additional_checked_source_boundary_calls'])==(50,36,14,1)
    assert (provenance['synchronized_events_in_complete_controls'],provenance['selected_sites_in_complete_controls'])==(641,336)
    assert provenance['original_source_members_verified']==287
    assert provenance['XCF_payloads_are_empty_scan_controls_not_genotype_or_phasing_validation']
    assert provenance['actual_runtime_library_is_conda_build_not_newly_compiled_from_inspected_source']
    assert not provenance['public_phasing_adapter_available']
    observed=json.loads(original['capture/observed.json'])
    assert observed['status']=='passed-original-common-stage-selection-and-joined-panel-controls' and observed['source_commit']==proof['source_commit']
    assert digest(original['protocol/SelectionProtocol.cpp'])==observed['source_protocol_sha256']
    assert digest(original['protocol/MakeSelectionFixture.cpp'])==observed['fixture_generator_sha256']
    for name in ['SelectionProtocol.cpp','MakeSelectionFixture.cpp','collect_selection_evidence.py']:
        assert original['protocol/'+name]==controls['source/tools/literature/shapeit-reference/'+name]
    reference_raw=original['source/original-reader-source-closure.zip']
    assert digest(reference_raw)=='7ce0722fbe1499ed72d5a46d81c9945d1141b24a7abbec07e5e1d3ce799e8f02'
    reference=archive(reference_raw,4194304)
    xcf=reference['upstream/5-c34d/xcftools/common/src/utils/xcf.h']
    notice=xcf[:xcf.index(b'*/')+2]+b'\n'
    assert notice==original['licenses/SHAPEIT5-XCF-MIT.txt']==controls['source/LICENSES/SHAPEIT5-XCF-MIT.txt']
    assert digest(notice)=='58f960c3f256ed6cf7ccfb8b14d28cb0a7e2e2e1b48ef0bcb74c9c1941ae87ae'
    boundary=json.loads(original['source-boundary/observed.json'])
    assert boundary['status']=='confirmed-original-void-file-gap-vector-boundary'
    assert (boundary['normal_original_scanner_exit_code'],boundary['checked_original_scanner_exit_code'])==(-11,-6)
    assert '__n < this->size()' in original['source-boundary/empty-main.stderr'].decode()
    cases=observed['cases'];assert len(cases)==len({c['name'] for c in cases})==50
    assert len(original['cases.tsv'].decode().splitlines())==51
    fixtures=observed['fixtures'];assert len(fixtures)==27
    for name,item in fixtures.items():
        assert digest(original['capture/'+item['path']])==item['sha256']
        if item['mode']=='xcf':
            stem=item['path'].removesuffix('.bcf')
            assert original['capture/'+stem+'.bin']==b''
            assert len(original['capture/'+stem+'.fam'].decode().splitlines())==item['samples']
    complete=failed=events=selected=0
    for case in cases:
        raw=original['capture/'+case['result']];assert digest(raw)==case['sha256'];lines=raw.decode().splitlines()
        if case['complete']:
            complete+=1;assert case['exit_code']==0 and case['expected_error'] is None
            h=next(line.split('\t') for line in lines if line.startswith('H\t'))
            e=lines[-1].split('\t');assert e[0]=='E'
            js=[line for line in lines if line.startswith('J\t')];ks=[line for line in lines if line.startswith('K\t')]
            assert int(h[3])==int(e[1])==len(js) and int(h[4])==int(e[2])==len(ks)
            assert sum(line.split('\t')[2]=='1' for line in js)==len(ks)
            assert [int(line.split('\t')[1]) for line in js]==list(range(len(js)))
            assert [int(line.split('\t')[1]) for line in ks]==list(range(len(ks)))
            events+=len(js);selected+=len(ks)
        else:
            failed+=1;assert case['exit_code']!=0 and case['expected_error'] is not None
            assert not any(line.startswith('E\t') for line in lines)
            if case['name']=='empty-main-plus-ref50':assert case['expected_error']=='source-empty-main' and case['exit_code']==-11
    assert (complete,failed,events,selected)==(36,14,641,336)
    result={'status':'passed','original_scanner_calls':50,'complete_original_masks':36,'expected_original_failures':14,
            'additional_checked_source_boundary_calls':1,'synchronized_events':641,'selected_sites':336,
            'native_tests_per_JVM':57,'JVM_profiles':3,'public_phasing_adapter_available':False,
            'scope':'Common-stage mask and counters on original joined events only. No native join reconstruction, binary genotype payload validation, protected file/runtime phasing workflow or phase-accuracy claim.'}
    print(json.dumps(result,indent=2));return result


if __name__=='__main__':verify()
