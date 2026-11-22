from pathlib import Path
import hashlib,json,subprocess,time
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';B=F/'workflow-public-examples-1';B.mkdir();jar=F/'workflow-normal-build-6/project-build/cli/omicsmatic-0.4.0.jar';rows=[]
def linux(p):s=str(p).replace('\\','/');return '/mnt/'+s[0].lower()+s[2:]
for name,request,platform in [('association','association-windows.json','windows21'),('training','training-linux.json','linux21')]:
 file=T/'examples/fusion-original'/request
 args=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-Xmx2g','-jar',str(jar),'fusion-tools','--request',str(file)] if platform=='windows21' else ['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','java','-Xmx2g','-jar',linux(jar),'fusion-tools','--request',linux(file)]
 with (B/(name+'.stdout')).open('xb') as out,(B/(name+'.stderr')).open('xb') as err:p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);code=p.wait()
 record=dict(case=name,platform=platform,request=str(file),exit_code=code);rows.append(record);(B/'PROGRESS.json').write_text(json.dumps(rows,indent=2)+'\n');print(json.dumps(record),flush=True);assert code==0,(B/(name+'.stderr')).read_text(errors='replace')[-1200:]
(B/'RESULT.json').write_text(json.dumps(dict(status='CHECKED_IN_EXAMPLES_EXECUTED_SUCCESSFULLY',cases=rows),indent=2)+'\n')