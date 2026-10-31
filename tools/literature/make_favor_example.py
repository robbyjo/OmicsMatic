#!/usr/bin/env python3
"""Create a protected synthetic FAVOR request;never execute analysis or install software."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys

from prepare_favor_runtime import ASSETS,Parser,Unavailable,read_policy,sha,strict_json

ROOT=Path(__file__).resolve().parents[2]
FIXTURE=ROOT/'src/main/resources/org/jlinalg/omics/favor-health'
ACTION=('Required action: use Python inside Linux/WSL and a freshly prepared same-host '
        'inventory with its exact SHA256. Choose a NEW request file and NEW ASCII analysis '
        'output below canonical existing parents. Missing/forbidden runtime prerequisites '
        'SKIP3;invalid input/hash/output fails2. This generator performs no analysis,network '
        'request or software installation.')

def input_file(path):
    path=Path(path).resolve(strict=True)
    return dict(path=str(path),sha256=sha(path))

def generate(inventory,expected,request_file,analysis_output):
    if not isinstance(expected,str) or len(expected)!=64 or any(c not in '0123456789abcdef' for c in expected):raise ValueError('Require exact lowercase inventory SHA256')
    inventory=Path(os.path.abspath(inventory))
    if not inventory.is_file() or inventory.resolve()!=inventory:raise Unavailable('Inventory is missing,linked or forbidden')
    if sha(inventory)!=expected:raise ValueError('Protected inventory SHA256 differs')
    data=strict_json(inventory)
    if data.get('schema_version')!=1 or data.get('status')!='inventory-only-not-admitted' or data.get('profile')!='favorannotator-csv-r4.4.3-linux-x86_64':raise ValueError('Unsupported or incomplete runtime inventory')
    policy=read_policy()
    if data.get('build_lock_sha256')!=policy['build_lock_sha256'] or data.get('GenomeInfoDbData_archive_sha256')!=policy['GenomeInfoDbData_archive_sha256']:raise ValueError('Inventory differs from the pinned package/data policy')
    request_file=Path(os.path.abspath(request_file));analysis_output=Path(os.path.abspath(analysis_output))
    for path in [request_file,analysis_output]:
        if path.exists() or path.is_symlink():raise ValueError('Refuse existing request/analysis output: '+str(path))
        if not path.parent.is_dir() or path.parent.resolve()!=path.parent:raise ValueError('Use an existing canonical output parent')
    if not str(analysis_output).isascii() or any(c.isspace() for c in str(analysis_output)):raise ValueError('Original FAVOR analysis work/output must be ASCII without whitespace')
    if request_file==analysis_output or request_file.is_relative_to(analysis_output) or analysis_output.is_relative_to(request_file):raise ValueError('Request and analysis output must be separate paths')
    provenance=strict_json(FIXTURE/'provenance.json')
    if set(provenance['resource_sha256'])!={'input.vcf','splits.csv','chr22_1.csv','chr22_2.csv'}:raise ValueError('Incomplete fixed synthetic fixture')
    for name,pin in provenance['resource_sha256'].items():
        if sha(FIXTURE/name)!=pin:raise ValueError('Synthetic fixture SHA256 differs: '+name)
    for name,pin in policy['original_source_sha256'].items():
        if sha(ASSETS/'upstream'/name)!=pin:raise Unavailable('Bundled original source changed: '+name)
    request=dict(schema_version=1,runtime=dict(inventory=dict(path=str(inventory),sha256=expected),source_root=str(ASSETS/'upstream')),vcf=input_file(FIXTURE/'input.vcf'),build='synthetic-no-biological-reference',chromosome=22,mode='DIPLOID_GT',chromosome_policy='EXACT',blank_records='IGNORE',reference=dict(id='fixed-synthetic-example',build='synthetic-no-biological-reference',split=input_file(FIXTURE/'splits.csv'),shards=[input_file(FIXTURE/'chr22_1.csv'),input_file(FIXTURE/'chr22_2.csv')]),output=str(analysis_output))
    raw=(json.dumps(request,indent=2,ensure_ascii=False,allow_nan=False)+'\n').encode('utf-8')
    with request_file.open('xb') as stream:stream.write(raw)
    return dict(status='synthetic-request-created-not-executed',request_file=str(request_file),request_sha256=hashlib.sha256(raw).hexdigest(),analysis_output=str(analysis_output),expected_rows=8,expected_scalar_values=48,scope='Artificial variants,samples,scores and gene labels only. No real biological build or study result is represented;fresh Java runtime health remains mandatory.')

def main(argv=None):
    parser=Parser(description=__doc__,epilog=ACTION);parser.add_argument('--inventory',required=True);parser.add_argument('--inventory-sha256',required=True);parser.add_argument('--out',required=True,help='NEW request JSON file');parser.add_argument('--analysis-out',required=True,help='NEW original-workflow analysis directory')
    options=parser.parse_args(argv)
    try:
        if not sys.platform.startswith('linux'):raise Unavailable('Create and run this Linux runtime request using Python inside Linux/WSL')
        report=generate(options.inventory,options.inventory_sha256,options.out,options.analysis_out);print(json.dumps(report,indent=2,ensure_ascii=False));return 0
    except Exception as failure:
        code=3 if isinstance(failure,(Unavailable,OSError)) else 2
        print('FAVOR EXAMPLE '+('SKIPPED' if code==3 else 'FAILED')+' (exit'+str(code)+'): '+str(failure)+'\n'+ACTION,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
