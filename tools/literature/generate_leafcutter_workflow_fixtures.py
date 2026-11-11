# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Literal full-program controls with explicit sample/metadata order, source thresholds, covariates, endpoint annotations, precision and missing-PCA states."""
import argparse,gzip,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/leafcutter-runtime') and not out.exists();out.mkdir(parents=True);(out/'inputs').mkdir();inputs=out/'inputs';cases=[];base=ROOT/'build/literature-review/leafcutter-runtime';samples=['S'+str(12-i) for i in range(12)]
    def file(name,text):path=inputs/name;path.write_text(text,encoding='utf8',newline='\n');return linux(path)
    groups=file('baseline-groups.txt',''.join(s+' '+('baseline' if i<6 else 'treated')+'\n' for i,s in enumerate(samples)))
    covariates=file('reordered-covariate-groups.txt',''.join(samples[i]+' '+('2' if i<6 else '10')+' '+str(29+i%3)+' '+('batchA' if i%2==0 else 'batchB')+'\n' for i in reversed(range(12))))
    def ds(path,threads=1,exons=None):
        result=dict(groups=path,maximum_cluster_size=3,minimum_samples_per_intron=5,minimum_samples_per_group=4,minimum_coverage=20,cluster_timeout_seconds=60,threads=threads,initialization='smart',seed=4711,sample_selection_policy='groups-file-order')
        if exons is not None:result['exons']=exons
        return result
    bed=[]
    for i,sample in enumerate(samples):
        lines=[]
        for j,(chrom,start,end) in enumerate([('chr1',100,200),('chr1',120,200),('chr2',300,400),('chr2',300,420)]):
            reads=(10+i%3 if j%2==0 else 20-i%3) if i<6 else (20-i%3 if j%2==0 else 10+i%3);left=start-10;right=end+9;lines.append(f'{chrom}\t{left}\t{right}\tread-{j}\t{reads}\t+\t{left}\t{right}\t0\t2\t10,10\t0,{right-left-10}')
        if i==0:lines.append(lines[0].replace('\t10\t+','\t2\t+'))
        bed.append(dict(sample_id=sample,junctions=file(sample+'.junc','\n'.join(lines)+'\n')))
    exons=file('literal-exons.txt','chr start end strand gene_name\nchr1 0 100 + geneA\nchr1 200 300 + geneA\nchr2 250 300 - geneB\nchr2 420 450 + geneC\n')
    cases.append(dict(id='complete-junction-phenotype-differential',kind='pipeline',samples=bed,cluster=dict(maximum_intron_length=100000,minimum_reads=3,minimum_ratio=.001,check_chromosomes=True),phenotype=dict(pcs=2,seed=4711,blacklist=['X','Y']),differential=ds(groups,exons=exons)))
    old=json.loads((base/'model-original-2/cases.json').read_bytes())['differential'][0];ids=old['introns'];rows=old['counts']
    counts=file('literal-counts.txt',' '.join(samples)+'\n'+''.join(name+' '+' '.join(map(str,row))+'\n' for name,row in zip(ids,rows)))
    cases.append(dict(id='differential-source-dispositions',kind='differential',counts=counts,differential=ds(groups)))
    extra=file('literal-counts-extra-sample.txt',' '.join([*samples,'S-extra'])+'\n'+''.join(name.removeprefix('chr')+' '+' '.join(map(str,[*row,35]))+'\n' for name,row in zip(ids,rows)))
    cases.append(dict(id='differential-covariate-order-and-omission',kind='differential',counts=extra,differential=ds(covariates,threads=2)))
    phenotypes=json.loads((base/'phenotype-fixtures-3/cases.json').read_bytes())['cases']
    for name in ['original-auto-randomized-pca','literal-blacklist-chromosomes','pseudocount-missing-and-axes']:
        original=next(c for c in phenotypes if c['id']==name);text='chrom '+' '.join(original['samples'])+'\n'+''.join(row['id']+' '+' '.join(str(n)+'/'+str(d) for n,d in row['counts'])+'\n' for row in original['rows']);path=inputs/(name+'.counts.gz');path.write_bytes(gzip.compress(text.encode('utf8'),mtime=0));cases.append(dict(id='phenotype-'+name,kind='phenotype',counts_ratio=linux(path),phenotype=dict(pcs=original['pcs'],seed=original['seed'],blacklist=original['blacklist'])))
    clusters=json.loads((base/'cluster-fixtures-1/cases.json').read_bytes())['cases'];large=next(c for c in clusters if any(f[4].isdigit() and int(f[4])>2**53 for sample in c['samples'] for line in sample['lines'] if len(f:=line.split())==12));selected=[large,next(c for c in clusters if any(len(f:=line.split())==12 and (f[9].isdigit() and int(f[9])>2 or f[5]=='?') for sample in c['samples'] for line in sample['lines']))]
    for i,original in enumerate(selected):
        sample_rows=[]
        for j,sample in enumerate(original['samples']):sample_rows.append(dict(sample_id=sample['id'],junctions=file('cluster-'+str(i)+'-'+str(j)+'.junc','\n'.join(sample['lines'])+'\n' if sample['lines'] else '')))
        cases.append(dict(id=['cluster-exact-large-counts','cluster-source-exclusions'][i],kind='cluster',samples=sample_rows,cluster=original['options']))
    request=dict(schema_version=1,mode='RUN',edition='source-2c9907ef66ad',runtime=linux(a.runtime),out=linux(out/'native-analysis-placeholder'),maximum_input_bytes=67108864,maximum_output_bytes=536870912,maximum_files=100000,timeout_seconds=600,maximum_audit_operations=100000000,analyses=cases);save(out/'request.json',request);save(out/'input-pins.json',{f.relative_to(out).as_posix():pin(f) for f in sorted(inputs.iterdir())});(out/'generator-at-attempt.py').write_bytes(Path(__file__).read_bytes());save(out/'COMPLETED.json',dict(status='PASS_LITERAL_FULL_LEAFCUTTER_WORKFLOW_INPUTS_ONLY',cases=len(cases),source_pin=pin(Path(__file__)),implementation_credit=False,whole_goal_complete=False));print('Prepared',len(cases),'complete literal workflow requests')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete input/source checks')
    main()
