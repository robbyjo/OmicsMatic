#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install original HICAT 1.0.9 and RANN 2.6.1 in a NEW private Linux directory."""
from pathlib import Path,PurePosixPath
import argparse,os,shutil,signal,subprocess,sys,tarfile,time,traceback,urllib.request
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,profile,pin,load,save,sources,source_tree,environment,check_output

def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    p.add_argument('--manager',required=True,type=Path)
    p.add_argument('--out',required=True,type=Path);p.add_argument('--source-cache',type=Path);p.add_argument('--cache-dir',type=Path)
    p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true')
    p.add_argument('--wall-seconds',type=int,default=7200)
    args=p.parse_args();out=args.out.absolute();owned=False;child=None;began=time.monotonic();calls=[]
    def stop_child():
        if child is not None and child.poll() is None:
            os.killpg(child.pid,signal.SIGTERM)
            try:child.wait(timeout=5)
            except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
    def run(label,command,env):
        nonlocal child
        command=list(map(str,command))
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
            child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True)
            save(out/('LIVE-'+label+'.json'),dict(pid=child.pid,command=command))
            while True:
                try:code=child.wait(timeout=10);break
                except subprocess.TimeoutExpired:
                    print('SAME_HICAT109_INSTALL_CHILD_RUNNING',label,child.pid,flush=True)
                    if time.monotonic()-began>args.wall_seconds:raise Unavailable('Declared install time limit exceeded; retry NEW paths')
        record=dict(label=label,command=command,exit_code=code);calls.append(record);save(out/(label+'-PROCESS.json'),record)
        if code:raise Unavailable('Original installation step failed: '+label+'; inspect full logs; dependent analysis skipped')
    def archive(entry):
        target=out/'downloads'/entry['filename'];cached=args.source_cache.resolve()/entry['filename'] if args.source_cache else None
        if cached is not None and cached.is_file():
            shutil.copyfile(cached,target);origin=dict(source='local-copy',path=str(cached))
        else:
            if not args.allow_download:raise Unavailable('Download forbidden; supply exact cached original archive '+entry['filename'])
            request=urllib.request.Request(entry['url'],headers={'User-Agent':'OmicsMatic-original-HICAT109-profile-installer'})
            total=0
            with urllib.request.urlopen(request,timeout=60) as response,target.open('xb') as stream:
                while True:
                    block=response.read(min(1048576,entry['archive']['bytes']+1-total))
                    if not block:break
                    stream.write(block);total+=len(block)
                    if total>entry['archive']['bytes']:raise ValueError('Original source exceeds pinned archive size')
                origin=dict(source='permitted-download',url=entry['url'],final_url=response.url,status=response.status)
        if pin(target)!=entry['archive']:raise ValueError('Original source archive differs: '+entry['name'])
        save(out/(entry['name']+'-DOWNLOAD.json'),dict(**origin,archive=pin(target)))
        return target
    def pristine(entry,path):
        destination=out/'source-original'/entry['name'];destination.mkdir();seen=set()
        with tarfile.open(path,'r:gz') as stream:
            for member in stream.getmembers():
                name=PurePosixPath(member.name)
                if name.is_absolute() or '..' in name.parts or '\\' in member.name or not name.parts or name.parts[0]!=entry['archive_top_level']:
                    raise ValueError('Unsafe or unexpected source member: '+member.name)
                if len(name.parts)==1:
                    if not member.isdir():raise ValueError('Source archive root must be a directory')
                    continue
                relative=PurePosixPath(*name.parts[1:]).as_posix()
                if relative in seen:raise ValueError('Duplicate source member: '+relative)
                seen.add(relative);target=destination/relative
                if member.isdir():target.mkdir(parents=True,exist_ok=True)
                elif member.isfile():
                    target.parent.mkdir(parents=True,exist_ok=True)
                    with stream.extractfile(member) as source,target.open('xb') as result:shutil.copyfileobj(source,result)
                    target.chmod(member.mode&0o777)
                else:raise ValueError('Original source must contain only regular files/directories; no link extraction permitted')
        return destination
    try:
        protected=[HERE,args.manager.resolve()]+[x.resolve() for x in [args.source_cache,args.cache_dir] if x is not None]
        out=check_output(out,protected);out.mkdir(parents=True,exist_ok=False);owned=True
        (out/'WARNINGS.md').write_text('# Private original runtime installation; analysis not started\n\n'+ACTION+'\n',encoding='utf-8')
        provider=out.parent/(out.name+'-provider')
        if provider.exists():raise FileExistsError('Provider output exists; retry NEW paths: '+str(provider))
        if not args.allow_install:raise Unavailable('Installation forbidden without --allow-install; dependent analysis skipped')
        if sys.platform!='linux':raise Unavailable('Selected original profiles require permitted Linux/WSL')
        if args.wall_seconds<1:raise ValueError('Positive installation wall limit required')
        if not args.allow_download and (args.source_cache is None or args.cache_dir is None):
            raise Unavailable('Offline setup requires complete --source-cache and --cache-dir, or permitted --allow-download')
        manager=args.manager.resolve()
        if not manager.is_file() or not os.access(manager,os.X_OK):raise Unavailable('Supply a permitted executable Pixi manager')
        selected=profile();entries=[dict(name=name,**entry) for name,entry in selected['packages'].items()];before=sources()
        for name in ['runtime','library','source-original','build-original','downloads','home','tmp','pixi-home']:(out/name).mkdir()
        cache=args.cache_dir.resolve() if args.cache_dir else out/'pixi-cache';cache.mkdir(exist_ok=True)
        for suffix in ['toml','lock']:shutil.copyfile(HERE/('dependency-pixi.'+suffix),out/'runtime'/('pixi.'+suffix))
        env=dict(os.environ,HOME=str(out/'home'),TMPDIR=str(out/'tmp'),PIXI_HOME=str(out/'pixi-home'),PIXI_CACHE_DIR=str(cache),
          PIXI_NO_PROGRESS='true',PIXI_NO_PATH_UPDATE='1',R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',
          OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',PYTHONDONTWRITEBYTECODE='1')
        for key in ['R_HOME','R_LIBS','R_LIBS_USER','R_LIBS_SITE','PYTHONHOME','PYTHONPATH','LD_PRELOAD']:env.pop(key,None)
        save(out/'INSTALLATION-INPUTS.json',dict(profile=selected['source_profile_name'],manager=pin(manager),adapter_sources=before,
          install_allowed=args.allow_install,download_allowed=args.allow_download,cache=str(cache),analysis_executed=False))
        paths={entry['name']:archive(entry) for entry in entries}
        originals={entry['name']:str(pristine(entry,paths[entry['name']])) for entry in entries}
        source_tree(originals)
        command=[manager,'install','--locked','--manifest-path',out/'runtime/pixi.toml']
        if not args.allow_download:command.append('--offline')
        run('locked-dependencies',command,env)
        prefix=out/'runtime/.pixi/envs/default'
        ready=dict(r_prefix=str(prefix),library=str(out/'library'))
        env=environment(ready,out)
        env.update(CONDA_PREFIX=str(prefix),MAKEFLAGS='-j2',R_MAKEVARS_USER='/dev/null',R_MAKEVARS_SITE='/dev/null',
          PKG_CONFIG_PATH=str(prefix/'lib/pkgconfig'))
        order=sorted(entries,key=lambda e:0 if e['name']=='RANN' else 1)
        for entry in order:
            build=out/'build-original'/entry['name'];shutil.copytree(originals[entry['name']],build)
            run('original-'+entry['name'],[prefix/'bin/R','CMD','INSTALL','--library='+str(out/'library'),build],env)
        source_tree(originals)
        if sources()!=before:raise ValueError('Installer/adapter changed during installation')
        command=[sys.executable,'-B',HERE/'adopt_runtime.py','--workspace',out,'--out',out.parent/(out.name+'-provider')]
        for name,path in originals.items():command += ['--source',name+'='+path]
        run('adopt-original-runtime',command,env)
        (out/'WARNINGS.md').write_text('# Installed original profile; no analysis executed\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
        save(out/'COMPLETED.json',dict(status='PASS_INSTALLED_AND_AUDITED_ORIGINAL_HICAT109_PROFILE_NOT_ANALYSIS',profile=selected['source_profile_name'],
          provider=str(out.parent/(out.name+'-provider')),calls=calls,source_archives={e['name']:pin(paths[e['name']]) for e in entries},
          new_family_credit=0,whole_goal_complete=False))
        print('PASS_INSTALLED_AND_AUDITED_ORIGINAL_HICAT109_PROFILE_NOT_ANALYSIS',flush=True);return 0
    except BaseException as error:
        stop_child();code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        label='SKIP3_HICAT109_INSTALL_UNAVAILABLE' if code==3 else 'FAIL2_HICAT109_INSTALL_UNADMITTED'
        print(label,repr(error),ACTION,flush=True)
        if owned:
            try:
                save(out/'FAILURE.json',dict(status=label,cause=repr(error),traceback=traceback.format_exc(),calls=calls,
                  dependent_analysis_skipped=True,complete_result_admitted=False,required_action=ACTION))
                (out/'WARNINGS.md').write_text('# '+label+'\n\n'+repr(error)+'\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
            except BaseException as recording:code=2;print('FAIL2_INSTALL_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
        return code

if __name__=='__main__':raise SystemExit(main())
