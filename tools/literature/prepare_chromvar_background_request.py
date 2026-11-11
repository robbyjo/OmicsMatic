# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare explicit typed background requests from retained original numerical populations."""
import argparse,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];BASE=ROOT/'build/literature-review/signac-runtime'
def save(p,v):p.write_text(json.dumps(v,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def prepare(directory,analysis,dataset,case,profile,reference=False):
    assert not directory.exists() and not analysis.exists();directory.mkdir();inputs=directory/'Unicode α inputs';inputs.mkdir();counts=dataset['counts'];peaks=['peak-α-'+str(i+1) for i in range(len(counts))];cells=['cell-β-'+str(i+1) for i in range(len(counts[0]))];triples=[(i+1,j+1,v) for i,row in enumerate(counts) for j,v in enumerate(row) if v!=0]
    (inputs/'counts.mtx').write_text('%%MatrixMarket matrix coordinate real general\n'+f'{len(peaks)} {len(cells)} {len(triples)}\n'+''.join(f'{i} {j} {v}\n' for i,j,v in triples),encoding='utf8',newline='\n')
    for name,values in [('peaks.tsv',peaks),('cells.tsv',cells)]:(inputs/name).write_text(''.join(v+'\n' for v in values),encoding='utf8',newline='\n')
    request=dict(schema_version=1,mode='BACKGROUND',profile=profile,out=str(analysis),genome_build='synthetic-not-a-reference-genome',bias_provenance='synthetic original numerical control; not a biological motif analysis',counts=str(inputs/'counts.mtx'),peak_features=str(inputs/'peaks.tsv'),cells=str(inputs/'cells.tsv'),seed=case['seed'],niterations=case['niterations'],w=case['w'],bs=case['bs'],rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',r_motif_library=str(BASE/'motif-reference-setup-1/R-lib'),r_signac_library=str(BASE/'reference-setup-1/R-lib'),r_base_library=str(ROOT/'build/omics-sctransform-r'))
    if reference:
        sequence='';ranges=[]
        for i,peak in enumerate(peaks):
            gc=6+((i*13)%23);seq='GC'*(gc//2)+('G' if gc%2 else '')+'A'*(40-gc)+'NNNNN';start=len(sequence)+1;sequence+=seq;ranges.append((peak,'chr1',start,len(sequence)))
        (inputs/'reference.fa').write_text('>chr1\n'+'\n'.join(sequence[i:i+80] for i in range(0,len(sequence),80))+'\n',encoding='ascii',newline='\n');(inputs/'reference.fa.fai').write_text(f'chr1\t{len(sequence)}\t6\t80\t81\n',encoding='ascii',newline='\n')
        (inputs/'ranges.tsv').write_text('peak_id\tchromosome\tstart\tend\n'+''.join('\t'.join(map(str,r))+'\n' for r in ranges),encoding='utf8',newline='\n');request.update(fasta=str(inputs/'reference.fa'),fai=str(inputs/'reference.fa.fai'),peak_ranges=str(inputs/'ranges.tsv'),bias_provenance='native canonical-base GC of synthetic supplied reference; ambiguity excluded')
    else:
        (inputs/'bias.tsv').write_text('peak_id\tbias\n'+''.join(f'{peak}\t{bias}\n' for peak,bias in zip(peaks,dataset['bias'])),encoding='utf8',newline='\n');request['bias']=str(inputs/'bias.tsv')
    save(directory/'request.json',request);return request,peaks,cells
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--analysis',type=Path,required=True);parser.add_argument('--case',default='baseline');parser.add_argument('--profile',choices=['1.16.0-source','1.34.1-package'],default='1.34.1-package');parser.add_argument('--reference',action='store_true');a=parser.parse_args();data=json.loads((BASE/'background-original-controls-1/cases.json').read_bytes());case=next(v for v in data['cases'] if v['id']==a.case);prepare(a.out.resolve(),a.analysis.resolve(),data['datasets'][case['dataset']],case,a.profile,a.reference);print(str(a.out.resolve()/'request.json'))
if __name__=='__main__':main()
