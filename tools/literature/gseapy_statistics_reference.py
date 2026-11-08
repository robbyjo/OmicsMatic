# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Execute unchanged original stats functions; retain source values and independent exact small-count tails."""
import argparse,fractions,hashlib,importlib.util,json,math,random,struct,sys,warnings
from pathlib import Path
import numpy as np
import scipy
from scipy.stats import hypergeom

def bits(value):return struct.pack('>d',float(value)).hex()
def pin(path):raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False,allow_nan=False)+'\n',encoding='utf8')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);a=parser.parse_args();source=a.source.resolve();out=a.out.resolve();assert not out.exists();out.mkdir(parents=True)
    intake=json.loads((source/'SOURCE.json').read_bytes());assert intake['status']=='passed-four-paper-reported-official-GSEApy-source-wheels'
    root=source.parents[3];runtime=root/'build/cellphonedb-workflow-case-sensitive-1/runtime-1'
    inventory_source=root/'src/main/resources/org/jlinalg/omics/scfates-workflow/setup/inventory.py';spec=importlib.util.spec_from_file_location('bound_runtime_inventory',inventory_source);inventory_module=importlib.util.module_from_spec(spec);spec.loader.exec_module(inventory_module)
    baseline=root/'build/literature-review/scfates-runtime/workflow-setup-3/base-after.json';expected=json.loads(baseline.read_bytes());before=inventory_module.inventory(runtime,out/'runtime-before.progress.json');save(out/'runtime-before.json',before);assert before==expected,'Original dependency tree changed; no analysis admitted'
    print('Verified complete bound reference runtime before original stats',flush=True)
    assert sys.version_info[:3]==(3,11,17) and np.__version__=='1.26.4' and scipy.__version__=='1.11.4'
    modules={};pins={}
    for version,profile in intake['versions'].items():
        path=source/version/'wheel-source/gseapy/stats.py';assert pin(path)==profile['members']['gseapy/stats.py'];spec=importlib.util.spec_from_file_location('original_gseapy_stats_'+version.replace('.','_'),path);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);modules[version]=module;pins[version]=pin(path)
    # Each fixture uses the original stats-level literal identifiers, not a
    # guessed high-level case conversion, downloaded library or online service.
    cases=[dict(id='literal-identities',query=['a','a','A',' gap ','','α','😀'],sets={'z':['a','A','a'],'α':['α','outside'],'😀':['😀'],'\ue000':[' gap ',''],'no hits':['x']},background=['a','A',' gap ','','α','😀','unused']),dict(id='integer-background-keeps-identities',query=['a','a','A'],sets={'subset':['a','a'],'empty':[],'full-query':['a','A'],'nohit':['x']},background=10),dict(id='explicit-empty-universe',query=['a'],sets={'removed':['a']},background=[]),dict(id='empty-query',query=[],sets={'a':['a']},background=['a']),dict(id='too-small-numeric-population',query=['a','b','c'],sets={'full':['a','b','c'],'partial':['a']},background=1),dict(id='zero-numeric-population',query=['a'],sets={'a':['a']},background=0),dict(id='all-background-mass',query=['a','b'],sets={'a':['a'],'all':['a','b']},background=['a','b']),dict(id='no-terms',query=['a'],sets={},background=['a'])]
    rng=random.Random(648172);universe=['g'+str(i) for i in range(80)]
    for i in range(100):
        query=rng.choices(universe,k=rng.randrange(0,65));sets={('term'+str(j)):rng.choices(universe,k=rng.randrange(0,85)) for j in range(9)}
        bg=rng.sample(universe,rng.randrange(0,81)) if i%2 else rng.randrange(1,120)
        cases.append(dict(id='random-'+str(i),query=query,sets=sets,background=bg))
    allcases=[]
    with warnings.catch_warnings(record=True) as captured:
        warnings.simplefilter('always')
        for version,module in modules.items():
            for case in cases:
                bg=set(case['background']) if isinstance(case['background'],list) else case['background'];values=list(module.calc_pvalues(case['query'],case['sets'],background=bg));rows=[]
                if values:
                    terms,p,odds,overlap,size,hits=values;q,reject=module.multiple_testing_correction(p,alpha=0.05,method='benjamini-hochberg')
                    for j,term in enumerate(terms):rows.append(dict(term=term,query_size=len(set(case['query'])&(bg if isinstance(bg,set) else set(case['query']))),overlap=overlap[j],term_size=size[j],hits=sorted(hits[j]),p=bits(p[j]),q=bits(q[j]),odds=bits(odds[j]),combined=bits(-np.log(p[j])*odds[j]) if version!='1.0.4' else None,reject=bits(reject[j])))
                allcases.append(dict(**case,edition=version,alpha=0.05,rows=rows))
    # Exhaustive small-count probability support plus invalid numeric margins.
    counts=[]
    for population in range(0,23):
        for query in range(0,population+1):
            for term in range(0,population+1):
                for overlap in range(0,min(query,term)+1):counts.append([population,query,term,overlap])
    counts.extend([[0,1,1,1],[1,3,1,1],[2,2,5,1],[20,5,6,0],[10000,500,1000,500],[1000000,1000,500,100],[20000,200,500,4],[1000,1000,1000,1000]])
    arr=np.asarray(counts,dtype=np.int64);tails=hypergeom.sf(arr[:,3]-1,arr[:,0],arr[:,2],arr[:,1]);tailrows=[];exact=0;maximum_relative=0.0
    for (population,query,term,overlap),tail in zip(counts,tails):
        if population>0 and population<=22 and query<=population and term<=population:
            numerator=sum(math.comb(term,x)*math.comb(population-term,query-x) for x in range(max(overlap,query-(population-term),0),min(query,term)+1));oracle=float(fractions.Fraction(numerator,math.comb(population,query)));assert math.isclose(float(tail),oracle,rel_tol=5e-14,abs_tol=0.0),(population,query,term,overlap,tail,oracle);exact+=1
            if oracle:maximum_relative=max(maximum_relative,abs(float(tail)-oracle)/oracle)
        odds={}
        for edition in modules:
            x,m,k,bg=map(float,[overlap,term,query,population]);value=((x+0.5)*(bg-m+0.5))/((m+0.5)*(k-x+0.5)) if edition=='1.0.4' else ((x+0.5)*(bg-m-k+x+0.5))/((m-x+0.5)*(k-x+0.5));odds[edition]=bits(value)
        tailrows.append(dict(counts=[population,query,term,overlap],p=bits(tail),odds=odds))
    corrections=[]
    families=[[],[float('nan')],[0.05],[0,0.05,1,float('nan')],[1,1,1],[0.01,0.04,0.04,0.9],[1e-300,0,0.5]]
    for j in range(20):families.append([rng.random() if i%7 else float('nan') for i in range(1+j)])
    for version,module in modules.items():
        for method in ['bonferroni','benjamini-hochberg']:
            for i,values in enumerate(families):
                for alpha in [0.0,0.05,1.0]:
                    q,reject=module.multiple_testing_correction(values,alpha=alpha,method=method);corrections.append(dict(edition=version,id=str(i),method=method,alpha=alpha,p=[bits(x) for x in values],q=[bits(x) for x in q],reject=[bits(x) for x in reject]))
    save(out/'cases.json',dict(cases=allcases,tails=tailrows,corrections=corrections))
    after=inventory_module.inventory(runtime,out/'runtime-after.progress.json');save(out/'runtime-after.json',after);assert before==after,'Reference runtime changed; no analysis admitted'
    assert all(pin(source/v/'wheel-source/gseapy/stats.py')==value for v,value in pins.items())
    save(out/'COMPLETED.json',dict(status='passed-original-four-edition-GSEApy-stats-reference-only',source_stats_pins=pins,source_profile=pin(source/'SOURCE.json'),producer=pin(Path(__file__).resolve()),cases=pin(out/'cases.json'),ora_cases=len(allcases),tail_cases=len(tailrows),correction_cases=len(corrections),independent_exact_small_tails=exact,maximum_scipy_vs_exact_relative_error=maximum_relative,original_warnings=[str(w.message) for w in captured],runtime=dict(python=sys.version,numpy=np.__version__,scipy=scipy.__version__,numpy_source=str(np.__file__),scipy_source=str(scipy.__file__)),runtime_baseline=pin(baseline),runtime_inventory_source=pin(inventory_source),complete_runtime_unchanged=True,runtime_records=len(after),high_level_adapter_admitted=False,implementation_credit=False,whole_goal_complete=False))
    print('Original four-edition stats:',len(allcases),'ORA cases,',len(tailrows),'tails,',len(corrections),'corrections;',exact,'exact rational-tail controls',flush=True)

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-source reference checks')
    main()
