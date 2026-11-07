# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare typed native chromVAR requests from captured original count/annotation/background controls."""
import argparse,json
from pathlib import Path
from prepare_signac_scan_request import ROOT,BASE,save
def token(v):return str(v) if isinstance(v,str) else format(v,'.17g')
def prepare(dataset,case,profile,inputs,target):
    inputs.mkdir();counts=dataset['counts'];p=len(counts);c=len(counts[0]);peaks=['peak'+str(i+1) for i in range(p)];cells=['cell'+str(i+1) for i in range(c)];motifs=['motif'+str(i+1) for i in range(len(case['peak_sets']))];integer=all(float(v).is_integer() for row in counts for v in row);values=[(i+1,j+1,counts[i][j]) for j in range(c) for i in range(p) if counts[i][j]!=0]
    (inputs/'counts.mtx').write_text('%%MatrixMarket matrix coordinate '+('integer' if integer else 'real')+' general\n'+f'{p} {c} {len(values)}\n'+''.join(f'{i} {j} '+(str(int(v)) if integer else token(v))+'\n' for i,j,v in values),encoding='utf8',newline='\n')
    for name,ids in [('peaks.tsv',peaks),('cells.tsv',cells)]:(inputs/name).write_text('\n'.join(ids)+'\n',encoding='utf8',newline='\n')
    (inputs/'motifs.tsv').write_text('motif_id\tmotif_name\n'+''.join(m+'\tFactor '+m+'\n' for m in motifs),encoding='utf8',newline='\n');(inputs/'annotations.tsv').write_text('motif_id\tpeak_id\n'+''.join(motifs[i]+'\t'+peaks[v-1]+'\n' for i,indices in enumerate(case['peak_sets']) for v in indices),encoding='utf8',newline='\n')
    b=dataset['background'];iterations=len(b[0]);(inputs/'background.tsv').write_text('\t'.join(['peak_id']+['iteration_'+str(i+1) for i in range(iterations)])+'\n'+''.join('\t'.join([peaks[i],*map(str,row)])+'\n' for i,row in enumerate(b)),encoding='utf8',newline='\n')
    r=dict(schema_version=1,mode='DEVIATIONS',profile=profile,out=str(target),genome_build='synthetic-not-a-reference-genome',motif_collection='synthetic-labelled-peak-sets-not-a-database',background_provenance='supplied synthetic index controls; not GC/intensity matched',norm=case['norm'])
    for key,name in [('counts','counts.mtx'),('peak_features','peaks.tsv'),('cells','cells.tsv'),('motif_labels','motifs.tsv'),('annotation_peaks','annotations.tsv'),('background','background.tsv')]:r[key]=str(inputs/name)
    if case['group'] is not None:
        (inputs/'groups.tsv').write_text('cell_id\tgroup\n'+''.join(cell+'\t'+group+'\n' for cell,group in zip(cells,case['group'],strict=True)),encoding='utf8',newline='\n');(inputs/'group-levels.tsv').write_text(''.join(level+'\n' for level in case['group_levels']),encoding='utf8',newline='\n');r.update(groups=str(inputs/'groups.tsv'),group_levels=str(inputs/'group-levels.tsv'))
    if case['expectation'] is not None:
        (inputs/'expectation.tsv').write_text('peak_id\texpectation\n'+''.join(peak+'\t'+token(v)+'\n' for peak,v in zip(peaks,case['expectation'],strict=True)),encoding='utf8',newline='\n');r['expectation']=str(inputs/'expectation.tsv')
    return r
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();data=json.loads((BASE/'chromvar-deviations-original-3/cases.json').read_bytes());case=next(v for v in data['cases'] if v['id']=='baseline');save(out/'request.json',prepare(data['datasets'][case['dataset']],case,'1.34.1-package',out/'Unicode α inputs',out/'Unicode β analysis'));print('Prepared native chromVAR request')
if __name__=='__main__':main()
