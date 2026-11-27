# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind existing original velocyto/Python/Samtools and complete numerical dependency closure."""
import hashlib,json,os,shlex,stat,subprocess,sys,sysconfig,traceback,importlib.metadata
from pathlib import Path
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n')as stream:json.dump(value,stream,sort_keys=True,indent=2,allow_nan=False);stream.write('\n')
def main():
    out=Path(sys.argv[1]);request=json.loads((out/'request.json').read_bytes());profile=json.loads((out/'profile.json').read_bytes())
    os.environ['MPLBACKEND']='Agg';os.environ['MPLCONFIGDIR']=str(out/'health-cache/matplotlib');os.environ['NUMBA_CACHE_DIR']=str(out/'health-cache/numba')
    env=dict(PATH='/usr/bin:/bin',LD_LIBRARY_PATH=request['library_paths']['python'],HOME=str(out),TMPDIR=str(out),LC_ALL='C',LANG='C',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',PYTHONHASHSEED='0',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',NUMEXPR_NUM_THREADS='1',NUMBA_NUM_THREADS='1',MPLBACKEND='Agg',MPLCONFIGDIR=str(out/'health-cache/matplotlib'),NUMBA_CACHE_DIR=str(out/'health-cache/numba'))
    programs={key:request[key]for key in ['python','samtools']}
    assert Path(programs['python']).resolve(strict=True)==Path(sys.executable).resolve(strict=True),'Bind with the explicitly selected private Python'
    for value in programs.values():
        with Path(value).open('rb')as stream:header=stream.read(20)
        assert header[:6]==b'\x7fELF\x02\x01'and int.from_bytes(header[18:20],'little')==62,'Original Linux x86_64 ELF executable required'
    if list(sys.version_info[:2])!=profile['python_major_minor']:raise FileNotFoundError('Compatible private Python3.11 runtime required')
    import velocyto,numpy,scipy,pandas,h5py,loompy,pysam,numba,matplotlib
    actual={p:importlib.metadata.version(p)for p in profile['packages']}
    if actual!=profile['packages']:raise FileNotFoundError('Install the exact permitted velocyto counting dependencies privately: expected '+repr(profile['packages'])+'; found '+repr(actual))
    package=Path(velocyto.__file__).parent
    for name,expected in profile['installed_python_files'].items():
        raw=(package/name).read_bytes();assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==expected,'Installed original scientific Python source differs: '+name
    assert velocyto.__version__=='0.17.17'and velocyto.LOOM_NUMERIC_DTYPE=='uint16','Original version/counting-buffer semantics differ'
    launchers=out/'bin';launchers.mkdir();launcher=launchers/'samtools';launcher.write_text('#!/bin/sh\nexport PATH=/usr/bin:/bin\nexport LC_ALL=C\nexport LD_LIBRARY_PATH='+shlex.quote(request['library_paths']['samtools'])+'\nexec '+shlex.quote(programs['samtools'])+' "$@"\n');launcher.chmod(0o755)
    command=[str(launcher),'--version'];save(out/'samtools-version-COMMAND.json',command)
    with(out/'samtools-version-stdout').open('xb')as stdout,(out/'samtools-version-stderr').open('xb')as stderr:result=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=30)
    assert result.returncode==0 and(out/'samtools-version-stdout').read_bytes().startswith(b'samtools 1.19.2\n'),'Original Samtools helper version differs'
    save(out/'PYTHON-HEALTH.json',dict(python=sys.version,packages=actual,original_scientific_source_matched=True,scientific_source_modified=False))
    # Import health covers the actual original counting, Loom and numerical modules.
    active={str(Path(v).resolve(strict=True))for v in programs.values()if v is not None}
    maps=Path('/proc/self/maps').read_text();(out/'PYTHON-MAPS.txt').write_text(maps)
    for line in maps.splitlines():
        parts=line.split(None,5)
        if len(parts)==6 and 'x'in parts[1]and parts[5].startswith('/'):
            assert not parts[5].endswith(' (deleted)'),'Loaded native library changed during health';active.add(str(Path(parts[5]).resolve(strict=True)))
    roots={str(Path(p).resolve(strict=True))for p in request['roots']+[request['source'],sys.prefix,str(launchers),sysconfig.get_path('stdlib')]}
    for value in request['library_paths'].values():roots.update(str(Path(p).resolve(strict=True))for p in value.split(':')if p)
    roots={p for p in roots if not any(p!=q and Path(p).is_relative_to(q)for q in roots)}
    aliases={p for p in ['/bin','/lib','/lib64','/sbin','/usr/lib64']if Path(p).is_symlink()}
    files=active|aliases|{p for p in programs.values()if p is not None}|{'/usr/bin/sh','/usr/bin/ldd','/usr/bin/setsid','/usr/bin/prlimit','/etc/ld.so.cache'}
    files.add(str(launchers/'samtools'))
    nodes={};visited=set();total=0
    def inspect(path):
        nonlocal total
        name=str(path);s=path.lstat()
        if name in nodes:return
        assert len(nodes)<250000,'Complete runtime node capacity exceeded'
        if stat.S_ISLNK(s.st_mode):
            try:target=path.resolve(strict=True)
            except FileNotFoundError:nodes[name]=dict(kind='dangling-symlink',target=os.readlink(path));return
            nodes[name]=dict(kind='symlink',target=os.readlink(path),real_path=str(target))
            if target.is_dir():
                if name not in aliases:roots.add(str(target))
            else:files.add(str(target))
        elif stat.S_ISDIR(s.st_mode):
            nodes[name]=dict(kind='directory',mode=stat.S_IMODE(s.st_mode))
            for child in sorted(path.iterdir()):inspect(child)
        elif stat.S_ISREG(s.st_mode):
            digest=hashlib.sha256();size=0
            with path.open('rb')as stream:
                for block in iter(lambda:stream.read(1048576),b''):size+=len(block);digest.update(block)
            end=path.stat();assert(s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns)==(end.st_dev,end.st_ino,end.st_size,end.st_mtime_ns)and size==s.st_size,'Runtime changed during census: '+name
            total+=size;assert total<=100000000000,'Complete runtime byte capacity exceeded';nodes[name]=dict(kind='file',mode=stat.S_IMODE(s.st_mode),bytes=size,sha256=digest.hexdigest())
        else:raise RuntimeError('Nonregular runtime node: '+name)
    # Inspect active dynamic-library closures in each executable's actual isolated environment.
    python_programs={str(Path(programs[name]).resolve(strict=True))for name in ['python']}
    python_active={path for path in active if path not in [programs['samtools']]}
    python_loader_directories=sorted({str(Path(path).parent)for path in python_active})
    save(out/'dynamic-inspection.json',dict(additional_existing_Python_loader_directories=python_loader_directories,inspection_only=True,scientific_environment_changed=False,reason='Standalone ldd omits the Python launcher and extension-module RPATH namespace already loaded during actual import health.'))
    pending=[]
    for name,value in programs.items():
        if value is not None:pending.append((Path(value),request['library_paths'][name],name=='python'))
    pending.extend((Path(path),request['library_paths']['python'],True)for path in python_active)
    pending.extend((Path(path),'',False)for path in ['/usr/bin/sh','/usr/bin/setsid','/usr/bin/prlimit'])
    checked=set()
    with(out/'dynamic-libraries.log').open('xb')as log:
        while pending:
            path,library,python_namespace=pending.pop();key=(str(path),library,python_namespace)
            if key in checked:continue
            inspection_library=':'.join([p for p in [library,*python_loader_directories]if p])if python_namespace else library
            checked.add(key);result=subprocess.run(['/usr/bin/ldd',str(path)],env=dict(env,LD_LIBRARY_PATH=inspection_library),stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=15)
            log.write((str(path)+' INSPECTION_LD_LIBRARY_PATH='+inspection_library+'\n').encode());log.write(result.stdout);text=result.stdout.decode('utf-8','backslashreplace')
            if '=> not found'in text:raise FileNotFoundError('Required native library unavailable: '+str(path)+'; inspect dynamic-libraries.log')
            assert result.returncode==0 or 'statically linked'in text or 'not a dynamic executable'in text,('ldd failed',path,text)
            for line in text.splitlines():
                for token in line.split():
                    if token.startswith('/'):
                        lexical=Path(token);node=lexical.parent.resolve(strict=True)/lexical.name;real=node.resolve(strict=True);files.update([str(node),str(real)]);pending.append((real,library,python_namespace));break
    while(roots|files)-visited:
        for name in sorted((roots|files)-visited):inspect(Path(name));visited.add(name)
    save(out/'inventory.json',dict(roots=sorted(roots),files=sorted(files),nodes=nodes,complete_bytes=total))
    save(out/'runtime.json',dict(schema_version=1,profile=request['profile'],programs=programs,source=request['source'],library_paths=request['library_paths'],environment=dict(PATH=str(launchers)+':/usr/bin:/bin',LD_LIBRARY_PATH=request['library_paths']['python']),host=dict(os=sys.platform,architecture=os.uname().machine,kernel=os.uname().release),installed=False,user_analysis_performed=False))
if __name__=='__main__':
    try:main()
    except BaseException as error:
        save(Path(sys.argv[1])/'BINDING-FAILURE.json',dict(error=type(error).__name__+': '+str(error),traceback=traceback.format_exc(),unavailable=isinstance(error,(FileNotFoundError,PermissionError,ImportError)),analysis_admitted=False));raise
