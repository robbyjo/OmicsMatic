"""Independent SciPy HiGHS LP truth for actual Cox risk contrasts; no inference calibration."""
from pathlib import Path
import csv,hashlib,json,sys
import numpy as np
import scipy
from scipy.optimize import linprog

target=Path(sys.argv[1]);assert not target.exists();target.mkdir(parents=True)
rng=np.random.default_rng(20261025);inputs=[];expected=[];attempts=0
while len(expected)<80:
    attempts+=1;n=12 if attempts%2 else 18;p=2 if attempts%3 else 3
    stop=rng.integers(1,9,n);entry=np.zeros(n,dtype=int) if attempts%2 else np.array([rng.integers(0,t) for t in stop]);strata=np.zeros(n,dtype=int) if attempts%2 else rng.integers(0,3,n)
    event=rng.random(n)<(.25 if attempts%4 else .75);event[0]=True;x=rng.integers(-3,4,(n,p))
    pairs=[(i,j) for i in range(n) if event[i] for j in range(n) if i!=j and strata[i]==strata[j] and entry[j]<stop[i]<=stop[j] and np.any(x[i]!=x[j])]
    d=np.array([x[i]-x[j] for i,j in pairs],dtype=float)
    if not pairs or np.linalg.matrix_rank(d)!=p:continue
    lp=linprog(-d.sum(axis=0),A_ub=-d,b_ub=np.zeros(len(d)),bounds=[(-1,1)]*p,method='highs')
    assert lp.success
    objective=-lp.fun
    if objective>1e-6:status='MONOTONE_LIKELIHOOD'
    elif abs(objective)<1e-10:status='FINITE_MAXIMUM'
    else:raise AssertionError('ambiguous independent oracle')
    name=f'case-{len(expected):03d}';expected.append([name,p,status,format(objective,'.17g')])
    for i in range(n):inputs.append([name,i,int(entry[i]),int(stop[i]),int(event[i]),int(strata[i]),*map(int,x[i]),*([0]*(3-p))])
for name,header,rows in [('inputs.tsv',['case','row','entry','time','event','stratum','x1','x2','x3'],inputs),('expected.tsv',['case','dimension','status','lp_objective'],expected)]:
    with (target/name).open('w',encoding='utf-8',newline='') as stream:
        writer=csv.writer(stream,delimiter='\t',lineterminator='\n');writer.writerow(header);writer.writerows(rows)
provenance={'generator':'SciPy HiGHS linprog: maximize sum(D*b), D*b>=0, -1<=b<=1; D=event-minus-risk actual interval/stratum contrasts','numpy':np.__version__,'scipy':scipy.__version__,'seed':20261025,'cases':len(expected),'states':{s:sum(r[2]==s for r in expected) for s in ['FINITE_MAXIMUM','MONOTONE_LIKELIHOOD']},'generator_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),'files_sha256':{n:hashlib.sha256((target/n).read_bytes()).hexdigest() for n in ['inputs.tsv','expected.tsv']},'scope':'Synthetic geometric existence checks, not sparse-event or population calibration'}
(target/'reference-provenance.json').write_text(json.dumps(provenance,indent=2)+'\n',encoding='utf-8');print(json.dumps(provenance))
