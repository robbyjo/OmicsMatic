# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run actual wrapped and final-census cancellation controls on diagnostic candidates."""
import argparse,copy,json,subprocess,time,traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

BASE=ROOT/'build/literature-review/sniffles-runtime';DISTRO='JLinAlg-Omics-SAIGE-20261026'


def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:';return '/mnt/d/'+path.as_posix()[3:]


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    preview=BASE/'diagnostic-interface-preview-1';prepared=json.loads((preview/'COMPLETED.json').read_bytes())
    assert prepared['status']=='PASS_ISOLATED_SNIFFLES_DIAGNOSTIC_INTERFACE_BUILD_ONLY'
    binding=BASE/'runtime-binding-4/COMPLETED.json';template=BASE/'current-boundary-controls-2/tamper-during-final-census/request.json'
    state=dict(status='FAIL2_SNIFFLES_CAUSAL_CANCELLATION_CONTROLS',implementation_credit=False,whole_goal_complete=False,controls=[])
    before={path.relative_to(ROOT).as_posix():pin(path) for path in [Path(__file__).resolve(),binding,template,preview/'COMPLETED.json']}
    try:
        for name,compiler in [('after-caller-launch','javac21'),('during-final-census','javac25')]:
            home=out/name;home.mkdir();result=home/'run';data=copy.deepcopy(json.loads(template.read_bytes()))
            data['out']=linux(result);data['runtime']=dict(path=linux(binding),pin=pin(binding))
            source=home/'request.json';save(source,data);artifact=ROOT/prepared['artifacts'][compiler]['path']
            assert pin(artifact)=={key:prepared['artifacts'][compiler][key] for key in ['bytes','sha256']}
            command=['wsl','-d',DISTRO,'--exec','/usr/bin/java','-cp',linux(artifact),'org.jlinalg.cli.SnifflesWorkflowCli','--request',linux(source)]
            save(home/'command.json',command);child=None;triggered=False;started=time.monotonic()
            try:
                with (home/'stdout').open('xb') as stdout,(home/'stderr').open('xb') as stderr:
                    child=subprocess.Popen(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
                    save(home/'STARTED.json',dict(owned_wsl_launcher_pid=child.pid,command=command))
                    while child.poll() is None:
                        assert time.monotonic()-started<1800,'Controlled cancellation observation exceeded capacity; no test credit'
                        if name=='after-caller-launch':ready=(result/'processes/case-earlier/launch.json').is_file() and not (result/'processes/case-earlier/process.json').exists()
                        else:ready=(result/'host-after/ldd-1').is_dir()
                        if not triggered and ready:
                            if name=='during-final-census':
                                previous=json.loads((result/'analyses/earlier/PROVISIONAL.json').read_bytes());assert previous['records']==3 and not previous['analysis_admitted']
                            (result/'CANCEL_REQUESTED').write_text('Controlled '+name+' cancellation; preserve every output and admit no case\n',encoding='utf8',newline='\n')
                            save(home/'TRIGGER.json',dict(stage=name,artifact=pin(artifact),earlier_provisional_case_expected=name=='during-final-census'));triggered=True
                        time.sleep(.01)
                    assert child.returncode==2 and triggered,(name,child.returncode,triggered)
            finally:
                if child is not None and child.poll() is None:
                    if result.is_dir() and not (result/'CANCEL_REQUESTED').exists():(result/'CANCEL_REQUESTED').write_text('Cancellation harness interrupted; no result admitted\n',encoding='utf8')
                    try:child.wait(timeout=240)
                    except subprocess.TimeoutExpired:
                        save(home/'CLEANUP-REQUIRES-ATTENTION.json',dict(owned_wsl_launcher_pid=child.pid,command=command,analysis_admitted=False))
                        raise RuntimeError('Owned diagnostic-test JVM has not returned after cancellation; inspect its exact retained command and output before continuing')
            record=json.loads((result/'COMPLETED.json').read_bytes())
            assert record['status'].startswith('FAIL2_') and not record['analysis_admitted'] and 'Cancellation requested' in record['error']
            assert 'Cancellation requested' in (result/'WARNINGS.md').read_text(encoding='utf8') and b'Cancellation requested' in (home/'stderr').read_bytes()
            previous=record['earlier_cases_remain_provisional_unless_complete_batch_passes'];assert all(not row['analysis_admitted'] for row in previous)
            if name=='after-caller-launch':assert 'CAUSE: ' in record['error'],'The actual wrapped cancellation reason was not preserved'
            else:assert len(previous)==1
            ledgers=[]
            for path in result.rglob('process.json'):
                process=json.loads(path.read_bytes());assert 'owned_live_after_cleanup' in process and not process['owned_live_after_cleanup'] and 'cleanup_state' not in process
                ledgers.append(dict(path=path.relative_to(result).as_posix(),pin=pin(path)))
            state['controls'].append(dict(id=name,compiler=compiler,artifact=pin(artifact),exit_code=child.returncode,receipt=pin(result/'COMPLETED.json'),
                actual_error=record['error'],retained_unadmitted_cases=len(previous),all_owned_process_cleanup_fields_present_and_empty=True,process_ledgers=ledgers))
            print('PASS actual '+name+' cancellation; cause visible; all cases unadmitted and owned cleanup terminal',flush=True)
        assert before=={name:pin(ROOT/name) for name in before}
        state.update(status='PASS_BOTH_REAL_SNIFFLES_CANCELLATION_DIAGNOSTIC_CONTROLS_ONLY',protected_inputs_unchanged=before,positive_calling_comparisons_still_separate=True)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Actual cancellation rejected admission, preserved reasons and left no live owned workers.')+
        '\n\nACTION: these are controlled failed/cancelled analyses, not admitted SV results. Keep every original diagnostic and partial output. An interrupted or failed suite grants no coverage credit.\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable causal cancellation/admission/cleanup checks')
    raise SystemExit(main())
