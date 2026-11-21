from pathlib import Path
import json,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1';assert not B.exists();B.mkdir(parents=True);I=R/'build/twosamplemr-historical-intake-20261121-1';script=B/'audit.R'
script.write_text(r'''args<-commandArgs(TRUE);root<-args[[1]];intake<-args[[2]];current<-args[[3]]
.libPaths(c(current,'C:/Users/User/AppData/Local/R/win-library/4.6',.Library),include.site=FALSE)
stopifnot(requireNamespace('jsonlite',quietly=TRUE));options(repos=c(MRCIEU='https://mrcieu.r-universe.dev',CRAN='https://cloud.r-project.org'),timeout=120)
installed<-installed.packages();saveRDS(installed,file.path(root,'installed.rds'),version=3)
available<-available.packages(type='win.binary');saveRDS(available,file.path(root,'available.rds'),version=3)
definitions<-function(folder){result<-list();for(file in list.files(folder,full.names=TRUE,pattern='[.]R$'))for(e in parse(file,keep.source=FALSE))if(is.call(e)&&length(e)==3L&&as.character(e[[1]])%in%c('<-','=')&&is.symbol(e[[2]])&&is.call(e[[3]])&&identical(e[[3]][[1]],as.name('function')))result[[as.character(e[[2]])]]<-list(file=basename(file),formals=paste(deparse(e[[3]][[2]]),collapse=' '));result}
result<-list()
for(version in c('0.4.9','0.5.6','0.5.7','0.6.8')){
 folder<-file.path(intake,version,'source');desc<-read.dcf(file.path(folder,'DESCRIPTION'))[1,];fields<-intersect(names(desc),c('Imports','Depends'))
 required<-unique(trimws(gsub('[(].*','',unlist(strsplit(paste(desc[fields],collapse=','),',')))));required<-setdiff(required,c('','R'));missing<-setdiff(required,rownames(installed))
 defs<-definitions(file.path(folder,'R'));exports<-grep('^export[(]',readLines(file.path(folder,'NAMESPACE')),value=TRUE)
 result[[version]]<-list(version=version,license=unname(desc['License']),source_functions=length(defs),functions=defs,exports=as.list(exports),required=as.list(required),missing=as.list(missing),missing_available_binary=as.list(intersect(missing,rownames(available))),missing_unavailable_binary=as.list(setdiff(missing,rownames(available))),already_installed=unname(lapply(intersect(required,rownames(installed)),function(p)as.list(installed[p,c('Package','Version','LibPath')]))))
}
writeLines(jsonlite::toJSON(result,auto_unbox=TRUE,pretty=TRUE,null='null',na='string'),file.path(root,'AUDIT.json'),useBytes=TRUE)
capture.output(sessionInfo(),file=file.path(root,'session.txt'))
''',encoding='utf-8',newline='\n')
command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(B),str(I),str(R/'build/twosamplemr-runtime-intake-20261121-1/library')];(B/'COMMAND.json').write_text(json.dumps(command)+'\n',encoding='utf-8')
with (B/'stdout').open('xb') as stdout,(B/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=B,env=dict(os.environ,LANG='C',LC_ALL='C'),stdout=stdout,stderr=stderr,timeout=300)
(B/'PROCESS.json').write_text(json.dumps(dict(exit_code=p.returncode))+'\n');assert p.returncode==0,(B/'stderr').read_text()
for version,row in json.loads((B/'AUDIT.json').read_bytes()).items():print(version,'functions',row['source_functions'],'missing',row['missing'],'not in binary index',row['missing_unavailable_binary'],flush=True)
