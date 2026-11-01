#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify retained managed common-stage evidence and current source; launches no upstream analysis."""
from pathlib import Path, PurePosixPath
import csv
import io
import json
import re
import zipfile
from shapeit_joined_validation import archive, digest
from shapeit_common_validation import SCIENCE as COMMON
from catalog_source_integrity import verify_source

ROOT=Path(__file__).resolve().parents[2];DATA=ROOT/'docs/literature/omics-2023-2026'
SCIENCE=COMMON|{'src/main/java/org/jlinalg/cli/'+name+'.java' for name in ['ShapeitCommonPhasing','ShapeitRuntime','ShapeitProcess','ShapeitJson','ShapeitRecipe','ShapeitToolsCli']}|{
    'src/main/java/org/jlinalg/cli/JLinAlgCli.java','src/test/java/org/jlinalg/cli/ShapeitWorkflowTest.java',
    'src/test/java/org/jlinalg/cli/ShapeitManagedReplayTest.java','src/test/resources/org/jlinalg/cli/shapeit-managed/original-controls.zip',
    'src/main/resources/org/jlinalg/omics/shapeit-runtime-lock.json','src/main/resources/org/jlinalg/omics/shapeit-runtime-setup/install_shapeit.py',
    'tools/literature/install_shapeit.py','tools/literature/shapeit-runtime-lock.json','tools/literature/test_install_shapeit.py',
    'tools/literature/shapeit_managed_validation.py','tools/literature/test_shapeit_managed_validation.py',
    'tools/literature/catalog_source_integrity.py','tools/literature/shapeit-reference/collect_managed_common_evidence.py',
    'docs/literature/omics-2023-2026/shapeit-common-dispatch-shared-source-compatibility.json','LICENSE'}
POSITIVE={'cohort-default','cohort-map','reference-default','scaffold-default','reference-scaffold-map','cohort-filters','cohort-duplicates','reference-duplicates'}
NEGATIVE={'cohort-wire-departures':'unadmitted missingness/ploidy encoding','reference-missing':'REFERENCE_LITERAL_MISSING','reference-unphased':'REFERENCE_UNPHASED','scaffold-unused':'scaffold applies zero constraints'}


def verify(root=ROOT,data=DATA,emit=True):
    if not __debug__:raise RuntimeError('Run validation with assertions enabled; do not use Python -O')
    record=json.loads((data/'shapeit-managed-common-validation.json').read_bytes())
    assert record['status']=='passed-scoped-original-SHAPEIT5-common-workflow-validation'
    assert record['source_sha256'].keys()==SCIENCE
    assert record['source_current_common_command_available'] is True and record['whole_paper_reproduced'] is False
    for field in ['download_source_build_installer_available','binary_genotype_payloads_validated','historical_editions_reproduced','pedigree_haploid_rare_ligation_validated','phase_accuracy_certified','released_or_deployed']:assert record[field] is False
    assert record['installation_mode']=='existing-permitted-exact-member-copy-only'
    name=record['artifact']['path'];assert PurePosixPath(name).name==name=='shapeit-managed-common-controls.zip'
    raw=(data/name).read_bytes();assert len(raw)==record['artifact']['bytes'] and digest(raw)==record['artifact']['sha256'];bundle=archive(raw,67108864)
    for path,expected in record['source_sha256'].items():
        assert digest(bundle['source/'+path])==expected
        if path=='src/main/java/org/jlinalg/cli/JLinAlgCli.java':verify_source(root,data,path,expected)
        else:assert digest((root/path).read_bytes())==expected,path
    lock_raw=bundle['source/tools/literature/shapeit-runtime-lock.json'];lock=json.loads(lock_raw)
    assert lock_raw==bundle['source/src/main/resources/org/jlinalg/omics/shapeit-runtime-lock.json']
    assert bundle['source/tools/literature/install_shapeit.py']==bundle['source/src/main/resources/org/jlinalg/omics/shapeit-runtime-setup/install_shapeit.py']
    assert lock['source_commit']=='c34d4db3e99a2f7e23deb727671ae260901a5886' and len(lock['files'])==32
    assert lock['files']['bin/phase_common']['sha256']=='74d6f951f6103e709ee3e754397a86b6b2cb4e0ab0bf543c1378cb4b7d894689'
    assert lock['files']['bin/joined-wire']['sha256']=='032cf41c4f1b54536032b1e57a5115364a1a230c128c0a8594d56981953b2898'
    marker_raw=bundle['workflow/installed-runtime-marker.json'];marker=json.loads(marker_raw)
    assert marker['status']=='installed-exact-runtime-only' and marker['scientific_analysis_admitted'] is False
    assert marker['lock_sha256']==digest(lock_raw) and marker['installer_sha256']==record['source_sha256']['tools/literature/install_shapeit.py'] and marker['files']==lock['files']
    fixture_raw=bundle['source/src/test/resources/org/jlinalg/cli/shapeit-managed/original-controls.zip']
    assert digest(fixture_raw)=='b04cd57720d67b4f7f5a6a242bca88326b68d692e1e262660c496d3f7bec97d4'
    fixture=archive(fixture_raw,8388608);replays={c['name']:c for c in json.loads(fixture['cases.json'])};assert len(replays)==9
    assert fixture['protocol/collect_managed_common_evidence.py']==bundle['source/tools/literature/shapeit-reference/collect_managed_common_evidence.py']
    def obj(path):return json.loads(bundle[path])
    def table(path):return list(csv.DictReader(io.StringIO(bundle[path].decode()),delimiter='\t'))
    def public(directory,code):
        prefix=directory+'/';session=obj(prefix+'session.json');manifest_raw=bundle[prefix+'manifest.json'];manifest=json.loads(manifest_raw)
        assert session['exit_code']==code and session['scientific_result_admitted'] is (code==0) and session['phase_accuracy_certified'] is False
        expected=set(manifest['files'])|{'manifest.json'}|({'COMPLETED.json'} if code==0 else set())
        actual={n[len(prefix):] for n in bundle if n.startswith(prefix)};assert actual==expected,(directory,actual^expected)
        for relative,identity in manifest['files'].items():
            assert not PurePosixPath(relative).is_absolute() and '..' not in PurePosixPath(relative).parts and '\\' not in relative
            raw=bundle[prefix+relative];assert len(raw)==identity['bytes'] and digest(raw)==identity['sha256'],relative
        assert b'Required action' in bundle[prefix+'WARNINGS.md']
        if code==0:
            complete=obj(prefix+'COMPLETED.json');assert complete['manifest_sha256']==digest(manifest_raw) and complete['scientific_result_admitted'] is True and complete['phase_accuracy_certified'] is False
        else:assert not any(n.startswith(prefix+'analysis/fit-process') or n==prefix+'analysis/phased.bcf' for n in bundle)
        return session
    rows=targets=references=scaffolds=source_an=0
    def fitting(directory,replay_name):
        nonlocal rows,targets,references,scaffolds,source_an
        public(directory,0);prefix=directory+'/analysis/';native=obj(prefix+'analysis.json');a=native['admission'];r=replays[replay_name]
        assert native['status']=='completed-audited-common-stage' and native['scientific_result_admitted'] is True and native['phase_accuracy_certified'] is False
        assert native['profile']==lock['profile'] and native['runtime']['files']==lock['files'] and native['runtime']['marker']['sha256']==digest(marker_raw)
        assert obj(directory+'/COMPLETED.json')['manifest_sha256']==r['original_public_manifest_sha256']
        assert bundle[prefix+'phased.bcf']==fixture[r['output_member']]
        assert bundle[prefix+'output-audit/target-allele-counts.tsv']==fixture[r['target_counts_member']]
        assert bundle[prefix+'input-count-review.tsv']==fixture[r['input_counts_member']]
        for field in ['selectedRows','selectedTargetCells','selectedReferenceCells','appliedScaffoldConstraints','declaredCountMismatchRows','partiallyMissingTargetCalls']:assert a[field]==r['admission'][field]
        for binding in r['bindings']:
            filename=binding['role'].lower()+'.bcf'+('.csi' if binding['kind']=='I' else '')
            assert bundle[prefix+'staged/'+filename]==fixture[binding['member']]
        assert digest(bundle[prefix+'original.join.tsv'])==r['protocol_sha256']
        ns=a['selectedTargetCells']//a['selectedRows'];nr=a['selectedReferenceCells']//a['selectedRows'];counts=table(prefix+'output-audit/target-allele-counts.tsv');assert len(counts)==a['selectedRows']
        for index,row in enumerate(counts):
            assert int(row['selected_ordinal'])==index and int(row['target_AN'])==2*ns and int(row['source_AN'])==2*(ns+nr)
            assert 0<=int(row['target_AC'])<=2*ns and row['target_AC']==row['source_AC']
        inputs=table(prefix+'input-count-review.tsv');assert sum(row['counts_match']=='false' for row in inputs)==a['declaredCountMismatchRows']
        for label in ['join-process','fit-process']:
            for suffix in ['', '.loader-check']:
                process=obj(prefix+label+suffix+'/process.json');assert process['status']=='completed-process-only' and process['exit_code']==0 and process['owned_live_after_cleanup']==[]
        if nr:assert b'SOURCE AN INCLUDES REFERENCE HAPLOTYPES' in bundle[directory+'/WARNINGS.md']
        rows+=a['selectedRows'];targets+=a['selectedTargetCells'];references+=2*a['selectedReferenceCells'];scaffolds+=a['appliedScaffoldConstraints'];source_an+=native['source_AN_includes_reference_rows']
    observations=obj('workflow/public-cli/observed.json');assert observations['status']=='passed-exact-packaged-public-common-cli-controls'
    assert {c['name'] for c in observations['commands']}==POSITIVE|NEGATIVE.keys()
    for case in observations['commands']:
        name=case['name'];directory='workflow/public-cli/'+name
        if name in POSITIVE:fitting(directory,'public-'+name)
        else:assert NEGATIVE[name] in public(directory,2)['error']
    policy=obj('workflow/policies/observed.json');assert policy['status']=='passed-packaged-common-count-and-missing-policies'
    assert [c['exit_code'] for c in policy['outcomes']]==[2,0,2]
    assert 'Declared AC/AN differs' in public('workflow/policies/count-strict',2)['error']
    fitting('workflow/policies/count-declared','policy-count-declared')
    assert b'DECLARED COUNT FILTERING' in bundle['workflow/policies/count-declared/WARNINGS.md']
    assert 'Target missing GT violates REQUIRE_COMPLETE' in public('workflow/policies/missing-complete',2)['error']
    transport=obj('workflow/transport/observed.json');assert transport['status']=='passed-packaged-transport-skip-and-invalid-input-controls'
    assert [(c['name'],c['actual_exit']) for c in transport['controls']]==[('bare-bcf',3),('plain-vcf',3),('gzip-vcf',3),('binary-xcf',3),('malformed-gzip',2)]
    for case in transport['controls']:
        directory='workflow/transport/'+case['name'];session=public(directory,case['actual_exit']);assert not any(n.startswith(directory+'/analysis/join-process') for n in bundle)
        if case['name']=='malformed-gzip':assert 'truncated or invalid' in session['error']
    assert (rows,targets,references,scaffolds,source_an)==(1131,41470,30160,573,377)
    assert observations['candidate_sha256']==policy['candidate_sha256']==transport['candidate_sha256']==record['packaged_candidate_sha256']=='480e6257e5ad500d3e3f33df661e21c3bada6c68e8eacb44bafd6b5ab82a75e2'
    process=obj('workflow/process/observed.json');assert process['tests']==13 and process['unrelated_process_preserved'] is True
    assert {c['name'] for c in process['controls']}=={'success','exit','orphan','wall','cancel','input-change','symlink','files','workspace','process-count','hard-as','hard-file','hard-cpu'}
    for case in process['controls']:
        ledger=obj('workflow/process/'+case['name']+'/process.json');assert ledger['owned_live_after_cleanup']==[] and ledger['status']==('completed-process-only' if case['name']=='success' else 'FAIL2-retained-no-result')
    shutdown=obj('capture/shutdown-observed.json');assert shutdown['status']=='passed-owned-JVM-shutdown-cleanup' and shutdown['child_exit_code']!=0 and shutdown['confirmed_live_launcher_pid']>0
    assert shutdown['shutdown']['status']=='shutdown-owned-processes-terminal' and shutdown['shutdown']['owned_live_after_cleanup']==[]
    tamper=obj('workflow/runtime-tamper/observed.json');assert tamper['tests']==9 and len(tamper['controls'])==9 and tamper['original_runtime_unchanged'] is True
    assert all(c['status']=='passed-no-runtime-or-analysis-admitted' and c['error'] for c in tamper['controls'])
    assert obj('workflow/retained-negative-fixture-links.json')==[dict(group='process',path='symlink/linked',literal_target='/tmp',materialized=False)]
    matrix=obj('matrix/observed.json');assert matrix['status']=='passed-native-owned-common-matrix' and matrix['tests_per_JVM']==380 and matrix['JVM_profiles']==3 and matrix['skipped']==0 and matrix['heap']=='128m'
    for path,expected in matrix['source_sha256'].items():assert expected==record['source_sha256'][path]
    native=bundle['matrix/shapeit-native-owned-common.jar'];assert digest(native)==matrix['candidate_sha256']==record['native_candidate_sha256'];classes=archive(native,8388608,False)
    for name,raw in classes.items():
        if name.endswith('.class'):assert int.from_bytes(raw[6:8],'big')==61
    for name in ['ShapeitCommonPhasing','ShapeitRuntime','ShapeitRecipe','ShapeitToolsCli','ShapeitProcess']:assert 'org/jlinalg/cli/'+name+'.class' in classes
    for name in ['SHAPEIT4-MIT.txt','SHAPEIT5-MIT.txt','SHAPEIT5-XCF-MIT.txt']:assert classes['META-INF/licenses/'+name]==bundle['source/LICENSES/'+name]
    packaged=bundle['packaged/shapeit-tested-classes.jar'];assert digest(packaged)==record['packaged_classes_sha256'];packaged_classes=archive(packaged,8388608,False)
    prior=archive(bundle['capture/prior-process-candidate.jar'],8388608,False)
    for name,raw in prior.items():
        if name.endswith('.class') and name.startswith(('org/jlinalg/cli/ShapeitProcess','org/jlinalg/cli/ShapeitRuntime','org/jlinalg/cli/ShapeitRecipe','org/jlinalg/cli/ShapeitJson','org/jlinalg/cli/ShapeitToolsCli')):assert packaged_classes[name]==raw
    for name,expected in matrix['dependency_sha256'].items():assert digest(bundle['matrix/lib/'+name])==expected
    versions=obj('matrix/runtime-versions.json')
    for profile,major in [('windows-java21',21),('windows-java25',25),('linux-java21',21)]:
        assert versions[profile]['major']==major and versions[profile]['exit_code']==0 and re.search(r'\b(?:openjdk|java) '+str(major)+r'(?:\.|\s)',versions[profile]['output'])
        run=next(c for c in matrix['runs'] if c['name']==profile);assert run['exit_code']==0 and '-Xmx128m' in run['command']
        text=bundle['matrix/'+profile+'.stdout'].decode();assert re.search(r'\[\s*380 tests successful\s*\]',text) and re.search(r'\[\s*0 tests skipped\s*\]',text) and re.search(r'\[\s*0 tests failed\s*\]',text)
    for name in ['javac-main','javac-tests','javadoc']:assert next(c for c in matrix['runs'] if c['name']==name)['exit_code']==0
    notices=record['dependency_source_notices'];assert notices['commit']=='fb578787cd8aa2f5b3900b0f3150993d5841e013' and notices['tag']=='5.0.0'
    for item in notices['files']:assert digest(bundle[item['member']])==item['sha256']
    with zipfile.ZipFile(io.BytesIO(bundle['matrix/dependency-sources/htsjdk-5.0.0-source.zip'])) as sources:assert sources.comment.decode()==notices['commit'] and b'under LGPL' in sources.read('README.md')
    for key,value in dict(packaged_protocols=20,actual_successful_fits=9,input_policy_refusals=6,unsupported_transport_skips=4,invalid_compression_failures=1,selected_rows=1131,target_cells=41470,reference_haplotype_cells=30160,applied_scaffold_constraints=573,source_AN_includes_reference_rows=377,native_tests_per_JVM=380,JVM_profiles=3,owned_process_controls=14,runtime_tamper_controls=9).items():assert record[key]==value,key
    result=dict(status='passed',actual_successful_fits=9,packaged_protocols=20,native_tests_per_JVM=380,JVM_profiles=3,portable_replays=9,applied_scaffold_constraints=573,source_AN_includes_reference_rows=377,source_current_common_command_available=True,whole_paper_reproduced=False,scope='Retained actual source-c34d managed common-stage controls, portable complete data/output replay and byte-pinned current source only. No live phasing is launched; exact-build adoption, historical editions and unimplemented stages remain explicit. Phase accuracy, release and whole-paper equivalence are not certified.')
    if emit:print(json.dumps(result,indent=2))
    return result


if __name__=='__main__':verify()
