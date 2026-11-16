#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read the bounded original-PoPS workflow qualification without refreshing old evidence."""
from pathlib import Path,PurePosixPath
import argparse,concurrent.futures,csv,hashlib,io,json,subprocess,sys,traceback,zipfile

ROOT=Path(__file__).resolve().parents[3]
BASE='3e8d406ef207244f2843267348eab6d3c28fe7b5'
DOC='docs/literature/pops-original-workflows'
MODIFIED={'src/main/java/org/jlinalg/cli/PopsOriginalCli.java','src/main/java/org/jlinalg/cli/PopsOriginalRuntime.java','docs/literature/POPS_ORIGINAL_WORKFLOWS.md'}
SOURCES=MODIFIED|{'src/test/java/org/jlinalg/cli/PopsOriginalContractTest.java'}|{'tools/literature/pops_original/'+name for name in ['convergence_reference.py','qualify_adapter.py','qualify_failures.py','qualify_official_fit.py','qualify_matrix.py','qualify_integrity.py','qualify_capture.py','PopsOriginalJUnitReplay.java','records.py']}
META={DOC+'/'+name for name in ['.gitattributes','SOURCE-PINS.json','EVIDENCE-PINS.json','PRIVATE-RECORDS.json','CHANGE-REVIEW.json','STATUS.json','VALIDATION-RECORDS.zip','WARNINGS.md']}
RUNS=['qualification-legacy-2','qualification-legacy-edges-2','qualification-lasso-2','qualification-munge-2','qualification-official-munge-1','qualification-official-fit-1','qualification-failures-2','qualification-matrix-2','qualification-integrity-1','qualification-capture-1','guide-example-1/run']
if not __debug__:raise RuntimeError('Evidence assertions must remain enabled')
class MissingPrerequisite(Exception):pass
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):
    h=hashlib.sha256();size=0
    with Path(path).open('rb') as f:
        for raw in iter(lambda:f.read(1048576),b''):size+=len(raw);h.update(raw)
    return dict(bytes=size,sha256=h.hexdigest())
def read(path):return json.loads(Path(path).read_bytes())
def git(*args):
    p=subprocess.run(['git','--no-replace-objects',*args],cwd=ROOT,capture_output=True)
    if p.returncode:raise MissingPrerequisite('Required genuine Git baseline unavailable: '+BASE+'. Fetch the named baseline in a complete checkout and retry NEW verification output; no source overlays or fingerprint refreshes are accepted.')
    return p.stdout
def blob(raw):return hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
def matches(raw,oid):return blob(raw)==oid or (b'\0' not in raw and b'\r\n' in raw and blob(raw.replace(b'\r\n',b'\n'))==oid)
def properties(path):return dict(line.split('=',1) for line in Path(path).read_text().splitlines() if line and not line.startswith('#'))
def member(root,name):
    p=PurePosixPath(name);assert not p.is_absolute() and '..' not in p.parts and '\\' not in name and p.parts
    return root.joinpath(*p.parts)
def source_guard(pins,review):
    tree={}
    for row in git('ls-tree','-rz','--full-tree',BASE).split(b'\0'):
        if not row:continue
        header,name=row.split(b'\t',1);mode,kind,oid=header.decode().split();assert kind=='blob' and mode in ['100644','100755'];tree[name.decode()]=oid
    assert len(tree)==review['baseline_files'] and set(pins)==SOURCES and set(review['modified_existing_files'])==MODIFIED
    actual=set(git('ls-files','-z').decode().strip('\0').split('\0'))|set(git('ls-files','--others','--exclude-standard','-z').decode().strip('\0').split('\0'));actual.discard('')
    assert actual==set(tree)|SOURCES|META,('Source membership differs',sorted(actual-(set(tree)|SOURCES|META))[:10])
    for name,oid in tree.items():
        raw=(ROOT/name).read_bytes()
        if name in MODIFIED:assert rawpin(raw)==review['modified_existing_files'][name]['after'] and rawpin(git('show',BASE+':'+name))==review['modified_existing_files'][name]['before'],name
        else:assert matches(raw,oid),'Unreviewed baseline change: '+name
    for name,expected in pins.items():assert pin(ROOT/name)==expected,name
    return len(tree)
def inventory(root):return {p.relative_to(root).as_posix()+('/' if p.is_dir() else ''):('0\t-' if p.is_dir() else str(p.stat().st_size)+'\t'+pin(p)['sha256']) for p in root.rglob('*')}
def check_bundle(root):
    marker=properties(root/'COMPLETED.properties');assert marker['profile']=='pops-original-v1'
    assert pin(root/'FILES.tsv')['sha256']==marker['manifest_sha256'] and pin(root/'runtime-identity.tsv')['sha256']==marker['runtime_identity_sha256']
    with (root/'FILES.tsv').open(newline='',encoding='utf-8') as f:
        rows=list(csv.reader(f,delimiter='\t'));assert rows.pop(0)==['path','bytes','sha256'];expected={r[0]:r[1]+'\t'+r[2] for r in rows};assert len(expected)==len(rows)
    actual=inventory(root);actual.pop('FILES.tsv');actual.pop('COMPLETED.properties');assert actual==expected,root
    return marker
def same_science(a,b):
    for name in ['results','audit']:assert inventory(a/name)==inventory(b/name),(a,b,name)
def compare_reports(bundle,prefix):
    for suffix,name in [('coefs','coefficients.tsv'),('preds','predictions.tsv'),('marginals','marginals.tsv')]:assert (bundle/'results'/name).read_bytes()==Path(str(prefix)+'.'+suffix).read_bytes(),(bundle,name)
def check_private(roots,private,status):
    files=private['files'];assert private['profile']=='original-pops-private-records-v1'
    paths={}
    for name,expected in files.items():
        alias,relative=name.split('/',1);assert alias in roots;path=member(roots[alias],relative)
        if not path.is_file():raise MissingPrerequisite('Named private record unavailable: '+name+'. Restore the matching retained records and upstream example data, or use --public-only to inspect source/metadata only. No analysis is performed by this reader.')
        paths[name]=(path,expected)
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        for name,actual in pool.map(lambda item:(item[0],pin(item[1][0])),paths.items()):assert actual==files[name],'Private bytes differ: '+name
    root=roots['records'];artifact=status['candidate_jar'];assert pin(root/'compile-3/candidate.jar')==artifact
    compiled=read(root/'compile-3/COMPLETED.json');assert compiled['tests']==dict(tests=270,failures=0,errors=0,skipped=0)
    assert set(compiled['changed_existing_packaged_classes'])=={'org/jlinalg/cli/PopsOriginalCli.class','org/jlinalg/cli/PopsOriginalRuntime.class'}
    compiled_sources=read(root/'compile-3/SOURCE-BEFORE.json')
    for name in ['src/main/java/org/jlinalg/cli/PopsOriginalCli.java','src/main/java/org/jlinalg/cli/PopsOriginalRuntime.java','src/test/java/org/jlinalg/cli/PopsOriginalContractTest.java']:assert pin(ROOT/name)==compiled_sources[name]
    linux=properties(root/'linux-contracts-1/results/COMPLETED.properties');assert linux['tests_succeeded']=='32' and linux['tests_failed']==linux['tests_skipped']==linux['tests_aborted']=='0'
    admitted=0;replays=0
    for name in RUNS:
        report=read(root/name/'COMPLETED.json');assert report['artifact_sha256']==artifact['sha256'] and report['full_adapter_qualification_claimed'] is False and report['whole_goal_complete'] is False
        for process in report['processes']:
            assert process['exit_code']==process['expected_exit'] and not process['timed_out'];args=process['command'];bundle=Path(args[args.index('--out')+1]);relative=bundle.as_posix().split('/build/pops-original/',1)[1];bundle=member(root,relative)
            if process['exit_code']==0:
                check_bundle(bundle);admitted+=1
                if '--verify' in args:
                    old=args[args.index('--verify')+1];old=member(root,old.split('/build/pops-original/',1)[1]);same_science(old,bundle);replays+=1
            else:assert not (bundle/'COMPLETED.properties').exists(),bundle
    legacy=root/'qualification-legacy-2/all-22-bundle';reference=root/'reference-1/v01-complete-22-chromosomes'
    assert (legacy/'results/selected.features').read_bytes()==(reference/'selected.features').read_bytes()
    for c in range(1,23):
        for suffix,name in [('coefs','coefficients.tsv'),('results','scores.tsv')]:assert (legacy/('results/chr-%02d/'%c)/name).read_bytes()==(reference/('original.%d.%s'%(c,suffix))).read_bytes()
    for case in ['v02-lasso','v02-lasso-stress']:compare_reports(root/'qualification-lasso-2'/(case+'-bundle'),root/'reference-1'/case/'original')
    tutorial=root/'qualification-official-fit-1';compare_reports(tutorial/'complete-official-bundle',tutorial/'direct-original/original')
    with zipfile.ZipFile(ROOT/'src/test/resources/org/jlinalg/cli/pops-workflow-inputs.zip') as z:
        cases=json.loads(z.read('CASES.json'));assert len(cases)==19
        for case in cases:
            p=root/'qualification-matrix-2'/(case+'-bundle')
            if case.startswith('failure-'):assert not (p/'COMPLETED.properties').exists();continue
            for suffix,name in [('coefs','coefficients.tsv'),('preds','predictions.tsv'),('marginals','marginals.tsv')]:assert (p/'results'/name).read_bytes()==z.read(case+'/original.'+suffix),(case,name)
    for policy in ['ignore','mean','zero']:
        for p in (root/'reference-1/munger'/policy).glob('original.*'):
            suffix=p.name[len('original.'):]
            if suffix=='rows.txt' or suffix.startswith(('cols.','mat.')):assert p.read_bytes()==(root/'qualification-munge-2'/(policy+'-bundle')/'results'/('features.'+suffix)).read_bytes()
    for p in (roots['upstream-data']/'features_munged').glob('pops_features.*'):
        suffix=p.name[len('pops_features.'):];assert p.read_bytes()==(root/'qualification-official-munge-1/official-complete-bundle/results'/('features.'+suffix)).read_bytes()
    failure=read(root/'qualification-failures-2/CONVERGENCE-CONTROL.json');assert failure['original_exit']==0 and failure['adapter_exit']==2 and failure['convergence_warnings']==35 and failure['finite_raw_reports_preserved']
    return dict(complete_named_private_files_checked=len(paths),admitted_task_bundles_checked=admitted,genuine_replay_families_checked=replays)
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--public-only',action='store_true');p.add_argument('--private-root',type=Path,default=ROOT/'build/pops-original');p.add_argument('--upstream-data',type=Path,default=ROOT/'build/pops/source-audit-1/upstream/example/data');a=p.parse_args();out=None
    try:
        a.out.resolve().mkdir(parents=True,exist_ok=False);out=a.out.resolve();doc=ROOT/DOC;status=read(doc/'STATUS.json');pins=read(doc/'SOURCE-PINS.json');review=read(doc/'CHANGE-REVIEW.json');count=source_guard(pins,review)
        assert status['status']=='PASS_BOUNDED_ORIGINAL_POPS_WORKFLOWS_SOURCE_AND_SCIENTIFIC_CONTROLS' and status['new_catalog_credit']==0 and status['whole_goal_complete'] is False
        for key,name in [('source_pins','SOURCE-PINS.json'),('evidence_pins','EVIDENCE-PINS.json'),('private_records','PRIVATE-RECORDS.json'),('validation_records','VALIDATION-RECORDS.zip')]:assert pin(doc/name)==status[key]
        evidence=read(doc/'EVIDENCE-PINS.json')
        with zipfile.ZipFile(doc/'VALIDATION-RECORDS.zip') as z:
            assert len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(evidence) and z.testzip() is None
            for name,expected in evidence.items():assert rawpin(z.read(name))==expected,name
        details={} if a.public_only else check_private({'records':a.private_root.resolve(),'upstream-data':a.upstream_data.resolve()},read(doc/'PRIVATE-RECORDS.json'),status)
        result=dict(status='PASS_PUBLIC_ORIGINAL_POPS_SOURCE_AND_RECORD_METADATA_ONLY' if a.public_only else 'PASS_ORIGINAL_POPS_SOURCE_EVIDENCE_AND_NAMED_PRIVATE_RECORDS',baseline_commit=BASE,baseline_files_checked=count,public_only=a.public_only,full_source_tool_or_paper_reproduction_claimed=False,fresh_scientific_execution_performed=False,installed_runtime_rechecked=False,new_catalog_credit=0,whole_goal_complete=False,**details)
        (out/'COMPLETED.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result),flush=True);return 0
    except Exception as error:
        code=3 if isinstance(error,(MissingPrerequisite,FileNotFoundError,PermissionError)) else 2
        message=dict(status='SKIP3_ORIGINAL_POPS_RECORD_READBACK' if code==3 else 'FAIL2_ORIGINAL_POPS_RECORD_READBACK',reason=repr(error),required_action='Retain failed evidence. Restore the named permitted prerequisites/records or correct the reported discrepancy and retry NEW output. Do not refresh source/evidence fingerprints. Public-only success does not validate missing private scientific records or runtime readiness.',traceback=traceback.format_exc())
        if out is not None:(out/('SKIPPED.json' if code==3 else 'FAILURE.json')).write_text(json.dumps(message,indent=2)+'\n')
        print(json.dumps(message),file=sys.stderr,flush=True);return code

if __name__=='__main__':raise SystemExit(main())
