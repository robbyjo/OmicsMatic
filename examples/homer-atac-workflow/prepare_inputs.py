#!/usr/bin/env python3
"""Prepare independent synthetic paired ATAC examples; never reads paper or patient data."""
import argparse
import hashlib
import json
from pathlib import Path


def prepare(out):
    out=Path(out).absolute()
    if out.exists():raise ValueError('Input output exists; choose a NEW folder')
    out.mkdir(parents=True);sequence='ACGT'*9;reference='chr1\t2000000\nchr2\t1000000\nchrUnused\t30000\n';(out/'reference.tsv').write_text(reference);replicates=[]
    for replicate in range(2):
        rows=[]
        def pair(name,contig,position,template=100,soft=False,quality=60,secondary=False):
            aligned=34 if soft else 36;mate=position+template-aligned
            for flag,start,next_position,tlen,cigar in [(99,position,mate,template,'2S34M' if soft else '36M'),(147,mate,position,-template,'34M2S' if soft else '36M')]:
                if secondary:flag|=256
                rows.append('\t'.join(map(str,[name,flag,contig,start,quality,cigar,'=',next_position,tlen,sequence,'I'*36])))
        for contig,base in [('chr1',18000),('chr1',52000),('chr2',32000)]:
            for i in range(120):
                position=base+(3 if i<4 else i);template=100 if i%2==0 else 356;pair(f'R{replicate}-{contig}-{base}-α-{i}',contig,position,template,i%17==0)
        for contig,start,count,step in [('chr1',1000000,400,500),('chr2',500000,300,700)]:
            for i in range(count):pair(f'R{replicate}-{contig}-background-{i}',contig,start+i*step,80 if i%2==0 else 120)
        pair(f'R{replicate}-low-MAPQ','chr1',1400000,quality=29);pair(f'R{replicate}-secondary','chr2',900000,secondary=True)
        assert len(rows)==2124;path=out/f'replicate-{replicate}-α.sam';path.write_text('@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:2000000\n@SQ\tSN:chr2\tLN:1000000\n'+'\n'.join(rows)+'\n',encoding='utf-8');replicates.append(path)
    (out/'replicates.tsv').write_text('replicate_id\tsam_path\n'+'\n'.join(f'Replicate {i} α\t{p.name}' for i,p in enumerate(replicates))+'\n',encoding='utf-8');(out/'single.tsv').write_text('replicate_id\tsam_path\nOnly α\t'+replicates[0].name+'\n',encoding='utf-8')
    cases=[dict(name='default-region-length100',manifest='replicates.tsv',peak_style='original-default',local_fold='0',clonal_fold='0',tag_cap='0',length_normalization='100',endpoints='extend',input_records=4248,eligible_weight=2120,replicates=2),dict(name='atac-explicit-filters',manifest='replicates.tsv',peak_style='atac',local_fold='4',clonal_fold='2',tag_cap='auto',length_normalization='0',endpoints='original-cap',input_records=4248,eligible_weight=2120,replicates=2),dict(name='histone-single-replicate',manifest='single.tsv',peak_style='histone',local_fold='0',clonal_fold='0',tag_cap='0',length_normalization='100',endpoints='extend',input_records=2124,eligible_weight=1060,replicates=1)]
    record=dict(status='prepared-synthetic-ATAC-inputs',reference_build='synthetic-ATAC-example-v1',reference_provenance='Independent synthetic two-chromosome paired fixture;unused third dictionary contig;no paper or patient data',effective_genome_size=3000000,cases=cases,sha256={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in out.iterdir() if p.is_file()},scope='Three clusters,paired background,two excluded low-MAPQ/secondary pairs,soft matching blocks,fullTLEN100/356 signed-byte-comparator aliases and Unicode source identities. Fixture filter-off choices are technical tests,not reported paper settings or recommended biological defaults.');(out/'inputs.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n');return record


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();result=prepare(args.out);print(json.dumps({k:v for k,v in result.items() if k not in ['cases','sha256']},ensure_ascii=False))
