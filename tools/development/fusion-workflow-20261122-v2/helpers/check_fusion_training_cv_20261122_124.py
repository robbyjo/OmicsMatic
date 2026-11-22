from pathlib import Path
import hashlib,json,subprocess,time
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';B=F/'workflow-training-cv-1';B.mkdir();r=json.loads((F/'workflow-linux-training-3/all-models.json').read_bytes());jar=F/'workflow-normal-build-6/project-build/cli/omicsmatic-0.4.0.jar'
def linux(p):s=str(p).replace('\\','/');return '/mnt/'+s[0].lower()+s[2:]
r['out']=linux(B/'run');r['options']['crossval']=2;file=B/'request.json';file.write_text(json.dumps(r,indent=2)+'\n');args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','java','-Xmx2g','-jar',linux(jar),'fusion-tools','--request',linux(file)];started=time.time()
with (B/'stdout').open('xb') as out,(B/'stderr').open('xb') as err:p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/'PROCESS.json').write_text(json.dumps(dict(pid=p.pid,arguments=args,started_unix=started),indent=2)+'\n');code=p.wait()
result=dict(exit_code=code,seconds=time.time()-started,models=['blup','lasso','top1','enet','bslmm'],crossval=2,synthetic_inputs_only=True)
(B/'RESULT.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result),flush=True)
if code:print((B/'stderr').read_text(errors='replace')[-2000:],flush=True)
assert code==0