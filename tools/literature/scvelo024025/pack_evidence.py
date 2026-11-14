#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain complete available development objects without reencoding scientific bytes."""
from pathlib import Path
import argparse,hashlib,json,os,re,stat,sys,time,traceback,zipfile
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2];BASE=ROOT/'build/literature-review/monocle-runtime'
from EvidenceStore import EvidenceStore,pin,decode

def encoded(value):return (json.dumps(value,indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf8')

def pack(out):
 if sys.platform!='linux':raise OSError('Linux lstat/readlink is required to preserve WSL links and native temporary identities')
 out=Path(out).resolve()
 if out.parent!=BASE or not re.fullmatch(r'scvelo-evidence-pack-[1-9][0-9]*',out.name):raise ValueError('Use a NEW direct scvelo-evidence-pack-N output beneath monocle-runtime')
 out.mkdir(exist_ok=False);started=time.monotonic()
 files={};links={};directories={};special={};objects={};sources={};omissions=[]
 number=int(out.name.rsplit('-',1)[1])
 def retained_scope(path):
  if path==out:return False
  if path.name.startswith('scvelo-'):return True
  match=re.fullmatch(r'evidence-scvelo-pack-controller-([1-9][0-9]*)',path.name)
  if match:return int(match.group(1))<number
  return path.name.startswith('evidence-scvelo-verifier-preflight-') and any((path/name).is_file() for name in ['COMPLETED.json','FAILURE.json'])
 scopes=sorted(p for p in BASE.iterdir() if retained_scope(p))
 if not scopes:raise ValueError('Complete original scVelo development evidence is unavailable')
 def record(path,name):
  info=path.lstat();mode=stat.S_IMODE(info.st_mode)
  if name=='scvelo-source-intake-1/PRIMARY_EXCERPTS.json':
   omissions.append(dict(path=name,**pin(path),reason='Publisher primary full-text paragraphs remain in the local licensed cache. The distributable evidence retains all250 XML identities, exact paragraph hashes, independently written source-role summaries and primary links.'));return
  if stat.S_ISLNK(info.st_mode):links[name]=dict(target=os.readlink(path),mode=mode);return
  if stat.S_ISDIR(info.st_mode):
   directories[name]=dict(mode=mode)
   for child in sorted(path.iterdir()):record(child,name+'/'+child.name)
  elif stat.S_ISREG(info.st_mode):
   identity=pin(path);files[name]=dict(mode=mode,**identity);sources[name]=path;objects.setdefault(identity['sha256'],path)
  else:special[name]=dict(mode=mode,file_type=stat.S_IFMT(info.st_mode),device=info.st_rdev,inode=info.st_ino,available_process_payload_preserved=False,scope='Original special-node identity only; never opened or followed.')
 for i,scope in enumerate(scopes):
  record(scope,scope.name)
  print('INVENTORIED_SCOPE',i+1,'of',len(scopes),scope.name,'regular_files='+str(len(files)),flush=True)
 native={}
 for name in list(files):
  if not name.endswith('/native-temporary-inventory.json'):continue
  doc=decode(sources[name].read_bytes());root=doc['original_root']
  analysis_root=bool(re.fullmatch(r'/tmp/omicsmatic-scvelo-[a-z0-9_]{8}',root))
  unit_fixture=name=='scvelo-native-temporary-controls-1/workspace/native-temporary-inventory.json' and bool(re.fullmatch(r'/tmp/omicsmatic-ipc-control-[a-z0-9_]{8}',root))
  if not (analysis_root or unit_fixture):raise ValueError('An original temporary receipt names an unexpected ownership boundary: '+name)
  native.setdefault(root,[]).append(name)
 native_roots=[]
 for original,receipts in sorted(native.items()):
  path=Path(original);prefix='native-originals/'+path.name;present=path.exists()
  row=dict(original_root=original,receipt_files=receipts,logical_prefix=prefix,available_at_pack=present,scope='Referenced original analysis temporary root; preserve all available bytes and node identities.' if original.startswith('/tmp/omicsmatic-scvelo-') else 'The separately source-bound native-IPC unit fixture uses TemporaryDirectory cleanup. Its copied regular bytes and node-identity receipt remain retained; a no-longer-live unit fixture is not relabeled a retained analysis root.')
  if present:
   if not stat.S_ISDIR(path.lstat().st_mode):raise ValueError('Owned original native temporary root changed file type')
   record(path,prefix)
  native_roots.append(row)
 routing={};archives={};archive=None;archive_name=None;limit=40*1024*1024;part=0
 try:
  for i,(digest,path) in enumerate(sorted(objects.items())):
   size=path.stat().st_size
   if archive is None or (archive.fp.tell()+size+size//1000+4096+len(archive.filelist)*160>limit and archive.filelist):
    if archive is not None:
     archive.close();archives[archive_name]=pin(out/archive_name)
     if archives[archive_name]['bytes']>limit:raise ValueError('A bounded archive exceeds40MiB; keep the partial attempt and use explicit lossless chunk storage')
    part+=1;archive_name='objects-'+str(part).zfill(4)+'.zip';archive=zipfile.ZipFile(out/archive_name,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9,allowZip64=True)
   info=zipfile.ZipInfo('objects/'+digest,(2026,11,14,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;info.create_system=3
   with archive.open(info,'w',force_zip64=True) as target,path.open('rb') as source:
    for chunk in iter(lambda:source.read(1048576),b''):target.write(chunk)
   routing[digest]=archive_name
   if i%500==0:print('PACKED_ORIGINAL_OBJECTS',i+1,'of',len(objects),'parts='+str(part),flush=True)
 finally:
  if archive is not None:archive.close()
 if archive_name is not None:archives[archive_name]=pin(out/archive_name)
 if any(v['bytes']>limit for v in archives.values()):raise ValueError('A bounded archive exceeds40MiB; no final store admitted')
 index=dict(schema_version=2,storage='sha256-objects-in-complete-bounded-zips',scopes=[p.name for p in scopes],files=files,links=links,directories=directories,special_nodes=special,object_archives=routing,native_original_roots=native_roots,omissions=omissions,original_bytes_reencoded=False,link_targets_dereferenced=False,scientific_sources_modified=False,irrecoverable_historical_files=4,irrecoverable_scope='Four earlier0.2.4 SVGs were overwritten before retention; the original trial remains refused. No bytes are reconstructed or claimed recovered.',whole_goal_complete=False)
 (out/'INDEX.json').write_bytes(encoded(index))
 with EvidenceStore(out,index) as store:result=store.verify(archives)
 for i,(name,wanted) in enumerate(files.items()):
  path=sources[name];info=path.lstat()
  if not stat.S_ISREG(info.st_mode) or pin(path)!={k:wanted[k] for k in ['bytes','sha256']} or stat.S_IMODE(info.st_mode)!=wanted['mode']:raise ValueError('A complete original source identity changed during packaging: '+name)
  if i%5000==0:print('RECHECKED_ORIGINAL_FILES',i+1,'of',len(files),flush=True)
 for name,wanted in links.items():
  if name.startswith('native-originals/'):
   prefix,relative=name[len('native-originals/'):].split('/',1);path=Path('/tmp')/prefix/relative
  else:path=BASE/name
  if not path.is_symlink() or os.readlink(path)!=wanted['target']:raise ValueError('Original retained link identity changed during packaging')
 result=dict(status='PASS_COMPLETE_AVAILABLE_SCVELO_LOSSLESS_ORIGINAL_EVIDENCE_STORE',**result,archive_files=archives,index=pin(out/'INDEX.json'),elapsed_seconds=time.monotonic()-started,omissions=omissions,irrecoverable_historical_SVGs=4,available_failed_analysis_objects_retained=True,scientific_interpreter_runtime_binaries_redistributed=False,original_uv_bootstrap_notices_retained=True,full_text_cache_retained_locally=True,new_tool_credit=0,whole_goal_complete=False)
 (out/'COMPLETED.json').write_bytes(encoded(result));print(result['status'],result['logical_files'],'logical files',len(archives),'bounded archives',flush=True);return result

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();existed=a.out.exists();eligible=a.out.absolute().parent==BASE and re.fullmatch(r'scvelo-evidence-pack-[1-9][0-9]*',a.out.name)
 try:pack(a.out);return 0
 except BaseException as error:
  result=dict(status='FAIL2_SCVELO_EVIDENCE_PACK_UNADMITTED',cause=repr(error),traceback=traceback.format_exc(),required_action='Preserve every partial archive, original object, source, warning/error and failed attempt. Resolve the actual issue and retry a NEW output. Never rewrite saved hashes, drop failed scientific cases, follow links or claim overwritten historical SVG bytes were recovered.',new_tool_credit=0,whole_goal_complete=False)
  if eligible and not existed and not isinstance(error,FileExistsError) and a.out.is_dir() and not (a.out/'FAILURE.json').exists():(a.out/'FAILURE.json').write_bytes(encoded(result))
  print(result['status'],result['cause'],flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
