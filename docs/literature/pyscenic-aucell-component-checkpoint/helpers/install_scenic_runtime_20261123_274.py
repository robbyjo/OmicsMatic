"""Linux-only private pinned pySCENIC runtime installation; no existing prefix mutation."""
from pathlib import Path
import hashlib,json,os,subprocess,time,sys
T=Path('/mnt/d/projects/JLinAlg/build/fu-1');D=T/'build/scenic-1/runtime-install-2';P=Path('/var/tmp/omicsmatic-scenic-runtime-20261123-274')
assert sys.platform=='linux' and not D.exists() and not P.exists();D.mkdir();P.mkdir()
uv=T/'build/squidpy-1/installer-intake-2/uv';intake=json.loads((uv.parent/'INTAKE.json').read_bytes());assert hashlib.sha256(uv.read_bytes()).hexdigest()==intake['extracted_executable']['sha256']
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
env=dict(os.environ,UV_CACHE_DIR=str(P/'cache'),UV_LINK_MODE='copy',UV_PYTHON_INSTALL_DIR=str(P/'python'),UV_PYTHON_BIN_DIR=str(P/'python-bin'),UV_DEFAULT_INDEX='https://pypi.org/simple',UV_CONCURRENT_BUILDS='2',UV_CONCURRENT_DOWNLOADS='4',UV_CONCURRENT_INSTALLS='4',UV_NO_PROGRESS='1',PYTHONDONTWRITEBYTECODE='1',OPENBLAS_NUM_THREADS='1',OMP_NUM_THREADS='1',NUMBA_NUM_THREADS='1',MPLBACKEND='Agg',NUMBA_CACHE_DIR=str(P/'numba-cache'),MPLCONFIGDIR=str(P/'matplotlib-cache'))
records=[]
def execute(label,arguments):
 start=time.time()
 with (D/(label+'.stdout')).open('xb') as out,(D/(label+'.stderr')).open('xb') as err:
  process=subprocess.Popen(arguments,cwd=D,env=env,stdin=subprocess.DEVNULL,stdout=out,stderr=err)
  save(D/(label+'.STARTED.json'),dict(pid=process.pid,arguments=arguments,started_unix=start,linux_start_ticks=Path('/proc/'+str(process.pid)+'/stat').read_text().rsplit(')',1)[1].split()[19]))
  code=process.wait()
 row=dict(step=label,exit_code=code,seconds=time.time()-start);records.append(row);save(D/'PROGRESS.json',records);print(json.dumps(row),flush=True)
 if code:
  save(D/'FAILURE.json',dict(status='SKIP3_PREREQUISITE_UNAVAILABLE' if code in (126,127) else 'FAIL2_RUNTIME_SETUP_FAILED',step=label,exit_code=code,scientific_analysis_executed=False,complete_result=False,required_action='Preserve this attempt. Supply/install permitted prerequisites; if installation is forbidden keep dependent analysis skipped. For changed dependency declarations use NEW private roots. Existing prefixes were not modified.'))
  print((D/(label+'.stderr')).read_text(errors='replace')[-2400:],flush=True);raise SystemExit(code)
execute('python-install',[str(uv),'python','install','3.10.22'])
execute('python-find',[str(uv),'python','find','3.10.22','--managed-python'])
python=Path((D/'python-find.stdout').read_text().strip());assert python.resolve().is_relative_to((P/'python').resolve())
target=P/'environment';execute('venv',[str(python),'-m','venv','--without-pip','--copies',str(target)])
requirements='''setuptools==68.2.2
pyscenic==0.12.1
ctxcore==0.2.0
numpy==1.23.5
pandas==1.5.3
scipy==1.10.1
numba==0.57.1
llvmlite==0.40.1
scikit-learn==1.1.3
dask[dataframe]==2022.12.1
distributed==2022.12.1
arboreto==0.1.6
pyarrow==12.0.1
loompy==3.0.7
umap-learn==0.5.3
networkx==2.8.8
attrs==22.2.0
'''
source=D/'requirements.in';source.write_text(requirements);lock=D/'requirements.lock'
execute('resolve',[str(uv),'pip','compile',str(source),'--python',str(target/'bin/python'),'--generate-hashes','--output-file',str(lock)])
execute('install',[str(uv),'pip','sync',str(lock),'--python',str(target/'bin/python'),'--require-hashes'])
execute('probe',[str(target/'bin/python'),'-B','-c','import sys,json,importlib.metadata as m,pyscenic.aucell,pyscenic.prune,pyscenic.utils,ctxcore.recovery,arboreto.algo;print(json.dumps(dict(python=sys.version,executable=sys.executable,packages={x.metadata["Name"]:x.version for x in m.distributions()}),sort_keys=True))'])
save(D/'COMPLETED.json',dict(status='PRIVATE_PINNED_ORIGINAL_PYSCENIC_RUNTIME_IMPORTED_NOT_SCIENTIFIC_QUALIFICATION',runtime_root=str(P),python=str(target/'bin/python'),steps=records,existing_prefixes_modified=False,scientific_analysis_executed=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
