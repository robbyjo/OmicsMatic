# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently audit original STREME Fisher fields with exact rational tails; Java admission pending."""
import argparse
from decimal import Decimal
from fractions import Fraction
import hashlib
import json
from math import comb,log10
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[2]


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def inside(token,point,digits):
    value=Decimal(token)
    step=Decimal(10)**(value.copy_abs().adjusted()-digits+1) if value else Decimal(0)
    return abs(Decimal(str(point))-value)<=step/2


def tsv(path,motif_ids):
    text=path.read_text(encoding='utf-8');assert text.endswith('\n')
    lines=text.splitlines();header=lines[0].split('\t');rows=[];blocks=[[]];separators=[]
    for index,line in enumerate(lines[1:],start=2):
        if not line:continue
        if line=='#':
            assert blocks[-1];blocks.append([]);separators.append(index);continue
        assert not line.startswith('#');fields=line.split('\t');assert len(fields)==len(header)
        row=dict(zip(header,fields));rows.append(row);blocks[-1].append(row)
    if not motif_ids:assert blocks==[[]];blocks=[]
    assert len(blocks)==len(motif_ids) and all({row['motif_ID'] for row in group}=={identity} for group,identity in zip(blocks,motif_ids))
    return rows,dict(motif_blocks=[dict(id=identity,rows=len(group)) for identity,group in zip(motif_ids,blocks)],separator_lines=separators)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--control',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    args.control=args.control.resolve();out=args.out.resolve();assert out.is_relative_to((ROOT/'build/literature-review/meme-runtime').resolve()) and not out.exists();out.mkdir(parents=True)
    original=json.loads((args.control/'COMPLETED.json').read_bytes());assert original['families']==original['successful_processes']==16
    families=[];sources={}
    for name in ['streme-supplied-control','streme-shuffled-control']:
        folder=args.control/'captured'/name/'result'
        for filename in ['streme.xml','sequences.tsv','sites.tsv']:
            path=folder/filename;sources[path.relative_to(ROOT).as_posix()]=pin(path)
        tree=ET.parse(folder/'streme.xml').getroot();model=tree.find('model');assert model.findtext('test')=='Fisher Exact Test'
        motif_ids=[m.attrib['id'] for m in tree.findall('./motifs/motif')]
        sequences,sequence_structure=tsv(folder/'sequences.tsv',motif_ids)
        sites,site_structure=tsv(folder/'sites.tsv',motif_ids)
        motif_rows=[]
        for motif in tree.findall('./motifs/motif'):
            identity=motif.attrib['id'];numeric=[]
            for subset,xml_prefix in [('train','train'),('test','test')]:
                positive=int(model.find(subset+'_positives').attrib['count']);negative=int(model.find(subset+'_negatives').attrib['count'])
                a=int(motif.attrib[xml_prefix+'_pos_count']);b=int(motif.attrib[xml_prefix+'_neg_count']);total=a+b
                assert 0<=a<=positive and 0<=b<=negative
                exact=sum((Fraction(comb(positive,k)*comb(negative,total-k),comb(positive+negative,total))
                           for k in range(max(a,0,total-negative),min(positive,total)+1)),Fraction(0))
                log=log10(exact.numerator)-log10(exact.denominator)
                ptext=motif.attrib[xml_prefix+'_pvalue'];logtext=motif.attrib[xml_prefix+'_log_pvalue']
                assert inside(ptext,float(exact),2),(name,identity,subset,ptext,float(exact))
                assert inside(logtext,log,6),(name,identity,subset,logtext,log)
                groups={}
                for row in sequences:
                    if row['motif_ID']!=identity:continue
                    key=row['seq_Class']+'|'+row['is_holdout?'];groups[key]=groups.get(key,0)+1
                numeric.append(dict(subset=subset,positive_sequences=positive,negative_sequences=negative,positive_pass=a,negative_pass=b,
                                    exact_numerator=str(exact.numerator),exact_denominator=str(exact.denominator),independent_p=float(exact),independent_log10=log,
                                    source_p=ptext,source_log10=logtext,reported_precision_consistent=True,sequence_class_census=groups))
            motif_rows.append(dict(id=identity,statistics=numeric,site_rows=sum(row['motif_ID']==identity for row in sites)))
        families.append(dict(name=name,sequence_rows=len(sequences),site_rows=len(sites),sequence_structure=sequence_structure,site_structure=site_structure,motifs=motif_rows,
                             reverse_site_example=next((row for row in sites if row['site_Strand']=='-'),None)))
    assert sum(len(m['statistics']) for f in families for m in f['motifs'])==8
    result=dict(status='passed-eight-independent-exact-rational-STREME-Fisher-output-checks',families=families,source_pins=sources,
                script_sha256=pin(Path(__file__))['sha256'],source_complete_control=pin(args.control/'COMPLETED.json'),
                scope='Independent exact fixed-margin one-sided right tails on the complete retained original training/held-out counts. Original decimal/log10 fields are checked at their source printed precision. Sequence/site censuses remain exploratory; this is not a Java model/site/score/output-family or runtime admission.',
                scientific_runtime_admitted=False,Java_adapter_available=False,implementation_credit=False)
    (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps(result,indent=2))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original statistic identity checks')
    main()
