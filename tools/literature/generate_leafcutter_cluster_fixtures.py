# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create literal source-clustering controls, including thresholds, order, source exclusions and int64-exact counts. No analysis credit."""
import argparse,json,random
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def bed(a,b,n,chrom='chr1',strand='+'):
    return '\t'.join(map(str,[chrom,a-8,b+7,'literal-junction',n,strand,a-8,b+7,0,2,'8,8','0,'+str(b-a+7)]))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);cases=[]
    def add(name,rows,minimum=3,ratio=.001,length=100000,check=True,ids=None):
        ids=ids or ['sample'+str(i+1) for i in range(len(rows))];cases.append(dict(id=name,options=dict(maximum_intron_length=length,minimum_reads=minimum,minimum_ratio=ratio,check_chromosomes=check),limits=dict(maximum_samples=1000,maximum_input_rows=100000,maximum_distinct_introns=100000,maximum_work=10000000,maximum_output_cells=1000000),samples=[dict(id=i,lines=r) for i,r in zip(ids,rows)]))
    add('basic-two-samples',[[bed(100,200,30),bed(100,300,20),bed(200,300,10)],[bed(100,200,5),bed(100,300,25)]])
    add('lexical-coordinate-order',[[bed(20,500,10),bed(100,500,10)],[bed(20,500,20),bed(100,500,20)]])
    add('first-seen-axis-order',[[bed(100,200,20,'chr2','-'),bed(100,300,20,'chr2','-'),bed(100,200,20),bed(100,300,20),bed(100,200,20,'X'),bed(100,300,20,'X')],[]])
    add('overlap-without-shared-splice-site',[[bed(100,300,30),bed(120,280,30)],[]])
    add('transitive-source-scan-order',[[bed(100,200,20),bed(150,250,20),bed(200,300,20),bed(250,350,20)],[bed(100,200,3)]])
    add('remove-bridge-and-recluster',[[bed(100,200,50),bed(200,300,4),bed(300,400,50),bed(400,500,50)],[]],ratio=.1)
    add('minimum-is-also-per-intron',[[bed(100,200,25),bed(100,300,25)],[]],minimum=50)
    add('pooled-minimum-before-refinement',[[bed(100,200,3),bed(100,300,3)],[]],minimum=7)
    add('inclusive-ratio-boundary',[[bed(100,200,3),bed(100,300,9)],[]],ratio=.25)
    add('pooled-three-read-threshold',[[bed(100,200,2),bed(100,300,1),bed(100,400,1)],[bed(100,200,1),bed(100,300,2),bed(100,400,1)]])
    add('duplicate-junction-rows',[[bed(100,200,2),bed(100,200,3),bed(100,300,10)],[bed(100,200,4),bed(100,300,4)]])
    add('zero-sample-denominators',[[bed(100,200,20),bed(100,300,30)],[],[]])
    extra='ALT:ignored\tBAD\tBAD\tignored\tBAD\t?\t0\t0\t0\t3\t8,8,8\t0,20,40'
    add('source-exclusions',[[bed(100,200,10),bed(100,300,10),bed(100,200,20,'chr1','?'),bed(100,200,100,'chrMT'),bed(100,300,100,'chrMT'),extra],[]])
    add('explicit-noncanonical-contigs',[[bed(100,200,10,'contig_α'),bed(100,300,10,'contig_α')],[bed(100,200,5,'contig_α')]],check=False,ids=['S.alpha','S.β'])
    add('maximum-intron-length-boundary',[[bed(100,200,10),bed(100,199,10),bed(100,201,100)],[]],length=100)
    add('strand-groups-and-literal-dot',[[bed(100,200,10,strand=s) for s in ['-','+','.']]+[bed(100,300,10,strand=s) for s in ['-','+','.']],[]])
    add('below-three-empty-groups',[[bed(100,200,1),bed(100,300,1)],[]])
    add('constitutive-singletons-are-not-emitted',[[bed(100,200,100),bed(300,400,100)],[]])
    add('zero-counts-empty',[[bed(100,200,0),bed(100,300,0)],[]])
    add('one-sample-source-merge-path',[[bed(100,200,20),bed(100,300,40)]])
    add('counts-above-float64-exact-integer',[[bed(100,200,9007199254740993),bed(100,300,9007199254740995)],[bed(100,200,3),bed(100,300,5)]],ratio=.25)
    add('minimum-zero-still-needs-three-pooled',[[bed(100,200,3),bed(100,300,3),bed(100,400,2)],[]],minimum=0,ratio=0)
    add('ratio-one-empty',[[bed(100,200,100),bed(100,300,3)],[]],ratio=1)
    add('source-current-defaults',[[bed(100,200,29),bed(100,300,30),bed(100,400,31)],[]],minimum=30)
    rng=random.Random(20261108)
    for index in range(64):
        rows=[[] for _ in range(1+index%4)];sites=[100+10*i for i in range(12)]
        for sample in rows:
            for _ in range(rng.randrange(8,65)):
                left,right=sorted(rng.sample(sites,2));sample.append(bed(left,right,rng.choice([0,1,2,3,4,5,9,10,20,30,50,100]),rng.choice(['chr2','chr1','X','alt_scaffold']),rng.choice(['+','-','?'])))
        add('seeded-graph-'+str(index+1).zfill(2),rows,minimum=rng.choice([0,3,5,10,30,50]),ratio=rng.choice([0,.001,.01,.1,.25,.5]),length=rng.choice([20,50,100,100000]),check=index%3!=0)
    save(out/'cases.json',dict(schema_version=1,cases=cases));save(out/'COMPLETED.json',dict(status='PASS_LITERAL_LEAFCUTTER_CLUSTER_FIXTURES_ONLY',case_count=len(cases),source_pin=pin(Path(__file__)),source_record_count=sum(len(s['lines']) for c in cases for s in c['samples']),implementation_credit=False,whole_goal_complete=False));print('Wrote',len(cases),'literal cases; no original/native analysis executed')

if __name__=='__main__':main()
