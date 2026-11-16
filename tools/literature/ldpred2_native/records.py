#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read immutable native LDpred2 development evidence; never repeat computation."""
from pathlib import Path
import argparse,gzip,hashlib,json,sys,traceback,zipfile
sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[3]
MODULE='tools/literature/ldpred2_native'
EVIDENCE='docs/literature/ldpred2-native-development'
ACTION='Native inf and one grid model only; build current source. Auto, workflow CLI, complete grid orchestration and validation-set selection remain open. All 19 original divergence controls remain unaccepted. Supply aligned variant/allele/build/population inputs and correct effective sample sizes; preserve failures, never repair them silently. Missing permitted record/reference prerequisites require SKIP3; other failures require FAIL2 and NEW output paths. Saved readback repeats no science and checks no large raw data unless explicitly requested. No new catalog credit; full250-paper goal remains active.'
def load(path):return json.loads(Path(path).read_bytes())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):
    path=Path(path);digest=hashlib.sha256();size=0
    if path.is_symlink():raise ValueError('Evidence aliases are not qualified: '+str(path))
    with path.open('rb') as stream:
        for chunk in iter(lambda:stream.read(8*1024*1024),b''):size+=len(chunk);digest.update(chunk)
    return dict(bytes=size,sha256=digest.hexdigest())
def archive(path,expected):
    with zipfile.ZipFile(path) as z:
        if len(z.namelist())!=len(set(z.namelist())) or set(z.namelist())!=set(expected):raise ValueError('Complete archive membership differs: '+str(path))
        for name,identity in expected.items():
            if rawpin(z.read(name))!=identity:raise ValueError('Archived bytes differ: '+name)
def verify(root=ROOT,private_base=None,catalog_metadata=None,rescan=False):
    root=Path(root).resolve();folder=root/EVIDENCE;proof=load(folder/'STATUS.json')
    entries=list(folder.iterdir())
    if any(p.is_symlink() or not p.is_file() for p in entries) or {p.name for p in entries}!=set(proof['public_files'])|{'STATUS.json'}:raise ValueError('Complete public development evidence membership differs')
    for name,identity in proof['public_files'].items():
        if pin(folder/name)!=identity:raise ValueError('Immutable public evidence differs: '+name)
    expected={name for name in proof['source_pins'] if name.startswith(MODULE+'/')}
    actual={p.relative_to(root).as_posix() for p in (root/MODULE).iterdir()}
    if actual!=expected:raise ValueError('Complete native development implementation membership differs')
    archive(folder/'own-source-snapshot.zip',dict(proof['source_pins'],**{'LICENSE-project.txt':proof['project_license']}))
    archive(folder/'validation-metadata.zip',load(folder/'METADATA-PINS.json'))
    sources_match=all((root/n).is_file() and pin(root/n)==identity for n,identity in proof['source_pins'].items())
    source_review=load(folder/'PRIMARY-ROLES.json')
    if len(source_review['primary_files'])!=250 or len(source_review['papers'])!=5:raise ValueError('Complete primary source census differs')
    metadata={name:(root/name).read_bytes() for name in proof['catalog_metadata_pins']} if catalog_metadata is None else dict(catalog_metadata)
    if set(metadata)!=set(proof['catalog_metadata_pins']) or {name:rawpin(raw) for name,raw in metadata.items()}!=proof['catalog_metadata_pins']:raise ValueError('Exact eight predecessor catalog metadata bytes required')
    if rescan:
        for row in source_review['primary_files'].values():
            raw=gzip.decompress((root/'build/literature-review/xml'/(row['pmcid']+'.xml.gz')).read_bytes())
            if hashlib.sha256(raw).hexdigest()!=row['xml_sha256']:raise ValueError('Frozen primary XML differs: '+row['pmcid'])
    with zipfile.ZipFile(root/'src/test/resources/org/jlinalg/genetics/ldpred2-bigsnpr122-original.zip') as z:
        names=z.read('case-index.txt').decode().splitlines()
        if len(names)!=49 or len(set(names))!=49:raise ValueError('Complete original model population differs')
        divergent=sum(z.read(n+'/grid-core-status.txt').strip()==b'DIVERGENCE' for n in names)
        if divergent!=19:raise ValueError('Preserved original divergence population differs')
    count=0
    if private_base is not None:
        expected_private=load(folder/'PRIVATE-RECORDS.json')['files']
        for name,identity in expected_private.items():
            if pin(Path(private_base)/name)!=identity:raise ValueError('Named complete retained private record differs: '+name)
        count=len(expected_private)
    return dict(status='PASS_NATIVE_LDPRED2_SAVED_DEVELOPMENT_RECORD_READBACK',current_project_sources_match_snapshot=sources_match,
        exact_predecessor_metadata_checked=True,catalog_metadata_is_actual_current_state=catalog_metadata is None,metadata_only_historical_view_used=catalog_metadata is not None,
        primary_sources_rescanned=250 if rescan else 0,java_tests=13,original_small_models=49,original_finite_grid_models=30,original_grid_divergences_retained=19,
        original_large_variants=1100000,large_stored_ld_entries=2200000,packaged_platforms=['Windows Java21','Linux Java21'],requested_java_heap_mb=256,
        complete_named_private_records_checked=count,large_raw_data_checked=count>0,fresh_scientific_execution_performed=False,
        full_grid_orchestration_implemented=False,auto_implemented=False,workflow_cli_implemented=False,new_catalog_credit=0,whole_goal_complete=False)
def main():
    p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--private-base',type=Path);p.add_argument('--rescan-sources',action='store_true');p.add_argument('--require-current-source',action='store_true');a=p.parse_args()
    try:
        result=verify(private_base=a.private_base,rescan=a.rescan_sources)
        if a.require_current_source and not result['current_project_sources_match_snapshot']:raise ValueError('Current native sources differ; retain this historical proof and qualify changed code separately')
        print(json.dumps(result,indent=2),flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,FileNotFoundError) else 2
        print('SKIP3_NATIVE_LDPRED2_EVIDENCE_UNAVAILABLE' if code==3 else 'FAIL2_NATIVE_LDPRED2_EVIDENCE_UNVERIFIED',repr(error),ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
