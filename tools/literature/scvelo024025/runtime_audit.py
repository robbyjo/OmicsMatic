#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture a complete selected original scVelo interpreter, environment, distributions and sources."""
from pathlib import Path,PurePosixPath
import argparse,ast,hashlib,importlib,importlib.metadata as md,json,os,platform,re,shutil,stat,sys,tarfile,traceback
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent
MODULES=['scvelo','scanpy','anndata','numpy','scipy','pandas','sklearn','matplotlib','numba','loompy','umap','igraph','networkx']
ACTION='SKIP3 skips dependent analysis when the exact selected original Linux/WSL CPU edition, private Python3.9.25, complete pinned dependencies or permitted execution is unavailable. Supply only permitted exact prerequisites and retry NEW paths. FAIL2 admits no provider/result after integrity or completeness failure. Preserve every original object, warning/error, raw report, partial file and full console. READY and structural completion do not establish convergence, statistical validity, author-environment identity or paper reproduction. The printed0.24 label is unresolved; do not normalize it to0.2.4. The R velociraptor wrapper is separate. This audit installs nothing and fits no model.'

def pin(p):
 h=hashlib.sha256();size=0
 with p.open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=h.hexdigest())

def save(p,v):p.write_bytes((json.dumps(v,indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf8'))

def profiles(version):
 original=json.loads((HERE/'source-profile.json').read_bytes());runtime=json.loads((HERE/'runtime-profile.json').read_bytes())
 found=[p for p in original['profiles'] if p['version']==version]
 if len(found)!=1 or version not in runtime['profiles']:raise OSError('Exact selected original edition unavailable; no silent version substitution: '+version)
 return found[0],runtime['profiles'][version]

def distribution_records(prefix):
 return sorted([dict(name=d.metadata['Name'],version=d.version,location=Path(d.locate_file('')).relative_to(prefix).as_posix()) for d in md.distributions()],key=lambda d:(d['name'].lower(),d['version'],d['location']))

def import_original(prefix,version,runtime):
 if sys.platform!='linux' or platform.machine()!='x86_64' or sys.version_info[:3]!=(3,9,25):raise OSError('Exact Linux/WSLx86_64 Python3.9.25 prerequisite unavailable')
 if Path(sys.prefix)!=prefix/runtime['venv'] or not Path(sys.base_prefix).is_relative_to(prefix/'python'):raise OSError('Invoke the exact selected private venv interpreter')
 before=distribution_records(prefix)
 if before!=runtime['installed_distribution_records']:raise OSError('Complete installed dependency records/versions/origins differ')
 origins={}
 for name in MODULES:
  module=importlib.import_module(name)
  origins[name]=dict(file=Path(module.__file__).resolve().relative_to(prefix).as_posix(),declared_version=getattr(module,'__version__',None))
 if sys.modules['scvelo'].__version__!=version:raise OSError('Original selected scVelo edition unavailable')
 if origins!=runtime['imported_module_origins']:raise OSError('Original imported module origins/versions differ')
 after=distribution_records(prefix)
 if after!=runtime['import_visible_distribution_records']:raise OSError('Complete import-visible installed/vendored dependency records differ')
 return before,after,origins

def capture(root,prefix,roots):
 records={}
 for folder,directories,files in os.walk(root,followlinks=False):
  for name in sorted(directories+files):
   p=Path(folder)/name;info=p.lstat();relative=p.relative_to(root).as_posix()
   if stat.S_ISLNK(info.st_mode):
    destination=p.resolve(strict=True)
    if not any(destination.is_relative_to(prefix/n) for n in roots):raise ValueError('Runtime link escapes the complete selected interpreter/environment: '+str(p))
    records[relative]=dict(kind='symlink',target=os.readlink(p),resolved=destination.relative_to(prefix).as_posix(),resolved_kind='directory' if destination.is_dir() else 'file')
   elif stat.S_ISREG(info.st_mode):records[relative]=dict(kind='file',mode=stat.S_IMODE(info.st_mode),**pin(p))
   elif stat.S_ISDIR(info.st_mode):records[relative]=dict(kind='directory',mode=stat.S_IMODE(info.st_mode))
   else:raise ValueError('Nonregular selected runtime member: '+str(p))
 return records

def archive_members(archive,destination=None):
 records={}
 with tarfile.open(archive,'r:gz') as tar:
  for item in tar:
   if item.isdir():continue
   name=PurePosixPath(item.name)
   if not item.isfile() or name.is_absolute() or '..' in name.parts or '\\' in item.name or name.as_posix()!=item.name or item.name in records:raise ValueError('Unsafe/duplicate original archive member')
   raw=tar.extractfile(item).read();records[item.name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest(),mode=stat.S_IMODE(item.mode))
   if destination is not None:
    target=destination/item.name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw);target.chmod(records[item.name]['mode'])
 return records

def semaphore_data_mapping(line,page_size):
 """Recognize only glibc's unlinked, shared, non-executable sem.XXXXXX data page.

 See glibc-2.36/sysdeps/pthread/sem_open.c: a temporary sem_t backing file
 is mapped PROT_READ|PROT_WRITE, MAP_SHARED and the temporary name unlinked.
 This is sampled process synchronization state, not immutable library code.
 """
 parts=line.split(None,5)
 if len(parts)!=6 or not re.fullmatch(r'/dev/shm/sem\.[A-Za-z0-9]{6} \(deleted\)',parts[5]):return None
 if parts[1]!='rw-s' or int(parts[2],16)!=0:return None
 begin,end=[int(v,16) for v in parts[0].split('-')]
 if end-begin!=page_size or begin%page_size or int(parts[4])<=0:return None
 return dict(classification='unlinked-nonexecutable-shared-POSIX-semaphore-data',address_start=begin,address_end=end,permissions=parts[1],offset=parts[2],device=parts[3],inode=parts[4],path=parts[5],process_map_line=line)

def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--prefix',type=Path,required=True);parser.add_argument('--version',required=True);parser.add_argument('--source-archive',type=Path,required=True);parser.add_argument('--wheel',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();out=a.out.absolute();owned=False
 try:
  prefix=a.prefix.absolute()
  if prefix!=prefix.resolve() or out!=out.resolve():raise ValueError('Canonical NEW prefix/output paths required')
  for protected in [prefix,HERE,a.source_archive.resolve(),a.wheel.resolve()]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected runtime/source/distribution')
  out.mkdir(parents=True,exist_ok=False);owned=True
  original,selected=profiles(a.version);before,after,origins=import_original(prefix,a.version,selected)
  archives={}
  for kind,provided in [('sdist',a.source_archive),('bdist_wheel',a.wheel)]:
   expected=[f for f in original['source_downloads'] if f['packagetype']==kind]
   if len(expected)!=1:raise ValueError('Exact original distribution population differs')
   row=expected[0];target=out/row['filename'];shutil.copyfile(provided,target)
   if pin(target)!={k:row[k] for k in ['bytes','sha256']}:raise ValueError('Complete original distribution differs: '+row['filename'])
   archives[row['filename']]=pin(target)
  source=out/'source';source.mkdir();members=archive_members(out/next(f['filename'] for f in original['source_downloads'] if f['packagetype']=='sdist'),source)
  source_modes={name:stat.S_IMODE((source/name).stat().st_mode) for name in members};mode_differences=[dict(file=name,archive_mode=row['mode'],materialized_host_mode=source_modes[name]) for name,row in members.items() if row['mode']!=source_modes[name]]
  installed=Path(sys.modules['scvelo'].__file__).resolve().parent
  files={p.relative_to(installed.parent).as_posix():pin(p) for p in installed.rglob('*') if p.is_file() and '__pycache__' not in p.parts}
  if files!=original['original_package_files']:raise ValueError('Complete installed original scVelo namespace differs')
  declarations=[]
  for name in sorted(files):
   if name.endswith('.py'):
    for node in ast.walk(ast.parse((installed.parent/name).read_bytes())):
     if isinstance(node,(ast.FunctionDef,ast.AsyncFunctionDef,ast.ClassDef)):declarations.append(dict(module_file=name,kind=type(node).__name__,name=node.name,line=node.lineno))
  order=lambda d:(d['module_file'],d['line'],d['kind'],d['name'])
  if sorted(declarations,key=order)!=sorted(original['original_declarations'],key=order):raise ValueError('Complete original declaration population differs')
  roots=['python',selected['venv']];runtime={n:capture(prefix/n,prefix,roots) for n in roots};loaded={};transient=[];artifacts={}
  maps=out/'PROCESS_MAPS.txt';maps.write_bytes(Path('/proc/self/maps').read_bytes());artifacts[maps.name]=pin(maps);(out/'transient-data').mkdir()
  for line in maps.read_text().splitlines():
   parts=line.split(None,5)
   if len(parts)!=6 or not parts[5].startswith('/'):continue
   name=parts[5]
   if name.endswith(' (deleted)'):
    row=semaphore_data_mapping(line,os.sysconf('SC_PAGESIZE'))
    if row is None:raise OSError('Unrecognized deleted runtime mapping; no provider admitted: '+line)
    relative='transient-data/'+str(len(transient)).zfill(6)+'.bin';target=out/relative
    with Path('/proc/self/mem').open('rb',buffering=0) as memory:
     memory.seek(row['address_start']);raw=memory.read(row['address_end']-row['address_start'])
    if len(raw)!=os.sysconf('SC_PAGESIZE'):raise OSError('Complete transient semaphore data sample unavailable')
    target.write_bytes(raw);artifacts[relative]=pin(target);transient.append(dict(**row,file=relative,**pin(target)));continue
   q=Path(name).resolve(strict=True)
   if q.is_file():loaded[str(q)]=pin(q)
  source_names=['runtime_audit.py','source-profile.json','runtime-profile.json','requirements-cpu024.txt','requirements-cpu025.txt','LICENSE-BSD-3-Clause.txt']
  ready=dict(status='READY_CAPTURED_PRIVATE_ORIGINAL_SCVELO_CPU_NOT_ANALYSIS',version=a.version,prefix=str(prefix),venv=selected['venv'],python=str(prefix/selected['venv']/'bin/python'),python_version=sys.version,base_prefix=sys.base_prefix,installed_distribution_records=before,import_visible_distribution_records=after,imported_module_origins=origins,source_profile=pin(HERE/'source-profile.json'),runtime_profile=pin(HERE/'runtime-profile.json'),source_pins={n:pin(HERE/n) for n in source_names},distribution_archives=archives,source_root=str(source),source_members=members,materialized_source_modes=source_modes,materialization_mode_differences=mode_differences,materialization_scope='Original archive bytes and modes remain intact in the saved source distribution. Extracted audit-copy permissions are recorded as observed host filesystem modes; analysis uses the separately captured native-Linux installed package and runtime modes.',installed_package=str(installed),original_package_files=files,complete_source_declarations=declarations,public_namespace=sorted(n for n in vars(sys.modules['scvelo']) if not n.startswith('_')),runtime=runtime,loaded_files=loaded,capture_artifacts=artifacts,transient_data_mappings=transient,transient_scope='Only exact non-executable shared one-page sem.XXXXXX mappings are sampled as process synchronization state. Original raw maps/data are retained; addresses and semaphore values are not cross-process code identity. Every other deleted mapping refuses readiness.',original_algorithms_patched=False,analysis_executed=False,new_tool_credit=0,whole_goal_complete=False)
  note='\nHost permission differences in extracted audit copies: '+str(len(mode_differences))+'. Original archive bytes/modes remain retained. The separately captured installed runtime uses native Linux permissions.\nNon-executable transient semaphore samples are process state, not identical cross-process code. All other deleted mappings refuse readiness.\n'
  save(out/'READY.json',ready);(out/'WARNINGS.md').write_text(ACTION+'\n'+note,encoding='utf8');print(ready['status'],a.version,'source-members='+str(len(members)),'runtime-members='+str(sum(map(len,runtime.values()))),flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;status='SKIP3_ORIGINAL_SCVELO_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_SCVELO_CAPTURE_UNADMITTED'
  record=dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,new_tool_credit=0,analysis_executed=False)
  if owned:
   try:save(out/'FAILURE.json',record);(out/'WARNINGS.md').write_text(status+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8')
   except BaseException as recording:print('FAIL2: could not retain complete failure record',repr(recording),'\n'+record['traceback'],flush=True);code=2
  print(status,repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
