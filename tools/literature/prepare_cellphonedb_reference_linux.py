# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install a NEW private original CellPhoneDB5.0.1 reference from exact cached Conda archives and recorded Python artifacts; no tool credit."""
import argparse,hashlib,json,os,shutil,signal,subprocess,sys,time,traceback
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--prefix',required=True,type=Path);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=a.out.absolute();prefix=a.prefix.absolute();assert sys.platform=='linux' and out==out.resolve() and prefix==prefix.resolve() and out.is_relative_to(ROOT/'build') and prefix.is_relative_to(ROOT/'build') and not out.exists() and not prefix.exists() and out.parent.is_dir() and prefix.parent.is_dir() and not out.is_relative_to(prefix) and not prefix.is_relative_to(out);out.mkdir();state=dict(status='SKIP3_CELLPHONEDB_REFERENCE_UNAVAILABLE',implementation_credit=False,whole_goal_complete=False,processes=[],analysis_executed=False);before=None;shared={};env=dict(os.environ)
    for key in list(env):
        if key.startswith(('PYTHON','CONDA_','MAMBA_','PIP_','R_')) or key in ['LD_PRELOAD','LD_LIBRARY_PATH']:env.pop(key,None)
    for name in ['private-home','private-cache','private-config','private-temp']:(out/name).mkdir()
    env.update(HOME=str(out/'private-home'),XDG_CACHE_HOME=str(out/'private-cache'),XDG_CONFIG_HOME=str(out/'private-config'),TMPDIR=str(out/'private-temp'),TMP=str(out/'private-temp'),TEMP=str(out/'private-temp'),MAMBA_ROOT_PREFIX=str(out/'private-cache/mamba-root'),PIP_CACHE_DIR=str(out/'private-cache/pip'),NUMBA_CACHE_DIR=str(out/'private-cache/numba'),PYTHONDONTWRITEBYTECODE='1',PIP_DISABLE_PIP_VERSION_CHECK='1',PIP_CONFIG_FILE='/dev/null',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1',LANG='C.UTF-8',LC_ALL='C.UTF-8')
    def members(group):
        result=[]
        for proc in Path('/proc').iterdir():
            if not proc.name.isdigit():continue
            try:
                values=(proc/'stat').read_text().split(') ',1)[1].split()
                if int(values[2])==group:result.append(dict(pid=int(proc.name),state=values[0],start_ticks=int(values[19])))
            except (OSError,ValueError,IndexError):pass
        return result
    def execute(label,command,timeout=1200):
        command=list(map(str,command));record=dict(command=command,started_at=time.time());save(out/(label+'.STARTED.json'),record);child=None;code=None;failure=None;started=time.monotonic()
        try:
            with (out/(label+'.stdout')).open('xb') as first,(out/(label+'.stderr')).open('xb') as second:
                child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);record['owned_process_group']=child.pid;save(out/(label+'.STARTED.json'),record);code=child.wait(timeout=timeout)
            assert code==0,label+' failed; inspect complete original stdout/stderr'
        except Exception as error:failure=error;record['error']=repr(error)
        finally:
            if child is not None:
                record['owned_before_cleanup']=members(child.pid)
                for sig in [signal.SIGTERM,signal.SIGKILL]:
                    if not any(r['state']!='Z' for r in members(child.pid)):break
                    try:os.killpg(child.pid,sig)
                    except ProcessLookupError:pass
                    deadline=time.monotonic()+3
                    while any(r['state']!='Z' for r in members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
                child.wait(timeout=10);record['owned_live_after_cleanup']=[r for r in members(child.pid) if r['state']!='Z'];assert not record['owned_live_after_cleanup']
            record.update(exit_code=code,elapsed_seconds=time.monotonic()-started);save(out/(label+'.process.json'),record);state['processes'].append(record)
        if failure:raise failure
    try:
        assert a.allow_install,'Installation is not authorized';assert a.allow_download,'Python dependency resolution needs explicit download permission or a future exact cached-wheel setup profile'
        for i,parent in enumerate([out,prefix.parent]):
            probe=parent/('cpdb-case-probe-'+str(i));assert not probe.exists();probe.mkdir();(probe/'case').write_bytes(b'lower');(probe/'CASE').open('xb').write(b'UPPER');assert (probe/'case').read_bytes()==b'lower' and (probe/'CASE').read_bytes()==b'UPPER','Require NEW case-sensitive Linux/WSL setup/cache and prefix parents'
        base=ROOT/'build/literature-review/leafcutter-runtime';source=ROOT/'build/literature-review/cellphonedb-runtime/source-intake-2';intake=json.loads((source/'COMPLETED.json').read_bytes());assert intake['status']=='PASS_EXACT_COMPLETE_PUBLISHED_CELLPHONEDB_SOURCE_DISTRIBUTIONS_ONLY';wheel=intake['versions']['5.0.1']['distribution'];assert intake['versions']['5.0.1']['original_license_body_present'] and pin(source/'5.0.1'/wheel['filename'])=={k:wheel[k] for k in ['bytes','sha256']};state['source_distribution']=wheel;state['source_intake']=pin(source/'COMPLETED.json');records=json.loads((ROOT/'build/leafcutter-case-sensitive-1/setup-1/complete-conda-package-records.json').read_bytes());by={r['name']:r for r in records};locks=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/leafcutter-workflow/setup/dependency-locks.json').read_bytes())['packages'];locked={r['name']:r for r in locks};needed=set();pending=['python','pip','numpy','scipy','scikit-learn']
        while pending:
            name=pending.pop()
            if name.startswith('__') or name in needed:continue
            assert name in by and name in locked,name;needed.add(name);pending.extend(r.split()[0] for r in by[name].get('depends',[]))
        chosen=[locked[name] for name in sorted(needed)];assert len(chosen)==43;save(out/'conda-dependency-locks.json',dict(packages=chosen,derivation='Exact transitive closure of python/pip/numpy/scipy/scikit-learn in the previously source-bound239-package solve; no solver or shared-prefix mutation.'));archives=out/'archives';archives.mkdir();lines=['@EXPLICIT']
        for row in chosen:
            original=base/'workflow-archives-1'/row['filename'];want={k:row[k] for k in ['bytes','sha256']};assert pin(original)==want;target=archives/row['filename'];shutil.copyfile(original,target);assert pin(target)==pin(original)==want;lines.append(target.as_uri()+'#'+row['md5']);shared[str(original)]=want
        installer=base/'r-python-setup-1/micromamba';installer_pin=pin(installer);explicit=out/'explicit.txt';explicit.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n');execute('conda-install',[installer,'create','--offline','--no-rc','-y','-p',prefix,'--file',explicit],1800);env.update(PATH=str(prefix/'bin')+':'+env.get('PATH',''),CONDA_PREFIX=str(prefix));constraints=out/'constraints.txt';constraints.write_text('\n'.join(['numpy==1.26.4','scipy==1.11.4','scikit-learn==1.3.2','pandas==2.2.3','numba==0.58.1','numpy-groupies==0.11.3','scanpy==1.9.8','anndata==0.9.2','ktplotspy==0.1.4','matplotlib==3.8.4','tqdm==4.67.1'])+'\n',encoding='utf8',newline='\n');python=prefix/'bin/python';original_wheel=source/'5.0.1'/wheel['filename'];execute('pip-plan',[python,'-I','-B','-m','pip','install','--dry-run','--report',out/'pip-plan.json','--constraint',constraints,original_wheel],1200);plan=json.loads((out/'pip-plan.json').read_bytes());requirements=[]
        for row in plan['install']:
            url=row['download_info']['url'];digest=row['download_info']['archive_info']['hashes']['sha256'];assert url.startswith(('https://files.pythonhosted.org/','file://')) and len(digest)==64;requirements.append(row['metadata']['name']+' @ '+url+' --hash=sha256:'+digest)
        archive_lock=out/'pip-source-archives.txt';archive_lock.write_text('\n'.join(requirements)+'\n',encoding='utf8',newline='\n');wheels=out/'wheels';wheels.mkdir();execute('pip-wheels',[python,'-I','-B','-m','pip','wheel','--no-deps','--require-hashes','--wheel-dir',wheels,'-r',archive_lock],1800);wheel_pins={f.name:pin(f) for f in wheels.iterdir() if f.is_file()};assert len(wheel_pins)==len(plan['install']);save(out/'complete-built-wheel-pins.json',wheel_pins);execute('pip-install',[python,'-I','-B','-m','pip','install','--no-index','--find-links',wheels,'--report',out/'pip-install.json','--constraint',constraints,'cellphonedb==5.0.1'],1200);execute('pip-check',[python,'-I','-B','-m','pip','check'],120)
        health_code='import importlib.metadata as m,sys,numpy,pandas,numpy_groupies;from cellphonedb.src.core.methods import cpdb_statistical_analysis_helper as h;print(sys.version);print({n:m.version(n) for n in ["cellphonedb","numpy","scipy","scikit-learn","pandas","numba","numpy-groupies","scanpy","anndata","ktplotspy"]});assert m.version("cellphonedb")=="5.0.1" and numpy.__version__=="1.26.4";print("PASS_ORIGINAL_IMPORT_ONLY_NO_ANALYSIS")';execute('original-import-health',[python,'-I','-B','-c',health_code],300);site=prefix/'lib/python3.11/site-packages'
        for name,want in intake['versions']['5.0.1']['members'].items():
            if name.startswith('cellphonedb/'):assert pin(site/name)==want,'Installed original source differs: '+name
        before=inventory(prefix,progress=out/'runtime-before.progress.json');save(out/'runtime-before.json',before);execute('import-health-repeat',[python,'-I','-B','-c',health_code],300);state.update(status='PASS_PRIVATE_ORIGINAL_CELLPHONEDB_501_REFERENCE_IMPORT_ONLY',runtime_environment=str(prefix),runtime_python=str(python),runtime_inventory=pin(out/'runtime-before.json'),original_module_source_unchanged=True,shared_installer_unchanged=pin(installer)==installer_pin,shared_archive_bytes_unchanged=all(pin(Path(name))==want for name,want in shared.items()),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__)),(ROOT/'tools/literature/inventory_hail_runtime_linux.py').relative_to(ROOT).as_posix():pin(ROOT/'tools/literature/inventory_hail_runtime_linux.py')})
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        if before is not None:
            after=inventory(prefix,progress=out/'runtime-after.progress.json');save(out/'runtime-after.json',after);state['runtime_trees_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_ORIGINAL_REFERENCE_PREFIX_CHANGED',error='Complete reference prefix changed during repeat import health')
        state['required_action']='This is private import readiness only, not scientific validation or named-tool credit. Preserve every archive, pip plan/wheel hash, compiler/build/import diagnostic and failed prefix. Missing or forbidden required installation keeps dependent analysis skipped. Correct the named permission, download, dependency, import or source mismatch and retry NEW paths; never patch original requirements or substitute reported5.1,5.0.0 or2.1.4 silently.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Original import readiness only; full original/native method and protocol controls remain required.')+'\n\n'+state['required_action']+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/dependency/preparation checks')
    raise SystemExit(main())
