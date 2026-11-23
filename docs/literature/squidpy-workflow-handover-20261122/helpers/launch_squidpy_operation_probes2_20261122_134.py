from pathlib import Path
import argparse,json,subprocess,time
parser=argparse.ArgumentParser();parser.add_argument('version');args=parser.parse_args()
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';S=T/'build/squidpy-1';B=S/('operations-probes-'+args.version+'-2-launch');B.mkdir()
versions={'1.1.2':'runtime-legacy-4/environment-1.1.2','1.2.2':'runtime-legacy-2/environment-1.2.2','1.8.4':'runtime-current-1/environment'}
prefix='/mnt/d/projects/JLinAlg/build/fu-1/build/squidpy-1/'
command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','env','PYTHONDONTWRITEBYTECODE=1','OMP_NUM_THREADS=1','OPENBLAS_NUM_THREADS=1','NUMBA_NUM_THREADS=1','MPLBACKEND=Agg','PYTHONHASHSEED=0',
 'NUMBA_CACHE_DIR='+prefix+'operations-probes-'+args.version+'-cache-2','MPLCONFIGDIR='+prefix+'operations-probes-'+args.version+'-mpl-2',
 prefix+versions[args.version]+'/bin/python','-s','-B','/mnt/d/projects/JLinAlg/build/probe_squidpy_operations2_20261122_134.py',
 '--tools','/mnt/d/projects/JLinAlg/build/fu-1/tools/literature/squidpy_workflow',
 '--out',prefix+'operations-probes-'+args.version+'-2']
start=time.time()
with (B/'stdout').open('xb') as out,(B/'stderr').open('xb') as err:
 p=subprocess.Popen(command,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err)
 (B/'PROCESS.json').write_text(json.dumps(dict(pid=p.pid,arguments=command,started_unix=start),indent=2)+'\n');code=p.wait()
(B/'RESULT.json').write_text(json.dumps(dict(exit_code=code,seconds=time.time()-start),indent=2)+'\n')
print((B/'stdout').read_text(errors='replace')[-3500:],flush=True)
if code:print((B/'stderr').read_text(errors='replace')[-4000:],flush=True)
raise SystemExit(code)
