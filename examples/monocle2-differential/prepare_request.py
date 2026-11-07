# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Write a closed Linux Monocle2 request for the byte-pinned synthetic example."""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--rscript',type=Path,required=True);p.add_argument('--r-home',type=Path,required=True);p.add_argument('--r-library',type=Path,action='append',required=True)
    p.add_argument('--out',type=Path,required=True);p.add_argument('--request',type=Path,required=True);a=p.parse_args()
    here=Path(__file__).resolve().parent;provenance=json.loads((here/'provenance.json').read_bytes())
    for n,v in provenance['inputs'].items():
        raw=(here/n).read_bytes()
        if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=v:raise ValueError('Example input differs: '+n)
    out=a.out.resolve();request=a.request.resolve()
    if out.exists() or request.exists():raise ValueError('Use NEW output and request paths; preserve earlier attempts')
    model=lambda *names:dict(intercept=True,terms=[dict(column=n) for n in names])
    r=dict(schema_version=1,mode='DIFF',version='2.26.0',matrix_format='dense-tsv',expression_units='raw-integer-counts',expression=str(here/'counts.tsv'),cell_metadata=str(here/'cells.tsv'),gene_metadata=str(here/'genes.tsv'),out=str(out),rscript=str(a.rscript.resolve()),r_home=str(a.r_home.resolve()),r_libraries=[str(p.resolve()) for p in a.r_library],
        metadata_types=dict(progress=dict(type='numeric'),progress_copy=dict(type='numeric'),condition=dict(type='factor',levels=['control','case']),sex=dict(type='factor',levels=['female','male']),batch=dict(type='factor',levels=['a','b','c','d'])),
        analyses=[dict(id='nb-fixed-relative',seed=20261107,family='negbinomial.size',size=8,lower_detection_limit=0.1,estimate_size_factors=True,dispersion=dict(estimate=False),relative_expr=True,full_model=model('condition','progress','sex'),reduced_model=model('progress','sex'))])
    with request.open('x',encoding='utf8',newline='\n') as stream:json.dump(r,stream,ensure_ascii=False,indent=2);stream.write('\n')
    print(request)


if __name__=='__main__':main()
