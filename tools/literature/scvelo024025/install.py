#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Install one exact original scVelo edition into a NEW private Linux prefix only when permitted."""
from pathlib import Path
import argparse,hashlib,json,os,platform,subprocess,sys,time,traceback,urllib.request,zipfile
sys.dont_write_bytecode=True
HERE=Path(__file__).resolve().parent
ACTION='SKIP3 skips dependent analysis if installation/download is forbidden or the exact selected Linux/WSLx86_64 CPU prerequisites cannot be provisioned. Use explicit --allow-install --allow-download only where permitted, NEW --prefix and --out paths, or an existing captured exact provider. Installation/readiness fits no model and gives no implementation credit. Inspect every setup log/error; retain the partial prefix and complete console, correct the actual cause and retry NEW paths. FAIL2 admits no provider/result after integrity failure. Setup creates no global Python executable links and changes no existing environment. The printed0.24 label is unresolved and the R velociraptor wrapper is separate. Original upstream BSD and bootstrap MIT/Apache notices remain retained.'
class PrerequisiteUnavailable(OSError):pass
def pin(p):
 h=hashlib.sha256();size=0
 with p.open('rb') as f:
  for raw in iter(lambda:f.read(1048576),b''):h.update(raw);size+=len(raw)
 return dict(bytes=size,sha256=h.hexdigest())
def save(p,v):p.write_bytes((json.dumps(v,indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf8'))
def main():
 parser=argparse.ArgumentParser(description=__doc__,epilog=ACTION);parser.add_argument('--version',choices=['0.2.4','0.2.5'],required=True);parser.add_argument('--prefix',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');a=parser.parse_args();out=a.out.absolute();prefix=a.prefix.absolute();owned=False;calls=[]
 try:
  if out!=out.resolve() or prefix!=prefix.resolve():raise ValueError('Canonical NEW paths required')
  for protected in [prefix,HERE]:
   if out.is_relative_to(protected) or protected.is_relative_to(out):raise ValueError('Output overlaps protected runtime/source')
  if prefix.is_relative_to(HERE) or HERE.is_relative_to(prefix):raise ValueError('Private runtime overlaps protected source')
  out.mkdir(parents=True,exist_ok=False);owned=True
  if not a.allow_install:raise PrerequisiteUnavailable('Installation permission absent; dependent analysis skipped')
  if not a.allow_download:raise PrerequisiteUnavailable('Fresh prerequisite downloads forbidden; dependent analysis skipped')
  if sys.platform!='linux' or platform.machine()!='x86_64':raise PrerequisiteUnavailable('This original setup requires permitted Linux/WSLx86_64 CPU execution')
  prefix.mkdir(parents=True,exist_ok=False)
  for name in ['tools','cache','python']:(prefix/name).mkdir()
  profile=json.loads((HERE/'source-profile.json').read_bytes());selected=next(p for p in profile['profiles'] if p['version']==a.version);runtime=json.loads((HERE/'runtime-profile.json').read_bytes())['profiles'][a.version];bootstrap=json.loads((HERE/'bootstrap-profile.json').read_bytes())
  # Profile0.2.4/0.2.5 use explicit venv024/venv025 names and corresponding locks.
  requirements='requirements-cpu'+runtime['venv'].removeprefix('venv')+'.txt'
  source_names=['install.py','bootstrap-profile.json','source-profile.json','runtime-profile.json','runtime_audit.py','requirements-cpu024.txt','requirements-cpu025.txt','LICENSE-BSD-3-Clause.txt','uv-bootstrap-notices/LICENSE-APACHE','uv-bootstrap-notices/LICENSE-MIT']
  sources={n:pin(HERE/n) for n in source_names};save(out/'SOURCE_PINS.json',sources)
  def download(url,path,maximum,sha):
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-permitted-private-original-scvelo-setup'}),timeout=180) as response:raw=response.read(maximum+1)
   path.write_bytes(raw)
   if len(raw)>maximum or hashlib.sha256(raw).hexdigest()!=sha:raise ValueError('Exact complete original download integrity differs: '+path.name)
  wheel=out/'uv-original.whl';download(bootstrap['url'],wheel,bootstrap['wheel_maximum_bytes'],bootstrap['archive_sha256']);uv=prefix/'tools/uv'
  with zipfile.ZipFile(wheel) as z:
   names=[n for n in z.namelist() if n.endswith('.data/scripts/uv')]
   if len(names)!=1:raise ValueError('Original uv executable population differs')
   raw=z.read(names[0])
   if hashlib.sha256(raw).hexdigest()!=bootstrap['binary_sha256']:raise ValueError('Original uv executable differs')
   uv.write_bytes(raw);uv.chmod(0o755)
  originals={}
  for row in selected['source_downloads']:
   path=out/row['filename'];download(row['url'],path,row['bytes'],row['sha256']);originals[row['packagetype']]=path
  env={k:v for k,v in os.environ.items() if k not in ['PYTHONPATH','PYTHONHOME','LD_PRELOAD','LD_LIBRARY_PATH']}
  env.update(PYTHONNOUSERSITE='1',PYTHONDONTWRITEBYTECODE='1',UV_CACHE_DIR=str(prefix/'cache/uv'),UV_NATIVE_TLS='true',UV_INDEX_URL='https://pypi.org/simple',PIP_CACHE_DIR=str(prefix/'cache/pip'),PIP_INDEX_URL='https://pypi.org/simple',PIP_NO_COMPILE='1',PIP_DISABLE_PIP_VERSION_CHECK='1',MPLCONFIGDIR=str(out/'matplotlib-cache'),NUMBA_CACHE_DIR=str(out/'numba-cache'),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
  def run(label,command):
   command=list(map(str,command));start=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    child=subprocess.Popen(command,cwd=out,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se);save(out/(label+'.live.json'),dict(pid=child.pid,command=command,started_unix=start));code=child.wait(timeout=3600)
   calls.append(dict(label=label,command=command,exit_code=code,elapsed_seconds=time.time()-start));save(out/'PROCESSES.json',calls);print(label,'exit',code,flush=True)
   if code:
    error=(out/(label+'.stderr')).read_text(encoding='utf8',errors='replace')
    if label=='complete-original-runtime-capture' and code==2 or 'THESE PACKAGES DO NOT MATCH THE HASHES' in error:raise ValueError('Original source/dependency integrity failed at'+label+'; preserve every setup log')
    raise PrerequisiteUnavailable('Original prerequisite failed at'+label+'; dependent analysis skipped; inspect complete console')
  run('standalone-python',[uv,'python','install','3.9.25','--install-dir',prefix/'python','--no-bin'])
  standalone=prefix/'python/cpython-3.9.25-linux-x86_64-gnu/bin/python3.9'
  if not standalone.is_file():raise PrerequisiteUnavailable('Exact standalone Python3.9.25 layout unavailable')
  venv=prefix/runtime['venv'];python=venv/'bin/python'
  run('private-venv',[uv,'venv',venv,'--python',standalone]);run('pip-bootstrap',[uv,'pip','install','--python',python,'pip==25.3','setuptools==75.8.2','wheel==0.45.1'])
  pip=[python,'-B','-m','pip','--disable-pip-version-check']
  run('complete-hashed-original-environment',[*pip,'install','--no-compile','--no-build-isolation','--require-hashes','--progress-bar','off','--requirement',HERE/requirements,'--report',out/'install-report.json'])
  run('pip-check',[*pip,'check'])
  run('complete-original-runtime-capture',[python,'-B',HERE/'runtime_audit.py','--prefix',prefix,'--version',a.version,'--source-archive',originals['sdist'],'--wheel',originals['bdist_wheel'],'--out',out/'provider'])
  for n,wanted in sources.items():
   if pin(HERE/n)!=wanted:raise ValueError('Setup source changed during provisioning: '+n)
  ready=out/'provider/READY.json'
  if not ready.is_file():raise PrerequisiteUnavailable('Complete captured original provider unavailable')
  save(out/'COMPLETED.json',dict(status='INSTALLED_AND_CAPTURED_PRIVATE_ORIGINAL_SCVELO_CPU_NOT_ANALYSIS',version=a.version,prefix=str(prefix),provider_directory=str(out/'provider'),provider_ready=pin(ready),processes=calls,global_python_links_created=False,existing_runtime_modified=False,original_algorithms_patched=False,analysis_executed=False,new_tool_credit=0,whole_goal_complete=False));(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8');print('INSTALLED_AND_CAPTURED_PRIVATE_ORIGINAL_SCVELO_CPU_NOT_ANALYSIS',a.version,flush=True);return 0
 except BaseException as error:
  code=3 if isinstance(error,OSError) and not isinstance(error,FileExistsError) else 2;status='SKIP3_ORIGINAL_SCVELO_INSTALLATION_UNAVAILABLE' if code==3 else 'FAIL2_ORIGINAL_SCVELO_INSTALLATION_UNADMITTED';record=dict(status=status,cause=repr(error),traceback=traceback.format_exc(),prefix=str(prefix),processes=calls,dependent_analysis_skipped=True,required_action=ACTION,new_tool_credit=0,whole_goal_complete=False)
  if owned:
   try:save(out/'FAILURE.json',record);(out/'WARNINGS.md').write_text(status+'\n'+repr(error)+'\n'+ACTION+'\n',encoding='utf8')
   except BaseException as recording:print('FAIL2: setup failure recording unavailable',repr(recording),'\n'+record['traceback'],flush=True);code=2
  print(status,repr(error),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
