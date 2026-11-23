from pathlib import Path
import concurrent.futures,hashlib,io,json,re,shutil,subprocess,sys,tarfile,time,urllib.request,zipfile
from handover_inventory_20261122_111 import snapshot,save,pin,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261122-squidpy-catalog-149'
Q=T/'docs/literature/squidpy-catalog-checkpoint';BRANCH='codex/squidpy-catalog-20261122'
published=json.loads((O/'PUBLISHED.json').read_bytes());BASE=published['commit']
pages=json.loads((O/'PAGES-PUBLIC-VERIFICATION.json').read_bytes());assert pages['commit']==BASE and pages['conclusion']=='success'
assert git('rev-parse','HEAD',cwd=T).decode().strip()==BASE and not git('status','--porcelain=v1','-uall',cwd=T).strip()
assert git('ls-remote','origin','refs/heads/main',cwd=T).decode().split()[0]==BASE
before=json.loads((O/'WORKTREES-BEFORE.json').read_bytes());indexes=json.loads((O/'INDEXES-BEFORE.json').read_bytes())
def preserve(label):
    current=snapshot();by={r['worktree']:r for r in current}
    assert len(current)==19 and all(by[r['worktree']]==r for r in before if Path(r['worktree'])!=T)
    for name,row in indexes.items():
        if Path(name)!=T:assert pin(Path(row['path']))==row['identity']
    save(O/(label+'.json'),current)
preserve('WORKTREES-PRECLEANUP')
plan=json.loads((Q/'CLEANUP-PLAN.json').read_bytes());root=T/'build/squidpy-catalog-149/checkpoint-reader-150'
assert [Path(p) for p in plan['roots']]==[root/n for n in ('false-count','changed-source','changed-archive')]
assert pin(Q/'reader-control-recovery.zip')==plan['recovery_archive']
with zipfile.ZipFile(Q/'reader-control-recovery.zip') as archive:
    for row in plan['files']:
        path=Path(row['path']);assert any(path.is_relative_to(Path(parent)) for parent in plan['roots'])
        assert pin(path)=={k:row[k] for k in ('bytes','sha256')}
        recovery=row['recovery'];raw=(T/recovery['path']).read_bytes() if recovery['kind']=='published_source' else archive.read(recovery['member'])
        assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())=={k:row[k] for k in ('bytes','sha256')}

cleanup=O/'cleanup-reader-copies.ps1'
cleanup.write_text("$ErrorActionPreference='Stop'\n$receiptRoot='D:\\projects\\JLinAlg\\build\\git-publish\\20261122-squidpy-catalog-149'\n$packet='D:\\projects\\JLinAlg\\build\\fu-1\\docs\\literature\\squidpy-catalog-checkpoint'\n$plan=Get-Content -LiteralPath (Join-Path $packet 'CLEANUP-PLAN.json') -Raw | ConvertFrom-Json\n$allowedBase='D:\\projects\\JLinAlg\\build\\fu-1\\build\\squidpy-catalog-149\\checkpoint-reader-150'\n$allowedNames=@('false-count','changed-source','changed-archive');$actualFiles=0L\nif(@($plan.roots).Count -ne 3){throw 'Unexpected cleanup root count'}\nforeach($entryRoot in $plan.roots){\n    $target=[IO.Path]::GetFullPath($entryRoot)\n    if([IO.Path]::GetDirectoryName($target) -ne $allowedBase -or [IO.Path]::GetFileName($target) -notin $allowedNames){throw 'Unexpected cleanup root'}\n    $nodes=@(Get-Item -LiteralPath $target -Force)+@(Get-ChildItem -LiteralPath $target -Recurse -Force)\n    foreach($node in $nodes){\n        if(-not ($node.FullName -eq $target -or $node.FullName.StartsWith($target+'\\',[StringComparison]::OrdinalIgnoreCase))){throw 'Cleanup path escape'}\n        if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'Reparse point in cleanup root'}\n        if(-not $node.PSIsContainer){$actualFiles++}\n    }\n}\nif($actualFiles -ne @($plan.files).Count){throw 'Unexpected extra files; preserve'}\n$logical=0L\nforeach($entry in $plan.files){\n    $file=[IO.Path]::GetFullPath($entry.path);$contained=$false\n    foreach($entryRoot in $plan.roots){$target=[IO.Path]::GetFullPath($entryRoot);if($file.StartsWith($target+'\\',[StringComparison]::OrdinalIgnoreCase)){$contained=$true}}\n    if(-not $contained){throw 'File removal target escape'}\n    $bytes=[IO.File]::ReadAllBytes($file);$algorithm=[Security.Cryptography.SHA256]::Create()\n    try{$digest=[BitConverter]::ToString($algorithm.ComputeHash($bytes)).Replace('-','').ToLowerInvariant()}finally{$algorithm.Dispose()}\n    if($bytes.LongLength -ne $entry.bytes -or $digest -ne $entry.sha256){throw 'Reader copy changed; preserve'}\n    $logical+=$entry.bytes\n}\n$before=[IO.DriveInfo]::new('D:\\').AvailableFreeSpace\nforeach($entry in $plan.files){Remove-Item -LiteralPath $entry.path -Force}\nforeach($entryRoot in $plan.roots){\n    $target=[IO.Path]::GetFullPath($entryRoot)\n    $dirs=@(Get-ChildItem -LiteralPath $target -Directory -Recurse -Force | Sort-Object {$_.FullName.Length} -Descending)\n    foreach($dir in $dirs){if(@(Get-ChildItem -LiteralPath $dir.FullName -Force).Count -eq 0){Remove-Item -LiteralPath $dir.FullName -Force}}\n    if(@(Get-ChildItem -LiteralPath $target -Force).Count -ne 0){throw 'Unexpected remaining bytes; preserve'}\n    Remove-Item -LiteralPath $target -Force\n}\n$after=[IO.DriveInfo]::new('D:\\').AvailableFreeSpace\n@{status='VERIFIED_REDUNDANT_CATALOG_READER_COPIES_REMOVED';files_removed=@($plan.files).Count;logical_bytes_removed=$logical;free_bytes_before=$before;free_bytes_after=$after;measured_free_space_change=$after-$before;roots=$plan.roots;recursive_delete=$false;scientific_evidence_runtimes_profiles_and_frozen_producers_preserved=$true} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $receiptRoot 'CLEANUP-RESULT.json') -Encoding UTF8\n",encoding='utf-8')
process=subprocess.run(['C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-File',str(cleanup)],cwd=T,capture_output=True,timeout=60)
(O/'cleanup.stdout').write_bytes(process.stdout);(O/'cleanup.stderr').write_bytes(process.stderr);assert process.returncode==0,process.stderr
result=json.loads((O/'CLEANUP-RESULT.json').read_bytes());assert all(not Path(p).exists() for p in plan['roots'])
save(Q/'CLEANUP.json',dict(result=result,recovery_plan='CLEANUP-PLAN.json',negative_copy_archive='reader-control-recovery.zip',
    other18_worktrees_and_indexes_preserved=True,original_failed_evidence_runtimes_profiles_frozen_contexts_retained=True,
    removed_only_completed_redundant_checkpoint_reader_input_copies=True))
shutil.copyfile(O/'PAGES-PUBLIC-VERIFICATION.json',Q/'PAGES-PUBLIC-VERIFICATION.json')
save(Q/'PUBLICATION.json',dict(admission_commit=BASE,selected_public_files=published['files'],all_selected_archive_and_GitHub_raw_bytes_equal=True,
    main_and_source_branch_refs_verified=True,exact_commit_Pages_run_id=pages['run_id'],Pages_conclusion='success',
    unfinished_reports_withheld=True,historical_sensitive_exports_withheld=True,whole_goal_complete=False))
shutil.copyfile(R/'build/finish_squidpy_catalog_publication_20261122_151.py',Q/'finish_publication.py')
doc=T/'docs/literature/SQUIDPY_CATALOG_CHECKPOINT_2026-11-22.md'
with doc.open('a',encoding='utf-8',newline='') as stream:
    stream.write('\nThe admission was published at `'+BASE+'`; every selected public file matched\nthe exact local Git archive. Its exact-commit Pages build/deploy passed, with\nthe two unfinished reports still404. After publication, cleanup removed'+str(result['files_removed'])+'\nredundant reader-input copies ('+str(result['logical_bytes_removed'])+' logical bytes;\nmeasured free-space change '+str(result['measured_free_space_change'])+' bytes). See the packet\'s\n`PUBLICATION.json`, `PAGES-PUBLIC-VERIFICATION.json` and `CLEANUP.json`.\n')
save(Q/'FILES.json',{p.relative_to(Q).as_posix():pin(p) for p in sorted(Q.rglob('*')) if p.is_file() and p.name!='FILES.json' and '__pycache__' not in p.parts})
def read_checkpoint(label,host):
    if host=='windows':args=[sys.executable,'-X','utf8','-B',str(T/'tools/literature/verify_squidpy_catalog_checkpoint.py')]
    else:args=['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--cd','/mnt/d/projects/JLinAlg/build/fu-1','--','env','GIT_DIR=/mnt/d/projects/JLinAlg/.git/worktrees/fu-1','GIT_WORK_TREE=/mnt/d/projects/JLinAlg/build/fu-1','GIT_OPTIONAL_LOCKS=0','python3','-B','/mnt/d/projects/JLinAlg/build/fu-1/tools/literature/verify_squidpy_catalog_checkpoint.py']
    process=subprocess.run(args,cwd=T,capture_output=True,timeout=60)
    (O/(label+'.stdout')).write_bytes(process.stdout);(O/(label+'.stderr')).write_bytes(process.stderr);assert process.returncode==0,process.stderr
    return json.loads(process.stdout)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:verified=list(pool.map(lambda host:read_checkpoint('postcleanup-'+host,host),('windows','linux')))
preserve('WORKTREES-POSTCLEANUP')
names=[row[3:] for row in git('status','--porcelain=v1','-uall',cwd=T).decode().splitlines()]
assert set(names)=={'docs/literature/SQUIDPY_CATALOG_CHECKPOINT_2026-11-22.md',*[ 'docs/literature/squidpy-catalog-checkpoint/'+n for n in ('CLEANUP.json','PUBLICATION.json','PAGES-PUBLIC-VERIFICATION.json','finish_publication.py','FILES.json')]}
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

commit,tree,identities=commit_selected(names,'Record verified Squidpy deployment and temporary-copy cleanup',BASE,'cleanup')
push_and_readback(commit,tree,identities,BASE,'cleanup')
preserve('WORKTREES-FINAL');assert not git('status','--porcelain=v1','-uall',cwd=T).strip()
save(O/'COMPLETED.json',dict(status='PUSHED_SQUIDPY_ADMISSION_EVIDENCE_DEPLOYMENT_AND_CLEANUP',commit=commit,admission_commit=BASE,
    scientific_source_and_released_binaries_unchanged=True,scoped_families=152,native=27,upstream=125,reviewed_uses=1685,pending_uses=1037,
    tests_per_host=16,actual_CLI_refusals=10,checkpoint_reader_controls=8,Pages_run=pages['run_id'],cleanup=result,
    other18_worktrees_and_indexes_preserved=True,historical_sensitive_exports_withheld=True,whole_goal_complete=False))
print(json.dumps(dict(status='SQUIDPY_ADMISSION_PUBLISHED_AND_VERIFIED',commit=commit,admission_commit=BASE,
    scoped_families=152,reviewed_uses=1685,pending_uses=1037,cleanup_logical_bytes=result['logical_bytes_removed'],
    cleanup_measured_free_space_change=result['measured_free_space_change'])),flush=True)
