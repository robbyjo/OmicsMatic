from pathlib import Path
import datetime, hashlib, json, re, shutil, subprocess, zipfile
from handover_inventory_20261122_111 import save, pin, git

R=Path('D:/projects/JLinAlg'); T=R/'build/fu-1'; S=T/'build/squidpy-1'
O=R/'build/git-publish/20261122-squidpy-workflow-handover-136'
D=T/'docs/literature/squidpy-workflow-handover-20261122'
assert not D.exists(); D.mkdir()
before=json.loads((O/'WORKTREES-BEFORE.json').read_bytes())
own=next(x for x in before if Path(x['worktree'])==T)
assert git('rev-parse','HEAD',cwd=T).decode().strip()=='abb43f59fe5b642431a173f3c2c62f4a6c040184'
assert len(own['status'])==14 and all(x['status']=='??' for x in own['status'])
drafts={}
for row in own['status']:
    name=row['path']; assert pin(T/name)==row['content']
    target=D/'drafts'/name; target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copyfile(T/name,target); assert pin(target)==row['content']
    drafts[name]=dict(saved_as='drafts/'+name,**row['content'])
save(D/'RESTORE.json',dict(base='abb43f59fe5b642431a173f3c2c62f4a6c040184',files=drafts,
    instruction='Restore byte-identically into a separate clean branch; finish qualification before enabling the command or catalog credit. Existing native component remains qualified and unchanged.'))

helpers={}
for path in sorted((R/'build').iterdir()):
    if path.is_file() and path.suffix in ('.py','.java') and (re.search(r'squidpy.*20261122_(134|135)\.',path.name,re.I) or path.name=='SquidpyWorkflowUnitTests135.java'):
        target=D/'helpers'/path.name;target.parent.mkdir(exist_ok=True);shutil.copyfile(path,target);helpers[path.name]=dict(origin=str(path),**pin(path))
assert len(helpers)==13,len(helpers)
save(D/'HELPERS.json',helpers)

files={}; objects={}
def capture(path,name):
    raw=path.read_bytes(); identity=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    assert len(raw)<80_000_000,(name,len(raw))
    files[name]=identity;objects[identity['sha256']]=raw

# Entire artificial operation probes, including original exceptions and earlier
# fixture failures. Cache/runtime binaries and full primary texts are excluded.
for version in ('1.1.2','1.2.2','1.8.4'):
    for run in (1,2):
        for suffix in ('','-launch'):
            directory=S/f'operations-probes-{version}-{run}{suffix}'
            for path in sorted(directory.rglob('*')):
                if path.is_file():capture(path,'private-evidence/'+path.relative_to(S).as_posix())
for name in ('operations-probes-source-1','operations-probes-source-2','java-support-intake-1','linux-runtime-install-1',
             'workflow-compile-1','workflow-compile-2','workflow-compile-3','workflow-compile-4','workflow-compile-5',
             'workflow-smoke-1.2.2-1','workflow-smoke-1.8.4-1','workflow-smoke-1.8.4-2'):
    for path in sorted((S/name).rglob('*')):
        if path.is_file():capture(path,'private-evidence/'+path.relative_to(S).as_posix())

jobs=[]
for version in ('1.1.2','1.2.2','1.8.4'):
    directory=S/f'workflow-cases-{version}-1'
    progress=json.loads((directory/'PROGRESS.json').read_bytes()) if (directory/'PROGRESS.json').exists() else []
    terminal={x['case'] for x in progress}
    result=json.loads((directory/'RESULT.json').read_bytes()) if (directory/'RESULT.json').exists() else None
    started={p.name[:-len('.PROCESS.json')] for p in directory.glob('*.PROCESS.json')}
    row=dict(version=version,private_root=str(directory),result=result,completed_cases=progress,
             in_flight_or_unreceipted_cases=sorted(started-terminal),batch_finished=result is not None)
    jobs.append(row)
    for name in ('PROGRESS.json','RESULT.json'):
        if (directory/name).exists():capture(directory/name,'private-evidence/'+(directory/name).relative_to(S).as_posix())
    # Capture only closed cases, never a changing/partial active case.
    for case in sorted(terminal):
        for suffix in ('.json','.PROCESS.json','.stdout','.stderr'):
            path=directory/(case+suffix)
            if path.exists():capture(path,'private-evidence/'+path.relative_to(S).as_posix())
        for path in sorted((directory/case).rglob('*')):
            if path.is_file():capture(path,'private-evidence/'+path.relative_to(S).as_posix())

proof=json.loads((T/'build/squidpy-handover-catalog-verify-136/COMPLETED.json').read_bytes())
capture(T/'build/squidpy-handover-catalog-verify-136/COMPLETED.json','catalog/COMPLETED.json')
save(D/'JOBS-AT-SNAPSHOT.json',dict(captured_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),jobs=jobs,
    instruction='Snapshot, not a claim that active jobs ended. Inspect the private PROGRESS/RESULT and exact .PROCESS records before any restart. Frozen compile-5 classes/resources are independent of relocated workspace drafts. Never kill unrelated workers.'))
save(D/'EVIDENCE.json',dict(files=files,selection='All artificial original probes, prototype compile contexts and smoke captures; only closed batch cases at snapshot. Cache/runtime binaries and full primary text excluded.'))
with zipfile.ZipFile(D/'evidence.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for digest,raw in sorted(objects.items()):
        info=zipfile.ZipInfo('objects/'+digest,(2026,11,22,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;archive.writestr(info,raw,compresslevel=9)
assert (D/'evidence.zip').stat().st_size<80_000_000
probes={}
for version in ('1.1.2','1.2.2','1.8.4'):
    result=json.loads((S/f'operations-probes-{version}-2/RESULT.json').read_bytes())
    probes[version]={k:result[k] for k in ('completed','failed')}
compile_result=json.loads((S/'workflow-compile-5/RESULT.json').read_bytes())
status=dict(schema_version=1,status='UNQUALIFIED_WORKFLOW_DEVELOPMENT_HANDOVER',base='abb43f59fe5b642431a173f3c2c62f4a6c040184',
    draft_files=14,java_main_sources=7,prototype_contract_tests=13,prototype_compile=compile_result,
    source_operation_probes=probes,workflow_cases_snapshotted=sum(len(j['completed_cases']) for j in jobs),
    workflow_case_expectation_mismatches=sum(c['exit_code']!=c['expected'] for j in jobs for c in j['completed_cases']),
    active_or_unreceipted_batches=[j['version'] for j in jobs if not j['batch_finished']],
    normal_project_build_run_for_draft=False,packaged_full_adapter_qualified=False,global_command_enabled=False,
    independent_direct_original_oracle_finished=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False,
    catalog=dict(papers=250,scoped_families=151,native_families=27,upstream_families=124,reviewed_uses=1679,pending_uses=1043,positive_observations=2722),
    native_qualified_component_preserved=True,evidence_index=pin(D/'EVIDENCE.json'),evidence_archive=pin(D/'evidence.zip'),
    private_linux_runtime_root='/var/tmp/omicsmatic-squidpy-workflow-20261122-134',
    private_evidence_root=str(S),full_private_machine_backup=False)
save(D/'STATUS.json',status)
save(O/'RELOCATION-PLAN.json',dict(files={str(T/n):dict(destination=str(D/x['saved_as']),bytes=x['bytes'],sha256=x['sha256']) for n,x in drafts.items()},
    recursive_delete=False,only_untracked_current_drafts=True,active_jobs_use_frozen_compile_5=True))
save(O/'PREPARED.json',dict(status='RESTORABLE_UNQUALIFIED_WORKFLOW_SNAPSHOT_PREPARED',
    drafts=14,helpers=len(helpers),logical_evidence_files=len(files),unique_objects=len(objects),
    evidence_archive_bytes=(D/'evidence.zip').stat().st_size,job_snapshot=status['active_or_unreceipted_batches'],
    workflow_cases_snapshotted=status['workflow_cases_snapshotted']))
print(json.dumps(json.loads((O/'PREPARED.json').read_bytes())),flush=True)
