# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Inspect existing original callers, helpers and their complete effective dependency closure."""
import hashlib,json,os,shlex,stat,subprocess,sys,sysconfig,traceback
from pathlib import Path

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n')as stream:json.dump(value,stream,sort_keys=True,indent=2,allow_nan=False);stream.write('\n')

def main():
    out=Path(sys.argv[1]);request=json.loads((out/'request.json').read_bytes());profile=json.loads((out/'profile.json').read_bytes());programs=request['programs'];library=request['library_path'];legacy=profile['legacy']
    if sys.version_info<(3,9):raise ImportError('Existing permitted Python 3.9 or newer required')
    assert Path(programs['python']).resolve(strict=True)==Path(sys.executable).resolve(strict=True),'Explicit selected Python required'
    launchers=out/'bin';launchers.mkdir()
    for name,program in programs.items():
        if name in ['python','graphtyper']:continue
        path=launchers/name;path.write_text('#!/bin/sh\nexport PATH=/usr/bin:/bin\nexport LC_ALL=C\nexport LD_LIBRARY_PATH='+shlex.quote(library)+'\nexec '+shlex.quote(program)+' "$@"\n');path.chmod(0o755)
    env=dict(PATH=str(launchers)+':/usr/bin:/bin',LD_LIBRARY_PATH=library,LC_ALL='C',LANG='C',HOME=str(out),TMPDIR=str(out),PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1')
    probes=[('graphtyper', [programs['graphtyper'],'call'if legacy else'genotype','--help'])]+[(name,[str(launchers/name),'--version'])for name in programs if name not in ['python','graphtyper']]
    for name,command in probes:
        save(out/(name+'-health-COMMAND.json'),command)
        with(out/(name+'-health-stdout')).open('xb')as stdout,(out/(name+'-health-stderr')).open('xb')as stderr:result=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=60)
        save(out/(name+'-health-RESULT.json'),dict(exit_code=result.returncode));assert result.returncode==0,('Original runtime health failed',name,result.returncode)
    roots={str(Path(p).resolve(strict=True))for p in request['roots']+[request['source'],sysconfig.get_path('stdlib'),str(launchers)]}
    if legacy:roots.add(str(Path(request['pipeline']).resolve(strict=True)))
    roots.update(str(Path(p).resolve(strict=True))for p in library.split(':')if p)
    tools=['sh','env','ldd','setsid','prlimit']
    if legacy:
        tools+=['bash','perl','cp','cut','cat','find','sort','mkdir','rm','mv','hostname','realpath','date','grep','awk','head','tail','wc','mktemp','seq','tee','sleep','readlink','dirname','basename','touch']
        # Bind the actual Perl interpreter search population used by the unchanged GNU parallel script.
        result=subprocess.run(['/usr/bin/perl','-e','print join("\\n",@INC)'],env=env,capture_output=True,check=True,timeout=30);(out/'perl-include-paths').write_bytes(result.stdout)
        roots.update(str(Path(p).resolve(strict=True))for p in result.stdout.decode().splitlines()if Path(p).is_dir())
    files=set(programs.values())|{str(launchers/name)for name in programs if name not in ['python','graphtyper']}|{'/etc/ld.so.cache'}
    for name in tools:
        path=Path('/usr/bin')/name
        if not path.is_file():raise FileNotFoundError('Required original pipeline helper unavailable: '+str(path))
        files.add(str(path))
    # Keep lexical aliases and their ultimate targets, without recursively binding all /usr/bin or /usr/lib.
    aliases={p for p in ['/bin','/lib','/lib64','/sbin','/usr/lib64']if Path(p).is_symlink()};files.update(aliases)
    maps=Path('/proc/self/maps').read_text();(out/'PYTHON-MAPS.txt').write_text(maps)
    for row in maps.splitlines():
        parts=row.split(None,5)
        if len(parts)==6 and 'x'in parts[1]and parts[5].startswith('/'):
            assert not parts[5].endswith(' (deleted)'),'Loaded native Python dependency changed during binding';files.add(str(Path(parts[5]).resolve(strict=True)))
    pending=[Path(p)for p in files if Path(p).is_file()];checked=set()
    with(out/'dynamic-libraries.log').open('xb')as log:
        while pending:
            path=pending.pop();real=path.resolve(strict=True)
            if str(real)in checked:continue
            checked.add(str(real));files.add(str(real))
            with real.open('rb')as inp:elf=inp.read(4)==b'\x7fELF'
            if not elf:continue
            result=subprocess.run(['/usr/bin/ldd',str(real)],env=env,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=30);log.write((str(real)+'\n').encode());log.write(result.stdout);text=result.stdout.decode('utf-8','backslashreplace')
            if '=> not found'in text:raise FileNotFoundError('Required original caller/helper native library unavailable: '+str(real))
            assert result.returncode==0 or 'statically linked'in text or 'not a dynamic executable'in text,('ldd failed',str(real),text)
            for line in text.splitlines():
                for token in line.split():
                    if token.startswith('/'):
                        p=Path(token);node=p.parent.resolve(strict=True)/p.name;files.add(str(node));files.add(str(node.resolve(strict=True)));pending.append(node);break
    roots={p for p in roots if not any(p!=q and Path(p).is_relative_to(q)for q in roots)};nodes={};visited=set();total=0
    def inspect(path):
        nonlocal total
        name=str(path)
        if name in nodes:return
        s=path.lstat();assert len(nodes)<250000,'Complete runtime node capacity exceeded'
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
            with path.open('rb')as inp:
                for block in iter(lambda:inp.read(1048576),b''):size+=len(block);digest.update(block)
            end=path.stat();assert(s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns)==(end.st_dev,end.st_ino,end.st_size,end.st_mtime_ns)and size==s.st_size,'Runtime changed during census: '+name
            total+=size;assert total<=100000000000,'Complete runtime byte capacity exceeded';nodes[name]=dict(kind='file',mode=stat.S_IMODE(s.st_mode),bytes=size,sha256=digest.hexdigest())
        else:raise RuntimeError('Nonregular runtime node: '+name)
    while(roots|files)-visited:
        for name in sorted((roots|files)-visited):inspect(Path(name));visited.add(name)
    save(out/'inventory.json',dict(roots=sorted(roots),files=sorted(files),nodes=nodes,complete_bytes=total))
    save(out/'runtime.json',dict(schema_version=1,profile=request['profile'],programs=programs,source=request['source'],pipeline=request['pipeline'],environment=dict(PATH=str(launchers)+':/usr/bin:/bin',LD_LIBRARY_PATH=library),host=dict(os=sys.platform,architecture=os.uname().machine,kernel=os.uname().release),installed=False,analysis_admitted=False))
if __name__=='__main__':
    try:main()
    except BaseException as error:save(Path(sys.argv[1])/'BINDING-FAILURE.json',dict(error=type(error).__name__+': '+str(error),traceback=traceback.format_exc(),unavailable=isinstance(error,(FileNotFoundError,PermissionError,ImportError)),analysis_admitted=False));raise
