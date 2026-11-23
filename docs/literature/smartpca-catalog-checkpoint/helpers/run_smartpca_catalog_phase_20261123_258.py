from pathlib import Path
import json,os,subprocess,sys,time
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/smartpca-catalog-255'
mode=sys.argv[1];host=sys.argv[2];tag=sys.argv[3];assert mode in ('plan','inspect','apply','verify') and host in ('windows','linux')
D=B/(mode+'-'+host+'-'+tag);D.mkdir();python='C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe'
args=[str(T/'tools/literature/close_smartpca_catalog.py'),'--'+mode,'--out',str(D/'result')]
if mode!='verify' or len(sys.argv)>4:
    args+=['--cache','D:/projects/JLinAlg/build/literature-review/xml','--jar',str(T/'build/smartpca-4/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar')]
if mode in ('inspect','apply'):args+=['--plan-dir',str(B/'plan-windows-1/result')]
if host=='windows':command=[python,'-X','utf8','-B',*args]
else:
    def linux(n):
        value=str(n).replace('\\','/');return '/mnt/'+value[0].lower()+value[2:] if len(value)>1 and value[1]==':' else value
    command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','env',
        'GIT_DIR=/mnt/d/projects/JLinAlg/.git/worktrees/fu-1','GIT_WORK_TREE='+linux(T),'GIT_OPTIONAL_LOCKS=0','python3','-B',*[linux(n) for n in args]]
start=time.monotonic()
with (D/'stdout').open('xb') as out,(D/'stderr').open('xb') as err:
    process=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err)
    (D/'PROCESS.json').write_text(json.dumps(dict(pid=process.pid,command=command),indent=2)+'\n');code=process.wait()
(D/'RESULT.json').write_text(json.dumps(dict(mode=mode,host=host,exit_code=code,seconds=time.monotonic()-start),indent=2)+'\n')
print(json.dumps(dict(mode=mode,host=host,exit_code=code)),flush=True)
assert code==0,(D/'stderr').read_text(errors='replace')[-3000:]
