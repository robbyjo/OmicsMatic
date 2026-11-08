# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Separate exact scFates installation over a bound read-only project Python base. No analysis is run."""
import argparse,hashlib,json,os,shutil,signal,subprocess,sys,time,traceback,urllib.error,urllib.request
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from inventory import inventory

class Unavailable(Exception):pass
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def canonical_new(path):
    path=path.absolute();assert path==path.resolve() and not path.exists() and path.parent.is_dir() and path.parent==path.parent.resolve(),'Require NEW paths under existing canonical unlinked parents';return path
def members(group):
    result=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:
            raw=(path/'stat').read_text();v=raw[raw.rindex(')')+2:].split()
            if int(v[2])==group and v[0]!='Z':result.append(int(path.name))
        except (OSError,ValueError,IndexError):pass
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--prefix',type=Path,required=True);p.add_argument('--base-runtime',type=Path,required=True);p.add_argument('--archive-cache',type=Path,action='append',default=[]);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=canonical_new(a.out);out.mkdir();state=dict(status='FAIL2_SCFATES_RUNTIME_SETUP',analysis_admitted=False,analysis_installed_software=False,namespace_patched=False,processes=[]);exit_code=2;before=None;base=None;prefix=None;kit=Path(__file__).resolve().parents[1]
    try:
        if sys.platform!='linux' or __import__('platform').machine() not in ['x86_64','amd64']:raise Unavailable('Require Linux x86_64, including WSL; skip dependent analysis on unsupported platforms')
        if not a.allow_install:raise Unavailable('Installation is forbidden or --allow-install absent; skip all dependent analysis')
        if not a.base_runtime.is_file():raise Unavailable('Required bound project Python base receipt is missing; export/prepare the permitted CellPhoneDB runtime base first, or keep dependent analysis skipped')
        profile=json.loads((kit/'profiles.json').read_bytes());profile_pin=pin(kit/'profiles.json')
        for name,want in profile['files'].items():assert pin(kit/name)==want,'Captured setup/source member differs: '+name
        base_receipt=a.base_runtime.resolve();raw=base_receipt.read_bytes();binding=json.loads(raw);base=Path(binding['runtime_environment']);python=Path(binding['runtime_python']);assert binding['status']=='PASS_BOUND_ORIGINAL_CELLPHONEDB_WORKFLOW_RUNTIME_ONLY' and binding['runtime_trees_unchanged'] and binding['shared_inputs_unchanged'] and not binding['namespace_patched'];assert binding['profile']==profile['base_profile'] and python==base/'bin/python' and base==base.resolve();before=inventory(base,progress=out/'base-before.progress.json');save(out/'base-before.json',before);assert pin(base_receipt)==dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());assert pin(base_receipt.parent/'runtime-before.json')==binding['runtime_inventory'];expected=json.loads((base_receipt.parent/'runtime-before.json').read_bytes());assert before==expected,'Bound Python base changed; never refresh its hashes';prefix=canonical_new(a.prefix);assert not prefix.is_relative_to(base) and not base.is_relative_to(prefix) and not prefix.is_relative_to(out) and not out.is_relative_to(prefix),'Runtime/output overlap';prefix.mkdir();probe=prefix/'case-probe';probe.mkdir();(probe/'A').write_bytes(b'UPPER')
        try:
            with (probe/'a').open('xb') as stream:stream.write(b'lower')
        except FileExistsError:raise Unavailable('Runtime prefix filesystem is case-insensitive. Preserve this failed prefix; retry NEW paths on permitted case-sensitive Linux storage')
        assert (probe/'A').read_bytes()==b'UPPER' and (probe/'a').read_bytes()==b'lower';env=dict(os.environ)
        for key in list(env):
            if key.startswith(('PYTHON','PIP_','CONDA_','R_','JAX_')) or key in ['LD_LIBRARY_PATH','LD_PRELOAD','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:env.pop(key,None)
        for name in ['private-home','private-cache','private-config','private-temp']:(out/name).mkdir()
        env.update(HOME=str(out/'private-home'),TMPDIR=str(out/'private-temp'),XDG_CACHE_HOME=str(out/'private-cache'),XDG_CONFIG_HOME=str(out/'private-config'),MPLCONFIGDIR=str(out/'private-config/matplotlib'),NUMBA_CACHE_DIR=str(out/'private-cache/numba'),PYTHONDONTWRITEBYTECODE='1',PYTHONHASHSEED='0',PIP_CONFIG_FILE='/dev/null',PIP_NO_CACHE_DIR='1',PIP_DISABLE_PIP_VERSION_CHECK='1',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1',MPLBACKEND='Agg',JAX_PLATFORMS='cpu',LANG='C.UTF-8',LC_ALL='C.UTF-8')
        def execute(name,command):
            command=list(map(str,command));record=dict(name=name,command=command);child=None;started=time.monotonic()
            try:
                with (out/(name+'.stdout')).open('xb') as first,(out/(name+'.stderr')).open('xb') as second:
                    child=subprocess.Popen(command,env=env,cwd=out,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);record['owned_process_group']=child.pid;save(out/(name+'.STARTED.json'),record);code=child.wait(timeout=600)
                record['exit_code']=code
                if code:raise Unavailable(name+' failed: read retained stdout/stderr; resolve the named software/permission cause and retry NEW paths')
            finally:
                if child:
                    for sig in [signal.SIGTERM,signal.SIGKILL]:
                        if not members(child.pid):break
                        try:os.killpg(child.pid,sig)
                        except ProcessLookupError:pass
                        end=time.monotonic()+3
                        while members(child.pid) and time.monotonic()<end:time.sleep(.05)
                    child.wait(timeout=10);record['owned_live_after_cleanup']=members(child.pid);assert not record['owned_live_after_cleanup']
                record['elapsed_seconds']=time.monotonic()-started;save(out/(name+'.process.json'),record);state['processes'].append(record)
        archives=out/'archives';archives.mkdir();locked=[]
        for entry in profile['dependency_archives']:
            file=archives/entry['filename'];want={k:entry[k] for k in ['bytes','sha256']};found=None
            for cache in [kit/'original',*a.archive_cache]:
                candidate=cache/entry['filename']
                if candidate.is_file() and not candidate.is_symlink() and pin(candidate)==want:found=candidate;break
            if found:shutil.copyfile(found,file)
            elif not a.allow_download:raise Unavailable('Exact archive missing and download not permitted: '+entry['filename']+'; supply a verified archive cache or explicit permitted download, otherwise skip analysis')
            else:
                assert entry['url'].startswith('https://files.pythonhosted.org/')
                with urllib.request.urlopen(urllib.request.Request(entry['url'],headers={'User-Agent':'OmicsMatic-runtime-preparation'}),timeout=120) as response,file.open('xb') as stream:
                    size=0
                    while block:=response.read(1048576):size+=len(block);assert size<=entry['bytes'];stream.write(block)
            assert pin(file)==want,'Downloaded/cached dependency differs';locked.append(file)
        dependencies=prefix/'dependencies';execute('install-exact-dependencies',[python,'-I','-B','-m','pip','install','--no-index','--no-deps','--no-compile','--target',dependencies,*locked])
        initial=inventory(prefix,progress=out/'runtime-before.progress.json');save(out/'runtime-before.json',initial);execute('complete-namespace-health',[python,'-B','-P','-s',kit/'setup/health.py','--prefix',prefix,'--profile',kit/'profiles.json','--out',out/'health.json']);after=inventory(prefix,progress=out/'runtime-after.progress.json');save(out/'runtime-after.json',after);assert initial==after;assert pin(base_receipt)==dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());assert pin(kit/'profiles.json')==profile_pin and all(pin(kit/name)==want for name,want in profile['files'].items()),'Executed setup/source changed';state.update(status='PASS_BOUND_ORIGINAL_SCFATES_WORKFLOW_RUNTIME_ONLY',profile=profile_pin,runtime_environment=str(prefix),runtime_python=str(python),base_environment=str(base),base_receipt=str(base_receipt),base_receipt_pin=pin(base_receipt),base_inventory=pin(out/'base-before.json'),runtime_inventory=pin(out/'runtime-before.json'),health=json.loads((out/'health.json').read_bytes()),runtime_trees_unchanged=True,analysis_installed_software=False,namespace_patched=False);exit_code=0
    except (Unavailable,urllib.error.URLError,PermissionError) as failure:state.update(status='SKIP3_SCFATES_RUNTIME_UNAVAILABLE',error=str(failure));exit_code=3;(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        if before is not None:
            after=inventory(base,progress=out/'base-after.progress.json');save(out/'base-after.json',after);state['shared_inputs_unchanged']=after==before
            if after!=before:state.update(status='FAIL2_SCFATES_BASE_RUNTIME_CHANGED',error='Complete read-only Python base changed; no runtime/analysis admitted');exit_code=2
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\nSKIP3: skip dependent scFates analysis if the exact base, platform, archives, filesystem or installation/download permission is unavailable. Read the named error and complete logs. Provide the exact permitted project Python base and archives, or keep analysis skipped. Use NEW receipt/prefix paths. FAIL2 admits no runtime or analysis: preserve originals/failed prefixes, correct the named source/hash/input cause, and retry NEW paths. Never refresh old binding hashes. RUN installs nothing. This stage installs original software only and does not fit or reproduce a paper. Original diagnostic suppression is retained. CPU Linux x86_64 only; optional R/GAM, GPU and unrelated scFates analyses are not admitted.\n',encoding='utf8',newline='\n')
    print(state['status'],state.get('error',''));return exit_code

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original runtime checks')
    raise SystemExit(main())
