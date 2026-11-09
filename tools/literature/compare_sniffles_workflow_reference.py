# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare every complete native case/record to direct original reference experiments.

Only whole-batch PASS results qualify. Dynamic command/date fields and explicit
legacy captured-input sample paths are checked separately, never silently edited.
"""
import argparse,json,re,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--workflow',type=Path,required=True)
    parser.add_argument('--reference',type=Path,required=True);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    base=ROOT/'build/literature-review/sniffles-runtime';out=a.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNIFFLES_COMPLETE_WORKFLOW_REFERENCE_COMPARISON',comparison_passed=False,implementation_credit=False,whole_goal_complete=False,cases=[])
    try:
        workflow=a.workflow.resolve();reference=a.reference.resolve();artifact=a.artifact.resolve()
        master=json.loads((workflow/'COMPLETED.json').read_bytes());original=json.loads((reference/'COMPLETED.json').read_bytes())
        assert master['status']=='PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW' and master['analysis_admitted']
        assert original['status'] in ['PASS_FOURTEEN_COMPLETE_ORIGINAL_SNIFFLES_REFERENCE_CONTROLS_ONLY','PASS_ELEVEN_EXTENDED_ORIGINAL_SNIFFLES_REFERENCE_EXPERIMENTS_ONLY']
        assert master['raw_inputs_unchanged'] and not master['source_namespace_patched'] and not master['owned_live_after_cleanup']
        before={p.relative_to(ROOT).as_posix():pin(p) for p in [workflow/'COMPLETED.json',reference/'COMPLETED.json',artifact,Path(__file__).resolve()]}
        command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-cp',str(artifact),'org.jlinalg.cli.SnifflesVerifyCli',
            '--result',str(workflow),'--out',str(out/'frozen-verification')]
        save(out/'verify-command.json',command);run=subprocess.run(command,capture_output=True,timeout=300)
        (out/'verify.stdout').write_bytes(run.stdout);(out/'verify.stderr').write_bytes(run.stderr)
        assert run.returncode==0,run.stderr.decode(errors='replace')
        verified=json.loads((out/'frozen-verification/COMPLETED.json').read_bytes());assert verified['verification_passed']
        expected={case.get('name',case.get('id')):case for case in original['cases'] if case.get('complete_decoding')}
        assert {case['id'] for case in master['analyses']}==set(expected),'Complete scientific case population differs from direct references'
        mapping=json.loads((workflow/'input-mapping.json').read_bytes());mapping={item['id']:item for item in mapping}
        request=json.loads((workflow/'request.original.json').read_bytes());requests={case['id']:case for case in request['analyses']}
        runtime=json.loads((workflow/'runtime.original.json').read_bytes())['runtime_root']
        total=0
        for case in master['analyses']:
            name=case['id'];ref=expected[name];version=case['edition'];assert version==ref['edition'].removeprefix('v')
            home=workflow/'analyses'/name;refhome=reference/name
            assert pin(refhome/'complete-original-records.json')==ref['complete_decoding'] and pin(refhome/'original.vcf')==ref['original_vcf']
            wanted=json.loads((refhome/'complete-original-records.json').read_bytes());header=json.loads((home/'native/header.json').read_bytes())
            source_lines=(refhome/'original.vcf').read_text(encoding='utf8').splitlines()
            assert [line for line in source_lines if line.startswith('##')]==wanted['metadata_lines']
            assert [line for line in source_lines if line.startswith('#CHROM\t')]==[wanted['column_line']]
            assert [line for line in source_lines if not line.startswith('#')]==[row['raw_line'] for row in wanted['records']]
            rows=[json.loads(line) for line in (home/'native/variants.jsonl').read_text(encoding='utf8').splitlines()]
            assert len(rows)==len(wanted['records'])==case['records']==ref['records']
            for number,(actual,expected_row) in enumerate(zip(rows,wanted['records']),1):
                assert {key:actual[key] for key in ['raw_line','columns','info','format','samples']}==expected_row,(name,'complete original record differs',number)
                assert actual['row']==number
                support='RE' if version=='1.0.7' else 'SUPPORT';assert actual['read_support_info_field']==support
                values=[field['value'] for field in expected_row['info'] if field['key']==support]
                assert actual['read_support']==(int(values[0]) if values else None)
            changes=[]
            assert len(header['metadata_lines'])==len(wanted['metadata_lines'])
            for previous,current in zip(wanted['metadata_lines'],header['metadata_lines']):
                if previous==current:continue
                if previous.startswith('##fileDate='):
                    expression=r'##fileDate="\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2}"'
                    assert re.fullmatch(expression,previous) and re.fullmatch(expression,current)
                    changes.append(dict(kind='original_source_run_timestamp',reference=previous,workflow=current))
                elif previous.startswith('##command='):
                    assert version!='1.0.7'
                    actual_args=json.loads((home/'original-request.json').read_bytes())['original_arguments']
                    expected_command='##command="'+runtime+'/sources/v'+version+'/src/sniffles/sniffles '+' '.join(actual_args)+'"'
                    assert current==expected_command,(name,'actual source command metadata differs from exact captured invocation')
                    source_command=json.loads((refhome/'command.json').read_bytes());source_command=source_command['command'] if isinstance(source_command,dict) else source_command
                    index=source_command.index('--input');source=source_command[index-1]
                    assert source.endswith('/sources/v'+version)
                    expected_reference='##command="'+source+'/src/sniffles/sniffles '+' '.join(source_command[index:])+'"'
                    assert previous==expected_reference,(name,'reference source command metadata differs from captured direct invocation')
                    changes.append(dict(kind='exact_recorded_original_invocations',reference=previous,workflow=current))
                else:raise AssertionError((name,'unexplained metadata difference',previous,current))
            if version=='1.0.7':
                declared=mapping[requests[name]['input']]
                assert wanted['samples']==[declared['original_path']] and header['samples']==[declared['captured_path']]
                assert wanted['column_line'].split('\t')[:9]==header['column_line'].split('\t')[:9]
                changes.append(dict(kind='explicit_legacy_captured_alignment_sample_label',original=wanted['samples'],captured=header['samples']))
            else:assert header['samples']==wanted['samples'] and header['column_line']==wanted['column_line']
            item=dict(id=name,edition=version,records=len(rows),every_raw_column_INFO_FORMAT_sample_value_matches=True,
                full_header_compared=True,explicit_original_run_metadata_changes=changes,
                reference_VCF=pin(refhome/'original.vcf'),workflow_original_VCF=pin(home/'original/calls.vcf'),native_rows=pin(home/'native/variants.jsonl'))
            state['cases'].append(item);total+=len(rows)
            assert pin(refhome/'complete-original-records.json')==ref['complete_decoding'] and pin(refhome/'original.vcf')==ref['original_vcf']
        assert before=={name:pin(ROOT/name) for name in before}
        state.update(status='PASS_COMPLETE_SNIFFLES_WORKFLOW_RECORDS_AGAINST_DIRECT_ORIGINAL_REFERENCES_ONLY',comparison_passed=True,
            complete_cases=len(state['cases']),complete_records=total,inputs=before,verified_current_frozen_data=True,
            independent_biological_accuracy_or_paper_reproduction=False,source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))})
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full original record/header comparison')
    raise SystemExit(main())
