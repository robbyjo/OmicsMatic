# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the frozen Sniffles native VCF checkpoint without executing Java or Sniffles.

Checks source/artifact integrity and complete archived reference comparisons.
Does not independently rerun SV calling, establish authenticity or certify biology.
"""
import argparse
import hashlib
import io
import json
import tarfile
import zipfile
from pathlib import Path, PurePosixPath
from setup_deseq2_reference import ROOT, pin

HOME=ROOT/'docs/literature/sniffles-vcf-development'


def byte_pin(raw):
    return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def verify(archive_only=False,home=HOME):
    proof=json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['status']=='PASS_SOURCE_BOUND_NATIVE_SNIFFLES_VCF_COMPONENT_ONLY'
    assert not proof['implementation_credit'] and not proof['SV_calling_adapter_available'] and not proof['whole_goal_complete']
    assert pin(home/'controls.zip')==proof['archive']
    for name,value in proof['artifacts'].items():assert pin(home/name)==value
    if not archive_only:
        assert all(pin(ROOT/name)==value for name,value in proof['source_pins'].items())
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    with zipfile.ZipFile(home/'controls.zip') as opened:
        assert opened.testzip() is None and len(opened.namelist())==len(set(opened.namelist()))
        index=json.loads(opened.read('files.json'))
        files={}
        for name,value in index.items():
            path=PurePosixPath(name)
            assert not path.is_absolute() and '..' not in path.parts and '\\' not in name and ':' not in name
            raw=opened.read('blobs/'+value['sha256'])
            assert byte_pin(raw)==value
            files[name]=raw
        assert set(opened.namelist())=={'files.json'}|{'blobs/'+v['sha256'] for v in index.values()}
        assert byte_pin(opened.read('files.json'))==proof['file_index']
    def document(name):return json.loads(files[name])
    base='build/literature-review/sniffles-runtime/'
    for name,value in proof['source_pins'].items():assert byte_pin(files['current-source/'+name])==value
    intake=document(base+'source-intake-3/COMPLETED.json')
    assert intake['status']=='passed-five-exact-Sniffles-source-editions-only' and len(intake['editions'])==5
    commits={}
    for tag,record in intake['editions'].items():
        raw=files[record['archive']['path']]
        assert byte_pin(raw)=={k:record['archive'][k] for k in ['bytes','sha256']}
        members={};casefold=set()
        with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as opened:
            for member in opened.getmembers():
                if member.isdir():continue
                assert member.isfile()
                path=PurePosixPath(member.name)
                assert path.parts[0]=='Sniffles-'+record['commit'] and '..' not in path.parts
                name=PurePosixPath(*path.parts[1:]).as_posix()
                assert name not in members and name.casefold() not in casefold
                casefold.add(name.casefold());members[name]=byte_pin(opened.extractfile(member).read())
        assert members==record['members']
        commits[tag]=record['commit']
    assert commits==proof['original_editions']
    source=document(base+'primary-roles-2/COMPLETED.json')
    assert source['source_papers']==250 and source['observations']==2465 and source['roles']==6
    assert not source['catalog_changed'] and not source['implementation_credit']
    assert byte_pin(files[base+'primary-roles-2/role-drafts.json'])==source['role_drafts']
    assert byte_pin(files[base+'primary-roles-2/source-audit.json'])==source['source_audit']
    assert all(not p['original_paragraph_text_redistributed'] for p in document(base+'primary-roles-2/source-audit.json')['papers'])
    setup=document(base+'reference-setup-3/COMPLETED.json')
    denied=document(base+'reference-denial-1/COMPLETED.json')
    assert denied['status']=='SKIP3_SNIFFLES_REFERENCE_UNAVAILABLE' and not denied['processes']
    assert not denied['analysis_admitted'] and 'Download/install permission absent' in denied['error']
    assert 'REQUIRED ACTION:' in files[base+'reference-denial-1/WARNINGS.md'].decode('utf8')
    assert setup['status']=='PASS_PRIVATE_FIVE_ORIGINAL_SNIFFLES_REFERENCE_RUNTIMES_ONLY'
    assert setup['preexisting_python_and_standard_library_unchanged'] and not setup['source_patched']
    assert setup['legacy_cpp_language_standard']==11 and len(setup['probes'])==5
    assert byte_pin(files[base+'reference-setup-3/RUNTIME-FILES.json'])==setup['runtime_files']
    assert byte_pin(files[base+'reference-setup-3/SOURCE-COPY-FILES.json'])==setup['source_copy_files']
    for process in setup['processes']:
        assert process['exit_code']==0 and not process['owned_live_after_cleanup']
    reference=document(base+'original-controls-2/COMPLETED.json')
    assert reference['status']=='PASS_FOURTEEN_COMPLETE_ORIGINAL_SNIFFLES_REFERENCE_CONTROLS_ONLY'
    assert len(reference['cases'])==14 and reference['original_records']==39
    cases={}
    for case in reference['cases']:
        assert case['exit_code']==0 and not case['owned_live_after_cleanup']
        path=base+'original-controls-2/'+case['name']+'/'
        assert byte_pin(files[path+'original.vcf'])==case['original_vcf']
        assert byte_pin(files[path+'complete-original-records.json'])==case['complete_decoding']
        decoded=document(path+'complete-original-records.json')
        assert len(decoded['records'])==case['records']
        lines=files[path+'original.vcf'].decode('utf8').splitlines()
        assert [r['raw_line'] for r in decoded['records']]==[r for r in lines if not r.startswith('#')]
        assert decoded['column_line']==next(r for r in lines if r.startswith('#CHROM\t'))
        cases[case['name']]=(case,decoded)
    total=0
    for build,validation,artifact in proof['compiler_cases']:
        built=document(base+build+'/COMPLETED.json')
        assert built['status']=='PASS_ISOLATED_JAVA17_SNIFFLES_VCF_COMPONENT_BUILD_ONLY'
        assert byte_pin(files[built['artifact']['path']])=={k:built['artifact'][k] for k in ['bytes','sha256']}
        assert byte_pin(files[built['artifact']['path']])==proof['artifacts'][artifact]
        checked=document(base+validation+'/COMPLETED.json')
        assert checked['status']=='PASS_SNIFFLES_NATIVE_42_COMPLETE_REFERENCE_COMPARISONS_AND_123_FORMAT_CONTROLS'
        assert checked['format_controls']==123 and len(checked['comparisons'])==42
        assert len(checked['public_CLI_failure_controls'])==12
        for control in checked['public_CLI_failure_controls']:
            if control['name']=='existing-output':
                assert control['original_output_unchanged']
                continue
            failed=base+validation+'/'+control['platform']+'/failed-'+control['name']+'/'
            assert byte_pin(files[failed+'COMPLETED.json'])==control['receipt']
            receipt=document(failed+'COMPLETED.json')
            assert receipt['status']=='FAIL2_SNIFFLES_NATIVE_VCF_INSPECTION' and not receipt['inspection_admitted']
            assert 'ACTION REQUIRED:' in files[failed+'WARNINGS.md'].decode('utf8')
        seen=set()
        for comparison in checked['comparisons']:
            key=(comparison['platform'],comparison['case']);assert key not in seen;seen.add(key)
            path=base+validation+'/'+comparison['platform']+'/'+comparison['case']+'/'
            receipt=document(path+'COMPLETED.json')
            assert byte_pin(files[path+'COMPLETED.json'])==comparison['receipt']
            assert receipt['inspection_admitted'] and not receipt['SV_calling_executed']
            for name,value in receipt['outputs'].items():assert byte_pin(files[path+name])==value
            case,expected=cases[comparison['case']]
            assert json.loads(files[path+'header.json'])=={k:expected[k] for k in ['metadata_lines','column_line','samples']}
            actual=[json.loads(line) for line in files[path+'variants.jsonl'].decode('utf8').splitlines()]
            assert len(actual)==case['records']
            for got,want in zip(actual,expected['records']):assert {k:got[k] for k in want}==want
            total+=len(actual)
        assert seen=={(p,c) for p in ['windows21','windows25','linux21'] for c in cases}
    assert total==234 and proof['complete_record_comparisons']==234 and proof['format_controls']==246
    with zipfile.ZipFile(home/'sniffles-vcf-development.jar') as first,zipfile.ZipFile(home/'sniffles-vcf-javac25-development.jar') as second:
        assert first.namelist()==second.namelist()
        differing=[n for n in first.namelist() if first.read(n)!=second.read(n)]
        assert differing==proof['compiler_byte_differences']==['org/jlinalg/cli/SnifflesVcfCli.class']
    return proof,files


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete archived source/artifact/reference checks')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--archive-only',action='store_true')
    args=parser.parse_args();proof,files=verify(args.archive_only)
    print(proof['status']+'; '+str(len(files))+' archived files; no caller/JVM execution or catalog credit')
