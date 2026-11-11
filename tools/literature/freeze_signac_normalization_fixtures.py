# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package immutable original Signac normalization controls, retaining exact raw outputs and source."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
DEST=ROOT/'src/test/resources/org/jlinalg/sequence/signac-normalization'

def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())

def text(data):return (json.dumps(data,indent=2,ensure_ascii=False)+'\n').encode('utf-8')
def sequence(value):return value if isinstance(value,list) else [value]
def csv(values):return ','.join(map(str,values))
def number(value):
    assert not isinstance(value,str) or value in ['Inf','-Inf','NaN']
    return str(value)

def main():
    original=BASE/'normalization-original-2';completion=json.loads((original/'COMPLETED.json').read_bytes())
    assert completion['normalization_cases']==1152 and completion['selection_cases']==396
    assert all(pin(ROOT/n)==v for n,v in completion['source_pins'].items())
    assert pin(original/'cases.json')==completion['cases'] and pin(original/'original.json')==completion['original']
    assert not DEST.exists();DEST.mkdir(parents=True)
    inputs=json.loads((original/'cases.json').read_bytes());outputs=json.loads((original/'original.json').read_bytes())
    files={'cases.json':(original/'cases.json').read_bytes(),'original.json':(original/'original.json').read_bytes(),
           'original.stdout':(original/'original.stdout').read_bytes(),'original.stderr':(original/'original.stderr').read_bytes(),
           'original.process.json':(original/'original.process.json').read_bytes(),'COMPLETED.json':(original/'COMPLETED.json').read_bytes(),
           'PROVENANCE.md':b'Full original Signac1.17.1 on R4.6.1 / Matrix1.7.5 / SeuratObject5.4.0. Five extracted edition functions are NOT five historical package installations. Historical assay wrappers1.6/1.7/1.9 were skipped because their slot argument is defunct in the installed SeuratObject. Exact skip diagnostics are retained in original.json. Current whole-package normalization and metadata/selection controls succeeded. No LSI, fragments, QC, motifs, linkage, public CLI or biological validation is claimed.\n'}
    files['matrix-ids.tsv']=('\n'.join(c['id'] for c in inputs['matrices'])+'\n').encode()
    warnings={'Some cells contain 0 total counts':'ZERO_CELL_COUNTS','Some features contain 0 total counts':'ZERO_FEATURE_COUNTS'}
    for inp,out in zip(inputs['matrices'],outputs['matrices'],strict=True):
        assert inp['id']==out['id'];prefix=inp['id']+'/'
        files[prefix+'features.tsv']=('\n'.join(inp['features'])+'\n').encode('utf-8')
        files[prefix+'cells.tsv']=('\n'.join(inp['cells'])+'\n').encode('utf-8')
        for name,key in [('pointers.tsv','p'),('indices.tsv','i'),('values.tsv','x'),('idf.tsv','idf')]:files[prefix+name]=(csv(inp[key])+'\n').encode()
        meta=out['metadata']
        files[prefix+'metadata.tsv']=('\n'.join('\t'.join(map(str,v)) for v in zip(sequence(meta['features']),sequence(meta['count']),sequence(meta['percentile']),strict=True))+'\n').encode('utf-8')
        rows=[]
        for case in out['normalized']:
            matrix=case['matrix'];assert sequence(matrix['features'])==inp['features'] and sequence(matrix['cells'])==inp['cells']
            assert sequence(matrix['p'])==inp['p'] and sequence(matrix['i'])==inp['i']
            values=sequence(matrix['x']);assert len(values)==len(inp['x'])
            codes=[warnings[w] for w in sequence(case['warnings'])]
            if any(v in ['Inf','-Inf'] for v in values):codes.append('NONFINITE_NORMALIZED_VALUES')
            rows.append('\t'.join([str(case['method']),str(case['scale']),str(case['supplied']).lower(),csv(map(number,values)),csv(codes)]))
        files[prefix+'normalized.tsv']=('\n'.join(rows)+'\n').encode()
        files[prefix+'selections.tsv']=('\n'.join('\t'.join([c['kind'],str(c['value']),csv(sequence(c['existing'])),csv(sequence(c['selected']))]) for c in out['selections'])+'\n').encode('utf-8')
    for name in completion['source_pins']:files['source/'+name]=(ROOT/name).read_bytes()
    for version in ['1.6.0','1.7.0','1.9.0','1.14.0','1.17.1']:
        for name in ['DESCRIPTION','LICENSE']:
            files[f'upstream/{version}/{name}']=(BASE/f'source-intake-1/{version}/source/Signac'/name).read_bytes()
    files['upstream/MIT-LICENSE.txt']=('Copyright (c) 2021 Tim Stuart\n\nPermission is hereby granted, free of charge, to any person obtaining a copy\nof this software and associated documentation files (the "Software"), to deal\nin the Software without restriction, including without limitation the rights\nto use, copy, modify, merge, publish, distribute, sublicense, and/or sell\ncopies of the Software, and to permit persons to whom the Software is\nfurnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all\ncopies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR\nIMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,\nFITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE\nAUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER\nLIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,\nOUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE\nSOFTWARE.\n').encode()
    archive=DEST/'original-controls.zip'
    with zipfile.ZipFile(archive,'x') as target:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;target.writestr(info,data)
    capture=dict(schema_version=1,status=completion['status'],archive=pin(archive),members=len(files),source_pins=completion['source_pins'],
                 member_pins={n:dict(bytes=len(v),sha256=hashlib.sha256(v).hexdigest()) for n,v in files.items()},
                 matrices=36,normalization_cases=1152,selection_cases=396,historical_source_editions=5,historical_packages_installed=False,
                 historical_assay_wrapper_skips=['1.6.0','1.7.0','1.9.0'],public_workflow_admitted=False,implementation_credit=False)
    (DEST/'CAPTURE.json').write_bytes(text(capture));(DEST/'.gitattributes').write_text('*.zip -text\nCAPTURE.json -text\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(archive=pin(archive),members=len(files))))

if __name__=='__main__':main()
