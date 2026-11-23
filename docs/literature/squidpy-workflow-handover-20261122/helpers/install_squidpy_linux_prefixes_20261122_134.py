from pathlib import Path
import json,subprocess,time
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/squidpy-1/linux-runtime-install-1';B.mkdir();script=B/'install.py'
script.write_text('''from pathlib import Path
import hashlib,json,os,subprocess,sys,time
S=Path('/mnt/d/projects/JLinAlg/build/fu-1/build/squidpy-1');O=S/'linux-runtime-install-1'
L=Path('/var/tmp/omicsmatic-squidpy-workflow-20261122-134');assert not L.exists();L.mkdir()
uv=S/'installer-intake-2/uv';assert hashlib.sha256(uv.read_bytes()).hexdigest()==json.loads((uv.parent/'INTAKE.json').read_text())['extracted_executable']['sha256']
env=dict(os.environ,UV_CACHE_DIR=str(L/'cache'),UV_LINK_MODE='copy',UV_PYTHON_INSTALL_DIR=str(L/'python'),UV_PYTHON_BIN_DIR=str(L/'python-bin'),UV_CONCURRENT_DOWNLOADS='4',UV_CONCURRENT_BUILDS='2',UV_CONCURRENT_INSTALLS='4',UV_NO_PROGRESS='1',PYTHONDONTWRITEBYTECODE='1',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',NUMBA_NUM_THREADS='1',MPLBACKEND='Agg')
steps=[];environments={}
def execute(name,args):
 start=time.time()
 with (O/(name+'.stdout')).open('xb') as out,(O/(name+'.stderr')).open('xb') as err:
  p=subprocess.Popen(args,cwd=L,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err)
  (O/(name+'.PROCESS.json')).write_text(json.dumps(dict(pid=p.pid,arguments=args,started_unix=start),indent=2)+'\\n');code=p.wait()
 row=dict(step=name,exit_code=code,seconds=time.time()-start);steps.append(row);(O/'PROGRESS.json').write_text(json.dumps(steps,indent=2)+'\\n');print(json.dumps(row),flush=True)
 if code:
  (O/'FAILURE.json').write_text(json.dumps(dict(status='PRIVATE_RUNTIME_SETUP_FAILED_PRESERVED',step=name,exit_code=code,required_action='Inspect original install/import error. Keep this new prefix and failed evidence. Use an existing explicitly named working runtime or a NEW permitted prefix; do not overwrite a failed environment.'),indent=2)+'\\n');raise SystemExit(code)
profile=Path('/mnt/d/projects/JLinAlg/build/fu-1/tools/literature/squidpy_workflow/SOURCE-PROFILE.json')
for version,python_version,lock,original_python_file in [
 ('1.8.4','3.12.3',S/'runtime-current-1/requirements.lock',None),
 ('1.2.2','3.10.22',S/'runtime-legacy-2/1.2.2-requirements.lock',S/'runtime-legacy-1/python-find.stdout'),
 ('1.1.2','3.8.20',S/'runtime-legacy-4/1.1.2-requirements.lock',S/'runtime-legacy-3/python-find.stdout')]:
 if version=='1.8.4':python=Path('/usr/bin/python3');original_python=python.resolve()
 else:
  execute(version+'-python-install',[str(uv),'python','install',python_version]);execute(version+'-python-find',[str(uv),'python','find',python_version,'--managed-python'])
  python=Path((O/(version+'-python-find.stdout')).read_text().strip());assert python.resolve().is_relative_to((L/'python').resolve());original_python=Path(original_python_file.read_text().strip()).resolve()
 assert hashlib.sha256(python.resolve().read_bytes()).hexdigest()==hashlib.sha256(original_python.read_bytes()).hexdigest(), 'Interpreter bytes differ from retained original intake'
 target=L/('squidpy-'+version);execute(version+'-venv',[str(python),'-m','venv','--without-pip','--symlinks',str(target)])
 (O/(version+'-LOCK.json')).write_text(json.dumps(dict(source=str(lock),bytes=lock.stat().st_size,sha256=hashlib.sha256(lock.read_bytes()).hexdigest(),interpreter_bytes_match_original=True),indent=2)+'\\n')
 execute(version+'-install',[str(uv),'pip','sync',str(lock),'--python',str(target/'bin/python'),'--require-hashes'])
 env.update(NUMBA_CACHE_DIR=str(L/('probe-numba-'+version)),MPLCONFIGDIR=str(L/('probe-mpl-'+version)))
 probe=O/(version+'-probe.py')
 probe.write_text("from pathlib import Path\\nimport hashlib,json,sys,importlib.metadata as m\\nimport squidpy as sq\\np=json.loads(Path("+repr(str(profile))+").read_text())['editions']["+repr(version)+"]\\nr=Path(m.distribution('squidpy').locate_file('squidpy'))\\nfor n,i in p['members'].items():\\n raw=(r/n).read_bytes();assert len(raw)==i['bytes'] and hashlib.sha256(raw).hexdigest()==i['sha256'],n\\nprint(json.dumps(dict(python=sys.version,executable=sys.executable,squidpy=m.version('squidpy'),original_wheel_members_checked=len(p['members']),packages={d.metadata['Name']:d.version for d in m.distributions()}),sort_keys=True))\\n")
 execute(version+'-probe',[str(target/'bin/python'),'-s','-B',str(probe)])
 environments[version]=dict(python=str(target/'bin/python'),expected_python=python_version,requirements_lock=str(lock),original_interpreter_bytes_match=True)
 (O/'ENVIRONMENTS.json').write_text(json.dumps(environments,indent=2)+'\\n')
(O/'RESULT.json').write_text(json.dumps(dict(status='ALL_THREE_LOCKED_ORIGINAL_SQUIDPY_LINUX_FILESYSTEM_RUNTIMES_INSTALLED',private_root=str(L),environments=environments,steps=steps,existing_prefixes_modified=False,author_full_environment_reproduced=False,new_family_credit=0),indent=2)+'\\n')
''',encoding='utf-8')
linux='/mnt/d'+script.as_posix()[2:];command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/python3',linux];start=time.time()
with (B/'launcher.stdout').open('xb') as out,(B/'launcher.stderr').open('xb') as err:
 p=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/'LAUNCHER.json').write_text(json.dumps(dict(pid=p.pid,arguments=command,started_unix=start),indent=2)+'\n');code=p.wait()
(B/'LAUNCHER-RESULT.json').write_text(json.dumps(dict(exit_code=code,seconds=time.time()-start),indent=2)+'\n');print((B/'launcher.stdout').read_text(errors='replace')[-5000:],flush=True)
if code:print((B/'launcher.stderr').read_text(errors='replace')[-3000:],flush=True)
raise SystemExit(code)
