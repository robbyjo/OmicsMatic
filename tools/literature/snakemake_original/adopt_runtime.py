#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal an exact installed original engine and its complete selected software tree.

No dependency is installed and no user workflow is executed. Adoption records the
whole environment, the exact official wheel's package files, resolved host ELF
dependencies and supplied project-driver controls. This is not a complete OS image.
"""
from pathlib import Path
import argparse,concurrent.futures,hashlib,json,os,platform,re,stat,struct,subprocess,sys,time,traceback,zipfile
if not __debug__:raise RuntimeError('Runtime-adoption assertions must remain enabled')
ROOT=Path(__file__).resolve().parents[3]
PROFILE='linux-x86_64-snakemake-9.27.1'
WHEEL='cb8b9045c68d2d561520a92d78c8acb0e7383cd7958adde1efb68d97a0601a92'
LOCK='ebc39cd3f2d04a7ae915554e8291bd0e84fe7fe6b9f0edf17dc4bb137c44080a'
ACTION='Missing or forbidden prerequisites mean SKIP3: skip dependent execution, restore the exact permitted environment/helpers or use a permitted Linux x86-64 host, then retry NEW output. FAIL2 admits no runtime or analysis; retain all diagnostics and resolve the named source/integrity discrepancy. Never refresh old hashes or infer an author environment. This command installs nothing and executes no user analysis.'
def encoded(value):return (json.dumps(value,indent=2)+'\n').encode()
def write(path,value):
    with path.open('xb') as f:f.write(encoded(value))
def sha(path):
    digest=hashlib.sha256();before=path.stat()
    with path.open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):digest.update(raw)
    after=path.stat()
    if (before.st_dev,before.st_ino,before.st_size,before.st_mtime_ns)!=(after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns):raise ValueError('Software changed during complete hashing: '+str(path))
    return digest.hexdigest()
def plain(text):
    text=str(text)
    if any(c in text for c in '\t\r\n\0'):raise ValueError('Control character in software record')
    return text
def nodes(prefix):
    result=[]
    for directory,dirs,files in os.walk(prefix,followlinks=False):
        for name in dirs+files:
            path=Path(directory)/name;info=path.lstat();relative=plain(path.relative_to(prefix).as_posix())
            if stat.S_ISLNK(info.st_mode):
                resolved=path.resolve(strict=True)
                if path.is_dir() and not resolved.is_relative_to(prefix):raise ValueError('External directory alias is not a closed runtime: '+str(path))
                kind='link';target=plain(os.readlink(path))
            elif stat.S_ISDIR(info.st_mode):kind='directory';target='-'
            elif stat.S_ISREG(info.st_mode):kind='file';target='-'
            else:raise ValueError('Nonregular installed software object: '+str(path))
            result.append((relative,kind,target,info.st_mode&0o777,info.st_size,info.st_mtime_ns,info.st_ino))
    return sorted(result)
def fingerprint(item):
    root,name,kind,target,mode,path=item;resolved=path.resolve(strict=True);size=path.stat().st_size if path.is_file() else 0
    return [root,plain(name),kind,str(mode),str(size),plain(target),plain(resolved),sha(path) if path.is_file() else '-']
def main():
    parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--environment',type=Path,required=True);parser.add_argument('--pixi-lock',type=Path,required=True);parser.add_argument('--wheel',type=Path,required=True);parser.add_argument('--driver-controls',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--max-files',type=int,default=100000);parser.add_argument('--max-bytes',type=int,default=4294967296);a=parser.parse_args();out=None
    try:
        if sys.platform!='linux' or platform.machine() not in ['x86_64','amd64']:raise FileNotFoundError('Selected runtime adoption requires Linux x86-64')
        prefix=a.environment.resolve(strict=True);python=prefix/'bin/python';target=a.out.absolute()
        if any(c.isspace() or ord(c)>127 for c in str(prefix)):raise ValueError('Selected software prefix needs ASCII without whitespace')
        if target.is_relative_to(prefix) or prefix.is_relative_to(target):raise ValueError('Adoption records and installed software must be separate')
        target.mkdir(parents=True,exist_ok=False);out=target.resolve();(out/'WARNINGS.md').write_text('# Original Snakemake runtime adoption\n\n'+ACTION+'\n',encoding='utf-8',newline='\n')
        if not python.is_file() or not os.access(python,os.X_OK):raise FileNotFoundError('Exact installed Python is unavailable or forbidden')
        if sha(a.wheel)!=WHEEL or sha(a.pixi_lock)!=LOCK:raise ValueError('Selected official release wheel or resolved environment lock differs')
        source=prefix/'lib/python3.12/site-packages/snakemake';expected={}
        with zipfile.ZipFile(a.wheel) as archive:
            for name in archive.namelist():
                if name.startswith('snakemake/') and not name.endswith('/'):
                    relative=name.removeprefix('snakemake/');raw=archive.read(name);expected[relative]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());path=source/relative
                    if not path.is_file() or path.stat().st_size!=len(raw) or sha(path)!=expected[relative]['sha256']:raise ValueError('Installed original package differs from the exact official wheel: '+relative)
        actual={p.relative_to(source).as_posix() for p in source.rglob('*') if p.is_file()}
        if actual!=set(expected):raise ValueError('Installed original package membership differs (including unverified bytecode); prepare a separate pristine permitted environment')
        write(out/'OFFICIAL-PACKAGE-FILES.json',dict(version='9.27.1',source_commit='f79d9eb5e2b679e1191818f24335907b041578be',wheel=dict(bytes=a.wheel.stat().st_size,sha256=WHEEL),files=expected,all_installed_package_files_match_official_wheel=True))
        controls=json.loads((a.driver_controls/'COMPLETED.json').read_bytes());assert controls['status']=='PASS_ORIGINAL_CLI_PROJECT_LOGGER_DRIVER_CONTROLS_NOT_JAVA_ADAPTER' and len(controls['cases'])==4
        helpers=ROOT/'src/main/resources/org/jlinalg/omics/snakemake-original-v1';helper_pins={p.relative_to(helpers).as_posix():sha(p) for p in helpers.rglob('*.py')}
        for case in controls['cases']:
            receipt=json.loads((a.driver_controls/case['case']/'DRIVER.json').read_bytes());assert receipt['helpers_before']==receipt['helpers_after']==helper_pins
            assert receipt['schema_version']==2 and receipt['policy_status']=='PASS_EFFECTIVE_ORIGINAL_PARSER_POLICY' and receipt['upstream_api_called']
        write(out/'DRIVER-CONTROLS.json',dict(record_path=str(a.driver_controls.resolve()),record_sha256=sha(a.driver_controls/'COMPLETED.json'),project_helpers=helper_pins,project_java_adapter_qualified=False))
        env=os.environ.copy()
        for key in ['PYTHONHOME','PYTHONPATH','PYTHONSTARTUP','LD_PRELOAD','LD_AUDIT','LD_LIBRARY_PATH']:env.pop(key,None)
        env.update(PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',PATH=str(prefix/'bin')+':/usr/bin:/bin',LANG='C.UTF-8',LC_ALL='C.UTF-8',TZ='UTC')
        command=[str(python),'-I','-B',str(helpers/'driver.py'),'--health'];start=time.monotonic()
        with (out/'health.stdout').open('xb') as so,(out/'health.stderr').open('xb') as se:health=subprocess.run(command,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=120)
        write(out/'HEALTH-PROCESS.json',dict(command=command,exit_code=health.returncode,seconds=round(time.monotonic()-start,3)));assert health.returncode==0
        healthy=json.loads((out/'health.stdout').read_bytes());assert healthy['profile']==PROFILE
        before=nodes(prefix);assert 0<len(before)<=a.max_files and a.max_bytes>0
        print('Complete environment entries: '+str(len(before))+'; checking all ELF headers',flush=True)
        items=[('env',name,kind,target,mode,prefix/name) for name,kind,target,mode,size,mtime,inode in before]
        byte_count=sum((prefix/name).stat().st_size for name,kind,target,mode,size,mtime,inode in before if (prefix/name).is_file());assert byte_count<=a.max_bytes
        helpers_system=[Path(p) for p in ['/usr/bin/setsid','/usr/bin/prlimit','/usr/bin/bash','/usr/bin/ldd','/usr/bin/env','/bin/sh']]
        for path in helpers_system:
            if not path.is_file() or not os.access(path,os.X_OK):raise FileNotFoundError('Required permitted system helper unavailable: '+str(path))
        candidates={path.resolve() for root,name,kind,target,mode,path in items if path.is_file()};candidates.update(path.resolve() for path in helpers_system)
        def classify(path):
            with path.open('rb') as f:header=f.read(20)
            if header[:4]!=b'\x7fELF':return None
            machine=int.from_bytes(header[18:20],'little' if header[5]==1 else 'big')
            return str(path),machine
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:elf=dict(row for row in pool.map(classify,sorted(candidates)) if row is not None)
        def dependencies(path):
            process=subprocess.run(['/usr/bin/ldd',path],env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=60);text=(process.stdout+process.stderr).decode('utf-8',errors='replace')
            if 'not found' in text:raise FileNotFoundError('Missing permitted ELF dependency for '+path+': '+text)
            if process.returncode and not any(message in text for message in ['not a dynamic executable','statically linked']):raise ValueError('Unexpected ldd result for '+path+': '+text)
            found=set()
            for match in re.finditer(r'(?<!\S)(/[^\s()]+)',text):
                dependency=Path(match.group(1))
                if dependency.is_file() and not dependency.resolve().is_relative_to(prefix):found.add(dependency)
            return path,text,found
        native=sorted(path for path,machine in elf.items() if machine==62);print('Resolving '+str(len(native))+' host-x86_64 ELF dependency families',flush=True)
        external=set(helpers_system);ldd={}
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            for path,text,found in pool.map(dependencies,native):ldd[path]=text;external.update(found)
        # Record canonical target modes as well as logical helper/library aliases.
        external.update(path.resolve() for path in list(external))
        write(out/'ELF-DEPENDENCIES.json',dict(scope='All environment file bytes are sealed. Dynamic-loader dependency inspection covers host-x86_64 ELF objects and named system helpers, not foreign-platform binaries, dynamically chosen undeclared libraries, kernel or a complete OS image.',host_objects=ldd,foreign_elf_objects={path:machine for path,machine in elf.items() if machine!=62},external_paths=sorted(map(str,external))))
        for path in sorted(external):items.append(('system',str(path),'link' if path.is_symlink() else 'file',os.readlink(path) if path.is_symlink() else '-',path.lstat().st_mode&0o777,path))
        print('Hashing '+str(len(items))+' complete software/helper entries',flush=True)
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:rows=sorted(pool.map(fingerprint,items))
        assert nodes(prefix)==before,'Installed software changed during adoption'
        manifest='root\tpath\tkind\tmode\tbytes\ttarget\tresolved\tsha256\n'+''.join('\t'.join(row)+'\n' for row in rows);(out/'runtime-files.tsv').write_text(manifest,encoding='utf-8',newline='\n')
        (out/'pixi.lock').write_bytes(a.pixi_lock.read_bytes())
        metadata={path.name:dict(bytes=path.stat().st_size,sha256=sha(path)) for path in sorted(out.iterdir()) if path.is_file()}
        ready=dict(status='READY_ADOPTED_SELECTED_SNAKEMAKE_RUNTIME_NOT_USER_ANALYSIS',schema_version=2,profile=PROFILE,environment=str(prefix),python=str(python),kernel=platform.release(),architecture=platform.machine(),versions=healthy['versions'],entries=len(rows),external_paths=len(external),logical_environment_file_bytes=byte_count,runtime_manifest=dict(bytes=(out/'runtime-files.tsv').stat().st_size,sha256=sha(out/'runtime-files.tsv')),health_sha256=sha(out/'health.stdout'),official_package_sha256=sha(out/'OFFICIAL-PACKAGE-FILES.json'),elf_dependencies_sha256=sha(out/'ELF-DEPENDENCIES.json'),driver_controls_sha256=sha(out/'DRIVER-CONTROLS.json'),lock_sha256=LOCK,adopter_sha256=sha(Path(__file__)),project_helpers=helper_pins,metadata=metadata,project_java_adapter_qualified=False,user_analysis_admitted=False,author_environment_reproduced=False,whole_goal_complete=False)
        write(out/'READY.json',ready);print(json.dumps(dict(status=ready['status'],entries=len(rows),external_paths=len(external))),flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,(FileNotFoundError,PermissionError)) else 2
        if out is not None:write(out/'FAILURE.json',dict(status='SKIP3' if code==3 else 'FAIL2',reason=repr(error),traceback=traceback.format_exc(),required_action=ACTION,user_analysis_admitted=False))
        print(('SKIP3' if code==3 else 'FAIL2'),repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
