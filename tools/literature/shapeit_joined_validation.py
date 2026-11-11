#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify retained raw-linkage evidence against current byte-pinned sources; no phasing."""
from pathlib import Path,PurePosixPath
import hashlib,io,json,re,zipfile

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'
SCIENCE={
    'src/main/java/org/jlinalg/cli/'+name+'.java' for name in ['ShapeitBcf','ShapeitRawIndex','ShapeitJoinedRecords','SimpleJson']
}|{'src/main/java/org/jlinalg/genetics/ShapeitCommonVariants.java'}|{
    'src/test/java/org/jlinalg/cli/'+name+'Test.java' for name in ['ShapeitBcf','ShapeitRawIndex','ShapeitJoinedRecords']
}|{'src/test/java/org/jlinalg/genetics/ShapeitCommonVariantsTest.java'}|{
    'src/test/resources/org/jlinalg/cli/shapeit-bcf/raw-oracles-3.zip',
    'src/test/resources/org/jlinalg/cli/shapeit-joined/original-controls.zip',
    'src/test/resources/org/jlinalg/genetics/shapeit-selection/original-controls.zip',
    'tools/literature/shapeit_joined_validation.py','tools/literature/test_shapeit_joined_validation.py',
}|{'tools/literature/shapeit-reference/'+name for name in ['JoinedWireProtocol.cpp','MakeJoinedWireDictionaryFixture.cpp','collect_joined_wire_evidence.py']}|{
    prefix+name for prefix in ['LICENSES/','src/main/resources/META-INF/licenses/'] for name in ['SHAPEIT4-MIT.txt','SHAPEIT5-MIT.txt','SHAPEIT5-XCF-MIT.txt']
}
digest=lambda raw:hashlib.sha256(raw).hexdigest()
def archive(raw,maximum=33554432,checksums=True):
    result={};total=0
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        for item in z.infolist():
            name=item.filename;path=PurePosixPath(name)
            assert name not in result and not item.is_dir() and not path.is_absolute() and '..' not in path.parts and '\\' not in name
            assert (item.external_attr>>16)&0o170000!=0o120000
            assert item.file_size<=maximum and len(result)<2048
            total+=item.file_size;assert total<=maximum
            result[name]=z.read(item)
    if checksums:
        rows=result['SHA256SUMS'].decode().splitlines();assert len(rows)==len(result)-1
        seen=set()
        for line in rows:
            expected,name=line.split('  ',1);assert name not in seen and name!='SHA256SUMS';seen.add(name);assert digest(result[name])==expected
        assert seen==set(result)-{'SHA256SUMS'}
    return result
def verify(root=ROOT,data=DATA):
    if not __debug__:raise RuntimeError('Validation requires Python assertions; do not use -O')
    record=json.loads((data/'shapeit-joined-validation.json').read_bytes())
    assert record['status']=='validated-native-SHAPEIT-original-join-raw-record-linkage'
    for key in ['public_phasing_adapter_available','runtime_admission_certified','binary_genotype_payloads_validated','phase_accuracy_certified']:assert record[key] is False
    assert record['source_sha256'].keys()==SCIENCE
    name=record['artifact']['path'];assert PurePosixPath(name).name==name=='shapeit-joined-native-controls.zip'
    raw=(data/name).read_bytes();assert digest(raw)==record['artifact']['sha256'] and len(raw)==record['artifact']['bytes']
    controls=archive(raw)
    for path,expected in record['source_sha256'].items():
        assert digest((root/path).read_bytes())==expected,path
        assert digest(controls['source/'+path])==expected,path
    matrix=json.loads(controls['matrix/observed.json'])
    assert matrix['status']=='passed-native-raw-linkage-matrix' and matrix['tests_per_JVM']==171 and matrix['JVM_profiles']==3 and matrix['skipped']==0 and matrix['heap']=='128m'
    assert record['native_tests_per_JVM']==171 and record['JVM_profiles']==3
    for path,expected in matrix['source_sha256'].items():assert expected==record['source_sha256'][path]
    native=controls['matrix/shapeit-native-joined.jar'];assert digest(native)==matrix['candidate_sha256']==record['native_candidate_sha256']
    contents=archive(native,4194304,False)
    classes=[name for name in contents if name.endswith('.class')];assert classes and all(int.from_bytes(contents[name][6:8],'big')==61 for name in classes)
    for name in ['ShapeitBcf','ShapeitRawIndex','ShapeitJoinedRecords']:assert 'org/jlinalg/cli/'+name+'.class' in classes
    assert 'org/jlinalg/genetics/ShapeitCommonVariants.class' in classes
    for name in ['SHAPEIT4-MIT.txt','SHAPEIT5-MIT.txt','SHAPEIT5-XCF-MIT.txt']:assert contents['META-INF/licenses/'+name]==controls['source/LICENSES/'+name]
    for name,expected in matrix['dependency_sha256'].items():assert digest(controls['matrix/lib/'+name])==expected
    notices=record['dependency_source_notices']
    assert notices['repository']=='https://github.com/samtools/htsjdk' and notices['tag']=='5.0.0' and notices['commit']=='fb578787cd8aa2f5b3900b0f3150993d5841e013'
    for item in notices['files']:assert digest(controls[item['member']])==item['sha256']
    source=controls['matrix/dependency-sources/htsjdk-5.0.0-source.zip']
    with zipfile.ZipFile(io.BytesIO(source)) as sources:
        assert sources.comment.decode()==notices['commit']
        assert sum(info.file_size for info in sources.infolist())<=33554432
        assert b'under LGPL' in sources.read('README.md') and b'Permission is hereby granted' in sources.read('src/main/java/htsjdk/samtools/util/BlockCompressedInputStream.java')
        assert all(name in sources.namelist() for name in ['build.gradle','settings.gradle','gradlew','gradle/wrapper/gradle-wrapper.jar'])
    assert b'GNU LESSER GENERAL PUBLIC LICENSE' in controls['matrix/dependency-sources/LGPL-2.1.txt']
    assert b'Apache License' in controls['matrix/dependency-sources/Apache-2.0.txt']
    profiles=['windows-java21','windows-java25','linux-java21']
    versions=json.loads(controls['matrix/runtime-versions.json'])
    for name,major in zip(profiles,[21,25,21]):
        version=versions[name];assert version['major']==major and version['exit_code']==0
        assert re.search(r'\b(?:openjdk|java) '+str(major)+r'(?:\.|\s)',version['output'])
    for name in ['javac-main','javac-tests','javadoc',*profiles]:
        run=next(run for run in matrix['runs'] if run['name']==name);assert run['exit_code']==0
        if name in profiles:
            assert '-Xmx128m' in run['command']
            text=controls['matrix/'+name+'.stdout'].decode()
            assert re.search(r'\[\s*171 tests successful\s*\]',text) and re.search(r'\[\s*0 tests skipped\s*\]',text) and re.search(r'\[\s*0 tests failed\s*\]',text)
    fixture=controls['source/src/test/resources/org/jlinalg/cli/shapeit-joined/original-controls.zip']
    assert digest(fixture)==record['original_fixture_sha256']=='fc63451afeb22f5c0c45d8a46af7634687ea85f6c25eb431d73e609cc6baad79'
    original=archive(fixture,8388608);observed=json.loads(original['capture/observed.json']);cases=json.loads(original['cases.json'])
    assert observed['source_commit']=='c34d4db3e99a2f7e23deb727671ae260901a5886'
    assert digest(original['protocol/JoinedWireProtocol.cpp'])==observed['source_protocol_sha256']==record['source_sha256']['tools/literature/shapeit-reference/JoinedWireProtocol.cpp']
    assert digest(original['protocol/MakeJoinedWireDictionaryFixture.cpp'])==observed['dictionary_generator_sha256']==record['source_sha256']['tools/literature/shapeit-reference/MakeJoinedWireDictionaryFixture.cpp']
    assert original['protocol/collect_joined_wire_evidence.py']==controls['source/tools/literature/shapeit-reference/collect_joined_wire_evidence.py']
    reference=original['source/original-reader-source-closure.zip'];assert digest(reference)==observed['original_source_closure_sha256']=='7ce0722fbe1499ed72d5a46d81c9945d1141b24a7abbec07e5e1d3ce799e8f02'
    upstream=archive(reference,8388608);assert len(observed['source_sha256'])>200
    for path,expected in observed['source_sha256'].items():assert digest(upstream['upstream/5-c34d/'+path])==expected
    assert len(cases)==len({c['name'] for c in cases})==29
    complete=failures=events=0
    for case in cases:
        raw=original[case['protocol_member']];assert digest(raw)==case['protocol_sha256'];lines=raw.decode().splitlines()
        assert lines[0]=='V\tOMICS-SHAPEIT-JOIN-WIRE-2\t'+str(len(case['roles'])) and lines[1].startswith('U\t')
        for binding in case['bindings']:assert digest(original[binding['member']])==binding['sha256'] and len(original[binding['member']])==binding['bytes']
        if case['complete']:
            complete+=1;assert case['exit_code']==0 and lines[-1]=='Z'
            count=sum(line.startswith('J\t') for line in lines);assert int(next(line for line in lines if line.startswith('E\t')).split('\t')[1])==count;events+=count
        else:
            failures+=1;assert case['exit_code']==1 and 'Z' not in lines and not any(line.startswith('E\t') for line in lines)
            assert 'AN field is needed' in original['capture/'+case['name']+'.stdout'].decode()
    assert (complete,failures,events)==(27,2,181)
    assert record['original_complete_streams']==complete and record['original_expected_failures']==failures and record['original_joined_events']==events
    result=dict(status='passed',original_complete_streams=complete,expected_original_failures=failures,original_joined_events=events,native_tests_per_JVM=171,JVM_profiles=3,public_phasing_adapter_available=False,
                scope='Complete raw-record linkage to bound original join captures only. This verifier checks retained evidence and current source identities; it does not launch/admit a phaser, decode XCF genotype bytes, apply pedigree/haploid constraints, or certify phase accuracy.')
    print(json.dumps(result,indent=2));return result
if __name__=='__main__':verify()
