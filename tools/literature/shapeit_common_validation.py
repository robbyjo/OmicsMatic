#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify current native common-data contracts and retained actual original fitting controls."""
from pathlib import Path,PurePosixPath
import io,json,re,zipfile
from shapeit_joined_validation import archive,digest
ROOT=Path(__file__).resolve().parents[2];DATA=ROOT/'docs/literature/omics-2023-2026'
SCIENCE={
    'src/main/java/org/jlinalg/cli/'+name+'.java' for name in ['ShapeitBcf','ShapeitRawIndex','ShapeitJoinedRecords','ShapeitCommonCalls','ShapeitCommonData','ShapeitCommonOutputs','SimpleJson']
}|{'src/main/java/org/jlinalg/genetics/'+name+'.java' for name in ['ShapeitCommonVariants','ShapeitGeneticMap','ShapeitGenotypes']}|{
    'src/test/java/org/jlinalg/cli/'+name+'Test.java' for name in ['ShapeitBcf','ShapeitRawIndex','ShapeitJoinedRecords','ShapeitCommonCalls','ShapeitCommonData']
}|{'src/test/java/org/jlinalg/genetics/'+name+'Test.java' for name in ['ShapeitCommonVariants','ShapeitGeneticMap','ShapeitGenotypes']}|{
    'src/test/resources/org/jlinalg/cli/shapeit-bcf/raw-oracles-3.zip','src/test/resources/org/jlinalg/cli/shapeit-joined/original-controls.zip',
    'src/test/resources/org/jlinalg/cli/shapeit-common/original-controls.zip','src/test/resources/org/jlinalg/genetics/shapeit-selection/original-controls.zip',
    'src/test/resources/org/jlinalg/genetics/shapeit-map/original-controls.zip','src/test/resources/org/jlinalg/genetics/shapeit-reference/initial-reader.zip',
    'tools/literature/shapeit_common_validation.py','tools/literature/test_shapeit_common_validation.py','tools/literature/shapeit_joined_validation.py',
}|{'tools/literature/shapeit-reference/'+name for name in ['MakeCommonFitFixture.cpp','CommonDataProtocol.cpp','BcfWireProtocol.cpp','JoinedWireProtocol.cpp','collect_common_fit_evidence.py']}|{
    prefix+name for prefix in ['LICENSES/','src/main/resources/META-INF/licenses/'] for name in ['SHAPEIT4-MIT.txt','SHAPEIT5-MIT.txt','SHAPEIT5-XCF-MIT.txt']
}
def verify(root=ROOT,data=DATA):
    if not __debug__:raise RuntimeError('Validation requires Python assertions; do not use -O')
    record=json.loads((data/'shapeit-common-validation.json').read_bytes())
    assert record['status']=='validated-native-SHAPEIT-common-data-and-output-contracts'
    for key in ['public_phasing_adapter_available','managed_runtime_admission_certified','binary_genotype_payloads_validated','haploid_pedigree_contracts_validated','phase_accuracy_certified']:assert record[key] is False
    assert record['source_sha256'].keys()==SCIENCE
    name=record['artifact']['path'];assert PurePosixPath(name).name==name=='shapeit-common-native-controls.zip'
    raw=(data/name).read_bytes();assert digest(raw)==record['artifact']['sha256'] and len(raw)==record['artifact']['bytes'];controls=archive(raw,50331648)
    for path,expected in record['source_sha256'].items():assert digest((root/path).read_bytes())==digest(controls['source/'+path])==expected,path
    matrix=json.loads(controls['matrix/observed.json']);assert matrix['status']=='passed-native-common-data-matrix' and matrix['tests_per_JVM']==349 and matrix['JVM_profiles']==3 and matrix['heap']=='128m' and matrix['skipped']==0
    assert record['native_tests_per_JVM']==349 and record['JVM_profiles']==3
    for path,expected in matrix['source_sha256'].items():assert expected==record['source_sha256'][path]
    native=controls['matrix/shapeit-native-common-data.jar'];assert digest(native)==matrix['candidate_sha256']==record['native_candidate_sha256'];classes=archive(native,8388608,False)
    assert all(int.from_bytes(raw[6:8],'big')==61 for name,raw in classes.items() if name.endswith('.class'))
    for name in ['ShapeitCommonCalls','ShapeitCommonData','ShapeitCommonOutputs']:assert 'org/jlinalg/cli/'+name+'.class' in classes
    for name in ['SHAPEIT4-MIT.txt','SHAPEIT5-MIT.txt','SHAPEIT5-XCF-MIT.txt']:assert classes['META-INF/licenses/'+name]==controls['source/LICENSES/'+name]
    for name,expected in matrix['dependency_sha256'].items():assert digest(controls['matrix/lib/'+name])==expected
    notices=record['dependency_source_notices'];assert notices['commit']=='fb578787cd8aa2f5b3900b0f3150993d5841e013' and notices['tag']=='5.0.0'
    for item in notices['files']:assert digest(controls[item['member']])==item['sha256']
    with zipfile.ZipFile(io.BytesIO(controls['matrix/dependency-sources/htsjdk-5.0.0-source.zip'])) as sources:
        assert sources.comment.decode()==notices['commit'] and b'under LGPL' in sources.read('README.md')
    versions=json.loads(controls['matrix/runtime-versions.json'])
    for profile,major in [('windows-java21',21),('windows-java25',25),('linux-java21',21)]:
        assert versions[profile]['major']==major and versions[profile]['exit_code']==0
        assert re.search(r'\b(?:openjdk|java) '+str(major)+r'(?:\.|\s)',versions[profile]['output'])
        run=next(r for r in matrix['runs'] if r['name']==profile);assert run['exit_code']==0 and '-Xmx128m' in run['command']
        text=controls['matrix/'+profile+'.stdout'].decode();assert re.search(r'\[\s*349 tests successful\s*\]',text) and re.search(r'\[\s*0 tests skipped\s*\]',text) and re.search(r'\[\s*0 tests failed\s*\]',text)
    for name in ['javac-main','javac-tests','javadoc']:assert next(r for r in matrix['runs'] if r['name']==name)['exit_code']==0
    fixture=controls['source/src/test/resources/org/jlinalg/cli/shapeit-common/original-controls.zip'];assert digest(fixture)==record['original_fixture_sha256']=='81523e3096bf076dea54138f56db752fb51b463e4b9810ee47ba8756fa35bff4'
    original=archive(fixture,16777216);observed=json.loads(original['capture/observed.json']);cases=json.loads(original['cases.json'])
    assert observed['source_commit']=='c34d4db3e99a2f7e23deb727671ae260901a5886' and observed['program_sha256']=='74d6f951f6103e709ee3e754397a86b6b2cb4e0ab0bf543c1378cb4b7d894689'
    reference=original['source/original-reader-source-closure.zip'];assert digest(reference)=='7ce0722fbe1499ed72d5a46d81c9945d1141b24a7abbec07e5e1d3ce799e8f02';upstream=archive(reference,8388608)
    assert len(observed['source_sha256'])>200
    for path,expected in observed['source_sha256'].items():assert digest(upstream['upstream/5-c34d/'+path])==expected
    for name,expected in observed['protocol_sha256'].items():assert digest(original['protocol/'+name])==expected==record['source_sha256']['tools/literature/shapeit-reference/'+name]
    assert original['protocol/collect_common_fit_evidence.py']==controls['source/tools/literature/shapeit-reference/collect_common_fit_evidence.py']
    assert len(cases)==len({c['name'] for c in cases})==12
    complete=failed=rows=targets=ref_haplotypes=0
    for case in cases:
        assert digest(original[case['join_member']])==case['join_sha256']
        for binding in case['bindings']:assert digest(original[binding['member']])==binding['sha256']
        if case['complete']:
            complete+=1;assert case['fit_exit_code']==case['initial_exit_code']==0 and digest(original[case['output_member']])==case['output_sha256']
            lines=[line.split('\t') for line in original[case['initial_member']].decode().splitlines()];assert lines[0]==['V','OMICS-SHAPEIT-COMMON-DATA-1']
            h=next(f for f in lines if f[0]=='H');ns,nr,n=map(int,h[1:]);assert lines[-1]==['E',str(n),str(n*ns),str(n*2*nr)]
            assert sum(f[0]=='G' for f in lines)==n*ns and sum(f[0]=='A' for f in lines)==n*2*nr
            assert all(f[3] in ['\0','\1'] for f in lines if f[0]=='A') # The actual unsigned-char getter is retained as a raw 0/1 byte.
            rows+=n;targets+=n*ns;ref_haplotypes+=n*2*nr
        else:
            failed+=1;assert case['fit_exit_code']==case['initial_exit_code']==1 and case['expected_source_failure'] in ['missing-reference','unphased-reference']
            assert 'genotype(s) in reference panel' in original['capture/fit-'+case['name']+'.stdout'].decode()
    assert (complete,failed,rows,targets,ref_haplotypes)==(10,2,1257,47770,30160)
    observations=json.loads(original['capture/output-observations.json']);assert observations['status']=='passed-complete-original-common-fit-output-checks'
    constraints=mismatched_AN=0
    for case in observations['cases']:
        assert case['scaffold_order_swaps']==0 and case['scaffold_order_matches']==case['scaffold_constraints'];constraints+=case['scaffold_constraints']
        assert case['output_declared_AN']==[2*(case['target']+case['reference'])] and case['physical_target_allele_slots']==[2*case['target']]
        if case['reference']:mismatched_AN+=case['variants']
    assert constraints==573 and mismatched_AN==377
    assert record['original_complete_fits']==10 and record['expected_original_failures']==2 and record['selected_rows']==1257 and record['target_cells']==47770 and record['reference_haplotype_cells']==30160 and record['applied_scaffold_constraints']==573 and record['source_AN_includes_reference_rows']==377
    result=dict(status='passed',original_complete_fits=complete,expected_original_failures=failed,selected_rows=rows,target_cells=targets,reference_haplotype_cells=ref_haplotypes,applied_scaffold_constraints=constraints,source_AN_includes_reference_rows=mismatched_AN,native_tests_per_JVM=349,JVM_profiles=3,public_phasing_adapter_available=False,
                scope='Current native ordinary-GT data preparation, map/selector integration and complete fitting-output contracts on retained original controls only. This verifier checks evidence and byte-pinned source; managed runtime/process admission, binary, pedigree/haploid, rare/ligation/posterior contracts and phase accuracy remain unvalidated.')
    print(json.dumps(result,indent=2));return result
if __name__=='__main__':verify()
