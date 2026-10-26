"""Independent rational assignment enumeration; no JLinAlg or asymptotic test.
Usage: python src/test/python/exact-conditional-reference.py OUTPUT_DIRECTORY
"""
from collections import defaultdict
from fractions import Fraction
from itertools import combinations
import csv,json,math
from pathlib import Path
import sys

root=Path(sys.argv[1]);root.mkdir(parents=True,exist_ok=True)
def table(path,header,rows):
    with path.open('w',encoding='utf-8',newline='') as f:
        w=csv.writer(f,delimiter='\t',lineterminator='\n');w.writerow(header);w.writerows(rows)
cases={
 'ternary':([0,1,0,1,0,1,0,0,0],[0,0,1,1,1,2,2,2,0],[0]*9,[0]*9),
 'stratified':([1,1,0,0,1,0,0,0,1,1,1,0],[0,1,1,2,0,1,2,2,0,0,1,2],[0]*4+[1]*4+[2]*4,[0]*12),
 'offsets':([1,0,1,0,1,1,1,0],[0,1,1,2,0,1,2,2],[0]*4+[1]*4,[-2,0,1,3,2,-1,0,1]),
 'missing':([1,0,1,0,0,1,0,0],[None,0,1,2,2,1,0,2],[0]*4+[1]*4,[0,1,-1,0,0,0,1,2]),
 'uninformative':([0,0,0,1,1,1,0,1,0,0],[0,1,2,0,2,1,0,1,2,0],[0]*3+[1]*2+[2]*5,[0]*10),
 'constant-stratum-genotype':([1,0,0,1,1,0,0,0],[2,2,2,0,1,1,2,0],[0]*3+[1]*5,[0]*8),
 'major-allele':([1,0,1,1,0,1,1,0],[2,1,2,2,1,0,2,2],[0]*4+[1]*4,[1,-1,2,0,0,2,-2,1]),
}
references=[];support=[]
for name,(y,g,strata,exponent) in cases.items():
    grouped=defaultdict(list)
    for i,value in enumerate(g):
        if value is not None:grouped[strata[i]].append(i)
    total={0:Fraction(1)};observed=sum(value for i,value in enumerate(g) if value is not None and y[i]);n=sum(len(v) for v in grouped.values());nc=0;informative=0
    for ids in grouped.values():
        k=sum(y[i] for i in ids);nc+=k;local=defaultdict(Fraction)
        for selected in combinations(ids,k):local[sum(g[i] for i in selected)]+=Fraction(2)**sum(exponent[i] for i in selected)
        norm=sum(local.values());local={score:weight/norm for score,weight in local.items()};informative+=len(local)>1
        joint=defaultdict(Fraction)
        for a,pa in total.items():
            for b,pb in local.items():joint[a+b]+=pa*pb
        total=dict(joint)
    observed_mass=total[observed];p=sum(v for v in total.values() if v<=observed_mass);less=sum(v for s,v in total.items() if s<=observed);greater=sum(v for s,v in total.items() if s>=observed)
    mean=sum(s*p for s,p in total.items());variance=sum(p*(s-mean)**2 for s,p in total.items())
    references.append([name,observed,float(p),math.log(float(p)),float(less),float(greater),float(mean),float(variance),n,nc,len(grouped),informative])
    for score,probability in sorted(total.items()):support.append([name,score,str(probability),math.log(float(probability))])
    table(root/(name+'.tsv'),['y','g','stratum','offset'],[[y[i],'NA' if g[i] is None else g[i],strata[i],exponent[i]*math.log(2)] for i in range(len(y))])
table(root/'reference.tsv',['case','observed_score','p_value','log_p_value','p_less','p_greater','mean','variance','observations','cases','strata','informative_strata'],references)
table(root/'support.tsv',['case','score','rational_probability','log_probability'],support)
(root/'PROVENANCE.txt').write_text('Python standard-library exhaustive assignment enumeration with Fraction weights 2^integer and exact rational stratum convolution. No JLinAlg code or likelihood asymptotic reference. Offsets are supplied as log(2)*integer.\n',encoding='utf-8')
print(json.dumps({'cases':len(cases),'support_rows':len(support)}))
