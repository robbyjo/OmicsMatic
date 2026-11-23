from pathlib import Path
import concurrent.futures,json,os,subprocess,sys,time
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/('build/mrpresso-catalog-178/checks-'+sys.argv[1]);B.mkdir()
python='C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
env=dict(os.environ,PYTHONPATH=str(T/'tools/literature'),JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot')
commands=[('windows-model-transaction',[python,'-X','utf8','-B','-m','unittest','-v','mrpresso_catalog.test_model','mrpresso_catalog.test_transaction'],0),
 ('linux-model-transaction',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd','/mnt/d/projects/JLinAlg/build/fu-1','--','env','GIT_DIR=/mnt/d/projects/JLinAlg/.git/worktrees/fu-1','GIT_WORK_TREE=/mnt/d/projects/JLinAlg/build/fu-1','GIT_OPTIONAL_LOCKS=0','PYTHONPATH=/mnt/d/projects/JLinAlg/build/fu-1/tools/literature','python3','-B','-m','unittest','-v','mrpresso_catalog.test_model','mrpresso_catalog.test_transaction'],0),
 ('workflow-checkpoint',[python,'-X','utf8','-B',str(T/'tools/literature/verify_mrpresso_workflow_checkpoint.py'),'--jar',str(T/'build/mrpresso-1/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar')],0),
 ]
def run(row):
    name,args,expected=row;start=time.monotonic()
    with (B/(name+'.stdout')).open('xb') as out,(B/(name+'.stderr')).open('xb') as err:
        p=subprocess.Popen(args,cwd=T,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err)
        (B/(name+'.PROCESS.json')).write_text(json.dumps(dict(pid=p.pid,command=args),indent=2)+'\n');code=p.wait()
    record=dict(case=name,exit_code=code,expected=expected,seconds=time.monotonic()-start)
    (B/(name+'.RESULT.json')).write_text(json.dumps(record,indent=2)+'\n');print(json.dumps(record),flush=True)
    if expected is not None:assert code==expected,(name,(B/(name+'.stderr')).read_text(errors='replace')[-2500:])
    return record
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:records=list(pool.map(run,commands))
(B/'RESULT.json').write_text(json.dumps(dict(cases=records,website_result_requires_comparison_to_retained33=True),indent=2)+'\n')
