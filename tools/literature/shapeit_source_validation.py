#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify retained exact source-build and complete workflow evidence; runs no build or analysis."""
from pathlib import Path, PurePosixPath
import gzip
import io
import json
import re
import struct

from shapeit_joined_validation import archive, digest
from shapeit_managed_validation import verify as verify_managed, POSITIVE, NEGATIVE

ROOT=Path(__file__).resolve().parents[2]
DATA=ROOT/'docs/literature/omics-2023-2026'
SCIENCE={'tools/literature/'+name for name in ['build_shapeit_runtime.py','test_build_shapeit_runtime.py',
    'shapeit_source_validation.py','test_shapeit_source_validation.py','install_shapeit.py','shapeit-runtime-lock.json']}|{
    'tools/literature/shapeit-source-build/'+name for name in ['build-lock.json','pixi.toml','pixi.lock','source-files.json','runtime-library-sources.json']}|{
    'tools/literature/shapeit-reference/JoinedWireProtocol.cpp','LICENSES/SHAPEIT5-MIT.txt','LICENSES/SHAPEIT5-XCF-MIT.txt','LICENSE'}


def bcf_payload(raw):
    assert len(raw)<=2097152
    with gzip.GzipFile(fileobj=io.BytesIO(raw)) as source:
        decoded=source.read(8388609);assert len(decoded)<=8388608 and not source.read(1)
    assert decoded[:5]==b'BCF\x02\x02'
    length=struct.unpack_from('<I',decoded,5)[0];assert 0<length<=1048576 and 9+length<=len(decoded)
    header,records=decoded[9:9+length].decode(),decoded[9+length:]
    offset=count=0
    while offset<len(records):
        assert offset+8<=len(records)
        shared,individual=struct.unpack_from('<II',records,offset);assert shared>=24
        offset+=8+shared+individual;assert offset<=len(records);count+=1
    assert offset==len(records)
    return header.splitlines(),records,count


def original_record_parity(current,previous):
    header,raw,count=bcf_payload(current);old_header,old_raw,old_count=bcf_payload(previous)
    assert raw==old_raw and count==old_count, 'Complete original fitting record payload differs'
    assert len([x for x in header if x.startswith('##fileDate=')])==len([x for x in old_header if x.startswith('##fileDate=')])==1
    assert [x for x in header if not x.startswith('##fileDate=')]==[x for x in old_header if not x.startswith('##fileDate=')], 'A header field other than fileDate changed'
    return count


def public(bundle,directory,code):
    prefix=directory+'/';manifest_raw=bundle[prefix+'manifest.json'];manifest=json.loads(manifest_raw);session=json.loads(bundle[prefix+'session.json'])
    assert session['exit_code']==code and session['scientific_result_admitted'] is (code==0) and session['phase_accuracy_certified'] is False
    actual={name[len(prefix):] for name in bundle if name.startswith(prefix)}
    assert actual==set(manifest['files'])|{'manifest.json'}|({'COMPLETED.json'} if code==0 else set()), directory
    for name,expected in manifest['files'].items():
        path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in name
        raw=bundle[prefix+name];assert len(raw)==expected['bytes'] and digest(raw)==expected['sha256'],name
    assert b'Required action' in bundle[prefix+'WARNINGS.md']
    if code==0:
        completed=json.loads(bundle[prefix+'COMPLETED.json']);assert completed['manifest_sha256']==digest(manifest_raw) and completed['scientific_result_admitted'] is True and completed['phase_accuracy_certified'] is False
    else:
        assert not any(name.startswith(prefix+'analysis/fit-process') or name==prefix+'analysis/phased.bcf' for name in bundle)
    return session


def private_bindings(text,home,expected):
    seen=[]
    for line in text.splitlines():
        line=line.strip()
        if not line:continue
        if line.startswith('linux-vdso.so.1 '):continue
        value=line.split('=>',1)[-1].strip().split(' (0x',1)[0].strip()
        assert value.startswith(home+'/lib/') and PurePosixPath(value).parent.as_posix()==home+'/lib',line
        seen.append(PurePosixPath(value).name)
    assert sorted(seen)==sorted(expected),seen


def verify(root=ROOT,data=DATA,emit=True):
    if not __debug__:raise RuntimeError('Validation requires assertions; do not use Python -O')
    record=json.loads((data/'shapeit-source-build-validation.json').read_bytes())
    assert record['status']=='passed-exact-source-built-SHAPEIT-common-runtime-setup'
    assert record['download_source_build_installer_available'] is True and record['source_sha256'].keys()==SCIENCE
    assert record['installation_mode']=='pinned-source-download-build-and-exact-adoption'
    for field in ['whole_paper_reproduced','phase_accuracy_certified','historical_editions_reproduced','binary_genotype_payloads_validated','released_or_deployed']:
        assert record[field] is False,field
    for path,expected in record['source_sha256'].items():assert digest((root/path).read_bytes())==expected,path
    item=record['artifact'];assert item['path']=='shapeit-source-build-controls.zip'
    raw=(data/item['path']).read_bytes();assert len(raw)==item['bytes'] and digest(raw)==item['sha256'];bundle=archive(raw,67108864)
    for path,expected in record['source_sha256'].items():assert digest(bundle['source/'+path])==expected,path
    verify_managed(root,data,emit=False)
    prior=archive((data/'shapeit-managed-common-controls.zip').read_bytes(),67108864)
    obj=lambda name:json.loads(bundle[name])
    lock=obj('source/tools/literature/shapeit-runtime-lock.json')
    config=obj('source/tools/literature/shapeit-source-build/build-lock.json')
    assert digest(bundle['source/tools/literature/shapeit-source-build/build-lock.json']) in bundle['source/tools/literature/build_shapeit_runtime.py'].decode()
    for name,expected in config['files'].items():
        raw=bundle['source/tools/literature/shapeit-source-build/'+name];assert len(raw)==expected['bytes'] and digest(raw)==expected['sha256']
    source_files=obj('source/tools/literature/shapeit-source-build/source-files.json');assert len(source_files['files'])==140
    libraries=obj('source/tools/literature/shapeit-source-build/runtime-library-sources.json');assert len(libraries)==21
    assert lock['source_commit']==config['source_commit']=='c34d4db3e99a2f7e23deb727671ae260901a5886' and len(lock['files'])==32
    assert len(config['common_object_order'])==34 and len(config['join_object_order'])==33
    assert config['downloads']['shapeit-source.tar.gz']['sha256']=='dcdad851e86ba01d8ddbf330e426f97c16e40cf64950dfd25d0641f1cd5f61f8'
    assert config['downloads']['pixi.tar.gz']['sha256']=='064f371e692aaa3400ac581c89da2843c91437f20c2d9155395a5d0037f33551'
    assert config['downloads']['libc6.deb']['sha256']=='ff5557d99b51f761c4b7c92368b9cc45565eda17df9bf9eb4b134d09825008be'
    census=obj('evidence/runtime-census.json');assert [c['index'] for c in census]==[2,3]
    roots=[]
    for index in [2,3]:
        setup=obj('setup/'+str(index)+'/report.json');observation=obj('setup/'+str(index)+'/observed.json')
        assert observation['exit_code']==0 and observation['source_unchanged'] is True and observation['report_sha256']==digest(bundle['setup/'+str(index)+'/report.json'])
        assert setup['status']=='installed-exact-source-built-runtime-only' and setup['scientific_analysis_admitted'] is False
        assert setup['source_commit']==lock['source_commit'] and setup['source_bytes_unchanged'] is True and setup['selected_source_files']==140 and setup['complete_runtime_files']==32
        assert setup['config_sha256']==digest(bundle['source/tools/literature/shapeit-source-build/build-lock.json'])
        captured=bundle['setup/'+str(index)+'/source/tools/literature/build_shapeit_runtime.py'];assert setup['builder_sha256']==digest(captured)
        if index==3:assert setup['builder_sha256']==record['source_sha256']['tools/literature/build_shapeit_runtime.py']
        roots.append(setup['work'])
        for name in ['dependency-install','compile-common','compile-join','unpack-libc']:
            process=obj('evidence/builds/'+str(index)+'/'+name+'.process.json')
            assert process['exit_code']==0 and process['owned_live_after_cleanup']==[]
            assert process['command'][:2]==['/usr/bin/prlimit','--as=4294967296']
            for suffix in ['stderr','stdout']:
                path='evidence/builds/'+str(index)+'/'+name+'.'+suffix
                if name=='unpack-libc' and suffix=='stdout':
                    omitted=obj('evidence/builds/'+str(index)+'/omitted-payloads.json');assert len(omitted)==1 and omitted[0]['sha256']==process['stdout_sha256']
                else:assert digest(bundle[path])==process[suffix+'_sha256']
        assert '--locked' in obj('evidence/builds/'+str(index)+'/dependency-install.process.json')['command']
        assert digest(bundle['evidence/builds/'+str(index)+'/canonical-compiler.specs'])==config['canonical_compiler_specs_sha256']
        assert len(setup['library_reconstruction'])==21
        for library in setup['library_reconstruction']:
            name=library['member'];assert library['raw_sha256']==libraries[name]['raw_sha256'] and library['canonical_sha256']==lock['files'][name]['sha256']
        assert len(setup['compiler_warning_lines'])==3 and all('not found' in s for s in setup['compiler_warning_lines'])
        assert setup['adopter_result']==census[index-2]['marker']
        marker=census[index-2]['marker'];assert marker['status']=='installed-exact-runtime-only' and marker['scientific_analysis_admitted'] is False and marker['files']==lock['files']
        assert marker['lock_sha256']==digest(bundle['source/tools/literature/shapeit-runtime-lock.json']) and marker['installer_sha256']==record['source_sha256']['tools/literature/install_shapeit.py']
        assert census[index-2]['files'].keys()==lock['files'].keys()|{'.omicsmatic-shapeit.json'}
        for name,expected in lock['files'].items():assert census[index-2]['files'][name]==expected
    assert roots[0]!=roots[1]
    relocated=obj('setup/3/running.json')['command'];assert '/opt/omicsmatic-shapeit-relocated-checkout-1/tools/literature/build_shapeit_runtime.py' in relocated
    unit=bundle['setup/3/unit-linux-final.log'].decode().replace('\r\n','\n')
    assert re.search(r'^Ran 24 tests in [0-9.]+s\n\nOK\n?$',unit,re.M) and 'test_cli_sigterm_retains_failure_and_cleans_its_worker_only' in unit
    assert 'skipped' not in unit and 'FAILED' not in unit and 'Traceback' not in unit
    negative=obj('negative/observed.json');assert negative['status']=='passed-real-source-setup-permission-and-integrity-refusals' and negative['analysis_launched'] is False
    assert [(c['name'],c['exit_code']) for c in negative['controls']]==[('no-download',3),('no-source-build',3),('no-install',3),('corrupt-cache',2),('unsafe-work-path',2)]
    for case in negative['controls']:
        name=case['name'];refusal=obj('negative/'+name+'.json');assert refusal['scientific_analysis_admitted'] is False and refusal['status'].startswith('SKIP3' if case['exit_code']==3 else 'FAIL2')
        assert case['home_created'] is False and b'WARNING:' in bundle['negative/'+name+'.stderr'] and b'Required action:' in bundle['negative/'+name+'.stderr']
    assert obj('negative/windows-host-control.json')['exit_code']==3 and obj('negative/windows-host.json')['scientific_analysis_admitted'] is False
    fits=protocols=tables=record_parities=0
    for repeat in [1,2]:
        totals=dict(rows=0,targets=0,references=0,scaffolds=0,source_an=0)
        observations=obj('controls/'+str(repeat)+'/observed.json')
        assert observations['status']=='passed-source-built-runtime-twenty-public-protocols' and observations['protocols']==20 and observations['actual_successful_fits']==9
        assert observations['candidate_sha256']=='480e6257e5ad500d3e3f33df661e21c3bada6c68e8eacb44bafd6b5ab82a75e2' and observations['runtime_unchanged'] is True
        assert observations['setup_report_sha256']==digest(bundle['setup/'+str(repeat+1)+'/report.json'])
        common=obj('evidence/workflows/'+str(repeat)+'/common/observed.json')
        assert len(common['commands'])==12 and common['successful_actual_fits']==8 and common['expected_pre_fitting_refusals']==4
        for case in common['commands']:
            directory='evidence/workflows/'+str(repeat)+'/common/'+PurePosixPath(case['work']).name
            assert case['public_manifest_sha256']==digest(bundle[directory+'/manifest.json'])
        policies=obj('evidence/workflows/'+str(repeat)+'/policies/observed.json')
        assert len(policies['outcomes'])==3
        for case in policies['outcomes']:
            assert case['manifest_sha256']==digest(bundle['evidence/workflows/'+str(repeat)+'/policies/'+case['name']+'/manifest.json'])
        cases=[('common',name,0) for name in sorted(POSITIVE)]+[('common',name,2) for name in sorted(NEGATIVE)]+[
            ('policies','count-strict',2),('policies','count-declared',0),('policies','missing-complete',2),
            ('transport','bare-bcf',3),('transport','plain-vcf',3),('transport','gzip-vcf',3),('transport','binary-xcf',3),('transport','malformed-gzip',2)]
        for group,name,code in cases:
            directory='evidence/workflows/'+str(repeat)+'/'+group+'/'+name;public(bundle,directory,code);protocols+=1
            if code:
                if group=='transport':assert not any(p.startswith(directory+'/analysis/join-process') for p in bundle)
                continue
            fits+=1;prefix=directory+'/analysis/';native=obj(prefix+'analysis.json');a=native['admission']
            assert native['scientific_result_admitted'] is True and native['phase_accuracy_certified'] is False and native['status']=='completed-audited-common-stage'
            runtime=native['runtime'];assert runtime['files']==lock['files'] and runtime['home']==census[repeat-1]['home']
            for role,program in [('join-process','joined-wire'),('fit-process','phase_common')]:
                for suffix in ['', '.loader-check']:
                    process=obj(prefix+role+suffix+'/process.json');assert process['owned_live_after_cleanup']==[] and process['exit_code']==0 and process['status']=='completed-process-only'
                    assert runtime['home']+'/lib/ld-linux-x86-64.so.2' in process['command'] and '--inhibit-rpath' in process['command']
                private_bindings(bundle[prefix+role+'.loader-check/process.stdout'].decode(),runtime['home'],lock['bindings'][program])
            totals['rows']+=a['selectedRows'];totals['targets']+=a['selectedTargetCells'];totals['references']+=2*a['selectedReferenceCells'];totals['scaffolds']+=a['appliedScaffoldConstraints'];totals['source_an']+=native['source_AN_includes_reference_rows']
            old='workflow/'+('public-cli' if group=='common' else 'policies')+'/'+name+'/analysis/'
            for table in ['input-count-review.tsv','output-audit/target-allele-counts.tsv']:
                assert bundle[prefix+table]==prior[old+table];tables+=1
            assert original_record_parity(bundle[prefix+'phased.bcf'],prior[old+'phased.bcf'])==a['selectedRows'];record_parities+=1
        assert totals==dict(rows=1131,targets=41470,references=30160,scaffolds=573,source_an=377),totals
    assert (protocols,fits,tables,record_parities)==(40,18,36,18)
    for name,value in dict(successful_runtime_installs=2,runtime_members=32,public_protocols=40,actual_successful_fits=18,unique_control_datasets=9,byte_equal_record_payloads=18,byte_equal_count_tables=36,installer_linux_tests=24).items():assert record[name]==value,name
    result=dict(status='passed',successful_runtime_installs=2,public_protocols=40,actual_successful_fits=18,
                unique_control_datasets=9,byte_equal_record_payloads=18,byte_equal_count_tables=36,installer_linux_tests=24,
                download_source_build_installer_available=True,whole_paper_reproduced=False,
                scope='Retained exact-byte source builds at two different prefixes, including the current builder from a relocated checkout, and two complete common-stage control repeats. Verifies evidence and current byte-pinned source only; no download, build, live phasing, biological phase-accuracy or whole-paper certification is performed.')
    if emit:print(json.dumps(result,indent=2))
    return result


if __name__=='__main__':verify()
