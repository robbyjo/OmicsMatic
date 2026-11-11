# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create small synthetic native fragment requests without overwriting examples."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
DEST=ROOT/'examples/signac-fragments'
def save(name,value):(DEST/name).write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    assert not DEST.exists();DEST.mkdir()
    controls=json.loads((BASE/'fragments-original-4/cases.json').read_bytes());case=next(v for v in controls['matrix_cases'] if v['id']=='random-discover-False-2000')
    (DEST/'fragments.tsv').write_bytes((BASE/'fragments-original-4/random.tsv').read_bytes())
    (DEST/'regions.tsv').write_text('chromosome\tstart\tend\n'+''.join(f"{v['chromosome']}\t{v['start']}\t{v['end']}\n" for v in case['regions']),encoding='utf8',newline='\n')
    cells=['A','B','C','D','Z'];(DEST/'object-cells.tsv').write_text('\n'.join(cells)+'\n',encoding='utf8',newline='\n');(DEST/'cell-map.tsv').write_text('cell\tbarcode\n'+''.join(v+'\t'+v+'\n' for v in cells),encoding='utf8',newline='\n')
    save('feature-request.json',dict(schema_version=1,mode='FEATURE_MATRIX',sources=[dict(path='fragments.tsv')],regions='regions.tsv',genome_build='synthetic-not-a-reference-genome',keep_all_features=False,process_n=2000,sort_records=16,out='../../build/signac-fragments-feature-demo-NEW'))
    save('count-request.json',dict(schema_version=1,mode='COUNT_FRAGMENTS',files=['fragments.tsv'],max_lines=0,out='../../build/signac-fragments-count-demo-NEW'))
    save('qc-request.json',dict(schema_version=1,mode='NUCLEOSOME_SIGNAL',sources=[dict(path='fragments.tsv',map_file='cell-map.tsv')],object_cells_file='object-cells.tsv',max_lines=0,out='../../build/signac-fragments-qc-demo-NEW'))
    save('binary-request.json',dict(schema_version=1,mode='BINARIZE_CSC',matrix='../../build/signac-fragments-feature-demo-NEW/matrix.mtx',features='../../build/signac-fragments-feature-demo-NEW/features.tsv',cells_file='../../build/signac-fragments-feature-demo-NEW/cells.tsv',out='../../build/signac-fragments-binary-demo-NEW'))
    save('verify-request.json',dict(schema_version=1,mode='VERIFY',analysis='../../build/signac-fragments-feature-demo-NEW'))
    save('lsi-request.json',dict(schema_version=1,mode='FIT',options={'counts':'../../build/signac-fragments-feature-demo-NEW/matrix.mtx','features':'../../build/signac-fragments-feature-demo-NEW/features.tsv','cells':'../../build/signac-fragments-feature-demo-NEW/cells.tsv','out':'../../build/signac-fragments-lsi-demo-NEW','rscript':'C:/Program Files/R/R-4.6.1/bin/Rscript.exe','r-library':'../../build/literature-review/signac-runtime/reference-setup-1/R-lib','r-base-library':'../../build/omics-sctransform-r','profile':'1.17.1-package-rspectra','components':2,'seed':4901}))
    print('Created synthetic Signac fragment examples')
if __name__=='__main__':main()
