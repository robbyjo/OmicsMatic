# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Census the exact permitted WASP/Python/BWA/Samtools/converter runtime. No installation."""
import hashlib,json,os,shlex,stat,subprocess,sys,sysconfig,traceback
from pathlib import Path

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n')as stream:json.dump(value,stream,sort_keys=True,indent=2,allow_nan=False);stream.write('\n')

def main():
    out=Path(sys.argv[1]);request=json.loads((out/'request.json').read_bytes());profile=json.loads((out/'profile.json').read_bytes())
    env=dict(PATH='/usr/bin:/bin',LD_LIBRARY_PATH=request['library_paths']['python'],HOME=str(out),TMPDIR=str(out),LC_ALL='C',LANG='C',PYTHONDONTWRITEBYTECODE='1',PYTHONNOUSERSITE='1',PYTHONHASHSEED='0',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1')
    programs={key:(str(Path(request[key]).resolve(strict=True))if request[key]is not None else None)for key in ['python','bwa','samtools','snp2h5']}
    programs['python']=str(Path(request['python'])) # Preserve venv selection even when its executable is a symlink.
    assert str(Path(programs['python']).resolve(strict=True))==str(Path(sys.executable).resolve(strict=True)),'Bind with the selected private Python'
    for name,value in programs.items():
        if value is None:continue
        with Path(value).open('rb')as stream:header=stream.read(20)
        assert header[:6]==b'\x7fELF\x02\x01'and int.from_bytes(header[18:20],'little')==62,('Require original Linux x86_64 ELF executable',name)
    if list(sys.version_info[:2])!=profile['python_major_minor']:raise FileNotFoundError('Required compatible Python3.11 runtime unavailable; install a separate permitted runtime')
    import numpy,pysam,tables
    actual=dict(numpy=numpy.__version__,pysam=pysam.__version__,tables=tables.__version__)
    if actual!=profile['packages']:raise FileNotFoundError('Required compatible WASP Python packages unavailable: expected '+repr(profile['packages'])+'; found '+repr(actual)+'. Install them privately and prepare a NEW binding.')
    # Source import uses only the exact profiled mapping directory; no source is changed.
    mapping=Path(request['wasp_source'])/'mapping';sys.path.insert(0,str(mapping));import find_intersecting_snps as original
    generated=original.generate_reads('AAA',[2],numpy.array([b'A']),numpy.array([b'C']))
    assert generated=={'AAA','ACA'},'Original WASP generation health differs'
    assert numpy.int is int,'Original NumPy alias prerequisite differs'
    launchers=out/'bin';launchers.mkdir()
    for name in ['bwa','samtools','snp2h5']:
        if programs[name]is None:continue
        lib=''if name=='bwa'else request['library_paths'][name]
        text='#!/bin/sh\nexport PATH=/usr/bin:/bin\nexport LC_ALL=C\nexport LD_LIBRARY_PATH='+shlex.quote(lib)+'\nexec '+shlex.quote(programs[name])+' "$@"\n'
        (launchers/name).write_text(text,encoding='utf-8',newline='\n');(launchers/name).chmod(0o755)
    def run(name,command,expected=0):
        save(out/(name+'-COMMAND.json'),command)
        with(out/(name+'-stdout')).open('xb')as stdout,(out/(name+'-stderr')).open('xb')as stderr:
            p=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);code=p.wait(timeout=120)
        save(out/(name+'-RESULT.json'),dict(exit_code=code,expected=expected));assert code==expected,(name,code)
    run('bwa-version',[str(launchers/'bwa')],1)
    assert b'0.7.17-r1188'in(out/'bwa-version-stderr').read_bytes(),'BWA0.7.17 version differs'
    run('samtools-version',[str(launchers/'samtools'),'--version'])
    assert(out/'samtools-version-stdout').read_bytes().startswith(b'samtools 1.19.2\n'),'Samtools helper version differs'
    if programs['snp2h5']is not None:
        health=out/'hdf-health';health.mkdir();(health/'chromInfo.txt').write_text('chr1\t100\n')
        (health/'chr1.vcf').write_text('##fileformat=VCFv4.2\n##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tS1\nchr1\t20\tv1\tA\tC\t.\tPASS\t.\tGT\t0|1\n')
        run('original-hdf-converter',[str(launchers/'snp2h5'),'--chrom',str(health/'chromInfo.txt'),'--format','vcf','--haplotype',str(health/'haplotypes.h5'),'--snp_index',str(health/'snp-index.h5'),'--snp_tab',str(health/'snp-table.h5'),str(health/'chr1.vcf')])
        for name in ['haplotypes','snp-index','snp-table']:
            with tables.open_file(health/(name+'.h5'),'r')as hdf:assert list(hdf.root._v_children),'Original converter produced no HDF5 nodes'
    save(out/'PYTHON-HEALTH.json',dict(python=sys.version,packages=actual,original_generation_smoke=True,source_modified=False))
    # Import health covers actual extension modules used by mapping/HDF5 IO.
    active={str(Path(v).resolve(strict=True))for v in programs.values()if v is not None}
    maps=Path('/proc/self/maps').read_text();(out/'PYTHON-MAPS.txt').write_text(maps)
    for line in maps.splitlines():
        parts=line.split(None,5)
        if len(parts)==6 and 'x'in parts[1]and parts[5].startswith('/'):
            assert not parts[5].endswith(' (deleted)'),'Loaded native library changed during health';active.add(str(Path(parts[5]).resolve(strict=True)))
    roots={str(Path(p).resolve(strict=True))for p in request['roots']+[request['wasp_source'],request['bwa_source'],str(launchers),sysconfig.get_path('stdlib')]}
    for value in request['library_paths'].values():roots.update(str(Path(p).resolve(strict=True))for p in value.split(':')if p)
    roots={p for p in roots if not any(p!=q and Path(p).is_relative_to(q)for q in roots)}
    aliases={p for p in ['/bin','/lib','/lib64','/sbin','/usr/lib64']if Path(p).is_symlink()}
    files=active|aliases|{p for p in programs.values()if p is not None}|{'/usr/bin/sh','/usr/bin/ldd','/usr/bin/setsid','/usr/bin/prlimit','/etc/ld.so.cache'}
    files.update(str(launchers/name)for name in ['bwa','samtools','snp2h5']if programs[name]is not None)
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
    python_active={path for path in active if path not in [programs['bwa'],programs['samtools'],programs['snp2h5']]}
    python_loader_directories=sorted({str(Path(path).parent)for path in python_active})
    save(out/'dynamic-inspection.json',dict(additional_existing_Python_loader_directories=python_loader_directories,inspection_only=True,scientific_environment_changed=False,reason='Standalone ldd omits the Python launcher and extension-module RPATH namespace already loaded during actual import health.'))
    pending=[]
    for name,value in programs.items():
        if value is not None:pending.append((Path(value),''if name=='bwa'else request['library_paths']['python'if name=='python'else name],name=='python'))
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
    save(out/'runtime.json',dict(schema_version=1,profile=request['profile'],programs=programs,wasp_source=request['wasp_source'],bwa_source=request['bwa_source'],library_paths=request['library_paths'],environment=dict(PATH=str(launchers)+':/usr/bin:/bin',LD_LIBRARY_PATH=request['library_paths']['python']),host=dict(os=sys.platform,architecture=os.uname().machine,kernel=os.uname().release),installed=False,user_analysis_performed=False))

if __name__=='__main__':
    try:main()
    except BaseException as error:
        save(Path(sys.argv[1])/'BINDING-FAILURE.json',dict(error=type(error).__name__+': '+str(error),traceback=traceback.format_exc(),unavailable=isinstance(error,(FileNotFoundError,PermissionError,ImportError)),analysis_admitted=False));raise
