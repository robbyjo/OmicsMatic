# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind current FUMA source dependencies after proving a rebuilt JAR is byte-identical."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def pin(path):
    with path.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
    return dict(bytes=path.stat().st_size,sha256=digest)


def class_info(raw):
    if raw[:4]!=b'\xca\xfe\xba\xbe':raise ValueError('Not a Java class')
    def u2(p):return struct.unpack_from('>H',raw,p)[0]
    def u4(p):return struct.unpack_from('>I',raw,p)[0]
    count=u2(8);pool=[None]*count;classes=[];offset=10;index=1
    widths={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
    while index<count:
        tag=raw[offset];offset+=1
        if tag==1:
            n=u2(offset);offset+=2;pool[index]=raw[offset:offset+n].decode('utf-8',errors='replace');offset+=n
        else:
            if tag not in widths:raise ValueError('Unknown constant-pool tag')
            if tag==7:classes.append(u2(offset))
            offset+=widths[tag]
            if tag in {5,6}:index+=1
        index+=1
    offset+=6;offset+=2+2*u2(offset)
    for _ in range(2): # fields, methods
        n=u2(offset);offset+=2
        for _ in range(n):
            offset+=6;attributes=u2(offset);offset+=2
            for _ in range(attributes):offset+=6+u4(offset+2)
    n=u2(offset);offset+=2;source=None
    for _ in range(n):
        name=pool[u2(offset)];length=u4(offset+2);offset+=6
        if name=='SourceFile':
            assert length==2;source=pool[u2(offset)]
        offset+=length
    assert offset==len(raw)
    dependencies=[]
    for index in classes:
        name=pool[index]
        if name.startswith('['):name=name.lstrip('[');name=name[1:-1] if name.startswith('L') and name.endswith(';') else ''
        if name:dependencies.append(name)
    return source,dependencies


def main():
    if not __debug__:raise RuntimeError('Never disable source-closure assertions')
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--rebuilt',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);a=parser.parse_args()
    proof= json.loads((ROOT/'docs/literature/fuma-expression-preparation-2/VALIDATION.json').read_text(encoding='utf-8'));artifact=ROOT/proof['artifact']['path'];expected={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert pin(artifact)==pin(a.rebuilt)==expected,'Current-source rebuild must be identical to the scientifically tested complete JAR'
    files={};checked={};excluded='org/jlinalg/cli/JLinAlgCli';missing=[]
    with zipfile.ZipFile(artifact) as archive:
        names={n[:-6] for n in archive.namelist() if n.endswith('.class')};seeds={n for n in names if n.rsplit('/',1)[-1].startswith('Fuma') or n.startswith('org/jlinalg/genetics/HardcallLd')};pending=list(seeds);seen=set()
        while pending:
            name=pending.pop()
            if name in seen or not name.startswith('org/jlinalg/') or name not in names:continue
            seen.add(name)
            if name==excluded:continue # Existing non-scientific dispatcher; only version() is used by these stages.
            raw=archive.read(name+'.class');source,dependencies=class_info(raw);assert source and '/' not in source and '\\' not in source
            package=name.rsplit('/',1)[0];path=ROOT/'src/main/java'/package/source
            if not path.is_file():missing.append(dict(name=name,source=source));continue
            relative=path.relative_to(ROOT).as_posix();files[relative]=pin(path);checked[name+'.class']=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());pending.extend(dependencies)
    assert not missing,missing
    assert 'src/main/java/org/jlinalg/genetics/HardcallLd.java' in files and any('FumaFisher' in n for n in files)
    assert len(files)>40 and len(checked)>100
    result=dict(status='passed-current-FUMA-source-dependency-closure-with-identical-rebuild',artifact=proof['artifact'],rebuilt_artifact=dict(path=a.rebuilt.resolve().relative_to(ROOT).as_posix(),**pin(a.rebuilt)),whole_JAR_bytes_identical=True,source_files=files,packaged_classes=checked,source_file_count=len(files),class_count=len(checked),metadata_dispatcher_excluded=dict(class_name=excluded,reason='Existing generic dispatcher is non-scientific metadata/route code; FUMA stages only call its version(). Current full rebuilt JAR still matches every byte of the tested artifact.'),collector_pin=pin(Path(__file__)),scientific_analysis_reexecuted=False)
    assert not a.out.exists();a.out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');print(json.dumps(dict(status=result['status'],source_files=len(files),classes=len(checked),whole_JAR_bytes_identical=True)))


if __name__=='__main__':main()
