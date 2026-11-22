from pathlib import Path
import csv,hashlib,json,os,shutil,subprocess,time
T=Path('.').resolve();B=T/'build/fusion-1/workflow-development-1';B.mkdir();data=T/'build/fusion-1/original-association-1/data';libraries=[T/'build/fusion-1/workflow-windows-library-1/library',T/'build/fusion-1/runtime-install-1/library'];versions={}
for lib in libraries:
 with (lib.parent/'PACKAGES.tsv').open(newline='',encoding='utf-8-sig') as f:
  for row in csv.DictReader(f,delimiter='\t'):versions.setdefault(row['Package'],row['Version'])
request={'schema_version':1,'mode':'RUN','profile':'fusion-9346c1222bffbb34499fa7a8e23c1b701b55cb05','operation':'ASSOCIATE','rscript':'C:/Program Files/R/R-4.6.1/bin/Rscript.exe','r_libraries':list(map(str,libraries)),'expected_R':'4.6.1','expected_packages':{k:versions[k] for k in ['here','optparse','jsonlite']},'inputs':{'sumstats':str(data/'complete.sumstats'),'weights':str(data/'weights.pos'),'weights_dir':str(data/'weights'),'ref_ld_prefix':str(data/'ref.'),'chromosome':'1'},'options':{},'seed':1,'out':str(B/'run')}
(B/'request.json').write_text(json.dumps(request,indent=2)+'\n',encoding='utf-8');classes=T/'build/fusion-1/workflow-compile-1/classes';oldjar=T/'build/fusion-1/normal-build-1/project-build/cli/omicsmatic-0.4.0.jar';args=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe','-Xmx2g','-cp',';'.join(map(str,[classes,T/'src/main/resources',oldjar])),'org.jlinalg.cli.JLinAlgCli','fusion-tools','--request',str(B/'request.json')]
for source in [*list((T/'src/main/java/org/jlinalg/cli').glob('FusionWorkflow*.java')),*list((T/'src/main/resources/org/jlinalg/omics/fusion-9346c122').rglob('*'))]:
 if source.is_file():target=B/'source-context'/source.relative_to(T);target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
(B/'COMMAND.json').write_text(json.dumps({'arguments':args,'development_classpath_not_packaged_qualification':True},indent=2)+'\n',encoding='utf-8')
with (B/'stdout').open('xb') as out,(B/'stderr').open('xb') as err:p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/'PROCESS.json').write_text(json.dumps({'pid':p.pid,'started_unix':time.time()})+'\n');code=p.wait()
(B/'RESULT.json').write_text(json.dumps({'exit_code':code},indent=2)+'\n');print(code,(B/'stdout').read_text(errors='replace'),(B/'stderr').read_text(errors='replace'));print('worker stderr:',(B/'run/worker/stderr.log').read_text(errors='replace')[-1800:] if (B/'run/worker/stderr.log').exists() else 'not started')
