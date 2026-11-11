# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze unchanged original input/default controls and complete native comparison contracts."""
import json
import hashlib
from pathlib import Path
import stat
import zipfile

ROOT=Path(__file__).resolve().parents[2]

def pin(path):
    raw=path.read_bytes();return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())


def census(root):
    result={}
    for path in sorted(root.rglob('*')):
        mode=path.lstat().st_mode
        if stat.S_ISDIR(mode):value=dict(kind='directory')
        else:
            assert stat.S_ISREG(mode),str(path)
            value=dict(kind='file',**pin(path))
        result[path.relative_to(root).as_posix()]=value
    return result


def main():
    source=ROOT/'build/literature-review/meme-runtime/tomtom-input-controls-1'
    output=ROOT/'src/test/resources/org/jlinalg/sequence/tomtom-inputs'
    assert not output.exists()
    proof=json.loads((source/'COMPLETED.json').read_bytes())
    assert proof['families']==24 and proof['successful_processes']==22 and proof['runtime_and_inputs_unchanged']
    expected=json.loads((source/'NATIVE-INVENTORY.json').read_bytes());actual=census(source/'captured')
    assert set(expected)==set(actual)
    for name,value in expected.items():assert {k:v for k,v in value.items() if k!='mode'}=={k:v for k,v in actual[name].items() if k!='mode'}
    files={};cases=[]
    for job in proof['jobs']:
        name=job['name'];kind='original-complete-input-and-output' if job['exit_code']==0 else 'original-invalid-probability-input'
        if name.endswith('-html'):kind='original-success-HTML-reader-unimplemented'
        row=dict(id=name,kind=kind,query_input='inputs/'+job['query_input'],exit_code=job['exit_code'],command=job['command'])
        cases.append(row)
        for suffix in ['PROCESS.json','stderr.log','stdout.log','result/tomtom.xml','result/tomtom.tsv']:
            path=source/'captured'/name/suffix
            if path.exists():files[name+'/'+suffix]=path.read_bytes()
    for path in (source/'captured/inputs').iterdir():
        assert path.is_file();files['inputs/'+path.name]=path.read_bytes()
    license_path=ROOT/'build/literature-review/fimo-runtime/original-source/meme-5.5.9/COPYING'
    files['UPSTREAM-COPYING']=license_path.read_bytes()
    files['NATIVE-INVENTORY.json']=(source/'NATIVE-INVENTORY.json').read_bytes()
    manifest=dict(schema_version=1,version='5.5.9',cases=cases,complete_output_contracts=20,original_failed_inputs=2,HTML_successes_with_unimplemented_reader=2,
        collector_completion=pin(source/'COMPLETED.json'),source_pins=proof['source_pins'],
        scope='All raw input bytes and original XML/TSV comparisons, not expected models derived from those outputs. Tests independently parse every input and verify every printed model/match. Omitted report images/HTML retain the complete native census. Raw HTML inputs remain unchanged; source success is not a native HTML reader claim. No runtime, execution, project analysis or implementation credit.')
    files['EXPECTED.json']=(json.dumps(manifest,indent=2,ensure_ascii=False)+'\n').encode()
    output.mkdir(parents=True)
    archive=output/'original-input-controls.zip'
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,date_time=(2026,11,3,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;z.writestr(info,data)
    (output/'manifest.json').write_text(json.dumps(dict(archive=pin(archive),entries=len(files),**manifest),indent=2,ensure_ascii=False)+'\n',encoding='utf-8',newline='\n')
    (output/'.gitattributes').write_text('original-input-controls.zip -text\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(archive=pin(archive),entries=len(files),complete_original_contracts=20)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original fixture checks')
    main()
