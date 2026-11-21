from pathlib import Path
import json,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-3';B=T/'build/twosamplemr-clump-1';O=B/'independent-original-1';O.mkdir();results={}
for root in sorted((B/'original-controls-3').iterdir()):
    if not root.is_dir() or not (root/'worker/report.json').exists():continue
    out=O/root.name;tmp=O/(root.name+'-temporary');tmp.mkdir()
    env=dict(os.environ,LANG='C',LC_ALL='C',TMPDIR=str(tmp),TEMP=str(tmp),TMP=str(tmp))
    command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(T/'tools/literature/twosamplemr_clump/compare_original.R'),str(root),str(out)]
    with (O/(root.name+'.stdout')).open('xb') as stdout,(O/(root.name+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,stdout=stdout,stderr=stderr,env=env,timeout=180)
    proof=json.loads((out/'RESULT.json').read_bytes()) if (out/'RESULT.json').exists() else {}
    results[root.name]=dict(exit_code=p.returncode,status=proof.get('status'),complete_returns=len(proof.get('calls',[])))
    print(root.name,results[root.name],flush=True)
(O/'RESULT.json').write_text(json.dumps(dict(results=results,complete_original_returns=sum(v['complete_returns'] for v in results.values()),all_pass=all(v['exit_code']==0 for v in results.values()),whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n')
