from pathlib import Path
import json,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1';O=B/'independent-original-1';O.mkdir();source=B/'java-pilot-2';prior=json.loads((source/'RESULT.json').read_bytes());assert prior['all_pass'];results={}
for key in prior['results']:
    version,name=key.split('/');root=source/version/name;out=O/version/name;out.parent.mkdir(exist_ok=True);command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(T/'tools/literature/twosamplemr_historical/compare_original.R'),str(root),str(out)]
    with (out.parent/(name+'.stdout')).open('xb') as stdout,(out.parent/(name+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,env=dict(os.environ,LANG='C',LC_ALL='C'),stdout=stdout,stderr=stderr,timeout=180)
    proof=json.loads((out/'RESULT.json').read_bytes()) if (out/'RESULT.json').exists() else {};results[key]=dict(exit_code=p.returncode,status=proof.get('status'),full_original_returns=proof.get('full_original_returns'),original_errors=proof.get('original_errors'));print(key,results[key],flush=True)
(O/'RESULT.json').write_text(json.dumps(dict(results=results,all_pass=all(v['exit_code']==0 for v in results.values()),full_original_returns=sum(v['full_original_returns'] or 0 for v in results.values()),original_errors=sum(v['original_errors'] or 0 for v in results.values()),new_catalog_credit=0,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n')
