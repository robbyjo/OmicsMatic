from pathlib import Path
import hashlib,json,os,subprocess,sys,tarfile
from handover_inventory_20261122_111 import pin,save
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/mrpresso-1';D=B/'runtime-install-windows-1';D.mkdir()
source=B/'upstream-intake-1/source';lib=D/'library';lib.mkdir();archive=D/'MRPRESSO-core-3e3c92d.tar.gz'
with tarfile.open(archive,'w:gz') as out:
    for path in sorted(source.rglob('*')):
        if path.is_file():out.add(path,arcname='MRPRESSO/'+path.relative_to(source).as_posix(),recursive=False)
r=D/'install.R';r.write_text('''args<-commandArgs(TRUE)
install.packages(args[1],repos=NULL,type="source",lib=args[2])
.libPaths(c(args[2],.Library))
stopifnot(as.character(packageVersion("MRPRESSO"))=="1.0")
reference<-new.env(parent=baseenv());sys.source(args[3],envir=reference,keep.source=FALSE)
stopifnot(identical(deparse(body(MRPRESSO::mr_presso)),deparse(body(reference$mr_presso))))
stopifnot(identical(formals(MRPRESSO::mr_presso),formals(reference$mr_presso)))
writeLines(c(R.version.string,paste(RNGkind(),collapse="/"),R.home(),find.package("MRPRESSO")),args[4])
writeLines(capture.output(sessionInfo()),args[5])
''',encoding='utf-8')
args=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(r),str(archive),str(lib),str(source/'R/mr_presso.R'),str(D/'RUNTIME.txt'),str(D/'SESSION.txt')]
env=dict(os.environ,R_LIBS_USER=str(lib),R_LIBS_SITE='',LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
with (D/'stdout').open('xb') as out,(D/'stderr').open('xb') as err:
    process=subprocess.Popen(args,cwd=T,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err)
    save(D/'PROCESS.json',dict(pid=process.pid,command=args));code=process.wait()
save(D/'RESULT.json',dict(exit_code=code,private_library_only=True,existing_runtime_or_libraries_modified=False,
    source_commit='3e3c92d7eda6dce0d1d66077373ec0f7ff4f7e87',source_package_selection='All original R/ files and package metadata/manual, bundled publication data excluded',
    source_archive=pin(archive),installed_files={p.relative_to(lib).as_posix():pin(p) for p in sorted(lib.rglob('*')) if p.is_file()},
    exported_function_body_and_formals_equal_original=code==0,new_family_credit=0,new_reviews=0))
print(json.dumps(dict(exit_code=code,library=str(lib))),flush=True);assert code==0,(D/'stderr').read_text(errors='replace')[-2000:]
