# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Map independent extended controls to typed requests without changing source populations."""
import argparse,csv,json,shutil
from pathlib import Path
from setup_deseq2_reference import ROOT,save,pin
BASE=ROOT/'build/literature-review/signac-runtime'

def main():
    p=argparse.ArgumentParser();p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir();data=out/'input';data.mkdir();source=BASE/'differential-requests-2';reference=BASE/'differential-extended-reference-1/original'
    for f in ['counts.mtx','data.mtx','features.txt','cells.txt']:shutil.copyfile(source/'input'/f,data/f)
    shutil.copyfile(reference/'scale-data.mtx',data/'scale-data.mtx')
    for kind in ['base','partial','constant']:
        with (reference/(kind+'-metadata.tsv')).open(encoding='utf8',newline='') as f:rows=list(csv.reader(f,delimiter='\t'))
        assert rows[0]==['','group','depth','batch'];rows[0][0]='cell'
        with (data/(kind+'-metadata.tsv')).open('w',encoding='utf8',newline='') as f:csv.writer(f,delimiter='\t',lineterminator='\n').writerows(rows)
    cases=json.loads((reference/'RESULT.json').read_bytes())['cases'];prototype=json.loads((source/'pair_wilcox.json').read_bytes())
    for name,original in cases.items():
        c=original['settings'];r=dict(prototype);r.update(mode=c['mode'],out=str(out/(name+'-analysis')),test=c['test'],test_slot=c['slot'],fc_slot=c['fc.slot'],min_pct=c.get('min.pct',.1),logfc_threshold=c.get('logfc.threshold',.1),only_positive=c.get('only.pos',False),max_cells_per_group=c.get('max.cells','Inf'))
        for field,file in [('counts','counts.mtx'),('features','features.txt'),('cells','cells.txt'),('data','data.mtx')]:r[field]=str(data/file)
        r['metadata']=str(data/(c.get('metadata','base')+'-metadata.tsv'))
        if c['mode']=='ALL':r.pop('contrast',None);r['return_threshold']=c.get('return.thresh',1)
        latent=c.get('latent',[]);latent=[latent] if isinstance(latent,str) else latent
        if latent:r['covariates']=[dict(name=v,kind='factor',levels=['batch1','batch2']) if v=='batch' else dict(name=v,kind='numeric') for v in latent]
        if c['fc.slot']=='scale.data' or c['slot']=='scale.data':r['scale_data']=str(data/'scale-data.mtx')
        if c.get('features'):
            features=c['features'];features=[features] if isinstance(features,str) else features;(data/(name+'-features.txt')).write_text(''.join(x+'\n' for x in features),encoding='ascii');r['test_features']=str(data/(name+'-features.txt'))
        save(out/(name+'.json'),r)
    native=dict(prototype);native.update(out=str(out/'native_tfidf-analysis'),data_source='native_tfidf');native.pop('data')
    for key,file in [('counts','counts.mtx'),('features','features.txt'),('cells','cells.txt')]:native[key]=str(data/file)
    native['metadata']=str(data/'base-metadata.tsv');save(out/'native_tfidf.json',native)
    counts=json.loads((out/'pair_count_means.json').read_bytes());counts.update(out=str(out/'counts_mode-analysis'),data_source='counts');counts.pop('data');save(out/'counts_mode.json',counts)
    save(out/'INPUT-PROVENANCE.json',dict(status='prepared-eight-original-extended-and-two-native-layer-requests',reference=pin(reference/'RESULT.json'),inputs={p.name:pin(p) for p in data.iterdir()},source_populations_preserved=True));print('Prepared8 extended plus native-TFIDF/count-mode controls')

if __name__=='__main__':main()
