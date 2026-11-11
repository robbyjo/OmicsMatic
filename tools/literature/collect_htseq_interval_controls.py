# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect actual GenomicArrayOfSets step geometry and documented assignment modes."""
import argparse
import hashlib
import json
import random
import subprocess
from pathlib import Path
from setup_htseq_reference import ROOT,BASE,DISTRO,linux,pin


def iv(chrom,start,end,strand='+'):
    # Strings preserve complete signed64 coordinate integers through JSON clients.
    return dict(chromosome=chrom,start=str(start),end=str(end),strand=strand)


def populations():
    common=[dict(interval=iv('chr1',0,12),feature='gene-A'),dict(interval=iv('chr1',7,18),feature='gene-B'),
        dict(interval=iv('chr1',20,26),feature='gene-A'),dict(interval=iv('chr1',5,15,'-'),feature='reverse'),
        dict(interval=iv('chr1',9,14),feature='gene-A'),dict(interval=iv('chr2',0,20),feature='gene-A')]
    queries=[dict(id='q'+str(i),blocks=blocks) for i,blocks in enumerate([
        [iv('chr1',0,5)],[iv('chr1',5,10)],[iv('chr1',10,17)],[iv('chr1',12,20)],
        [iv('chr1',18,22)],[iv('chr1',0,26)],[iv('chr1',11,13),iv('chr1',20,23)],
        [iv('chr1',10,10)],[iv('missing',0,20)],[iv('chr1',0,20,'-')],
        [iv('chr1',0,5),iv('chr2',0,5)],[iv('chr1',0,5),iv('missing',0,5)],[],
        [iv('1',0,20)],[iv('chr1',9,13),iv('chr1',9,13)],
    ])]
    cases=[dict(id='hand-stranded',stranded=True,features=common,queries=queries),
        dict(id='hand-unstranded',stranded=False,features=common,queries=queries),
        dict(id='empty-index',stranded=False,features=[],queries=queries)]
    for stranded in [False,True]:
        features=[dict(interval=iv('chr1',i,i+7,'+' if i%2 else '-'),feature=name)
            for i,name in enumerate(['gene-α','gene-😀','gene-\ue000','gene-α','', '__no_feature'])]
        cases.append(dict(id='unicode-and-zero-'+stranded.__str__(),stranded=stranded,features=features,
            queries=[dict(id='forward',blocks=[iv('chr1',0,20)]),dict(id='reverse',blocks=[iv('chr1',0,20,'-')])]))
    cases.append(dict(id='zero-length-feature',stranded=False,
        features=[dict(interval=iv('chr1',4,4),feature='zero-feature')],queries=[dict(id='query',blocks=[iv('chr1',0,20)])]))
    big=9007199254741000
    cases.append(dict(id='above-double-integer-range',stranded=True,
        features=[dict(interval=iv('large',big,big+100),feature='exact-64bit'),dict(interval=iv('large',big+40,big+80),feature='nested')],
        queries=[dict(id='large'+str(i),blocks=[iv('large',big+i,big+i+20)]) for i in [0,30,60,90,100]]))
    rng=random.Random(732421)
    for number in range(36):
        stranded=bool(number%2)
        features=[]
        for i in range(8+number%19):
            start=rng.randrange(0,90)
            features.append(dict(interval=iv(rng.choice(['chr1','chr2']),start,start+rng.randrange(0,26),rng.choice(['+','-'])),feature='gene-'+str(rng.randrange(0,9))))
        queries=[]
        for i in range(24):
            blocks=[]
            for j in range(rng.randrange(0,4)):
                start=rng.randrange(0,110)
                blocks.append(iv(rng.choice(['chr1','chr2','absent']),start,start+rng.randrange(0,21),rng.choice(['+','-'])))
            queries.append(dict(id='q'+str(i),blocks=blocks))
        cases.append(dict(id='random-'+str(number).zfill(2),stranded=stranded,features=features,queries=queries))
    return cases


COLLECTOR='''import HTSeq,json,sys
from pathlib import Path
def interval(row):return HTSeq.GenomicInterval(row['chromosome'],int(row['start']),int(row['end']),row['strand'])
def geometry(value):return dict(chromosome=value.chrom,start=str(value.start),end=str(value.end),strand=value.strand)
cases=json.loads(Path(sys.argv[1]).read_text())
for case in cases:
 array=HTSeq.GenomicArrayOfSets('auto',stranded=case['stranded'])
 try:
  for feature in case['features']:array[interval(feature['interval'])]+=feature['feature']
 except Exception as error:
  case['insertion_error']=dict(error_type=type(error).__name__,error=str(error));continue
 for query in case['queries']:
  steps=[];sets=[]
  try:
   for block in query['blocks']:
    current=[]
    for position,values in array[interval(block)].steps():
     current.append(dict(interval=geometry(position),features=sorted(values)));sets.append(set(values))
    steps.append(current)
  except Exception as error:
   query['query_error']=dict(error_type=type(error).__name__,error=str(error));continue
  result={}
  for mode in ['UNION','INTERSECTION_STRICT','INTERSECTION_NONEMPTY']:
   aggregate=None
   for value in sets:
    if mode=='INTERSECTION_NONEMPTY' and not value:continue
    if aggregate is None:aggregate=set(value)
    elif mode=='UNION':aggregate.update(value)
    else:aggregate.intersection_update(value)
   result[mode]=sorted(aggregate) if aggregate is not None else []
  query['original_steps']=steps;query['documented_mode_assignments_from_original_steps']=result
errors=[]
for name,feature,query,stranded in [
 ('negative-query',dict(interval=dict(chromosome='chr1',start='0',end='10',strand='+'),feature='g'),dict(chromosome='chr1',start='-1',end='5',strand='+'),True),
 ('dot-strand-stranded',None,dict(chromosome='chr1',start='0',end='5',strand='.'),True),
 ('negative-feature',dict(interval=dict(chromosome='chr1',start='-10',end='2',strand='+'),feature='g'),dict(chromosome='chr1',start='0',end='5',strand='+'),True)]:
 try:
  a=HTSeq.GenomicArrayOfSets('auto',stranded=stranded)
  if feature:a[interval(feature['interval'])]+=feature['feature']
  values=[dict(interval=geometry(p),features=sorted(v)) for p,v in a[interval(query)].steps()]
  errors.append(dict(id=name,accepted=True,steps=values))
 except Exception as error:errors.append(dict(id=name,accepted=False,error_type=type(error).__name__,error=str(error)))
Path(sys.argv[2]).write_text(json.dumps(dict(schema_version=1,HTSeq=HTSeq.__version__,cases=cases,domain_probes=errors,
 source_scope='Actual original GenomicArrayOfSets step output. Set resolution implements the documented modes over those original steps; this is not an original htseq-count SAM/GTF run.'),ensure_ascii=False,indent=2)+'\\n',encoding='utf8')
'''


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    out=parser.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir()
    recovery=BASE/'reference-inventory-recovery-2'
    runtime=json.loads((recovery/'COMPLETED.json').read_bytes())
    assert runtime['status']=='recovered-private-exact-HTSeq-reference-inventory'
    cases=populations()
    (out/'input.json').write_text(json.dumps(cases,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
    (out/'collector.py').write_text(COLLECTOR,encoding='utf8',newline='\n')
    argv=['wsl.exe','-d',DISTRO,'--',runtime['python'],'-I',linux(out/'collector.py'),linux(out/'input.json'),linux(out/'RESULT.json')]
    result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120)
    (out/'collector.stdout').write_bytes(result.stdout)
    (out/'collector.stderr').write_bytes(result.stderr)
    (out/'process.json').write_text(json.dumps(dict(command=argv,exit_code=result.returncode),indent=2)+'\n',encoding='utf8',newline='\n')
    assert result.returncode==0,'Original HTSeq collector failed; preserve attempted source and logs'
    reference=json.loads((out/'RESULT.json').read_bytes())
    assert reference['HTSeq']=='2.1.2' and len(reference['cases'])==len(cases)
    report=dict(schema_version=1,status='collected-original-HTSeq-interval-step-controls',
        source=pin(Path(__file__).read_bytes()),runtime_recovery=pin((recovery/'COMPLETED.json').read_bytes()),
        result=pin((out/'RESULT.json').read_bytes()),populations=len(cases),
        queries=sum(len(c['queries']) for c in cases),actual_steps=sum(len(s) for c in reference['cases'] for q in c['queries'] for s in q.get('original_steps',[])),
        insertion_refusals=sum('insertion_error' in c for c in reference['cases']),
        query_refusals=sum('query_error' in q for c in reference['cases'] for q in c['queries']),
        original_htseq_count_executed=False,whole_package_reproduced=False,implementation_credit=False)
    (out/'COMPLETED.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf8',newline='\n')
    print(json.dumps(report))


if __name__=='__main__':
    if not __debug__:
        raise RuntimeError('Never disable original reference checks')
    main()
