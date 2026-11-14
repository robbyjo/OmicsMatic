#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read lossless content-addressed evidence without changing original scientific bytes."""
from pathlib import Path,PurePosixPath
import hashlib,json,re,zipfile

def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for block in iter(lambda:f.read(1048576),b''):h.update(block);n+=len(block)
 return dict(bytes=n,sha256=h.hexdigest())

def rawpin(b):return dict(bytes=len(b),sha256=hashlib.sha256(b).hexdigest())

def safe_name(n):
 p=PurePosixPath(n)
 if not isinstance(n,str) or p.is_absolute() or p.as_posix()!=n or '\\' in n or any(x in ['', '.', '..'] for x in n.split('/')):raise ValueError('Unsafe retained logical path')
 return n

class EvidenceStore:
 def __init__(self,archive,index):
  self.archive=Path(archive);self.index=index
  if index.get('schema_version')!=1 or index.get('storage')!='sha256-objects-with-lossless-logical-paths':raise ValueError('Exact lossless evidence storage schema required')
  for kind in ['files','links','directories']:
   if not isinstance(index[kind],dict):raise ValueError('Complete retained identity maps required')
   for name in index[kind]:safe_name(name)
  names=[n for kind in ['files','links','directories'] for n in index[kind]]
  if len(names)!=len(set(names)):raise ValueError('A logical identity appears in more than one file type')
  self.blobs={}
  for name,row in index['files'].items():
   if type(row['bytes'])!=int or row['bytes']<0 or not re.fullmatch('[a-f0-9]{64}',row['sha256']):raise ValueError('Exact complete object size/hash required')
   value={k:row[k] for k in ['bytes','sha256']};old=self.blobs.setdefault(row['sha256'],value)
   if old!=value:raise ValueError('A content digest has inconsistent file identities')
 def verify(self,expected_archive):
  if self.archive.is_symlink() or pin(self.archive)!=expected_archive:raise ValueError('Exact complete evidence archive differs')
  with zipfile.ZipFile(self.archive) as z:
   names=z.namelist();wanted={'objects/'+n for n in self.blobs}
   if len(names)!=len(set(names)) or set(names)!=wanted:raise ValueError('Complete unique evidence-object population differs')
   for digest,wanted in self.blobs.items():
    h=hashlib.sha256();size=0
    with z.open('objects/'+digest) as f:
     for block in iter(lambda:f.read(1048576),b''):h.update(block);size+=len(block)
    if dict(bytes=size,sha256=h.hexdigest())!=wanted:raise ValueError('Complete raw evidence object differs: '+digest)
  return dict(logical_files=len(self.index['files']),unique_objects=len(self.blobs),retained_links=len(self.index['links']),retained_directories=len(self.index['directories']),logical_bytes=sum(r['bytes'] for r in self.index['files'].values()),unique_bytes=sum(r['bytes'] for r in self.blobs.values()))
 def read(self,name):
  safe_name(name);row=self.index['files'][name]
  with zipfile.ZipFile(self.archive) as z:raw=z.read('objects/'+row['sha256'])
  if rawpin(raw)!={k:row[k] for k in ['bytes','sha256']}:raise ValueError('Raw retained logical member differs: '+name)
  return raw
 def json(self,name):return json.loads(self.read(name))
 def materialize(self,name,out):
  # Only an explicitly selected regular file is written. Links stay identities.
  safe_name(name);out=Path(out).resolve();out.mkdir(parents=True,exist_ok=False);target=out/PurePosixPath(name);target.parent.mkdir(parents=True,exist_ok=True)
  if not target.resolve().is_relative_to(out):raise ValueError('Selected evidence file escapes its NEW output')
  target.write_bytes(self.read(name));return target
