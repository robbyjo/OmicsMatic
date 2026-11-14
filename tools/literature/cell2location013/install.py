#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Download/install only with explicit permission, into a NEW private Linux CPU prefix."""
from pathlib import Path
import argparse,hashlib,json,os,platform,subprocess,sys,time,traceback,urllib.request,zipfile
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent
ACTION='SKIP3 skips dependent analysis if installation/download is forbidden or the exact Linuxx86_64 CPU prerequisites cannot be provisioned. Use explicit --allow-install --allow-download only where permitted, NEW --prefix and --out paths, or supply an already captured permitted runtime to the analysis adapter. Inspect every named setup log/error; preserve partial prefixes and all receipts, correct the actual cause and retry NEW paths. FAIL2 admits no provider/result after integrity failure. The setup creates no global Python executable links and changes no existing runtime. Installation/readiness executes no model and is not a paper result. Source0.1.3 does not reproduce the reported0.7a0 edition. Read the development guide and retain all original package licenses.'
class PrerequisiteUnavailable(OSError):pass

def pin(p):
 h=hashlib.sha256();n=0
 with p.open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);n+=len(raw)
 return dict(bytes=n,sha256=h.hexdigest())

def save(p,v):p.write_bytes((json.dumps(v,indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf8'))

def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--prefix',type=Path,required=True);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=a.out.absolute();prefix=a.prefix.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve() or prefix!=prefix.resolve():raise ValueError('Canonical NEW paths required')
  for protected in [prefix,HERE]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected runtime/source')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if not a.allow_install:raise PrerequisiteUnavailable('Installation permission absent; no original analysis launched')
  if not a.allow_download:raise PrerequisiteUnavailable('Complete fresh prerequisite downloads are forbidden; no original analysis launched')
  if sys.platform!='linux' or platform.machine()!='x86_64':raise PrerequisiteUnavailable('This source setup requires permitted Linux/WSLx86_64 CPU execution')
  prefix.mkdir(parents=True,exist_ok=False)
  for n in ['tools','cache','python']:(prefix/n).mkdir()
  profile=json.loads((HERE/'source-profile.json').read_bytes());bootstrap=json.loads((HERE/'bootstrap-profile.json').read_bytes())
  sources={n:pin(HERE/n) for n in ['install.py','bootstrap-profile.json','requirements-cpu.txt','source-profile.json','runtime_audit.py','LICENSE-Apache-2.0.txt']};save(out/'SOURCE_PINS.json',sources)
  def download(url,path,maximum,sha):
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-permitted-private-cell2location-setup'}),timeout=180) as response:raw=response.read(maximum+1)
   path.write_bytes(raw)
   if len(raw)>maximum or hashlib.sha256(raw).hexdigest()!=sha:raise ValueError('Exact complete download integrity differs: '+path.name)
  wheel=out/'uv-original.whl';download(bootstrap['url'],wheel,bootstrap['wheel_maximum_bytes'],bootstrap['archive_sha256']);uv=prefix/'tools/uv'
  with zipfile.ZipFile(wheel) as z:
   names=[n for n in z.namelist() if n.endswith('.data/scripts/uv')]
   if len(names)!=1:raise ValueError('Exact original uv wheel executable population differs')
   raw=z.read(names[0])
   if hashlib.sha256(raw).hexdigest()!=bootstrap['binary_sha256']:raise ValueError('Exact original uv binary differs')
   uv.write_bytes(raw);uv.chmod(0o755)
  archive=out/profile['archive_name'];download(profile['url'],archive,profile['archive']['bytes'],profile['archive']['sha256'])
  env={k:v for k,v in os.environ.items() if k not in ['PYTHONPATH','PYTHONHOME','LD_PRELOAD','LD_LIBRARY_PATH']};env.update(PYTHONNOUSERSITE='1',PYTHONDONTWRITEBYTECODE='1',UV_CACHE_DIR=str(prefix/'cache/uv'),UV_NATIVE_TLS='true',PIP_CACHE_DIR=str(prefix/'cache/pip'),PIP_NO_COMPILE='1',PIP_DISABLE_PIP_VERSION_CHECK='1',PIP_INDEX_URL='https://pypi.org/simple',UV_INDEX_URL='https://pypi.org/simple',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',MPLCONFIGDIR=str(out/'matplotlib-cache'))
  def run(label,command):
   command=list(map(str,command));start=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/(label+'.live.json'),dict(pid=child.pid,command=command,started_unix=start));code=child.wait(timeout=3600)
   row=dict(label=label,command=command,exit_code=code,elapsed_seconds=time.time()-start);calls.append(row);save(out/'PROCESSES.json',calls);print(label,'exit',code,flush=True)
   if label=='complete-original-runtime-capture' and code==2:raise ValueError('Original runtime capture integrity/completeness failed; retain its full diagnostics and partial prefix')
   if code:raise PrerequisiteUnavailable('Original setup prerequisite failed at '+label+'; retain complete console and partial prefix')
  run('standalone-python',[uv,'python','install','3.9.25','--install-dir',prefix/'python','--no-bin'])
  interpreters=list((prefix/'python').glob('cpython-3.9.25-linux-x86_64-gnu/bin/python3.9'))
  if len(interpreters)!=1:raise PrerequisiteUnavailable('Exact standalone Python3.9.25 layout unavailable')
  run('private-venv',[uv,'venv',prefix/'venv','--python',interpreters[0]]);python=prefix/'venv/bin/python'
  run('pip-bootstrap',[uv,'pip','install','--python',python,'pip==25.3','setuptools==75.8.2','wheel==0.45.1']);pip=[python,'-B','-m','pip','--disable-pip-version-check']
  run('original-torch-cpu',[*pip,'install','--no-deps','--no-compile','--progress-bar','off','--index-url','https://download.pytorch.org/whl/cpu','torch==2.0.1+cpu','--report',out/'torch-install-report.json'])
  run('complete-pinned-original-environment',[*pip,'install','--no-compile','--no-build-isolation','--progress-bar','off','--requirement',HERE/'requirements-cpu.txt',archive,'--report',out/'install-report.json'])
  run('pip-check',[*pip,'check'])
  run('complete-original-runtime-capture',[python,'-B',HERE/'runtime_audit.py','--prefix',prefix,'--source-archive',archive,'--out',out/'provider'])
  for n,wanted in sources.items():
   if pin(HERE/n)!=wanted:raise ValueError('Setup source changed during provisioning: '+n)
  ready=out/'provider/READY.json'
  if not ready.is_file():raise PrerequisiteUnavailable('Complete captured original provider unavailable')
  save(out/'COMPLETED.json',dict(status='INSTALLED_AND_CAPTURED_PRIVATE_ORIGINAL_CELL2LOCATION013_CPU_NOT_ANALYSIS',prefix=str(prefix),provider_directory=str(out/'provider'),provider_ready=pin(ready),processes=calls,global_python_links_created=False,existing_runtime_modified=False,original_algorithms_patched=False,analysis_executed=False,new_tool_credit=0,whole_goal_complete=False));(out/'WARNINGS.md').write_bytes((ACTION+'\n').encode('utf8'));print('INSTALLED_AND_CAPTURED_PRIVATE_ORIGINAL_CELL2LOCATION013_CPU_NOT_ANALYSIS',flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;status='SKIP3_ORIGINAL_INSTALLATION_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_INSTALLATION_UNADMITTED';record=dict(status=status,cause=repr(error),traceback=traceback.format_exc(),prefix=str(prefix),processes=calls,dependent_analysis_skipped=True,required_action=ACTION,new_tool_credit=0,whole_goal_complete=False)
  if owned:
   try:save(out/'FAILURE.json',record)
   except BaseException as recording:print('FAIL2: failure record unavailable; retain console',repr(recording),flush=True);code=2
   try:(out/'WARNINGS.md').write_bytes((status+'\n'+repr(error)+'\n'+ACTION+'\n').encode('utf8'))
   except BaseException as recording:print('FAIL2: warnings record unavailable; retain console',repr(recording),flush=True);code=2
  print(status,repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
