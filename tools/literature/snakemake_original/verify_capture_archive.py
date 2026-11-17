#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Independently verify every compact development-archive member and shared-reference binding."""
from pathlib import Path
import argparse,hashlib,json,shutil,zipfile
ROOT=Path(__file__).resolve().parents[3]
def pin(path):
    digest=hashlib.sha256();size=0
    with path.open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):digest.update(raw);size+=len(raw)
    return dict(bytes=size,sha256=digest.hexdigest())
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--archive',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build');out.mkdir(exist_ok=False);shutil.copyfile(__file__,out/'as-run-verifier.py');declared=json.loads((a.archive/'ARCHIVED.json').read_bytes());path=a.archive/'managed-capture-evidence.zip';before=pin(path);assert before==declared['archive'];members={}
    with zipfile.ZipFile(path) as z:
        names=z.namelist();assert len(names)==len(set(names));index=json.loads(z.read('INDEX.json'));assert index['schema_version']==1
        for name,row in index['files'].items():
            assert not Path(name).is_absolute() and '..' not in Path(name).parts and row['member'].startswith(('metadata/','objects/')) and '..' not in Path(row['member']).parts
            old=members.setdefault(row['member'],row['pin']);assert old==row['pin']
        assert set(names)==set(members)|{'INDEX.json'}
        for name,expected in members.items():
            digest=hashlib.sha256();size=0
            with z.open(name) as f:
                for raw in iter(lambda:f.read(1048576),b''):digest.update(raw);size+=len(raw)
            actual=dict(bytes=size,sha256=digest.hexdigest());assert actual==expected,name
            if name.startswith('objects/'):assert name[8:]==actual['sha256'] and index['blobs'][actual['sha256']]==actual
        completed=json.loads(z.read(index['files']['COMPLETED.json']['member']));assert len(completed['cases'])==18 and completed['original_software_unchanged'] and not completed['user_analysis_admitted'] and not completed['whole_goal_complete'];bundles=0
        if index.get('managed_bundles_included'):
            for case in completed['cases']:
                prefix='cases/'+case['case']+'/bundle/';files={name[len(prefix):]:value for name,value in index['files'].items() if name.startswith(prefix)}
                if not case['expected_success']:assert not files;continue
                manifest=json.loads(z.read(files['manifest.json']['member']));marker=json.loads(z.read(files['COMPLETED.json']['member']));assert marker['kind']=='declared-managed-workflow' and marker['status']=='completed-declared-managed-workflow-evidence' and not marker['user_analysis_admitted'] and marker['manifest']==files['manifest.json']['pin']
                assert set(files)==set(manifest['files'])|{'manifest.json','COMPLETED.json'}
                for name,value in manifest['files'].items():assert value==files[name]['pin']
                bundles+=1
            assert bundles==6
    assert pin(path)==before
    result=dict(status='PASS_COMPLETE_COMPACT_CAPTURE_ARCHIVE_CENSUS_EVERY_MEMBER_AND_SHARED_BLOB_REFERENCE',archive=before,physical_members=len(members)+1,virtual_references=len(index['files']),unique_blobs=len(index['blobs']),managed_bundles_checked=bundles,engine_executed=False,user_analysis_admitted=False,new_catalog_credit=0,whole_goal_complete=False)
    with (out/'COMPLETED.json').open('x',encoding='utf-8') as f:json.dump(result,f,indent=2);f.write('\n')
    print('PASS_COMPACT_CAPTURE_ARCHIVE_COMPLETE_VERIFICATION',flush=True)
if __name__=='__main__':main()
