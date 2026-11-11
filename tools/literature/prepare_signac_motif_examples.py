# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare literal Java-only motif enrichment requests from original source controls."""
import json
from pathlib import Path
from validate_signac_motif_workflow import prepare
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
TARGET=ROOT/'examples/signac-motifs'

def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def main():
    assert not TARGET.exists();TARGET.mkdir();data=json.loads((BASE/'motif-original-2/cases.json').read_bytes());original=json.loads((BASE/'motif-original-2/original.json').read_bytes());reference=next(r for r in original['results'] if r['id']=='explicit-BH' and r['profile']=='1.17.1-package');request=prepare(data,reference,TARGET/'inputs',ROOT/'build/signac-motifs-demo-NEW')
    for field in ['motif_matrix','peak_features','motif_labels','query','background']:request[field]='inputs/'+Path(request[field]).name
    request['out']='../../build/signac-motifs-demo-NEW';save(TARGET/'request.json',request);legacy=dict(request,profile='1.6.0-source',out='../../build/signac-motifs-legacy-demo-NEW');save(TARGET/'legacy-request.json',legacy);all_background=dict(request,background=None,out='../../build/signac-motifs-all-demo-NEW');save(TARGET/'all-background-request.json',all_background);save(TARGET/'verify-request.json',dict(schema_version=1,mode='VERIFY',analysis='../../build/signac-motifs-demo-NEW'));(TARGET/'.gitattributes').write_text('*.json -text\n*.md -text\ninputs/* -text\n',encoding='utf8',newline='\n');print('Created three native motif enrichment requests and one VERIFY request')

if __name__=='__main__':main()
