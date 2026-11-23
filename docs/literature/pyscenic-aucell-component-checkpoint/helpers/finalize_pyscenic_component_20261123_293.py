from pathlib import Path
import hashlib,json,shutil,subprocess,sys,urllib.request
from handover_inventory_20261122_111 import entries,git,pin,save,snapshot
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-scenic-270';Q=T/'docs/literature/pyscenic-aucell-component-checkpoint'
base='44695a13abc1c063ac92810656a8b2fdec649e2a';branch='codex/scenic-workflows-20261123'
assert git('rev-parse','HEAD',cwd=T).decode().strip()==base and not entries(T)
publication=json.loads((O/'GITHUB-READBACK.json').read_bytes());cleanup=json.loads((O/'CLEANUP.json').read_bytes())
assert publication['status']=='PASS' and publication['commit']==cleanup['publication_commit']==base and len(publication['files'])==57
assert len(cleanup['removed_files'])==84 and len(cleanup['removed_roots'])==12 and cleanup['published_recovery_verified']
save(Q/'CLEANUP.json',cleanup)
resources=T/'src/main/resources/org/jlinalg/omics/pyscenic-ce41b61';source=json.loads((resources/'SOURCE.json').read_bytes());numpy=next(r for r in source['sources'] if r['package']=='numpy');whitespace={}
for name in ('numpy/numpy/random/LICENSE.md','numpy/numpy/random/mtrand.pyx'):
 actual=pin(resources/name);assert actual==numpy['files'][name];whitespace[name]=actual
save(Q/'UPSTREAM-WHITESPACE.json',dict(status='EXACT_ORIGINAL_NUMPY_BYTES_PRESERVED',files=whitespace,source_commit=numpy['commit'],
 reason='Two original vendored files contain upstream trailing spaces. Narrow per-file Git attributes disable only blank-at-eol checking while preserving all original bytes; own-source and scientific checks remain unchanged.',
 initial_check_report=(O/'staged-diff-check-first.stdout').read_text(encoding='utf-8'),scientific_source_modified=False))
save(Q/'PUBLICATION.json',dict(status='PASS_NATIVE_PYSCENIC_AUCELL_COMPONENT_PUBLICATION_AND_SCOPED_CLEANUP',source_commit=base,source_files_byte_exact_on_GitHub=57,
 normal_tests=2059,previous_payloads_unchanged=22995,focused_methods_per_JVM=7,original_unique_cases=37,original_permutations=114,reader_controls=8,
 native_component_qualified=True,full_SCENIC_workflow_qualified=False,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False,
 other_owners_preserved=18,registered_worktrees=19,previously_rejected_historical_exports_performed=False,new_release_published=False,site_source_changed=False,
 workflow_runs_at_readback=publication['workflow_runs'],cleanup_receipt='CLEANUP.json',private_machine_transfer_performed=False))
with (Q/'README.md').open('a',encoding='utf-8',newline='\n') as f:f.write(f'''

All57 selected source/component/evidence files were read back byte for byte from
GitHub at `{base}`. [PUBLICATION.json](PUBLICATION.json) records this scope.
No release or site deployment was performed; no Actions run was present at
readback. [UPSTREAM-WHITESPACE.json](UPSTREAM-WHITESPACE.json) records a narrow
Git whitespace exception for two unmodified NumPy files; their original source
bytes and hashes were preserved. Scientific gates were not relaxed.

After verified publication,12 redundant compiler-copy roots were removed:
84 files,{cleanup['logical_file_bytes']:,} logical bytes. Measured available space
increased by{cleanup['measured_available_space_delta']:,} bytes and may include unrelated
activity. Every removed byte is recoverable from objects in evidence.zip;
[CLEANUP.json](CLEANUP.json) records exact paths/members. Original/failed inputs
and outputs, exact producers, private runtimes and all19 worktrees remain. Eighteen
other owners' HEADs, dirty bytes and indexes remain unchanged. No unrelated
process was stopped. Full SCENIC workflow qualification, catalog admission and
the full250-paper objective remain unfinished.
''')
guide=T/'docs/literature/PYSCENIC_AUCELL_COMPONENT.md';text=guide.read_text(encoding='utf-8');needle='## Existing AUCell remains separate'
assert needle in text;text=text.replace(needle,'''**Input-QC warning:** zero or identical expression values, including an all-zero
cell, are still ranked by the source tie rule and can produce positive scores.
Check expression/detection quality separately. `eligible` describes the bounded
numerical profile and its diagnostics; it is not cell QC, statistical significance
or proof of biological activity.

'''+needle,1);guide.write_text(text,encoding='utf-8',newline='\n')
doc=T/'docs/literature/PYSCENIC_COMPONENT_CHECKPOINT_2026-11-23.md'
with doc.open('a',encoding='utf-8',newline='\n') as f:f.write(f'''

## Publication and cleanup

All57 selected files are verified on GitHub main at `{base}`. Read
[publication](pyscenic-aucell-component-checkpoint/PUBLICATION.json),
[cleanup](pyscenic-aucell-component-checkpoint/CLEANUP.json) and
[upstream whitespace](pyscenic-aucell-component-checkpoint/UPSTREAM-WHITESPACE.json)
receipts. Twelve redundant compiler-copy roots were removed only after byte-exact
public recovery was verified:84 files,{cleanup['logical_file_bytes']:,} logical bytes,
measured available-space increase{cleanup['measured_available_space_delta']:,} bytes
(possibly including unrelated activity). Exact producers, original/failed evidence,
runtimes and other owners remain. No release/site deployment was performed.
''')
helpers=['verify_pyscenic_component_push_20261123_291.py','cleanup_pyscenic_component_20261123_292.ps1',Path(__file__).name]
for name in helpers:shutil.copyfile(R/'build'/name,Q/'helpers'/name)
paths=['docs/literature/pyscenic-aucell-component-checkpoint/'+n for n in ('CLEANUP.json','UPSTREAM-WHITESPACE.json','PUBLICATION.json','README.md')]+['docs/literature/PYSCENIC_AUCELL_COMPONENT.md','docs/literature/PYSCENIC_COMPONENT_CHECKPOINT_2026-11-23.md']+['docs/literature/pyscenic-aucell-component-checkpoint/helpers/'+n for n in helpers]
assert sorted(x['path'] for x in entries(T))==sorted(paths)
pins={n:pin(T/n) for n in paths};save(O/'FINAL-PUBLICATION-FILES.json',pins)
git('-c','core.autocrlf=false','add','--',*paths,cwd=T);git('diff','--cached','--check',cwd=T)
for name in paths:assert git('show',':'+name,cwd=T)==(T/name).read_bytes()
message=O/'final-commit.txt';message.write_text('Record pySCENIC component publication, numerical scope and cleanup\n\nRetain exact recovery for 84 redundant compiler files, original and failed evidence, source/license bytes, private producers/runtimes and other worktree owners. Clarify tied/all-zero expression scores are not cell QC or biological proof. Full SCENIC workflow and catalog admission remain pending.\n',encoding='utf-8')
git('commit','--file',str(message),cwd=T);commit=git('rev-parse','HEAD',cwd=T).decode().strip();save(O/'FINAL-COMMITTED.json',dict(commit=commit,source_commit=base,paths=paths))
git('push','--atomic','origin',commit+':refs/heads/main',commit+':refs/heads/'+branch,cwd=T)
readback=[]
for name in paths:
 req=urllib.request.Request('https://raw.githubusercontent.com/robbyjo/OmicsMatic/'+commit+'/'+name,headers={'User-Agent':'OmicsMatic-pyscenic-component-final-readback'})
 with urllib.request.urlopen(req,timeout=45) as response:raw=response.read()
 actual=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());assert actual==pins[name];readback.append(dict(path=name,identity=actual))
refs={r.split()[1]:r.split()[0] for r in git('ls-remote','--heads','origin','main',branch,cwd=T).decode().splitlines()};assert set(refs.values())=={commit}
save(O/'FINAL-GITHUB-READBACK.json',dict(status='PASS',commit=commit,refs=refs,files=readback))
p=subprocess.run([sys.executable,'-X','utf8','-B','tools/literature/verify_pyscenic_aucell_component.py','--jar',str(T/'build/scenic-1/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar')],cwd=T,capture_output=True,timeout=90)
(O/'FINAL-VERIFY.stdout').write_bytes(p.stdout);(O/'FINAL-VERIFY.stderr').write_bytes(p.stderr);assert p.returncode==0,p.stderr
after=snapshot();save(O/'WORKTREES-FINAL.json',after);old={x['worktree']:x for x in json.loads((O/'WORKTREES-BEFORE.json').read_bytes())};current={x['worktree']:x for x in after};others=[x for x in old if Path(x)!=T];indexes=json.loads((O/'INDEXES-BEFORE.json').read_bytes())
assert len(others)==18 and set(old)==set(current) and all(old[x]==current[x] for x in others)
assert all(pin(Path(indexes[x]['path']))==indexes[x]['identity'] for x in others) and not entries(T)
save(O/'COMPLETED.json',dict(status='NATIVE_PYSCENIC_AUCELL_COMPONENT_VALIDATED_PUBLISHED_CLEANED',main=commit,source_commit=base,source_files_verified=57,final_metadata_files_verified=len(paths),
 normal_tests=2059,previous_payloads_unchanged=22995,focused_methods_per_JVM=7,native_component_qualified=True,full_SCENIC_workflow_qualified=False,
 catalog_admitted=False,new_family_credit=0,new_reviews=0,scoped_families=154,reviewed_uses=1697,pending_uses=1025,
 cleanup_logical_bytes=cleanup['logical_file_bytes'],cleanup_measured_available_space_delta=cleanup['measured_available_space_delta'],other_owners_preserved=18,current_checkout_clean=True,whole_goal_complete=False,
 required_next_work=['original SCENIC GRN/ctx and full capture/VERIFY/REPLAY','historical edition and paper/resource role review','P153 missed SCENIC+ comparator correction','guarded catalog admission','higher-impact gaps and full-cohort remaining semantic review']))
print(json.dumps(dict(status='PASS',main=commit,source_files_verified=57,final_metadata_files_verified=len(paths),normal_tests=2059,previous_payloads_unchanged=22995,new_family_credit=0,new_reviews=0,other_owners_preserved=18,current_checkout_clean=True,whole_goal_complete=False),indent=2))
