from pathlib import Path
import concurrent.futures,hashlib,json,os,re,subprocess,sys,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/gc-8';B=T/'build/gcta-catalog-1';O=B/'controls-1';O.mkdir();sys.path.insert(0,str(T/'tools/literature'));from gcta_catalog import evidence as e
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf-8',newline='\n')
def linux(p):return '/mnt/d/'+Path(p).relative_to(Path('D:/')).as_posix()
def task(host):
    folder=O/host;folder.mkdir();args=['-B','-m','unittest','gcta_catalog.test_source','gcta_catalog.test_model','gcta_catalog.test_transaction','-v'];cwd=T/'tools/literature';env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1',GCTA_PRIMARY_CACHE=str(R/'build/literature-review/xml'))
    command=['C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','-X','utf8',*args] if host=='windows' else ['C:/Windows/System32/wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','--cd',linux(cwd),'--exec','/usr/bin/env','GCTA_PRIMARY_CACHE='+linux(R/'build/literature-review/xml'),'PYTHONDONTWRITEBYTECODE=1','/usr/bin/python3',*args]
    with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=cwd,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
    error=(folder/'stderr').read_text(errors='replace');match=re.search(r'Ran (\d+) tests?',error);record=dict(command=command,exit_code=p.returncode,tests=int(match[1]) if match else None);save(folder/'PROCESS.json',record);print('GCTA catalog controls',host,record['tests'],'tests exit',p.returncode,flush=True);return host,record
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:results=dict(pool.map(task,['windows','linux']))
save(O/'COMPLETED.json',dict(status='CONTROL_OBSERVATIONS_REVIEW_REQUIRED',results=results));assert all(r['exit_code']==0 and r['tests']==23 for r in results.values()),'Inspect preserved test failures before correcting anything'
Q=T/'docs/literature/gcta-catalog-controls';Q.mkdir();files={p.relative_to(O).as_posix():p.read_bytes() for p in O.rglob('*') if p.is_file()}
with zipfile.ZipFile(Q/'checks.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for name,raw in sorted(files.items()):archive.writestr(name,raw)
save(Q/'CONTROLS.json',dict(schema_version=1,status='PASS_GCTA_SOURCE_MODEL_TRANSACTION_CONTROLS_TWO_HOSTS',tests_per_host=23,results=results,source_code=e.sources(),archive=pin((Q/'checks.zip').read_bytes()),files={n:pin(raw) for n,raw in files.items()},scientific_model_execution=False,old_source_or_hashes_changed=False,new_family_credit=0))
print('PASS23 source/model/transaction controls per host;current-source controls saved',flush=True)
