from pathlib import Path
import ast,concurrent.futures,hashlib,importlib.util,io,json,os,re,shutil,subprocess,sys,time,zipfile
from handover_inventory_20261122_111 import snapshot,save,pin,git

R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';S=T/'build/squidpy-1'
O=R/'build/git-publish/20261122-squidpy-qualified-handover-148'
H=T/'docs/literature/squidpy-workflow-qualified-handover-20261122'
Q=T/'docs/literature/squidpy-workflow-qualification'
BASE='ffe594faa3d69ccf388088c0facbb25738a95445'
DISTRO='JLinAlg-Omics-SAIGE-20261026'
assert git('rev-parse','HEAD',cwd=T).decode().strip()==BASE
assert git('branch','--show-current',cwd=T).decode().strip()=='codex/squidpy-qualification-20261122'
O.mkdir();H.mkdir();(H/'checks').mkdir();(H/'maintenance').mkdir()
before=snapshot();save(O/'WORKTREES-BEFORE.json',before)
prior=json.loads((S/'workflow-iteration-137/WORKTREES-BEFORE.json').read_bytes())
by={x['worktree']:x for x in before}
assert len(before)==19 and all(by[x['worktree']]==x for x in prior if Path(x['worktree'])!=T)
indexes={}
for state in before:
    cwd=Path(state['worktree']);name=git('rev-parse','--git-path','index',cwd=cwd).decode().strip()
    path=Path(name);path=path if path.is_absolute() else cwd/path
    indexes[state['worktree']]=dict(path=str(path.resolve()),identity=pin(path))
save(O/'INDEXES-BEFORE.json',indexes)
status=json.loads((Q/'STATUS.json').read_bytes());jar=Path(status['producer']['path'])
assert pin(jar)=={k:status['producer'][k] for k in ('bytes','sha256')}
sys.path.insert(0,str(T/'tools/literature'))
from fusion_catalog.model import META
baseline={n:pin(T/n) for n in META};save(H/'CATALOG-BASELINE.json',dict(base=BASE,files=baseline))

def linux(path):
    value=Path(path).as_posix();return '/mnt/'+value[0].lower()+value[2:] if len(value)>1 and value[1]==':' else value

def call(label,command,expected=0):
    started=time.monotonic();p=subprocess.run(command,cwd=T,capture_output=True,timeout=60)
    (H/'checks'/(label+'.stdout')).write_bytes(p.stdout);(H/'checks'/(label+'.stderr')).write_bytes(p.stderr)
    result=dict(case=label,exit_code=p.returncode,expected=expected,seconds=time.monotonic()-started,command=command)
    assert p.returncode==expected,(label,p.returncode,p.stderr[-1200:])
    return result

py=[sys.executable,'-X','utf8','-B']
checks=[]
jobs=[
 ('workflow-checkpoint-windows',py+[str(T/'tools/literature/verify_squidpy_workflow_checkpoint.py'),'--jar',str(jar)]),
 ('workflow-checkpoint-linux',['wsl.exe','-d',DISTRO,'--','python3','-B',linux(T/'tools/literature/verify_squidpy_workflow_checkpoint.py')]),
 ('native-checkpoint-windows',py+[str(T/'tools/literature/verify_squidpy_native_checkpoint.py'),'--jar',str(S/'native-normal-build-1/project-build/cli/omicsmatic-0.4.0.jar')]),
 ('canonical-catalog-windows',py+[str(T/'tools/literature/close_fusion_catalog.py'),'--verify','--out',str(T/'build/squidpy-handover-catalog-148')]),
 ('fresh-all250-primary',py+[str(T/'tools/literature/audit_squidpy_sources.py'),'--cache',str(R/'build/literature-review/xml'),'--out',str(T/'build/squidpy-handover-source-148')]),
]
with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
    for result in pool.map(lambda row:call(*row),jobs):checks.append(result)
assert json.loads((T/'build/squidpy-handover-source-148/ROLES.json').read_bytes())==json.loads((T/'build/squidpy-source-roles-145/ROLES.json').read_bytes())
shutil.copyfile(T/'build/squidpy-handover-source-148/ROLES.json',H/'ROLES.json')
shutil.copyfile(T/'build/squidpy-handover-catalog-148/COMPLETED.json',H/'CATALOG-VERIFIED.json')
drafts=[]
for name in ('tools/literature/audit_squidpy_sources.py','tools/literature/squidpy_catalog/__init__.py','tools/literature/squidpy_catalog/primary.py','tools/literature/squidpy_catalog/model.py'):
    ast.parse((T/name).read_text(encoding='utf-8'),filename=name);drafts.append(dict(path=name,**pin(T/name)))
save(H/'DRAFT-SYNTAX.json',dict(status='PARSED_ONLY_NOT_ADMITTED_OR_TRANSACTION_TESTED',files=drafts))

# Exercise the saved qualification reader against byte/claim contradictions.
C=O/'reader-controls';C.mkdir();controls=[];mutated={}
reader=T/'tools/literature/verify_squidpy_workflow_checkpoint.py'
for case in ('false-count','changed-source','changed-archive','resealed-index'):
    root=C/case;root.mkdir()
    for name in status['source_code']:
        dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/name,dest)
    for name in ('STATUS.json','EVIDENCE.json','evidence.zip'):
        dest=root/'docs/literature/squidpy-workflow-qualification'/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(Q/name,dest)
    q=root/'docs/literature/squidpy-workflow-qualification'
    if case=='false-count':
        value=json.loads((q/'STATUS.json').read_bytes());value['packaged_cases']=74;save(q/'STATUS.json',value)
    elif case=='changed-source':
        path=root/'tools/literature/squidpy_workflow/operations.py';path.write_bytes(path.read_bytes()+b'\n# Contradict the qualified source identity.\n')
    elif case=='changed-archive':
        path=q/'evidence.zip';data=bytearray(path.read_bytes());data[len(data)//2]^=1;path.write_bytes(data)
    else:
        path=q/'EVIDENCE.json';value=json.loads(path.read_bytes());name=sorted(value['files'])[0];value['files'][name]['sha256']='0'*64;save(path,value)
        value=json.loads((q/'STATUS.json').read_bytes());value['evidence_index']=pin(path);save(q/'STATUS.json',value)
    for host in ('windows','linux'):
        target=str(root) if host=='windows' else linux(root)
        source=str(reader) if host=='windows' else linux(reader)
        code=('import importlib.util,json;from pathlib import Path;'+
              's=importlib.util.spec_from_file_location("saved",'+repr(source)+');'+
              'm=importlib.util.module_from_spec(s);s.loader.exec_module(m);'+
              'print(json.dumps(m.verify(Path('+repr(target)+'))))')
        command=py+['-c',code] if host=='windows' else ['wsl.exe','-d',DISTRO,'--','python3','-B','-c',code]
        controls.append(call('checkpoint-'+case+'-'+host,command,1))
for host in ('windows','linux'):
    controls.append(next(x for x in checks if x['case']=='workflow-checkpoint-'+host))
save(H/'READER-CONTROLS.json',dict(status='PASS_TWO_HOSTS_FOUR_CONTRADICTIONS_PLUS_VALID',checks=10,cases=controls,
    original_analysis_rerun=False,byte_integrity_is_not_host_authentication=True))

# Exact negative-copy recovery, for post-publication removal of redundant copies.
entries=[];objects={}
for path in sorted(C.rglob('*')):
    assert not path.is_symlink()
    if not path.is_file():continue
    relative=Path(*path.relative_to(C).parts[1:]);identity=pin(path);original=T/relative
    if original.is_file() and pin(original)==identity:
        recovery=dict(kind='published_source',path=relative.as_posix())
    else:
        raw=path.read_bytes();objects[identity['sha256']]=raw
        recovery=dict(kind='negative_control_archive',member='objects/'+identity['sha256'])
    entries.append(dict(path=str(path),**identity,recovery=recovery))
with zipfile.ZipFile(H/'control-recovery.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for digest,raw in sorted(objects.items()):
        info=zipfile.ZipInfo('objects/'+digest,(2026,11,22,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw,compresslevel=9)
save(H/'CLEANUP-PLAN.json',dict(root=str(C),files=entries,archive=pin(H/'control-recovery.zip'),
    scope='Only completed copied qualification-reader negative controls. Original science, previous failures, runtimes, producer and worktrees excluded.'))
save(H/'CHECKS.json',dict(status='PASS_HANDOVER_SAVED_CHECKPOINT_AND_SOURCE_CHECKS',cases=checks,
    total_registered_worktrees=19,other18_worktrees_unchanged=True,whole_goal_complete=False,
    scoped_families=151,native=27,upstream=124,reviewed_uses=1679,pending_uses=1043))
inventory=json.loads((T/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes())
gaps=sorted([dict(id=r['id'],name=r['name'],papers=r['paper_count'],status=r['implementation_status']) for r in inventory if r['implementation_status'] in ('missing','method-overlap')],key=lambda r:(-r['papers'],r['id']))
save(H/'REMAINING-CANDIDATES.json',dict(status='MECHANICAL_CANDIDATES_REQUIRE_SOURCE_ROLE_AND_EXISTING_PROVIDER_AUDIT',top_30=gaps[:30],
    note='Counts are catalog observations, not an automatic work order. Aliases, wrappers, reused outputs, method baselines, commercial restrictions and useful scope need review before implementation.'))
for name in ('bundle_squidpy_qualification_20261122_147.py','prepare_squidpy_qualified_handover_20261122_148.py','handover_inventory_20261122_111.py'):
    shutil.copyfile(R/'build'/name,H/'maintenance'/name)
assert all(pin(T/n)==identity for n,identity in baseline.items())
after=snapshot();by={x['worktree']:x for x in after};assert all(by[x['worktree']]==x for x in before if Path(x['worktree'])!=T)
for record in indexes.values():assert pin(Path(record['path']))==record['identity']
save(O/'WORKTREES-AFTER-CHECKS.json',after)
print(json.dumps(dict(status='HANDOVER_CHECKS_SAVED',checks=len(checks),reader_controls=len(controls),cleanup_files=len(entries),cleanup_bytes=sum(x['bytes'] for x in entries))),flush=True)
