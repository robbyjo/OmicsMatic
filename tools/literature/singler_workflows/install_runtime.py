#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install a selected unchanged original SingleR release in a NEW Linux workspace."""
from pathlib import Path,PurePosixPath
import argparse,os,shutil,signal,subprocess,sys,tarfile,time,traceback,urllib.request
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,profile,pin,save,sources,source_tree,environment,check_output

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--edition',required=True);p.add_argument('--manager',required=True,type=Path)
    p.add_argument('--out',required=True,type=Path);p.add_argument('--source-cache',type=Path);p.add_argument('--cache-dir',type=Path)
    p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true')
    p.add_argument('--wall-seconds',type=int,default=7200)
    a=p.parse_args();out=a.out.absolute();owned=False;child=None;began=time.monotonic();calls=[]
    def stop():
        if child is not None and child.poll() is None:
            os.killpg(child.pid,signal.SIGTERM)
            try:child.wait(timeout=5)
            except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
    def run(label,command,env):
        nonlocal child
        command=list(map(str,command))
        with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
            child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,start_new_session=True)
            save(out/('LIVE-'+label+'.json'),dict(pid=child.pid,command=command))
            while True:
                try:code=child.wait(timeout=10);break
                except subprocess.TimeoutExpired:
                    print('SAME_SINGLER_INSTALL_CHILD_RUNNING',label,child.pid,flush=True)
                    if time.monotonic()-began>a.wall_seconds:raise Unavailable('Declared installation time limit reached; retry NEW paths')
        record=dict(label=label,command=command,exit_code=code);calls.append(record);save(out/(label+'-PROCESS.json'),record)
        if code:raise Unavailable('Required original installation step failed: '+label+'; inspect complete logs; dependent analysis skipped')
    def archive(name,entry):
        target=out/'downloads'/entry['archive_filename']
        cached=a.source_cache.resolve()/entry['archive_filename'] if a.source_cache else None
        if cached is not None and cached.is_file():
            shutil.copyfile(cached,target);origin=dict(source='local-copy',path=str(cached))
        else:
            if not a.allow_download:raise Unavailable('Download forbidden; supply the exact cached archive '+entry['archive_filename'])
            req=urllib.request.Request(entry['url'],headers={'User-Agent':'OmicsMatic-original-SingleR-installer'})
            total=0
            with urllib.request.urlopen(req,timeout=120) as response,target.open('xb') as stream:
                while True:
                    block=response.read(min(1048576,entry['archive']['bytes']+1-total))
                    if not block:break
                    stream.write(block);total+=len(block)
                    if total>entry['archive']['bytes']:raise ValueError('Original archive exceeds its pinned size')
                origin=dict(source='permitted-download',url=entry['url'],final_url=response.url,status=response.status,headers=dict(response.headers))
        if pin(target)!=entry['archive']:raise ValueError('Exact original archive differs: '+name)
        save(out/(name+'-DOWNLOAD.json'),dict(**origin,archive=pin(target)));return target
    def pristine(name,archive):
        destination=out/'source-original'/name;destination.mkdir();seen=set()
        with tarfile.open(archive,'r:gz') as tar:
            for m in tar.getmembers():
                n=PurePosixPath(m.name)
                if n.is_absolute() or '..' in n.parts or '\\' in m.name or not n.parts or n.parts[0]!=name:
                    raise ValueError('Unsafe or unexpected original archive member: '+m.name)
                if len(n.parts)==1:
                    if not m.isdir():raise ValueError('Archive root must be a directory')
                    continue
                relative=PurePosixPath(*n.parts[1:]).as_posix()
                if relative in seen:raise ValueError('Duplicate original archive member: '+relative)
                seen.add(relative);target=destination/relative
                if m.isdir():target.mkdir(parents=True,exist_ok=True)
                elif m.isfile():
                    target.parent.mkdir(parents=True,exist_ok=True)
                    with tar.extractfile(m) as src,target.open('xb') as dst:shutil.copyfileobj(src,dst)
                    target.chmod(m.mode&0o777)
                else:raise ValueError('Only regular original source files and directories may be extracted')
        return str(destination)
    try:
        protected=[HERE,a.manager.resolve()]+[p.resolve() for p in [a.source_cache,a.cache_dir] if p is not None]
        out=check_output(out,protected);out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text(ACTION+'\nInstallation alone is not a completed analysis.\n')
        selected=profile(a.edition);provider=out.parent/(out.name+'-provider')
        if provider.exists():raise FileExistsError('Provider output already exists; preserve it and use NEW paths')
        if not a.allow_install:raise Unavailable('Installation forbidden without --allow-install; dependent analysis skipped')
        if sys.platform!='linux':raise Unavailable('The original profile requires permitted Linux/WSL execution')
        if a.wall_seconds<1:raise ValueError('Positive installation wall limit required')
        if not a.allow_download and (a.source_cache is None or a.cache_dir is None):
            raise Unavailable('Offline setup requires both complete --source-cache and --cache-dir')
        manager=a.manager.resolve()
        if not manager.is_file() or not os.access(manager,os.X_OK):raise Unavailable('Supply a permitted executable Pixi manager')
        before=sources(a.edition)
        for n in ['runtime','library','source-original','build-original','downloads','home','tmp','pixi-home']:(out/n).mkdir()
        cache=a.cache_dir.resolve() if a.cache_dir else out/'pixi-cache';cache.mkdir(exist_ok=True)
        for suffix in ['toml','lock']:shutil.copyfile(HERE/'dependencies'/(a.edition+'.'+suffix),out/'runtime'/('pixi.'+suffix))
        env=dict(os.environ,HOME=str(out/'home'),TMPDIR=str(out/'tmp'),PIXI_HOME=str(out/'pixi-home'),PIXI_CACHE_DIR=str(cache),
          PIXI_NO_PROGRESS='true',PIXI_NO_PATH_UPDATE='1',R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',
          OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',PYTHONDONTWRITEBYTECODE='1')
        for k in ['R_HOME','R_LIBS','R_LIBS_USER','R_LIBS_SITE','PYTHONHOME','PYTHONPATH','LD_PRELOAD','LD_LIBRARY_PATH']:env.pop(k,None)
        save(out/'INSTALLATION-INPUTS.json',dict(edition=a.edition,manager=pin(manager),adapter_sources=before,
          install_allowed=a.allow_install,download_allowed=a.allow_download,cache=str(cache),analysis_executed=False))
        order=[n for n in ['GenomeInfoDbData','DelayedMatrixStats','beachmat','BiocNeighbors','SingleR'] if n in selected['packages']]
        paths={n:archive(n,selected['packages'][n]) for n in order}
        originals={n:pristine(n,paths[n]) for n in order};source_tree(a.edition,originals)
        command=[manager,'install','--locked','--manifest-path',out/'runtime/pixi.toml']
        if not a.allow_download:command.append('--offline')
        run('locked-dependencies',command,env)
        prefix=out/'runtime/.pixi/envs/default';ready=dict(r_prefix=str(prefix),library=str(out/'library'))
        env=environment(ready,out)
        env.update(CONDA_PREFIX=str(prefix),MAKEFLAGS='-j2',R_MAKEVARS_USER='/dev/null',R_MAKEVARS_SITE='/dev/null',PKG_CONFIG_PATH=str(prefix/'lib/pkgconfig'))
        for n in order:
            build=out/'build-original'/n;shutil.copytree(originals[n],build)
            run('original-'+n,[prefix/'bin/R','CMD','INSTALL','--no-multiarch','--library='+str(out/'library'),build],env)
        source_tree(a.edition,originals)
        if sources(a.edition)!=before:raise ValueError('Installer/adapter changed during installation')
        command=[sys.executable,'-B',HERE/'adopt_runtime.py','--edition',a.edition,'--workspace',out,'--out',provider]
        for n,path in originals.items():command+=['--source',n+'='+path]
        run('adopt-original-runtime',command,env)
        save(out/'COMPLETED.json',dict(status='PASS_INSTALLED_AND_AUDITED_ORIGINAL_SINGLER_PROFILE_NOT_ANALYSIS',edition=a.edition,
          provider=str(provider),calls=calls,source_archives={n:pin(paths[n]) for n in order},new_family_credit=0,whole_goal_complete=False))
        print('PASS_INSTALLED_AND_AUDITED_ORIGINAL_SINGLER_PROFILE_NOT_ANALYSIS',a.edition,flush=True);return 0
    except BaseException as e:
        stop();code=3 if isinstance(e,OSError) and not isinstance(e,FileExistsError) else 2
        label='SKIP3_SINGLER_INSTALL_UNAVAILABLE' if code==3 else 'FAIL2_SINGLER_INSTALL_UNADMITTED'
        print(label,repr(e),ACTION,flush=True)
        if owned:
            try:
                save(out/'FAILURE.json',dict(status=label,cause=repr(e),traceback=traceback.format_exc(),calls=calls,
                  dependent_analysis_skipped=True,complete_result_admitted=False,required_action=ACTION))
                (out/'WARNINGS.md').write_text(label+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf-8')
            except BaseException as recording:code=2;print('FAIL2_INSTALL_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
        return code

if __name__=='__main__':raise SystemExit(main())
