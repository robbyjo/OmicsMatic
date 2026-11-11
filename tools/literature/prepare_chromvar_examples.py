# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create complete literal native chromVAR examples with explicit synthetic backgrounds."""
import json
from pathlib import Path
from prepare_chromvar_request import prepare,ROOT,BASE,save
TARGET=ROOT/'examples/chromvar-deviations'
def main():
    if TARGET.exists():assert {p.name for p in TARGET.iterdir()}=={'README.md'},'Preserve existing example inputs and requests'
    else:TARGET.mkdir()
    data=json.loads((BASE/'chromvar-deviations-original-3/cases.json').read_bytes());case=next(v for v in data['cases'] if v['id']=='baseline');r=prepare(data['datasets'][case['dataset']],case,'1.34.1-package',TARGET/'inputs',ROOT/'build/chromvar-deviations-demo-NEW')
    for key in ['counts','peak_features','cells','motif_labels','annotation_peaks','background']:r[key]='inputs/'+Path(r[key]).name
    r['out']='../../build/chromvar-deviations-demo-NEW';save(TARGET/'request.json',r);save(TARGET/'legacy-request.json',dict(r,profile='1.16.0-source',out='../../build/chromvar-deviations-legacy-demo-NEW'))
    cells=(TARGET/'inputs/cells.tsv').read_text(encoding='utf8').splitlines();(TARGET/'inputs/groups.tsv').write_text('cell_id\tgroup\n'+''.join(cell+'\t'+('a' if i<3 else 'b')+'\n' for i,cell in enumerate(cells)),encoding='utf8',newline='\n');(TARGET/'inputs/group-levels.tsv').write_text('a\nb\n',encoding='utf8',newline='\n');save(TARGET/'grouped-request.json',dict(r,groups='inputs/groups.tsv',group_levels='inputs/group-levels.tsv',out='../../build/chromvar-deviations-grouped-demo-NEW'));save(TARGET/'verify-request.json',dict(schema_version=1,mode='VERIFY',analysis=r['out']));(TARGET/'.gitattributes').write_text('*.json -text\n*.md -text\ninputs/* -text\n',encoding='utf8',newline='\n');print('Prepared three native chromVAR requests and a literal VERIFY request')
if __name__=='__main__':main()
