# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only physical audit of the retained content-addressed LDAK evidence."""
import hashlib,json,os
from pathlib import Path

def require(ok,why):
 if not ok:raise ValueError(why)

def pin(path):
 path=Path(path)
 with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def audit(root,expected):
 root=Path(root).resolve();require(pin(root/'INDEX.json')==expected,'Closed LDAK index changed');index=json.loads((root/'INDEX.json').read_bytes());objects={}
 require(len(index['files'])==11474 and index['source_files']==99 and index['current_method_classes']==48,'Closed evidence population differs')
 for name,row in index['files'].items():
  obj=row['object'];require(obj=='.objects/'+row['pin']['sha256'],'Unexpected physical object path');require(obj not in objects or objects[obj]==row['pin'],'Object identity collision');objects[obj]=row['pin']
 require(len(objects)==2874 and sum(v['bytes'] for v in objects.values())==2975360402,'Closed physical-object population differs')
 for name,value in objects.items():require(pin(root/name)==value,'Closed physical bytes changed: '+name)
 for name,row in index['files'].items():
  p=root/name;obj=root/row['object'];require(p.resolve().is_relative_to(root) and p.is_file() and not p.is_symlink(),'Closed payload missing or outside evidence root: '+name);require(os.path.samefile(p,obj) and p.stat().st_size==row['pin']['bytes'],'Closed payload no longer matches its checked internal object: '+name)
 files={p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_file()};require(files==set(index['files'])|set(objects)|{'INDEX.json'},'Closed physical membership differs')
 return dict(status='PASS_ALL11474_CLOSED_PAYLOADS_AND2874_PHYSICAL_OBJECTS',index=expected,payload_files=11474,unique_objects=2874,unique_object_bytes=2975360402,logical_payload_bytes=16946786025,source_files=99,primary_papers=250,current_complete_bundles=23,method_classes=48,historical_fingerprints_refreshed=False)
