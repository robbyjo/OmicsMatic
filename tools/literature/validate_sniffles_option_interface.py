# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise the isolated corrected option interface against complete original outputs.

Uses the fresh installed/bound runtime, both compiler artifacts for portable
verification, and a deliberately rehashed false-view rejection. No catalog credit.
"""
import argparse,copy,json,os,re,stat,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/sniffles-runtime';DISTRO='JLinAlg-Omics-SAIGE-20261026'


def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:';return '/mnt/d/'+path.as_posix()[3:]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    preview=BASE/'option-interface-preview-1';binding=BASE/'runtime-binding-4/COMPLETED.json'
    state=dict(status='FAIL2_ISOLATED_SNIFFLES_OPTION_INTERFACE_VALIDATION',implementation_credit=False,workspace_interface_applied=False,whole_goal_complete=False,refusals=[],comparisons=[],portable_verifications=[])
    try:
        built=json.loads((preview/'COMPLETED.json').read_bytes());assert built['status']=='PASS_ISOLATED_SNIFFLES_OPTION_INTERFACE_PREVIEW_BUILD_ONLY'
        assert all(pin(ROOT/name)==value for name,value in built['current_workspace_sources_unchanged'].items()),'Preserve the running-test workspace until this isolated candidate is validated'
        artifacts={label:ROOT/row['path'] for label,row in built['artifacts'].items()}
        assert all(pin(artifacts[label])=={key:row[key] for key in ['bytes','sha256']} for label,row in built['artifacts'].items())
        refs={}
        for folder,wanted in [('option-original-1','PASS_FIVE_ORIGINAL_SNIFFLES_OPTION_EXPERIMENTS_ONLY'),('original-controls-2','PASS_FOURTEEN_COMPLETE_ORIGINAL_SNIFFLES_REFERENCE_CONTROLS_ONLY'),('extended-original-2','PASS_ELEVEN_EXTENDED_ORIGINAL_SNIFFLES_REFERENCE_EXPERIMENTS_ONLY')]:
            record=json.loads((BASE/folder/'COMPLETED.json').read_bytes());assert record['status']==wanted
            for case in record['cases']:
                if case.get('complete_decoding'):refs[case.get('id',case.get('name'))]=(BASE/folder,case)
        original_binding=json.loads(binding.read_bytes());assert original_binding['status']=='PASS_BOUND_ORIGINAL_SNIFFLES_RUNTIME_ONLY'
        request=json.loads((BASE/'workflow-current-request-2/request.json').read_bytes());request['runtime']=dict(path=linux(binding),pin=pin(binding))
        fixtures=BASE/'fixtures-1';extended=BASE/'extended-fixtures-1'
        def input(id,kind,path,reference=None):
            row=dict(id=id,kind=kind,path=linux(path),pin=pin(path))
            if reference:row['reference']=reference
            return row
        request['inputs']=[input('sampleA','bam',fixtures/'sampleA.bam'),input('sampleB','bam',fixtures/'sampleB.bam'),
            input('referencePlain','fasta',extended/'reference.fa'),input('cram','cram',extended/'sampleA.cram','referencePlain'),
            input('knownDup','vcf',extended/'known-dup.vcf'),input('knownV207','vcf',BASE/'original-controls-2/v207-sampleA/original.vcf'),
            input('tandem','bed',fixtures/'tandem-repeats.bed')]
        common=dict(threads=1,minsvlen=80,mapq=22)
        request['analyses']=[
            dict(id='v107-legacy-segment-high',edition='1.0.7',input='sampleA',threads=1,legacy_minimum_segment_size=1000000),
            dict(id='v24-alignment-high',edition='2.4',input='sampleA',threads=1,minimum_alignment_length=1000000),
            dict(id='v107-numeric-genotype',edition='1.0.7',input='sampleA',**common,minsupport=10,legacy_minimum_segment_size=500,legacy_genotype=True),
            dict(id='v202-numeric-auto-literal',edition='2.0.2',input='sampleA',**common,minsupport='auto',minimum_alignment_length=500,sample_id='--literal sample202'),
            dict(id='v24-numeric-cram-literal-phase',edition='2.4',input='cram',**common,minsupport=10,minimum_alignment_length=500,sample_id='--literal sample24',phase=True),
            dict(id='v107-known-dup',edition='1.0.7',input='sampleA',threads=1,genotype_vcf='knownDup'),
            dict(id='v207-sampleA',edition='2.0.7',input='sampleA',threads=1),dict(id='v232-sampleA',edition='2.3.2',input='sampleA',threads=1),
            dict(id='v207-force',edition='2.0.7',input='sampleB',threads=1,genotype_vcf='knownV207'),
            dict(id='v202-tandem',edition='2.0.2',input='sampleA',threads=1,tandem_repeats='tandem')]
        def run(home,command,code,timeout=1800):
            home.mkdir();save(home/'command.json',command)
            process=subprocess.run(command,capture_output=True,timeout=timeout)
            (home/'stdout').write_bytes(process.stdout);(home/'stderr').write_bytes(process.stderr)
            save(home/'process.json',dict(observed_exit_code=process.returncode,expected_exit_code=code))
            assert process.returncode==code,(home,process.returncode,process.stderr.decode(errors='replace'))
            return process
        def caller(path):return ['wsl','-d',DISTRO,'--exec','/usr/bin/java','-cp',linux(artifacts['javac25']),'org.jlinalg.cli.SnifflesWorkflowCli','--request',linux(path)]
        for name,edition,key in [('legacy-alignment-filter-refused','1.0.7','minimum_alignment_length'),('modern-legacy-segment-refused','2.4','legacy_minimum_segment_size')]:
            home=out/name;home.mkdir();data=copy.deepcopy(request);data['out']=linux(home/'run');data['inputs']=[data['inputs'][0]]
            data['analyses']=[dict(id='wrong-edition-option',edition=edition,input='sampleA',threads=1,**{key:1000000})]
            save(home/'request.json',data);process=run(home/'invocation',caller(home/'request.json'),3,120)
            assert not (home/'run').exists() and b'no ' in process.stderr and b'ACTION' in process.stderr
            state['refusals'].append(dict(id=name,exit_code=3,no_original_process_or_output_directory=True));print('PASS isolated option refusal '+name,flush=True)
        request['out']=linux(out/'workflow');save(out/'request.json',request)
        protected=[Path(__file__).resolve(),binding,preview/'COMPLETED.json',*artifacts.values()]
        for item in request['inputs']:
            assert item['path'].startswith('/mnt/d/');protected.append(Path('D:/'+item['path'].removeprefix('/mnt/d/')))
        before={path.relative_to(ROOT).as_posix():pin(path) for path in protected}
        run(out/'calling',caller(out/'request.json'),0,7200)
        workflow=out/'workflow';master=json.loads((workflow/'COMPLETED.json').read_bytes())
        assert master['status']=='PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW' and master['analysis_admitted'] and len(master['analyses'])==10
        assert master['raw_inputs_unchanged'] and not master['source_namespace_patched'] and not master['owned_live_after_cleanup']
        mapping={item['id']:item for item in json.loads((workflow/'input-mapping.json').read_bytes())};requested={row['id']:row for row in request['analyses']};total=0
        for case in master['analyses']:
            name=case['id'];version=case['edition'];reference,expected=refs[name];old=reference/name;home=workflow/'analyses'/name
            assert pin(old/'complete-original-records.json')==expected['complete_decoding'] and pin(old/'original.vcf')==expected['original_vcf']
            original=json.loads((old/'complete-original-records.json').read_bytes());native=json.loads((home/'native/header.json').read_bytes())
            rows=[json.loads(line) for line in (home/'native/variants.jsonl').read_text(encoding='utf8').splitlines()]
            assert [{key:row[key] for key in ['raw_line','columns','info','format','samples']} for row in rows]==original['records']
            assert len(rows)==expected['records']==case['records'];total+=len(rows)
            assert len(native['metadata_lines'])==len(original['metadata_lines']);changes=[]
            for previous,current in zip(original['metadata_lines'],native['metadata_lines']):
                if previous==current:continue
                if previous.startswith('##fileDate='):
                    assert re.fullmatch(r'##fileDate="\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2}"',current);changes.append('recorded source timestamp')
                elif previous.startswith('##command='):
                    args=json.loads((home/'original-request.json').read_bytes())['original_arguments']
                    assert current=='##command="'+original_binding['runtime_root']+'/sources/v'+version+'/src/sniffles/sniffles '+' '.join(args)+'"'
                    command=json.loads((old/'command.json').read_bytes());command=command['command'] if isinstance(command,dict) else command
                    index=command.index('--input');assert previous=='##command="'+command[index-1]+'/src/sniffles/sniffles '+' '.join(command[index:])+'"'
                    changes.append('exact captured original invocations')
                else:raise AssertionError((name,'unexpected source header difference',previous,current))
            if version=='1.0.7':
                assert native['samples']==[mapping[requested[name]['input']]['captured_path']] and original['samples']==[mapping[requested[name]['input']]['original_path']]
                assert native['column_line'].split('\t')[:9]==original['column_line'].split('\t')[:9];changes.append('explicit legacy captured-input sample path')
            else:assert native['column_line']==original['column_line'] and native['samples']==original['samples']
            state['comparisons'].append(dict(id=name,edition=version,records=len(rows),complete_original_VCF_and_native_rows_match=True,explicit_metadata_changes=changes))
        assert total==20 and any('SVTYPE=INV for a declared DUP' in warning for warning in master['warnings'])
        assert 'SVTYPE=INV for a declared DUP' in (workflow/'WARNINGS.md').read_text(encoding='utf8')
        runtimes={'windows21':['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'],
            'windows25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'],'linux21':['wsl','-d',DISTRO,'--exec','/usr/bin/java']}
        for compiler,artifact in artifacts.items():
            for platform,prefix in runtimes.items():
                transform=linux if platform=='linux21' else lambda path:str(path.resolve())
                target=out/(compiler+'-'+platform+'-verify')
                cmd=[*prefix,'-cp',transform(artifact),'org.jlinalg.cli.SnifflesVerifyCli','--result',transform(workflow),'--out',transform(target)]
                run(out/(compiler+'-'+platform+'-verify-process'),cmd,0,300)
                receipt=json.loads((target/'COMPLETED.json').read_bytes());assert receipt['verification_passed'] and receipt['complete_records']==20
                assert all(warning in (target/'WARNINGS.md').read_text(encoding='utf8') for warning in master['warnings'])
                state['portable_verifications'].append(dict(compiler=compiler,platform=platform,receipt=pin(target/'COMPLETED.json'),original_warnings_retained=True))
        # Independent controlled copy: repair only the inventory hashes so the
        # verifier must detect semantic inconsistency with the unchanged VCF.
        clone=out/'false-rehashed-view';clone.mkdir();pending=[workflow]
        while pending:
            parent=pending.pop()
            with os.scandir(parent) as stream:
                for item in stream:
                    path=Path(item.path);destination=clone/path.relative_to(workflow);mode=item.stat(follow_symlinks=False).st_mode
                    if stat.S_ISDIR(mode):destination.mkdir();pending.append(path)
                    else:assert stat.S_ISREG(mode);destination.write_bytes(path.read_bytes())
        target=clone/'analyses/v107-known-dup/native/variants.jsonl';rows=[json.loads(line) for line in target.read_text(encoding='utf8').splitlines()]
        assert rows[0]['read_support']==0;rows[0]['read_support']=999;target.write_text('\n'.join(json.dumps(row,separators=(',',':')) for row in rows)+'\n',encoding='utf8',newline='\n')
        index=clone/'result-files.json';inventory=json.loads(index.read_bytes());inventory[target.relative_to(clone).as_posix()]=pin(target);save(index,inventory)
        counterfeit=json.loads((clone/'COMPLETED.json').read_bytes());counterfeit['result_files_pin']=pin(index);save(clone/'COMPLETED.json',counterfeit)
        rejected=out/'false-rehashed-verification';run(out/'false-rehashed-process',[*runtimes['windows21'],'-cp',str(artifacts['javac21']),'org.jlinalg.cli.SnifflesVerifyCli','--result',str(clone),'--out',str(rejected)],2,300)
        failure=json.loads((rejected/'COMPLETED.json').read_bytes());assert not failure['verification_passed'] and 'Frozen native full-record view differs from original VCF' in failure['error']
        assert before=={name:pin(ROOT/name) for name in before}
        state.update(status='PASS_ISOLATED_SNIFFLES_OPTIONS_TEN_COMPLETE_CASES_AND_SIX_PORTABLE_VERIFICATIONS_ONLY',
            complete_cases=10,complete_records=20,protected_inputs_and_sources_unchanged=before,workflow_receipt=pin(workflow/'COMPLETED.json'),
            internally_rehashed_false_view_rejected=pin(rejected/'COMPLETED.json'),current_workspace_interface_still_unapplied=True)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full original option/value and false-view checks')
    raise SystemExit(main())
