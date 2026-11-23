from pathlib import Path
import json,subprocess,time
T=Path('D:/projects/JLinAlg/build/fu-1');F=T/'build/squidpy-1';B=F/'runtime-current-1';B.mkdir();script=B/'install.py'
script.write_text('''from pathlib import Path
import hashlib,json,os,subprocess,time,venv
B=Path('/mnt/d/projects/JLinAlg/build/fu-1/build/squidpy-1/runtime-current-1');F=B.parent
uv=F/'installer-intake-2/uv';expected=json.loads((uv.parent/'INTAKE.json').read_text())['extracted_executable'];assert uv.stat().st_size==expected['bytes'] and hashlib.sha256(uv.read_bytes()).hexdigest()==expected['sha256'];uv.chmod(0o755)
target=B/'environment';assert not target.exists();venv.EnvBuilder(with_pip=False,symlinks=False).create(target)
env=dict(os.environ,UV_CACHE_DIR=str(B/'cache'),UV_LINK_MODE='copy',UV_CONCURRENT_BUILDS='2',UV_CONCURRENT_INSTALLS='4',UV_CONCURRENT_DOWNLOADS='4',UV_NO_PROGRESS='1',PYTHONDONTWRITEBYTECODE='1',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',NUMBA_NUM_THREADS='1',NUMBA_CACHE_DIR=str(B/'numba-cache'),MPLCONFIGDIR=str(B/'matplotlib-cache'),MPLBACKEND='Agg')
(B/'requirements.in').write_text('squidpy==1.8.4\\n')
commands=[('uv-version',[str(uv),'--version']),('resolve',[str(uv),'pip','compile',str(B/'requirements.in'),'--python',str(target/'bin/python'),'--generate-hashes','--output-file',str(B/'requirements.lock')]),('install',[str(uv),'pip','sync',str(B/'requirements.lock'),'--python',str(target/'bin/python'),'--require-hashes']),('probe',[str(target/'bin/python'),'-B','-c','import sys, json, squidpy, importlib.metadata as m; print(json.dumps(dict(python=sys.version, squidpy=squidpy.__version__, packages={x.metadata["Name"]:x.version for x in m.distributions()}),sort_keys=True))'])]
records=[]
for name,args in commands:
 started=time.time()
 with (B/(name+'.stdout')).open('xb') as out,(B/(name+'.stderr')).open('xb') as err:
  p=subprocess.Popen(args,cwd=B,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/(name+'.PROCESS.json')).write_text(json.dumps(dict(pid=p.pid,arguments=args,started_unix=started),indent=2)+'\\n');code=p.wait()
 record=dict(step=name,exit_code=code,seconds=time.time()-started);records.append(record);(B/'PROGRESS.json').write_text(json.dumps(records,indent=2)+'\\n');print(json.dumps(record),flush=True)
 if code:
  (B/'FAILURE.json').write_text(json.dumps(dict(step=name,exit_code=code,required_action='Inspect original installer/import diagnostics; preserve this attempt and use NEW output for any changed runtime. No Squidpy result or family credit.'),indent=2)+'\\n');print((B/(name+'.stderr')).read_text(errors='replace')[-3000:],flush=True);raise SystemExit(code)
(B/'RESULT.json').write_text(json.dumps(dict(status='ORIGINAL_SQUIDPY_1_8_4_PRIVATE_RUNTIME_INSTALLED_AND_IMPORTED',steps=records,system_or_existing_Scanpy_R_prefix_modified=False,new_family_credit=0),indent=2)+'\\n')
''',encoding='utf-8')
args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/python3',str(script).replace('D:/','/mnt/d/')];started=time.time()
with (B/'launcher.stdout').open('xb') as out,(B/'launcher.stderr').open('xb') as err:
 p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/'LAUNCHER.json').write_text(json.dumps(dict(pid=p.pid,arguments=args,started_unix=started),indent=2)+'\n');code=p.wait()
(B/'LAUNCHER-RESULT.json').write_text(json.dumps(dict(exit_code=code,seconds=time.time()-started),indent=2)+'\n');print((B/'launcher.stdout').read_text(errors='replace')[-4000:],flush=True)
if code:print((B/'launcher.stderr').read_text(errors='replace')[-2000:],flush=True)
raise SystemExit(code)
