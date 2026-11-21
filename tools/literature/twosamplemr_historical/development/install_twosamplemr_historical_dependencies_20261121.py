from pathlib import Path
import hashlib,json,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1';O=R/'build/twosamplemr-historical-runtime-20261121-1';assert not O.exists();O.mkdir();(O/'library').mkdir();(O/'downloads').mkdir();script=O/'install-dependencies.R'
script.write_text(r'''args<-commandArgs(TRUE);root<-args[[1]];intake<-args[[2]];baseline<-args[[3]];lib<-file.path(root,'library')
.libPaths(c(lib,baseline,.Library),include.site=FALSE)
stopifnot(!any(grepl('/win-library/',.libPaths(),fixed=TRUE)))
options(repos=c(MRCIEU='https://mrcieu.r-universe.dev',CRAN='https://cloud.r-project.org'),timeout=300)
before<-installed.packages();saveRDS(before,file.path(root,'installed-before.rds'),version=3)
available<-available.packages(type='win.binary');saveRDS(available,file.path(root,'available-before.rds'),version=3)
required<-character()
for(v in c('0.4.9','0.5.6','0.5.7','0.6.8')){d<-read.dcf(file.path(intake,v,'source/DESCRIPTION'))[1,];f<-intersect(c('Depends','Imports'),names(d));required<-c(required,trimws(gsub('[(].*','',unlist(strsplit(paste(d[f],collapse=','),',')))))}
required<-setdiff(unique(required),c('','R'));missing<-setdiff(required,rownames(before));stopifnot(all(missing%in%rownames(available)))
writeLines(missing,file.path(root,'requested-packages.txt'))
install.packages(missing,lib=lib,type='win.binary',dependencies=NA,destdir=file.path(root,'downloads'))
after<-installed.packages();saveRDS(after,file.path(root,'installed-after.rds'),version=3)
absent<-setdiff(required,rownames(after));writeLines(absent,file.path(root,'unavailable-after.txt'))
if(length(absent))stop(paste('Required dependencies remain unavailable:',paste(absent,collapse=',')))
for(p in missing)if(!requireNamespace(p,quietly=TRUE))stop(paste('Installed dependency namespace cannot load:',p))
capture.output(sessionInfo(),file=file.path(root,'session-after.txt'));writeLines('PASS_PRIVATE_HISTORICAL_PACKAGE_DEPENDENCIES_NAMESPACE_LOAD',file.path(root,'COMPLETED.txt'))
''',encoding='utf-8',newline='\n')
command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(O),str(R/'build/twosamplemr-historical-intake-20261121-1'),str(R/'build/twosamplemr-runtime-intake-20261121-1/library')];(O/'COMMAND.json').write_text(json.dumps(dict(command=command,user_library_not_used=True,protected_baseline_read_only=True,authorization='User permits downloads/private installs where operating environment allows'),indent=2)+'\n',encoding='utf-8')
with (O/'stdout').open('xb') as stdout,(O/'stderr').open('xb') as stderr:
    p=subprocess.Popen(command,cwd=O,env=dict(os.environ,LANG='C',LC_ALL='C'),stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW);(O/'PROCESS.json').write_text(json.dumps(dict(pid=p.pid))+'\n');print('Started private dependency install',p.pid,flush=True);code=p.wait()
downloads={p.name:dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest()) for p in (O/'downloads').iterdir() if p.is_file()};(O/'TERMINAL.json').write_text(json.dumps(dict(exit_code=code,downloads=downloads,complete=(O/'COMPLETED.txt').exists()),indent=2)+'\n',encoding='utf-8');print('Dependency install exit',code,'downloads',len(downloads),flush=True)
