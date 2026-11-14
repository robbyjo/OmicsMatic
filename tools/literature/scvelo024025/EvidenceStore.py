#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read lossless original evidence across bounded ZIPs; never follow retained links."""
from pathlib import Path,PurePosixPath
import hashlib,json,re,zipfile

def pin(path):
 h=hashlib.sha256();size=0
 with path.open('rb') as f:
  for block in iter(lambda:f.read(1048576),b''):h.update(block);size+=len(block)
 return dict(bytes=size,sha256=h.hexdigest())

def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())

def safe_name(name):
 if not isinstance(name,str) or '\\' in name or '\0' in name:raise ValueError('Unsafe retained logical path')
 p=PurePosixPath(name)
 if p.is_absolute() or p.as_posix()!=name or any(n in ['', '.', '..'] for n in name.split('/')):raise ValueError('Unsafe retained logical path')
 return name

def pairs(values):
 result={}
 for key,value in values:
  if key in result:raise ValueError('Duplicate retained JSON key: '+key)
  result[key]=value
 return result

def decode(raw):return json.loads(raw,object_pairs_hook=pairs,parse_constant=lambda token:(_ for _ in ()).throw(ValueError('Nonfinite JSON token: '+token)))

class EvidenceStore:
 def __init__(self,directory,index):
  self.directory=Path(directory);self.index=index;self.open_archives={}
  if index.get('schema_version')!=2 or index.get('storage')!='sha256-objects-in-complete-bounded-zips':raise ValueError('Exact lossless multipart evidence schema required')
  names=[]
  for kind in ['files','links','directories','special_nodes']:
   if not isinstance(index.get(kind),dict):raise ValueError('Complete original file-type identity maps required')
   for name in index[kind]:safe_name(name)
   names.extend(index[kind])
  if len(names)!=len(set(names)):raise ValueError('A logical identity has conflicting file types')
  self.members=sorted(names);self.blobs={}
  for row in index['files'].values():
   if type(row['bytes'])!=int or row['bytes']<0 or not re.fullmatch('[a-f0-9]{64}',row['sha256']):raise ValueError('Exact complete retained size/hash required')
   identity={k:row[k] for k in ['bytes','sha256']}
   if self.blobs.setdefault(row['sha256'],identity)!=identity:raise ValueError('Inconsistent content-addressed original identity')
  if set(index['object_archives'])!=set(self.blobs):raise ValueError('Incomplete or extra retained object routing')
  for name in index['object_archives'].values():
   if not re.fullmatch(r'objects-[0-9]{4}\.zip',name):raise ValueError('Unexpected evidence archive path')

 def archive(self,name):
  if name not in self.open_archives:self.open_archives[name]=zipfile.ZipFile(self.directory/name)
  return self.open_archives[name]

 def verify(self,expected):
  wanted_archives=set(self.index['object_archives'].values())
  if set(expected)!=wanted_archives or {p.name for p in self.directory.glob('objects-*.zip')}!=wanted_archives:raise ValueError('Complete archive population differs')
  seen=set()
  for name in sorted(wanted_archives):
   path=self.directory/name
   if path.is_symlink() or pin(path)!=expected[name]:raise ValueError('Exact retained evidence archive differs: '+name)
   z=self.archive(name);members=z.namelist();wanted={'objects/'+digest for digest,archive in self.index['object_archives'].items() if archive==name}
   if len(members)!=len(set(members)) or set(members)!=wanted:raise ValueError('Complete unique retained archive member population differs')
   for member in members:
    digest=member[len('objects/'):];h=hashlib.sha256();size=0
    with z.open(member) as f:
     for block in iter(lambda:f.read(1048576),b''):h.update(block);size+=len(block)
    if dict(bytes=size,sha256=h.hexdigest())!=self.blobs[digest]:raise ValueError('Original evidence object bytes differ: '+digest)
    seen.add(digest)
  if seen!=set(self.blobs):raise ValueError('A complete original object was omitted')
  return dict(logical_files=len(self.index['files']),unique_objects=len(self.blobs),retained_links=len(self.index['links']),retained_directories=len(self.index['directories']),retained_special_nodes=len(self.index['special_nodes']),logical_bytes=sum(x['bytes'] for x in self.index['files'].values()),unique_bytes=sum(x['bytes'] for x in self.blobs.values()),archives=len(wanted_archives))

 def read(self,name):
  safe_name(name);row=self.index['files'][name];z=self.archive(self.index['object_archives'][row['sha256']]);raw=z.read('objects/'+row['sha256'])
  if rawpin(raw)!={k:row[k] for k in ['bytes','sha256']}:raise ValueError('Retained original logical member differs: '+name)
  return raw

 def json(self,name):return decode(self.read(name))
 def path(self,name):return FrozenPath(self,safe_name(name))
 def close(self):
  for z in self.open_archives.values():z.close()
  self.open_archives.clear()
 def __enter__(self):return self
 def __exit__(self,*args):self.close()

 def materialize(self,name,out):
  safe_name(name);out=Path(out).resolve();relative=Path(*PurePosixPath(name).parts)
  if relative.drive or relative.is_absolute():raise ValueError('Selected evidence path is not safe on this platform')
  out.mkdir(parents=True,exist_ok=False);target=out/relative
  if not target.resolve().is_relative_to(out):raise ValueError('Selected evidence file escapes its NEW output')
  target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(self.read(name));return target

class FrozenPath:
 """Explicit read-only archive view for the unchanged complete scientific comparator."""
 def __init__(self,store,name):self.store=store;self.name=name
 def __truediv__(self,name):return FrozenPath(self.store,safe_name(self.name+'/'+str(name)))
 def __str__(self):return self.name
 @property
 def parent(self):return FrozenPath(self.store,str(PurePosixPath(self.name).parent))
 def as_posix(self):return self.name
 def relative_to(self,other):
  if not isinstance(other,FrozenPath) or other.store is not self.store:raise ValueError('Different frozen evidence stores')
  return PurePosixPath(self.name).relative_to(PurePosixPath(other.name))
 def is_symlink(self):return self.name in self.store.index['links']
 def is_file(self):return self.name in self.store.index['files']
 def is_dir(self):return self.name in self.store.index['directories']
 def read_bytes(self):return self.store.read(self.name)
 def read_text(self,encoding='utf8',errors='strict'):return self.read_bytes().decode(encoding,errors)
 def open(self,mode='r'):
  if mode!='rb' or not self.is_file():raise ValueError('Only retained regular-file binary reads are permitted')
  row=self.store.index['files'][self.name];z=self.store.archive(self.store.index['object_archives'][row['sha256']]);return z.open('objects/'+row['sha256'])
 def rglob(self,pattern):
  if pattern!='*':raise ValueError('Only complete frozen populations may be enumerated')
  prefix=self.name+'/'
  return (FrozenPath(self.store,n) for n in self.store.members if n.startswith(prefix))
