#!/usr/bin/env python3
"""Verify retained SHAPEIT map results without admitting or executing a phaser."""
import hashlib
import json
from pathlib import Path
import zipfile
import io
from catalog_source_integrity import verify_source
from shapeit_development_validation import archive

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'docs/literature/omics-2023-2026'
digest = lambda raw: hashlib.sha256(raw).hexdigest()


def verify(root=ROOT, data=DATA):
    if not __debug__:
        raise RuntimeError('Verification requires normal Python; remove -O or -OO')
    root, data = Path(root), Path(data)
    proof = json.loads((data/'shapeit-map-validation.json').read_bytes())
    assert proof['schema_version']==1 and proof['status']=='passed-native-map-and-checked-original-boundaries'
    assert not proof['public_phasing_adapter_available'] and not proof['whole_paper_reproduced']
    assert proof['new_tool_integrations']==0
    assert proof['source_profiles']=={'4.1.3':'ca896a27d7d6650c56f516f7de8d537fdf114a1d','4.2.2':'16865e593ad50689271ead985dc8f4296d2f491d','5-c34d':'c34d4db3e99a2f7e23deb727671ae260901a5886'}
    assert (proof['original_observations'],proof['complete_original_observations'],proof['expected_original_failure_observations'])==(127,105,22)
    assert (proof['native_tests_per_JVM'],proof['JVM_profiles'],proof['compiler_rounding_difference_rows'])==(133,3,12)
    for item in proof['source_evidence']:
        verify_source(root, data, item['path'], item['sha256'])
    native = proof['native_controls']; raw = (data/native['path']).read_bytes()
    assert len(raw)==native['bytes'] and digest(raw)==native['sha256']
    controls = archive(raw, 16777216)
    matrix = json.loads(controls['matrix/observed.json'])
    assert matrix['status']=='passed133exact-native-package-tests-three-JVMs-and-strict17-javadoc'
    assert set(matrix['profiles'])=={'Windows21','Windows25','Linux21'}
    assert all(row=={'tests':133,'passed':133,'skipped':0} for row in matrix['profiles'].values())
    assert {row['label'] for row in matrix['runs']}=={'strict-Java17','strict-javadoc','compile-package-tests','Windows21','Windows25','Linux21'}
    assert all(row['exit_code']==0 for row in matrix['runs'])
    for path, expected in matrix['source_sha256'].items(): assert digest(controls['source/'+path])==expected
    candidate = controls['artifacts/shapeit-native-map.jar']
    assert digest(candidate)==matrix['candidate_sha256']==proof['native_candidate_sha256']
    with zipfile.ZipFile(io.BytesIO(candidate)) as jar:
        classes = [name for name in jar.namelist() if name.endswith('.class')]
        assert classes and all(name.startswith('org/jlinalg/genetics/ShapeitGeneticMap') for name in classes)
        assert all(int.from_bytes(jar.read(name)[6:8],'big')==61 for name in classes)
        for name in ['SHAPEIT4-MIT.txt','SHAPEIT5-MIT.txt']:
            assert jar.read('META-INF/licenses/'+name)==controls['source/src/main/resources/META-INF/licenses/'+name]
    original_raw = controls['artifacts/original-map-controls.zip']
    assert digest(original_raw)==proof['original_controls_sha256']=='977e798fc1b7b1bff2344d51be84d02f7d8ed25449479920db3c71d8ce54b9d6'
    original = archive(original_raw, 8388608)
    provenance = json.loads(original['provenance.json'])
    assert (provenance['original_observations'],provenance['complete_observations'],provenance['expected_source_failure_observations'])==(127,105,22)
    assert provenance['fused_vs_split_differing_result_rows']==12
    assert provenance['source_undefined_boundaries_are_not_successful_analysis'] and not provenance['public_phasing_adapter_available']
    observed = json.loads(original['capture/observed.json'])
    assert observed['status']=='passed-original-map-parsing-interpolation-and-checked-boundary-controls'
    assert digest(original['protocol/MapProtocol.cpp'])==observed['protocol_sha256']
    assert original['protocol/MapProtocol.cpp']==controls['source/tools/literature/shapeit-reference/MapProtocol.cpp']
    original_reference = original['source/original-reader-source-closure.zip']
    assert digest(original_reference)=='7ce0722fbe1499ed72d5a46d81c9945d1141b24a7abbec07e5e1d3ce799e8f02'
    reference = archive(original_reference, 4194304)
    for profile, units in observed['source_units'].items():
        for path, expected in units.items():
            name = 'source/4.2.2/'+path if profile=='4.2.2' else 'upstream/'+profile+'/'+('phase_common/' if profile=='5-c34d' else '')+path
            assert digest((original if profile=='4.2.2' else reference)[name])==expected
    new_source = json.loads(original['capture/4.2.2-selected-source.json'])
    assert new_source['commit']=='16865e593ad50689271ead985dc8f4296d2f491d'
    for item in new_source['files']:
        raw = original['source/4.2.2/'+item['path']]
        assert digest(raw)==item['sha256']
        assert hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()==item['git_blob_sha1']
    cases = {case['name']:case for case in observed['cases']}
    assert set(cases)=={'mixed','global-mean','flat','single-exact','zero-span-exact','random','default','coercions','hexadecimal','nan-payloads','negative-zero','all-before','right-boundary','empty-variants','empty-map','bad-columns','decreasing-bp','decreasing-cm','whitespace-line'}
    expected = set()
    for profile in ['4.1.3','4.2.2','5-c34d']:
        for arithmetic in ['fused','split','checked']:
            for name in cases:
                undefined = name in {'right-boundary','empty-variants','empty-map','whitespace-line'} or name=='all-before' and profile=='4.1.3'
                rejected = name in {'bad-columns','decreasing-bp','decreasing-cm'}
                if arithmetic!='checked' and (undefined or rejected): continue
                expected.add((profile,arithmetic,name))
    observations = observed['observations']
    actual = [(o['profile'],o['arithmetic'],o['case']) for o in observations]
    assert len(actual)==len(set(actual))==127 and set(actual)==expected
    complete = failed = differences = 0
    for item in observations:
        rows = original['capture/'+item['result']].decode().splitlines()
        name = item['case']; inputs = cases[name]['inputs']
        assert original['capture/'+inputs['map']].decode()==cases[name]['map']
        assert [int(line) for line in original['capture/'+inputs['positions']].decode().splitlines()]==cases[name]['positions']
        if item['complete']:
            complete += 1; assert item['exit_code']==0 and rows[-1].startswith('E\t')
            assert not item['source_undefined_boundary'] and not item['source_parser_rejected']
            count = sum(row.startswith('K\t' if cases[name]['mode']=='read' else 'V\t') for row in rows)
            assert rows[-1]=='E\t'+str(count)
            if cases[name]['mode']!='read': assert count==len(cases[name]['positions'])
            if item['arithmetic']=='fused':
                other = original['capture/'+item['result'].replace('-fused-','-split-')].decode().splitlines()
                assert len(other)==len(rows); differences += sum(a!=b for a,b in zip(rows,other))
        else:
            failed += 1; assert item['exit_code']!=0 and not any(row.startswith('E\t') for row in rows)
            assert item['source_undefined_boundary'] or item['source_parser_rejected']
    assert (complete,failed,differences)==(105,22,12)
    result = {'status':'passed','original_map_observations':127,'complete_original_results':105,'expected_original_failures':22,
              'native_tests_per_JVM':133,'JVM_profiles':3,'compiler_rounding_difference_rows':12,'public_phasing_adapter_available':False,
              'scope':'Original map parsing and finite numerical mapping only. Known undefined original vector reads are refused. No protected file/runtime phasing workflow or phase accuracy is certified.'}
    print(json.dumps(result,indent=2))
    return result


if __name__=='__main__': verify()
