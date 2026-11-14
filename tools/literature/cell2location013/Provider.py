#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the entire captured private interpreter/environment and original source before analysis."""
from pathlib import Path
import json,os
from runtime_audit import capture,pin
HERE=Path(__file__).resolve().parent
class Unavailable(OSError):pass

def regular(p):
 if not p.is_file() or p.is_symlink():raise Unavailable('Exact regular prerequisite unavailable: '+str(p))

def verify_provider(directory):
 ready_file=directory/'READY.json';regular(ready_file);ready=json.loads(ready_file.read_bytes());profile_file=HERE/'source-profile.json';regular(profile_file);profile=json.loads(profile_file.read_bytes())
 if ready.get('status')!='READY_CAPTURED_PRIVATE_ORIGINAL_CELL2LOCATION013_CPU_NOT_ANALYSIS' or ready.get('version')!='0.1.3' or ready.get('source_profile')!=pin(profile_file):raise ValueError('Exact original provider/source identity differs')
 protected={ready_file:pin(ready_file),profile_file:pin(profile_file)}
 for n,wanted in ready['source_pins'].items():
  p=HERE/n;regular(p)
  if pin(p)!=wanted:raise ValueError('Captured runtime helper/profile differs: '+n)
  protected[p]=wanted
 if ready['installed_distribution_records']!=profile['installed_distribution_records'] or ready['import_visible_distribution_records']!=profile['import_visible_distribution_records'] or ready['imported_module_origins']!=profile['imported_module_origins']:raise ValueError('Complete installed/import-visible original dependency identities differ')
 prefix=Path(ready['prefix']);python=Path(ready['python'])
 if prefix!=prefix.resolve() or python!=prefix/'venv/bin/python' or not python.is_file() or not os.access(python,os.X_OK):raise Unavailable('Exact permitted private Python interpreter unavailable')
 for n in ['python','venv']:
  root=prefix/n
  if not root.is_dir():raise Unavailable('Complete captured runtime root unavailable: '+str(root))
  actual=capture(root,prefix)
  if actual!=ready['runtime'][n]:raise ValueError('Complete captured runtime file/link/directory population or bytes differ: '+n)
  for name,entry in actual.items():
   if entry['kind']=='file':protected[root/name]={k:entry[k] for k in ['bytes','sha256']}
 for n,wanted in ready['loaded_files'].items():
  p=Path(n);regular(p)
  if pin(p)!=wanted:raise ValueError('Original loaded runtime/system file differs: '+n)
  protected[p]=wanted
 archive=directory/profile['archive_name'];regular(archive)
 if pin(archive)!=profile['archive']:raise ValueError('Complete original source archive differs')
 protected[archive]=profile['archive']
 source=Path(ready['source_root'])
 if source!=directory/'source':raise ValueError('Original captured source root differs')
 actual={p.relative_to(source).as_posix():p for p in source.rglob('*') if p.is_file() or p.is_symlink()}
 if set(actual)!=set(profile['source_members']):raise ValueError('Complete55-member original source population differs')
 for n,wanted in profile['source_members'].items():
  regular(actual[n])
  if pin(actual[n])!=wanted:raise ValueError('Original source member differs: '+n)
  protected[actual[n]]=wanted
 return ready,protected
