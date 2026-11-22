from pathlib import Path
import concurrent.futures,json,subprocess,time
T=Path('D:/projects/JLinAlg/build/gp-1');F=T/'build/fusion-1';N=F/'workflow-normal-build-5';assert json.loads((N/'RESULT.json').read_bytes())['exit_code']==0;jar=N/'project-build/cli/omicsmatic-0.4.0.jar';B=F/'workflow-runtime-controls-1';B.mkdir();base=json.loads((F/'workflow-linux-training-3/all-models.json').read_bytes())
def linux(p):s=str(p).replace('\\','/');return '/mnt/'+s[0].lower()+s[2:]
cases=[]
for name,expected in [('timeout-training',2),('wrong-R-edition',3),('wrong-package-edition',3),('missing-GEMMA',3),('input-byte-cap',2),('initial-output-byte-cap',2)]:
 r=json.loads(json.dumps(base));r['out']=linux(B/name)
 if name=='timeout-training':r['limits']={'timeout_seconds':10}
 if name=='wrong-R-edition':r['expected_R']='9.9.9'
 if name=='wrong-package-edition':r['expected_packages']['here']='0.0.0'
 if name=='missing-GEMMA':r['binaries']['gemma']=linux(B/'unavailable-gemma')
 if name=='input-byte-cap':r['limits']={'maximum_input_bytes':2}
 if name=='initial-output-byte-cap':r['limits']={'maximum_output_bytes':1024}
 file=B/(name+'.json');file.write_text(json.dumps(r,indent=2)+'\n');cases.append((name,expected,file))
def execute(item):
 name,expected,file=item;args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','java','-Xmx2g','-jar',linux(jar),'fusion-tools','--request',linux(file)];started=time.time()
 with (B/(name+'.stdout')).open('xb') as out,(B/(name+'.stderr')).open('xb') as err:p=subprocess.Popen(args,cwd=T,stdin=subprocess.DEVNULL,stdout=out,stderr=err);code=p.wait()
 root=B/name;failure=json.loads((root/'FAILURE.json').read_bytes());assert code==expected and failure['exit_code']==expected and not failure['results_admitted'],(name,code,failure)
 assert not (root/'COMPLETED.json').exists() and not (root/'admitted').exists(),name
 attempt=root/'worker-process.json';owned=[]
 if attempt.exists():
  metadata=json.loads(attempt.read_bytes());owned=metadata.get('owned_processes',[]);assert all(not row['same_identity_alive'] for row in owned),name
 if name=='timeout-training':assert 'timed out' in failure['cause'] and owned,(name,failure)
 else:assert not (root/'worker/original-process.json').exists(),name
 record=dict(case=name,exit_code=code,expected=expected,seconds=time.time()-started,observed_owned_processes=len(owned),all_recorded_identities_closed=True,no_admitted_result=True,analysis_not_started=name!='timeout-training');print(json.dumps(record),flush=True);return record
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:rows=list(pool.map(execute,cases))
(B/'RESULT.json').write_text(json.dumps(dict(status='PASS_PACKAGED_RUNTIME_SKIP_FAILURE_AND_OWNED_TIMEOUT_CONTROLS',cases=rows,synthetic_inputs_only=True,process_scope='owned root and observed birth-identified children; no OS containment claim'),indent=2)+'\n')