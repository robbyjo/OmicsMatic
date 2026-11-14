#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Require the entire captured selected original runtime and source population before/after execution."""
from pathlib import Path
import json,os,stat
from runtime_audit import archive_members,capture,pin,profiles,semaphore_data_mapping
HERE=Path(__file__).resolve().parent
class Unavailable(OSError):pass
def regular(p):
 if not p.is_file() or p.is_symlink():raise Unavailable('Exact regular original prerequisite unavailable: '+str(p))

def verify_provider(directory):
 ready_file=directory/'READY.json';regular(ready_file);ready=json.loads(ready_file.read_bytes())
 if ready.get('status')!='READY_CAPTURED_PRIVATE_ORIGINAL_SCVELO_CPU_NOT_ANALYSIS':raise ValueError('Exact original provider identity differs')
 original,selected=profiles(ready['version']);prefix=Path(ready['prefix']);python=Path(ready['python'])
 if ready['venv']!=selected['venv'] or prefix!=prefix.resolve() or python!=prefix/selected['venv']/'bin/python' or not python.is_file() or not os.access(python,os.X_OK):raise Unavailable('Exact permitted selected private interpreter unavailable')
 if ready['source_profile']!=pin(HERE/'source-profile.json') or ready['runtime_profile']!=pin(HERE/'runtime-profile.json'):raise ValueError('Exact original source/runtime profiles differ')
 protected={ready_file:pin(ready_file)}
 for n,wanted in ready['source_pins'].items():
  path=HERE/n;regular(path)
  if pin(path)!=wanted:raise ValueError('Captured original runtime helper/profile differs: '+n)
  protected[path]=wanted
 for key in ['installed_distribution_records','import_visible_distribution_records','imported_module_origins']:
  if ready[key]!=selected[key]:raise ValueError('Complete original installed/import-visible identity differs: '+key)
 if ready['original_package_files']!=original['original_package_files']:raise ValueError('Complete original package namespace identity differs')
 roots=['python',selected['venv']]
 if set(ready['runtime'])!=set(roots):raise ValueError('Complete selected runtime roots differ')
 for n in roots:
  root=prefix/n
  if not root.is_dir():raise Unavailable('Complete captured original runtime root unavailable: '+n)
  current=capture(root,prefix,roots)
  if current!=ready['runtime'][n]:raise ValueError('Complete selected runtime member/link/directory population or bytes differ: '+n)
  for relative,row in current.items():
   if row['kind']=='file':protected[root/relative]={k:row[k] for k in ['bytes','sha256']}
 for n,wanted in ready['loaded_files'].items():
  path=Path(n);regular(path)
  if pin(path)!=wanted:raise ValueError('Original loaded runtime/system library differs: '+n)
  protected[path]=wanted
 maps=directory/'PROCESS_MAPS.txt';regular(maps);transient=[semaphore_data_mapping(line,os.sysconf('SC_PAGESIZE')) for line in maps.read_text().splitlines() if line.endswith(' (deleted)')]
 if any(row is None for row in transient) or len(transient)!=len(ready['transient_data_mappings']):raise ValueError('Unrecognized/missing deleted mapping in retained original capture')
 expected_artifacts={'PROCESS_MAPS.txt'}
 for i,(actual,row) in enumerate(zip(transient,ready['transient_data_mappings'])):
  if any(row.get(k)!=v for k,v in actual.items()):raise ValueError('Original transient process-state mapping classification differs')
  name='transient-data/'+str(i).zfill(6)+'.bin'
  if row['file']!=name or row['bytes']!=os.sysconf('SC_PAGESIZE'):raise ValueError('Complete transient synchronization data identity differs')
  expected_artifacts.add(name)
  if ready['capture_artifacts'].get(name)!={k:row[k] for k in ['bytes','sha256']}:raise ValueError('Transient capture bytes differ')
 if set(ready['capture_artifacts'])!=expected_artifacts or {p.name for p in (directory/'transient-data').iterdir()}!={Path(n).name for n in expected_artifacts if n!='PROCESS_MAPS.txt'}:raise ValueError('Complete original process-state artifact population differs')
 for n,wanted in ready['capture_artifacts'].items():
  path=directory/n;regular(path)
  if pin(path)!=wanted:raise ValueError('Original process capture artifact differs: '+n)
  protected[path]=wanted
 wanted_archives={row['filename']:{k:row[k] for k in ['bytes','sha256']} for row in original['source_downloads']}
 if ready['distribution_archives']!=wanted_archives:raise ValueError('Complete original distribution archive identities differ')
 for n,wanted in wanted_archives.items():
  path=directory/n;regular(path)
  if pin(path)!=wanted:raise ValueError('Complete original distribution archive differs: '+n)
  protected[path]=wanted
 archive=directory/next(row['filename'] for row in original['source_downloads'] if row['packagetype']=='sdist')
 members=archive_members(archive)
 if members!=ready['source_members']:raise ValueError('Complete original archive source inventory differs')
 if set(ready['materialized_source_modes'])!=set(members) or ready['materialization_mode_differences']!=[dict(file=name,archive_mode=row['mode'],materialized_host_mode=ready['materialized_source_modes'][name]) for name,row in members.items() if row['mode']!=ready['materialized_source_modes'][name]]:raise ValueError('Original archive versus host materialization-mode distinction differs')
 source=Path(ready['source_root'])
 if source!=directory/'source':raise ValueError('Captured original source root differs')
 files={p.relative_to(source).as_posix():p for p in source.rglob('*') if p.is_file() or p.is_symlink()}
 if set(files)!=set(members):raise ValueError('Complete original extracted source population differs')
 for name,wanted in members.items():
  path=files[name];regular(path)
  if pin(path)!={k:wanted[k] for k in ['bytes','sha256']} or stat.S_IMODE(path.stat().st_mode)!=ready['materialized_source_modes'][name]:raise ValueError('Original extracted source bytes/recorded host mode differ: '+name)
  protected[path]={k:wanted[k] for k in ['bytes','sha256']}
 return ready,protected
