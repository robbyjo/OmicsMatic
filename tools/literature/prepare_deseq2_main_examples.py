# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare current main-command examples without editing frozen standalone examples."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def main():
    target=ROOT/'examples/deseq2-tools';assert not target.exists();target.mkdir()
    for name in ['paired','shrinkage','lrt','normalize','frozen-means']:
        folder='deseq2-inference' if name in ['paired','shrinkage','lrt'] else 'deseq2-normalization'
        request=json.loads((ROOT/'examples'/folder/(name+'.json')).read_bytes())
        for key in ['counts','samples','keep_genes','controls','geometric_means']:
            if key in request:request[key]='../'+folder+'/'+request[key]
        request['out']='../../build/deseq2-tools-example-'+name
        (target/(name+'.json')).write_text(json.dumps(request,indent=2,ensure_ascii=True)+'\n',encoding='utf8',newline='\n')
        if folder=='deseq2-inference':
            (target/('verify-'+name+'.json')).write_text(json.dumps(dict(schema_version=1,mode='VERIFY',analysis=request['out']),indent=2)+'\n',encoding='utf8',newline='\n')
    print('Prepared three main original-runtime and two native examples; frozen examples unchanged')

if __name__=='__main__':main()
