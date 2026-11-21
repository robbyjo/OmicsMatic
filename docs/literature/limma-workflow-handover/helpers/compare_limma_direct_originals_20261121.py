from pathlib import Path
import concurrent.futures,json,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/lm-3';B=T/'build/limma-workflows-1';O=B/'direct-originals-1';O.mkdir();RS=Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe');script=T/'tools/literature/limma_workflow/stock_reference.R'
sources={p.name:p/'analysis' for p in (B/'java-probe-2').iterdir() if p.is_dir()};sources.update({p.name:p/'analysis' for p in (B/'java-probe-extended-1').iterdir() if p.is_dir()})
env={k:v for k,v in os.environ.items() if not k.startswith('R_LIBS') and k not in ['R_PROFILE','R_PROFILE_USER','R_ENVIRON','R_ENVIRON_USER','R_DEFAULT_PACKAGES','R_TESTS','LANG','LANGUAGE'] and not k.startswith('LC_')};env.update(LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
def run(item):
    name,root=item;folder=O/name;folder.mkdir();command=[str(RS),'--vanilla',str(script),str(root),str(folder)]
    with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=folder,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
    result=json.loads((folder/'COMPLETED.json').read_bytes()) if (folder/'COMPLETED.json').exists() else {}
    (folder/'PROCESS.json').write_text(json.dumps(dict(command=command,exit_code=p.returncode),indent=2)+'\n',encoding='utf-8',newline='\n');print(name,p.returncode,[(k,v.get('detail')) for k,v in result.get('comparisons',{}).items() if not v.get('all_equal')],flush=True);return name,dict(exit_code=p.returncode,result=result)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=dict(pool.map(run,sources.items()))
(O/'RESULTS.json').write_text(json.dumps(results,indent=2)+'\n',encoding='utf-8',newline='\n')
