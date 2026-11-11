# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze exact original count/quantification/QC/binarization references as portable native tests."""
import gzip
import hashlib
import json
from pathlib import Path
import zipfile
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
DEST=ROOT/'src/test/resources/org/jlinalg/sequence/signac-fragments'
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def seq(value):return [] if value is None else value if isinstance(value,list) else [value]
def lines(rows):return ('\n'.join('\t'.join(map(str,row)) for row in rows)+'\n').encode('utf8')
def csv(values):return ','.join(map(str,values))
def main():
    assert not DEST.exists();original=BASE/'fragments-original-4';record=json.loads((original/'COMPLETED.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in record['source_pins'].items()) and pin(original/'original.json')==record['original']
    binary=BASE/'binarize-original-2';binary_record=json.loads((binary/'COMPLETED.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in binary_record['source_pins'].items()) and pin(binary/'original.json')==binary_record['original']
    inputs=json.loads((original/'cases.json').read_bytes());output=json.loads((original/'original.json').read_bytes());files={}
    for name in ['cases.json','original.json','rounding.tsv','original-fround.c','original.stdout','original.stderr','original.process.json','COMPLETED.json']:files['original/'+name]=(original/name).read_bytes()
    for name in ['cases.json','original.json','original.stdout','original.stderr','original.process.json','COMPLETED.json']:files['binarization/'+name]=(binary/name).read_bytes()
    for file in inputs['files']:
        name=file['id']
        for suffix in ['.tsv','.tsv.gz','.tsv.gz.tbi']:files['input/'+name+suffix]=(original/(name+suffix)).read_bytes()
        files['input/'+name+'.ordinary.gz']=gzip.compress(files['input/'+name+'.tsv'],mtime=0)
    for case,result in zip(inputs['count_cases'],output['counts'],strict=True):
        assert case['id']==result['id'];prefix='count/'+case['id']+'/'
        files[prefix+'settings.tsv']=lines([[csv(case['files']),'NULL' if case['cells'] is None else csv(case['cells']),case['lines']]])
        files[prefix+'expected.tsv']=lines([[r[k] for k in ['CB','frequency_count','mononucleosomal','nucleosome_free','reads_count']] for r in result['result']['value']])
    for case,result in zip(inputs['matrix_cases'],output['quantification'],strict=True):
        assert case['id']==result['id'];prefix='matrix/'+case['id']+'/'
        files[prefix+'sources.tsv']=lines([[s['file'],'NULL' if s['map'] is None else csv(m['cell']+'='+m['barcode'] for m in s['map'])] for s in case['sources']])
        files[prefix+'regions.tsv']=lines([[r['chromosome'],r['start'],r['end']] for r in case['regions']])
        files[prefix+'settings.tsv']=lines([['NULL' if case['cells'] is None else csv(case['cells']),str(case['keep']).lower(),case['chunk']]])
        value=result['result']['value']
        if 'error' in value:files[prefix+'ERROR.txt']=(value['error']+'\n').encode()
        else:
            for name,key in [('features.tsv','features'),('cells.tsv','cells')]:files[prefix+name]=('\n'.join(seq(value[key]))+ ('\n' if seq(value[key]) else '')).encode('utf8')
            for name,key in [('pointers.tsv','p'),('indices.tsv','i'),('values.tsv','x')]:files[prefix+name]=(csv(seq(value[key]))+'\n').encode()
    byid={c['id']:c for c in inputs['matrix_cases']}
    for result in output['nucleosome']:
        case=byid[result['id']];prefix='qc/'+result['id']+'-'+str(result['lines'])+'/'
        files[prefix+'settings.tsv']=lines([[result['id'],result['lines'],csv(case['object_cells'])]])
        value=result['result']['value']
        if 'error' in value:files[prefix+'ERROR.txt']=(value['error']+'\n').encode()
        else:files[prefix+'expected.tsv']=lines(zip(seq(value['cells']),seq(value['ratio_kind']),seq(value['ratio']),seq(value['percentile_kind']),seq(value['percentile']),strict=True))
    binary_inputs=json.loads((binary/'cases.json').read_bytes())['matrices'];binary_results=json.loads((binary/'original.json').read_bytes())['results']
    for case,result in zip(binary_inputs,binary_results,strict=True):
        assert case['id']==result['id'];prefix='binary/'+case['id']+'/'
        for name,key in [('features.tsv','features'),('cells.tsv','cells')]:files[prefix+name]=('\n'.join(case[key])+'\n').encode('utf8')
        for name,key in [('pointers.tsv','p'),('indices.tsv','i'),('input.tsv','x')]:files[prefix+name]=(csv(case[key])+'\n').encode()
        files[prefix+'expected.tsv']=(csv(seq(result['x']))+'\n').encode()
    files['count-ids.tsv']=('\n'.join(c['id'] for c in inputs['count_cases'])+'\n').encode();files['matrix-ids.tsv']=('\n'.join(c['id'] for c in inputs['matrix_cases'])+'\n').encode()
    files['qc-ids.tsv']=('\n'.join(c['id']+'-'+str(c['lines']) for c in output['nucleosome'])+'\n').encode();files['binary-ids.tsv']=('\n'.join(c['id'] for c in binary_inputs)+'\n').encode()
    for version in ['1.6.0','1.7.0','1.9.0','1.14.0','1.17.1']:
        source=BASE/'source-intake-1'/version/'source/Signac'
        for name in ['R/quantification.R','R/fragments.R','R/utilities.R','R/preprocessing.R','src/group.cpp','LICENSE','DESCRIPTION']:files[f'upstream/{version}/{name}']=(source/name).read_bytes()
    files['upstream/MIT-LICENSE.txt']=(ROOT/'src/main/resources/org/jlinalg/omics/signac-lsi/MIT-LICENSE.txt').read_bytes()
    DEST.mkdir(parents=True);archive=DEST/'original-controls.zip'
    with zipfile.ZipFile(archive,'x') as z:
        for name,raw in sorted(files.items()):
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,raw)
    capture=dict(schema_version=1,status='frozen-full-original-Signac-fragment-QC-and-binarization-controls',archive=pin(archive),members=len(files),member_pins={n:dict(bytes=len(v),sha256=hashlib.sha256(v).hexdigest()) for n,v in sorted(files.items())},source_pins={**record['source_pins'],**binary_record['source_pins'],'tools/literature/freeze_signac_fragment_fixtures.py':pin(Path(__file__))},count_cases=63,matrix_cases=64,qc_cases=33,binary_cases=36,rounding_scalars=20100,full_original_package='1.17.1',historical_binaries_executed=False)
    (DEST/'CAPTURE.json').write_text(json.dumps(capture,indent=2)+'\n',encoding='utf8',newline='\n');(DEST/'.gitattributes').write_text('*.zip -text\nCAPTURE.json -text\n',encoding='utf8',newline='\n');print(json.dumps(dict(archive=pin(archive),members=len(files))))
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original fixture integrity checks')
    main()
