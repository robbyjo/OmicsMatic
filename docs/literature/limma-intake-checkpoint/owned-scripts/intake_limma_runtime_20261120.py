from pathlib import Path
import hashlib,json,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/lm-1';B=T/'build/limma-high-impact-1';O=B/'runtime-intake-1';O.mkdir(parents=True,exist_ok=False);libs=R/'build/literature-review/monocle-runtime/idep244-adopted-provider-4/libraries-requested.txt';rs=Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe')
script=O/'inspect.R';script.write_text('''args <- commandArgs(TRUE)
libs <- readLines(args[1],warn=FALSE)
.libPaths(c(libs,.libPaths()))
out <- args[2]
capture.output(sessionInfo(),file=file.path(out,"session-before.txt"))
rows <- lapply(c("limma","statmod","locfit","edgeR","MASS"),function(pkg) {
 if(!requireNamespace(pkg,quietly=TRUE))return(data.frame(package=pkg,version="UNAVAILABLE",path=""))
 data.frame(package=pkg,version=as.character(packageVersion(pkg)),path=normalizePath(find.package(pkg),winslash="/"))
})
write.table(do.call(rbind,rows),file.path(out,"packages.tsv"),sep="\\t",row.names=FALSE,quote=FALSE)
writeLines(.libPaths(),file.path(out,"libraries-effective.txt"))
capture.output(sessionInfo(),file=file.path(out,"session-after.txt"))
if(requireNamespace("limma",quietly=TRUE)) {
 file.copy(file.path(find.package("limma"),"DESCRIPTION"),file.path(out,"limma-DESCRIPTION"),overwrite=FALSE)
 for(name in c("fitFDist","squeezeVar","fitFDistUnequalDF1","fitFDistRobustly","eBayes","voom","removeBatchEffect","normalizeBetweenArrays")) {
  fn <- get0(name,envir=asNamespace("limma"),inherits=FALSE)
  if(is.function(fn))dput(list(formals=formals(fn),body=body(fn)),file=file.path(out,paste0(name,".R")))
 }
}
''',encoding='utf-8',newline='\n')
def pin(p):
    with p.open('rb') as stream:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
command=[str(rs),'--vanilla',str(script),str(libs),str(O)];env={k:v for k,v in os.environ.items() if not k.startswith('R_LIBS') and k not in ['R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS','LANG','LANGUAGE'] and not k.startswith('LC_')};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
with (O/'stdout').open('xb') as stdout,(O/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=O,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
(O/'PROCESS.json').write_text(json.dumps(dict(command=command,exit_code=p.returncode,rscript=pin(rs),libraries_request=pin(libs)),indent=2)+'\n',encoding='utf-8');assert p.returncode==0,(O/'stderr').read_text(errors='replace');print((O/'packages.tsv').read_text(),flush=True)
