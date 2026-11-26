# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only permitted-runtime census. No installation and no scientific user analysis."""
import hashlib,json,os,shutil,stat,subprocess,sys,sysconfig,traceback
from pathlib import Path
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n')as f:json.dump(v,f,sort_keys=True,indent=2);f.write('\n')
def main():
    out=Path(sys.argv[1]);request=json.loads((out/'request.json').read_bytes());environment=request['environment']
    env=dict(environment,LC_ALL='C.UTF-8',LANG='C.UTF-8',PYTHONDONTWRITEBYTECODE='1',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',TMPDIR=str(out),XDG_CACHE_HOME=str(out/'cache'))
    if 'HOME'in os.environ:env['HOME']=os.environ['HOME']
    names=['bash','sh','env','sed','grep','uname','which','R','Rscript','java','python3','setsid','prlimit','ldd']
    programs={};selections={}
    for name in names:
        value=shutil.which(name,path=env['PATH'])
        if not value:raise FileNotFoundError('Required permitted runtime program absent: '+name)
        programs[name]=str(Path(value).resolve(strict=True));selections[name]=value
    assert programs['python3']==str(Path(sys.executable).resolve(strict=True)),'Bind using selected isolated Python'
    def run(name,command):
        save(out/(name+'-COMMAND.json'),command)
        with(out/(name+'-stdout')).open('xb')as stdout,(out/(name+'-stderr')).open('xb')as stderr:
            p=subprocess.Popen(command,env=env,cwd=out,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr)
            code=p.wait(timeout=600)
        save(out/(name+'-RESULT.json'),dict(exit_code=code));assert code==0,(name,code)
    run('R-health',[programs['Rscript'],'--vanilla',str(out/'health.R')])
    profile=json.loads((out/'profile.json').read_bytes());actual=json.loads((out/'R-profile.json').read_bytes())
    assert all(actual[k]==profile[k]for k in ['R','DiffBind','packages','functions']),'Original package versions or complete namespace function population differs'
    run('java-health',[programs['java'],'-version'])
    active=set(programs.values())
    maps=['R-MAPS.txt']
    (out/'PYTHON-MAPS.txt').write_bytes(Path('/proc/self/maps').read_bytes());maps.append('PYTHON-MAPS.txt')
    java_pid=os.environ.get('OMICS_DIFFBIND_JAVA_PID')
    if java_pid:
        (out/'JAVA-MAPS.txt').write_bytes(Path('/proc',java_pid,'maps').read_bytes());maps.append('JAVA-MAPS.txt')
    for name in maps:
        for line in(out/name).read_text().splitlines():
            fields=line.split(None,5)
            if len(fields)!=6 or 'x'not in fields[1]or not fields[5].startswith('/'):continue
            if fields[5].endswith(' (deleted)'):raise RuntimeError('Loaded executable/library changed during health: '+fields[5])
            active.add(str(Path(fields[5]).resolve(strict=True)))
    save(out/'active-native-files.json',sorted(active))
    # Whole directory populations, including empty directories, and separate external link targets.
    roots={str(Path(p).resolve(strict=True))for p in request['roots']}
    roots.update([str(Path(request['source']).resolve(strict=True)),environment['JAVA_HOME']]);roots.update(environment['R_LIBS_USER'].split(':'))
    roots.update((out/'R-ROOTS.txt').read_text().splitlines())
    roots.add(sysconfig.get_path('stdlib'))
    roots.update(p for p in environment['LD_LIBRARY_PATH'].split(':')if p)
    missing=[p for p in roots if not Path(p).is_dir()]
    if missing:raise FileNotFoundError('Declared runtime directories unavailable: '+repr(missing)+'. Supply required permitted directories or remove unused search-directory entries in a NEW binding request.')
    roots={str(Path(p).resolve(strict=True))for p in roots}
    roots={p for p in roots if not any(p!=q and Path(p).is_relative_to(q)for q in roots)}
    aliases={p for p in ['/bin','/lib','/lib64','/sbin','/usr/lib64']if Path(p).is_symlink()}
    files=set(programs.values())|set(selections.values())|active|aliases|{'/bin/sh','/usr/bin/env',str(Path('/etc/ld.so.cache').resolve(strict=True))}
    nodes={};visited=set();bytes_total=0;elf=[]
    def inspect(p):
        nonlocal bytes_total
        name=str(p);s=p.lstat()
        if name in nodes:return
        if len(nodes)>=250000:raise RuntimeError('Complete runtime census exceeds250000nodes')
        if stat.S_ISLNK(s.st_mode):
            try:target=p.resolve(strict=True)
            except FileNotFoundError:
                # Debian JDKs can retain an unused src.zip link without installing JDK sources.
                # Required programs and actual ELF dependencies are checked separately, never excused here.
                nodes[name]=dict(kind='dangling-symlink',target=os.readlink(p));return
            nodes[name]=dict(kind='symlink',target=os.readlink(p),real_path=str(target))
            if target.is_dir():
                if name not in aliases:roots.add(str(target))
            else:files.add(str(target))
        elif stat.S_ISDIR(s.st_mode):
            nodes[name]=dict(kind='directory',mode=stat.S_IMODE(s.st_mode))
            for child in sorted(p.iterdir()):inspect(child)
        elif stat.S_ISREG(s.st_mode):
            h=hashlib.sha256();first=b'';size=0
            with p.open('rb')as f:
                for block in iter(lambda:f.read(1048576),b''):
                    if not size:first=block[:4]
                    size+=len(block);h.update(block)
            end=p.stat();assert(s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns)==(end.st_dev,end.st_ino,end.st_size,end.st_mtime_ns)and size==s.st_size,'Runtime changed during census: '+name
            bytes_total+=size
            if bytes_total>100000000000:raise RuntimeError('Runtime exceeds100GB complete census capacity')
            nodes[name]=dict(kind='file',mode=stat.S_IMODE(s.st_mode),bytes=size,sha256=h.hexdigest())
            if first==b'\x7fELF'and name in active:elf.append(p)
        else:raise RuntimeError('Nonregular runtime node: '+name)
    while True:
        pending=(roots|files)-visited
        if not pending:break
        for name in sorted(pending):inspect(Path(name));visited.add(name)
    # Check the active profile's dependency closure, not optional audio/GUI or other unused modules.
    # Full declared-root bytes and directory populations are still pinned above.
    checked=set();ldd=Path('/usr/bin/ldd').resolve(strict=True);inspect(ldd);files.add(str(ldd))
    # Standalone ldd lacks libraries already loaded by the Java/R launchers.
    # Add only existing, fully inventoried launcher directories for INSPECTION;
    # the bound environment used for health and scientific execution is unchanged.
    rhome=Path((out/'R-ROOTS.txt').read_text().splitlines()[0]);jhome=Path(environment['JAVA_HOME'])
    launcher=[jhome/'lib',jhome/'lib/server',rhome/'lib',rhome.parents[1]/'lib']
    launcher=[str(p.resolve(strict=True))for p in launcher if p.is_dir()]
    roots.update(launcher)
    while (roots|files)-visited:
        for name in sorted((roots|files)-visited):inspect(Path(name));visited.add(name)
    inspection=dict(env,LD_LIBRARY_PATH=':'.join([environment['LD_LIBRARY_PATH'],*launcher]))
    save(out/'dynamic-inspection.json',dict(additional_existing_launcher_directories=launcher,inspection_only=True,scientific_environment_changed=False))
    with(out/'dynamic-libraries.log').open('xb')as log:
        while elf:
            p=elf.pop()
            if str(p)in checked:continue
            checked.add(str(p));r=subprocess.run([str(ldd),str(p)],env=inspection,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=15)
            log.write((str(p)+'\n').encode());log.write(r.stdout)
            text=r.stdout.decode('utf-8','backslashreplace')
            if '=> not found'in text:raise FileNotFoundError('Required dynamic library unavailable: '+str(p)+'; inspect dynamic-libraries.log')
            for line in text.splitlines():
                for token in line.split():
                    if token.startswith('/'):
                        q=Path(token).resolve(strict=True);inspect(q);files.add(str(q))
                        with q.open('rb')as stream:
                            if stream.read(4)==b'\x7fELF':elf.append(q)
                        break
    # External directory links found while resolving dependencies must also be fully inventoried.
    while (roots|files)-visited:
        for name in sorted((roots|files)-visited):inspect(Path(name));visited.add(name)
    save(out/'inventory.json',dict(roots=sorted(roots),files=sorted(files),nodes=nodes,complete_bytes=bytes_total))
    save(out/'runtime.json',dict(schema_version=1,status='COMPLETE_DIFFBIND_RUNTIME_BINDING_ONLY',source=str(Path(request['source']).resolve(strict=True)),environment=environment,programs=programs,selections=selections,host=dict(os=sys.platform,architecture=os.uname().machine,kernel=os.uname().release),analysis_admitted=False,software_installed=False))
if __name__=='__main__':
    try:main()
    except BaseException as e:
        out=Path(sys.argv[1]);save(out/'BINDING-FAILURE.json',dict(error=type(e).__name__+': '+str(e),traceback=traceback.format_exc(),unavailable=isinstance(e,(FileNotFoundError,PermissionError)),analysis_admitted=False));raise
