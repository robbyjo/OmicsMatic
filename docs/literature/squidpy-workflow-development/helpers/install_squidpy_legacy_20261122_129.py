from pathlib import Path
import json,subprocess,time
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/squidpy-1/runtime-legacy-1';B.mkdir();script=B/'install.py'
script.write_text('''from pathlib import Path
import hashlib,json,os,subprocess,time
B=Path('/mnt/d/projects/JLinAlg/build/fu-1/build/squidpy-1/runtime-legacy-1');uv=B.parent/'installer-intake-2/uv';expected=json.loads((uv.parent/'INTAKE.json').read_text())['extracted_executable'];assert hashlib.sha256(uv.read_bytes()).hexdigest()==expected['sha256']
env=dict(os.environ,UV_CACHE_DIR=str(B/'cache'),UV_LINK_MODE='copy',UV_PYTHON_INSTALL_DIR=str(B/'python'),UV_PYTHON_BIN_DIR=str(B/'python-bin'),XDG_DATA_HOME=str(B/'xdg'),UV_CONCURRENT_BUILDS='2',UV_CONCURRENT_DOWNLOADS='4',UV_CONCURRENT_INSTALLS='4',UV_NO_PROGRESS='1',PYTHONDONTWRITEBYTECODE='1',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',NUMBA_NUM_THREADS='1',MPLBACKEND='Agg')
records=[]
def execute(name,args):
 started=time.time()
 with (B/(name+'.stdout')).open('xb') as out,(B/(name+'.stderr')).open('xb') as err:
  p=subprocess.Popen(args,cwd=B,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/(name+'.PROCESS.json')).write_text(json.dumps(dict(pid=p.pid,arguments=args,started_unix=started),indent=2)+'\\n');code=p.wait()
 row=dict(step=name,exit_code=code,seconds=time.time()-started);records.append(row);(B/'PROGRESS.json').write_text(json.dumps(records,indent=2)+'\\n');print(json.dumps(row),flush=True)
 if code:
  (B/'FAILURE.json').write_text(json.dumps(dict(step=name,exit_code=code,required_action='Inspect original installation/import error; keep this attempt and use NEW output for changed dependency declarations. No runtime result or family credit.'),indent=2)+'\\n');print((B/(name+'.stderr')).read_text(errors='replace')[-3000:],flush=True);raise SystemExit(code)
execute('python-install',[str(uv),'python','install','3.10'])
execute('python-find',[str(uv),'python','find','3.10','--managed-python'])
python=Path((B/'python-find.stdout').read_text().strip());assert python.resolve().is_relative_to((B/'python').resolve())
requirements='''+'"""'+'''numpy==1.21.6
scipy==1.8.0
pandas==1.4.2
anndata==0.8.0
scanpy==1.9.1
scikit-learn==1.0.2
numba==0.55.2
llvmlite==0.38.1
matplotlib==3.5.1
seaborn==0.12.2
statsmodels==0.13.2
networkx==2.8.4
scikit-image==0.19.2
omnipath==1.0.6
dask[array,distributed]==2022.5.0
dask-image==2021.12.0
xarray==2022.3.0
zarr==2.11.3
numcodecs==0.10.2
h5py==3.7.0
tifffile==2022.5.4
pillow==9.1.0
'''+ '"""'+'''
for version in ['1.2.2','1.1.2']:
 target=B/('environment-'+version);assert not target.exists();execute(version+'-venv',[str(python),'-m','venv','--without-pip','--copies',str(target)])
 source=B/(version+'-requirements.in');source.write_text('squidpy=='+version+'\\n'+requirements);lock=B/(version+'-requirements.lock')
 execute(version+'-resolve',[str(uv),'pip','compile',str(source),'--python',str(target/'bin/python'),'--generate-hashes','--output-file',str(lock)])
 execute(version+'-install',[str(uv),'pip','sync',str(lock),'--python',str(target/'bin/python'),'--require-hashes'])
 env.update(NUMBA_CACHE_DIR=str(B/('numba-cache-'+version)),MPLCONFIGDIR=str(B/('matplotlib-cache-'+version)))
 execute(version+'-probe',[str(target/'bin/python'),'-B','-c','import sys,json,squidpy,importlib.metadata as m; print(json.dumps(dict(python=sys.version,squidpy=squidpy.__version__,packages={x.metadata["Name"]:x.version for x in m.distributions()}),sort_keys=True))'])
(B/'RESULT.json').write_text(json.dumps(dict(status='ORIGINAL_SQUIDPY_1_2_2_AND_1_1_2_PRIVATE_RUNTIMES_INSTALLED_AND_IMPORTED',steps=records,exact_paper_full_runtime_reproduced=False,system_or_existing_Scanpy_R_prefix_modified=False,new_family_credit=0),indent=2)+'\\n')
''',encoding='utf-8')
linux='/mnt/'+script.drive[0].lower()+script.as_posix()[2:];args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/python3',linux];started=time.time()
with (B/'launcher.stdout').open('xb') as out,(B/'launcher.stderr').open('xb') as err:
 p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);(B/'LAUNCHER.json').write_text(json.dumps(dict(pid=p.pid,arguments=args,started_unix=started),indent=2)+'\n');code=p.wait()
(B/'LAUNCHER-RESULT.json').write_text(json.dumps(dict(exit_code=code,seconds=time.time()-started),indent=2)+'\n');print((B/'launcher.stdout').read_text(errors='replace')[-4000:],flush=True)
if code:print((B/'launcher.stderr').read_text(errors='replace')[-2000:],flush=True)
raise SystemExit(code)
