from pathlib import Path
import concurrent.futures,hashlib,importlib.util,json,shutil,subprocess,sys,zipfile
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261122-squidpy-catalog-149'
Q=T/'docs/literature/squidpy-catalog-checkpoint';D=T/'build/squidpy-catalog-149/checkpoint-reader-150';D.mkdir()
reader=T/'tools/literature/verify_squidpy_catalog_checkpoint.py';status=json.loads((Q/'STATUS.json').read_bytes())
with zipfile.ZipFile(Q/'reader-draft1.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for name in ('READER-DRAFT1.py','STATUS-DRAFT1.json','VERIFIED-DRAFT1.json'):z.write(O/name,name)
save(Q/'READER-REFINEMENT.json',dict(reason='Bind reported status counters explicitly to the independently checked evidence counts before publication.',
    previous_reader=pin(O/'READER-DRAFT1.py'),current_reader=pin(reader),retained_preliminary_context='reader-draft1.zip',
    scientific_source_inputs_or_results_changed=False,historical_published_qualification_rehashed=False))
status['source_code']['tools/literature/verify_squidpy_catalog_checkpoint.py']=pin(reader)
for name in ('reader-draft1.zip','READER-REFINEMENT.json'):status['source_code']['docs/literature/squidpy-catalog-checkpoint/'+name]=pin(Q/name)
save(Q/'STATUS.json',status)
for case in ('false-count','changed-source','changed-archive'):
    root=D/case;q=root/'docs/literature/squidpy-catalog-checkpoint';q.mkdir(parents=True)
    save(q/'STATUS.json',status)
    if case=='false-count':
        value=dict(status,tests_per_host=999);save(q/'STATUS.json',value)
    elif case=='changed-source':
        name=next(iter(status['source_code']));dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes((T/name).read_bytes()+b'\n# contradict the source pin\n')
    else:
        for name in status['source_code']:
            dest=root/name;dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(T/name,dest)
        shutil.copyfile(Q/'EVIDENCE.json',q/'EVIDENCE.json');data=bytearray((Q/'evidence.zip').read_bytes());data[len(data)//2]^=1;(q/'evidence.zip').write_bytes(data)
records=[]
def linux(path):
    value=str(path).replace('\\','/');return '/mnt/'+value[0].lower()+value[2:] if len(value)>1 and value[1]==':' else value
def run(row):
    host,case,root,expected=row;directory=D/(host+'-'+case);directory.mkdir()
    if case=='valid':
        args=[str(reader)];code=None
    else:
        source=str(reader) if host=='windows' else linux(reader);target=str(root) if host=='windows' else linux(root)
        code=('import importlib.util,json;from pathlib import Path;s=importlib.util.spec_from_file_location("saved",'+repr(source)+');'+
              'm=importlib.util.module_from_spec(s);s.loader.exec_module(m);print(json.dumps(m.verify(Path('+repr(target)+'))))')
    if host=='windows':command=[sys.executable,'-X','utf8','-B',*(['-c',code] if code else args)]
    else:command=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd',linux(T),'--','env','GIT_DIR=/mnt/d/projects/JLinAlg/.git/worktrees/fu-1','GIT_WORK_TREE='+linux(T),'GIT_OPTIONAL_LOCKS=0','python3','-B',*(['-c',code] if code else [linux(reader)])]
    process=subprocess.run(command,cwd=T,capture_output=True,timeout=60)
    (directory/'stdout').write_bytes(process.stdout);(directory/'stderr').write_bytes(process.stderr)
    assert process.returncode==expected,(host,case,process.returncode,process.stderr[-1200:])
    if case=='false-count':assert b'Reported checkpoint counts differ' in process.stderr
    if case=='changed-source':assert next(iter(status['source_code'])).encode() in process.stderr
    if case=='changed-archive':assert b'AssertionError' in process.stderr and b'FileNotFoundError' not in process.stderr
    return dict(host=host,case=case,exit_code=process.returncode,expected=expected)
rows=[(host,case,T if case=='valid' else D/case,0 if case=='valid' else 1) for host in ('windows','linux') for case in ('valid','false-count','changed-source','changed-archive')]
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:records=list(pool.map(run,rows))
save(Q/'READER-CONTROLS.json',dict(status='PASS_TWO_HOSTS_VALID_AND_THREE_CHECKPOINT_CONTRADICTIONS',checks=8,cases=records,
    original_analysis_reexecuted=False,byte_integrity_not_host_authentication=True))
status['source_code']['docs/literature/squidpy-catalog-checkpoint/READER-CONTROLS.json']=pin(Q/'READER-CONTROLS.json');save(Q/'STATUS.json',status)
# Retain every altered temporary control byte in an exact-recovery packet; only
# redundant copies are eligible for removal after publication.
entries=[];objects={}
for case in ('false-count','changed-source','changed-archive'):
    base=D/case
    for path in sorted(base.rglob('*')):
        assert not path.is_symlink()
        if not path.is_file():continue
        relative=path.relative_to(base);identity=pin(path);original=T/relative
        if original.is_file() and pin(original)==identity:recovery=dict(kind='published_source',path=relative.as_posix())
        else:
            raw=path.read_bytes();objects[identity['sha256']]=raw;recovery=dict(kind='control_archive',member='objects/'+identity['sha256'])
        entries.append(dict(path=str(path),**identity,recovery=recovery))
with zipfile.ZipFile(Q/'reader-control-recovery.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for digest,raw in sorted(objects.items()):
        info=zipfile.ZipInfo('objects/'+digest,(2026,11,22,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw,compresslevel=9)
save(Q/'CLEANUP-PLAN.json',dict(roots=[str(D/c) for c in ('false-count','changed-source','changed-archive')],files=entries,
    recovery_archive=pin(Q/'reader-control-recovery.zip'),scope='Completed copied checkpoint-reader negative inputs only; scientific evidence, original failures, primary XML, profiles, runtimes, producers and worktrees excluded.'))
shutil.copyfile(R/'build/qualify_squidpy_catalog_reader_20261122_150.py',Q/'qualify_reader.py')
spec=importlib.util.spec_from_file_location('new_saved',reader);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
save(Q/'CHECKPOINT-VERIFIED.json',module.verify(T,T/'build/squidpy-1/workflow-normal-build-145/project-build/cli/omicsmatic-0.4.0.jar'))
print(json.dumps(dict(status='PASS_SQUIDPY_CATALOG_CHECKPOINT_READERS',checks=len(records),temporary_copy_files=len(entries),temporary_copy_bytes=sum(r['bytes'] for r in entries))),flush=True)
