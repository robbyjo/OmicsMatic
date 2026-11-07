# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare typed FASTA/PFM/count/query requests from the immutable original scanner controls."""
import argparse
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'

def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def prepare(data,case,profile,inputs,target):
    inputs.mkdir();d=data['datasets'][case['dataset']];peaks=[f"{p['chromosome']}-{p['start']}-{p['end']}" for p in d['peaks']];newline='\r\n' if d['crlf'] else '\n';(inputs/'reference.fa').write_bytes(''.join('>'+id+newline+sequence+newline for id,sequence in d['sequences'].items()).encode('utf8'))
    values=[(row+1,column+1,d['counts'][row][column]) for column in range(len(d['cells'])) for row in range(len(peaks)) if d['counts'][row][column]!=0];(inputs/'counts.mtx').write_text('%%MatrixMarket matrix coordinate integer general\n'+f"{len(peaks)} {len(d['cells'])} {len(values)}\n"+''.join(f'{i} {j} {value}\n' for i,j,value in values),encoding='utf8',newline='\n')
    (inputs/'peak-features.tsv').write_text('\n'.join(peaks)+'\n',encoding='utf8',newline='\n');(inputs/'cells.tsv').write_text('\n'.join(d['cells'])+'\n',encoding='utf8',newline='\n');(inputs/'peaks.tsv').write_text('chromosome\tstart\tend\n'+''.join(f"{r['chromosome']}\t{r['start']}\t{r['end']}\n" for r in d['peaks']),encoding='utf8',newline='\n')
    rows=['motif_id\tmotif_name\tposition\tA\tC\tG\tT\tbg_A\tbg_C\tbg_G\tbg_T']
    for model in d['pfms']:
        for i,row in enumerate(model['rows']):rows.append('\t'.join(map(str,[model['id'],model['name'],i+1,*row,*model['background']])))
    (inputs/'pfms.tsv').write_text('\n'.join(rows)+'\n',encoding='utf8',newline='\n');selected=lambda values:[peaks[v] if isinstance(v,int) else v for v in values];(inputs/'query.tsv').write_text(''.join(v+'\n' for v in selected(case['query'])),encoding='utf8',newline='\n')
    request=dict(schema_version=1,mode='FIT',profile=profile,scanner_profile='1.34.0-package',out=str(target),genome_build='synthetic-not-a-reference-genome',motif_collection='synthetic-original-scanner-controls-not-JASPAR',rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_motif_library=str(BASE/'motif-reference-setup-1/R-lib'),r_signac_library=str(BASE/'reference-setup-1/R-lib'),r_base_library=str(ROOT/'build/omics-sctransform-r'),features_match=case['features_match'],p_adjust_method=case['adjustment'],seed=case['seed'])
    for key,name in [('counts','counts.mtx'),('peak_features','peak-features.tsv'),('cells','cells.tsv'),('peak_ranges','peaks.tsv'),('fasta','reference.fa'),('pfm','pfms.tsv'),('query','query.tsv')]:request[key]=str(inputs/name)
    if isinstance(case['background'],list):(inputs/'background.tsv').write_text(''.join(v+'\n' for v in selected(case['background'])),encoding='utf8',newline='\n');request['background']=str(inputs/'background.tsv')
    else:request['background']=case['background']
    return request

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();data=json.loads((BASE/'scan-original-7/cases.json').read_bytes());case=next(c for c in data['cases'] if c['id']=='matched');save(out/'request.json',prepare(data,case,'1.17.1-package',out/'Unicode α inputs',out/'Unicode β analysis'));print('Prepared original FASTA/PFM scan/enrichment request')

if __name__=='__main__':main()
