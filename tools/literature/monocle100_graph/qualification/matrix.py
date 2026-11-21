"""Execute every declared original graph reference case; retain all outcomes."""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,shutil,subprocess,time

ROOT=Path(__file__).resolve().parents[4]
JAVA21=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe')

def mount(path):return '/mnt/d/'+path.resolve().relative_to(Path('D:/')).as_posix()
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
def producer(jar,classes):
    result={'jar':dict(path=str(jar),**pin(jar.read_bytes())),'quick_compiled_only':classes is not None}
    if classes:result['classes']={p.relative_to(classes).as_posix():pin(p.read_bytes()) for p in sorted(classes.rglob('*.class'))}
    result['resources']={p.relative_to(ROOT).as_posix():pin(p.read_bytes()) for p in sorted((ROOT/'src/main/resources/org/jlinalg/omics/monocle100-graph').rglob('*')) if p.is_file()}
    p=ROOT/'src/main/resources/org/jlinalg/omics/monocle100-neighbors/supervisor.py';result['resources'][p.relative_to(ROOT).as_posix()]=pin(p.read_bytes());return result

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--reference',type=Path,required=True);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--classes',type=Path);parser.add_argument('--workers',type=int,default=2);args=parser.parse_args()
    out=args.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(parents=True,exist_ok=False);reference=args.reference.resolve();jar=args.jar.resolve();classes=args.classes.resolve() if args.classes else None;assert 1<=args.workers<=2
    expected=json.loads((reference/'RESULT.json').read_bytes());plan=json.loads((reference/'PLAN.json').read_bytes());assert expected['status']=='PASS_UNCHANGED_ORIGINAL100_GRAPH_REFERENCE_CASES' and 0<len(plan['cases'])<=256 and {case['name'] for case in plan['cases']}==set(expected['cases'])
    save(out/'ORIGINAL-PLAN.json',plan);save(out/'ORIGINAL-RESULT.json',expected);before=producer(jar,classes);save(out/'PRODUCER.json',before);shutil.copyfile(__file__,out/'EXECUTED-matrix.py')
    runtime=json.loads((ROOT.parent/'mo-2/build/monocle100-neighbors-1/pilot-inputs-1/runtime.json').read_bytes());runtime['profile']='monocle100-original-graph-r43-v1';save(out/'runtime.json',runtime)
    cp_linux=mount(jar);cp_windows=str(jar)
    if classes:cp_linux=mount(classes)+':'+mount(ROOT/'src/main/resources')+':'+cp_linux;cp_windows=str(classes)+';'+str(ROOT/'src/main/resources')+';'+cp_windows
    def execute(case):
        name=case['name'];directory=out/name;directory.mkdir();request=dict(schema_version=1,source_cds=case['source_cds'],runtime=mount(out/'runtime.json'),dataset_provenance='Complete explicitly prepared synthetic original1.0.0 CDS; '+name+'; no author pipeline or biological root claim',out=mount(directory/'capture'),**case['parameters']);save(directory/'request.json',request)
        command=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--','/usr/bin/timeout','--signal=TERM','--kill-after=20','720','java','-Xmx2g','-cp',cp_linux,'org.jlinalg.cli.Monocle100GraphCli','RUN','--request',mount(directory/'request.json')]
        save(directory/'STARTED.json',dict(command=command,started_unix=time.time(),quick_compiled_only=classes is not None))
        with (directory/'stdout').open('xb') as stdout,(directory/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=780)
        save(directory/'PROCESS.json',dict(exit_code=p.returncode));wanted=expected['cases'][name]['expected_cli_exit'];capture=directory/'capture';analysis=json.loads((capture/'analysis.json').read_bytes()) if (capture/'analysis.json').exists() else {};complete=(capture/'COMPLETED.json').exists();admitted=analysis.get('graph_admitted');ordering=analysis.get('pseudotime_admitted');end=json.loads((capture/'worker/SUPERVISOR-END.json').read_bytes()) if (capture/'worker/SUPERVISOR-END.json').exists() else {};process=json.loads((capture/'worker/PROCESS.json').read_bytes()) if (capture/'worker/PROCESS.json').exists() else {}
        verify_code=None
        if p.returncode==0:
            verify_command=[str(JAVA21),'-Xmx2g','-cp',cp_windows,'org.jlinalg.cli.Monocle100GraphCli','VERIFY','--capture',str(capture),'--out',str(directory/'verify-windows')];save(directory/'VERIFY-STARTED.json',dict(command=verify_command))
            with (directory/'verify.stdout').open('xb') as stdout,(directory/'verify.stderr').open('xb') as stderr:v=subprocess.run(verify_command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=120)
            verify_code=v.returncode;save(directory/'VERIFY-PROCESS.json',dict(exit_code=verify_code))
        matches=p.returncode==wanted and complete==(wanted==0) and admitted==(wanted==0) and ordering==(wanted==0 and case['parameters']['operation']!='LEARN') and analysis.get('scientific_test_admitted') is False and end.get('alive_after_cleanup') is False and end.get('owned_live_after_cleanup')==[] and process.get('owned_live_after_cleanup')==[] and (wanted!=0 or verify_code==0)
        result=dict(case=name,operation=case['parameters']['operation'],expected_exit_code=wanted,exit_code=p.returncode,complete=complete,graph_admitted=admitted,pseudotime_admitted=ordering,verify_windows_exit_code=verify_code,expected_original_error=expected['cases'][name]['source_error'] is not None,expected_convergence_refusal=expected['cases'][name]['declared_convergence_policy_refused'],all_expected=matches)
        save(directory/'CASE-RESULT.json',result);print(name,'exit',p.returncode,'expected',wanted,'PASS' if matches else 'UNEXPECTED',flush=True);return name,result
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:results=dict(pool.map(execute,plan['cases']))
    assert producer(jar,classes)==before,'Producer changed during complete execution matrix'
    success=all(row['all_expected'] for row in results.values());result=dict(status='PASS_ALL_ACTUAL_GRAPH_EXPECTATIONS' if success else 'FAIL_ACTUAL_GRAPH_EXPECTATIONS',total=len(results),completed_graphs=sum(row['complete'] for row in results.values()),retained_failures=sum(not row['complete'] for row in results.values()),quick_compiled_only=classes is not None,results=results,whole_paper_reproduced=False,scientific_test_admitted=False)
    save(out/'RESULT.json',result);print(result['status'],flush=True);return 0 if success else 2
if __name__=='__main__':raise SystemExit(main())
