# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create literal paired-matrix LinkPeaks examples from the immutable original controls."""
import json
from pathlib import Path
from prepare_signac_link_probe import prepare
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
TARGET=ROOT/'examples/signac-links'

def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')

def main():
    assert not TARGET.exists(),'Preserve earlier examples'
    TARGET.mkdir();data=json.loads((BASE/'links-original-4/cases.json').read_bytes())
    controls=[('request.json','pearson-6-1','1.17.1-package','signac-links-demo-NEW'),('historical-request.json','spearman-10-0.05','1.14.0-source','signac-links-historical-demo-NEW'),('empty-request.json','zero-p-cutoff','1.17.1-package','signac-links-empty-demo-NEW')]
    fields={'counts':'counts.mtx','peak_features':'peak-features.tsv','cells':'cells.tsv','expression':'expression.mtx','gene_features':'gene-features.tsv','expression_cells':'cells.tsv','peak_ranges':'peaks.tsv','gene_coords':'genes.tsv','peak_metadata':'metadata.tsv'}
    for name,case_id,profile,destination in controls:
        case=next(c for c in data['cases'] if c['id']==case_id);assert case['dataset']=='base' and not case['precomputed_count']
        if name=='request.json':
            request=prepare(data,case,profile,TARGET/'inputs',ROOT/'build'/destination)
        else:
            request=json.loads((TARGET/'request.json').read_bytes())
            request['profile']=profile
            for key in ['method','distance','min_distance','min_cells','n_sample','pvalue_cutoff','score_cutoff','gene_id','seed']:request[key]=case[key]
        request.update({key:'inputs/'+file for key,file in fields.items()});request['out']='../../build/'+destination
        request['r_library']='../../build/literature-review/signac-runtime/reference-setup-1/R-lib';request['r_base_library']='../../build/omics-sctransform-r'
        save(TARGET/name,request)
    save(TARGET/'verify-request.json',dict(schema_version=1,mode='VERIFY',analysis='../../build/signac-links-demo-NEW'))
    (TARGET/'.gitattributes').write_text('*.json -text\n*.md -text\ninputs/* -text\n',encoding='utf8',newline='\n')
    print('Created three literal LinkPeaks FIT requests and one VERIFY request')

if __name__=='__main__':main()
