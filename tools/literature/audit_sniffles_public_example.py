# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit the actually executed relocated example against its complete direct original."""
import argparse,json,re,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from sniffles_installation_evidence import verify

BASE=ROOT/'build/literature-review/sniffles-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNIFFLES_PUBLIC_EXAMPLE_AUDIT',implementation_credit=False,whole_goal_complete=False)
    try:
        home=BASE/'public-example-1';old=BASE/'option-original-1/v24-numeric-cram-literal-phase'
        exported=json.loads((home/'EXPORTED.json').read_bytes());request=json.loads((home/'request.json').read_bytes())
        assert exported['status']=='PASS_FROZEN_TOY_SNIFFLES_EXAMPLE_EXPORT_ONLY' and not exported['analysis_executed']
        master=json.loads((home/'analysis/COMPLETED.json').read_bytes());checked=json.loads((home/'verification/COMPLETED.json').read_bytes())
        assert master['status']=='PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW' and master['analysis_admitted']
        assert checked['verification_passed'] and checked['complete_records']==2
        assert len(master['analyses'])==1 and master['analyses'][0]['id']=='phased-cram' and master['analyses'][0]['records']==2
        assert master['analyses'][0]['phased_genotypes']==2 and master['raw_inputs_unchanged'] and not master['owned_live_after_cleanup']
        assert pin(home/'request.json')==exported['exported_request'] and pin(home/'application/sniffles-workflow.jar')==exported['exported_artifact']
        _,frozen=verify(True);prefix='build/literature-review/sniffles-runtime/extended-fixtures-1/'
        for row in request['inputs']:
            path=ROOT/('D:/'+row['path'].removeprefix('/mnt/d/'))
            assert pin(path)==row['pin']==exported['exported_input_files'][row['id']]
            assert path.read_bytes()==frozen[prefix+('reference.fa' if row['id']=='reference' else 'sampleA.cram')]
        case=home/'analysis/analyses/phased-cram';original=json.loads((old/'complete-original-records.json').read_bytes())
        original_receipt=json.loads((old/'COMPLETED.json').read_bytes());assert pin(old/'original.vcf')==original_receipt['original_vcf']
        actual=[json.loads(line) for line in (case/'native/variants.jsonl').read_bytes().splitlines()]
        assert len(actual)==len(original['records'])==2
        for row,expected in zip(actual,original['records']):
            assert {key:row[key] for key in ['raw_line','columns','info','format','samples']}==expected
            assert row['read_support_info_field']=='SUPPORT' and row['read_support']==20
            assert row['samples'][0][row['format'].index('PS')]=='1001'
        header=json.loads((case/'native/header.json').read_bytes());assert header['column_line']==original['column_line']
        assert header['samples']==original['samples']==['--literal sample24']
        assert len(header['metadata_lines'])==len(original['metadata_lines']);changes=[]
        for before,after in zip(original['metadata_lines'],header['metadata_lines']):
            if before==after:continue
            key=before.partition('=')[0];assert after.partition('=')[0]==key
            if key=='##fileDate':assert all(re.fullmatch(r'##fileDate="\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2}"',value) for value in [before,after])
            elif key=='##command':
                original_command=json.loads((old/'command.json').read_bytes());i=original_command.index('--input')
                assert before==key+'="'+original_command[i-1]+'/src/sniffles/sniffles '+' '.join(original_command[i:])+'"'
                runtime=json.loads((home/'analysis/runtime.original.json').read_bytes())['runtime_root']
                args=json.loads((case/'original-request.json').read_bytes())['original_arguments']
                assert after==key+'="'+runtime+'/sources/v2.4/src/sniffles/sniffles '+' '.join(args)+'"'
            else:raise AssertionError(('Unexplained example header change',before,after))
            changes.append(dict(key=key,original=before,example=after))
        def population(root):return {p.relative_to(root).as_posix():pin(p) for p in root.rglob('*') if p.is_file()}
        before=population(home);commands=json.loads((home/'commands.json').read_bytes())
        # Refusing an existing export is tested without invoking a caller or installer.
        export=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3.12','-I','-B','-S',
            '/mnt/d/projects/JLinAlg/tools/literature/export_sniffles_public_example.py','--out','/mnt/d/projects/JLinAlg/'+home.relative_to(ROOT).as_posix(),
            '--binding',request['runtime']['path'],'--artifact',commands['call'][2]]
        run=subprocess.run(export,capture_output=True,timeout=120);save(out/'existing-output-command.json',export)
        (out/'existing-output.stdout').write_bytes(run.stdout);(out/'existing-output.stderr').write_bytes(run.stderr)
        assert run.returncode==2 and b'ACTION' in run.stderr and before==population(home)
        ledgers=[]
        for path in (home/'analysis').rglob('process.json'):
            value=json.loads(path.read_bytes());assert 'owned_live_after_cleanup' in value and not value['owned_live_after_cleanup']
            ledgers.append(dict(path=path.relative_to(home).as_posix(),pin=pin(path)))
        state.update(status='PASS_ACTUAL_RELOCATED_SNIFFLES_PUBLIC_EXAMPLE_AND_EXISTING_EXPORT_REFUSAL_ONLY',
            complete_cases=1,complete_records=2,phased_genotypes=2,all_original_record_and_header_values_checked=True,
            explicit_source_metadata_changes=changes,published_fixture_bytes_unchanged=True,existing_export_refused_without_changes=True,
            example_population=before,process_ledgers=ledgers,source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},
            independent_biological_accuracy_or_paper_reproduction=False)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete public example/reference checks')
    raise SystemExit(main())
