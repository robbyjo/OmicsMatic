"""Actual graph normal-JAR no-result controls with the real R runtime on Linux."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,signal,subprocess,sys,time

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def live(pid):
    try:
        raw=Path('/proc',str(pid),'stat').read_text();fields=raw[raw.rindex(')')+2:].split();return fields[0]!='Z'
    except FileNotFoundError:return False
def wait_started(root,process):
    path=root/'capture/worker/PROCESS-STARTED.json';until=time.monotonic()+60
    while not path.is_file():
        if process.poll() is not None:raise RuntimeError('Java exited before owned R startup')
        if time.monotonic()>until:raise TimeoutError('Original R startup receipt observation exceeded60 seconds')
        time.sleep(.05)
    return json.loads(path.read_bytes())
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();assert sys.platform=='linux'
    ROOT=Path(__file__).resolve().parents[4];out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);jar=args.jar.resolve(strict=True);shutil.copyfile(__file__,out/'EXECUTED-refusals.py');runtime=ROOT.parent/'mo-2/build/monocle100-neighbors-1/pilot-inputs-1/runtime.json';results={}
    for name in ['timeout','cancel','java-parent-death','capture-cap','broken-console']:
        root=out/name;root.mkdir();shutil.copyfile(ROOT/'examples/monocle100-graph/complete-cds.rds',root/'cds.rds');configuration=json.loads(runtime.read_bytes());configuration['profile']='monocle100-original-graph-r43-v1';save(root/'runtime.json',configuration)
        request=dict(schema_version=1,source_cds='cds.rds',runtime='runtime.json',operation='RUN',use_partition=True,close_loop=False,learn_graph_control={'ncenter':5,'minimal_branch_len':2,'rann.k':6,'maxiter':100},convergence_policy='require',root_cells=['cell-001'],reduction_slot='UMAP',seed=100022,dataset_provenance='Complete synthetic original27-cell control; actual no-result '+name+' test',out='capture')
        if name=='timeout':request['timeout_seconds']=1
        if name=='capture-cap':request['maximum_output_bytes']=1024
        save(root/'request.json',request);command=['java','-Xmx2g','-cp',str(jar),'org.jlinalg.cli.Monocle100GraphCli','RUN','--request',str(root/'request.json')];save(root/'STARTED.json',dict(command=command))
        with (root/'stderr').open('xb') as stderr,(root/'stdout').open('xb') as stdout:
            process=subprocess.Popen(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE if name=='broken-console' else stdout,stderr=stderr)
            if name=='broken-console':process.stdout.close()
            group=None;supervisor=None
            try:
                if name in ['cancel','java-parent-death']:
                    record=wait_started(root,process);group=record['owned_process_group']
                    if name=='cancel':(root/'capture/CANCEL_REQUESTED').write_text('Owned real-R Java workflow cancellation test\n')
                    else:
                        pid=record['owner_java_pid'];raw=Path('/proc',str(pid),'stat').read_text();fields=raw[raw.rindex(')')+2:].split()
                        assert pid==process.pid and int(fields[19])==record['owner_java_start_ticks'] and os.fsencode(str(root/'request.json')) in Path('/proc',str(pid),'cmdline').read_bytes(),'Refuse to kill an unverified process'
                        supervisor=json.loads((root/'capture/worker/SUPERVISOR-STARTED.json').read_bytes())['pid'];os.kill(pid,signal.SIGKILL)
                code=process.wait(timeout=700)
                assert code!=0 if name=='java-parent-death' else code==2,(name,code)
                capture=root/'capture';assert not (capture/'COMPLETED.json').exists();analysis=json.loads((capture/'analysis.json').read_bytes());assert analysis['graph_admitted'] is False and analysis['pseudotime_admitted'] is False and analysis['scientific_test_admitted'] is False
                if name=='java-parent-death':
                    until=time.monotonic()+20
                    while not (capture/'worker/PROCESS.json').exists() and live(supervisor) and time.monotonic()<until:time.sleep(.05)
                report=json.loads((capture/'worker/PROCESS.json').read_bytes());assert not report['owned_live_after_cleanup']
                group=report['owned_process_group'];assert not live(group)
                if name=='broken-console':
                    assert report['status']=='PASS_PROCESS_ONLY' and (capture/'unadmitted-analysis.json').exists() and 'Console recording failed' in (capture/'FAILURE.txt').read_text()
                else:assert report['status']=='FAIL2_SUPERVISION'
                if name!='java-parent-death':
                    end=json.loads((capture/'worker/SUPERVISOR-END.json').read_bytes());assert end['alive_after_cleanup'] is False and not end['owned_live_after_cleanup']
                row=dict(status='PASS_ACTUAL_NORMAL_JAR_NO_RESULT_CONTROL',name=name,exit_code=code,no_complete_graph_admitted=True,owned_R_group_not_live=True,R_worker_process_status=report['status'],Java_SIGKILL_initial_false_admission_retained=name=='java-parent-death');save(root/'RESULT.json',row);results[name]=row;print('PASS actual Java/R',name,flush=True)
            finally:
                if process.poll() is None:process.terminate();
                try:process.wait(timeout=20)
                except subprocess.TimeoutExpired:process.kill();process.wait(timeout=10)
                # The owned supervisor handles its R session when this verified
                # Java parent exits. Never signal a stale, unverified process group.
    save(out/'RESULT.json',dict(status='PASS5_ACTUAL_NORMAL_JAR_GRAPH_REAL_R_NO_RESULT_CONTROLS',producer_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),controls=results,scientific_results_admitted=0));return 0
if __name__=='__main__':raise SystemExit(main())
