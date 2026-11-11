# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare typed requests from the independent synthetic original-test fixture."""
import argparse,csv,json,shutil
from decimal import Decimal
from pathlib import Path
from setup_deseq2_reference import ROOT,save,pin
BASE=ROOT/'build/literature-review/signac-runtime'

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();original=BASE/'differential-original-reference-3/original';data=out/'input';data.mkdir()
    lines=(original/'counts.mtx').read_text().splitlines();body=[l for l in lines if l and not l.startswith('%')];dim=body[0].split();assert len(dim)==3;genes,cells,entries=map(int,dim);assert (genes,cells)==(120,36) and len(body)==entries+1
    rows=[]
    for line in body[1:]:
        r,c,v=line.split();x=Decimal(v);assert x>=0 and x==x.to_integral_value() and x<=2**53-1;rows.append(f'{r} {c} {int(x)}')
    # Explicit synthetic-fixture encoding, not rounding user estimates.
    (data/'counts.mtx').write_text('%%MatrixMarket matrix coordinate integer general\n'+body[0]+'\n'+'\n'.join(rows)+'\n',encoding='ascii',newline='\n');shutil.copyfile(original/'tfidf.mtx',data/'data.mtx')
    (data/'features.txt').write_text(''.join(f'f{i}\n' for i in range(1,genes+1)),encoding='ascii');(data/'cells.txt').write_text(''.join(f'c{i}\n' for i in range(1,cells+1)),encoding='ascii')
    with (original/'metadata.tsv').open(encoding='utf8',newline='') as src,(data/'metadata.tsv').open('w',encoding='utf8',newline='') as dest:
        table=list(csv.reader(src,delimiter='\t'));assert table[0]==['','group','depth','batch'];table[0][0]='cell';csv.writer(dest,delimiter='\t',lineterminator='\n').writerows(table)
    cases=json.loads((original/'RESULT.json').read_bytes())['cases']
    for name,reference in cases.items():
        c=reference['settings'];r=dict(schema_version=1,mode=c['mode'],profile='Seurat-5.6.0-R4.6.1-base',out=str(out/(name+'-analysis')),counts='input/counts.mtx',features='input/features.txt',cells='input/cells.txt',metadata='input/metadata.tsv',group_column='group',group_levels=['a','b','c'],data_source='provided',data='input/data.mtx',test=c['test'],test_slot=c['slot'],fc_slot='data',min_pct=c['min.pct'],logfc_threshold=c['logfc.threshold'],only_positive=c.get('only.pos',False),seed=17,rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_libraries=[str(BASE/'reference-setup-1/R-lib'),str(ROOT/'build/omics-sctransform-r')],timeout_seconds=180,maximum_output_bytes=100000000)
        if c['mode']=='PAIR':r['contrast']=dict(numerator=['a'],denominator=['b'])
        else:r['return_threshold']=1
        latent=c.get('latent',[]);latent=[latent] if isinstance(latent,str) else latent
        if latent:r['covariates']=[dict(name=v,kind='factor',levels=['batch1','batch2']) if v=='batch' else dict(name=v,kind='numeric') for v in latent]
        save(out/(name+'.json'),r)
    save(out/'INPUT-PROVENANCE.json',dict(status='prepared-independent-synthetic-original-reference-requests',source_matrices=pin(original/'original-matrices.rds'),counts_source=pin(original/'counts.mtx'),counts_encoded=pin(data/'counts.mtx'),counts_conversion='Checked integer-valued rpois synthetic counts, encoded as declared integer Matrix Market; no rounding.',data_source=pin(original/'tfidf.mtx'),data_encoded=pin(data/'data.mtx'),metadata_header='Explicit fixture schema: blank source row-name header becomes cell; all values/order preserved.',requests=list(cases)))
    print('Prepared six exact original-case requests and preserved synthetic input provenance')

if __name__=='__main__':main()
