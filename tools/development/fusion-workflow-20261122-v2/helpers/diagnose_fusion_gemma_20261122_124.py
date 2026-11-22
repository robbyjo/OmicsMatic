from pathlib import Path
import hashlib,json,shutil,subprocess,time
R=Path('D:/projects/JLinAlg');T=R/'build/gp-1';F=T/'build/fusion-1';B=F/'gemma-output-diagnostic-1';B.mkdir();data=B/'data';data.mkdir();source=F/'workflow-linux-training-1/all-models/worker/tmp'
for ext in ['bed','bim','fam']:shutil.copyfile(source/('train.'+ext),data/('train.'+ext))
def linux(p):s=str(p).replace('\\','/');return '/mnt/'+s[0].lower()+s[2:]
exe=linux(F/'binary-intake-1/gemma');rows=[]
for name,extra in [('strict',[]),('explicit-working-directory',['-outdir','.'])]:
 folder=B/name;folder.mkdir();(folder/'tmp').mkdir()
 args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(folder),'--',exe,'-miss','1','-maf','0','-r2','1','-rpace','1000','-wpace','1000','-bfile',linux(data/'train'),'-bslmm','2','-o','tmp/train',*extra]
 with (folder/'stdout').open('xb') as out,(folder/'stderr').open('xb') as err:
  p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(folder/'PROCESS.json').write_text(json.dumps(dict(launcher_pid=p.pid,arguments=args,started_unix=time.time()),indent=2)+'\n');code=p.wait()
 paths=[p for p in folder.rglob('*') if p.is_file()]
 record=dict(case=name,exit_code=code,weight_file=(folder/'tmp/train.param.txt').is_file(),files={p.relative_to(folder).as_posix():dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in paths})
 rows.append(record);(B/'PROGRESS.json').write_text(json.dumps(rows,indent=2)+'\n');print(name,code,'weight_file',record['weight_file'],flush=True)
 print((folder/'stderr').read_text(errors='replace')[-1500:],flush=True);print((folder/'stdout').read_text(errors='replace')[-1000:],flush=True)
(B/'RESULT.json').write_text(json.dumps(rows,indent=2)+'\n')