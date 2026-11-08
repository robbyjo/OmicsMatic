# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare complete exact Palantir imports in NEW private dependency/edition directories; never mutate the shared interpreter."""
import argparse,hashlib,json,os,shutil,signal,subprocess,sys,time,traceback,urllib.error,urllib.request,zipfile
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_hail_reference_linux import ROOT,pin,save
from inventory_hail_runtime_linux import inventory

class Unavailable(Exception):pass

def members(group):
    result=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:
            raw=(path/'stat').read_text();fields=raw[raw.rindex(')')+2:].split()
            if int(fields[2])==group and fields[0]!='Z':result.append(int(path.name))
        except (OSError,ValueError,IndexError):pass
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');p.add_argument('--reuse-downloads',type=Path);a=p.parse_args();out=a.out.absolute();assert sys.platform=='linux' and out==out.resolve() and out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);state=dict(status='FAIL2_PALANTIR_REFERENCE_SETUP',implementation_credit=False,whole_goal_complete=False,processes=[]);before=None;prefix=None;exit_code=2;env=dict(os.environ)
    for key in list(env):
        if key.startswith(('PYTHON','PIP_','CONDA_','R_','JAX_')) or key in ['LD_PRELOAD','LD_LIBRARY_PATH','JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS']:env.pop(key,None)
    for name in ['private-home','private-cache','private-config','private-temp']:(out/name).mkdir()
    env.update(HOME=str(out/'private-home'),TMPDIR=str(out/'private-temp'),XDG_CACHE_HOME=str(out/'private-cache'),XDG_CONFIG_HOME=str(out/'private-config'),NUMBA_CACHE_DIR=str(out/'private-cache/numba'),MPLCONFIGDIR=str(out/'private-config/matplotlib'),MPLBACKEND='Agg',PIP_CONFIG_FILE='/dev/null',PIP_NO_CACHE_DIR='1',PIP_DISABLE_PIP_VERSION_CHECK='1',PYTHONDONTWRITEBYTECODE='1',PYTHONHASHSEED='0',LANG='C.UTF-8',LC_ALL='C.UTF-8',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1',JAX_PLATFORMS='cpu')
    def execute(name,command,timeout=600,dependency=False):
        command=list(map(str,command));record=dict(name=name,command=command);child=None;started=time.monotonic()
        try:
            with (out/(name+'.stdout')).open('xb') as first,(out/(name+'.stderr')).open('xb') as second:
                child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=first,stderr=second,start_new_session=True);record['owned_process_group']=child.pid;save(out/(name+'.STARTED.json'),record);code=child.wait(timeout=timeout)
            record['exit_code']=code
            if code and dependency:raise Unavailable(name+' could not supply a complete compatible runtime; retain and read full stdout/stderr, resolve the named dependency/network/permission cause and retry NEW paths')
            assert code==0,name+' failed; inspect full retained logs'
        finally:
            if child is not None:
                for sig in [signal.SIGTERM,signal.SIGKILL]:
                    if not members(child.pid):break
                    try:os.killpg(child.pid,sig)
                    except ProcessLookupError:pass
                    end=time.monotonic()+3
                    while members(child.pid) and time.monotonic()<end:time.sleep(.05)
                child.wait(timeout=10);record['owned_live_after_cleanup']=members(child.pid);assert not record['owned_live_after_cleanup']
            record['elapsed_seconds']=time.monotonic()-started;save(out/(name+'.process.json'),record);state['processes'].append(record)
    try:
        if not a.allow_install:raise Unavailable('Installation is not permitted: --allow-install absent. Skip all dependent Palantir analysis; obtain permitted software setup or retain SKIP3.')
        if not a.allow_download and a.reuse_downloads is None:raise Unavailable('This first runtime preparation requires dependency resolution/download: --allow-download absent. Skip dependent analysis until exact permitted archives/locks are available.')
        intake=ROOT/'build/literature-review/palantir-runtime/source-intake-3';source=json.loads((intake/'SOURCE.json').read_bytes());assert source['status']=='PASS_EXACT_OFFICIAL_PALANTIR_SOURCE_ONLY' and set(source['versions'])=={'1.4.4','1.4.6'};setup=ROOT/'build/cellphonedb-workflow-case-sensitive-1/setup-1';binding=json.loads((setup/'COMPLETED.json').read_bytes());assert binding['status']=='PASS_BOUND_ORIGINAL_CELLPHONEDB_WORKFLOW_RUNTIME_ONLY';prefix=Path(binding['runtime_environment']);python=Path(binding['runtime_python']);before=inventory(prefix,progress=out/'shared-before.progress.json');save(out/'shared-before.json',before);assert before==json.loads((setup/'runtime-after.json').read_bytes()),'Shared interpreter tree changed before setup';pins={str(p):pin(p) for p in [Path(__file__).resolve(),intake/'SOURCE.json',setup/'COMPLETED.json']}
        constraints='numpy==1.26.4\npandas==2.2.3\nscipy==1.11.4\nscikit-learn==1.3.2\nanndata==0.11.4\njax==0.5.3\njaxlib==0.5.3\n';(out/'constraints.txt').write_text(constraints,encoding='utf8');cache=a.reuse_downloads.absolute() if a.reuse_downloads else None
        if cache is not None:
            assert cache==cache.resolve() and cache.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and cache!=out and not out.is_relative_to(cache);cached=json.loads((cache/'dependency-locks.json').read_bytes());assert cached['source_profile']==pin(intake/'SOURCE.json') and cached['shared_interpreter_receipt']==pin(setup/'COMPLETED.json') and (cache/'constraints.txt').read_text(encoding='utf8')==constraints;shutil.copyfile(cache/'pip-plan.json',out/'pip-plan.json');save(out/'reused-downloads.json',dict(cache=str(cache),dependency_locks=pin(cache/'dependency-locks.json'),plan=pin(cache/'pip-plan.json'),software_prefix_reused=False,network_used=False));pins[str(cache/'dependency-locks.json')]=pin(cache/'dependency-locks.json');pins[str(cache/'pip-plan.json')]=pin(cache/'pip-plan.json')
        else:
            command=[python,'-I','-B','-m','pip','install','--dry-run','--ignore-installed','--only-binary=:all:','--index-url','https://pypi.org/simple','--report',out/'pip-plan.json','--constraint',out/'constraints.txt','palantir==1.4.6'];execute('exact-dependency-resolution',command,600,True)
        plan=json.loads((out/'pip-plan.json').read_bytes());assert 1<=len(plan['install'])<=100;wheels=out/'wheels';wheels.mkdir();locks=[];total=0
        for row in plan['install']:
            name=row['metadata']['name'];version=row['metadata']['version'];download=row['download_info'];url=download['url'];filename=url.rsplit('/',1)[-1];sha=download['archive_info']['hashes']['sha256'];assert url.startswith('https://files.pythonhosted.org/') and filename.endswith('.whl') and Path(filename).name==filename
            metadata_url='https://pypi.org/pypi/'+name+'/'+version+'/json'
            if cache is not None:raw=(cache/('pypi-'+name+'-'+version+'.json')).read_bytes();assert len(raw)<=8388608
            else:
                with urllib.request.urlopen(urllib.request.Request(metadata_url,headers={'User-Agent':'OmicsMatic-source-review'}),timeout=60) as response:raw=response.read(8388609);assert len(raw)<=8388608
            meta=json.loads(raw);matches=[v for v in meta['urls'] if v['url']==url and v['filename']==filename and v['digests']['sha256']==sha and not v['yanked']];assert len(matches)==1;published=matches[0];assert published['size']<=536870912 and total+published['size']<=2147483648;total+=published['size'];(out/('pypi-'+name+'-'+version+'.json')).write_bytes(raw);file=wheels/filename
            if cache is not None:shutil.copyfile(cache/'wheels'/filename,file)
            else:
                with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-review'}),timeout=120) as response,file.open('xb') as target:
                    size=0
                    while block:=response.read(1048576):size+=len(block);assert size<=published['size'];target.write(block)
            assert pin(file)==dict(bytes=published['size'],sha256=sha)
            with zipfile.ZipFile(file) as z:
                assert len(z.namelist())==len(set(z.namelist())) and z.testzip() is None
                for member in z.namelist():assert not member.startswith('/') and '..' not in Path(member).parts and '\\' not in member
            locks.append(dict(name=name,version=version,filename=filename,url=url,bytes=published['size'],sha256=sha))
        if cache is not None:assert locks==cached['archives'],'Cached plan/archive closure differs'
        current=[v for v in locks if v['name'].lower()=='palantir'];assert len(current)==1 and current[0]['version']=='1.4.6' and current[0]['sha256']==source['versions']['1.4.6']['wheel']['sha256'];save(out/'dependency-locks.json',dict(schema_version=1,python_version='3.11.17',archives=locks,total_archive_bytes=total,source_profile=pin(intake/'SOURCE.json'),CPU_only=True,shared_interpreter_receipt=pin(setup/'COMPLETED.json')));dependencies=out/'dependencies';nonpalantir=[wheels/v['filename'] for v in locks if v['name'].lower()!='palantir'];execute('install-locked-dependencies',[python,'-I','-B','-m','pip','install','--no-index','--no-deps','--no-compile','--target',dependencies,*nonpalantir],600,True);editions={}
        for version,original in source['versions'].items():
            archive=intake/version/original['wheel']['filename'];assert pin(archive)=={k:original['wheel'][k] for k in ['bytes','sha256']};target=out/'editions'/version;execute('install-original-'+version,[python,'-I','-B','-m','pip','install','--no-index','--no-deps','--no-compile','--target',target,archive],180,True);health=out/('health-'+version+'.py');health.write_text('import sys,os,json,importlib.metadata as m\nfrom pathlib import Path\nsys.path[:0]='+repr([str(target),str(dependencies)])+'\nassert sys.flags.ignore_environment==0 and sys.flags.safe_path and sys.flags.no_user_site and sys.flags.hash_randomization==0 and os.environ["PYTHONHASHSEED"]=="0"\nimport palantir,numpy,pandas,scipy,scanpy,anndata,mellon,jax,sklearn\nassert palantir.__version__=='+repr(version)+'\nassert Path(palantir.__file__).resolve().is_relative_to(Path('+repr(str(target))+'))\nfor module in [numpy,pandas,scipy,scanpy,anndata,mellon,jax,sklearn]:assert Path(module.__file__).resolve().is_relative_to(Path('+repr(str(dependencies))+'))\nprint(json.dumps(dict(status="PASS_COMPLETE_UNCHANGED_ORIGINAL_PALANTIR_IMPORT_ONLY",edition=palantir.__version__,python=sys.version,hash_seed=os.environ["PYTHONHASHSEED"],versions={name:m.version(name) for name in ["palantir","numpy","pandas","scipy","scanpy","anndata","mellon","jax","jaxlib","scikit-learn"]},namespace_functions_patched=False,pickle_loaded=False)))\n',encoding='utf8');execute('full-import-health-'+version,[python,'-B','-P','-s',health],300,True);report=json.loads((out/('full-import-health-'+version+'.stdout')).read_bytes())
            records={}
            for member,want in original['wheel_members'].items():
                if member.endswith('.dist-info/RECORD'):records[member]=dict(original_wheel=want,installed=pin(target/member),policy='Installer-generated RECORD is retained separately; original scientific source, license, metadata and every other wheel member must match exactly.')
                else:assert pin(target/member)==want,'Original edition source/license/metadata member changed: '+member
            assert len(records)==1;save(out/('installer-record-'+version+'.json'),records);assert {p.relative_to(target).as_posix() for p in (target/'palantir').rglob('*') if p.is_file()}=={n for n in original['wheel_members'] if n.startswith('palantir/')},'Unexpected original package member'
            tree=inventory(target,progress=out/('edition-'+version+'.progress.json'));save(out/('edition-'+version+'.json'),tree);editions[version]=dict(path=str(target),health=report,inventory=pin(out/('edition-'+version+'.json')),original_wheel=original['wheel'])
        deps_tree=inventory(dependencies,progress=out/'dependencies.progress.json');save(out/'dependencies.json',deps_tree);assert all(pin(Path(n))==v for n,v in pins.items());state.update(status='PASS_COMPLETE_PRIVATE_ORIGINAL_PALANTIR_IMPORTS_ONLY',python=str(python),shared_prefix=str(prefix),dependencies=str(dependencies),dependency_inventory=pin(out/'dependencies.json'),dependency_locks=pin(out/'dependency-locks.json'),editions=editions,source_pins=pins,namespace_functions_patched=False,pickle_loaded=False,analysis_admitted=False);exit_code=0
    except (Unavailable,urllib.error.URLError,PermissionError) as error:state.update(status='SKIP3_PALANTIR_RUNTIME_UNAVAILABLE',error=repr(error));exit_code=3;(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n')
    finally:
        if before is not None:
            after=inventory(prefix,progress=out/'shared-after.progress.json');save(out/'shared-after.json',after);state['shared_runtime_unchanged']=before==after
            if before!=after:state.update(status='FAIL2_PALANTIR_SHARED_RUNTIME_CHANGED',error='Shared interpreter changed; no reference or analysis admitted');exit_code=2
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\nSKIP3: skip all dependent Palantir analysis if required software/download/installation is unavailable or forbidden. Read the exact error and complete logs; obtain permitted compatible dependencies and retry NEW private paths. FAIL2: no reference or analysis is admitted; preserve originals and failed setup, resolve the named source/hash/namespace problem, and retry NEW paths. Never refresh an old binding hash. RUN must not install. This is import preparation only, not validated trajectory fitting or paper reproduction. Both exact editions retain their distinct random-generator behavior; neither is inferred to be the unreported paper edition. The shared interpreter stays unchanged. Original diagnostic suppression is not modified. CPU imports only; optional GAM/FCS extras and other unrequested analysis are not admitted.\n',encoding='utf8')
    print(state['status'],state.get('error',''));return exit_code

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact runtime/source checks')
    raise SystemExit(main())
