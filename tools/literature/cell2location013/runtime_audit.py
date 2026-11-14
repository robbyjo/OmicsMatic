#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture exact original source, distribution, runtime-file and loaded-system-library identities."""
from pathlib import Path,PurePosixPath
import argparse,ast,hashlib,importlib.metadata as md,json,os,shutil,stat,sys,tarfile,traceback,urllib.request
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent
ACTION='SKIP3 skips dependent analysis when the exact permitted Linux/WSL CPU Python3.9.25 runtime, pinned distributions and their original import behavior, original cell2location0.1.3 source or required execution/download is unavailable. Install only where permitted; retain setup errors, supply the named exact prerequisite and retry a NEW path. FAIL2 admits no provider/result after an integrity or completeness failure. Preserve complete original objects, warnings/errors, partial files and raw console. This audit installs nothing and runs no model. READY is not an analysis result, paper reproduction or qualification of the historical0.7a0 edition. Hashes establish integrity, not authenticity. Read the development guide.'

def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for b in iter(lambda:f.read(1048576),b''):h.update(b);n+=len(b)
 return dict(bytes=n,sha256=h.hexdigest())

def save(p,v):p.write_bytes((json.dumps(v,indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf8'))

def distribution_records(prefix):
 return sorted([dict(name=d.metadata['Name'],version=d.version,location=Path(d.locate_file('')).relative_to(prefix).as_posix()) for d in md.distributions()],key=lambda d:(d['name'].lower(),d['version'],d['location']))

def capture(root,prefix):
 records={}
 for folder,directories,files in os.walk(root,followlinks=False):
  for name in sorted(directories+files):
   p=Path(folder)/name;info=p.lstat();relative=p.relative_to(root).as_posix()
   if stat.S_ISLNK(info.st_mode):
    destination=p.resolve(strict=True)
    if not any(destination.is_relative_to(prefix/n) for n in ['python','venv']):raise ValueError('Runtime link escapes the complete captured interpreter/environment: '+str(p))
    records[relative]=dict(kind='symlink',target=os.readlink(p),resolved=destination.relative_to(prefix).as_posix(),resolved_kind='directory' if destination.is_dir() else 'file')
   elif stat.S_ISREG(info.st_mode):records[relative]=dict(kind='file',mode=stat.S_IMODE(info.st_mode),**pin(p))
   elif stat.S_ISDIR(info.st_mode):records[relative]=dict(kind='directory',mode=stat.S_IMODE(info.st_mode))
   else:raise ValueError('Nonregular runtime member: '+str(p))
 return records

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--prefix',type=Path,required=True);p.add_argument('--source-archive',type=Path);p.add_argument('--allow-download',action='store_true');p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.absolute();owned=False
 try:
  prefix=a.prefix.resolve();profile= json.loads((HERE/'source-profile.json').read_bytes())
  if out!=out.resolve():raise ValueError('Canonical NEW output required')
  protected=[prefix,HERE,a.source_archive.resolve() if a.source_archive else HERE]
  if any(out.is_relative_to(x) or x.is_relative_to(out) for x in protected):raise ValueError('Output overlaps protected runtime/source/archive')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if sys.platform!='linux':raise OSError('Use the pinned Linux/WSL CPU runtime; no native Windows analysis is admitted')
  if Path(sys.prefix)!=prefix/'venv' or not Path(sys.base_prefix).is_relative_to(prefix/'python'):raise OSError('Invoke with this private prefix venv/bin/python')
  if sys.version_info[:3]!=(3,9,25):raise OSError('Exact Python3.9.25 prerequisite unavailable')
  installed_records=distribution_records(prefix)
  if installed_records!=profile['installed_distribution_records']:raise OSError('Exact complete installed distribution population/versions/origins unavailable')
  if not a.source_archive and not a.allow_download:raise OSError('Supply the original local source archive or explicit --allow-download')
  archive=out/profile['archive_name']
  if a.source_archive:shutil.copyfile(a.source_archive,archive)
  else:
   with urllib.request.urlopen(urllib.request.Request(profile['url'],headers={'User-Agent':'OmicsMatic-original-cell2location-source-audit'}),timeout=90) as f:archive.write_bytes(f.read(profile['archive']['bytes']+1))
  if pin(archive)!=profile['archive']:raise ValueError('Exact complete original source archive differs')
  source=out/'source';source.mkdir()
  with tarfile.open(archive,'r:gz') as tar:
   members=[m for m in tar.getmembers() if not m.isdir()]
   if len(members)!=len({m.name for m in members}) or {m.name for m in members}!=set(profile['source_members']):raise ValueError('Original archive member population differs')
   for m in members:
    n=PurePosixPath(m.name)
    if not m.isfile() or n.is_absolute() or '..' in n.parts or '\\' in m.name or n.as_posix()!=m.name:raise ValueError('Unsafe original archive member')
    raw=tar.extractfile(m).read();target=source/m.name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
    if pin(target)!=profile['source_members'][m.name]:raise ValueError('Original source member differs: '+m.name)
  import cell2location,torch,scvi,pyro,cv2,plotnine,adjustText
  if cell2location.__version__!='0.1.3' or torch.__version__!='2.0.1+cpu' or torch.cuda.is_available():raise OSError('Exact original CPU execution prerequisite unavailable')
  visible=distribution_records(prefix)
  if visible!=profile['import_visible_distribution_records']:raise OSError('Original import-visible installed and vendored distribution records differ')
  module_origins={}
  for name,expected_origin in profile['imported_module_origins'].items():
   module=sys.modules.get(name)
   if module is None or not getattr(module,'__file__',None):raise OSError('Original imported module unavailable: '+name)
   actual_origin=dict(file=Path(module.__file__).resolve().relative_to(prefix).as_posix(),declared_version=getattr(module,'__version__',None))
   if actual_origin!=expected_origin:raise OSError('Actual original module origin/version differs: '+name)
   module_origins[name]=actual_origin
  installed=Path(cell2location.__file__).resolve().parent
  expected={str(PurePosixPath(n).relative_to('cell2location-0.1.3/cell2location')):wanted for n,wanted in profile['source_members'].items() if n.startswith('cell2location-0.1.3/cell2location/')}
  actual_sources={p.relative_to(installed).as_posix():pin(p) for p in installed.rglob('*') if p.is_file() and '__pycache__' not in p.parts}
  if actual_sources!=expected:raise ValueError('Installed complete original package source differs')
  declarations=[]
  for n in sorted(expected):
   if not n.endswith('.py'):continue
   for node in ast.walk(ast.parse((installed/n).read_text(encoding='utf8'))):
    if isinstance(node,(ast.FunctionDef,ast.AsyncFunctionDef,ast.ClassDef)):declarations.append(dict(file=n,line=node.lineno,kind=type(node).__name__,name=node.name))
  # Include every interpreter/environment member, not only selected module origins.
  runtime={n:capture(prefix/n,prefix) for n in ['python','venv']}
  libraries={}
  for line in Path('/proc/self/maps').read_text().splitlines():
   parts=line.split(None,5)
   if len(parts)!=6 or not parts[5].startswith('/'):continue
   name=parts[5]
   if name.endswith(' (deleted)'):raise OSError('A loaded runtime mapping was deleted; no complete provider admitted')
   q=Path(name).resolve(strict=True)
   if q.is_file():libraries[str(q)]=pin(q)
  sources={n:pin(HERE/n) for n in ['runtime_audit.py','source-profile.json','requirements-cpu.txt','LICENSE-Apache-2.0.txt']}
  result=dict(status='READY_CAPTURED_PRIVATE_ORIGINAL_CELL2LOCATION013_CPU_NOT_ANALYSIS',version='0.1.3',prefix=str(prefix),python=str(prefix/'venv/bin/python'),base_prefix=sys.base_prefix,python_version=sys.version,installed_distribution_records=installed_records,import_visible_distribution_records=visible,imported_module_origins=module_origins,source_profile=pin(HERE/'source-profile.json'),source_pins=sources,source_archive=profile['archive'],source_root=str(source),installed_package=str(installed),original_package_files=expected,complete_source_declarations=declarations,public_namespace=sorted(n for n in vars(cell2location) if not n.startswith('_')),runtime=runtime,loaded_files=libraries,original_algorithms_patched=False,analysis_executed=False,new_tool_credit=0,whole_goal_complete=False)
  save(out/'READY.json',result);(out/'WARNINGS.md').write_bytes((ACTION+'\n').encode('utf8'));print(result['status'],'source-files='+str(len(expected)),'runtime-members='+str(sum(map(len,runtime.values()))),flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) else 2;status='SKIP3_ORIGINAL_RUNTIME_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_RUNTIME_CAPTURE_UNADMITTED';failure=dict(status=status,cause=repr(error),traceback=traceback.format_exc(),required_action=ACTION,new_tool_credit=0,whole_goal_complete=False)
  if owned:
   try:save(out/'FAILURE.json',failure)
   except BaseException as recording:print('FAIL2: failure report unavailable; retain console',repr(recording),flush=True);code=2
   try:(out/'WARNINGS.md').write_bytes((status+'\n'+repr(error)+'\n'+ACTION+'\n').encode('utf8'))
   except BaseException as recording:print('FAIL2: warnings file unavailable; retain console',repr(recording),flush=True);code=2
  print(status,repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
