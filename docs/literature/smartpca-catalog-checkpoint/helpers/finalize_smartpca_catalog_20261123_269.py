from pathlib import Path
import hashlib,json,shutil,subprocess,sys,urllib.request
from handover_inventory_20261122_111 import entries,git,pin,save,snapshot
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-smartpca-catalog-255';Q=T/'docs/literature/smartpca-catalog-checkpoint'
base='f8bcdded2fd4198c111e1354bbdfab5f00e7eaa1';branch='codex/smartpca-catalog-20261123'
assert git('rev-parse','HEAD',cwd=T).decode().strip()==base and not entries(T)
publication=json.loads((O/'GITHUB-READBACK.json').read_bytes());raw_cleanup=json.loads((O/'CLEANUP.json').read_bytes());deployment=json.loads((O/'DEPLOYMENT.json').read_bytes())
assert publication['status']=='PASS' and publication['commit']==raw_cleanup['publication_commit']==base and len(publication['raw_files'])==63
assert len(raw_cleanup['removed_files'])==32 and len(raw_cleanup['removed_roots'])==2 and raw_cleanup['published_recovery_verified']
assert deployment['status']=='PASS_AUTOMATIC_PAGES_DEPLOYMENT_REPORT_EXCLUSIONS' and deployment['head_sha']==base and deployment['conclusion']=='success'
shutil.copyfile(O/'CLEANUP.json',Q/'CLEANUP-RAW.json')
cleanup=dict(raw_cleanup,status='PASS_EXACT_REDUNDANT_CATALOG_PREVIEW_SOURCE_COPY_CLEANUP',scope='32 redundant preview/source copies; no compiler output or scientific result was deleted in this stage',raw_receipt='CLEANUP-RAW.json',raw_receipt_identity=pin(Q/'CLEANUP-RAW.json'),initial_allowlist_guard_refused_before_any_deletion=True)
save(Q/'CLEANUP.json',cleanup);save(Q/'DEPLOYMENT.json',deployment)
shutil.copyfile(T/'build/smartpca-catalog-255/CLEANUP-GUARD-FIRST.ps1',Q/'CLEANUP-GUARD-FIRST.ps1')
save(Q/'CLEANUP-GUARD-RECOVERY.json',dict(status='PASS_GUARD_REFUSAL_BEFORE_DELETE_AND_EXACT_LITERAL_ALLOWLIST_RECOVERY',
 first_script=pin(Q/'CLEANUP-GUARD-FIRST.ps1'),cause='PowerShell array-expression precedence produced an invalid combined allowlist value. The first target failed validation before the deletion loop.',
 first_attempt_files_removed=0,correction='Use two explicit absolute literal directory strings; revalidate every resolved path, namespace, file hash, process/reparse guard and published recovery before native LiteralPath deletion.',
 corrected_removed_files=32,corrected_removed_roots=2,original_scientific_evidence_and_other_owners_preserved=True))
save(Q/'PUBLICATION.json',dict(status='PASS_SMARTPCA_CATALOG_PUBLICATION_READBACK_AND_SCOPED_CLEANUP',source_commit=base,source_files_byte_exact_on_GitHub=63,
 scoped_families=154,native=29,upstream=125,reviewed_uses=1697,pending_uses=1025,new_native_family_credit=1,new_reviews=6,
 tests_per_host=21,actual_CLI_refusals=10,saved_reader_controls=8,complete_manifest_entries=861,catalog_admitted=True,native_science_unchanged=True,
 other_owners_preserved=18,registered_worktrees=19,previously_rejected_historical_exports_performed=False,whole_goal_complete=False,
 full_checkWebsite_passed=False,existing_missing_citation_panels=33,literature_reports_deployed=False,pages_workflow_changed=False,
 automatic_site_workflow=deployment,new_release_published=False,private_machine_transfer_performed=False,cleanup_receipt='CLEANUP.json'))
with (Q/'README.md').open('a',encoding='utf-8',newline='\n') as f:f.write(f'''

All63 selected source/catalog/evidence files were read back byte for byte from
GitHub at `{base}`. [PUBLICATION.json](PUBLICATION.json) records admission scope.
The existing Pages workflow automatically deployed the eligible website; both
unfinished literature reports remain excluded and return404. The workflow and
live checks passed; [DEPLOYMENT.json](DEPLOYMENT.json) records them. No release
was published. This does not change the retained33 full-website citation errors.

After verified publication,32 redundant preview/source copies in two exact roots
were removed: {cleanup['logical_file_bytes']:,} logical bytes. Measured available space
increased by {cleanup['measured_available_space_delta']:,} bytes and may include unrelated
activity. Every removed byte is recoverable from objects in evidence.zip; see
[CLEANUP.json](CLEANUP.json). Its raw receipt retains a historical compiler-copy
status label; the normalized receipt states the actual metadata/source-copy scope.
The first cleanup allowlist expression was refused before deletion; its script
and corrected exact-literal recovery are retained. Original/failed evidence,
producer JARs, runtimes,19 worktrees and18 other owners' dirty bytes/indexes remain.
No unrelated process was stopped. The full250-paper objective remains unfinished.
''')
doc=T/'docs/literature/SMARTPCA_CATALOG_CHECKPOINT_2026-11-23.md';text=doc.read_text(encoding='utf-8')
text=text.replace('publishes repository source/evidence, not a website or release.','publishes repository source/evidence; neither literature report nor a release is published.')
text+=f'''

## Published state and cleanup

Source/catalog admission is verified on GitHub main at `{base}` (63 exact files).
The existing automatic Pages deployment succeeded for eligible website files;
both unfinished literature reports remain excluded and return404. No release
was published. See [publication](smartpca-catalog-checkpoint/PUBLICATION.json) and
[deployment](smartpca-catalog-checkpoint/DEPLOYMENT.json) receipts. These hosted
checks do not imply full local website validation passed;33 citation errors remain.

After byte-exact public recovery was confirmed,32 redundant preview/source copies
were removed from two listed roots: {cleanup['logical_file_bytes']:,} logical bytes,
with measured available-space increase {cleanup['measured_available_space_delta']:,} bytes
(possibly including unrelated activity). [Cleanup](smartpca-catalog-checkpoint/CLEANUP.json)
lists exact paths/recovery objects. Preserve all original/failed evidence,
private runtimes/producers, dirty worktrees and restricted histories.
'''
doc.write_text(text,encoding='utf-8',newline='\n')
helpers=['verify_smartpca_catalog_publication_20261123_266.py','cleanup_smartpca_catalog_20261123_267.ps1','check_smartpca_catalog_deployment_20261123_268.py',Path(__file__).name]
for name in helpers:shutil.copyfile(R/'build'/name,Q/'helpers'/name)
paths=['docs/literature/smartpca-catalog-checkpoint/'+n for n in ('CLEANUP-RAW.json','CLEANUP.json','DEPLOYMENT.json','CLEANUP-GUARD-FIRST.ps1','CLEANUP-GUARD-RECOVERY.json','PUBLICATION.json','README.md')]+['docs/literature/SMARTPCA_CATALOG_CHECKPOINT_2026-11-23.md']+['docs/literature/smartpca-catalog-checkpoint/helpers/'+n for n in helpers]
assert sorted(x['path'] for x in entries(T))==sorted(paths)
pins={n:pin(T/n) for n in paths};save(O/'FINAL-PUBLICATION-FILES.json',pins)
git('-c','core.autocrlf=false','add','--',*paths,cwd=T);git('diff','--cached','--check',cwd=T)
for name in paths:assert git('show',':'+name,cwd=T)==(T/name).read_bytes()
message=O/'final-commit.txt';message.write_text('Record smartpca catalog publication, Pages exclusions and cleanup\n\nPreserve exact public recovery for redundant copies, failed helper receipts, original scientific evidence and all other worktree owners. Record successful automatic eligible-site deployment while literature reports remain withheld and 33 citation errors remain open.\n',encoding='utf-8')
git('commit','--file',str(message),cwd=T);commit=git('rev-parse','HEAD',cwd=T).decode().strip();save(O/'FINAL-COMMITTED.json',dict(commit=commit,source_commit=base,paths=paths))
git('push','--atomic','origin',commit+':refs/heads/main',commit+':refs/heads/'+branch,cwd=T)
files=[]
for name in paths:
 req=urllib.request.Request('https://raw.githubusercontent.com/robbyjo/OmicsMatic/'+commit+'/'+name,headers={'User-Agent':'OmicsMatic-smartpca-catalog-final-readback'})
 with urllib.request.urlopen(req,timeout=45) as response:raw=response.read()
 actual=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());assert actual==pins[name];files.append(dict(path=name,identity=actual))
refs={r.split()[1]:r.split()[0] for r in git('ls-remote','--heads','origin','main',branch,cwd=T).decode().splitlines()};assert set(refs.values())=={commit}
save(O/'FINAL-GITHUB-READBACK.json',dict(status='PASS',commit=commit,refs=refs,files=files))
result=subprocess.run([sys.executable,'-X','utf8','-B','tools/literature/verify_smartpca_catalog_checkpoint.py','--jar',str(T/'build/smartpca-4/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar')],cwd=T,capture_output=True,timeout=90)
(O/'FINAL-SAVED-READER.stdout').write_bytes(result.stdout);(O/'FINAL-SAVED-READER.stderr').write_bytes(result.stderr);assert result.returncode==0,result.stderr
after=snapshot();save(O/'WORKTREES-FINAL.json',after)
old={x['worktree']:x for x in json.loads((O/'WORKTREES-BEFORE.json').read_bytes())};current={x['worktree']:x for x in after};others=[x for x in old if Path(x)!=T];indexes=json.loads((O/'INDEXES-BEFORE.json').read_bytes())
assert len(others)==18 and set(old)==set(current) and all(old[x]==current[x] for x in others)
assert all(pin(Path(indexes[x]['path']))==indexes[x]['identity'] for x in others) and not entries(T)
save(O/'COMPLETED.json',dict(status='SMARTPCA_CATALOG_ADMITTED_VALIDATED_PUBLISHED_AND_CLEANED',main=commit,source_commit=base,source_files_verified=63,final_metadata_files_verified=len(paths),
 scoped_families=154,native=29,upstream=125,reviewed_uses=1697,pending_uses=1025,new_native_family_credit=1,new_reviews=6,
 tests_per_host=21,CLI_refusals=10,reader_checks=8,complete_manifest_entries=861,other_owners_preserved=18,current_checkout_clean=True,
 full_checkWebsite_passed=False,existing_missing_citation_panels=33,literature_reports_deployed=False,automatic_pages_workflow_succeeded=True,
 cleanup_logical_bytes=cleanup['logical_file_bytes'],cleanup_measured_available_space_delta=cleanup['measured_available_space_delta'],whole_goal_complete=False,
 required_next_work=['higher-impact missing capability/source-role assessment','all1025 pending semantic reviews and missed-provider screening','33 existing website citation-panel errors','private machine transfer before retirement']))
print(json.dumps(dict(status='PASS',main=commit,scoped_families=154,reviewed_uses=1697,pending_uses=1025,source_files_verified=63,final_metadata_files_verified=len(paths),other_owners_preserved=18,current_checkout_clean=True,whole_goal_complete=False),indent=2))
