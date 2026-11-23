from pathlib import Path
import concurrent.futures,hashlib,io,json,re,shutil,subprocess,sys,tarfile,time,urllib.request,zipfile
from handover_inventory_20261122_111 import snapshot,save,pin,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';D=T/'docs/literature/squidpy-workflow-handover-20261122'
O=R/'build/git-publish/20261122-squidpy-workflow-handover-136';C=O/'reader-controls-1'
BASE='abb43f59fe5b642431a173f3c2c62f4a6c040184';BRANCH='codex/squidpy-workflow-20261122'
before=json.loads((O/'WORKTREES-BEFORE.json').read_bytes())
assert len(before)==19 and git('rev-parse','HEAD',cwd=T).decode().strip()==BASE
assert git('branch','--show-current',cwd=T).decode().strip()==BRANCH
assert git('ls-remote','origin','refs/heads/main',cwd=T).decode().split()[0]==BASE
assert not git('diff','--cached','--name-only',cwd=T).strip()

def check_other_worktrees(label):
 current=snapshot();by={x['worktree']:x for x in current}
 assert len(current)==19 and all(by[x['worktree']]==x for x in before if Path(x['worktree'])!=T)
 save(O/(label+'.json'),current);return current

for name in ('check_squidpy_handover_20261122_136.py','publish_squidpy_workflow_handover_20261122_136.py'):
 shutil.copyfile(R/'build'/name,D/'maintenance'/name)
checks=D/'checks';checks.mkdir()
for case in json.loads((D/'READER-CONTROLS.json').read_bytes())['cases']:
 for suffix in ('.stdout','.stderr'):shutil.copyfile(O/(case['case']+suffix),checks/(case['case']+suffix))

# Exact replacement inventory for temporary reader-control copies. Most bytes
# are already in the saved checkpoint; unique negative-control inputs are retained.
entries=[];unique={}
for path in sorted(C.rglob('*')):
 assert not path.is_symlink()
 if not path.is_file():continue
 relative=path.relative_to(C);name=Path(*relative.parts[1:]).as_posix();expected=pin(path)
 if (D/name).is_file() and pin(D/name)==expected:recovery=dict(kind='published_checkpoint',path=str(D/name))
 else:
  raw=path.read_bytes();unique[expected['sha256']]=raw
  recovery=dict(kind='retained_control_archive',archive=str(D/'control-recovery.zip'),member='objects/'+expected['sha256'])
 entries.append(dict(path=str(path),**expected,recovery=recovery))
with zipfile.ZipFile(D/'control-recovery.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for digest,raw in sorted(unique.items()):
  info=zipfile.ZipInfo('objects/'+digest,(2026,11,22,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw,compresslevel=9)
cleanup_plan=dict(root=str(C),files=entries,unique_control_archive=pin(D/'control-recovery.zip'),
    scope='Only completed temporary copied checkpoint inputs. Exact checked-in replacements and the retained negative-control archive restore every file. Original scientific captures, runtimes, compiled producer contexts and unrelated worktrees excluded.')
save(D/'CLEANUP-PLAN.json',cleanup_plan);save(O/'CLEANUP-PLAN.json',cleanup_plan)

def seal():save(D/'FILES.json',{p.relative_to(D).as_posix():pin(p) for p in sorted(D.rglob('*')) if p.is_file() and p.name!='FILES.json' and '__pycache__' not in p.parts})
def read_checkpoint(label):
 p=subprocess.run([sys.executable,'-X','utf8','-B',str(D/'verify_checkpoint.py')],cwd=T,capture_output=True)
 (O/(label+'.stdout')).write_bytes(p.stdout);(O/(label+'.stderr')).write_bytes(p.stderr)
 assert p.returncode==0,p.stderr
 return json.loads(p.stdout)

seal();proof=read_checkpoint('prepublication-reader')
current=check_other_worktrees('WORKTREES-PREPUBLICATION')
own=next(x for x in current if Path(x['worktree'])==T)
allowed={x['path']:x['content'] for x in own['status']}
exact={'.gitattributes','docs/literature/HANDOVER_CURRENT.md','docs/literature/SQUIDPY_WORKFLOW_HANDOVER_2026-11-22.md'}
assert all(n in exact or n.startswith('docs/literature/squidpy-workflow-handover-20261122/') for n in allowed)
assert not git('diff',BASE,'--','src','tools','site','.github','docs/literature/omics-2023-2026',cwd=T).strip()
for name in json.loads((D/'RESTORE.json').read_bytes())['files']:assert not (T/name).exists(),name
patterns=[rb'-----BEGIN [A-Z ]*PRIVATE KEY-----',rb'gh[pousr]_[A-Za-z0-9]{30,}',rb'github_pat_[A-Za-z0-9_]{30,}',rb'AKIA[0-9A-Z]{16}',rb'(?im)^set-cookie\s*:',rb'(?im)^authorization\s*:\s*(?:bearer|basic)\s+\S+']
findings=[]
for name in allowed:
 parts=[(name,(T/name).read_bytes())]
 if name.endswith('.zip'):
  with zipfile.ZipFile(T/name) as z:parts.extend((name+'!'+m,z.read(m)) for m in z.namelist())
 for label,raw in parts:
  for pattern in patterns:
   if re.search(pattern,raw):findings.append(dict(path=label,pattern=pattern.decode()))
assert not findings,findings
links=[]
for name in ('docs/literature/SQUIDPY_WORKFLOW_HANDOVER_2026-11-22.md','docs/literature/squidpy-workflow-handover-20261122/README.md'):
 for target in re.findall(r'\]\(([^)]+)\)',(T/name).read_text(encoding='utf-8')):
  if not re.match(r'^[a-z]+:',target) and not target.startswith('#'):
   assert ((T/name).parent/target.split('#')[0]).exists(),(name,target)
   links.append(dict(source=name,target=target))
save(O/'PREPUBLICATION.json',dict(reader=proof,files=len(allowed),sensitive_pattern_findings=findings,links_checked=links,
    all18_other_worktrees_preserved=True,production_source_and_catalog_and_site_unchanged=True,
    full_checkWebsite_passed=False,existing_missing_citation_panels=33,whole_goal_complete=False))
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
   raw=archive.extractfile(member).read();assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==identities[member.name]
 save(O/(tag+'-COMMITTED.json'),dict(commit=commit,tree=tree,base=expected_base,files=len(names),archive_bytes_exact=True))
 return commit,tree,identities

def fetch(url):
 for attempt in range(3):
  try:
   with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-Squidpy-development-handover','Cache-Control':'no-cache'}),timeout=40) as response:return response.read()
  except Exception:
   if attempt==2:raise
   time.sleep(2)

def push_and_readback(commit,tree,identities,expected_base,tag):
 assert git('ls-remote','origin','refs/heads/main',cwd=T).decode().split()[0]==expected_base
 with (O/(tag+'-push.stdout')).open('xb') as out,(O/(tag+'-push.stderr')).open('xb') as err:
  p=subprocess.run(['git','push','--atomic','origin','HEAD:refs/heads/main','HEAD:refs/heads/'+BRANCH],cwd=T,stdout=out,stderr=err)
 assert p.returncode==0,(O/(tag+'-push.stderr')).read_text(errors='replace')
 refs=git('ls-remote','origin','refs/heads/main','refs/heads/'+BRANCH,cwd=T).decode().splitlines()
 assert len(refs)==2 and all(r.split()[0]==commit for r in refs)
 api='https://api.github.com/repos/robbyjo/OmicsMatic'
 assert json.loads(fetch(api+'/git/ref/heads/main'))['object']['sha']==commit
 assert json.loads(fetch(api+'/git/commits/'+commit))['tree']['sha']==tree
 def check(name):
  raw=fetch('https://raw.githubusercontent.com/robbyjo/OmicsMatic/'+commit+'/'+name)
  found=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());assert found==identities[name],name
  return name,found
 with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:public=dict(pool.map(check,sorted(identities)))
 save(O/(tag+'-PUBLIC-READBACK.json'),public)
 print(json.dumps(dict(status='PUSHED_MAIN_AND_ALL_PUBLIC_BYTES_VERIFIED',phase=tag,commit=commit,files=len(public))),flush=True)

commit,tree,identities=commit_selected(allowed,'Save full Squidpy workflow draft, terminal failures and exact handover work',BASE,'handover')
push_and_readback(commit,tree,identities,BASE,'handover')
check_other_worktrees('WORKTREES-AFTER-FIRST-PUSH')
assert not git('status','--porcelain=v1','-uall',cwd=T).strip()

# Verify every replacement afresh before the guarded native PowerShell removal.
with zipfile.ZipFile(D/'control-recovery.zip') as z:
 for entry in entries:
  path=Path(entry['path']);assert path.is_relative_to(C) and pin(path)=={k:entry[k] for k in ('bytes','sha256')}
  recovery=entry['recovery'];raw=Path(recovery['path']).read_bytes() if recovery['kind']=='published_checkpoint' else z.read(recovery['member'])
  assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())=={k:entry[k] for k in ('bytes','sha256')}
cleanup_script=O/'cleanup-controls.ps1'
cleanup_script.write_text(r'''$ErrorActionPreference='Stop'
$receiptRoot='D:\projects\JLinAlg\build\git-publish\20261122-squidpy-workflow-handover-136'
$plan=Get-Content -LiteralPath (Join-Path $receiptRoot 'CLEANUP-PLAN.json') -Raw | ConvertFrom-Json
$target=[IO.Path]::GetFullPath($plan.root)
if($target -ne 'D:\projects\JLinAlg\build\git-publish\20261122-squidpy-workflow-handover-136\reader-controls-1') { throw 'Unexpected cleanup root' }
$nodes=@(Get-Item -LiteralPath $target -Force)+@(Get-ChildItem -LiteralPath $target -Recurse -Force)
foreach($node in $nodes) {
    if(-not ($node.FullName -eq $target -or $node.FullName.StartsWith($target+'\',[StringComparison]::OrdinalIgnoreCase))) { throw 'Cleanup path escape' }
    if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Reparse point in cleanup root' }
}
$logical=0L
foreach($entry in $plan.files) {
    $file=[IO.Path]::GetFullPath($entry.path)
    if(-not $file.StartsWith($target+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Removal target escapes controls root' }
    $bytes=[IO.File]::ReadAllBytes($file);$algorithm=[Security.Cryptography.SHA256]::Create()
    try { $digest=[BitConverter]::ToString($algorithm.ComputeHash($bytes)).Replace('-','').ToLowerInvariant() } finally { $algorithm.Dispose() }
    if($bytes.LongLength -ne $entry.bytes -or $digest -ne $entry.sha256) { throw 'Control copy changed before cleanup' }
    $logical+=$entry.bytes
}
$before=[IO.DriveInfo]::new('D:\').AvailableFreeSpace
@{free_bytes_before=$before;logical_bytes=$logical;files=@($plan.files).Count} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $receiptRoot 'CLEANUP-MEASUREMENT-BEFORE.json') -Encoding UTF8
foreach($entry in $plan.files) { Remove-Item -LiteralPath $entry.path -Force }
$dirs=@(Get-ChildItem -LiteralPath $target -Directory -Recurse -Force | Sort-Object { $_.FullName.Length } -Descending)
foreach($dir in $dirs) { if(@(Get-ChildItem -LiteralPath $dir.FullName -Force).Count -eq 0) { Remove-Item -LiteralPath $dir.FullName -Force } }
if(@(Get-ChildItem -LiteralPath $target -Force).Count -ne 0) { throw 'Unexpected controls remain; preserved' }
Remove-Item -LiteralPath $target -Force
$after=[IO.DriveInfo]::new('D:\').AvailableFreeSpace
@{status='VERIFIED_REDUNDANT_READER_CONTROL_COPIES_REMOVED';files_removed=@($plan.files).Count;logical_bytes_removed=$logical;free_bytes_before=$before;free_bytes_after=$after;measured_free_space_change=$after-$before;root=$target;recursive_delete=$false;scientific_evidence_runtimes_frozen_producers_and_unique_work_preserved=$true} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $receiptRoot 'CLEANUP-RESULT.json') -Encoding UTF8
''',encoding='utf-8')
p=subprocess.run(['C:/Windows/System32/WindowsPowerShell/v1.0/powershell.exe','-NoProfile','-File',str(cleanup_script)],cwd=T,capture_output=True)
(O/'cleanup-controls.stdout').write_bytes(p.stdout);(O/'cleanup-controls.stderr').write_bytes(p.stderr)
assert p.returncode==0,p.stderr
cleanup=json.loads((O/'CLEANUP-RESULT.json').read_bytes());assert not C.exists()
save(D/'CLEANUP.json',dict(result=cleanup,plan='CLEANUP-PLAN.json',replacement_archive='control-recovery.zip',
    all18_other_worktrees_preserved=True,full_original_and_failed_captures_runtimes_and_frozen_contexts_retained=True,
    earlier_draft_relocation_receipt='maintenance/RELOCATION-RESULT.json',
    earlier_post_removal_receipt_failure_and_exact_recovery='maintenance/RELOCATION-ATTEMPT1-RECOVERY.json'))
document=T/'docs/literature/SQUIDPY_WORKFLOW_HANDOVER_2026-11-22.md'
with document.open('a',encoding='utf-8',newline='') as f:
 f.write('\nAfter the handover was pushed and public bytes read back, cleanup removed\n'+str(cleanup['files_removed'])+' verified redundant reader-control files ('+str(cleanup['logical_bytes_removed'])+' logical\nbytes; measured free-space change '+str(cleanup['measured_free_space_change'])+' bytes). Exact recovery uses\nthe checked-in packet plus `control-recovery.zip`; see the packet\'s `CLEANUP.json`\nand `CLEANUP-PLAN.json`. All19 worktrees, original/failed evidence, private\nruntimes and frozen producers remain preserved.\n')
seal();read_checkpoint('postcleanup-reader');check_other_worktrees('WORKTREES-AFTER-CLEANUP')
changed=git('status','--porcelain=v1','-uall',cwd=T).decode().splitlines();names=[x[3:] for x in changed]
assert set(names)=={'docs/literature/SQUIDPY_WORKFLOW_HANDOVER_2026-11-22.md','docs/literature/squidpy-workflow-handover-20261122/FILES.json','docs/literature/squidpy-workflow-handover-20261122/CLEANUP.json'}
final_commit,final_tree,cleanup_identities=commit_selected(names,'Record verified handover scratch cleanup and retained recovery inputs',commit,'cleanup')
identities.update(cleanup_identities)
push_and_readback(final_commit,final_tree,identities,commit,'cleanup')
check_other_worktrees('WORKTREES-AFTER-FINAL-PUSH')
assert not git('status','--porcelain=v1','-uall',cwd=T).strip()
result=dict(status='SQUIDPY_WORKFLOW_HANDOVER_AND_CLEANUP_PUSHED_MAIN_ALL_PUBLIC_BYTES_VERIFIED',
    commit=final_commit,handover_commit=commit,files=len(identities),all_public_files_read_back=True,
    all18_other_worktrees_preserved=True,owner_checkout_clean=True,production_source_catalog_and_site_unchanged=True,
    draft_files_preserved=14,prototype_contract_tests=13,final_cases=42,expected_outcomes=40,known_unexpected_failures=2,
    full_adapter_qualified=False,new_family_credit=0,new_reviews=0,scoped_families=151,reviewed_uses=1679,pending_uses=1043,
    cleanup=cleanup,private_runtimes_primary_text_credentials_and_blocked_historical_exports_not_exported=True,
    site_deployment_or_release_created=False,whole_goal_complete=False)
save(O/'PUBLISHED.json',result);print(json.dumps(result),flush=True)
