# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read Linux evidence without following Batch links; export exact regular bytes and explicit link metadata."""
import argparse,hashlib,json,os,stat
from pathlib import Path

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--base',required=True,type=Path);p.add_argument('--plan',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();base=a.base.resolve(strict=True);out=a.out;assert not out.exists() and out.parent.resolve(strict=True)==out.parent;out.mkdir();(out/'objects').mkdir();records={};excluded=[];total=0
    for item in json.loads(a.plan.read_bytes()):
        source=base/item['source'];assert source.resolve(strict=True)==source and source.is_relative_to(base)
        for parent,dirs,files in os.walk(source,followlinks=False):
            here=Path(parent)
            for name in sorted([*dirs,*files]):
                path=here/name;rel=path.relative_to(source).as_posix();logical=item['logical']+'/'+rel;mode=path.lstat().st_mode
                complete=item['source'].startswith('cli-')
                omit=not complete and (name in ['classes','archives','mamba-root','private-cache','private-pycache','__pycache__'] or name.startswith(('hail-temp','hail-local-temp','private-temp','private-local-temp')) or name.endswith(('.jar','.whl','.tar.gz','.tar.bz2','.conda')))
                if omit:
                    excluded.append(logical)
                    if name in dirs:dirs.remove(name)
                    continue
                assert logical not in records
                if stat.S_ISLNK(mode):
                    if name in dirs:dirs.remove(name)
                    records[logical]=dict(kind='symlink',target=os.readlink(path),resolved=str(path.resolve()),target_exists=path.exists())
                    if path.is_file():
                        raw=path.read_bytes();records[logical]['content']=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
                elif stat.S_ISDIR(mode):records[logical]=dict(kind='directory')
                else:
                    assert stat.S_ISREG(mode),str(path);raw=path.read_bytes();digest=hashlib.sha256(raw).hexdigest();total+=len(raw);assert total<536870912;target=out/'objects'/digest
                    if target.exists():assert target.read_bytes()==raw
                    else:target.write_bytes(raw)
                    assert path.read_bytes()==raw,'Evidence changed while capturing: '+str(path);records[logical]=dict(kind='file',bytes=len(raw),sha256=digest)
    (out/'index.json').write_text(json.dumps(dict(schema_version=1,status='PASS_EXACT_LINUX_REGULAR_BYTES_AND_LINK_METADATA_ONLY',records=records,excluded_runtime_archives_caches_and_compile_outputs=excluded,total_regular_bytes=total),sort_keys=True,indent=2)+'\n',encoding='utf8',newline='\n');print(len(records),'evidence records;',total,'regular bytes; no runtime execution')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable evidence capture checks')
    main()
