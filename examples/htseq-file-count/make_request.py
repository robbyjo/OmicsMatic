# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create one explicit synthetic COUNT request; run this helper inside Linux/WSL."""
import argparse
import json
from pathlib import Path

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--runtime-home',required=True)
parser.add_argument('--version',required=True,choices=['0.5.3','0.6.0','0.11.1','0.13.5','2.1.2'])
parser.add_argument('--request',type=Path,required=True)
parser.add_argument('--run-output',type=Path,required=True)
args=parser.parse_args();data=Path(__file__).resolve().parent
if not args.runtime_home.startswith('/'):parser.error('COUNT requires an explicit absolute Linux private runtime home')
out=args.run_output.resolve();request=args.request.resolve()
if out.exists() or out.is_symlink():parser.error('Choose a NEW run-output path; existing evidence is preserved')
request.parent.mkdir(parents=True,exist_ok=True);out.parent.mkdir(parents=True,exist_ok=True)
flags='score' if args.version in ['0.5.3','0.6.0'] else 'ignore'
value=dict(schema_version=1,operation='COUNT',version=args.version,runtime_home=args.runtime_home,alignment=str(data/'reads.sam'),annotation=str(data/'features.gtf'),
    format='sam',order='name',stranded='yes',overlap='union',nonunique='none',secondary=flags,supplementary=flags,minimum_mapq=21,
    feature_type='exon',id_attribute='gene_id',genome_label='synthetic-chr1',annotation_label='two-example-exons-A-B',counting_unit='original-auto',out=str(out))
with request.open('x',encoding='utf8',newline='\n') as stream:json.dump(value,stream,indent=2);stream.write('\n')
print('Created explicit synthetic COUNT request: '+str(request))
