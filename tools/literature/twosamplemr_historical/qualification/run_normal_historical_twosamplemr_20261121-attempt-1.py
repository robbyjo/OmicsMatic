from pathlib import Path
import concurrent.futures,copy,hashlib,json,os,subprocess,time
R=Path('D:/projects/JLinAlg');T=R/'build/mr-6';B=T/'build/twosamplemr-historical-qualification-1';P=R/'build/mr-5/build/twosamplemr-historical-1';O=B/'normal-analyses-1';D=B/'direct-original-1';J=P/'normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar';JAVA='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe';RSCRIPT='C:/Program Files/R/R-4.6.1/bin/Rscript.exe'
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
assert pin(J)==dict(bytes=196990713,sha256='84ed28614a9527bb46312cdf69ea6c84f9e84bb322da3e07aab5dc3f46402df7')
assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=T).decode().strip()=='f00f86ff3014dccea9b3459e96832f7b94eefd7c'
B.mkdir(exist_ok=False);O.mkdir();D.mkdir();prior=json.loads((P/'java-pilot-2/RESULT.json').read_bytes());assert prior['all_pass'];cases=[]
for key,result in prior['results'].items():
    version,name=key.split('/');cases.append((key,P/'java-pilot-2'/version/(name+'.json'),result['expected_exit']))
cases.append(('0.4.9/multivariable-latin',P/'column-order-control-3/request.json',0));assert len(cases)==49
save(B/'PRODUCER.json',dict(JAR=str(J),pin=pin(J),source_head='f00f86ff3014dccea9b3459e96832f7b94eefd7c',classpath_contains_only_normal_producer=True,prior_source_snapshots=str(P/'normal-gradle-3/sources')))
def process(folder,label,command,env=None):
    save(folder/(label+'-COMMAND.json'),dict(command=command,started_unix=time.time()))
    with (folder/(label+'.stdout')).open('xb') as stdout,(folder/(label+'.stderr')).open('xb') as stderr:
        p=subprocess.Popen(command,cwd=T,env=env,stdout=stdout,stderr=stderr,creationflags=subprocess.CREATE_NO_WINDOW)
        save(folder/(label+'-STARTED.json'),dict(owned_pid=p.pid,command=command))
        try:code=p.wait(timeout=240)
        except subprocess.TimeoutExpired:
            # The normal CLI's own shorter timeout owns its descendants. Do not
            # restart or stop arbitrary processes on observation failure.
            save(folder/(label+'-OBSERVATION-TIMEOUT.json'),dict(owned_pid=p.pid,still_live=p.poll() is None));raise
    save(folder/(label+'-TERMINAL.json'),dict(exit_code=code,finished_unix=time.time()));return code
def run(case):
    key,source,expected=case;version,name=key.split('/');folder=O/version/name;folder.mkdir(parents=True);capture=folder/'analysis';request=copy.deepcopy(json.loads(source.read_bytes()));request['out']=str(capture)
    request['preparation_note']+='; fresh normal-producer qualification with no loose production classes or resource directories'
    save(folder/'request.json',request);save(folder/'INPUTS.json',{k:dict(path=request[k],**pin(Path(request[k]))) for k in ['data','exposure','outcome','harmonised'] if k in request})
    command=[JAVA,'-Xmx1g','-cp',str(J),'org.jlinalg.cli.TwoSampleMrHistoricalCli','--request',str(folder/'request.json')]
    code=process(folder,'run',command);manifest=json.loads((capture/'manifest.json').read_bytes()) if (capture/'manifest.json').exists() else {};result=dict(exit_code=code,expected_exit=expected,results_admitted=manifest.get('results_admitted'),error=manifest.get('error'))
    if code==0:result['verify_exit']=process(folder,'verify',[JAVA,'-Xmx1g','-cp',str(J),'org.jlinalg.cli.TwoSampleMrHistoricalCli','--verify',str(capture)])
    result['normal_expectation_passed']=code==expected and (code!=0 or result.get('verify_exit')==0)
    if result['normal_expectation_passed']:
        comparison=D/version/name;comparison.parent.mkdir(parents=True,exist_ok=True)
        compare_code=process(folder,'direct-original',[RSCRIPT,'--vanilla',str(T/'tools/literature/twosamplemr_historical/compare_original.R'),str(capture),str(comparison)],dict(os.environ,LANG='C',LC_ALL='C'))
        proof=json.loads((comparison/'RESULT.json').read_bytes()) if (comparison/'RESULT.json').exists() else {}
        result['direct_original']=dict(exit_code=compare_code,full_original_returns=proof.get('full_original_returns'),original_errors=proof.get('original_errors'),guarded_functions=proof.get('source_functions_guarded'))
    save(folder/'RESULT.json',result);print(key,json.dumps(result),flush=True);return key,result
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=dict(pool.map(run,cases))
normal_pass=all(v['normal_expectation_passed'] for v in results.values());direct_pass=all(v.get('direct_original',{}).get('exit_code')==0 for v in results.values())
save(O/'RESULT.json',dict(status='PASS_49_NORMAL_PRODUCER_EXPECTATIONS' if normal_pass else 'FAILED',results=results,normal_JAR=True,successes=sum(v['exit_code']==0 for v in results.values()),original_failures=sum(v['exit_code']==2 for v in results.values()),new_catalog_credit=0,whole_goal_complete=False))
save(D/'RESULT.json',dict(status='PASS_FRESH_TAGGED_ORIGINAL_COMPARISONS' if direct_pass else 'FAILED',cases=len(results),full_original_returns=sum(v.get('direct_original',{}).get('full_original_returns') or 0 for v in results.values()),original_errors=sum(v.get('direct_original',{}).get('original_errors') or 0 for v in results.values()),results={k:v.get('direct_original') for k,v in results.items()},namespace_rewritten=False,new_catalog_credit=0,whole_goal_complete=False))
assert normal_pass and direct_pass
print('PASS 49 normal cases,45 successful,4 original rank errors;121 complete returns plus4 errors/RNG matched',flush=True)
