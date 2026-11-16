# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Explicit current-source boundary and genuine frozen predecessor checks."""
from pathlib import Path
import hashlib,json,subprocess,sys,time
from .primary import ROOT
from .model import META,GUIDE

BASE='2bdd5c41fb55e685950fabaf7619f69a4a28c599'
CATALOG_BASE='82162f75683ebd651a05f8a21d8e482ca54c2885'
EVIDENCE='docs/literature/pops-catalog-integration'
PROOF='docs/literature/omics-2023-2026/pops-catalog-validation.json'
COMPATIBILITY='docs/literature/omics-2023-2026/pops-reviews-shared-source-compatibility.json'
def encoded(value):return (json.dumps(value,ensure_ascii=False,indent=2)+'\n').encode()
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):
    h=hashlib.sha256();size=0
    with Path(path).open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):size+=len(raw);h.update(raw)
    return dict(bytes=size,sha256=h.hexdigest())
def git(*args,cwd=ROOT):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=cwd)
def owned(name):return name==GUIDE or name in [PROOF,COMPATIBILITY] or name.startswith(('tools/literature/pops_catalog/',EVIDENCE+'/'))
def tree():
    result={}
    for row in git('ls-tree','-rz','--full-tree',BASE).split(b'\0'):
        if not row:continue
        header,name=row.split(b'\t',1);mode,kind,oid=header.decode().split();assert kind=='blob' and mode in ['100644','100755'];result[name.decode()]=dict(mode=mode,git_object=oid)
    assert len(result)==13548
    return result
def blob(raw):return hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
def source_inventory():
    baseline=tree();records={}
    for name,identity in baseline.items():
        if name in META:continue
        raw=(ROOT/name).read_bytes();oid=identity['git_object'];assert blob(raw)==oid or (b'\0' not in raw and blob(raw.replace(b'\r\n',b'\n'))==oid),name
        records[name]=dict(**identity,bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    result=dict(baseline=BASE,baseline_files=13548,files=records,reviewed_existing_source_modifications=[],scientific_sources_unchanged=True)
    source_guard(result);return result
def source_guard(record):
    baseline=tree();assert record['baseline']==BASE and set(record['files'])==set(baseline)-set(META)
    names={n.decode() for n in git('ls-files','--cached','--others','--exclude-standard','-z').split(b'\0') if n}
    assert {n for n in names if not owned(n) and n not in META}==set(record['files']),'Unreviewed current project source membership'
    for name,expected in record['files'].items():
        assert {k:expected[k] for k in ['mode','git_object']}==baseline[name]
        assert pin(ROOT/name)=={k:expected[k] for k in ['bytes','sha256']},name
    return dict(baseline=BASE,existing_non_catalog_files_hashed=len(record['files']),existing_source_modifications=0,old_scientific_source_or_Git_overlays=False)
def checkout(path,head):
    path=Path(path).resolve();assert git('rev-parse','HEAD',cwd=path).decode().strip()==head and not git('status','--porcelain=v1','--untracked-files=all',cwd=path),'Exact clean frozen checkout required: '+str(path)
    return path
def run(out,name,command,cwd):
    start=time.monotonic()
    with (out/(name+'.stdout')).open('xb') as so,(out/(name+'.stderr')).open('xb') as se:p=subprocess.run(command,cwd=cwd,stdout=so,stderr=se,stdin=subprocess.DEVNULL)
    (out/(name+'.process.json')).write_bytes(encoded(dict(command=command,cwd=str(cwd),exit_code=p.returncode,seconds=round(time.monotonic()-start,3),verifier_source_modified=False)))
    assert p.returncode==0,'Frozen predecessor refused; inspect retained consoles: '+name
def qualify(args,out):
    snapshot=checkout(args.workflow_checkout,BASE);catalog=checkout(args.catalog_checkout,CATALOG_BASE)
    command=[sys.executable,'-X','utf8','-B','tools/literature/pops_original/records.py','--private-root',str(ROOT/'build/pops-original'),'--upstream-data',str(ROOT/'build/pops/source-audit-1/upstream/example/data'),'--out',str(out/'original-source-records')]
    run(out,'original-source-records',command,snapshot)
    command=[sys.executable,'-X','utf8','-B','tools/literature/ldpred2_catalog/catalog.py','--verify','--catalog-parent-checkout',str(args.catalog_parent_checkout),'--previous-parent-checkout',str(args.previous_parent_checkout),'--baseline-checkout',str(args.baseline_checkout),'--rescan-sources','--out',str(catalog/'build'/('pops-parent-'+out.parent.name+'-'+out.name))]
    run(out,'previous-complete-catalog',command,catalog)
    checkout(snapshot,BASE);checkout(catalog,CATALOG_BASE)
    proof=json.loads((out/'original-source-records/COMPLETED.json').read_bytes());assert proof['complete_named_private_files_checked']==6547 and proof['admitted_task_bundles_checked']==42 and proof['genuine_replay_families_checked']==13
    return dict(status='PASS_GENUINE_FROZEN_POPS_WORKFLOWS_AND_PREDECESSOR_CATALOG',snapshot_commit=BASE,catalog_commit=CATALOG_BASE,whole_predecessor_catalog_rescanned=True,original_scientific_records=proof,old_verifiers_or_Git_overlaid=False,fresh_scientific_execution=False,new_catalog_credit=0,whole_goal_complete=False)
