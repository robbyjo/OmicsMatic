#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install a NEW locked private Arrow runtime when installation/downloads are explicitly allowed."""
from pathlib import Path, PurePosixPath
import argparse, os, shutil, signal, subprocess, sys, tarfile, time, traceback, urllib.request
sys.dont_write_bytecode=True
from Runtime import HERE,ACTION,Unavailable,pin,load,save,sources,source_tree

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION)
    parser.add_argument('--manager',required=True,type=Path,help='Permitted executable Pixi manager; tested with 0.83.0')
    parser.add_argument('--out',required=True,type=Path)
    parser.add_argument('--allow-install',action='store_true')
    parser.add_argument('--allow-download',action='store_true')
    parser.add_argument('--source-cache',type=Path,help='Directory containing the exact arrow_12.0.1.tar.gz')
    parser.add_argument('--cache-dir',type=Path,help='Permitted Pixi cache; offline installation requires a populated cache')
    parser.add_argument('--wall-seconds',type=int,default=3600)
    args=parser.parse_args();out=args.out.absolute();owned=False;child=None;calls=[];start=time.monotonic()
    def stop_child():
        if child is not None and child.poll() is None:
            os.killpg(child.pid,signal.SIGTERM)
            try:child.wait(timeout=5)
            except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
    def run(label,command,env):
        nonlocal child
        if time.monotonic()-start>args.wall_seconds:raise Unavailable('Installation wall limit exhausted before '+label)
        command=list(map(str,command));began=time.monotonic()
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
            child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,start_new_session=True)
            save(out/(label+'-LIVE.json'),dict(pid=child.pid,command=command))
            while True:
                try:code=child.wait(timeout=10);break
                except subprocess.TimeoutExpired:
                    print('ORIGINAL_ARROW1201_INSTALL_CHILD_RUNNING',label,child.pid,flush=True)
                    if time.monotonic()-start>args.wall_seconds:raise Unavailable('Installation wall limit exceeded; retry NEW paths')
        record=dict(label=label,command=command,exit_code=code,elapsed_seconds=time.monotonic()-began)
        calls.append(record);save(out/(label+'-PROCESS.json'),record)
        print('ORIGINAL_ARROW1201_INSTALL_STEP',label,code,flush=True)
        if code:raise Unavailable('Original runtime installation/audit failed: '+label+'; dependent analysis skipped; inspect full logs')
    try:
        protected=[HERE,args.manager.resolve()]+[p.resolve() for p in [args.source_cache,args.cache_dir] if p is not None]
        if out!=out.resolve() or any(out.is_relative_to(p) or p.is_relative_to(out) for p in protected):
            raise ValueError('Use a NEW canonical installation directory separate from manager, source and caches')
        out.mkdir(parents=True,exist_ok=False);owned=True
        if not args.allow_install:raise Unavailable('Installation forbidden without --allow-install; dependent analysis skipped')
        if sys.platform!='linux':raise Unavailable('This selected original runtime requires permitted Linux/WSL')
        if args.wall_seconds<1:raise ValueError('Positive installation wall limit required')
        manager=args.manager.resolve()
        if not manager.is_file() or not os.access(manager,os.X_OK):raise Unavailable('Supply a permitted executable Pixi manager')
        if not args.allow_download and (args.source_cache is None or args.cache_dir is None):
            raise Unavailable('Offline installation requires exact --source-cache and populated --cache-dir, or permitted --allow-download')
        for name in ['runtime','source-original','downloads','home','tmp','pixi-home']:(out/name).mkdir()
        cache=args.cache_dir.resolve() if args.cache_dir else out/'cache';cache.mkdir(parents=True,exist_ok=True)
        before=sources();installer={n:pin(HERE/n) for n in ['install_runtime.py']}
        profile=load(HERE/'source-profile.json');expected={k:profile['source_archive'][k] for k in ['bytes','sha256']}
        target=out/'downloads/arrow_12.0.1.tar.gz'
        local=args.source_cache.resolve()/'arrow_12.0.1.tar.gz' if args.source_cache else None
        if local is not None and local.is_file():
            shutil.copyfile(local,target)
            provenance=dict(source='local-copy',path=str(local))
        else:
            if not args.allow_download:raise Unavailable('Exact cached arrow_12.0.1.tar.gz unavailable; downloads forbidden')
            request=urllib.request.Request(profile['source_archive']['requested_url'],headers={'User-Agent':'OmicsMatic-original-Arrow1201-install/1'})
            total=0
            with urllib.request.urlopen(request,timeout=60) as response,target.open('xb') as stream:
                while True:
                    block=response.read(min(1048576,expected['bytes']+1-total))
                    if not block:break
                    stream.write(block);total+=len(block)
                    if total>expected['bytes']:raise ValueError('Original source download exceeds declared size')
                provenance=dict(source='permitted-download',url=profile['source_archive']['requested_url'],final_url=response.url,status=response.status)
        if pin(target)!=expected:raise ValueError('Original CRAN Arrow 12.0.1 archive identity differs')
        save(out/'DOWNLOAD.json',dict(**provenance,identity=pin(target)))
        seen=set()
        with tarfile.open(target,'r:gz') as archive:
            for member in archive.getmembers():
                path=PurePosixPath(member.name)
                if path.is_absolute() or '..' in path.parts or '\\' in member.name or not path.parts or path.parts[0]!='arrow':
                    raise ValueError('Unsafe/unexpected original source member: '+member.name)
                if member.name in seen:raise ValueError('Duplicate original source member: '+member.name)
                seen.add(member.name);destination=(out/'source-original').joinpath(*path.parts[1:])
                if member.isdir():destination.mkdir(parents=True,exist_ok=True)
                elif member.isfile():
                    destination.parent.mkdir(parents=True,exist_ok=True)
                    with archive.extractfile(member) as src,destination.open('xb') as dst:shutil.copyfileobj(src,dst)
                    destination.chmod(member.mode & 0o777)
                else:raise ValueError('Unsupported original source file kind: '+member.name)
        source_tree(out/'source-original')
        for name in ['pixi.toml','pixi.lock']:shutil.copyfile(HERE/('reference-'+name),out/'runtime'/name)
        env=dict(os.environ,HOME=str(out/'home'),TMPDIR=str(out/'tmp'),PIXI_HOME=str(out/'pixi-home'),
                 PIXI_CACHE_DIR=str(cache),PIXI_NO_PROGRESS='true',PYTHONDONTWRITEBYTECODE='1',
                 R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1')
        for name in ['R_HOME','R_LIBS','PYTHONPATH','PYTHONHOME']:env.pop(name,None)
        save(out/'INSTALLATION-INPUTS.json',dict(manager=pin(manager),adapter_sources=before,installer_sources=installer,
            install_allowed=args.allow_install,download_allowed=args.allow_download,cache=str(cache),analysis_executed=False))
        command=[manager,'install','--locked','--manifest-path',out/'runtime/pixi.toml']
        if not args.allow_download:command.append('--offline')
        run('locked-dependencies',command,env)
        source_tree(out/'source-original')
        if pin(target)!=expected or sources()!=before or any(pin(HERE/n)!=v for n,v in installer.items()):
            raise ValueError('Original source or installer/adapter changed during installation')
        run('audit-provider',[sys.executable,'-B',HERE/'adopt_runtime.py','--workspace',out/'runtime',
            '--source',out/'source-original','--out',out/'provider'],env)
        (out/'WARNINGS.md').write_text('# Original runtime installed and audited; no analysis executed\n\n'
            'Review locked-dependencies.stderr for all manager warnings, including any skipped optional post-link scripts.\n\n'+ACTION+'\n',encoding='utf-8')
        save(out/'COMPLETED.json',dict(status='PASS_INSTALLED_AUDITED_ORIGINAL_ARROW1201_RUNTIME_NOT_ANALYSIS',
            provider=str(out/'provider'),provider_identity=pin(out/'provider/READY.json'),calls=calls,
            source_regular_members=1619,adapter_sources=before,installer_sources=installer,
            analysis_executed=False,new_family_credit=0,whole_goal_complete=False))
        print('PASS_INSTALLED_AUDITED_ORIGINAL_ARROW1201_RUNTIME_NOT_ANALYSIS',flush=True);return 0
    except BaseException as error:
        stop_child()
        code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2
        status='SKIP3_ARROW1201_INSTALL_REQUIREMENT_UNAVAILABLE' if code==3 else 'FAIL2_ARROW1201_INSTALL_UNADMITTED'
        print(status,repr(error),ACTION,flush=True)
        if owned:
            try:
                save(out/'FAILURE.json',dict(status=status,cause=repr(error),traceback=traceback.format_exc(),calls=calls,required_action=ACTION))
                (out/'WARNINGS.md').write_text('# '+status+'\n\n'+repr(error)+'\n\n'+ACTION+'\n',encoding='utf-8')
            except BaseException as recording:
                code=2;print('FAIL2_INSTALL_RECORDING_UNAVAILABLE',repr(recording),ACTION,flush=True)
        return code

if __name__=='__main__':raise SystemExit(main())
