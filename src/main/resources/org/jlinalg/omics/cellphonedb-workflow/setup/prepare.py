# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install a NEW exact private CellPhoneDB runtime from pinned original archives, with explicit permissions and owned cleanup; analysis never installs software."""
import argparse,hashlib,json,os,shutil,signal,subprocess,sys,time,traceback,urllib.request,uuid,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from inventory import inventory


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def members(group):
    result=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:
            fields=(path/'stat').read_text().split(') ',1)[1].split()
            if int(fields[2])==group:result.append(dict(pid=int(path.name),state=fields[0],start_ticks=int(fields[19])))
        except (OSError,ValueError,IndexError):pass
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--prefix',required=True,type=Path);parser.add_argument('--micromamba',required=True,type=Path);parser.add_argument('--archive-cache',action='append',default=[],type=Path);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');a=parser.parse_args();out=a.out.absolute();prefix=a.prefix.absolute();kit=Path(__file__).resolve().parent.parent;assert sys.platform=='linux' and out==out.resolve() and out.parent.is_dir() and not out.exists() and prefix==prefix.resolve() and prefix.parent.is_dir() and not prefix.exists() and not prefix.is_relative_to(out) and not out.is_relative_to(prefix),'Require NEW distinct canonical Linux setup/prefix directories with existing parents';out.mkdir();state=dict(status='SKIP3_CELLPHONEDB_RUNTIME_UNAVAILABLE',analysis_executed=False,processes=[],filesystem_checks=[]);before=None;shared={};env=dict(os.environ)
    for key in list(env):
        if key.startswith(('PYTHON','CONDA_','MAMBA_','PIP_','R_')) or key in ['LD_LIBRARY_PATH','LD_PRELOAD']:env.pop(key,None)
    for name in ['private-home','private-cache','private-temp','private-config']:(out/name).mkdir()
    env.update(HOME=str(out/'private-home'),TMPDIR=str(out/'private-temp'),TMP=str(out/'private-temp'),TEMP=str(out/'private-temp'),XDG_CACHE_HOME=str(out/'private-cache'),XDG_CONFIG_HOME=str(out/'private-config'),MAMBA_ROOT_PREFIX=str(out/'private-cache/mamba-root'),NUMBA_CACHE_DIR=str(out/'private-cache/numba'),PIP_CACHE_DIR=str(out/'private-cache/pip'),PIP_DISABLE_PIP_VERSION_CHECK='1',PIP_CONFIG_FILE='/dev/null',PYTHONDONTWRITEBYTECODE='1',LANG='C.UTF-8',LC_ALL='C.UTF-8',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1')
    def execute(label,command,timeout=1200):
        command=list(map(str,command));record=dict(command=command,started_at=time.time());child=None;code=None;error=None;started=time.monotonic()
        try:
            with (out/(label+'.stdout')).open('xb') as first,(out/(label+'.stderr')).open('xb') as second:
                child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);record['owned_process_group']=child.pid;save(out/(label+'.STARTED.json'),record);code=child.wait(timeout=timeout)
            assert code==0,label+' failed; inspect complete retained stdout/stderr'
        except Exception as failure:error=failure;record['error']=repr(failure)
        finally:
            if child is not None:
                record['owned_before_cleanup']=members(child.pid)
                for sig in [signal.SIGTERM,signal.SIGKILL]:
                    if not any(p['state']!='Z' for p in members(child.pid)):break
                    try:os.killpg(child.pid,sig)
                    except ProcessLookupError:pass
                    deadline=time.monotonic()+3
                    while any(p['state']!='Z' for p in members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
                child.wait(timeout=10);record['owned_live_after_cleanup']=[p for p in members(child.pid) if p['state']!='Z']
                if record['owned_live_after_cleanup']:error=RuntimeError('Owned installer descendants remain live')
            record.update(exit_code=code,elapsed_seconds=time.monotonic()-started);save(out/(label+'.process.json'),record);state['processes'].append(record)
        if error:raise error
    def case_sensitive(parent):
        probe=parent/('.cellphonedb-case-probe-'+uuid.uuid4().hex);probe.mkdir();(probe/'case').write_bytes(b'lower');row=dict(parent=str(parent),probe=str(probe),case_sensitive=False);state['filesystem_checks'].append(row)
        try:(probe/'CASE').open('xb').write(b'UPPER')
        except FileExistsError:raise RuntimeError('Required Linux package filenames would collide. Use NEW permitted case-sensitive Linux/WSL setup/cache and prefix directories; keep dependent analysis skipped')
        assert (probe/'case').read_bytes()==b'lower' and (probe/'CASE').read_bytes()==b'UPPER';row['case_sensitive']=True
    try:
        assert a.allow_install,'Installation permission flag absent; no software installed';profile=json.loads((kit/'profiles.json').read_bytes());locks=json.loads((kit/'setup/dependency-locks.json').read_bytes());assert profile['edition']=='5.0.1'
        for name,want in profile['files'].items():
            assert pin(kit/name)==want,'Source/setup profile mismatch: '+name;copy=out/'kit-source'/name;copy.parent.mkdir(parents=True,exist_ok=True);copy.write_bytes((kit/name).read_bytes())
        (out/'kit-source/profiles.json').write_bytes((kit/'profiles.json').read_bytes());assert pin(a.micromamba.resolve())==locks['installer'],'Supplied installer does not match the admitted exact micromamba identity';shared[str(a.micromamba.resolve())]=pin(a.micromamba.resolve());case_sensitive(out);case_sensitive(prefix.parent);archives=out/'archives';archives.mkdir();paths={}
        for row in [*locks['conda_packages'],*locks['python_archives']]:
            name=row['filename'];assert Path(name).name==name and row['url'].startswith(('https://conda.anaconda.org/conda-forge/','https://files.pythonhosted.org/'));target=archives/name;cached=next((home/name for home in [kit/'original',*a.archive_cache] if (home/name).is_file()),None)
            if cached is not None:shared[str(cached.resolve())]=pin(cached);shutil.copyfile(cached,target)
            else:
                assert a.allow_download,'Required archive missing and download permission absent: '+name
                with urllib.request.urlopen(row['url'],timeout=120) as response,target.open('xb') as stream:shutil.copyfileobj(response,stream)
            assert pin(target)=={k:row[k] for k in ['bytes','sha256']},'Exact archive mismatch: '+name;paths[name]=target
        explicit=out/'explicit.txt';explicit.write_text('@EXPLICIT\n'+''.join(paths[row['filename']].as_uri()+'#'+row['md5']+'\n' for row in locks['conda_packages']),encoding='utf8',newline='\n');execute('conda-install',[a.micromamba.resolve(),'create','--no-rc','--offline','-y','-p',prefix,'--file',explicit],1800);env.update(PATH=str(prefix/'bin')+':'+env.get('PATH',''),CONDA_PREFIX=str(prefix));python=prefix/'bin/python';requirements=out/'pinned-python-archives.txt';requirements.write_text(''.join(row['name']+' @ '+paths[row['filename']].as_uri()+' --hash=sha256:'+row['sha256']+'\n' for row in locks['python_archives']),encoding='utf8',newline='\n');wheels=out/'wheels';wheels.mkdir();execute('pip-wheels',[python,'-I','-B','-m','pip','wheel','--no-deps','--no-index','--no-build-isolation','--require-hashes','--wheel-dir',wheels,'-r',requirements],1200);save(out/'built-wheel-pins.json',{p.name:pin(p) for p in wheels.iterdir() if p.is_file()});execute('pip-install',[python,'-I','-B','-m','pip','install','--no-index','--find-links',wheels,'--report',out/'pip-install.json','cellphonedb==5.0.1'],1200);execute('pip-check',[python,'-I','-B','-m','pip','check'],120);execute('original-health',[python,'-I','-B',kit/'setup/health.py',kit,out/'health.json'],300);health=json.loads((out/'health.json').read_bytes());assert health['status']=='PASS_ORIGINAL_CELLPHONEDB_NAMESPACE_ONLY';before=inventory(prefix,progress=out/'runtime-before.progress.json');save(out/'runtime-before.json',before);execute('repeat-health',[python,'-I','-B',kit/'setup/health.py',kit,out/'repeat-health.json'],300);assert health==json.loads((out/'repeat-health.json').read_bytes());state.update(status='PASS_BOUND_ORIGINAL_CELLPHONEDB_WORKFLOW_RUNTIME_ONLY',edition='5.0.1',runtime_environment=str(prefix),runtime_python=str(python),profile=pin(kit/'profiles.json'),runtime_inventory=pin(out/'runtime-before.json'),health=health,namespace_patched=False,analysis_installed_software=False)
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        if before is not None:
            after=inventory(prefix,progress=out/'runtime-after.progress.json');save(out/'runtime-after.json',after);state['runtime_trees_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_CELLPHONEDB_RUNTIME_CHANGED_DURING_PREPARATION',error='Complete installed prefix changed during repeat health')
        state['shared_inputs_unchanged']=all(pin(Path(name))==want for name,want in shared.items())
        if not state['shared_inputs_unchanged']:state.update(status='FAIL2_SHARED_INSTALLER_OR_ARCHIVE_CHANGED',error='A read-only shared input changed')
        state['required_action']='Setup health is not analysis admission. SKIP3: keep dependent analysis skipped if required installation/download/runtime use is unavailable or forbidden. Provide explicit --allow-install and, for uncached artifacts, --allow-download, the exact permitted micromamba and NEW case-sensitive Linux setup/prefix paths. Preserve every partial, original archive, wheel build, health error and process log; correct the named cause and retry NEW paths. RUN installs nothing. FAIL2 admits no partial result. No database or paper version is substituted.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Complete exact dependency/source import health only; no analysis was executed.')+'\n\n'+state['required_action']+'\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''));return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/runtime/preparation checks')
    raise SystemExit(main())
