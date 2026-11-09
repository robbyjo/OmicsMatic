# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare a complete workflow to exact original references, including genotype provenance.

Every declared case and raw field is mandatory. Only explicitly checked source
command/date metadata and literal captured legacy sample paths may differ.
"""
import argparse,json,re,subprocess,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

STATUSES={'PASS_FOURTEEN_COMPLETE_ORIGINAL_SNIFFLES_REFERENCE_CONTROLS_ONLY',
    'PASS_ELEVEN_EXTENDED_ORIGINAL_SNIFFLES_REFERENCE_EXPERIMENTS_ONLY',
    'PASS_FIVE_ORIGINAL_SNIFFLES_OPTION_EXPERIMENTS_ONLY'}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--workflow',type=Path,required=True)
    parser.add_argument('--reference',type=Path,action='append',required=True);parser.add_argument('--artifact',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    base=ROOT/'build/literature-review/sniffles-runtime';out=a.out.resolve();assert not out.exists() and out.is_relative_to(base);out.mkdir(parents=True)
    state=dict(status='FAIL2_COMPLETE_SNIFFLES_ORIGINAL_REFERENCE_COMPARISON',comparison_passed=False,implementation_credit=False,whole_goal_complete=False,cases=[])
    try:
        workflow=a.workflow.resolve();artifact=a.artifact.resolve();master=json.loads((workflow/'COMPLETED.json').read_bytes())
        assert master['status']=='PASS_COMPLETE_ORIGINAL_SNIFFLES_AND_NATIVE_VCF_WORKFLOW' and master['analysis_admitted']
        protected=[workflow/'COMPLETED.json',artifact,Path(__file__).resolve()];expected={}
        for reference in a.reference:
            reference=reference.resolve();receipt=reference/'COMPLETED.json';original=json.loads(receipt.read_bytes());assert original['status'] in STATUSES;protected.append(receipt)
            for case in original['cases']:
                if not case.get('complete_decoding'):continue
                id=case.get('name',case.get('id'));assert id not in expected,'Ambiguous original reference case: '+id;expected[id]=(reference,case)
        ids=[case['id'] for case in master['analyses']];assert ids and len(ids)==len(set(ids)) and set(ids)<=set(expected)
        if len(a.reference)==1:assert set(ids)==set(expected),'Single-reference mode requires its entire complete scientific case population'
        before={path.relative_to(ROOT).as_posix():pin(path) for path in protected}
        command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-cp',str(artifact),'org.jlinalg.cli.SnifflesVerifyCli',
            '--result',str(workflow),'--out',str(out/'frozen-verification')]
        save(out/'verify-command.json',command);run=subprocess.run(command,capture_output=True,timeout=300)
        (out/'verify.stdout').write_bytes(run.stdout);(out/'verify.stderr').write_bytes(run.stderr);assert run.returncode==0,run.stderr.decode(errors='replace')
        verified=json.loads((out/'frozen-verification/COMPLETED.json').read_bytes());assert verified['verification_passed']
        request=json.loads((workflow/'request.original.json').read_bytes());declared={case['id']:case for case in request['analyses']}
        assert ids==[case['id'] for case in request['analyses']]
        mapping={item['id']:item for item in json.loads((workflow/'input-mapping.json').read_bytes())}
        runtime=json.loads((workflow/'runtime.original.json').read_bytes())['runtime_root'];total=0
        for case in master['analyses']:
            id=case['id'];reference,record=expected[id];old=reference/id;home=workflow/'analyses'/id;version=case['edition']
            assert version==record['edition'].removeprefix('v') and pin(old/'original.vcf')==record['original_vcf'] and pin(old/'complete-original-records.json')==record['complete_decoding']
            wanted=json.loads((old/'complete-original-records.json').read_bytes());header=json.loads((home/'native/header.json').read_bytes())
            raw=(old/'original.vcf').read_text(encoding='utf8').splitlines()
            assert [line for line in raw if line.startswith('##')]==wanted['metadata_lines']
            assert [line for line in raw if line.startswith('#CHROM\t')]==[wanted['column_line']]
            assert [line for line in raw if not line.startswith('#')]==[row['raw_line'] for row in wanted['records']]
            rows=[json.loads(line) for line in (home/'native/variants.jsonl').read_text(encoding='utf8').splitlines()]
            assert len(rows)==len(wanted['records'])==case['records']==record['records']
            for number,(actual,original) in enumerate(zip(rows,wanted['records']),1):
                assert actual['row']==number and {key:actual[key] for key in ['raw_line','columns','info','format','samples']}==original,(id,number,'complete original record differs')
                support='RE' if version=='1.0.7' else 'SUPPORT';values=[field['value'] for field in original['info'] if field['key']==support]
                assert actual['read_support_info_field']==support and actual['read_support']==(int(values[0]) if values else None)
            assert len(header['metadata_lines'])==len(wanted['metadata_lines']);changes=[]
            for previous,current in zip(wanted['metadata_lines'],header['metadata_lines']):
                if previous==current:continue
                key=previous.partition('=')[0];assert current.partition('=')[0]==key,'Metadata key/order changed'
                if key in ['##fileDate','##genotypeFileDate']:
                    expression=re.escape(key)+r'="\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2}"'
                    assert re.fullmatch(expression,previous) and re.fullmatch(expression,current)
                    changes.append(dict(kind='recorded_source_timestamp',key=key,reference=previous,workflow=current))
                elif key in ['##command','##genotypeCommand']:
                    assert version!='1.0.7';arguments=json.loads((home/'original-request.json').read_bytes())['original_arguments']
                    assert current==key+'="'+runtime+'/sources/v'+version+'/src/sniffles/sniffles '+' '.join(arguments)+'"'
                    original_command=json.loads((old/'command.json').read_bytes());original_command=original_command['command'] if isinstance(original_command,dict) else original_command
                    index=original_command.index('--input');source=original_command[index-1];assert source.endswith('/sources/v'+version)
                    assert previous==key+'="'+source+'/src/sniffles/sniffles '+' '.join(original_command[index:])+'"'
                    changes.append(dict(kind='exact_recorded_source_invocations',key=key,reference=previous,workflow=current))
                else:raise AssertionError((id,'unexplained metadata difference',previous,current))
            if version=='1.0.7':
                input=mapping[declared[id]['input']];assert wanted['samples']==[input['original_path']] and header['samples']==[input['captured_path']]
                assert wanted['column_line'].split('\t')[:9]==header['column_line'].split('\t')[:9]
                changes.append(dict(kind='explicit_legacy_captured_input_sample_path',reference=wanted['samples'],workflow=header['samples']))
            else:assert header['column_line']==wanted['column_line'] and header['samples']==wanted['samples']
            assert pin(old/'original.vcf')==record['original_vcf'] and pin(old/'complete-original-records.json')==record['complete_decoding']
            total+=len(rows);state['cases'].append(dict(id=id,edition=version,records=len(rows),every_raw_column_INFO_FORMAT_sample_value_matches=True,
                full_header_compared=True,explicit_source_metadata_changes=changes,original_vcf=pin(old/'original.vcf'),workflow_vcf=pin(home/'original/calls.vcf'),native_rows=pin(home/'native/variants.jsonl')))
        assert before=={name:pin(ROOT/name) for name in before}
        state.update(status='PASS_COMPLETE_SNIFFLES_ORIGINAL_RECORD_AND_HEADER_COMPARISONS_ONLY',comparison_passed=True,complete_cases=len(ids),complete_records=total,
            protected_inputs_unchanged=before,all_declared_workflow_cases_compared=True,unused_reference_cases=sorted(set(expected)-set(ids)),
            independent_biological_accuracy_or_paper_reproduction=False,source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))})
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable full-case/full-record/explicit metadata checks')
    raise SystemExit(main())
