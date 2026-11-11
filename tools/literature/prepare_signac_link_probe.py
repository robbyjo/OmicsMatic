# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare typed paired-matrix inputs from the immutable synthetic original control dataset."""
import argparse
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def matrix(path,rows,stored=False,integer=False):
    values=[(row+1,col+1,value) for col in range(len(rows[0])) for row,values in enumerate(rows) for value in [values[col]] if stored or value!=0]
    path.write_text('%%MatrixMarket matrix coordinate '+('integer' if integer else 'real')+' general\n'+f'{len(rows)} {len(rows[0])} {len(values)}\n'+''.join(f'{row} {col} {value}\n' for row,col,value in values),encoding='utf8',newline='\n')
def prepare(data,case,profile,out,target):
    d=data['datasets'][case['dataset']];out.mkdir();names=[f"{r['chromosome']}-{r['start']}-{r['end']}" for r in d['peaks']];gene_names=[g['gene_id'] if case['gene_id'] else g['gene_name'] for g in d['genes']]
    matrix(out/'counts.mtx',d['counts'],integer=True);matrix(out/'expression.mtx',d['expression'],bool(d.get('keep_stored_zeros')))
    for name,values in [('peak-features.tsv',names),('gene-features.tsv',gene_names),('cells.tsv',d['cells'])]:(out/name).write_text('\n'.join(values)+'\n',encoding='utf8',newline='\n')
    (out/'peaks.tsv').write_text('chromosome\tstart\tend\n'+''.join(f"{r['chromosome']}\t{r['start']}\t{r['end']}\n" for r in d['peaks']),encoding='utf8',newline='\n');(out/'genes.tsv').write_text('chromosome\tstart\tend\tstrand\tgene_id\tgene_name\n'+''.join(f"{g['chromosome']}\t{g['start']}\t{g['end']}\t{g['strand']}\t{g['gene_id']}\t{g['gene_name']}\n" for g in d['genes']),encoding='utf8',newline='\n')
    (out/'metadata.tsv').write_text('peak_id\tGC.percent\tsequence.length\tcount\n'+''.join(f"{name}\t{m['gc'] if m['gc'] is not None else 'NA'}\t{m['length']}\t{sum(row) if case['precomputed_count'] else ''}\n" for name,m,row in zip(names,d['meta'],d['counts'],strict=True)),encoding='utf8',newline='\n')
    if d.get('missing_metadata'):(out/'metadata.tsv').write_text('peak_id\tsequence.length\tcount\n'+''.join(f"{name}\t{m['length']}\t\n" for name,m in zip(names,d['meta'],strict=True)),encoding='utf8',newline='\n')
    result=dict(schema_version=1,mode='FIT',profile=profile,out=str(target),genome_build='synthetic-not-a-reference-genome',rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_library=str(BASE/'reference-setup-1/R-lib'),r_base_library=str(ROOT/'build/omics-sctransform-r'))
    for field,name in [('counts','counts.mtx'),('peak_features','peak-features.tsv'),('cells','cells.tsv'),('expression','expression.mtx'),('gene_features','gene-features.tsv'),('expression_cells','cells.tsv'),('peak_ranges','peaks.tsv'),('gene_coords','genes.tsv'),('peak_metadata','metadata.tsv')]:result[field]=str(out/name)
    for key in ['method','distance','min_distance','min_cells','n_sample','pvalue_cutoff','score_cutoff','gene_id','seed']:result[key]=case[key]
    if case['genes_use'] is not None:result['genes_use']=case['genes_use']
    return result
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();data=json.loads((BASE/'links-original-4/cases.json').read_bytes());case=next(c for c in data['cases'] if c['id']=='pearson-6-1');out.mkdir();request=prepare(data,case,'1.17.1-package',out/'inputs',out/'analysis');save(out/'request.json',request);print('Prepared typed LinkPeaks synthetic probe')
if __name__=='__main__':main()
