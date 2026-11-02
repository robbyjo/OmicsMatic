# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze existing original completion/retry receipts, without rerunning engines."""
import hashlib,json
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def main():
    destination=ROOT/'src/test/resources/org/jlinalg/workflow/nextflow/completion-controls.zip';assert not destination.exists()
    source=ROOT/'src/test/resources/org/jlinalg/workflow/nextflow/original-engine-controls.zip'
    assert hashlib.sha256(source.read_bytes()).hexdigest()=='d1777e588dddb99d68e345b2184345ca511b7b64a44d639166df167cb7896093'
    entries={};index=['id\tengine_exit\trows\toutcome']
    with zipfile.ZipFile(source) as archive:
        for line in archive.read('expected.tsv').decode().splitlines()[1:]:
            version,mode,rows,exit_code,_=line.split('\t');case='base/'+version+'/'+mode
            outcome='ENGINE_FAILURE' if exit_code!='0' else 'FAILED_OR_INCOMPLETE' if mode=='ignored-task-failure' else 'EMPTY' if mode=='empty-workflow' else 'COMPLETE'
            index.append('\t'.join([case,exit_code,rows,outcome]))
            for name in ['nextflow.log','trace.tsv']:entries[case+'/'+name]=archive.read(version+'/'+mode+'/'+name)
    proof=ROOT/'docs/literature/nextflow-runtime-development/VALIDATION.json';record=json.loads(proof.read_bytes());source=proof.parent/record['archive']['path'];assert hashlib.sha256(source.read_bytes()).hexdigest()==record['archive']['sha256']
    with zipfile.ZipFile(source) as archive:
        retries=json.loads(archive.read('evidence/original-retries/COMPLETED.json'))
        assert retries['cases']==6
        for row in retries['runs']:
            assert row['control_expectation_passed'];case='retry/'+row['version']+'/'+row['mode'];prefix='evidence/original-retries/'+row['version']+'/'+row['mode']
            outcome='COMPLETE_AFTER_RETRIES' if row['exit_code']==0 else 'ENGINE_FAILURE'
            index.append('\t'.join([case,str(row['exit_code']),str(len(row['rows'])),outcome]))
            for name in ['nextflow.log','trace.tsv']:entries[case+'/'+name]=archive.read(prefix+'/'+name)
    assert len(index)==22;entries['expected.tsv']=('\n'.join(index)+'\n').encode()
    receipt=dict(scope='Twenty-one actual original-engine completion/trace receipts from three exact Nextflow editions. Recovered retries retain failed attempts; ignored failure and empty-workflow status remain distinct. These reuse tiny synthetic inputs and do not reproduce a biological paper.',cases=21,source_runtime_checkpoint='docs/literature/nextflow-runtime-development/VALIDATION.json',source_runtime_archive_sha256=record['archive']['sha256'],producer_sha256=hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),files={name:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for name,raw in entries.items()})
    entries['receipt.json']=(json.dumps(receipt,indent=2)+'\n').encode()
    with zipfile.ZipFile(destination,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in sorted(entries.items()):
            entry=zipfile.ZipInfo(name,(1980,1,1,0,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;entry.external_attr=0o100644<<16;archive.writestr(entry,raw)
    with zipfile.ZipFile(destination) as archive:assert len(archive.infolist())==len(entries) and all(archive.read(name)==raw for name,raw in entries.items())
    print(json.dumps(dict(status='frozen-21-original-Nextflow-completion-controls',bytes=destination.stat().st_size,sha256=hashlib.sha256(destination.read_bytes()).hexdigest(),entries=len(entries))))


if __name__=='__main__':main()
