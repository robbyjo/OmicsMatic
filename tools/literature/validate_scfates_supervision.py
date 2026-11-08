# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual source-worker timeout, sampled output capacity and owned cancellation."""
import argparse,copy,json,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def linux(path):return '/mnt/d/projects/JLinAlg/'+path.resolve().relative_to(ROOT).as_posix()
def main():
    p=argparse.ArgumentParser(description=__doc__)
    for name in ['build','fixtures','out']:p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--control',choices=['timeout','output-capacity','cancellation'],required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/scfates-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_SCFATES_SUPERVISION_CONTROL',control=a.control,implementation_credit=False,whole_goal_complete=False)
    try:
        build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};template=json.loads((a.fixtures/'requests/positive-batch.json').read_bytes());request=copy.deepcopy(template);request['analyses']=[copy.deepcopy(template['analyses'][-1] if a.control=='output-capacity' else template['analyses'][0])];request['out']=linux(out/'analysis');request['timeout_seconds']=1 if a.control=='timeout' else 120
        if a.control=='output-capacity':request['maximum_output_bytes']=2000000
        path=out/'request.json';save(path,request);command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-Xmx3g','-cp',linux(jar),'org.jlinalg.cli.ScfatesWorkflowCli','--request',linux(path)];save(out/'STARTED.json',dict(command=command,artifact=build['artifact']));process=None
        with (out/'stdout').open('xb') as stdout,(out/'stderr').open('xb') as stderr:
            process=subprocess.Popen(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
            if a.control=='cancellation':
                started=out/'analysis/worker/original.STARTED.json';deadline=time.monotonic()+300
                while not started.exists() and process.poll() is None and time.monotonic()<deadline:time.sleep(.1)
                assert started.is_file() and process.poll() is None,'Original worker did not start for owned cancellation';receipt=json.loads(started.read_bytes());pid=receipt['owned_process_group'];assert type(pid)is int and pid>1;probe=subprocess.run(['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/bin/kill','-0',str(pid)],capture_output=True,timeout=30);(out/'live-owned-worker.stdout').write_bytes(probe.stdout);(out/'live-owned-worker.stderr').write_bytes(probe.stderr);assert probe.returncode==0,'Owned worker was not live at cancellation check';state['confirmed_live_owned_process_group']=pid;marker=out/'analysis/worker/CANCEL_REQUESTED';assert not marker.exists();marker.write_text('Explicit validation cancellation of the confirmed live owned worker\n',encoding='utf8')
            code=process.wait(timeout=1200)
        state.update(command=command,exit_code=code);assert code==2,(code,(out/'stderr').read_bytes()[-3000:]);home=out/'analysis';admission=json.loads((home/'analysis-status.json').read_bytes());supervisor=json.loads((home/'worker/original.process.json').read_bytes());java=json.loads((home/'worker/java-process.json').read_bytes());assert admission['result_admitted'] is False and admission['status'].startswith('FAIL2') and supervisor['status']=='FAIL2_ORIGINAL_WORKFLOW_SUPERVISION' and not supervisor['owned_live_after_cleanup'] and not java['owned_live_after_cleanup'];cause={'timeout':'exceeded explicit timeout','output-capacity':'Complete sampled output capacity exceeded','cancellation':'Owned worker cancellation requested'}[a.control];assert cause in supervisor['error'];state.update(status='PASS_ACTUAL_SCFATES_'+a.control.upper().replace('-','_')+'_WHOLE_BATCH_REJECTION_ONLY',analysis_status=admission,supervision=supervisor,java_process=java,artifact=build['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())},analysis_admitted=False);assert all(pin(ROOT/n)==want for n,want in build['source_pins'].items())
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 2

if __name__=='__main__':raise SystemExit(main())
