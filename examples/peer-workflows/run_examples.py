"""Run six original dense PEER examples with an explicit locally built runtime binding."""
from pathlib import Path
import argparse,json,subprocess,sys

def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jar',type=Path,required=True);parser.add_argument('--runtime',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--java',default='java');args=parser.parse_args()
    root=args.out.resolve();root.mkdir(parents=True,exist_ok=False);source=Path(__file__).resolve().parent;runtime=json.loads(args.runtime.read_bytes());jar=args.jar.resolve()
    if not jar.is_file():raise FileNotFoundError('Build the current source executable JAR and supply --jar')
    settings={'DENSE':{},'MEAN':dict(add_mean=True),'KNOWN':dict(add_mean=True,covariates=str(source/'covariates.tsv')),'UNCERTAINTY':dict(measurement_variance=str(source/'variance.tsv')),'PRIORS':dict(alpha_a=.125,alpha_b=.25,epsilon_a=.5,epsilon_b=1.25,covariate_precision=50),'PCA':dict(initialization='pca')}
    results=[]
    for name,options in settings.items():
        request=dict(schema_version=1,mode='RUN',profile='PEER-1.3-40bc4b2-original-fp32',out=str(root/name),matrix=str(source/'matrix.tsv'),runtime=runtime,factors=2,seed=17,preparation_note='Synthetic binary-rational expression; literal IDs; no participants. Prepared continuous values are not raw RNA-seq counts.',**options)
        path=root/(name+'.json');save(path,request);commands=[]
        for mode,arguments in [('RUN',['--request',str(path)]),('VERIFY',['--verify',str(root/name)])]:
            command=[args.java,'-jar',str(jar),'peer-tools',*arguments]
            with (root/(name+'.'+mode+'.stdout')).open('xb') as stdout,(root/(name+'.'+mode+'.stderr')).open('xb') as stderr:process=subprocess.run(command,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=3660)
            commands.append(dict(mode=mode,command=command,exit_code=process.returncode))
            if process.returncode!=0:
                print(('SKIP3: DEPENDENT ANALYSIS SKIPPED' if process.returncode==3 else 'FAIL2: NO COMPLETE RESULT')+' for '+name+'; read '+str(root/name/'WARNINGS.md')+' and the retained stderr logs.',file=sys.stderr);break
        results.append(dict(example=name,commands=commands,complete_result=len(commands)==2 and all(v['exit_code']==0 for v in commands)))
    code=2 if any(v['exit_code'] not in [0,3] for r in results for v in r['commands']) else 3 if any(not r['complete_result'] for r in results) else 0
    save(root/'RESULT.json',dict(status='PASS_SIX_ORIGINAL_PEER_EXAMPLES_AND_SEPARATE_VERIFICATIONS' if code==0 else 'SKIP3_DEPENDENT_ANALYSES_SKIPPED' if code==3 else 'FAIL2_NO_COMPLETE_EXAMPLE_SET',examples=results,complete_example_set=code==0,whole_paper_reproduced=False))
    print('PEER examples saved in '+str(root));return code

if __name__=='__main__':raise SystemExit(main())
