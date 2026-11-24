"""Run synthetic prepared Java fits, separate original R calls and saved inspection.

No installation, paper inputs, catalog mutation or independent optimizer.
Raw requests, logs, results and comparisons remain in NEW private output.
"""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,sys,time

HERE=Path(__file__).resolve().parent

def save(path,value):
    path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8',newline='\n')

def main(argv=None):
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('jar','runtime','rscript','out'):p.add_argument('--'+name,type=Path,required=True)
    p.add_argument('--java',default='java');p.add_argument('--cases',nargs='+')
    a=p.parse_args(argv);root=a.out.absolute()
    if root.exists() or not root.parent.is_dir() or root.parent.resolve()!=root.parent:raise ValueError('Use NEW output with an existing physical parent')
    for dependency in (a.jar,a.runtime/'PROFILE.json',a.rscript):
        if not dependency.is_file():
            print('SKIP3: missing prerequisite '+str(dependency)+'. Install/stage the exact permitted runtime or retain the skipped analysis; see docs/GENOMICSEM_WORKFLOW.md.',file=sys.stderr);return 3
    cases=json.loads((HERE/'fixtures/CASES.json').read_bytes())
    if a.cases:
        if not set(a.cases)<=set(c['name']for c in cases):raise ValueError('Unknown synthetic case')
        cases=[c for c in cases if c['name']in a.cases]
    root.mkdir();results=[];jar=a.jar.absolute();runtime=a.runtime.absolute()
    env=os.environ.copy();env.update(LC_ALL='C',LANG='C',R_LIBS=str(runtime/'library'),R_LIBS_USER=str(runtime/'library'),R_LIBS_SITE='',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1')
    def run(command,folder,prefix):
        with(folder/(prefix+'.stdout')).open('xb')as out,(folder/(prefix+'.stderr')).open('xb')as err:
            return subprocess.run(command,stdout=out,stderr=err,env=env).returncode
    identity=dict(bytes=jar.stat().st_size,sha256=hashlib.sha256(jar.read_bytes()).hexdigest())
    for case in cases:
        folder=root/case['name'];folder.mkdir();q=case['request'];q['runtime_home']=str(runtime);q['out']=str(folder/'capture')
        q['inputs']={k:str((HERE/'fixtures'/v).resolve())for k,v in q['inputs'].items()};request=folder/'request.json';save(request,q)
        start=time.monotonic();java=[a.java,'-Xmx4g','-jar',str(jar),'genomicsem-tools']
        code=run(java+['--request',str(request)],folder,'adapter');row=dict(case=case['name'],adapter_exit=code,seconds=time.monotonic()-start,comparison_exit=None,inspect_exit=None)
        if code==0:
            reference=folder/'reference';reference.mkdir()
            row['comparison_exit']=run([str(a.rscript.absolute()),'--vanilla',str(HERE/'reference.R'),str(request),str(reference),str(folder/'capture/worker/original-result.rds'),str(runtime)],folder,'reference')
            row['inspect_exit']=run(java+['--inspect',str(folder/'capture')],folder,'inspect')
        row['passed']=row['adapter_exit']==row['comparison_exit']==row['inspect_exit']==0;results.append(row);save(folder/'RESULT.json',row)
        done=len(results)==len(cases);save(root/'RESULT.json',dict(status=('PASS'if all(c['passed']for c in results)else'FAIL')if done or not row['passed']else'RUNNING',cases=results,jar=identity))
        print(json.dumps(row),flush=True)
        if not row['passed']:
            code=3 if row['adapter_exit']==3 else 2
            print(('SKIP3: prerequisite unavailable; dependent analysis skipped.'if code==3 else'FAIL2: no complete qualification.')+' Preserve this attempt and read adapter/reference stderr plus capture/FAILURE.md; fix the stated cause and use NEW output. No result is replaced.',file=sys.stderr);return code
    return 0

if __name__=='__main__':
    try:raise SystemExit(main())
    except FileNotFoundError as e:print('SKIP3: dependency unavailable: '+str(e)+'. Use an allowed installation method or retain the skip.',file=sys.stderr);raise SystemExit(3)
    except (OSError,ValueError,KeyError)as e:print('FAIL2: '+str(e)+'. Preserve the attempt and retry NEW output after resolving the cause.',file=sys.stderr);raise SystemExit(2)
