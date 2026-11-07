# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Write an explicit Windows Cicero request for the byte-pinned synthetic example; install nothing."""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference-setup',type=Path,required=True);p.add_argument('--r-home',type=Path,required=True);p.add_argument('--rscript',type=Path);p.add_argument('--request',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();here=Path(__file__).resolve().parent
    provenance=json.loads((here/'provenance.json').read_bytes())
    for name,wanted in provenance['inputs'].items():
        raw=(here/name).read_bytes()
        if dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())!=wanted:raise ValueError('Example input differs: '+name)
    setup=a.reference_setup.resolve();result=json.loads((setup/'RESULT.json').read_bytes());started=json.loads((setup/'STARTED.json').read_bytes())
    if result['status']!='INSTALLED_ORIGINAL_CICERO_REFERENCE_ONLY' or not result['preexisting_libraries_unchanged']:raise ValueError('Declared setup has no clean original namespace readiness receipt; skip dependent analysis')
    libraries=[setup/'R-lib',*[Path(p) for p in started['libraries']]];request=a.request.resolve();out=a.out.resolve()
    if request.exists() or out.exists():raise ValueError('Use NEW request and output paths; preserve previous attempts')
    r=dict(schema_version=1,mode='COACCESS',version='1.3.9',matrix_format='dense-tsv',coordinate_system='one-based-closed',count_units='raw-integer-counts',counts=str(here/'counts.tsv'),peak_metadata=str(here/'peaks.tsv'),cell_metadata=str(here/'cells.tsv'),embedding=str(here/'embedding.tsv'),genome=str(here/'genome.tsv'),numeric_metadata=[],binarize=False,out=str(out),r_home=str(a.r_home.resolve()),rscript=str((a.rscript or a.r_home/'bin/Rscript.exe').resolve()),r_libraries=list(map(str,libraries)),transport_chunk_entries=1000,
        analyses=[dict(id='coaccess',aggregation_seed=20261108,k=20,size_factor_normalize=True,summary_stats=[],model=dict(window=500000,power=0.75,max_elements=200),distance_parameter=dict(mode='estimate',seed=20261109,maxit=100,sample_num=6,distance_constraint=250000,convergence=1e-22,max_sample_windows=500,require_full_sample=True,allow_maxit_warning=False),skipped_windows_policy='retain-source-dispositions',unresolved_connections_policy='retain-source-NA')])
    with request.open('x',encoding='utf8',newline='\n') as stream:json.dump(r,stream,ensure_ascii=False,indent=2);stream.write('\n')
    print(request)


if __name__=='__main__':main()
