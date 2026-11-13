#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise actual setup skips, corrupt-cache refusal and safe archive-boundary failures."""
from pathlib import Path
import argparse,hashlib,io,json,subprocess,sys,tarfile,traceback,zipfile
from provider import save,pin,extract
HERE=Path(__file__).resolve().parent
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False;calls=[];boundaries=[]
 try:
  if out.is_relative_to(HERE) or HERE.is_relative_to(out):raise ValueError('Test output overlaps provider source')
  out.mkdir(parents=True,exist_ok=False);owned=True
  before={n:pin(HERE/n) for n in ['provider.py','source-profile.json','test_setup.py']};(out/'source-snapshot').mkdir()
  for n in before:(out/'source-snapshot'/n).write_bytes((HERE/n).read_bytes())
  def run(label,flags,expected,target=None):
   target=target or out/label;command=list(map(str,[sys.executable,HERE/'provider.py',*flags,'--out',target]));r=subprocess.run(command,stdin=subprocess.DEVNULL,capture_output=True,timeout=120)
   (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);row=dict(label=label,command=command,exit_code=r.returncode,expected_exit=expected);calls.append(row);save(out/(label+'.process.json'),row)
   if r.returncode!=expected:raise ValueError('Unexpected setup control exit at '+label)
   if target.is_relative_to(out) and target.exists() and target!=out/'existing':assert not (target/'READY.json').exists()
   return target
  run('install-permission-skip',[],3);run('missing-source-skip',['--allow-install'],3)
  profile=json.loads((HERE/'source-profile.json').read_bytes());platform='windows-x86_64' if sys.platform=='win32' else 'linux-x86_64'
  cache=out/'corrupt-cache';cache.mkdir();(cache/profile['platforms'][platform]['archive']['name']).write_bytes(b'Explicitly corrupt source; no installation allowed from these bytes.\n')
  dest=run('corrupt-archive-refusal',['--allow-install','--source-cache',cache],2);assert not (dest/'runtime').exists()
  existing=out/'existing';existing.mkdir();(existing/'keep.txt').write_bytes(b'Original existing output must remain intact\n');old=pin(existing/'keep.txt')
  run('existing-output-skip',['--allow-install'],3,existing);assert pin(existing/'keep.txt')==old and {p.name for p in existing.iterdir()}=={'keep.txt'}
  target=HERE/'refused-test-provider-child';run('protected-source-refusal',['--allow-install'],2,target);assert not target.exists()
  run('protected-cache-refusal',['--allow-install','--source-cache',cache],2,cache/'refused-child');assert not (cache/'refused-child').exists()
  raw=b'Exact synthetic archive member\n';wanted=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
  fixtures=out/'archive-fixtures';fixtures.mkdir()
  tests=[('zip-traversal','../escaped.txt','file'),('zip-unexpected','unexpected.txt','file'),('zip-link','member.txt','symlink')]
  for label,name,kind in tests:
   source=fixtures/(label+'.zip')
   with zipfile.ZipFile(source,'x') as z:
    info=zipfile.ZipInfo(name);info.external_attr=(0o120777 if kind=='symlink' else 0o100644)<<16;z.writestr(info,raw)
   dest=out/(label+'-extract')
   try:extract(source,dest,{'member.txt':wanted})
   except ValueError:boundaries.append(label)
   else:raise ValueError('Unsafe ZIP fixture was admitted: '+label)
  source=fixtures/'tar-device.tar'
  with tarfile.open(source,'x') as t:
   info=tarfile.TarInfo('member.txt');info.type=tarfile.FIFOTYPE;t.addfile(info)
  try:extract(source,out/'tar-device-extract',{'member.txt':wanted})
  except ValueError:boundaries.append('tar-device')
  else:raise ValueError('Unsafe TAR device was admitted')
  source=fixtures/'incomplete.zip'
  with zipfile.ZipFile(source,'x') as z:z.writestr('member.txt',raw)
  try:extract(source,out/'incomplete-extract',{'member.txt':wanted,'missing.txt':wanted})
  except ValueError:boundaries.append('incomplete-population')
  else:raise ValueError('Incomplete archive population was admitted')
  assert not (out/'escaped.txt').exists() and all(pin(HERE/n)==v for n,v in before.items())
  result=dict(status='PASS_QUPATH051_SETUP_POLICY_CACHE_AND_ARCHIVE_BOUNDARIES',public_setup_controls=len(calls),archive_boundary_controls=len(boundaries),calls=calls,boundaries=boundaries,
   original_installs_performed_by_this_suite=0,original_analysis_executed=False,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);print(result['status'],flush=True);return 0
 except Exception as e:
  result=dict(status='FAIL2_QUPATH_SETUP_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,boundaries=boundaries)
  if owned:save(out/'FAILURE.json',result)
  print(result['status'],repr(e),flush=True);return 2
if __name__=='__main__':raise SystemExit(main())
