# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check a frozen development checkpoint; never execute Sniffles or grant SV credit."""
import argparse, hashlib, json, zipfile
from pathlib import Path, PurePosixPath
from setup_deseq2_reference import ROOT, pin

HOME=ROOT/'docs/literature/sniffles-execution-development'
BASE='build/literature-review/sniffles-runtime/'


def byte_pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def safe(name):
    path=PurePosixPath(name)
    assert name and not path.is_absolute() and '..' not in path.parts and '\\' not in name and ':' not in name


def verify(archive_only=False,home=HOME):
    proof=json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['status']=='PASS_SNIFFLES_EXECUTION_DEVELOPMENT_WITH_DECLARED_LIMITATIONS_ONLY'
    assert not any(proof[k] for k in ['implementation_credit','SV_calling_adapter_available','scientific_calling_workflow_validated','whole_paper_reproduced','whole_goal_complete'])
    assert pin(home/'controls.zip')==proof['archive']
    assert all(pin(home/name)==value for name,value in proof['artifacts'].items())
    if not archive_only:
        assert all(pin(ROOT/name)==value for name,value in proof['source_pins'].items())
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    with zipfile.ZipFile(home/'controls.zip') as archive:
        assert archive.testzip() is None and len(archive.namelist())==len(set(archive.namelist()))
        encoded=archive.read('files.json');assert byte_pin(encoded)==proof['file_index'];index=json.loads(encoded);files={}
        assert len(index)==proof['logical_files']
        for name,value in index.items():
            safe(name);raw=archive.read('blobs/'+value['sha256']);assert byte_pin(raw)==value;files[name]=raw
        assert set(archive.namelist())=={'files.json'}|{'blobs/'+value['sha256'] for value in index.values()}
    def document(name):return json.loads(files[name])
    for name,value in proof['source_pins'].items():assert byte_pin(files['current-source/'+name])==value
    for build,artifact,native in proof['compiler_cases']:
        built=document(BASE+build+'/COMPLETED.json')
        assert built['status']=='PASS_ISOLATED_JAVA17_SNIFFLES_EXECUTION_BUILD_ONLY' and not built['calling_adapter_validated']
        assert byte_pin(files[built['artifact']['path']])==proof['artifacts'][artifact]
        assert all(byte_pin(files[BASE+build+'/source-snapshot/'+name])==value for name,value in built['source_pins'].items())
        with zipfile.ZipFile(home/artifact) as jar,zipfile.ZipFile(ROOT/'docs/literature/sniffles-vcf-development'/native) as old:
            assert byte_pin((ROOT/'docs/literature/sniffles-vcf-development'/native).read_bytes())=={k:built['native_reference'][k] for k in ['bytes','sha256']}
            for name in old.namelist():
                if name.endswith('.class'):assert jar.read(name)==old.read(name)
            for name in ['profiles.json','bootstrap.py','worker.py']:
                path='src/main/resources/org/jlinalg/omics/sniffles-execution/'+name
                assert jar.read('org/jlinalg/omics/sniffles-execution/'+name)==files['current-source/'+path]
    profile_raw=files['current-source/src/main/resources/org/jlinalg/omics/sniffles-execution/profiles.json']
    profile=json.loads(profile_raw);assert len(profile['editions'])==5 and len(profile['private_site_members'])==573
    assert {tag:row['commit'] for tag,row in profile['editions'].items()}==proof['original_editions']
    binding_path=BASE+'runtime-binding-3/';binding=document(binding_path+'COMPLETED.json')
    assert binding['status']=='PASS_BOUND_ORIGINAL_SNIFFLES_RUNTIME_ONLY' and not binding['analysis_admitted']
    assert not binding['analysis_installed_software'] and not binding['source_namespace_patched'] and not binding['startup_hooks_executed']
    assert binding['python_hash_seed_effective']=='0' and not binding['owned_live_after_cleanup']
    assert set(binding['versions'])=={'1.0.7','2.0.2','2.0.7','2.3.2','2.4'} and len(binding['binding_files'])==400
    for name,value in binding['binding_files'].items():assert byte_pin(files[binding_path+name])==value
    assert files[binding_path+'profile.original.json']==profile_raw
    assert files[binding_path+'bootstrap.py']==files['current-source/src/main/resources/org/jlinalg/omics/sniffles-execution/bootstrap.py']
    health=document(binding_path+'health/HEALTH.json')
    assert health['status']=='PASS_BOUND_ORIGINAL_DEPENDENCY_IMPORTS_ONLY' and health['packages']==profile['packages']
    for edition in binding['versions']:
        report=document(binding_path+'version-'+edition+'/process/process.json')
        assert report['exit_code']==0 and not report['owned_live_after_cleanup']
    validation_path=BASE+'execution-development-validation-3/'
    validation=document(validation_path+'COMPLETED.json')
    assert validation['status']=='PASS_SNIFFLES_EXECUTION_DEVELOPMENT_PUBLIC_REFUSALS_AND_PROCESS_CONTROLS_WITH_DECLARED_SKIPS_ONLY'
    assert not validation['implementation_credit'] and not validation['calling_workflow_scientifically_validated']
    seen={(case['compiler'],case['platform'],case['name']) for case in validation['cases']}
    expected={(compiler,platform,name) for compiler in ['javac21','javac25'] for platform in ['windows21','windows25','linux21']
        for name in ['runtime-missing','reject-failed-batch','workflow-missing-request','existing-binding','existing-verification']}
    assert seen==expected and len(validation['cases'])==30
    for case in validation['cases']:
        prefix=validation_path+case['compiler']+'-'+case['platform']+'/'
        report=document(prefix+case['name']+'/process.json');assert report['exit_code']==case['exit_code']==report['expected_exit_code']
        if case['name'] in ['existing-binding','existing-verification']:assert case['existing_output_preserved'];continue
        assert b'ACTION' in files[prefix+case['name']+'/stderr']
        if case['receipt'] is not None:
            folder='binding' if case['name']=='runtime-missing' else 'verification'
            receipt=document(prefix+folder+'/COMPLETED.json')
            assert byte_pin(files[prefix+folder+'/COMPLETED.json'])==case['receipt']
            assert not receipt.get('analysis_admitted',False) and not receipt.get('verification_passed',False)
            assert b'ACTION' in files[prefix+folder+'/WARNINGS.md']
    import io
    passed=skipped=0
    for case in validation['process_controls']:
        raw=files[validation_path+case['archive_name']];assert byte_pin(raw)==case['archive']
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            assert archive.testzip() is None and len(archive.namelist())==len(set(archive.namelist()))
            nodes=json.loads(archive.read('nodes.json'));regular={}
            for name,node in nodes.items():
                safe(name)
                if node['kind']=='regular':
                    member=archive.read('files/'+name);assert byte_pin(member)==node['pin'];regular[name]=member
                else:assert node['kind'] in ['directory','fifo-not-opened','symlink-not-followed']
            assert set(archive.namelist())=={'nodes.json'}|{'files/'+name for name in regular}
            receipt=json.loads(regular['COMPLETED.json'])
            assert byte_pin(regular['COMPLETED.json'])==case['receipt']
            assert receipt['status']=='PASS_SNIFFLES_PROCESS_CONTROLS_WITH_DECLARED_SKIPS_ONLY'
            assert receipt['controls_count']==12 and receipt['passed_controls']==case['passed']==11 and receipt['skipped_controls']==case['skipped']==1
            assert receipt['unrelated_sentinel_preserved'] and not receipt['scientific_analysis_admitted']
            assert json.loads(regular['unrelated-sentinel/CLEANUP.json'])['owned_terminal']
            for control in receipt['controls']:
                if control['name']=='fifo':
                    assert control['status']=='SKIP3_FIFO_CREATION_UNAVAILABLE_ON_WORKSPACE_FILESYSTEM'
                    assert control['reason']=='FIFO_CREATION_UNAVAILABLE:95' and control['required_action']
                if 'ledger' in control:assert byte_pin(regular[control['name']+'/process.json'])==control['ledger']
            for name,value in regular.items():
                if name.endswith('/process.json'):
                    assert not json.loads(value).get('owned_live_after_cleanup',[])
            assert any(node['kind']=='symlink-not-followed' and node['target']=='/etc/passwd' for node in nodes.values())
            passed+=case['passed'];skipped+=case['skipped']
    assert passed==validation['passed_process_controls']==proof['passed_process_controls']==22
    assert skipped==validation['skipped_process_controls']==proof['skipped_process_controls']==2
    assert 'did not exercise FIFO rejection' in validation['superseded_FIFO_claim']
    assert 'Operation not supported' in files[BASE+'process-controls-1/fifo/process.stderr'].decode('utf8')
    assert proof['public_refusal_controls']==30 and proof['clean_checkout_runtime_provisioning_unfinished']
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen development checkpoint checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--archive-only',action='store_true');args=parser.parse_args()
    proof,files=verify(args.archive_only)
    print(proof['status']+'; '+str(len(files))+' archived files; 30 public refusal checks, 22 process passes, 2 explicit FIFO skips; no caller execution or availability credit')
