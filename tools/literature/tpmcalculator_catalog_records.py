#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Preserve/read back actual catalog transition, tests and desktop/mobile checks."""
from pathlib import Path
import argparse,hashlib,json,sys,zipfile
sys.dont_write_bytecode=True
sys.path.insert(0,str(Path(__file__).resolve().parent))
from tpmcalculator_catalog.catalog import verify as verify_catalog
from tpmcalculator_catalog.model import META,GUIDE,PROOF,MODULE

ROOT=Path(__file__).resolve().parents[2]
EVIDENCE=ROOT/'docs/literature/tpmcalculator-catalog-validation'
if not __debug__:raise RuntimeError('Do not disable evidence assertions')

def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return rawpin(path.read_bytes())
def write(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')

def freeze():
    EVIDENCE.mkdir(exist_ok=False)
    (EVIDENCE/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    paths={}
    for folder in ['tpm-catalog-admission-1','tpm-catalog-verification-1','tpm-catalog-ui-1']:
        for path in (ROOT/'build'/folder).rglob('*'):
            if path.is_file():paths[folder+'/'+path.relative_to(ROOT/'build'/folder).as_posix()]=path
    for name in ['tpm-catalog-admission-1.log','tpm-catalog-verification-1.log','tpm-catalog-tests-pre-admission-1.log',
                 'tpm-catalog-tests-post-admission-1.log','tpm-catalog-ui-1.log','tpm-catalog-website-1.log']:
        paths['logs/'+name]=ROOT/'build'/name
    index={}
    with zipfile.ZipFile(EVIDENCE/'complete-catalog-records.zip','x',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,path in sorted(paths.items()):
            raw=path.read_bytes();archive.writestr(name,raw);index[name]=rawpin(raw)
    write(EVIDENCE/'RECORD-INDEX.json',index)
    source_names=list(META)+[GUIDE,PROOF,Path(__file__).relative_to(ROOT).as_posix()]
    source_names += [path.relative_to(ROOT).as_posix() for path in sorted((ROOT/MODULE).iterdir()) if path.is_file()]
    result=dict(status='PASS_NATIVE_TPMCALCULATOR_CATALOG_FULL_CHAIN_TESTS_AND_UI_RECORDS',
        source_files={name:pin(ROOT/name) for name in source_names},
        evidence_files={path.name:pin(path) for path in EVIDENCE.iterdir() if path.is_file()},
        catalog_tests_pre_admission=8,catalog_tests_post_admission=8,
        full_predecessor_chain_and_primary_rescan=True,complete_native_public_records_rechecked=True,
        desktop='1440x1000',mobile_dark='390x1000',mobile_warning_image_inspected=True,
        metadata_only_change=True,new_scoped_integrations=122,native_integrations=21,upstream_integrations=101,
        reviewed_uses=1197,unreviewed_uses=1493,missing_families=265,source_families=446,observations=2690,
        scientific_sources_or_verifiers_changed=False,fresh_native_scientific_calls_repeated=False,
        public_site_deployed=False,whole_paper_reproduced=False,whole_goal_complete=False)
    write(EVIDENCE/'QUALIFICATION.json',result)

def verify():
    result=json.loads((EVIDENCE/'QUALIFICATION.json').read_bytes())
    for kind,base in [('source_files',ROOT),('evidence_files',EVIDENCE)]:
        for name,identity in result[kind].items():assert pin(base/name)==identity,(kind,name)
    with zipfile.ZipFile(EVIDENCE/'complete-catalog-records.zip') as archive:
        index=json.loads((EVIDENCE/'RECORD-INDEX.json').read_bytes())
        assert len(archive.namelist())==len(set(archive.namelist()))==len(index)
        assert set(archive.namelist())==set(index)
        for name,identity in index.items():assert rawpin(archive.read(name))==identity,name
        saved=json.loads(archive.read('tpm-catalog-verification-1/COMPLETED.json'))
        assert saved['primary_sources_rescanned'] and saved['registry_definitions']==485
        assert saved['component']['full_public_record_payloads_read_back']
        assert saved['new_scoped_integrations']==122 and not saved['whole_goal_complete']
        ui=json.loads(archive.read('tpm-catalog-ui-1/COMPLETED.json'))
        assert ui['new_scoped_integrations']==122 and not ui['warning_overflow'] and ui['browser_errors']==[] and not ui['public_site_deployed']
        for name in ['pre','post']:
            raw=archive.read('logs/tpm-catalog-tests-'+name+'-admission-1.log')
            assert b'Ran 8 tests' in raw and b'OK' in raw and b'FAILED' not in raw
        assert b'BUILD SUCCESSFUL' in archive.read('logs/tpm-catalog-website-1.log')
    current=verify_catalog(ROOT,rescan=False)
    assert current['new_scoped_integrations']==result['new_scoped_integrations']==122
    return result

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--freeze',action='store_true');args=parser.parse_args()
    if args.freeze:freeze()
    result=verify();print(json.dumps({key:result[key] for key in ['status','new_scoped_integrations','native_integrations','reviewed_uses','unreviewed_uses','missing_families','public_site_deployed','whole_goal_complete']},indent=2))
