# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check data-only original pre/post clipping, identities and error dispositions; no independent trajectory fit is claimed."""
import argparse,json,math,struct,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();reference=a.reference.resolve();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_PALANTIR_SOURCE_OBSERVATION_AUDIT',cases=[],implementation_credit=False,whole_goal_complete=False,independent_trajectory_refit=False)
    try:
        report=json.loads((reference/'COMPLETED.json').read_bytes());assert report['status']=='PASS_TWO_FULL_ORIGINAL_PALANTIR_WORKFLOW_DISCOVERY_FAMILIES_ONLY' and report['all_runtime_trees_unchanged'];files=[Path(__file__).resolve(),*[p for p in reference.rglob('*') if p.is_file() and not any(n.startswith('private-') for n in p.parts)]];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files};changed=0;rows_below_one=0;numeric=0;errors=[]
        for edition in ['1.4.4','1.4.6']:
            original=report['reports'][edition];assert original['successful_original_cases']==(7 if edition=='1.4.4' else 8) and original['expected_original_errors']==3 and original['partial_identifier_dispositions']==1 and original['source_warning_filters_preserved']
            for case in original['cases']:
                home=reference/edition/case['id'];assert (home/'global-rng-before.json').is_file() and (home/'global-rng-after.json').is_file()
                if case['status']=='original-error':
                    assert case['error_type']==('ValueError' if case['id']=='source-missing-early-cell' else 'KeyError');errors.append(dict(edition=edition,id=case['id'],type=case['error_type']));continue
                before=json.loads((home/'observed-before-result-clipping.json').read_bytes());after=json.loads((home/'returned.json').read_bytes());assert before['pseudotime']==after['pseudotime'] and before['entropy']==after['entropy'];pre=before['branch_probs'];post=after['branch_probs'];assert pre['index']==post['index'] and pre['values']['shape']==post['values']['shape'];shape=pre['values']['shape'];width=shape[1];assert shape[0]==case['cells']==96
                expected_columns=[{'cell-047':'branch-A','cell-095':'branch-B'}[v] for v in pre['columns']] if case['id']=='anndata-annotated-terminal-output' else pre['columns'];assert post['columns']==expected_columns;left=pre['values']['float64_bits'];right=post['values']['float64_bits'];assert len(left)==len(right)==shape[0]*width;altered=0;decoded=[]
                for first,second in zip(left,right,strict=True):
                    value=struct.unpack('>d',bytes.fromhex(first))[0];expected='0000000000000000' if value<.01 else first;assert second==expected,'Original result clipping differs';altered+=first!=second;numeric+=1;decoded.append(struct.unpack('>d',bytes.fromhex(second))[0])
                for i in range(shape[0]):
                    row=decoded[i*width:(i+1)*width]
                    if width and all(math.isfinite(v) for v in row) and sum(row)<1-1e-10:rows_below_one+=1
                changed+=altered;state['cases'].append(dict(edition=edition,id=case['id'],values_checked=len(left),clipped_probability_states=altered,entropy_before_clipping_retained=True,cell_axis_preserved=True,source_terminal_annotations_preserved=True))
        assert len(state['cases'])==15 and len(errors)==6 and changed>0 and rows_below_one>0 and all(pin(ROOT/n)==v for n,v in pins.items());state.update(status='PASS_COMPLETE_PALANTIR_PRE_POST_CLIPPING_AND_ERROR_OBSERVATIONS_ONLY',numeric_probability_states=numeric,clipped_probability_states=changed,returned_rows_below_unit_mass=rows_below_one,original_errors=errors,source_pins=pins)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
