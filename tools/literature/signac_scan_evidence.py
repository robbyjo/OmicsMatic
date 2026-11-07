# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Lossless content-addressed evidence ZIPs retaining every logical file and its exact bytes."""
import hashlib,json,re,zipfile
from pathlib import PurePosixPath

def byte_pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def safe(name):
    p=PurePosixPath(name)
    if not name or p.is_absolute() or '..' in p.parts or '.' in p.parts or p.as_posix()!=name or any(c in name for c in '\\:\t\r\n\0'):raise ValueError('Unsafe logical evidence path: '+name)
def strict_json(raw):
    def pairs(items):
        result={}
        for k,v in items:
            if k in result:raise ValueError('Duplicate evidence JSON key')
            result[k]=v
        return result
    return json.loads(raw.decode('utf8'),object_pairs_hook=pairs)

def pack(path,files):
    logical={};objects={}
    for name,raw in sorted(files.items()):
        safe(name);value=byte_pin(raw);logical[name]=value;digest=value['sha256']
        if digest in objects and objects[digest]!=raw:raise ValueError('Conflicting content hash')
        objects[digest]=raw
    members={'objects/'+digest:raw for digest,raw in objects.items()};members['logical-files.json']=(json.dumps(dict(schema_version=1,files=logical),sort_keys=True,indent=2)+'\n').encode('utf8')
    with zipfile.ZipFile(path,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(members.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    record=dict(archive_layout='sha256-objects-with-logical-file-map',archive_members=len(members),logical_files=len(logical),archive_file_pins={n:byte_pin(v) for n,v in members.items()},logical_file_pins=logical)
    if unpack(path,record)!=files:raise ValueError('Content-addressed evidence did not round-trip exactly')
    return record

def unpack(path,record):
    if record['archive_layout']!='sha256-objects-with-logical-file-map':raise ValueError('Unsupported evidence archive layout')
    with zipfile.ZipFile(path) as z:
        names=z.namelist()
        if len(names)!=len(set(names)) or len(names)!=record['archive_members'] or set(names)!=set(record['archive_file_pins']):raise ValueError('Physical evidence inventory differs')
        objects={};index=None
        for name in names:
            if name!='logical-files.json' and not re.fullmatch(r'objects/[0-9a-f]{64}',name):raise ValueError('Unexpected physical evidence member')
            raw=z.read(name);value=byte_pin(raw)
            if value!=record['archive_file_pins'][name]:raise ValueError('Physical evidence hash/length differs: '+name)
            if name=='logical-files.json':index=strict_json(raw)
            else:
                if value['sha256']!=name.removeprefix('objects/'):raise ValueError('Object content differs from its SHA-256 name')
                objects[value['sha256']]=raw
        if index is None or index.get('schema_version')!=1 or set(index)!={'schema_version','files'} or index['files']!=record['logical_file_pins'] or len(index['files'])!=record['logical_files']:raise ValueError('Logical evidence inventory differs')
        files={}
        for name,want in index['files'].items():
            safe(name);digest=want['sha256']
            if digest not in objects or byte_pin(objects[digest])!=want:raise ValueError('Logical evidence content differs: '+name)
            files[name]=objects[digest]
        if set(objects)!={v['sha256'] for v in index['files'].values()}:raise ValueError('Unreferenced evidence object')
        return files
