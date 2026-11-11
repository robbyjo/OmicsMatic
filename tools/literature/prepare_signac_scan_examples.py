# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare complete synthetic FASTA/PFM/count examples with explicit scanner dependencies."""
import json
from pathlib import Path
from prepare_signac_scan_request import prepare,save,BASE,ROOT

TARGET=ROOT/'examples/signac-scan'

def main():
    assert not TARGET.exists();TARGET.mkdir()
    data=json.loads((BASE/'scan-original-7/cases.json').read_bytes())
    case=next(c for c in data['cases'] if c['id']=='matched')
    r=prepare(data,case,'1.17.1-package',TARGET/'inputs',ROOT/'build/signac-scan-effective-demo-NEW')
    for field in ['counts','peak_features','cells','peak_ranges','fasta','pfm','query']:r[field]='inputs/'+Path(r[field]).name
    for field in ['r_motif_library','r_signac_library','r_base_library']:r[field]='../../'+Path(r[field]).relative_to(ROOT).as_posix()
    r['out']='../../build/signac-scan-effective-demo-NEW';save(TARGET/'request.json',r)
    save(TARGET/'legacy-request.json',dict(r,profile='1.6.0-source',out='../../build/signac-scan-effective-legacy-demo-NEW'))
    save(TARGET/'all-background-request.json',dict(r,background=None,out='../../build/signac-scan-effective-all-demo-NEW'))
    save(TARGET/'verify-request.json',dict(schema_version=1,mode='VERIFY',analysis='../../build/signac-scan-effective-demo-NEW'))
    (TARGET/'.gitattributes').write_text('*.json -text\n*.md -text\ninputs/* -text\n',encoding='utf8',newline='\n')
    print('Prepared three literal scan/enrichment requests and one VERIFY request')

if __name__=='__main__':main()
