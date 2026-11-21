from pathlib import Path
import concurrent.futures,json,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-7';B=T/'build/twosamplemr-historical-clump-1';O=B/'normal-analyses-1';O.mkdir();D=B/'direct-original-normal-1';D.mkdir();J=B/'normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar';JAVA='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe'
completed=json.loads((B/'normal-gradle-1/COMPLETED.json').read_bytes());assert completed['exit_code']==0 and sum(v['tests'] for v in completed['reports'].values())==812
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
def run(item):
    name,previous=item;folder=O/name;folder.mkdir();r=json.loads((B/'pilot-1'/name/'request.json').read_bytes());capture=folder/('space path/analysis' if name=='space path' else 'analysis');r.update(out=str(capture));r['preparation_note']+='; actual normal JAR only, no loose production classes/resources';save(folder/'request.json',r);command=[JAVA,'-Xmx1g','-cp',str(J),'org.jlinalg.cli.TwoSampleMrHistoricalClumpCli','--request',str(folder/'request.json')];save(folder/'COMMAND.json',dict(command=command))
    with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,stdout=stdout,stderr=stderr,timeout=180)
    manifest=json.loads((capture/'manifest.json').read_bytes()) if (capture/'manifest.json').exists() else {};result=dict(exit_code=p.returncode,expected_exit=previous['expected_exit'],results_admitted=manifest.get('results_admitted'),error=manifest.get('error'))
    if p.returncode==0:
        with (folder/'verify.stdout').open('xb') as stdout,(folder/'verify.stderr').open('xb') as stderr:q=subprocess.run([JAVA,'-cp',str(J),'org.jlinalg.cli.TwoSampleMrHistoricalClumpCli','--verify',str(capture)],cwd=T,stdout=stdout,stderr=stderr,timeout=90)
        result['verify_exit']=q.returncode
    result['expected_outcome']=p.returncode==previous['expected_exit'] and (p.returncode!=0 or result.get('verify_exit')==0)
    if result['expected_outcome'] and name!='space path':
        out=D/name;tmp=folder/'direct-tmp';tmp.mkdir();command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(T/'tools/literature/twosamplemr_historical_clump/compare_original.R'),str(capture),str(out)];save(folder/'COMPARE-COMMAND.json',dict(command=command))
        with (folder/'compare.stdout').open('xb') as stdout,(folder/'compare.stderr').open('xb') as stderr:q=subprocess.run(command,cwd=T,env=dict(os.environ,LANG='C',LC_ALL='C',TMPDIR=str(tmp),TEMP=str(tmp),TMP=str(tmp)),stdout=stdout,stderr=stderr,timeout=180)
        proof=json.loads((out/'RESULT.json').read_bytes()) if (out/'RESULT.json').exists() else {};result.update(compare_exit=q.returncode,full_original_returns=len(proof.get('calls',[])),comparison_status=proof.get('status'))
    save(folder/'RESULT.json',result);print(name,result,flush=True);return name,result
prior=json.loads((B/'pilot-1/RESULT.json').read_bytes());assert prior['all_pass']
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=dict(pool.map(run,prior['results'].items()))
ok=all(v['expected_outcome'] and (k=='space path' or v.get('compare_exit')==0) for k,v in results.items());save(O/'RESULT.json',dict(results=results,all_pass=ok,normal_JAR=True,full_original_returns=sum(v.get('full_original_returns',0) for v in results.values()),expected_original_error_comparisons=3,new_catalog_credit=0,whole_goal_complete=False));assert ok
