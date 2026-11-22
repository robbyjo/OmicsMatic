from pathlib import Path
import hashlib,json,subprocess,time
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';B=F/'workflow-linux-training-3';B.mkdir();old=F/'workflow-linux-training-1'
def linux(p):s=str(p).replace('\\','/');return '/mnt/'+s[0].lower()+s[2:]
cp=':'.join(linux(p) for p in [F/'workflow-compile-6/classes',F/'workflow-compile-6/resources',F/'normal-build-1/project-build/cli/omicsmatic-0.4.0.jar']);records=[]
originals={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (old/'data').iterdir()}
for name in ['all-models','estimated-h2']:
 r=json.loads((old/(name+'.json')).read_bytes());r['out']=linux(B/name);r['options']['verbose']=2
 if name=='all-models':r['runtime_compatibility']={'gemma_working_directory_output':True}
 file=B/(name+'.json');file.write_text(json.dumps(r,indent=2)+'\n',encoding='utf-8');args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','java','-Xmx2g','-cp',cp,'org.jlinalg.cli.JLinAlgCli','fusion-tools','--request',linux(file)]
 with (B/(name+'.stdout')).open('xb') as out,(B/(name+'.stderr')).open('xb') as err:
  p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/(name+'.PROCESS.json')).write_text(json.dumps(dict(pid=p.pid,arguments=args,started_unix=time.time()),indent=2)+'\n');code=p.wait()
 assert originals=={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (old/'data').iterdir()}
 record=dict(case=name,exit_code=code,original_inputs_unchanged=True);records.append(record);(B/'PROGRESS.json').write_text(json.dumps(records,indent=2)+'\n');print(json.dumps(record),flush=True)
 if code:
  print((B/(name+'.stderr')).read_text(errors='replace')[-1800:],flush=True)
  for log in ['stderr.log','original.stdout.log','original.stderr.log']:
   f=B/name/'worker'/log
   if f.is_file():print(log,f.read_text(errors='replace')[-1200:],flush=True)
(B/'RESULT.json').write_text(json.dumps(records,indent=2)+'\n')