"""Bind permitted original RFMix and isolated helpers. No installation or network access."""
import hashlib,json,os,shlex,stat,subprocess,sys,traceback
from pathlib import Path

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n')as out:json.dump(value,out,sort_keys=True,indent=2,allow_nan=False);out.write('\n')

def main():
    out=Path(sys.argv[1]);request=json.loads((out/'request.json').read_bytes());environment=request['environment']
    env=dict(environment,HOME=str(out),TMPDIR=str(out),LC_ALL='C',LANG='C')
    programs={'rfmix':str(Path(request['executable']).resolve(strict=True))}
    for name,value in request['helpers'].items():
        if value is not None:programs[name]=str(Path(value).resolve(strict=True))
    for name,value in programs.items():
        with Path(value).open('rb')as stream:header=stream.read(20)
        assert header[:6]==b'\x7fELF\x02\x01'and int.from_bytes(header[18:20],'little')==62,('Require original Linux x86_64 ELF executable',name)
    launchers=out/'bin';launchers.mkdir()
    for name in ['gzip','bcftools']:
        if name not in programs:continue
        lib=request['bcftools_library_path']if name=='bcftools'else environment['LD_LIBRARY_PATH']
        text='#!/bin/sh\nexport PATH=/usr/bin:/bin\nexport LC_ALL=C\nexport LD_LIBRARY_PATH='+shlex.quote(lib)+'\nexport BCFTOOLS_PLUGINS=\nexec '+shlex.quote(programs[name])+' "$@"\n'
        (launchers/name).write_text(text,encoding='utf-8',newline='\n');(launchers/name).chmod(0o755)
    execution=dict(env,PATH=str(launchers))
    def run(name,command,selected=execution):
        save(out/(name+'-COMMAND.json'),command)
        with(out/(name+'-stdout')).open('xb')as stdout,(out/(name+'-stderr')).open('xb')as stderr:
            child=subprocess.Popen(command,env=selected,cwd=out,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr);code=child.wait(timeout=120)
        save(out/(name+'-RESULT.json'),dict(exit_code=code));assert code==0,(name,code)
    run('original-help',[programs['rfmix'],'--help'])
    banner=(out/'original-help-stdout').read_bytes()+(out/'original-help-stderr').read_bytes()
    assert b'v2.03-r0'in banner,'Original version banner differs'
    run('gzip-health',[str(launchers/'gzip'),'--version'])
    if 'bcftools'in programs:run('bcftools-health',[str(launchers/'bcftools'),'--version'])
    # Own synthetic smoke cohort; no author data or original model source is redistributed.
    health=out/'health';health.mkdir();queries=['Q0','Q1'];refs=['R'+str(i)for i in range(12)]
    header='##fileformat=VCFv4.2\n##contig=<ID=22,length=1000000>\n##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">\n#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\t'
    with(health/'query.vcf').open('x')as q,(health/'reference.vcf').open('x')as r,(health/'genetic.map').open('x')as g:
        q.write(header+'\t'.join(queries)+'\n');r.write(header+'\t'.join(refs)+'\n')
        for i in range(500):
            prefix='22\t'+str(1000+i*1000)+'\t.\tA\tG\t.\tPASS\t.\tGT\t'
            q.write(prefix+'0|1\t'+('0|0'if i<250 else'1|1')+'\n')
            r.write(prefix+'\t'.join(('0|0'if j<6 else'1|1')if(i+j*7)%29 else'0|1'for j in range(12))+'\n')
            g.write('22\t'+str(1000+i*1000)+'\t'+repr(i*.001)+'\n')
    (health/'sample.map').write_text(''.join(name+'\t'+('A'if i<6 else'B')+'\n'for i,name in enumerate(refs)))
    command=[programs['rfmix'],'-f',str(health/'query.vcf'),'-r',str(health/'reference.vcf'),'-m',str(health/'sample.map'),'-g',str(health/'genetic.map'),'-o',str(health/'result'),'--chromosome=22','--rf-window-size=50','--crf-spacing=5','--crf-weight=1','--generations=8','--em-iterations=0','--trees=100','--node-size=2','--max-missing=0.05','--bootstrap-mode=1','--rf-minimum-snps=10','--n-threads=1','--random-seed=20261127']
    run('original-model-health',command)
    for suffix in ['msp.tsv','fb.tsv','rfmix.Q','sis.tsv']:assert(health/('result.'+suffix)).stat().st_size>0
    roots={str(Path(p).resolve(strict=True))for p in request['roots']+[request['source'],request['prefix'],str(launchers)]}
    for value in [environment['LD_LIBRARY_PATH'],request['bcftools_library_path']]:roots.update(str(Path(p).resolve(strict=True))for p in value.split(':')if p)
    roots={p for p in roots if not any(p!=q and Path(p).is_relative_to(q)for q in roots)}
    aliases={p for p in ['/bin','/lib','/lib64','/sbin','/usr/lib64']if Path(p).is_symlink()}
    files=set(programs.values())|aliases|{'/usr/bin/sh','/usr/bin/ldd','/etc/ld.so.cache'}|{str(launchers/n)for n in programs if n!='rfmix'}
    nodes={};visited=set();bytes_total=0
    def inspect(path):
        nonlocal bytes_total
        name=str(path);s=path.lstat()
        if name in nodes:return
        assert len(nodes)<250000,'Runtime census exceeds node budget'
        if stat.S_ISLNK(s.st_mode):
            target=path.resolve(strict=True);nodes[name]=dict(kind='symlink',target=os.readlink(path),real_path=str(target))
            if target.is_dir():
                if name not in aliases:roots.add(str(target))
            else:files.add(str(target))
        elif stat.S_ISDIR(s.st_mode):
            nodes[name]=dict(kind='directory',mode=stat.S_IMODE(s.st_mode))
            for child in sorted(path.iterdir()):inspect(child)
        elif stat.S_ISREG(s.st_mode):
            h=hashlib.sha256();size=0
            with path.open('rb')as stream:
                for block in iter(lambda:stream.read(1048576),b''):size+=len(block);h.update(block)
            end=path.stat();assert(s.st_dev,s.st_ino,s.st_size,s.st_mtime_ns)==(end.st_dev,end.st_ino,end.st_size,end.st_mtime_ns)and size==s.st_size,'Runtime changed during census: '+name
            bytes_total+=size;assert bytes_total<=100000000000,'Runtime byte budget exceeded'
            nodes[name]=dict(kind='file',mode=stat.S_IMODE(s.st_mode),bytes=size,sha256=h.hexdigest())
        else:raise RuntimeError('Nonregular runtime node: '+name)
    # The scientific process and BCF helper resolve libraries in separate environments.
    checked=set();pending=[(Path(value),request['bcftools_library_path']if name=='bcftools'else environment['LD_LIBRARY_PATH'])for name,value in programs.items()]
    pending.append((Path('/bin/sh').resolve(strict=True),environment['LD_LIBRARY_PATH']))
    with(out/'dynamic-libraries.log').open('xb')as log:
        while pending:
            path,lib=pending.pop();key=(str(path),lib)
            if key in checked:continue
            checked.add(key);result=subprocess.run(['/usr/bin/ldd',str(path)],env=dict(env,PATH='/usr/bin:/bin',LD_LIBRARY_PATH=lib),stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=15)
            log.write((str(path)+' LD_LIBRARY_PATH='+lib+'\n').encode());log.write(result.stdout)
            text=result.stdout.decode('utf-8','backslashreplace')
            if '=> not found'in text:raise FileNotFoundError('Required dynamic library unavailable: '+str(path))
            assert result.returncode==0 or 'statically linked'in text or 'not a dynamic executable'in text,('ldd failed',path,text)
            for line in text.splitlines():
                for token in line.split():
                    if token.startswith('/'):
                        lexical=Path(token);node=lexical.parent.resolve(strict=True)/lexical.name;real=node.resolve(strict=True);files.update([str(node),str(real)]);pending.append((real,lib));break
    while(roots|files)-visited:
        for name in sorted((roots|files)-visited):inspect(Path(name));visited.add(name)
    save(out/'inventory.json',dict(roots=sorted(roots),files=sorted(files),nodes=nodes,complete_bytes=bytes_total))
    save(out/'runtime.json',dict(schema_version=1,profile=request['profile'],source=str(Path(request['source']).resolve(strict=True)),environment=environment,programs=programs,bcftools_library_path=request['bcftools_library_path'],host=dict(os=sys.platform,architecture=os.uname().machine,kernel=os.uname().release),software_installed=False,analysis_admitted=False))

if __name__=='__main__':
    try:main()
    except BaseException as error:
        save(Path(sys.argv[1])/'BINDING-FAILURE.json',dict(error=type(error).__name__+': '+str(error),traceback=traceback.format_exc(),unavailable=isinstance(error,(FileNotFoundError,PermissionError)),analysis_admitted=False));raise
