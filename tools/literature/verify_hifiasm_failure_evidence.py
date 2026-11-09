# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently audit all ten terminal real failure controls, retaining a failed harness's top-level-cause assumption."""
import argparse,json,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','executions','out']:p.add_argument('--'+name,type=Path,required=True)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_FAILURE_EVIDENCE_AUDIT',cases={},analysis_admitted=False,implementation_credit=False,earlier_harness_relabelled=False)
    try:
        execution=a.executions.resolve();terminal=json.loads((execution/'COMPLETED.json').read_bytes());assert terminal['status']=='FAIL2_HIFIASM_FAILURE_CONTROLS' and 'AssertionError' in terminal['error'];build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};names=['windows21-skip','windows25-skip','missing-runtime','legacy-hic-skip','timeout','output-capacity','cancellation','late-original-failure','late-graph-mutation','late-file-addition'];allpins={}
        for name in names:
            home=execution/name;analysis=home/'analysis';process=json.loads((home/'process.json').read_bytes());report=json.loads((analysis/'COMPLETED.json').read_bytes());code=3 if name.endswith('-skip') or name=='missing-runtime' else 2;assert process['exit_code']==code and report['status'].startswith('SKIP3_' if code==3 else 'FAIL2_') and not report['analysis_admitted'];assert 'org.jlinalg.cli.HifiasmWorkflowCli' in process['command'] and str(jar) in process['command'] or 'org.jlinalg.cli.HifiasmWorkflowCli' in process['command'] and '/mnt/d/projects/JLinAlg/'+jar.relative_to(ROOT).as_posix() in process['command'];assert not (analysis/'result-files.json').exists();assert 'ACTION REQUIRED:' in (analysis/'WARNINGS.md').read_text();records=[]
            for file in (analysis/'analyses').glob('*/process/process.json') if (analysis/'analyses').exists() else []:
                receipt=json.loads(file.read_bytes());assert not receipt['owned_live_after_cleanup'];records.append(receipt)
            if code==3:assert not records and not (analysis/'analyses').exists()
            if name.startswith('late-'):assert report['earlier_cases_are_provisional_unless_entire_receipt_passes'] and all(not r['analysis_admitted'] for r in report['earlier_cases_are_provisional_unless_entire_receipt_passes'])
            diagnostic=(home/'original.stderr').read_text()
            if name=='timeout':assert 'wall-time capacity exceeded' in diagnostic and records
            if name=='output-capacity':assert 'exit 153' in diagnostic and records
            if name=='cancellation':assert (analysis/'CANCEL_REQUESTED').is_file() and 'cancelled' in diagnostic and records
            if name=='late-original-failure':assert 'no nonempty sequence-bearing GFA' in diagnostic and len(records)==2 and all(r['exit_code']==0 for r in records)
            if name in ['late-graph-mutation','late-file-addition']:
                mutation=json.loads((home/'mutation.json').read_bytes());assert mutation['kind']==name and mutation['second_process_launch']['owned_launcher_pid']>0 and len(records)==2 and all(r['exit_code']==0 for r in records);assert ('Previously bound' in diagnostic and 'changed during assembly:' in diagnostic) if name=='late-graph-mutation' else 'Previously completed evidence population changed:' in diagnostic;target=ROOT/mutation['path'];assert pin(target)==mutation.get('after',mutation.get('pin'))
            request=json.loads((home/'request.json').read_bytes())
            for record in request['inputs'].values():
                raw=record['path'];path=ROOT/raw.removeprefix('/mnt/d/projects/JLinAlg/') if raw.startswith('/mnt/d/projects/JLinAlg/') else Path(raw);assert pin(path)=={k:record[k] for k in ['bytes','sha256']}
            state['cases'][name]=dict(exit_code=code,status=report['status'],owned_process_receipts=len(records),earlier_provisional_cases=len(report['earlier_cases_are_provisional_unless_entire_receipt_passes']),named_nested_cause_checked=name in ['late-graph-mutation','late-file-addition'])
        for file in execution.rglob('*'):
            if file.is_file():allpins[file.relative_to(ROOT).as_posix()]=pin(file)
        assert len(state['cases'])==10 and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'retained-files.json',allpins);state.update(status='passed-ten-terminal-real-hifiasm-skip-owned-failure-and-atomic-admission-controls',artifact=build['artifact'],retained_files=pin(out/'retained-files.json'),original_harness_error=terminal['error'],interpretation='The original harness expected the changed-file cause at the top-level error string. The strict RSEM process primitive wraps it. This NEW read-only audit checks the actual nested Java cause, complete original receipts, injected action, unchanged raw inputs, clean owned processes and whole-batch no-admission. No earlier report or scientific hash was refreshed.',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete actual failure evidence checks')
    raise SystemExit(main())
