# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create runnable synthetic native TSS and gene-activity requests in new example paths."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
DEST=ROOT/'examples/signac-annotations'
def save(name,value):(DEST/name).write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    assert not DEST.exists();DEST.mkdir();data=json.loads((BASE/'annotation-original-2/cases.json').read_bytes());tss=next(v for v in data['tss_cases'] if v['id']=='endpoints-True-2000');gene=next(v for v in data['gene_cases'] if v['id']=='default-2000-False')
    (DEST/'fragments.tsv').write_bytes((BASE/'annotation-original-2/endpoints.tsv').read_bytes());(DEST/'tss-cells.tsv').write_text('D\nB\nA\nC\nZ\n',encoding='utf8',newline='\n');(DEST/'gene-cells.tsv').write_text('D\nB\nA\nC\n',encoding='utf8',newline='\n')
    (DEST/'positions.tsv').write_text('chromosome\tstart\tend\tstrand\tsequence_length\n'+''.join(f"{r['chromosome']}\t{r['start']}\t{r['end']}\t{r['strand']}\t.\n" for r in tss['positions']),encoding='utf8',newline='\n');(DEST/'annotation.tsv').write_text('chromosome\tstart\tend\tstrand\tgene_id\tgene_name\tgene_biotype\n'+''.join(f"{r['chromosome']}\t{r['start']}\t{r['end']}\t{r['strand']}\t{r['gene_id']}\t{r['gene_name'] or ''}\t{r['gene_biotype']}\n" for r in gene['annotation']),encoding='utf8',newline='\n')
    for mode,name in [('TSS_FAST','fast'),('TSS_PROFILE','profile')]:save(name+'-request.json',dict(schema_version=1,mode=mode,sources=[dict(path='fragments.tsv')],object_cells_file='tss-cells.tsv',positions='positions.tsv',genome_build='synthetic-not-a-reference-genome',process_n=2000,region_extension=1000,out='../../build/signac-tss-'+name+'-demo-NEW'))
    save('derived-request.json',dict(schema_version=1,mode='TSS_FAST',sources=[dict(path='fragments.tsv')],object_cells_file='tss-cells.tsv',annotation='annotation.tsv',genome_build='synthetic-not-a-reference-genome',process_n=2000,region_extension=1000,out='../../build/signac-derived-tss-demo-NEW'))
    save('gene-request.json',dict(schema_version=1,mode='GENE_ACTIVITY',sources=[dict(path='fragments.tsv')],object_cells_file='gene-cells.tsv',annotation='annotation.tsv',genome_build='synthetic-not-a-reference-genome',process_n=2000,upstream=2000,downstream=0,biotypes=['protein_coding'],max_width=500000,gene_ids=False,sort_records=16,out='../../build/signac-gene-activity-demo-NEW'))
    save('verify-request.json',dict(schema_version=1,mode='VERIFY',analysis='../../build/signac-tss-fast-demo-NEW'))
    save('lsi-request.json',dict(schema_version=1,mode='FIT',options={'counts':'../../build/signac-gene-activity-demo-NEW/matrix.mtx','features':'../../build/signac-gene-activity-demo-NEW/features.tsv','cells':'../../build/signac-gene-activity-demo-NEW/cells.tsv','out':'../../build/signac-gene-LSI-demo-NEW','rscript':'C:/Program Files/R/R-4.6.1/bin/Rscript.exe','r-library':'../../build/literature-review/signac-runtime/reference-setup-1/R-lib','r-base-library':'../../build/omics-sctransform-r','profile':'1.17.1-package-rspectra','components':2,'seed':4901}))
    print('Created synthetic TSS and gene-activity examples')
if __name__=='__main__':main()
