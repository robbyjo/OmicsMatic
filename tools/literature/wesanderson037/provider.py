#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install the exact original palette package privately for reference generation and original R plotting."""
from pathlib import Path
import argparse,hashlib,json,os,re,subprocess,tarfile,traceback,urllib.request
HERE=Path(__file__).resolve().parent
ACTION='Required action: use a permitted Linux/WSL R4.5.3, explicit --allow-install and NEW output. Supply exact --source-archive or permit its download with --allow-download. This installs only the original package into a private library and runs a readiness probe, not paper analysis. Missing/forbidden installation, source, download or runtime SKIP3 skips dependent original R analysis; supply permitted prerequisites or retain the skip. Java palette generation needs no R installation. FAIL2 retains diagnostics; resolve the named source/profile contradiction and retry NEW paths without changing original code or colors.'
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--rscript',required=True,type=Path);p.add_argument('--source-archive',type=Path);p.add_argument('--allow-download',action='store_true');p.add_argument('--allow-install',action='store_true');p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  for path in [a.rscript,a.source_archive]:
   if path and (out.is_relative_to(path.resolve()) or path.resolve().is_relative_to(out)):raise ValueError('Output overlaps protected runtime/source')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if not a.allow_install:raise OSError('Original package installation is forbidden; dependent R analysis skipped')
  if not a.source_archive and not a.allow_download:raise OSError('Exact local source or download permission required')
  if os.name=='nt':raise OSError('Use the documented Linux/WSL R4.5.3 source-reference profile')
  helper_pins={p.name:pin(p) for p in [HERE/'provider.py',HERE/'InstallOriginal.R',HERE/'source-profile.json']};profile=json.loads((HERE/'source-profile.json').read_bytes());archive=out/'wesanderson_0.3.7.tar.gz';env={k:v for k,v in os.environ.items() if k not in ['R_LIBS','R_LIBS_USER','R_LIBS_SITE','R_DEFAULT_PACKAGES','R_PROFILE_USER','R_ENVIRON_USER']};env['TMPDIR']=str(out/'tmp');(out/'tmp').mkdir()
  def run(label,args):
   command=list(map(str,args));r=subprocess.run(command,cwd=out,env=env,stdin=subprocess.DEVNULL,capture_output=True,timeout=240);(out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);calls.append(dict(label=label,command=command,exit_code=r.returncode));save(out/(label+'.process.json'),calls[-1])
   if r.returncode:
    if label=='R-version' or r.returncode in [126,127]:raise OSError('Original R runtime unavailable at '+label)
    raise ValueError('Original provider failed: '+label)
   return (r.stdout+r.stderr).decode('utf8',errors='replace')
  version=run('R-version',[a.rscript,'--version'])
  if not re.search(r'\b4\.5\.3\b',version):raise OSError('The qualified reference R4.5.3 runtime is unavailable')
  if a.source_archive:archive.write_bytes(a.source_archive.read_bytes())
  else:
   with urllib.request.urlopen(urllib.request.Request(profile['url'],headers={'User-Agent':'OmicsMatic-private-original-palette/1'}),timeout=60) as response:archive.write_bytes(response.read(profile['source_archive']['bytes']+1))
  if pin(archive)!=profile['source_archive']:raise ValueError('Original complete package archive differs')
  source=out/'source';source.mkdir()
  with tarfile.open(archive) as tar:
   members=[m for m in tar.getmembers() if not m.isdir()]
   if len(members)!=len(profile['source_files']) or {m.name for m in members}!=set(profile['source_files']):raise ValueError('Original complete package population differs')
   for m in members:
    if not m.isfile() or m.issym() or m.islnk() or m.name.startswith('/') or '\\' in m.name or any(x in ['','.','..'] for x in m.name.split('/')):raise ValueError('Unsafe original source member')
    raw=tar.extractfile(m).read();path=source/m.name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    if pin(path)!=profile['source_files'][m.name]:raise ValueError('Original source member differs')
  run('install',[a.rscript,'--vanilla',HERE/'InstallOriginal.R',archive,out/'library']);ready=(out/'library/READY.txt').read_text(encoding='utf8').splitlines()
  if len(ready)!=26 or ready[1]!='0.3.7':raise ValueError('Original readiness/version/palette population differs')
  if any(pin(HERE/name)!=wanted for name,wanted in helper_pins.items()) or any(pin(source/name)!=wanted for name,wanted in profile['source_files'].items()):raise ValueError('Original source or setup helper changed during installation')
  save(out/'READY.json',dict(status='READY_PRIVATE_ORIGINAL_WESANDERSON037_NO_ANALYSIS',source_profile=pin(HERE/'source-profile.json'),source_archive=pin(archive),setup_source_pins=helper_pins,reference_R_version=version,source_files=profile['source_files'],library=str(out/'library'),rscript=str(a.rscript),rscript_fingerprint=pin(a.rscript.resolve()),calls=calls,original_source_modified=False,paper_analysis_executed=False));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print('READY_PRIVATE_ORIGINAL_WESANDERSON037_NO_ANALYSIS',flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_ORIGINAL_R_PROVIDER_UNAVAILABLE' if code==3 else 'FAIL2_WESANDERSON037_PROVIDER',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',v);(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
