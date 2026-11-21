from pathlib import Path
import concurrent.futures,hashlib,json,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-7';B=T/'build/twosamplemr-historical-clump-1';C=B/'compile-1';C.mkdir();O=B/'pilot-1';O.mkdir();D=B/'direct-original-1';D.mkdir();J=R/'build/mr-5/build/twosamplemr-historical-1/normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar';prior=R/'build/mr-4/build/twosamplemr-clump-adapter-1/pilot-2';V=R/'build/twosamplemr-historical-runtime-20261121-1';JAVA='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe';classes=C/'classes';classes.mkdir();cp=';'.join(map(str,[classes,T/'src/main/resources',J]))
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
sources=sorted(T.glob('src/main/java/org/jlinalg/cli/TwoSampleMrHistoricalClump*.java'));assert len(sources)==5;command=['C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe','--release','17','-Xlint:all','-Werror','-cp',str(J),'-d',str(classes),*map(str,sources)];save(C/'COMMAND.json',dict(command=command))
with (C/'stdout').open('xb') as stdout,(C/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,stdout=stdout,stderr=stderr,timeout=180)
save(C/'PROCESS.json',dict(exit_code=p.returncode));assert p.returncode==0,(C/'stderr').read_text()
cases=json.loads((prior/'RESULT.json').read_bytes());assert cases['all_pass'];results={}
def run(item):
    name,previous=item;folder=O/name;folder.mkdir();capture=folder/'analysis';r=json.loads((prior/(name+'.request.json')).read_bytes());r.update(profile='TwoSampleMR0.6.8-ieugwasr1.2.1-PLINK1.9-20260927-R4.6.1-Windows64',out=str(capture),r_libraries=list(map(str,[V/'versions/0.6.8/library',V/'library',R/'build/twosamplemr-runtime-intake-20261121-1/library'])),preparation_note='Literal synthetic published modern-clumping control input, run against original tagged0.6.8 with declared present-day ieugwasr1.2.1/PLINK; no scientific repair')
    if name=='space path':r['out']=str(folder/'space path/analysis');capture=Path(r['out'])
    save(folder/'request.json',r);command=[JAVA,'-Xmx1g','-cp',cp,'org.jlinalg.cli.TwoSampleMrHistoricalClumpCli','--request',str(folder/'request.json')];save(folder/'COMMAND.json',dict(command=command))
    with (folder/'stdout').open('xb') as stdout,(folder/'stderr').open('xb') as stderr:p=subprocess.run(command,cwd=T,stdout=stdout,stderr=stderr,timeout=180)
    state=json.loads((capture/'manifest.json').read_bytes()) if (capture/'manifest.json').exists() else {};result=dict(exit_code=p.returncode,expected_exit=previous['expected_exit'],results_admitted=state.get('results_admitted'),error=state.get('error'))
    if p.returncode==0:
        with (folder/'verify.stdout').open('xb') as stdout,(folder/'verify.stderr').open('xb') as stderr:q=subprocess.run([JAVA,'-cp',cp,'org.jlinalg.cli.TwoSampleMrHistoricalClumpCli','--verify',str(capture)],cwd=T,stdout=stdout,stderr=stderr,timeout=90)
        result['verify_exit']=q.returncode
    result['expected_outcome']=p.returncode==previous['expected_exit'] and (p.returncode!=0 or result.get('verify_exit')==0)
    if result['expected_outcome'] and name!='space path':
        out=D/name;tmp=folder/'direct-tmp';tmp.mkdir();command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(T/'tools/literature/twosamplemr_historical_clump/compare_original.R'),str(capture),str(out)];save(folder/'COMPARE-COMMAND.json',dict(command=command));env=dict(os.environ,LANG='C',LC_ALL='C',TMPDIR=str(tmp),TEMP=str(tmp),TMP=str(tmp))
        with (folder/'compare.stdout').open('xb') as stdout,(folder/'compare.stderr').open('xb') as stderr:q=subprocess.run(command,cwd=T,env=env,stdout=stdout,stderr=stderr,timeout=180)
        proof=json.loads((out/'RESULT.json').read_bytes()) if (out/'RESULT.json').exists() else {};result.update(compare_exit=q.returncode,complete_original_returns=len(proof.get('calls',[])),comparison_status=proof.get('status'))
    save(folder/'RESULT.json',result);print(name,result,flush=True);return name,result
with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=dict(pool.map(run,cases['results'].items()))
save(O/'RESULT.json',dict(results=results,all_pass=all(x['expected_outcome'] and (name=='space path' or x.get('compare_exit')==0) for name,x in results.items()),normal_JAR=False,new_catalog_credit=0,whole_goal_complete=False))
save(D/'RESULT.json',dict(cases=24,full_original_returns=sum(x.get('complete_original_returns',0) for x in results.values()),all_pass=all(x.get('compare_exit')==0 for name,x in results.items() if name!='space path'),namespace_bindings_rewritten_in_direct_calls=False,new_catalog_credit=0,whole_goal_complete=False))
assert all(x['expected_outcome'] and (name=='space path' or x.get('compare_exit')==0) for name,x in results.items())
