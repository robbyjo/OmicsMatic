#!/usr/bin/env python3
"""Independent Python reference for the paper's static-degree greedy selection.

Uses ordinary sets and tuple sorting rather than the Java CSR/packed-key code.
Ties and eligibility are explicit. The counterexample has an exhaustive maximum
independent-set calculation,so maximal is never confused with maximum size.
"""
import argparse,hashlib,itertools,json,random
from pathlib import Path
def reference(ids,eligible,pairs,cutoff,ties):
    neighbors={identity:set() for identity in eligible};threshold=0
    for a,b,value in pairs:
        if value>cutoff:
            threshold+=1
            if a in eligible and b in eligible:neighbors[a].add(b);neighbors[b].add(a)
    initial={identity:len(values) for identity,values in neighbors.items()};positions={identity:i for i,identity in enumerate(ids)};order=sorted(eligible,key=lambda identity:(initial[identity],positions[identity] if ties=='INPUT_ORDER' else identity));retained=[];blocked={}
    for identity in order:
        if identity in blocked:continue
        retained.append(identity)
        for other in neighbors[identity]:blocked.setdefault(other,identity)
    decisions=[dict(sample_id=identity,eligible=identity in eligible,initial_degree=initial.get(identity,0),status='ineligible' if identity not in eligible else 'excluded-related' if identity in blocked else 'retained',excluded_by=blocked.get(identity,'')) for identity in ids]
    return dict(retained=retained,decisions=decisions,supplied_pairs=len(pairs),threshold_pairs=threshold,eligible_threshold_pairs=sum(len(values) for values in neighbors.values())//2)
def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args(argv);args.out.mkdir(parents=True,exist_ok=False);rng=random.Random(20261027);cases=[]
    for i in range(500):
        n=1+rng.randrange(75);ids=['sample%03d'%j for j in range(n)];rng.shuffle(ids);eligible=[identity for identity in ids if rng.random()>.2];pairs=[]
        for a,b in itertools.combinations(ids,2):
            if rng.random()<(i%8+1)/10:pairs.append([a,b,rng.choice([-.02,0,.05,.125,.25,.5])])
        ties='INPUT_ORDER' if i%2==0 else 'LEXICAL_ID';cases.append(dict(case='graph-%03d'%i,samples=ids,eligible=eligible,pairs=pairs,cutoff=.125,ties=ties,expected=reference(ids,set(eligible),pairs,.125,ties)))
    for trial in range(10000):
        ids=['v%d'%i for i in range(10)];pairs=[[a,b,.5] for a,b in itertools.combinations(ids,2) if rng.random()<.3];result=reference(ids,set(ids),pairs,.125,'INPUT_ORDER');edges={frozenset([a,b]) for a,b,_ in pairs};maximum=0
        for size in range(10,0,-1):
            if any(all(frozenset(pair) not in edges for pair in itertools.combinations(subset,2)) for subset in itertools.combinations(ids,size)):maximum=size;break
        if maximum>len(result['retained']):cases.append(dict(case='maximal-is-not-maximum',samples=ids,eligible=ids,pairs=pairs,cutoff=.125,ties='INPUT_ORDER',expected=result,exhaustive_maximum_size=maximum));break
    else:raise AssertionError('No independent counterexample found')
    record=dict(status='independent-Python-static-degree-greedy-reference',seed=20261027,generator_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),cases=cases,scope='Synthetic reference only;not an upstream SAIGE algorithm equivalence claim. Eligibility first,strict threshold,initial degrees,explicit ties,full decisions and named blockers. Exhaustive counterexample proves only maximal,not maximum cardinality.');path=args.out/'references.json';path.write_text(json.dumps(record,separators=(',',':'))+'\n',encoding='utf-8',newline='\n');print(json.dumps(dict(cases=len(cases),bytes=path.stat().st_size,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),counterexample_greedy_size=len(cases[-1]['expected']['retained']),counterexample_maximum_size=cases[-1]['exhaustive_maximum_size'])))
if __name__=='__main__':main()
