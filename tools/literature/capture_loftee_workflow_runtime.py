# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture exact private VEP105/Perl/BioPerl code and checked prefix-relocation descriptions; executes no variant annotation."""
import argparse,concurrent.futures,hashlib,json,os,re,stat,subprocess,sys,time
from pathlib import Path


def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def scan(root,kind):
    root=root.resolve();old=str(root).encode();files={};total=0;prefix_count=0
    for directory,dirs,names in os.walk(root,followlinks=False):
        base=Path(directory);relative=base.relative_to(root).parts
        dirs[:]=sorted(d for d in dirs if not(kind=='environment' and not relative and d=='conda-meta') and not(kind=='bioperl' and relative==('Bio',) and d=='EnsEMBL') and d!='tmp')
        for name in sorted([*names,*[d for d in dirs if (base/d).is_symlink()]]):
            path=base/name;rel=path.relative_to(root).as_posix();s=path.lstat()
            if stat.S_ISLNK(s.st_mode):
                target=os.readlink(path);resolved=path.resolve();assert resolved.is_relative_to(root),(path,target);files[rel]=dict(kind='symlink',target=target,mode=stat.S_IMODE(s.st_mode));continue
            assert stat.S_ISREG(s.st_mode),(path,'Not a regular file');raw=path.read_bytes();assert len(raw)==s.st_size<=536870912,(path,'runtime file limit');entry=dict(kind='file',mode=stat.S_IMODE(s.st_mode),**pin(raw));total+=len(raw)
            if old in raw:
                prefix_count+=1
                try:raw.decode('utf8');is_text=b'\0' not in raw
                except UnicodeDecodeError:is_text=False
                if is_text:entry['prefix_relocation']=dict(kind='exact-text',old_prefix=str(root))
                else:
                    regions=[];start=0
                    while True:
                        at=raw.find(old,start)
                        if at<0:break
                        end=raw.find(b'\0',at);assert end>=at,(path,'Unterminated prefix string');regions.append(dict(offset=at,length=end-at));start=end+1
                    entry['prefix_relocation']=dict(kind='exact-NUL-terminated-binary-fields-shorter-or-equal-prefix',old_prefix=str(root),regions=regions)
            files[rel]=entry
    return dict(kind=kind,baseline_root=str(root),files=files,total_file_bytes=total,regular_files=sum(x['kind']=='file' for x in files.values()),symlinks=sum(x['kind']=='symlink' for x in files.values()),prefix_relocated_files=prefix_count)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--environment',required=True,type=Path);p.add_argument('--bioperl',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();assert sys.platform.startswith('linux');out=a.out.resolve();assert not out.exists();out.mkdir(parents=True);start=time.monotonic();records={}
    for kind,root in [('environment',a.environment),('bioperl',a.bioperl)]:
        records[kind]=scan(root,kind);(out/(kind+'.json')).write_text(json.dumps(records[kind],indent=2)+'\n',encoding='utf8');print(kind,records[kind]['regular_files'],'files',records[kind]['total_file_bytes'],'bytes',records[kind]['prefix_relocated_files'],'relocation files',flush=True)
    root=a.environment.resolve();elfs=[root/'bin'/n for n in ['perl','samtools','bgzip','tabix']];elfs.extend(p for p in (root/'lib/perl5').rglob('*.so') if p.is_file());host={};unresolved={};ldd={}
    def dependencies(path):
        r=subprocess.run(['/usr/bin/ldd',str(path)],capture_output=True,env={'PATH':'/usr/bin:/bin','LC_ALL':'C','LD_LIBRARY_PATH':str(root/'lib')},timeout=60);return str(path),r.returncode,r.stdout.decode('utf8',errors='replace'),r.stderr.decode('utf8',errors='replace')
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        for name,code,stdout,stderr in pool.map(dependencies,elfs):
            ldd[name]=dict(exit_code=code,stdout=stdout,stderr=stderr)
            if 'not found' in stdout:unresolved[name]=stdout
            for value in re.findall(r'(?<!\S)(/[^\s()]+)',stdout):
                path=Path(value).resolve()
                if not path.is_relative_to(root):host[str(path)]=pin(path.read_bytes())
    for name in ['/usr/bin/dash','/etc/ld.so.cache']:
        path=Path(name).resolve();host[str(path)]=pin(path.read_bytes())
    (out/'host-and-ELF-dependencies.json').write_text(json.dumps(dict(host_files=host,unresolved_optional_package_objects=unresolved,ldd=ldd),indent=2)+'\n',encoding='utf8');result=dict(status='captured-private-original-VEP105-code-and-relocation-baseline',environment_files=records['environment']['regular_files'],bioperl_files=records['bioperl']['regular_files'],host_files=len(host),elapsed_seconds=time.monotonic()-start,variant_analysis_executed=False,implementation_credit=False);(out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf8');print(json.dumps(result),flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original/runtime path and byte checks')
    main()
