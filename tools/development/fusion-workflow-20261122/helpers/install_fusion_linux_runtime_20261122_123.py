from pathlib import Path
import json,subprocess
T=Path('D:/projects/JLinAlg/build/gp-1');B=T/'build/fusion-1/workflow-linux-install-1';B.mkdir();(B/'library').mkdir();(B/'downloads').mkdir();script=B/'install.R';script.write_text('''args<-commandArgs(TRUE)
lib<-normalizePath(args[1],mustWork=TRUE)
.libPaths(c(lib,.Library))
options(repos=c(CRAN="https://cloud.r-project.org"),timeout=60)
needed<-c("here","glmnet","coloc","susieR","corrplot")
install.packages(needed,lib=lib,type="source",dependencies=c("Depends","Imports","LinkingTo"),destdir=args[2],Ncpus=1)
write.table(installed.packages(lib.loc=lib)[,c("Package","Version","License"),drop=FALSE],file=args[3],sep="\\t",quote=FALSE,row.names=FALSE)
for(p in needed)if(!requireNamespace(p,quietly=TRUE))stop(paste("SKIP3: private Linux package unavailable:",p))
print(sessionInfo())
''',encoding='utf-8',newline='\n')
prefix='/opt/jlinalg-saige-runtime-v1/source/.pixi/envs/default';linux='/mnt/d/projects/JLinAlg/build/gp-1/build/fusion-1/workflow-linux-install-1';args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','env','PATH='+prefix+'/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin','R_LIBS_USER='+linux+'/library','R_LIBS_SITE=',prefix+'/bin/Rscript','--vanilla',linux+'/install.R',linux+'/library',linux+'/downloads',linux+'/PACKAGES.tsv'];(B/'COMMAND.json').write_text(json.dumps({'arguments':args,'existing_runtime_read_only':prefix,'private_library_only':linux+'/library'},indent=2)+'\n')
with (B/'stdout').open('xb') as out,(B/'stderr').open('xb') as err:
 p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/'PROCESS.json').write_text(json.dumps({'pid':p.pid})+'\n');code=p.wait()
(B/'RESULT.json').write_text(json.dumps({'exit_code':code,'global_installation_requested':False},indent=2)+'\n');print(json.dumps({'exit_code':code,'evidence':str(B)}))
