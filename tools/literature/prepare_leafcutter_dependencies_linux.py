# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Provision NEW isolated R/Stan/Python dependency candidate; source/model workflows remain unadmitted."""
import argparse,json,os,shutil,signal,subprocess,sys,time,traceback
from pathlib import Path
from setup_hail_reference_linux import ROOT,pin,save,group_members
from inventory_hail_runtime_linux import inventory

ACTION='Required action: keep dependent original LeafCutter analyses skipped until exact permitted source, complete runtime and scientific workflow validation pass. Preserve this attempt, archives, resolved plan, all package records and errors. Where installation is permitted correct the named solver/network/library/compiler cause and retry NEW private paths; otherwise supply an allowed runtime or retain SKIP3. Never modify an existing environment, substitute versions or treat dependency health as analysis admission.'

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--environment',required=True,type=Path);p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve();prefix=a.environment.resolve();assert sys.platform=='linux' and out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and prefix.is_relative_to(ROOT/'build') and not out.exists() and not prefix.exists() and not out.is_relative_to(prefix) and not prefix.is_relative_to(out);out.mkdir(parents=True);(out/'setup-at-attempt.py').write_bytes(Path(__file__).read_bytes());sources={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),ROOT/'tools/literature/setup_hail_reference_linux.py',ROOT/'tools/literature/inventory_hail_runtime_linux.py']};state=dict(status='SKIP3_LEAFCUTTER_DEPENDENCY_CANDIDATE_UNAVAILABLE',source_pins=sources,analysis_admitted=False,implementation_credit=False,whole_goal_complete=False,processes=[]);env=dict(os.environ);shared=None
    for key in list(env):
        if key.startswith(('R_','PYTHON','PIP_','MAMBA_','CONDA_','VIRTUAL_ENV')) or key in ['LD_LIBRARY_PATH','LD_PRELOAD','JAVA_HOME']:env.pop(key,None)
    for name in ['private-home','private-temp','private-config','private-cache']:(out/name).mkdir()
    env.update(HOME=str(out/'private-home'),TMPDIR=str(out/'private-temp'),XDG_CONFIG_HOME=str(out/'private-config'),XDG_CACHE_HOME=str(out/'private-cache'),MAMBA_ROOT_PREFIX=str(out/'private-cache/mamba-root'),LANG='C.UTF-8',LC_ALL='C.UTF-8',PYTHONDONTWRITEBYTECODE='1',PYTHONPYCACHEPREFIX=str(out/'private-cache/pycache'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1',R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null')
    def execute(name,command,timeout):
        command=list(map(str,command));child=None;error=None;started=time.monotonic();record=dict(command=command,started_at=time.time(),status='RUNNING_PROCESS_ONLY');save(out/(name+'.STARTED.json'),record);code=None
        try:
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True);record['owned_process_group']=child.pid
                while child.poll() is None:
                    if time.monotonic()-started>timeout:raise TimeoutError(name+' exceeded owned preparation timeout')
                    time.sleep(.2)
                code=child.returncode
            assert code==0,name+' failed; inspect complete stdout/stderr';record['status']='completed-process-only'
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
                child.wait(timeout=10);record['owned_after_cleanup']=group_members(child.pid);record['owned_live_after_cleanup']=[r for r in record['owned_after_cleanup'] if r['state']!='Z']
                if record['owned_live_after_cleanup']:error=RuntimeError('Owned dependency preparation workers remain alive');record['status']='FAIL2_OWNED_CLEANUP_INCOMPLETE'
            record.update(exit_code=code,elapsed_seconds=time.monotonic()-started);save(out/(name+'.process.json'),record);state['processes'].append(record)
        if error:raise error
    try:
        assert a.allow_install,'Installation flag absent; no new prefix is created';bootstrap=ROOT/'build/literature-review/loftee-runtime/reference-setup-1/micromamba';compiler=Path(shutil.which('g++')).resolve();shared=dict(base_python=pin(Path(sys.executable).resolve()),bootstrap=pin(bootstrap),compiler=dict(path=str(compiler),**pin(compiler)));save(out/'shared-before.json',shared);mamba=out/'micromamba';shutil.copyfile(bootstrap,mamba);mamba.chmod(0o700);assert pin(mamba)==shared['bootstrap']
        packages=['r-base=4.2.*','r-rstan=2.21.8','r-stanheaders=2.21.0_7','r-rstantools=2.2.0','r-rcpp=1.0.12','r-rcppeigen=0.3.3.9.3','r-bh=1.78.0_0','r-jsonlite=1.8.8','r-ggplot2=3.5.1','r-r.utils','r-gridextra','r-reshape2','r-hmisc','r-foreach','r-dplyr=1.1.4','r-magrittr','r-stringr','r-domc','r-optparse','r-shiny','r-shinyjs','r-dt','r-intervals','r-shinycssloaders','r-gtable','bioconductor-dirichletmultinomial=1.40.0','r-oompabase','python=3.11','numpy=1.26.4','scipy=1.11.4','scikit-learn=1.3.2','pip','make','gxx_linux-64=12.*'];save(out/'requested-dependencies.json',packages);base=[mamba,'create','--no-rc','--override-channels','-c','conda-forge','-c','bioconda','-p',prefix,*packages];execute('solve',[*base,'--dry-run','--json'],1200);execute('install',[*base,'-y','--json'],1800);records={f.name:json.loads(f.read_bytes()) for f in sorted((prefix/'conda-meta').glob('*.json'))};save(out/'complete-conda-package-records.json',records);assert len(records)>30;env['PATH']=str(prefix/'bin')+':'+env.get('PATH','');env['CONDA_PREFIX']=str(prefix);env['R_LIBS_USER']=str(prefix/'lib/R/library');env['R_LIBS_SITE']=str(prefix/'lib/R/library');before=inventory(prefix,progress=out/'prefix-before.progress.json');save(out/'runtime-before.json',before)
        rcode='cat(R.version.string,"\\n"); p<-installed.packages(); for(n in c("rstan","StanHeaders","rstantools","Rcpp","RcppEigen","DirichletMultinomial","doMC","jsonlite")) {stopifnot(n %in% p[,"Package"]);cat(n,p[n,"Version"],"\\n")}; library(rstan); stopifnot(as.character(packageVersion("rstan"))=="2.21.8"); cat("PASS_ORIGINAL_RSTAN_DEPENDENCY_NAMESPACE_ONLY\\n")';execute('R-health',[prefix/'bin/Rscript','--vanilla','-e',rcode],180);pcode='import numpy,scipy,sklearn,sys; assert numpy.__version__=="1.26.4" and scipy.__version__=="1.11.4" and sklearn.__version__=="1.3.2"; print(sys.version); print(numpy.__version__,scipy.__version__,sklearn.__version__); print("PASS_PYTHON_PHENOTYPE_DEPENDENCIES_ONLY")';execute('Python-health',[prefix/'bin/python','-I','-B','-X','pycache_prefix='+str(out/'private-cache/pycache'),'-c',pcode],120);after=inventory(prefix,progress=out/'prefix-after.progress.json');save(out/'runtime-after.json',after);assert before==after,'Candidate runtime tree changed during namespace health';state.update(status='PASS_PRIVATE_LEAFCUTTER_DEPENDENCY_CANDIDATE_ONLY',runtime_environment=str(prefix),Rscript=str(prefix/'bin/Rscript'),python=str(prefix/'bin/python'),runtime_inventory=pin(out/'runtime-before.json'),runtime_trees_unchanged=True,conda_records=pin(out/'complete-conda-package-records.json'),leafcutter_installed=False,TailRank_installed=False)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n');print('SKIP3 dependent original analyses remain skipped:',repr(failure),flush=True)
    finally:
        if shared:
            after=dict(base_python=pin(Path(sys.executable).resolve()),bootstrap=pin(bootstrap),compiler=dict(path=str(compiler),**pin(compiler)));save(out/'shared-after.json',after);state['shared_runtime_unchanged']=after==shared
            if after!=shared:state.update(status='FAIL2_PREEXISTING_RUNTIME_CHANGED',error='Named shared runtime identity changed; keep all analyses unadmitted')
        assert all(pin(ROOT/n)==v for n,v in sources.items());state['required_action']=ACTION;save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Dependency namespace health only. LeafCutter/TailRank and scientific workflows are not yet admitted.')+'\n\n'+ACTION+'\n',encoding='utf8',newline='\n')
    print(state['status'],flush=True);return 0 if state['status'].startswith('PASS_') else 3

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact private preparation checks')
    raise SystemExit(main())
