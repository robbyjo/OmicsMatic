from pathlib import Path
import csv,hashlib,json,math,random,shutil,subprocess,time
T=Path('D:/projects/JLinAlg/build/gp-1');B=T/'build/fusion-1/workflow-linux-training-1';B.mkdir();data=B/'data';data.mkdir();old=T/'build/fusion-1/original-association-1/data';prefix=data/'train'
for ext in ['bed','bim']:shutil.copyfile(old/('ref.1.'+ext),Path(str(prefix)+'.'+ext))
raw=(old/'ref.1.bed').read_bytes();fam=(old/'ref.1.fam').read_text().splitlines();n=len(fam);bps=(n+3)//4;lut={0:2,1:None,2:1,3:0};rng=random.Random(9346123);lines=[];pheno=[''];covar=['FID IID C1']
for i,row in enumerate(fam):
 cells=row.split();g0=lut[(raw[3+i//4]>>(2*(i%4)))&3];g4=lut[(raw[3+4*bps+i//4]>>(2*(i%4)))&3];y=1.4*g0+.4*g4+1.2*rng.gauss(0,1);cells[5]=f'{y:.17g}';lines.append(' '.join(cells));pheno.append(f'{cells[0]} {cells[1]} {y:.17g}');covar.append(f'{cells[0]} {cells[1]} {rng.gauss(0,1):.17g}')
Path(str(prefix)+'.fam').write_text('\n'.join(lines)+'\n',encoding='ascii',newline='\n');(data/'phenotype').write_text('\n'.join(pheno[1:])+'\n',encoding='ascii',newline='\n');(data/'covariates').write_text('\n'.join(covar)+'\n',encoding='ascii',newline='\n')
def linux(p):s=str(p).replace('\\','/');return '/mnt/'+s[0].lower()+s[2:]
lib=T/'build/fusion-1/workflow-linux-install-1/library';versions={}
with (lib.parent/'PACKAGES.tsv').open(newline='',encoding='utf-8-sig') as f:
 for row in csv.DictReader(f,delimiter='\t'):versions[row['Package']]=row['Version']
versions.update(optparse='1.8.2',jsonlite='2.0.0');rscript='/opt/jlinalg-saige-runtime-v1/source/.pixi/envs/default/bin/Rscript';bindir=T/'build/fusion-1/binary-intake-1';gcta=T/'build/fusion-1/gcta-modern-intake-3/gcta64';requests={}
base={'schema_version':1,'mode':'RUN','profile':'fusion-9346c1222bffbb34499fa7a8e23c1b701b55cb05','operation':'TRAIN','rscript':rscript,'r_libraries':[linux(lib)],'expected_R':'4.5.2','expected_packages':{k:versions[k] for k in ['jsonlite','here','optparse','glmnet']},'inputs':{'bfile':linux(prefix),'phenotype':linux(data/'phenotype'),'covariates':linux(data/'covariates')},'seed':1,'options':{'noclean':True,'save_hsq':True},'binaries':{'plink':linux(bindir/'plink')}}
r=json.loads(json.dumps(base));r['options'].update(models='blup,lasso,top1,enet,bslmm',hsq_set=.4,crossval=0,rn=True,resid=True);r['binaries']['gemma']=linux(bindir/'gemma');r['out']=linux(B/'all-models');requests['all-models']=r
r=json.loads(json.dumps(base));r['options'].update(models='top1',crossval=2,hsq_p=1);r['binaries']['gcta']=linux(gcta);r['runtime_compatibility']={'gcta_no_analysis_probe_exit1':True};r['out']=linux(B/'estimated-h2');requests['estimated-h2']=r
cp=':'.join(linux(p) for p in [T/'build/fusion-1/workflow-compile-3/classes',T/'build/fusion-1/workflow-compile-3/resources',T/'build/fusion-1/normal-build-1/project-build/cli/omicsmatic-0.4.0.jar']);records=[];before={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in data.iterdir()}
for name,r in requests.items():
 file=B/(name+'.json');file.write_text(json.dumps(r,indent=2)+'\n',encoding='utf-8');args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','java','-Xmx2g','-cp',cp,'org.jlinalg.cli.JLinAlgCli','fusion-tools','--request',linux(file)]
 with (B/(name+'.stdout')).open('xb') as out,(B/(name+'.stderr')).open('xb') as err:p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/(name+'.PROCESS.json')).write_text(json.dumps({'pid':p.pid,'arguments':args,'started_unix':time.time()},indent=2)+'\n');code=p.wait()
 assert before=={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in data.iterdir()},'Original input mutated'
 records.append({'case':name,'exit_code':code,'original_inputs_unchanged':True});(B/'PROGRESS.json').write_text(json.dumps(records,indent=2)+'\n');print(name,code,(B/(name+'.stderr')).read_text(errors='replace')[:1700],flush=True)
 worker=B/name/'worker'
 if code:
  for log in ['stderr.log','original.stdout.log','original.stderr.log']:
   f=worker/log
   if f.exists():print(log,f.read_text(errors='replace')[-1400:],flush=True)
(B/'RESULT.json').write_text(json.dumps(records,indent=2)+'\n')
