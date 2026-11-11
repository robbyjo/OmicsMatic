# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Separate private LeafCutter runtime preparation: CAPTURE an existing validated source build, or INSTALL exact locked archives in NEW paths with explicit flags."""
import argparse,hashlib,json,os,shutil,signal,subprocess,sys,time,traceback,urllib.request,uuid
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from inventory import inventory


def pin(path):
    digest=hashlib.sha256();size=0
    with Path(path).open('rb') as stream:
        while block:=stream.read(1048576):size+=len(block);digest.update(block)
    return dict(bytes=size,sha256=digest.hexdigest())


def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')


def members(group):
    result=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:
            raw=(path/'stat').read_text();fields=raw[raw.rindex(')')+2:].split()
            if int(fields[2])==group:result.append(dict(pid=int(path.name),state=fields[0],start_ticks=int(fields[19])))
        except (FileNotFoundError,ProcessLookupError,PermissionError):pass
    return result


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--mode',required=True,choices=['CAPTURE','INSTALL']);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--dependency-receipt',type=Path);parser.add_argument('--library-receipt',type=Path);parser.add_argument('--prefix',type=Path);parser.add_argument('--micromamba',type=Path);parser.add_argument('--archive-cache',type=Path,action='append',default=[]);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');a=parser.parse_args();out=a.out.absolute();kit=Path(__file__).resolve().parent.parent
    assert sys.platform=='linux' and out==out.resolve() and not out.exists() and out.parent.resolve()==out.parent,'Require Linux and NEW canonical setup output under an existing writable parent';out.mkdir();state=dict(status='SKIP3_LEAFCUTTER_RUNTIME_UNAVAILABLE',mode=a.mode,analysis_executed=False,processes=[]);roots={};before={};shared={};env=dict(os.environ);profile=json.loads((kit/'profiles.json').read_bytes());action='Required action: keep dependent analysis skipped until exact permitted preparation passes. Preserve all source, archives, compiler and namespace diagnostics, partial prefixes and receipts. CAPTURE requires the original matching dependency/library build receipts. INSTALL requires --allow-install, a NEW prefix and a supplied permitted micromamba; uncached archives also require --allow-download. Correct the named dependency/source/compiler/permission cause and retry NEW paths, or provide an allowed matching runtime. Never patch original requirements, replace a source hash or silently modify a shared environment.'
    for key in list(env):
        if key.startswith(('R_','PYTHON','CONDA_','MAMBA_','PIP_')) or key in ['LD_LIBRARY_PATH','LD_PRELOAD','MAKEFLAGS','CXX','CXX14','CXXFLAGS','CXX14FLAGS']:env.pop(key,None)
    for name in ['private-temp','private-config','private-cache','private-home']:(out/name).mkdir()
    env.update(HOME=str(out/'private-home'),TMPDIR=str(out/'private-temp'),TMP=str(out/'private-temp'),TEMP=str(out/'private-temp'),XDG_CONFIG_HOME=str(out/'private-config'),XDG_CACHE_HOME=str(out/'private-cache'),MAMBA_ROOT_PREFIX=str(out/'private-cache/mamba-root'),LANG='C.UTF-8',LC_ALL='C.UTF-8',MAKEFLAGS='-j1',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1',PYTHONDONTWRITEBYTECODE='1',R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null')
    def execute(name,command,timeout):
        command=list(map(str,command));record=dict(command=command,started_at=time.time());save(out/(name+'.STARTED.json'),record);child=None;code=None;started=time.monotonic();error=None
        try:
            with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:
                child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True);record['owned_process_group']=child.pid;code=child.wait(timeout=timeout)
            assert code==0,name+' failed; inspect complete stdout/stderr'
        except Exception as failure:error=failure;record['error']=repr(failure)
        finally:
            if child is not None:
                record['owned_before_cleanup']=members(child.pid)
                for sig,seconds in [(signal.SIGTERM,3),(signal.SIGKILL,3)]:
                    if not any(r['state']!='Z' for r in members(child.pid)):break
                    try:os.killpg(child.pid,sig)
                    except ProcessLookupError:pass
                    deadline=time.monotonic()+seconds
                    while any(r['state']!='Z' for r in members(child.pid)) and time.monotonic()<deadline:time.sleep(.05)
                child.wait(timeout=10);record['owned_live_after_cleanup']=[r for r in members(child.pid) if r['state']!='Z']
                if record['owned_live_after_cleanup']:error=RuntimeError('Owned setup processes remain live')
            record.update(exit_code=code,elapsed_seconds=time.monotonic()-started);save(out/(name+'.process.json'),record);state['processes'].append(record)
        if error:raise error
    def case_sensitive(parent,label):
        probe=parent/('.leafcutter-case-probe-'+uuid.uuid4().hex);probe.mkdir();lower=probe/'case-sensitive-probe';upper=probe/'CASE-SENSITIVE-PROBE';lower.write_bytes(b'lower\n');row=dict(path=str(parent),probe=str(probe),case_sensitive=False);state.setdefault('filesystem_checks',[]).append(row)
        try:
            with upper.open('xb') as stream:stream.write(b'UPPER\n')
        except FileExistsError:raise RuntimeError('INSTALL requires a case-sensitive '+label+' filesystem; case-distinct Linux dependency files would collide. Use NEW permitted case-sensitive Linux/WSL output and prefix directories, or keep dependent analysis skipped')
        assert lower.read_bytes()==b'lower\n' and upper.read_bytes()==b'UPPER\n';row['case_sensitive']=True
    try:
        for name,want in profile['files'].items():
            assert pin(kit/name)==want,'Packaged setup/original source differs: '+name
            snapshot=out/'kit-source'/name;snapshot.parent.mkdir(parents=True,exist_ok=True);snapshot.write_bytes((kit/name).read_bytes())
        (out/'kit-source/profiles.json').write_bytes((kit/'profiles.json').read_bytes())
        if a.mode=='CAPTURE':
            assert a.dependency_receipt and a.library_receipt,'CAPTURE requires both original build receipts';dependency=json.loads(a.dependency_receipt.read_bytes());binding=json.loads(a.library_receipt.read_bytes());assert dependency['status']=='PASS_PRIVATE_LEAFCUTTER_DEPENDENCY_CANDIDATE_ONLY' and dependency['runtime_trees_unchanged'] and binding['status']=='PASS_ORIGINAL_LEAFCUTTER_PRIVATE_R_LIBRARY_AND_LITERAL_FIT_ONLY' and binding['dependency_runtime_unchanged'] and binding['namespace_patched'] is False
            prefix=Path(dependency['runtime_environment']);library=Path(binding['R_library']);source=json.loads((a.library_receipt.parent/'original-source-stage.json').read_bytes());assert binding['source_revision']==profile['revision'] and binding['R_package_version']==profile['R_package_version']
            for name,want in source['members'].items():assert pin(Path(source['stage'])/name)==want==profile['files']['original/leafcutter/'+name]
            roots=dict(runtime=prefix,library=library);state['prior_build_receipts']={str(p.resolve()):pin(p) for p in [a.dependency_receipt,a.library_receipt]}
        else:
            assert a.allow_install,'Installation flag absent';assert a.prefix and a.micromamba and a.micromamba.is_file(),'INSTALL requires explicit NEW prefix and permitted micromamba';prefix=a.prefix.absolute();assert prefix==prefix.resolve() and not prefix.exists() and prefix.parent.resolve()==prefix.parent and not prefix.is_relative_to(out) and not out.is_relative_to(prefix),'INSTALL prefix must be NEW and separate from setup output';case_sensitive(out,'setup/cache');case_sensitive(prefix.parent,'runtime prefix parent');library=out/'R-lib';library.mkdir();shared['micromamba']=dict(path=str(a.micromamba.resolve()),**pin(a.micromamba.resolve()));archives=out/'archives';archives.mkdir();locks=json.loads((kit/'setup/dependency-locks.json').read_bytes());paths=[]
            for package in locks['packages']:
                name=package['filename'];assert Path(name).name==name and package['url'].startswith(('https://conda.anaconda.org/conda-forge/','https://conda.anaconda.org/bioconda/'));target=archives/name;cached=next((home/name for home in a.archive_cache if (home/name).is_file()),None)
                if cached is not None:shutil.copyfile(cached,target)
                else:
                    assert a.allow_download,'Required archive absent and download forbidden: '+name
                    with urllib.request.urlopen(package['url'],timeout=120) as response,target.open('xb') as stream:shutil.copyfileobj(response,stream)
                assert pin(target)=={k:package[k] for k in ['bytes','sha256']};paths.append((target,package))
            explicit=out/'explicit.txt';explicit.write_text('@EXPLICIT\n'+''.join(file.as_uri()+'#'+row['md5']+'\n' for file,row in paths),encoding='utf8',newline='\n');execute('dependencies-install',[a.micromamba.resolve(),'create','--no-rc','--offline','-y','-p',prefix,'--file',explicit],1800);env.update(PATH=str(prefix/'bin')+':'+env.get('PATH',''),CONDA_PREFIX=str(prefix),R_LIBS_USER=str(library),R_LIBS_SITE=str(prefix/'lib/R/library'));makevars=out/'Makevars';makevars.write_text('CXX14 = '+str(prefix/'bin/x86_64-conda-linux-gnu-g++')+'\nCXX14FLAGS = -O0 -g0\nCXX11FLAGS = -O0 -g0\nCXXFLAGS = -O0 -g0\nCFLAGS = -O0 -g0\n',encoding='utf8',newline='\n');env['R_MAKEVARS_USER']=str(makevars);stage=out/'source/leafcutter';stage.mkdir(parents=True)
            for name,want in profile['files'].items():
                if name.startswith('original/leafcutter/'):
                    relative=name.removeprefix('original/leafcutter/');destination=stage/relative;destination.parent.mkdir(parents=True,exist_ok=True);destination.write_bytes((kit/name).read_bytes())
            for name in ['Biobase','oompaData','TailRank']:
                archive=kit/profile['addons'][name]['path'];execute(name+'-install',[prefix/'bin/R','CMD','INSTALL','--no-multiarch','--library='+str(library),archive],600)
            execute('LeafCutter-install',[prefix/'bin/R','CMD','INSTALL','--preclean','--no-multiarch','--library='+str(library),stage],1800)
            for name,want in profile['files'].items():
                if name.startswith('original/leafcutter/'):assert pin(stage/name.removeprefix('original/leafcutter/'))==want,'Original source changed during compilation'
            roots=dict(runtime=prefix,library=library)
        assert all(path.is_absolute() and path.resolve()==path and path.is_dir() for path in roots.values()) and all(not out.is_relative_to(path) for path in roots.values());env.update(PATH=str(prefix/'bin')+':'+env.get('PATH',''),CONDA_PREFIX=str(prefix),R_LIBS_USER=str(library),R_LIBS_SITE=str(prefix/'lib/R/library'))
        for name,path in roots.items():before[name]=inventory(path,progress=out/(name+'-before.progress.json'));save(out/(name+'-before.json'),before[name])
        if a.mode=='CAPTURE':assert before['runtime']==json.loads((a.dependency_receipt.parent/'runtime-before.json').read_bytes()) and before['library']==json.loads((a.library_receipt.parent/'library-inventory.json').read_bytes())
        records=[json.loads(file.read_bytes()) for file in sorted((prefix/'conda-meta').glob('*.json'))];locked=json.loads((kit/'setup/dependency-locks.json').read_bytes())['packages'];assert {(r['name'],r['version'],r['build'],r['sha256']) for r in records}=={(r['name'],r['version'],r['build'],r['sha256']) for r in locked};save(out/'complete-conda-package-records.json',records)
        execute('R-namespace-health',[prefix/'bin/Rscript','--vanilla',kit/'setup/health.R',library,kit/'original/leafcutter',out/'R-health.json'],120);health=json.loads((out/'R-health.json').read_bytes());assert health['status']=='PASS_ORIGINAL_LEAFCUTTER_NAMESPACE_AND_LOSSLESS_EXPORT_ONLY' and health['version']==profile['R_package_version'] and health['rstan']=='2.21.8' and health['jsonlite']=='1.8.8'
        execute('Python-health',[prefix/'bin/python','-I','-B','-c','import sys,numpy,scipy,sklearn;assert sys.version_info[:2]==(3,11) and numpy.__version__=="1.26.4" and scipy.__version__=="1.11.4" and sklearn.__version__=="1.3.2";print(sys.version)'],120)
        state.update(status='PASS_BOUND_ORIGINAL_LEAFCUTTER_WORKFLOW_RUNTIME_ONLY',edition=profile['edition'],revision=profile['revision'],R_package_version=profile['R_package_version'],runtime_environment=str(prefix),R_library=str(library),runtime_python=str(prefix/'bin/python'),Rscript=str(prefix/'bin/Rscript'),health=health,profile=pin(kit/'profiles.json'),runtime_inventory=pin(out/'runtime-before.json'),library_inventory=pin(out/'library-before.json'),namespace_patched=False)
    except Exception as failure:state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8',newline='\n');print('SKIP3 dependent analysis remains skipped:',repr(failure),flush=True)
    finally:
        unchanged=True
        for name,was in before.items():after=inventory(roots[name],progress=out/(name+'-after.progress.json'));save(out/(name+'-after.json'),after);unchanged&=was==after
        state['runtime_trees_unchanged']=unchanged and bool(before)
        if before and not unchanged:state.update(status='FAIL2_ORIGINAL_RUNTIME_CHANGED',error='Bound runtime/library changed during preparation health')
        if shared:state['shared_installer_unchanged']=pin(Path(shared['micromamba']['path']))=={k:shared['micromamba'][k] for k in ['bytes','sha256']}
        if state.get('shared_installer_unchanged') is False:state.update(status='FAIL2_SHARED_INSTALLER_CHANGED',error='Supplied installer changed')
        if (out/'kit-source/profiles.json').is_file():
            if pin(kit/'profiles.json')!=pin(out/'kit-source/profiles.json') or any(pin(kit/name)!=want for name,want in profile['files'].items()):state.update(status='FAIL2_SETUP_SOURCE_CHANGED',error='Exact source/setup kit changed during preparation; preserve the captured kit and admit no binding')
        diagnostics=[];compiler_warnings=[]
        for path in sorted(out.glob('*.stderr')):
            if path.stat().st_size:
                diagnostics.append(path.name)
                with path.open(encoding='utf8',errors='replace') as stream:
                    for line in stream:
                        if 'warning:' in line.lower():
                            warning=line.strip()
                            if warning not in compiler_warnings and len(compiler_warnings)<32:compiler_warnings.append(warning)
        state['diagnostic_files']=diagnostics;state['compiler_warning_examples']=compiler_warnings;state['required_action']=action
        if state.get('filesystem_checks'):save(out/'filesystem-checks.json',state['filesystem_checks'])
        save(out/'COMPLETED.json',state);detail=state.get('error','Namespace/runtime preparation only; no analysis admitted.');detail+='\n\nOriginal diagnostics: '+(', '.join(diagnostics) if diagnostics else 'none emitted')
        if compiler_warnings:detail+='\n\nORIGINAL COMPILER WARNINGS (full diagnostics remain in the named files):\n\n'+'\n\n'.join(compiler_warnings)
        detail+='\n\nINSTALL requires case-sensitive setup/cache and runtime-prefix parents. On WSL-mounted NTFS, use a NEW dedicated case-sensitive directory where permitted; otherwise keep dependent analysis skipped. Do not reuse a prefix with case-colliding extracted files.'
        (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+detail+'\n\n'+action+'\n',encoding='utf8',newline='\n')
    print(state['status']);return 0 if state['status'].startswith('PASS_') else 2 if state['status'].startswith('FAIL2') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact setup checks')
    try:raise SystemExit(main())
    except Exception as failure:print('SKIP3 LeafCutter setup unavailable: '+repr(failure)+'\nRequired action: preserve partial setup paths, choose NEW permitted writable paths and supply exact allowed dependencies; otherwise skip dependent analysis.',file=sys.stderr);raise SystemExit(3)
