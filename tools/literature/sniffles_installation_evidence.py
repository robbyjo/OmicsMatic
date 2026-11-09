# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check frozen clean-install and upstream format/source experiments without execution."""
import argparse,io,json,tarfile,zipfile
from pathlib import PurePosixPath
from setup_deseq2_reference import ROOT,pin
from sniffles_native_evidence import verify as native_verify,byte_pin

HOME=ROOT/'docs/literature/sniffles-installation-validation'
BASE='build/literature-review/sniffles-runtime/'


def verify(archive_only=False,home=HOME):
    proof=json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['status']=='PASS_PRIVATE_SNIFFLES_CLEAN_INSTALL_AND_ORIGINAL_FORMAT_EXPERIMENTS_ONLY'
    assert not any(proof[key] for key in ['implementation_credit','SV_calling_adapter_available','Java_calling_workflow_validated','whole_paper_reproduced','whole_goal_complete'])
    assert pin(home/'controls.zip')==proof['archive']
    if not archive_only:
        assert all(pin(ROOT/name)==value for name,value in proof['source_pins'].items())
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    _,prior=native_verify(True)
    with zipfile.ZipFile(home/'controls.zip') as archive:
        assert archive.testzip() is None and len(archive.namelist())==len(set(archive.namelist()))
        index_raw=archive.read('files.json');assert byte_pin(index_raw)==proof['file_index'];index=json.loads(index_raw);files={}
        for name,value in index.items():
            path=PurePosixPath(name);assert name and not path.is_absolute() and '..' not in path.parts and '\\' not in name and ':' not in name
            raw=archive.read('blobs/'+value['sha256']);assert byte_pin(raw)==value;files[name]=raw
        assert len(files)==proof['logical_files'] and set(archive.namelist())=={'files.json'}|{'blobs/'+v['sha256'] for v in index.values()}
    for name,value in proof['companion_artifacts'].items():
        assert value==proof['source_pins'][name]
        raw=(ROOT/name).read_bytes();assert byte_pin(raw)==value;files['current-source/'+name]=raw
    def document(name):return json.loads(files[name])
    for name,value in proof['source_pins'].items():assert byte_pin(files['current-source/'+name])==value
    manifest=document('current-source/docs/literature/sniffles-runtime-distribution/DISTRIBUTION.json')
    profile=document('current-source/src/main/resources/org/jlinalg/omics/sniffles-execution/profiles.json')
    assert manifest['profile']==byte_pin(files['current-source/src/main/resources/org/jlinalg/omics/sniffles-execution/profiles.json'])
    assets=files['current-source/docs/literature/sniffles-runtime-distribution/assets.zip'];assert byte_pin(assets)==manifest['assets']
    source=json.loads(prior[BASE+'source-intake-3/COMPLETED.json'])
    assert manifest['source_commits']=={tag:row['commit'] for tag,row in source['editions'].items()}
    assert manifest['source_archives']=={tag:row['archive'] for tag,row in source['editions'].items()}
    with zipfile.ZipFile(io.BytesIO(assets)) as archive:
        assert archive.testzip() is None and len(archive.namelist())==len(set(archive.namelist())) and set(archive.namelist())==set(manifest['asset_members'])
        for name,value in manifest['asset_members'].items():assert byte_pin(archive.read(name))==value
        assert byte_pin(archive.read('legacy-sniffles'))==manifest['original_binary']==profile['legacy_binary']
        old=source['editions']['v1.0.7'];raw=prior[old['archive']['path']]
        with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as original:
            for name in manifest['asset_members']:
                if name.startswith('notices/v1.0.7/'):
                    relative=name.removeprefix('notices/v1.0.7/')
                    assert archive.read(name)==original.extractfile('Sniffles-'+old['commit']+'/'+relative).read()
    old_wheels=json.loads(prior[BASE+'reference-setup-3/downloaded-archives.json'])
    assert manifest['wheels']=={name:value for name,value in old_wheels.items() if value['package'] in ['pysam','edlib','psutil']}
    validation_path=BASE+'install-validation-1/';validation=document(validation_path+'COMPLETED.json')
    assert validation['status']=='PASS_SNIFFLES_CLEAN_INSTALL_AND_FIVE_PUBLIC_REFUSALS_ONLY'
    assert not validation['analysis_admitted'] and not validation['implementation_credit'] and not validation['preexisting_ignored_runtime_required']
    assert validation['minimal_clean_source_files']==12 and validation['installed_runtime_files']==1721 and validation['private_site_files']==573
    assert {case['id'] for case in validation['cases']}=={'permission-absent','download-only','install-only','unsupported-windows','existing-output','fresh-install'}
    for case in validation['cases']:
        run=document(validation_path+case['id']+'/process.json');assert run['exit_code']==case['exit_code']==run['expected_exit_code']
    installed=validation_path+'clean-project/build/literature-review/sniffles-runtime/installed/'
    receipt=document(installed+'COMPLETED.json');assert byte_pin(files[installed+'COMPLETED.json'])==validation['installed_receipt']
    assert receipt['status']=='PASS_INSTALLED_EXACT_PRIVATE_SNIFFLES_RUNTIME_ONLY' and not receipt['analysis_admitted']
    assert not receipt['system_packages_installed'] and not receipt['source_namespace_patched'] and receipt['preparation_inputs_unchanged']
    assert receipt['shared_python_and_standard_library_unchanged'] and receipt['original_runtime_health_and_binding_still_required']
    assert len(receipt['processes'])==1 and receipt['processes'][0]['exit_code']==0 and not receipt['processes'][0]['owned_live_after_cleanup']
    assert files[installed+'shared-before.json']==files[installed+'shared-after.json']
    assert document(installed+'installed-site-files.json')==profile['private_site_members']
    expected={'python':profile['python'],'legacy-sniffles':profile['legacy_binary']}
    expected.update({'site/'+name:value for name,value in profile['private_site_members'].items()})
    for tag,row in profile['editions'].items():expected.update({'sources/'+tag+'/'+name:value for name,value in row['members'].items()})
    assert len(expected)==1721 and document(installed+'runtime-files.json')==expected and byte_pin(files[installed+'runtime-files.json'])==receipt['files']
    assert all(byte_pin(files['current-source/'+name])==value for name,value in document(validation_path+'clean-source-files.json').items())
    assert all(byte_pin(files['current-source/'+name])==value for name,value in receipt['source_pins'].items())
    assert 'ACTION REQUIRED' in files[installed+'WARNINGS.md'].decode('utf8')
    official={manifest['pip']['filename']:manifest['pip'],**manifest['wheels']}
    assert len(receipt['downloads'])==4
    for row in receipt['downloads']:
        wanted=official[row['name']];assert row['url']==wanted['url'] and {k:row[k] for k in ['bytes','sha256']}=={k:wanted[k] for k in ['bytes','sha256']}
    for name in ['permission-absent','download-only','install-only','unsupported-windows']:
        rejected=document(validation_path+'clean-project/build/literature-review/sniffles-runtime/'+name+'/COMPLETED.json')
        assert rejected['status']=='SKIP3_SNIFFLES_INSTALLATION_UNAVAILABLE' and not rejected['processes'] and not rejected['downloads'] and not rejected['analysis_admitted']
    experiments=document(BASE+'extended-original-2/COMPLETED.json')
    assert experiments['status']=='PASS_ELEVEN_EXTENDED_ORIGINAL_SNIFFLES_REFERENCE_EXPERIMENTS_ONLY'
    assert experiments['original_sources_and_inputs_unchanged'] and not experiments['Java_calling_adapter_validated'] and not experiments['analysis_admitted']
    assert len(experiments['cases'])==11 and experiments['scientific_comparison_cases']==9 and experiments['source_behavior_probes']==2
    records=0
    for case in experiments['cases']:
        prefix=BASE+'extended-original-2/'+case['id']+'/'
        assert case==document(prefix+'COMPLETED.json')
        process=document(prefix+'process.json');assert process['exit_code']==0 and not process['owned_live_after_cleanup']
        assert byte_pin(files[prefix+'original.vcf'])==case['original_vcf']
        if case['kind']=='wrong-contig-error':
            assert case['original_source_fatal_exit_zero'] and case['failed_VCF_not_parsed_as_valid_callset'] and case['original_failed_vcf_bytes']==0
            assert files[prefix+'original.vcf']==b'' and b'Sniffles2 Error:' in files[prefix+'original.stderr']
            continue
        decoded=document(prefix+'complete-original-records.json')
        assert byte_pin(files[prefix+'complete-original-records.json'])==case['complete_decoding']
        rows=[line for line in files[prefix+'original.vcf'].decode('utf8').splitlines() if not line.startswith('#')]
        assert rows==[row['raw_line'] for row in decoded['records']] and len(rows)==case['records']
        records+=len(rows)
        if case['kind']=='known-dup':assert case['declared_input_SVTYPE']=='DUP' and case['observed_original_SVTYPEs']==['INV'] and case['source_discrepancy']
        elif case['kind']=='empty':assert not rows
        else:assert len(rows)==3
    assert records==25==proof['complete_original_records']
    failed=document(BASE+'extended-original-1/COMPLETED.json');assert failed['status']=='FAIL2_SNIFFLES_EXTENDED_ORIGINAL_REFERENCE' and not failed['analysis_admitted']
    assert 'raw.endswith' in files[BASE+'extended-original-1/error.traceback'].decode('utf8')
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen installation and original experiment checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--archive-only',action='store_true');args=parser.parse_args()
    proof,files=verify(args.archive_only)
    print(proof['status']+'; '+str(proof['logical_files'])+' archived files plus '+str(len(proof['companion_artifacts']))+' pinned repository companions; clean private install and upstream experiments only; no caller execution or catalog credit')
