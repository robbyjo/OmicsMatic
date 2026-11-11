# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise current typed preflight and real fatal/tamper/cancel batch boundaries."""
import argparse,copy,hashlib,json,subprocess,time,traceback
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/sniffles-runtime';DISTRO='JLinAlg-Omics-SAIGE-20261026'


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:';return '/mnt/d/'+path.as_posix()[3:]
def local(path):
    assert path.startswith('/mnt/d/');return Path('D:/'+path.removeprefix('/mnt/d/'))


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--artifact',type=Path,default=BASE/'execution-build-14/sniffles-execution-development.jar');a=parser.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    artifact=a.artifact.resolve();base_request=BASE/'workflow-current-request-1/request.json'
    original= json.loads(base_request.read_bytes());bindings=BASE/'runtime-binding-4/COMPLETED.json'
    state=dict(status='FAIL2_CURRENT_SNIFFLES_WORKFLOW_BOUNDARIES',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,preflight=[],late_controls=[])
    protected=[Path(__file__).resolve(),artifact,base_request,bindings,ROOT/'src/main/java/org/jlinalg/cli/SnifflesWorkflowCli.java',ROOT/'build/cli/omicsmatic-0.3.7.jar']
    for item in original['inputs']:protected.append(local(item['path']))
    before={p.relative_to(ROOT).as_posix():pin(p) for p in protected}
    def request(home,analyses):
        data=copy.deepcopy(original);data['out']=linux(home/'run');data['analyses']=analyses
        ids={case['input'] for case in analyses}|{case[key] for case in analyses for key in ['tandem_repeats','genotype_vcf'] if key in case}
        for item in data['inputs']:
            if item['id'] in ids and item.get('reference'):ids.add(item['reference'])
        data['inputs']=[item for item in data['inputs'] if item['id'] in ids];return data
    positive=dict(id='earlier',edition='2.4',input='sampleA',threads=1)
    def command(source):return ['wsl','-d',DISTRO,'--exec','/usr/bin/java','-cp',linux(artifact),'org.jlinalg.cli.SnifflesWorkflowCli','--request',linux(source)]
    try:
        built=json.loads((artifact.parent/'COMPLETED.json').read_bytes())
        assert built['status']=='PASS_ISOLATED_JAVA17_SNIFFLES_EXECUTION_BUILD_ONLY' and pin(artifact)=={key:built['artifact'][key] for key in ['bytes','sha256']}
        assert all(pin(ROOT/name)==value for name,value in built['source_pins'].items()),'The tested artifact no longer matches current source; use a matching build snapshot'
        binding=json.loads(bindings.read_bytes());assert binding['status']=='PASS_BOUND_ORIGINAL_SNIFFLES_RUNTIME_ONLY' and not binding['analysis_admitted'] and not binding['owned_live_after_cleanup']
        for name,want in [('missing-binding',3),('changed-binding-pin',3),('missing-bound-edition',3),('legacy-phase-unavailable',3),('arbitrary-command-rejected',2)]:
            home=out/name;home.mkdir();data=request(home,[copy.deepcopy(positive)])
            if name in ['missing-binding','changed-binding-pin','missing-bound-edition']:
                fake=home/'binding';fake.mkdir();manifest=fake/'COMPLETED.json';data['runtime']=dict(path=linux(manifest),pin=pin(bindings))
                if name=='changed-binding-pin':manifest.write_bytes(bindings.read_bytes()+b'\n')
                elif name=='missing-bound-edition':
                    modified=copy.deepcopy(binding);del modified['versions']['2.4'];save(manifest,modified);data['runtime']['pin']=pin(manifest)
            elif name=='legacy-phase-unavailable':data['analyses'][0].update(edition='1.0.7',phase=True)
            else:data['command']=['forbidden arbitrary command must never execute']
            source=home/'request.json';save(source,data);cmd=command(source);save(home/'command.json',cmd)
            run=subprocess.run(cmd,capture_output=True,timeout=120);(home/'stdout').write_bytes(run.stdout);(home/'stderr').write_bytes(run.stderr)
            assert run.returncode==want and b'ACTION' in run.stderr,(name,run.returncode,run.stderr.decode(errors='replace'))
            result=home/'run';receipt=None
            if result.exists():
                record=json.loads((result/'COMPLETED.json').read_bytes());assert not record['analysis_admitted']
                assert not (result/'host-before').exists() and not (result/'processes').exists(),'Preflight refusal launched native/Python work'
                assert b'ACTION' in (result/'WARNINGS.md').read_bytes();receipt=pin(result/'COMPLETED.json')
            state['preflight'].append(dict(id=name,expected_exit_code=want,observed_exit_code=run.returncode,receipt=receipt,no_original_process_launched=True))
            print('PASS current preflight '+name,flush=True)
        for name in ['source-fatal-after-earlier-case','tamper-during-final-census','cancel-during-final-census']:
            home=out/name;home.mkdir()
            if name.startswith('source-fatal'):
                data=json.loads((BASE/'workflow-fatal-request-1/request.json').read_bytes());data['out']=linux(home/'run')
            else:data=request(home,[copy.deepcopy(positive)])
            source=home/'request.json';save(source,data);cmd=command(source);save(home/'command.json',cmd)
            child=None;triggered=False;started=time.monotonic();result=home/'run';timed_out=False
            try:
                with (home/'stdout').open('xb') as stdout,(home/'stderr').open('xb') as stderr:
                    child=subprocess.Popen(cmd,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,cwd=ROOT)
                    save(home/'STARTED.json',dict(owned_wsl_launcher_pid=child.pid,command=cmd))
                    while child.poll() is None:
                        if time.monotonic()-started>1800:
                            timed_out=True
                            if result.is_dir() and not (result/'CANCEL_REQUESTED').exists():(result/'CANCEL_REQUESTED').write_text('Controlled test timeout; no coverage credit\n',encoding='utf8')
                            raise TimeoutError('Whole boundary experiment exceeded observation capacity; cancellation requested, never infer the expected failure')
                        if not triggered and name!='source-fatal-after-earlier-case' and (result/'host-after/ldd-1').is_dir():
                            report=json.loads((result/'analyses/earlier/PROVISIONAL.json').read_bytes());assert not report['analysis_admitted'] and report['records']==3
                            if name.startswith('tamper'):
                                target=result/'analyses/earlier/native/variants.jsonl';assert target.resolve().is_relative_to(result.resolve())
                                raw=target.read_bytes();(home/'variant-before.jsonl').write_bytes(raw);rows=[json.loads(line) for line in raw.splitlines()]
                                assert rows[0]['read_support']==20;rows[0]['read_support']=999
                                target.write_text('\n'.join(json.dumps(row,separators=(',',':')) for row in rows)+'\n',encoding='utf8',newline='\n')
                                save(home/'TRIGGER.json',dict(stage='final-library-census-started',target=target.relative_to(result).as_posix(),before=pin(home/'variant-before.jsonl'),after=pin(target),only_controlled_copy_changed=True))
                            else:
                                (result/'CANCEL_REQUESTED').write_text('Controlled cancellation during final library census after complete provisional case\n',encoding='utf8',newline='\n')
                                save(home/'TRIGGER.json',dict(stage='final-library-census-started',all_case_data_still_provisional=True))
                            triggered=True
                        time.sleep(.025)
                    assert child.returncode==2,(name,child.returncode)
            finally:
                if child is not None and child.poll() is None:
                    if result.is_dir() and not (result/'CANCEL_REQUESTED').exists():(result/'CANCEL_REQUESTED').write_text('Boundary harness interrupted; preserve all outputs and admit none\n',encoding='utf8')
                    try:child.wait(timeout=240)
                    except subprocess.TimeoutExpired:
                        save(home/'CLEANUP-REQUIRES-ATTENTION.json',dict(owned_wsl_launcher_pid=child.pid,known_command=cmd,analysis_admitted=False))
                        raise RuntimeError('Owned boundary JVM has not returned after cancellation; inspect exact command/PID and retained output before further analysis')
            assert not timed_out
            record=json.loads((result/'COMPLETED.json').read_bytes());assert record['status'].startswith('FAIL2_') and not record['analysis_admitted']
            cases=record['earlier_cases_remain_provisional_unless_complete_batch_passes'];assert len(cases)==1 and all(not case['analysis_admitted'] for case in cases)
            assert b'ACTION' in (result/'WARNINGS.md').read_bytes()
            if name.startswith('source-fatal'):
                assert 'diagnostic rejects admission despite exit0' in record['error']
                failed=result/'processes/case-real-zero-exit-fatal/process.json';process=json.loads(failed.read_bytes());assert process['exit_code']==0 and not process['owned_live_after_cleanup']
                assert (result/'analyses/real-zero-exit-fatal/original/calls.vcf').stat().st_size==0
                assert (result/'analyses/real-zero-exit-fatal/native/variants.jsonl').exists() is False
            elif name.startswith('tamper'):
                assert triggered and 'Previously bound input, request, helper or completed output changed' in record['error']
            else:assert triggered and 'Cancellation requested' in record['error']
            ledgers=[]
            for path in result.rglob('process.json'):
                process=json.loads(path.read_bytes());assert 'owned_live_after_cleanup' in process and not process['owned_live_after_cleanup'] and 'cleanup_state' not in process
                ledgers.append(dict(path=path.relative_to(result).as_posix(),pin=pin(path)))
            state['late_controls'].append(dict(id=name,observed_exit_code=child.returncode,receipt=pin(result/'COMPLETED.json'),earlier_cases_unadmitted=1,
                trigger_observed=triggered,process_ledgers=ledgers,elapsed_seconds=time.monotonic()-started))
            print('PASS real current batch boundary '+name+'; earlier case unadmitted and owned workers terminal',flush=True)
        assert before=={name:pin(ROOT/name) for name in before}
        state.update(status='PASS_CURRENT_SNIFFLES_FIVE_PREFLIGHT_AND_THREE_REAL_LATE_FAILURE_CONTROLS_ONLY',
            protected_inputs_and_sources_unchanged=before,current_artifact=pin(artifact),fresh_original_binding=pin(bindings),
            scientific_positive_workflow_comparison_still_required=True)
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''),flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable current artifact/owned-workflow failure controls')
    raise SystemExit(main())
