# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build unchanged original LeafCutter and explicit addons in a NEW private R library; keep the locked dependency prefix immutable."""
import argparse,json,os,shutil,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save,group_members
from inventory_hail_runtime_linux import inventory

ACTION='Required action: keep dependent original differential-splicing analyses skipped until exact source/library/runtime and complete scientific workflow checks pass. Preserve source, all failed stages, package archives and compiler/health diagnostics. Where permitted correct the named dependency/compiler/source/namespace cause and retry NEW private paths; otherwise provide an allowed matching runtime or retain SKIP3. Never patch original R/Stan/C++ source, alter DESCRIPTION requirements, silently choose another edition, modify a shared environment or admit partial fits.'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--source',required=True,type=Path);p.add_argument('--addons',required=True,type=Path);p.add_argument('--compatibility',required=True,type=Path);p.add_argument('--edition',choices=['source-2c9907ef66ad','v0.2.6','v0.2.7'],default='source-2c9907ef66ad');p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();assert sys.platform=='linux' and out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);(out/'setup-at-attempt.py').write_bytes(Path(__file__).read_bytes());sources={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),ROOT/'tools/literature/setup_hail_reference_linux.py',ROOT/'tools/literature/inventory_hail_runtime_linux.py']};state=dict(status='SKIP3_ORIGINAL_LEAFCUTTER_R_LIBRARY_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,processes=[],source_pins=sources,edition=a.edition);before=None;prefix=None;env=dict(os.environ)
    for key in list(env):
        if key.startswith(('R_','PYTHON','CONDA_')) or key in ['LD_LIBRARY_PATH','LD_PRELOAD','MAKEFLAGS','CXX','CXX14','CXXFLAGS','CXX14FLAGS']:env.pop(key,None)
    for name in ['R-lib','private-temp','private-config','private-cache']:(out/name).mkdir()
    library=out/'R-lib';env.update(TMPDIR=str(out/'private-temp'),TMP=str(out/'private-temp'),TEMP=str(out/'private-temp'),XDG_CONFIG_HOME=str(out/'private-config'),XDG_CACHE_HOME=str(out/'private-cache'),R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',R_LIBS_USER=str(library),LANG='C.UTF-8',LC_ALL='C.UTF-8',MAKEFLAGS='-j1',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1')
    def execute(name,command,timeout):
        command=list(map(str,command));record=dict(command=command,started_at=time.time());save(out/(name+'.STARTED.json'),record);child=None;error=None;code=None;started=time.monotonic()
        try:
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True);record['owned_process_group']=child.pid
                while child.poll() is None:
                    if time.monotonic()-started>timeout:raise TimeoutError(name+' exceeded owned install/health timeout')
                    time.sleep(.2)
                code=child.returncode
            assert code==0,name+' failed; inspect full stdout/stderr';record['status']='completed-process-only'
        except Exception as failure:error=failure;record.update(status='SKIP3_PREPARATION_PROCESS_UNAVAILABLE',error=repr(failure))
        finally:
            if child is not None:
                record['owned_before_cleanup']=group_members(child.pid)
                for sig,seconds in [(signal.SIGTERM,3),(signal.SIGKILL,3)]:
                    if not any(r['state']!='Z' for r in group_members(child.pid)):break
                    try:os.killpg(child.pid,sig)
                    except ProcessLookupError:pass
                    deadline=time.monotonic()+seconds
                    while any(r['state']!='Z' for r in group_members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
                child.wait(timeout=10);record['owned_live_after_cleanup']=[r for r in group_members(child.pid) if r['state']!='Z']
                if record['owned_live_after_cleanup']:error=RuntimeError('Owned compiler/health processes remain alive');record['status']='FAIL2_OWNED_CLEANUP_INCOMPLETE'
            record.update(exit_code=code,elapsed_seconds=time.monotonic()-started);save(out/(name+'.process.json'),record);state['processes'].append(record)
        if error:raise error
    try:
        assert a.allow_install,'Installation flag absent; dependent R analysis skipped';runtime=json.loads((a.runtime/'COMPLETED.json').read_bytes());assert runtime['status']=='PASS_PRIVATE_LEAFCUTTER_DEPENDENCY_CANDIDATE_ONLY';prefix=Path(runtime['runtime_environment']);before=inventory(prefix,progress=out/'dependency-before.progress.json');save(out/'dependency-before.json',before);assert before==json.loads((a.runtime/'runtime-before.json').read_bytes());env['PATH']=str(prefix/'bin')+':'+env.get('PATH','');env['CONDA_PREFIX']=str(prefix);env['R_LIBS_SITE']=str(prefix/'lib/R/library');makevars=out/'Makevars';makevars.write_text('CXX14 = '+str(prefix/'bin/x86_64-conda-linux-gnu-g++')+'\nCXX14FLAGS = -O0 -g0\nCXX11FLAGS = -O0 -g0\nCXXFLAGS = -O0 -g0\nCFLAGS = -O0 -g0\n',encoding='utf8',newline='\n');env['R_MAKEVARS_USER']=str(makevars)
        intake=json.loads((a.source/'COMPLETED.json').read_bytes());assert intake['status']=='PASS_THREE_COMPLETE_ORIGINAL_LEAFCUTTER_R_PACKAGE_TREES_ONLY';source=intake['editions'][a.edition];stage=out/'work-source/leafcutter';stage.mkdir(parents=True);bindings={}
        for name,want in source['members'].items():
            original=a.source/a.edition/'source'/name;assert pin(original)=={k:want[k] for k in ['bytes','sha256']}
            if name.startswith('leafcutter/'):
                relative=name.removeprefix('leafcutter/');destination=stage/relative;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes(original.read_bytes());bindings[relative]={k:want[k] for k in ['bytes','sha256']}
        save(out/'original-source-stage.json',dict(revision=source['revision'],R_package_version=source['R_package_version'],stage=str(stage),members=bindings));addons=json.loads((a.addons/'COMPLETED.json').read_bytes());compat=json.loads((a.compatibility/'COMPLETED.json').read_bytes());assert addons['status']=='PASS_EXACT_CRAN_TAILRANK_SOURCE_ONLY' and compat['status']=='PASS_EXACT_R42_COMPATIBLE_BIOBASE_OOMPADATA_SOURCES_ONLY';archives=[]
        for name in ['Biobase','oompaData']:
            row=compat['packages'][name]['archive'];file=a.compatibility/name/row['filename'];assert pin(file)=={k:row[k] for k in ['bytes','sha256']};archives.append((name,file))
        row=addons['archive'];archive=a.addons/row['filename'];assert pin(archive)=={k:row[k] for k in ['bytes','sha256']};archives.append(('TailRank',archive));archive_pins={str(file):pin(file) for name,file in archives};save(out/'addon-archives.json',archive_pins)
        for name,file in archives:execute(name+'-install',[prefix/'bin/R','CMD','INSTALL','--no-multiarch','--library='+str(library),file],600)
        execute('LeafCutter-install',[prefix/'bin/R','CMD','INSTALL','--preclean','--no-multiarch','--library='+str(library),stage],1800);assert all(pin(stage/name)==want for name,want in bindings.items()),'An original source member changed during build';assert all(pin(Path(name))==want for name,want in archive_pins.items());health=out/'health.R';health.write_text(r'''args<-commandArgs(TRUE)
library(jsonlite)
.libPaths(c(args[1],.libPaths()))
library(leafcutter)
ns<-asNamespace("leafcutter")
stopifnot(normalizePath(getNamespaceInfo(ns,"path"))==normalizePath(file.path(args[1],"leafcutter")))
checked<-character()
for(path in list.files(file.path(args[2],"R"),pattern="\\.R$",full.names=TRUE)) {
 for(expr in parse(path,keep.source=FALSE)) {
  if(is.call(expr) && length(expr)>=3 && (identical(expr[[1]],as.name("<-")) || identical(expr[[1]],as.name("="))) && is.symbol(expr[[2]]) && is.call(expr[[3]]) && identical(expr[[3]][[1]],as.name("function"))) {
   name<-as.character(expr[[2]]);expected<-eval(expr[[3]],envir=ns);actual<-get(name,envir=ns,inherits=FALSE)
   stopifnot(identical(paste(deparse(formals(expected)),collapse="\n"),paste(deparse(formals(actual)),collapse="\n")),identical(paste(deparse(body(expected)),collapse="\n"),paste(deparse(body(actual)),collapse="\n")))
   checked<-c(checked,name)
  }
 }
}
stopifnot(length(checked)>10,length(get("stanmodels",ns))==5)
y<-matrix(c(10,20,12,18,9,21,11,19,20,10,18,12,22,8,19,11),ncol=2,byrow=TRUE)
xnull<-matrix(1,nrow=8,ncol=1);colnames(xnull)<-"intercept"
xfull<-cbind(intercept=1,group=rep(c(0,1),each=4))
fit<-dirichlet_multinomial_anova_mc(xfull,xnull,y,robust=FALSE,seed=4711,iter=2000)
stopifnot(is.finite(fit$loglr),is.finite(fit$lrtp),fit$lrtp>=0,fit$lrtp<=1)
saveRDS(fit,file.path(args[3],"literal-fit.rds"))
report<-list(status="PASS_ORIGINAL_LEAFCUTTER_NAMESPACE_AND_LITERAL_FIT_ONLY",version=as.character(packageVersion("leafcutter")),R=R.version.string,package_path=getNamespaceInfo(ns,"path"),namespace_functions_checked=checked,loglr=fit$loglr,df=fit$df,p=fit$lrtp,refit_null_flag=fit$refit_null_flag,fit_null_return_code=fit$fit_null$return_code,fit_full_return_code=fit$fit_full$return_code,analysis_admitted=FALSE)
write_json(report,file.path(args[3],"HEALTH.json"),auto_unbox=TRUE,pretty=TRUE,digits=NA,null="null",na="string")
''',encoding='utf8',newline='\n');execute('health',[prefix/'bin/Rscript','--vanilla',health,library,stage,out],300);report=json.loads((out/'HEALTH.json').read_bytes());assert report['status']=='PASS_ORIGINAL_LEAFCUTTER_NAMESPACE_AND_LITERAL_FIT_ONLY' and report['version']==source['R_package_version'];final=inventory(library,progress=out/'library-final.progress.json');save(out/'library-inventory.json',final);state.update(status='PASS_ORIGINAL_LEAFCUTTER_PRIVATE_R_LIBRARY_AND_LITERAL_FIT_ONLY',runtime_environment=str(prefix),Rscript=str(prefix/'bin/Rscript'),R_library=str(library),source_revision=source['revision'],R_package_version=source['R_package_version'],source_stage=pin(out/'original-source-stage.json'),health=report,library_inventory=pin(out/'library-inventory.json'),compiler_flags=pin(makevars),namespace_patched=False)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n');print('SKIP3 dependent R analysis remains skipped:',repr(failure),flush=True)
    finally:
        if before is not None:
            after=inventory(prefix,progress=out/'dependency-after.progress.json');save(out/'dependency-after.json',after);state['dependency_runtime_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_DEPENDENCY_RUNTIME_CHANGED',error='Complete shared dependency prefix changed during preparation; admit no analysis/library binding')
        assert all(pin(ROOT/n)==want for n,want in sources.items());state['required_action']=ACTION;save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Original namespace and literal technical fit only; complete workflow/paper admission remains separate.')+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
    print(state['status']);return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/library checks')
    raise SystemExit(main())
