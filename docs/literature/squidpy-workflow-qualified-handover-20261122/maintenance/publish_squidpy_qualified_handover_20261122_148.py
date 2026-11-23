from pathlib import Path
import concurrent.futures,hashlib,importlib.util,io,json,re,shutil,subprocess,sys,tarfile,time,urllib.request,zipfile
from handover_inventory_20261122_111 import snapshot,save,pin,git

R=Path('D:/projects/JLinAlg');T=R/'build/fu-1'
O=R/'build/git-publish/20261122-squidpy-qualified-handover-148'
H=T/'docs/literature/squidpy-workflow-qualified-handover-20261122'
BASE='ffe594faa3d69ccf388088c0facbb25738a95445';BRANCH='codex/squidpy-qualification-20261122'
before=json.loads((O/'WORKTREES-BEFORE.json').read_bytes());indexes=json.loads((O/'INDEXES-BEFORE.json').read_bytes())
assert git('rev-parse','HEAD',cwd=T).decode().strip()==BASE
assert git('branch','--show-current',cwd=T).decode().strip()==BRANCH
assert git('ls-remote','origin','refs/heads/main',cwd=T).decode().split()[0]==BASE
assert not git('diff','--cached','--name-only',cwd=T).strip()

def preserve(label):
    current=snapshot();by={r['worktree']:r for r in current}
    assert len(current)==19 and all(by[r['worktree']]==r for r in before if Path(r['worktree'])!=T)
    for name,row in indexes.items():
        if Path(name)!=T:assert pin(Path(row['path']))==row['identity']
    save(O/(label+'.json'),current)
    return current

def seal():
    save(H/'FILES.json',{p.relative_to(H).as_posix():pin(p) for p in sorted(H.rglob('*')) if p.is_file() and p.name!='FILES.json' and '__pycache__' not in p.parts})

def checkpoint(label):
    p=subprocess.run([sys.executable,'-X','utf8','-B',str(H/'verify_checkpoint.py')],cwd=T,capture_output=True,timeout=60)
    (O/(label+'.stdout')).write_bytes(p.stdout);(O/(label+'.stderr')).write_bytes(p.stderr)
    assert p.returncode==0,p.stderr
    return json.loads(p.stdout)

for name in ('publish_squidpy_qualified_handover_20261122_148.py',):shutil.copyfile(R/'build'/name,H/'maintenance'/name)
save(H/'PRESERVATION.json',dict(registered_worktrees=19,other18_dirty_bytes_heads_branches_and_indexes_unchanged=True,
    private_before_snapshot=str(O/'WORKTREES-BEFORE.json'),private_index_snapshot=str(O/'INDEXES-BEFORE.json'),
    historical_sensitive_exports_not_included=True,root_bulk_staged_or_reset=False,private_runtime_or_full_paper_text_uploaded=False))
seal();proof=checkpoint('prepublication-reader');current=preserve('WORKTREES-PREPUBLICATION')
own=next(r for r in current if Path(r['worktree'])==T);allowed={r['path']:r['content'] for r in own['status']}
exact={'.gitattributes','HANDOVER_2026-11-22.md','docs/literature/HANDOVER_CURRENT.md',
       'docs/literature/SQUIDPY_QUALIFIED_HANDOVER_2026-11-22.md','docs/literature/SQUIDPY_WORKFLOWS.md',
       'src/main/java/org/jlinalg/cli/JLinAlgCli.java','src/test/resources/org/jlinalg/squidpy/primitive-npy-original.zip',
       'tools/literature/audit_squidpy_sources.py','tools/literature/verify_squidpy_workflow_capture.py','tools/literature/verify_squidpy_workflow_checkpoint.py'}
prefixes=('docs/literature/squidpy-workflow-qualification/','docs/literature/squidpy-workflow-qualified-handover-20261122/',
          'examples/squidpy-original/','src/main/java/org/jlinalg/cli/SquidpyWorkflow',
          'src/test/java/org/jlinalg/cli/SquidpyWorkflow','src/main/resources/org/jlinalg/omics/squidpy-workflow/',
          'tools/literature/squidpy_workflow/','tools/literature/squidpy_catalog/')
assert all(n in exact or n.startswith(prefixes) for n in allowed),set(allowed)-exact
assert not git('diff',BASE,'--','.github','site','docs/literature/omics-2023-2026','tools/literature/reviewed-uses.json',cwd=T).strip()
patterns=[rb'-----BEGIN [A-Z ]*PRIVATE KEY-----',rb'gh[pousr]_[A-Za-z0-9]{30,}',rb'github_pat_[A-Za-z0-9_]{30,}',
          rb'AKIA[0-9A-Z]{16}',rb'(?im)^set-cookie\s*:',rb'(?im)^authorization\s*:\s*(?:bearer|basic)\s+\S+']
findings=[];zip_members=0
for name in allowed:
    parts=[(name,(T/name).read_bytes())]
    if name.endswith('.zip'):
        with zipfile.ZipFile(T/name) as z:
            for member in z.namelist():parts.append((name+'!'+member,z.read(member)));zip_members+=1
    for label,raw in parts:
        for pattern in patterns:
            if re.search(pattern,raw):findings.append(dict(path=label,pattern=pattern.decode()))
assert not findings,findings
links=[]
for name in ('docs/literature/SQUIDPY_QUALIFIED_HANDOVER_2026-11-22.md',
             'docs/literature/squidpy-workflow-qualified-handover-20261122/README.md',
             'tools/literature/squidpy_catalog/README.md'):
    for target in re.findall(r'\]\(([^)]+)\)',(T/name).read_text(encoding='utf-8')):
        if not re.match(r'^[a-z]+:',target) and not target.startswith('#'):
            assert ((T/name).parent/target.split('#')[0]).exists(),(name,target)
            links.append(dict(source=name,target=target))
save(O/'PREPUBLICATION.json',dict(reader=proof,files=len(allowed),sensitive_pattern_findings=findings,
    zip_members_scanned=zip_members,links_checked=links,other18_worktrees_and_indexes_preserved=True,
    canonical_catalog_and_site_unchanged=True,full_checkWebsite_passed=False,preexisting_missing_citation_panels=33,
    catalog_admission_complete=False,whole_goal_complete=False))
save(O/'PUBLICATION-FILES.json',allowed)

def commit_selected(names,message,expected_base,tag):
    identities={n:pin(T/n) for n in names}
    assert git('rev-parse','HEAD',cwd=T).decode().strip()==expected_base
    assert not git('diff','--cached','--name-only',cwd=T).strip()
    git('add','--',*sorted(names),cwd=T)
    assert set(git('diff','--cached','--name-only',cwd=T).decode().splitlines())==set(names)
    for name,expected in identities.items():
        raw=git('show',':'+name,cwd=T);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==expected,name
    git('diff','--cached','--check',cwd=T)
    git('commit','-m',message,cwd=T)
    commit=git('rev-parse','HEAD',cwd=T).decode().strip();tree=git('rev-parse','HEAD^{tree}',cwd=T).decode().strip()
    assert git('rev-parse','HEAD^',cwd=T).decode().strip()==expected_base
    archive_path=O/(tag+'-source.tar')
    with archive_path.open('xb') as out:p=subprocess.run(['git','archive',commit,'--',*sorted(names)],cwd=T,stdout=out,stderr=subprocess.PIPE)
    assert p.returncode==0,p.stderr
    with tarfile.open(archive_path) as archive:
        members=[m for m in archive if m.isfile()];assert {m.name for m in members}==set(names)
        for member in members:
            raw=archive.extractfile(member).read();assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==identities[member.name],member.name
    save(O/(tag+'-COMMITTED.json'),dict(commit=commit,tree=tree,base=expected_base,files=len(names),archive_bytes_exact=True))
    return commit,tree,identities

def fetch(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-reviewed-handover','Cache-Control':'no-cache'}),timeout=35) as response:return response.read()
        except Exception:
            if attempt==2:raise
            time.sleep(1)

def push_and_readback(commit,tree,identities,expected_base,tag):
    assert git('ls-remote','origin','refs/heads/main',cwd=T).decode().split()[0]==expected_base
    with (O/(tag+'-push.stdout')).open('xb') as out,(O/(tag+'-push.stderr')).open('xb') as err:
        p=subprocess.run(['git','push','--atomic','origin','HEAD:refs/heads/main','HEAD:refs/heads/'+BRANCH],cwd=T,stdout=out,stderr=err,timeout=120)
    assert p.returncode==0,(O/(tag+'-push.stderr')).read_text(errors='replace')
    refs=git('ls-remote','origin','refs/heads/main','refs/heads/'+BRANCH,cwd=T).decode().splitlines()
    assert len(refs)==2 and all(row.split()[0]==commit for row in refs)
    api='https://api.github.com/repos/robbyjo/OmicsMatic'
    assert json.loads(fetch(api+'/git/ref/heads/main?nocache='+commit))['object']['sha']==commit
    assert json.loads(fetch(api+'/git/commits/'+commit+'?nocache='+commit))['tree']['sha']==tree
    def check(name):
        raw=fetch('https://raw.githubusercontent.com/robbyjo/OmicsMatic/'+commit+'/'+name)
        identity=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());assert identity==identities[name],name
        return name,identity
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:public=dict(pool.map(check,sorted(identities)))
    save(O/(tag+'-PUBLIC-READBACK.json'),dict(commit=commit,files=public))
    print(json.dumps(dict(status='PUSHED_MAIN_AND_ALL_PUBLIC_BYTES_VERIFIED',phase=tag,commit=commit,files=len(public))),flush=True)

commit,tree,identities=commit_selected(allowed,'Qualify Squidpy workflows and document remaining handover work',BASE,'qualified')
push_and_readback(commit,tree,identities,BASE,'qualified')
preserve('WORKTREES-AFTER-FIRST-PUSH')
assert not git('status','--porcelain=v1','-uall',cwd=T).strip()

# No scientific inputs/results/runtimes are deleted. Verify every duplicate's
# exact retained replacement before writing a narrowly bounded cleanup program.
plan=json.loads((H/'CLEANUP-PLAN.json').read_bytes());C=Path(plan['root'])
assert C==O/'reader-controls' and pin(H/'control-recovery.zip')==plan['archive']
with zipfile.ZipFile(H/'control-recovery.zip') as z:
    for row in plan['files']:
        path=Path(row['path']);assert path.is_relative_to(C) and pin(path)=={k:row[k] for k in ('bytes','sha256')}
        recovery=row['recovery'];raw=(T/recovery['path']).read_bytes() if recovery['kind']=='published_source' else z.read(recovery['member'])
        assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())=={k:row[k] for k in ('bytes','sha256')}
cleanup=O/'cleanup-controls.ps1'
cleanup.write_text(r'''$ErrorActionPreference='Stop'
$receiptRoot='D:\projects\JLinAlg\build\git-publish\20261122-squidpy-qualified-handover-148'
$packet='D:\projects\JLinAlg\build\fu-1\docs\literature\squidpy-workflow-qualified-handover-20261122'
$plan=Get-Content -LiteralPath (Join-Path $packet 'CLEANUP-PLAN.json') -Raw | ConvertFrom-Json
$target=[IO.Path]::GetFullPath($plan.root)
if($target -ne 'D:\projects\JLinAlg\build\git-publish\20261122-squidpy-qualified-handover-148\reader-controls'){throw 'Unexpected cleanup root'}
$nodes=@(Get-Item -LiteralPath $target -Force)+@(Get-ChildItem -LiteralPath $target -Recurse -Force)
foreach($node in $nodes){
    if(-not ($node.FullName -eq $target -or $node.FullName.StartsWith($target+'\',[StringComparison]::OrdinalIgnoreCase))){throw 'Cleanup path escape'}
    if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'Reparse point in cleanup root'}
}
if(@($nodes | Where-Object {-not $_.PSIsContainer}).Count -ne @($plan.files).Count){throw 'Unexpected additional files; preserve'}
$logical=0L
foreach($entry in $plan.files){
    $file=[IO.Path]::GetFullPath($entry.path)
    if(-not $file.StartsWith($target+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Removal target escape'}
    $bytes=[IO.File]::ReadAllBytes($file);$algorithm=[Security.Cryptography.SHA256]::Create()
    try{$digest=[BitConverter]::ToString($algorithm.ComputeHash($bytes)).Replace('-','').ToLowerInvariant()}finally{$algorithm.Dispose()}
    if($bytes.LongLength -ne $entry.bytes -or $digest -ne $entry.sha256){throw 'Control copy changed; preserve'}
    $logical+=$entry.bytes
}
$before=[IO.DriveInfo]::new('D:\').AvailableFreeSpace
foreach($entry in $plan.files){Remove-Item -LiteralPath $entry.path -Force}
$dirs=@(Get-ChildItem -LiteralPath $target -Directory -Recurse -Force | Sort-Object {$_.FullName.Length} -Descending)
foreach($dir in $dirs){if(@(Get-ChildItem -LiteralPath $dir.FullName -Force).Count -eq 0){Remove-Item -LiteralPath $dir.FullName -Force}}
if(@(Get-ChildItem -LiteralPath $target -Force).Count -ne 0){throw 'Unexpected remaining files; preserve'}
Remove-Item -LiteralPath $target -Force
$after=[IO.DriveInfo]::new('D:\').AvailableFreeSpace
@{status='VERIFIED_REDUNDANT_READER_CONTROL_COPIES_REMOVED';files_removed=@($plan.files).Count;logical_bytes_removed=$logical;free_bytes_before=$before;free_bytes_after=$after;measured_free_space_change=$after-$before;root=$target;recursive_delete=$false;scientific_evidence_runtimes_frozen_producers_and_unique_work_preserved=$true} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $receiptRoot 'CLEANUP-RESULT.json') -Encoding UTF8
''',encoding='utf-8')
p=subprocess.run(['C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-File',str(cleanup)],cwd=T,capture_output=True,timeout=60)
(O/'cleanup.stdout').write_bytes(p.stdout);(O/'cleanup.stderr').write_bytes(p.stderr);assert p.returncode==0,p.stderr
result=json.loads((O/'CLEANUP-RESULT.json').read_bytes());assert not C.exists()
save(H/'CLEANUP.json',dict(result=result,recovery_plan='CLEANUP-PLAN.json',negative_bytes_archive='control-recovery.zip',
    source_bytes_verified_on_GitHub=True,publication_commit=commit,all18_other_worktrees_and_indexes_preserved=True,
    full_original_failed_scientific_captures_runtimes_and_frozen_contexts_retained=True))
with (T/'docs/literature/SQUIDPY_QUALIFIED_HANDOVER_2026-11-22.md').open('a',encoding='utf-8',newline='') as f:
    f.write('\nAfter publication and byte-exact GitHub readback, cleanup removed'+str(result['files_removed'])+
            ' redundant\nreader-control copies ('+str(result['logical_bytes_removed'])+' logical bytes; measured free-space change '+
            str(result['measured_free_space_change'])+' bytes).\nExact recovery mappings and preservation checks are recorded in the packet\'s\n`CLEANUP.json` and `CLEANUP-PLAN.json`. No scientific evidence or runtime was removed.\n')
seal();checkpoint('postcleanup-reader');preserve('WORKTREES-AFTER-CLEANUP')
names=[row[3:] for row in git('status','--porcelain=v1','-uall',cwd=T).decode().splitlines()]
assert set(names)=={'docs/literature/SQUIDPY_QUALIFIED_HANDOVER_2026-11-22.md',
    'docs/literature/squidpy-workflow-qualified-handover-20261122/FILES.json',
    'docs/literature/squidpy-workflow-qualified-handover-20261122/CLEANUP.json'}
final,tree,identities=commit_selected(names,'Record verified handover copy cleanup and preservation',commit,'cleanup')
push_and_readback(final,tree,identities,commit,'cleanup')
preserve('WORKTREES-FINAL');checkpoint('final-reader')
assert not git('status','--porcelain=v1','-uall',cwd=T).strip()
save(O/'COMPLETED.json',dict(status='PUSHED_REVIEWED_CURRENT_WORK_TO_MAIN_WITH_EXACT_PUBLIC_READBACK',commit=final,
    qualification_commit=commit,reviewed_current_files=len(allowed),cleanup=result,other18_worktrees_and_indexes_preserved=True,
    catalog_admission_complete=False,historical_sensitive_snapshot_exports_withheld=True,whole_goal_complete=False))
print(json.dumps(dict(status='HANDOVER_AND_PUBLICATION_COMPLETE',commit=final,qualification_commit=commit,
    reviewed_current_files=len(allowed),removed_temporary_copies=result['files_removed'],logical_bytes_removed=result['logical_bytes_removed'],
    measured_free_space_change=result['measured_free_space_change'])),flush=True)
