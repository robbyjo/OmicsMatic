# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Complete logical evidence trees with deduplicated, checksummed file payloads."""
from pathlib import Path,PurePosixPath
import hashlib,json,zipfile
from contextlib import ExitStack
from Runtime import pin,load,save,unique

def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def safe(name):
    path=PurePosixPath(name)
    if path.is_absolute() or not path.parts or '..' in path.parts or '\\' in name or ':' in name:
        raise ValueError('Unsafe logical evidence path: '+name)
    return path

class Writer:
    def __init__(self,target):
        self.archive=zipfile.ZipFile(target,'x',zipfile.ZIP_DEFLATED,compresslevel=9)
        self.target=Path(target);self.files={};self.blobs={};self.portable=set()
        self.part_paths=[];self.part=None;self.part_bytes=0
    def member(self,name,raw,archive=None):
        info=zipfile.ZipInfo(name,(2026,11,15,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
        info.external_attr=0o100644<<16;(archive or self.archive).writestr(info,raw)
    def add(self,name,path):
        safe(name)
        if name.casefold() in self.portable:raise ValueError('Duplicate/colliding logical evidence path: '+name)
        path=Path(path)
        if path.is_symlink() or not path.is_file():raise ValueError('Evidence payload must be a regular file: '+str(path))
        raw=path.read_bytes();identity=rawpin(raw)
        if pin(path)!=identity:raise ValueError('Evidence changed during capture: '+str(path))
        key='blobs/'+identity['sha256']
        if key not in self.blobs:
            if self.part is None or self.part_bytes+len(raw)>45_000_000:
                if self.part is not None:self.part.close()
                path=self.target.parent/('payloads-%04d.zip'%(len(self.part_paths)+1))
                self.part_paths.append(path);self.part=zipfile.ZipFile(path,'x',zipfile.ZIP_DEFLATED,compresslevel=9)
                self.part_bytes=0
            self.member(key,raw,self.part);self.part_bytes+=len(raw)
            self.blobs[key]=dict(archive=self.part_paths[-1].name,**identity)
        elif {k:self.blobs[key][k] for k in ['bytes','sha256']}!=identity:raise ValueError('Blob identity collision')
        self.files[name]=dict(blob=key,**identity);self.portable.add(name.casefold())
    def tree(self,label,root,exclude=()):
        root=Path(root)
        for path in sorted(root.rglob('*')):
            relative=path.relative_to(root).as_posix()
            if any(relative==part or relative.startswith(part+'/') for part in exclude):continue
            if path.is_symlink():raise ValueError('Declare excluded cache links explicitly: '+str(path))
            if path.is_file():self.add(label+'/'+relative,path)
    def finish(self,qualification):
        if self.part is not None:self.part.close()
        index=dict(schema='omicsmatic-complete-logical-evidence-v2',files=self.files,blobs=self.blobs)
        raw=(json.dumps(index,ensure_ascii=False,allow_nan=False,indent=2)+'\n').encode()
        self.member('EVIDENCE-INDEX.json',raw)
        self.member('QUALIFICATION.json',(json.dumps(qualification,ensure_ascii=False,allow_nan=False,indent=2)+'\n').encode())
        self.archive.close()
        return dict(index=rawpin(raw),logical_files=len(self.files),unique_payloads=len(self.blobs),
          unique_payload_bytes=sum(v['bytes'] for v in self.blobs.values()),payload_archives=len(self.part_paths))

def extract(bundle,out,proof):
    out=Path(out);out.mkdir(parents=True,exist_ok=False)
    if pin(bundle)!=proof['evidence_bundle']:raise ValueError('Frozen evidence archive differs')
    with ExitStack() as stack:
        archive=stack.enter_context(zipfile.ZipFile(bundle))
        names=archive.namelist()
        if set(names)!={'EVIDENCE-INDEX.json','QUALIFICATION.json'} or len(names)!=2:raise ValueError('Unexpected index archive entries')
        raw=archive.read('EVIDENCE-INDEX.json')
        if rawpin(raw)!=proof['evidence_layout']['index']:raise ValueError('Complete logical evidence index differs')
        index=json.loads(raw,object_pairs_hook=unique)
        if index['schema']!='omicsmatic-complete-logical-evidence-v2':raise ValueError('Unsupported evidence layout')
        inner=json.loads(archive.read('QUALIFICATION.json'),object_pairs_hook=unique)
        if inner!={k:v for k,v in proof.items() if k not in ['evidence_layout','evidence_bundle','evidence_payloads']}:
            raise ValueError('Complete inner and outer qualification disagree')
        parts={}
        if {v['archive'] for v in index['blobs'].values()}!=set(proof['evidence_payloads']):raise ValueError('Physical archive population differs')
        for name,identity in proof['evidence_payloads'].items():
            if len(safe(name).parts)!=1 or not name.startswith('payloads-') or not name.endswith('.zip'):
                raise ValueError('Unsafe payload archive name')
            path=Path(bundle).parent/name
            if pin(path)!=identity:raise ValueError('Complete payload archive differs: '+name)
            part=stack.enter_context(zipfile.ZipFile(path));members=part.namelist()
            expected={n for n,v in index['blobs'].items() if v['archive']==name}
            if set(members)!=expected or len(members)!=len(set(n.casefold() for n in members)):
                raise ValueError('Missing/colliding physical payloads: '+name)
            parts[name]=part
        for name,identity in index['blobs'].items():
            expected={k:identity[k] for k in ['bytes','sha256']}
            if name!='blobs/'+identity['sha256'] or rawpin(parts[identity['archive']].read(name))!=expected:
                raise ValueError('Frozen payload differs: '+name)
        portable=set()
        for name,identity in index['files'].items():
            path=safe(name)
            if name.casefold() in portable:raise ValueError('Colliding logical evidence paths')
            portable.add(name.casefold())
            payload=identity['blob']
            if payload not in index['blobs'] or {k:identity[k] for k in ['bytes','sha256']}!={k:index['blobs'][payload][k] for k in ['bytes','sha256']}:
                raise ValueError('Logical payload identity differs: '+name)
            target=out.joinpath(*path.parts);target.parent.mkdir(parents=True,exist_ok=True)
            with target.open('xb') as stream:stream.write(parts[index['blobs'][payload]['archive']].read(payload))
    if len(index['files'])!=proof['evidence_layout']['logical_files'] or len(index['blobs'])!=proof['evidence_layout']['unique_payloads']:
        raise ValueError('Complete logical/physical population differs')
    if len(parts)!=proof['evidence_layout']['payload_archives']:raise ValueError('Payload volume count differs')
    return index
