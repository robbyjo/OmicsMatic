# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Write a NEW synthetic Monocle3 request using a completed private reference setup."""
import argparse
import json
from pathlib import Path
import sys


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--setup',type=Path,required=True,help='Completed reference-setup-3 directory; installs nothing')
    p.add_argument('--rscript',type=Path,required=True)
    p.add_argument('--request',type=Path,required=True,help='NEW request JSON; keep this outer path ASCII on Windows')
    p.add_argument('--out',type=Path,required=True,help='NEW analysis output directory')
    a=p.parse_args();setup=a.setup.resolve();request=a.request.resolve();out=a.out.resolve();inputs=request.parent/'monocle3-synthetic-inputs'
    try:
        proof=json.loads((setup/'RESULT.json').read_bytes());started=json.loads((setup/'STARTED.json').read_bytes())
        if proof['status']!='INSTALLED_ORIGINAL_MONOCLE3_REFERENCE_ONLY' or proof['exact_Monocle3']!='1.4.27':raise ValueError('Setup is not the recorded original 1.4.27 profile')
        libraries=[str(setup/'R-lib'),*started['libraries']]
        if not a.rscript.is_file() or any(not Path(n).is_dir() for n in libraries):raise ValueError('Declared Rscript or private R library is unavailable')
    except (OSError,ValueError,KeyError) as failure:
        print('SKIP3: synthetic analysis skipped; required runtime setup is unavailable:',failure,file=sys.stderr)
        print('REQUIRED ACTION: provision the exact Windows R 4.6.1 / SOURCE-PROFILE.json private dependency profile only where permitted. Otherwise skip the analysis. This helper installs nothing.',file=sys.stderr)
        return 3
    if request.exists() or out.exists() or inputs.exists():
        print('FAIL2: choose NEW request, analysis output and request-parent/monocle3-synthetic-inputs paths; nothing overwritten.',file=sys.stderr);return 2
    request.parent.mkdir(parents=True,exist_ok=True);inputs.mkdir()
    for name in ['counts.tsv','cell-metadata.tsv','gene-metadata.tsv']:(inputs/name).write_bytes((Path(__file__).parent/'inputs'/name).read_bytes())
    data=dict(schema_version=1,mode='RUN',version='1.4.27',count_units='raw-integer-counts',counts=str(inputs/'counts.tsv'),cell_metadata=str(inputs/'cell-metadata.tsv'),gene_metadata=str(inputs/'gene-metadata.tsv'),
        metadata_types={'progress':{'type':'numeric'},'batch':{'type':'factor','levels':['a','b']}},rscript=str(a.rscript.resolve()),r_libraries=libraries,out=str(out),timeout_seconds=300,
        analyses=[dict(id='trajectory',seed=20261107,trajectory=dict(preprocess=dict(method='PCA',num_dim=10,norm_method='log',pseudo_count=1,scaling=True),
            umap=dict(n_neighbors=15,min_dist=0.1,metric='cosine',fast_sgd=False),cluster=dict(method='leiden',k=15,resolution=0.01,partition_qval=0.05,weight=False),
            graph=dict(use_partition=False,close_loop=False,ncenter=12,minimal_branch_len=3,prune_graph=True,maxiter=10),root_cells=['cell-001']),
            fit_models=dict(family='quasipoisson',intercept=True,terms=[dict(column='progress'),dict(column='batch')]),
            graph_test=dict(neighbor_graph='principal_graph',k=25,alternative='greater'))])
    request.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
    print('Prepared synthetic request:',request);print('Next: java -Xmx1g -cp PATH_TO_DEVELOPMENT_JAR org.jlinalg.cli.Monocle3WorkflowCli --request "'+str(request)+'"')
    return 0


if __name__=='__main__':
    raise SystemExit(main())
