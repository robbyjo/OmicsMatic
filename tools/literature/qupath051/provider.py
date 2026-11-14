#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install a pinned, complete original QuPath0.5.1 release into a NEW private directory."""
from pathlib import Path
import argparse,hashlib,json,os,platform,re,shutil,subprocess,sys,tarfile,time,traceback,urllib.request,uuid,zipfile

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
ACTION=('Required action: use a NEW output and a supported Windows/Linux x86_64 environment. --allow-install permits private extraction; --allow-download permits fetching only the pinned official binary and source archives when no matching --source-cache archive is available. Supply missing archives or permitted native runtime prerequisites and retry NEW paths. SKIP3 skips dependent installation/analysis; FAIL2 admits no provider. Retain complete downloads, source/runtime inventories and process diagnostics. The embedded Java21 runtime is part of the selected official distribution. Other QuPath editions, external extensions, trained models and author image/ROI/stain choices are separate. This setup performs no image analysis and earns no catalog credit. Source hashes establish integrity, not producer authentication or a reproducible source build.')

def pin(path):
    sha=hashlib.sha256();size=0
    with path.open('rb') as source:
        for block in iter(lambda:source.read(1024*1024),b''):sha.update(block);size+=len(block)
    return dict(bytes=size,sha256=sha.hexdigest())

def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')

def member(name):
    if not name or name.startswith('/') or '\\' in name or ':' in name or any(p in ['', '.', '..'] for p in name.split('/')):
        raise ValueError('Unsafe original archive member: '+name)
    return name

def tree(root):
    result={}
    for path in sorted(root.rglob('*')):
        if path.is_symlink():raise ValueError('Unexpected link in private source/runtime: '+str(path))
        if path.is_file():result[path.relative_to(root).as_posix()]=pin(path)
        elif not path.is_dir():raise ValueError('Unexpected nonregular source/runtime entry')
    return result

def verify_tree(root,expected):
    wanted={n:{k:v[k] for k in ['bytes','sha256']} for n,v in expected.items()}
    if tree(root)!=wanted:raise ValueError('Complete original source/runtime population or bytes differ: '+str(root))

def archive(row,cache,out,allow_download,calls):
    target=out/'downloads'/row['name'];expected={k:row[k] for k in ['bytes','sha256']}
    original=cache/row['name'] if cache is not None else None
    if original is not None and original.is_file():
        if original.is_symlink() or pin(original)!=expected:raise ValueError('Cached original archive differs: '+row['name'])
        shutil.copyfile(original,target);calls.append(dict(operation='copy-pinned-original-archive',source=str(original),target=str(target)))
    else:
        if not allow_download:raise OSError('Missing pinned '+row['name']+'; supply --source-cache or permitted --allow-download')
        request=urllib.request.Request(row['url'],headers={'User-Agent':'OmicsMatic-QuPath051-provider'})
        with urllib.request.urlopen(request,timeout=90) as response,target.open('xb') as dest:
            size=0
            while True:
                block=response.read(1024*1024)
                if not block:break
                size+=len(block)
                if size>row['bytes']:raise ValueError('Original archive exceeded pinned complete size')
                dest.write(block)
        calls.append(dict(operation='download-pinned-official-archive',url=row['url'],target=str(target)))
    if pin(target)!=expected:raise ValueError('Complete official archive digest differs: '+row['name'])
    return target

def extract(path,destination,expected,strip_prefix=None):
    destination.mkdir(exist_ok=False);seen=set()
    def output(name,is_dir):
        if strip_prefix is not None:
            if name==strip_prefix:
                if not is_dir:raise ValueError('Source archive root is not a directory')
                return None
            prefix=strip_prefix+'/'
            if not name.startswith(prefix):raise ValueError('Source archive root differs')
            name=name[len(prefix):]
        name=member(name);target=destination/name
        if not target.resolve().is_relative_to(destination.resolve()):raise ValueError('Archive member escapes private output')
        if is_dir:target.mkdir(parents=True,exist_ok=True);return None
        if name in seen or name not in expected:raise ValueError('Unexpected/duplicate original file: '+name)
        seen.add(name);target.parent.mkdir(parents=True,exist_ok=True);return target
    if path.name.endswith('.zip'):
        with zipfile.ZipFile(path) as archive:
            if len(archive.namelist())!=len(set(archive.namelist())):raise ValueError('Duplicate original archive entry')
            for info in archive.infolist():
                if ((info.external_attr>>16)&0o170000)==0o120000:raise ValueError('Original links are not part of this profile')
                target=output(info.filename.rstrip('/'),info.is_dir())
                if target is not None:
                    if info.file_size!=expected[info.filename]['bytes']:raise ValueError('Original ZIP complete file size differs')
                    with archive.open(info) as source,target.open('xb') as dest:shutil.copyfileobj(source,dest)
    else:
        with tarfile.open(path,'r:*') as archive:
            for info in archive:
                if not info.isdir() and not info.isfile():raise ValueError('Unexpected original TAR link/device member')
                target=output(info.name,info.isdir())
                if target is not None:
                    name=target.relative_to(destination).as_posix()
                    if info.size!=expected[name]['bytes']:raise ValueError('Original TAR complete file size differs')
                    with archive.extractfile(info) as source,target.open('xb') as dest:shutil.copyfileobj(source,dest)
                    if os.name!='nt':target.chmod(info.mode&0o777)
    if seen!=set(expected):raise ValueError('Incomplete original archive file population')
    verify_tree(destination,expected)

def private_environment(out):
    env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']}
    home=out/'owned-home';home.mkdir(exist_ok=False);prefs=out/'owned-preferences';prefs.mkdir(exist_ok=False)
    node='omicsmatic/qupath051/'+uuid.uuid4().hex
    env['JAVA_TOOL_OPTIONS']=' '.join(['-Xmx2048m','-Djava.awt.headless=true','-Duser.home="'+str(home)+'"',
        '-Djava.util.prefs.userRoot="'+str(prefs)+'"','-Dqupath.prefs.name='+node,'-Doffline=true'])
    return env,dict(qupath_preference_node=node,user_home=str(home),java_preferences_root=str(prefs),
        note='QuPath uses a fresh named preference node. Windows Java Preferences may store that new node in the current-user registry; the existing QuPath node is not selected. Headless execution is not an OS sandbox.',java_tool_options=env['JAVA_TOOL_OPTIONS'])

def verify_provider(provider):
    profile=json.loads((HERE/'source-profile.json').read_bytes());ready=json.loads((provider/'READY.json').read_bytes())
    if ready['status']!='READY_PRIVATE_ORIGINAL_QUPATH051_NOT_ANALYSIS' or ready['version']!='0.5.1' or ready['source_commit']!=profile['source_commit'] or ready['source_profile']!=pin(HERE/'source-profile.json'):
        raise ValueError('Original provider version/profile identity differs')
    selected=profile['platforms'][ready['platform']]
    verify_tree(provider/'runtime',selected['files']);verify_tree(provider/'source',profile['source_members'])
    if ready['launcher']!=str((provider/'runtime'/selected['launcher']).resolve()):raise ValueError('Original launcher identity differs')
    return ready

def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--source-cache',type=Path);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.absolute();owned=False;calls=[]
    try:
        if out!=out.resolve():raise ValueError('Canonical NEW provider output required')
        cache=args.source_cache.resolve() if args.source_cache else None
        for protected in [HERE,ROOT/'src',ROOT/'docs']+([cache] if cache else []):
            if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Provider output overlaps protected source/cache')
        out.mkdir(parents=True,exist_ok=False);owned=True
        if not args.allow_install:raise OSError('Private installation not permitted by this invocation; --allow-install is required')
        machine=platform.machine().lower()
        if machine not in ['amd64','x86_64']:raise OSError('Only pinned x86_64 official releases are qualified')
        os_id='windows' if sys.platform=='win32' else 'linux' if sys.platform.startswith('linux') else None
        if os_id is None:raise OSError('Only pinned Windows/Linux runtime setup is supported')
        platform_id=os_id+'-x86_64';profile=json.loads((HERE/'source-profile.json').read_bytes());selected=profile['platforms'][platform_id]
        if profile['version']!='0.5.1' or profile['source_commit']!='4e6bfc31904be7ee39c10d457149cb962002e1f2' or profile['original_scientific_source_modified']:
            raise ValueError('Selected official original source identity differs')
        (out/'downloads').mkdir();before={n:pin(HERE/n) for n in ['source-profile.json','provider.py']};save(out/'BEFORE.json',dict(source_pins=before,platform=platform_id))
        binary=archive(selected['archive'],cache,out,args.allow_download,calls);source=archive(profile['source_archive'],cache,out,args.allow_download,calls)
        extract(binary,out/'runtime',selected['files']);extract(source,out/'source',profile['source_members'],profile['source_archive_root'])
        release=(out/'runtime'/selected['java_runtime_release']).read_text(encoding='utf8')
        if 'JAVA_VERSION="'+selected['embedded_java_version']+'"' not in release:raise ValueError('Embedded original Java runtime version differs')
        launcher=out/'runtime'/selected['launcher']
        if not os.access(launcher,os.X_OK):raise OSError('Original QuPath launcher cannot be executed; supply permitted filesystem/native prerequisites')
        env,preferences=private_environment(out);save(out/'PREFERENCES.json',preferences)
        for label,flags in [('version',['--version']),('script-help',['script','--help'])]:
            command=[str(launcher),*flags];start=time.time()
            with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
                child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
                save(out/(label+'.live.json'),dict(pid=child.pid,command=command,started_unix=start));code=child.wait(timeout=120)
            row=dict(operation='original-cli-probe',label=label,command=command,exit_code=code,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
            if code:
                if code in [-1073741515,127]:raise OSError('Native original launcher prerequisite unavailable; inspect '+label+' logs')
                raise ValueError('Original CLI probe failed; inspect '+label+' logs')
        if '0.5.1' not in (out/'version.stdout').read_text(encoding='utf8',errors='replace'):raise ValueError('Original CLI did not identify selected0.5.1 version')
        if 'Runs script' not in (out/'script-help.stdout').read_text(encoding='utf8',errors='replace'):raise ValueError('Original full script command is unavailable')
        verify_tree(out/'runtime',selected['files']);verify_tree(out/'source',profile['source_members'])
        if any(pin(HERE/n)!=wanted for n,wanted in before.items()):raise ValueError('Provider source/profile changed during setup')
        ready=dict(status='READY_PRIVATE_ORIGINAL_QUPATH051_NOT_ANALYSIS',version='0.5.1',source_commit=profile['source_commit'],platform=platform_id,
            source_profile=pin(HERE/'source-profile.json'),provider_source=pin(HERE/'provider.py'),launcher=str(launcher.resolve()),
            complete_runtime_files=selected['file_count'],complete_source_files=len(profile['source_members']),embedded_java_version=selected['embedded_java_version'],
            original_scientific_source_modified=False,official_binary_rebuilt=False,external_extensions_qualified=False,preferences=preferences,calls=calls,
            image_analysis_executed=False,new_tool_credit=0,whole_goal_complete=False)
        save(out/'READY.json',ready);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(ready['status'],platform_id,flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,OSError) else 2;result=dict(status='SKIP3_QUPATH_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_QUPATH051_SETUP',cause=repr(error),traceback=traceback.format_exc(),calls=calls,analysis_admitted=False,required_action=ACTION)
        if owned:save(out/'FAILURE.json',result);(out/'WARNINGS_FAILURE.md').write_text(result['status']+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
        print(result['status'],repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
