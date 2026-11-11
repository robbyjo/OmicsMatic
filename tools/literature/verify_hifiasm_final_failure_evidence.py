# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Audit ten retained terminal controls plus the single retried observer action; preserve both original suite receipts."""
import argparse,json,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','original','retry','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_FINAL_FAILURE_EVIDENCE',cases={},analysis_admitted=False,implementation_credit=False,earlier_suite_relabelled=False)
    try:
        original=a.original.resolve();retry=a.retry.resolve();first=json.loads((original/'COMPLETED.json').read_bytes());second=json.loads((retry/'COMPLETED.json').read_bytes());assert first['status']=='FAIL2_HIFIASM_FAILURE_CONTROLS' and 'JSONDecodeError' in first['error'] and second['status']=='passed-selected-real-hifiasm-owned-failure-control' and set(second['cases'])=={'late-file-addition'};built=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/built['artifact']['path'];assert pin(jar)=={k:built['artifact'][k] for k in ['bytes','sha256']};names=['windows21-skip','windows25-skip','missing-runtime','legacy-hic-skip','timeout','output-capacity','cancellation','late-original-failure','late-graph-mutation','late-file-addition','final-verification-cancellation']
        for name in names:
            home=(retry if name=='late-file-addition' else original)/name;analysis=home/'analysis';receipt=json.loads((home/'process.json').read_bytes());report=json.loads((analysis/'COMPLETED.json').read_bytes());code=3 if name.endswith('-skip') or name=='missing-runtime' else 2;assert receipt['exit_code']==code and report['status'].startswith('SKIP3_' if code==3 else 'FAIL2_') and not report['analysis_admitted'] and not (analysis/'result-files.json').exists();expected_jar=str(jar) if name.startswith('windows') else '/mnt/d/projects/JLinAlg/'+jar.relative_to(ROOT).as_posix();assert expected_jar in receipt['command'];warnings=(analysis/'WARNINGS.md').read_text();assert 'ACTION REQUIRED:' in warnings and report['error_details'] and (analysis/'error.traceback.txt').is_file();all_owned=[];source_owned=[]
            for file in analysis.rglob('process.json'):
                process=json.loads(file.read_bytes());assert not process['owned_live_after_cleanup'];all_owned.append(process)
                if file.parent.parent.parent.name=='analyses':source_owned.append(process)
            if code==3:assert not (analysis/'analyses').exists()
            if name.startswith('late-') or name=='final-verification-cancellation':assert report['earlier_cases_are_provisional_unless_entire_receipt_passes'] and all(not c['analysis_admitted'] for c in report['earlier_cases_are_provisional_unless_entire_receipt_passes'])
            if name=='late-graph-mutation':assert 'changed during assembly:' in warnings and (home/'mutation.json').is_file()
            if name=='late-file-addition':assert 'population changed:' in warnings and (home/'mutation.json').is_file()
            if name=='final-verification-cancellation':assert (analysis/'CANCEL_REQUESTED').is_file() and (analysis/'host-after/ldd-1/launch.json').is_file() and 'Cancellation requested' in warnings
            request=json.loads((home/'request.json').read_bytes())
            for record in request['inputs'].values():
                raw=record['path'];file=ROOT/raw.removeprefix('/mnt/d/projects/JLinAlg/') if raw.startswith('/mnt/d/projects/JLinAlg/') else Path(raw);assert pin(file)=={k:record[k] for k in ['bytes','sha256']}
            state['cases'][name]=dict(root=home.relative_to(ROOT).as_posix(),exit_code=code,status=report['status'],all_owned_process_receipts=len(all_owned),earlier_provisional_cases=len(report['earlier_cases_are_provisional_unless_entire_receipt_passes']),source='selected-stable-observer-retry' if name=='late-file-addition' else 'retained-terminal-control')
        unused=original/'late-file-addition';assert not (unused/'process.json').exists() and not (unused/'mutation.json').exists() and json.loads((unused/'analysis/COMPLETED.json').read_bytes())['analysis_admitted'];assert len(state['cases'])==11 and all(pin(ROOT/n)==v for n,v in built['source_pins'].items());retained={file.relative_to(ROOT).as_posix():pin(file) for folder in [original,retry] for file in folder.rglob('*') if file.is_file()};save(out/'retained-files.json',retained);state.update(status='passed-eleven-source-bound-hifiasm-skip-owned-failure-and-final-admission-controls',artifact=built['artifact'],retained_files=pin(out/'retained-files.json'),unobserved_old_action_excluded=True,old_uninjected_scientific_run_preserved=True,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual source/cleanup/admission checks')
    raise SystemExit(main())
