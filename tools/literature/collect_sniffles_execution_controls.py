# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Combine explicit failure receipts without relabeling failed or obsolete suites."""
import argparse,json,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/sniffles-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNIFFLES_COMBINED_FAILURE_CONTROL_AUDIT',implementation_credit=False,whole_goal_complete=False)
    try:
        def doc(path):return json.loads(path.read_bytes())
        def unadmitted(result,provisionals=None):
            value=doc(result/'COMPLETED.json');assert value['status'].startswith('FAIL2_') and not value['analysis_admitted']
            earlier=value['earlier_cases_remain_provisional_unless_complete_batch_passes']
            assert all(not row['analysis_admitted'] for row in earlier)
            if provisionals is not None:assert len(earlier)==provisionals
            assert b'ACTION' in (result/'WARNINGS.md').read_bytes();ledgers={}
            for path in result.rglob('process.json'):
                row=doc(path);assert 'owned_live_after_cleanup' in row and not row['owned_live_after_cleanup'] and 'cleanup_state' not in row
                ledgers[path.relative_to(result).as_posix()]=pin(path)
            return dict(path=result.relative_to(ROOT).as_posix(),receipt=pin(result/'COMPLETED.json'),error=value['error'],
                retained_unadmitted_cases=len(earlier),process_ledgers=ledgers,all_owned_cleanup_fields_present_and_empty=True)
        boundary=BASE/'current-boundary-controls-2';earlier=doc(boundary/'COMPLETED.json')
        assert earlier['status']=='FAIL2_CURRENT_SNIFFLES_WORKFLOW_BOUNDARIES' and earlier['error']=='AssertionError()'
        assert len(earlier['preflight'])==5 and len(earlier['late_controls'])==2
        expected={'missing-binding':3,'changed-binding-pin':3,'missing-bound-edition':3,'legacy-phase-unavailable':3,'arbitrary-command-rejected':2}
        assert {row['id']:row['observed_exit_code'] for row in earlier['preflight']}==expected
        for row in earlier['preflight']:
            assert row['expected_exit_code']==row['observed_exit_code'] and row['no_original_process_launched']
            result=boundary/row['id']/'run'
            assert not(result/'host-before').exists() and not(result/'processes').exists()
        fatal=unadmitted(boundary/'source-fatal-after-earlier-case/run',1)
        assert 'diagnostic rejects admission despite exit0' in fatal['error']
        failed=boundary/'source-fatal-after-earlier-case/run'
        assert doc(failed/'processes/case-real-zero-exit-fatal/process.json')['exit_code']==0
        assert (failed/'analyses/real-zero-exit-fatal/original/calls.vcf').read_bytes()==b''
        assert not(failed/'analyses/real-zero-exit-fatal/native/variants.jsonl').exists()
        tamper=unadmitted(boundary/'tamper-during-final-census/run',1)
        assert 'Previously bound input, request, helper or completed output changed' in tamper['error']
        old_cancel=unadmitted(boundary/'cancel-during-final-census/run',1)
        assert 'Cancellation requested' not in old_cancel['error']
        assert doc(boundary/'cancel-during-final-census/TRIGGER.json')['all_case_data_still_provisional']
        corrected=BASE/'cancellation-diagnostic-controls-1';causes=doc(corrected/'COMPLETED.json')
        assert causes['status']=='PASS_BOTH_REAL_SNIFFLES_CANCELLATION_DIAGNOSTIC_CONTROLS_ONLY'
        assert {row['id'] for row in causes['controls']}=={'after-caller-launch','during-final-census'}
        new_cancel=[]
        for row in causes['controls']:
            actual=unadmitted(corrected/row['id']/'run',row['retained_unadmitted_cases'])
            assert 'Cancellation requested' in actual['error'] and actual['receipt']==row['receipt'] and row['exit_code']==2
            if row['id']=='after-caller-launch':assert 'CAUSE: ' in actual['error']
            new_cancel.append(actual)
        option=doc(BASE/'option-interface-validation-1/COMPLETED.json')
        assert option['status']=='FAIL2_ISOLATED_SNIFFLES_OPTION_INTERFACE_VALIDATION' and 'unexpected source header difference' in option['error']
        assert len(option['refusals'])==2 and all(row['exit_code']==3 for row in option['refusals'])
        for label in ['workflow-current-reference-comparison-1','option-interface-validation-1']:
            assert doc(BASE/label/'COMPLETED.json')['status'].startswith('FAIL')
        old=unadmitted(BASE/'workflow-extended-1',10);intent=doc(BASE/'obsolete-format-cancellation-1/INTENT.json')
        assert intent['status']=='REQUESTED_OWNED_SUPERSEDED_FORMAT_BATCH_CANCELLATION' and not intent['analysis_admitted']
        assert len(intent['original_VCF_pins'])==intent['provisional_cases']==10
        for name,value in intent['original_VCF_pins'].items():assert pin(BASE/'workflow-extended-1/analyses'/name/'original/calls.vcf')==value
        refinement=doc(BASE/'obsolete-refinement-cancellation-2/INTENT.json');obsolete=[old]
        assert not refinement['analysis_admitted'] and len(refinement['outputs'])==2
        for row in refinement['outputs']:obsolete.append(unadmitted(ROOT/row['path'],row['provisional_cases']))
        proofs=['option-interface-method-comparison-3','diagnostic-interface-method-comparison-1']
        expected_status=['PASS_BOTH_OPTION_INTERFACE_EXECUTION_ADMISSION_PATH_COMPARISONS_ONLY','PASS_BOTH_CAUSAL_DIAGNOSTIC_ONLY_BYTECODE_COMPARISONS']
        for name,status in zip(proofs,expected_status):
            value=doc(BASE/name/'COMPLETED.json');assert value['status']==status and len(value['comparisons'])==2
            assert all(pin(ROOT/path)==wanted for path,wanted in value['source_pins'].items())
        state.update(status='PASS_EXPLICIT_SNIFFLES_FAILURE_AND_SUPERSESSION_RECEIPT_AUDIT_ONLY',
            prior_boundary_suite_status_unchanged=earlier['status'],prior_diagnostic_bug_retained=old_cancel,
            prior_option_suite_status_unchanged=option['status'],header_comparator_failure_retained=True,
            five_actual_preflight_controls=earlier['preflight'],two_wrong_edition_preflight_controls=option['refusals'],
            original_fatal_after_earlier_case=fatal,final_census_data_mutation=tamper,
            corrected_actual_cancellations=new_cancel,superseded_failed_attempts=obsolete,
            ten_superseded_original_VCF_pins_unchanged=intent['original_VCF_pins'],
            source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},
            scope='Read-only explicit receipt audit. Failed harnesses remain failed. Earlier scientific/failure paths are related to the corrected candidate by separately retained method/class byte comparisons. Actual corrected cancellation controls are separate executions. No independent caller/biological certification.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable no-admission or historical-failure checks')
    raise SystemExit(main())
