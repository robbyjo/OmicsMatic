from pathlib import Path
import hashlib,json,shutil,subprocess,sys,urllib.request
from handover_inventory_20261122_111 import entries,git,pin,save,snapshot
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-smartpca-fst-240';Q=T/'docs/literature/smartpca-fst-qualification'
base='70054506b16b8313d8f5e42b13d291a79489addc';branch='codex/smartpca-fst-20261123'
assert git('rev-parse','HEAD',cwd=T).decode().strip()==base and not entries(T)
publication=json.loads((O/'GITHUB-READBACK.json').read_bytes());cleanup=json.loads((O/'CLEANUP.json').read_bytes())
assert publication['status']=='PASS' and publication['commit']==cleanup['publication_commit']==base and len(publication['files'])==44
assert cleanup['published_recovery_verified'] and len(cleanup['removed_files'])==36 and len(cleanup['removed_roots'])==6
save(Q/'CLEANUP.json',cleanup)
save(Q/'PUBLICATION.json',dict(status='PASS_NATIVE_SMARTPCA_FST_GLOBAL_COMMAND_HANDOVER_PUBLICATION_AND_SCOPED_CLEANUP',source_commit=base,source_files_byte_exact_on_GitHub=44,
 normal_tests=1906,previous_payloads_unchanged=22972,packaged_protocol_commands=362,example_commands=18,focused_methods_per_JVM=14,
 native_fst_workflow_qualified=True,global_dispatch_integrated=True,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False,
 scoped_families=153,reviewed_uses=1691,pending_uses=1031,other_worktree_owners_preserved=18,registered_worktrees=19,previously_rejected_historical_exports_performed=False,new_release_published=False,
 site_source_changed=False,workflow_runs_at_readback=publication['workflow_runs'],cleanup_receipt='CLEANUP.json',private_runtime_or_large_jar_transfer_performed=False))
readme=Q/'README.md';readme.write_text(readme.read_text(encoding='utf-8')+f'''

All 44 selected source/checkpoint files were read back byte for byte from GitHub
at `{base}`; [PUBLICATION.json](PUBLICATION.json) records this stage. No release
or site deployment was performed; no Actions run was present at readback. The
saved reader passes on Windows and Linux; the exact producer was also checked
on Windows. Historical hashes remain unchanged.

After verified publication, six redundant compiler-copy directories were removed:
36 files, {cleanup['logical_file_bytes']:,} logical bytes. The measured available-space
increase was {cleanup['measured_available_space_delta']:,} bytes and may include other
filesystem activity. Every removed byte is recoverable from the published
content-addressed evidence archive. [CLEANUP.json](CLEANUP.json) lists exact paths
and archive members. All original/failed scientific evidence, exact producer
JARs, runtimes and 19 worktrees remain. Eighteen other owners' HEADs, dirty bytes
and indexes are preserved. No unrelated process was stopped. Catalog admission
and the full 250-paper objective remain unfinished.
''',encoding='utf-8',newline='\n')
handover=T/'docs/literature/HANDOVER_SMARTPCA_FST_2026-11-23.md'
handover.write_text(handover.read_text(encoding='utf-8')+f'''

## Publication and cleanup receipts

The 44 selected source/guide/example/evidence files are verified on GitHub main
at `{base}`. [Publication](smartpca-fst-qualification/PUBLICATION.json) and
[cleanup](smartpca-fst-qualification/CLEANUP.json) receipts record the scope.
The six allowed redundant compiler-copy roots were removed after verified public
recovery: 36 files, {cleanup['logical_file_bytes']:,} logical bytes; measured available
space increased by {cleanup['measured_available_space_delta']:,} bytes (possibly including
other activity). Exact producers, all unique/failed evidence, runtimes and other
owners remain. No release or site deployment is claimed. No new scientific batch
was left running by this handover; check actual process ownership before resuming.
''',encoding='utf-8',newline='\n')
helpers=['verify_smartpca_fst_push_20261123_252.py','cleanup_smartpca_fst_20261123_253.ps1',Path(__file__).name]
for name in helpers:shutil.copyfile(R/'build'/name,Q/'helpers'/name)
paths=['docs/literature/smartpca-fst-qualification/'+name for name in ('CLEANUP.json','PUBLICATION.json','README.md')]+['docs/literature/HANDOVER_SMARTPCA_FST_2026-11-23.md']+['docs/literature/smartpca-fst-qualification/helpers/'+name for name in helpers]
assert sorted(x['path'] for x in entries(T))==sorted(paths)
pins={name:pin(T/name) for name in paths};save(O/'FINAL-PUBLICATION-FILES.json',pins)
git('-c','core.autocrlf=false','add','--',*paths,cwd=T);git('diff','--cached','--check',cwd=T)
for name in paths:assert git('show',':'+name,cwd=T)==(T/name).read_bytes()
message=O/'final-commit.txt';message.write_text('Record smartpca handover publication and verified cleanup\n\nRetain exact recovery for 36 redundant compiled files, original/failed evidence, producers, runtimes and all other worktree owners. Six-role catalog admission and the full toolbox goal remain open.\n',encoding='utf-8')
git('commit','--file',str(message),cwd=T);commit=git('rev-parse','HEAD',cwd=T).decode().strip();save(O/'FINAL-COMMITTED.json',dict(commit=commit,source_commit=base,paths=paths))
git('push','--atomic','origin',commit+':refs/heads/main',commit+':refs/heads/'+branch,cwd=T)
files=[]
for name in paths:
 with urllib.request.urlopen(urllib.request.Request('https://raw.githubusercontent.com/robbyjo/OmicsMatic/'+commit+'/'+name,headers={'User-Agent':'OmicsMatic-smartpca-fst-final-readback'}),timeout=45) as response:raw=response.read()
 actual=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());assert actual==pins[name];files.append(dict(path=name,identity=actual))
refs={row.split()[1]:row.split()[0] for row in git('ls-remote','--heads','origin','main',branch,cwd=T).decode().splitlines()};assert set(refs.values())=={commit}
save(O/'FINAL-GITHUB-READBACK.json',dict(status='PASS',commit=commit,refs=refs,files=files))
for script,args in [('verify_smartpca_fst_checkpoint.py',['--jar',str(T/'build/smartpca-4/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar')]),('verify_smartpca_workflow_checkpoint.py',[])]:
 result=subprocess.run([sys.executable,'-X','utf8','-B','tools/literature/'+script,*args],cwd=T,capture_output=True,timeout=60)
 (O/('FINAL-'+script+'.stdout')).write_bytes(result.stdout);(O/('FINAL-'+script+'.stderr')).write_bytes(result.stderr);assert result.returncode==0,result.stderr
after=snapshot();save(O/'WORKTREES-FINAL.json',after)
old={x['worktree']:x for x in json.loads((O/'WORKTREES-BEFORE.json').read_bytes())};current={x['worktree']:x for x in after};others=[x for x in old if Path(x)!=T]
indexes=json.loads((O/'INDEXES-BEFORE.json').read_bytes());assert len(others)==18 and set(old)==set(current)
assert all(old[x]==current[x] for x in others) and all(pin(Path(indexes[x]['path']))==indexes[x]['identity'] for x in others)
assert not entries(T)
save(O/'COMPLETED.json',dict(status='SMARTPCA_FST_AND_HANDOVER_VALIDATED_PUBLISHED_CLEANED',main=commit,source_commit=base,
 source_files_verified=44,final_metadata_files_verified=len(paths),other_owners_preserved=18,current_checkout_clean=True,
 normal_tests=1906,packaged_protocol_commands=362,native_fst_workflow_qualified=True,global_dispatch_integrated=True,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False,
 scoped_families=153,reviewed_uses=1691,pending_uses=1031,cleanup_logical_bytes=cleanup['logical_file_bytes'],cleanup_measured_available_space_delta=cleanup['measured_available_space_delta'],
 required_next_work=['guarded smartpca six-role catalog admission','higher-impact gaps','all pending full-cohort reviews and missed-provider screening','33 existing website citation-panel errors','private machine transfer before retirement']))
print(json.dumps(dict(status='PASS',main=commit,source_commit=base,source_files_verified=44,final_metadata_files_verified=len(paths),normal_tests=1906,packaged_protocol_commands=362,current_checkout_clean=True,other_owners_preserved=18,whole_goal_complete=False),indent=2))
