# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect unchanged original ChIPseeker nearest-feature component controls; install nothing."""
import argparse
import itertools
import json
import os
import random
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from setup_deseq2_reference import BASES,RSCRIPT,environment,pin,save


def row(contig,start,end,strand):return dict(contig=contig,start=start,end=end,strand=strand)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--sources',type=Path,required=True);parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args();out=args.out.resolve();source=args.sources.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir(parents=True)
    editions=[];paths=[Path(__file__),ROOT/'tools/literature/collect_chipseeker_nearest.R']
    for version in ['1.22.1','1.26.2','1.34.1','1.48.1']:
        folder=source/version/'selected-source/ChIPseeker/R';original=folder/'getNearestFeatureIndicesAndDistances.R';utilities=folder/'utilities.R'
        receipt=json.loads((source/version/'SOURCE.json').read_bytes())
        for path in [original,utilities]:assert pin(path)==receipt['source_members']['ChIPseeker/R/'+path.name]
        assert pin(original)['sha256']=='599e14b879a34de1581b8e576a7b572b2e23f99c414dab9d049c498442b630cf'
        editions.append(dict(version=version,nearest=original.as_posix(),utilities=utilities.as_posix()));paths.extend([original,utilities])
    bounds={p.relative_to(ROOT).as_posix():pin(p) for p in paths}
    for name in bounds:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    features=[row('chr1',100,180,'+'),row('chr1',220,300,'-'),row('chr1',100,160,'+'),
        row('chr1',400,500,'+'),row('chr1',450,550,'-'),row('chr2',50,100,'+'),row('chr2',200,250,'-')]
    peaks=[row('chr1',a,b,s) for a,b,s in [(1,5,'+'),(95,105,'+'),(130,150,'-'),(170,180,'*'),(195,205,'+'),
        (299,301,'-'),(320,380,'+'),(495,505,'-'),(545,555,'*'),(700,710,'+')]]+[row('chr2',95,110,'+'),row('missing',10,20,'*')]
    wildcard=[row('chr1',100,180,'*'),row('chr1',200,300,'-'),row('chr2',100,200,'+')]
    # All explicit flag combinations retain the source's precedence, including both directional flags.
    cases=[]
    rng=random.Random(20261107)
    random_features=[]
    for i in range(40):
        start=rng.randrange(1,4000);random_features.append(row('chr1' if i%3 else 'chr2',start,start+rng.randrange(1,250),'+' if i%2 else '-'))
    random_features.extend([row('chr1',100,180,'+'),row('chr1',100,200,'+')])
    random_peaks=[]
    for i in range(60):
        start=rng.randrange(1,4500);random_peaks.append(row('chr1' if i%3 else 'chr2',start,start+rng.randrange(0,350),'*'))
    random_peaks.append(row('missing',1,10,'*'))
    overflow_features=[*random_features,row('chr2',2147483600,2147483647,'-')]
    overflow_peaks=[*random_peaks[:-1],row('chr2',2147483640,2147483647,'*'),random_peaks[-1]]
    unstranded=[dict(p,strand='*') for p in peaks]
    for dataset,fs,ps in [('ordinary',features,peaks),('wildcard',wildcard,peaks),('unstranded',features,unstranded),('random-unstranded',random_features,random_peaks),('overflow-unstranded',overflow_features,overflow_peaks)]:
        for same,ignore,up,down,overlap in itertools.product([False,True],[False,True],[False,True],[False,True],['TSS','all']):
            options=dict(same_strand=same,ignore_overlap=ignore,ignore_upstream=up,ignore_downstream=down,overlap=overlap)
            cases.append(dict(id=dataset+'-'+''.join(str(int(v)) for v in [same,ignore,up,down])+'-'+overlap,features=fs,peaks=ps,options=options))
    options=dict(same_strand=False,ignore_overlap=False,ignore_upstream=False,ignore_downstream=False,overlap='TSS')
    for label,fs,ps in [('empty-peaks',features,[]),('empty-features',[],peaks),('reserved-seqlevel',[row('chrNA',10,20,'+')],peaks)]:
        cases.append(dict(id=label,features=fs,peaks=ps,options=options))
    save(out/'PLAN.json',dict(libraries=[p.as_posix() for p in BASES],editions=editions,cases=cases))
    command=[str(RSCRIPT),'--vanilla',str(ROOT/'tools/literature/collect_chipseeker_nearest.R'),str(out/'PLAN.json'),str(out/'ORIGINAL.json'),os.pathsep.join(map(str,BASES))]
    save(out/'STARTED.json',dict(command=command,source_pins=bounds,installation_performed=False,analysis_admitted=False))
    with (out/'stdout.log').open('xb') as a,(out/'stderr.log').open('xb') as b:
        r=subprocess.run(command,cwd=ROOT,env=environment(),stdin=subprocess.DEVNULL,stdout=a,stderr=b,timeout=600)
    save(out/'TERMINAL.json',dict(exit_code=r.returncode));assert r.returncode==0,'Read retained R stderr; component comparison skipped'
    result=json.loads((out/'ORIGINAL.json').read_bytes());rows=result['cases'];assert len(rows)==4*len(cases)
    assert all(pin(ROOT/name)==wanted for name,wanted in bounds.items())
    save(out/'COMPLETED.json',dict(status='captured-original-ChIPseeker-nearest-component-controls-not-Java-admission',versions=[p['version'] for p in editions],cases_per_version=len(cases),
        original_calls=len(rows),original_successes=sum(c['source_error'] is None for c in rows),original_failures=sum(c['source_error'] is not None for c in rows),
        original_result=pin(out/'ORIGINAL.json'),source_pins=bounds,dependency_versions=result['dependency_versions'],full_package_loaded=False,installation_performed=False,analysis_admitted=False))
    print(json.dumps(dict(calls=len(rows),successes=sum(c['source_error'] is None for c in rows),failures=sum(c['source_error'] is not None for c in rows))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original source bindings')
    main()
