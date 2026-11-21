from pathlib import Path
import hashlib,json,subprocess,sys
R=Path('D:/projects/JLinAlg');T=R/'build/gc-8';O=T/'build/gcta-catalog-1/cli-controls-1';O.mkdir();sys.path.insert(0,str(T/'tools/literature'));from gcta_catalog import model as m,evidence as e
def snapshot():return {n:hashlib.sha256((T/n).read_bytes()).hexdigest() for n in [*m.META,m.DATA+'/SHA256SUMS',e.PROOF,e.COMPAT,m.COVERAGE]}
before=snapshot();prefix=[sys.executable,'-B',str(T/'tools/literature/close_gcta_catalog.py')];cases=[('existing-output',['--verify','--out',str(T/'build/gcta-catalog-1/verify-fresh-1')],2),('missing-primary-cache',['--verify','--primary-cache',str(O/'absent-primary-cache'),'--out',str(O/'missing-cache-receipt')],3),('stale-inspect',['--inspect','--plan-dir',str(T/'build/gcta-catalog-1/plan-1'),'--out',str(O/'stale-inspect-receipt')],2),('outside-owner-output',['--verify','--out',str(R/'build/gcta-outside-owner-refusal-20261120-1')],2),('missing-mode',['--out',str(O/'missing-mode-receipt')],2)];rows=[]
assert not (R/'build/gcta-outside-owner-refusal-20261120-1').exists()
for name,args,expected in cases:
    command=prefix+args
    with (O/(name+'.stdout')).open('xb') as stdout,(O/(name+'.stderr')).open('xb') as stderr:p=subprocess.run(command,cwd=T,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
    rows.append(dict(name=name,command=command,exit_code=p.returncode,expected_exit=expected));assert p.returncode==expected,(name,(O/(name+'.stderr')).read_text(errors='replace')[-1500:]);assert snapshot()==before
assert not (R/'build/gcta-outside-owner-refusal-20261120-1').exists();assert json.loads((O/'missing-cache-receipt/FAILURE.json').read_bytes())['complete_result'] is False
(O/'COMPLETED.json').write_text(json.dumps(dict(status='PASS_FIVE_GCTA_CATALOG_CLI_REFUSALS_CANONICAL_BYTES_UNCHANGED',results=rows,canonical_before_after=before,analysis_executed=False),indent=2)+'\n',encoding='utf-8',newline='\n');print('PASS5 actual CLI refusals;canonical bytes unchanged',flush=True)
