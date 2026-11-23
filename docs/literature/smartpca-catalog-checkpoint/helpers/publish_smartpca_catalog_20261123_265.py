from pathlib import Path
import hashlib,io,json,re,subprocess,tarfile,zipfile,shutil
from handover_inventory_20261122_111 import entries,git,pin,save,snapshot
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-smartpca-catalog-255';Q=T/'docs/literature/smartpca-catalog-checkpoint'
base='efe2e19dd1f4e5add6a6dd09b949e900368dea74';branch='codex/smartpca-catalog-20261123'
assert git('symbolic-ref','--short','HEAD',cwd=T).decode().strip()==branch and git('rev-parse','HEAD',cwd=T).decode().strip()==base
assert not git('diff','--cached','--name-only',cwd=T)
assert git('ls-remote','--heads','origin','main',cwd=T).decode().split()[0]==base
shutil.copyfile(Path(__file__),Q/'helpers'/Path(__file__).name)
after=snapshot();save(O/'WORKTREES-PREPUBLICATION.json',after)
old={x['worktree']:x for x in json.loads((O/'WORKTREES-BEFORE.json').read_bytes())};current={x['worktree']:x for x in after};others=[x for x in old if Path(x)!=T];indexes=json.loads((O/'INDEXES-BEFORE.json').read_bytes())
assert len(others)==18 and set(old)==set(current) and all(old[x]==current[x] for x in others)
assert all(pin(Path(indexes[x]['path']))==indexes[x]['identity'] for x in others)
paths=sorted(x['path'] for x in entries(T));assert 50<=len(paths)<80,len(paths)
prefixes=['docs/literature/smartpca-catalog-admission/','docs/literature/smartpca-catalog-checkpoint/','docs/literature/smartpca-catalog-sources/','tools/literature/smartpca_catalog/']
exact={'.gitattributes','docs/literature/HANDOVER_2026-11-23.md','docs/literature/HANDOVER_CURRENT.md','docs/literature/SMARTPCA_CATALOG_CHECKPOINT_2026-11-23.md',
 'docs/literature/omics-2023-2026/README.md','docs/literature/omics-2023-2026/SHA256SUMS','docs/literature/omics-2023-2026/WARNINGS.md',
 'docs/literature/omics-2023-2026/papers.json','docs/literature/omics-2023-2026/summary.json','docs/literature/omics-2023-2026/tool-inventory.json',
 'docs/literature/omics-2023-2026/smartpca-catalog-validation.json','docs/literature/omics-2023-2026/smartpca-source-role-coverage.json',
 'site/literature-review.html','site/literature-warnings.html','tools/literature/reviewed-uses.json','tools/literature/audit_smartpca_catalog_sources.py',
 'tools/literature/close_smartpca_catalog.py','tools/literature/verify_smartpca_catalog_checkpoint.py'}
assert all(n in exact or any(n.startswith(p) for p in prefixes) for n in paths),paths
pins={n:pin(T/n) for n in paths};save(O/'MAIN-PUBLICATION-FILES.json',pins)
patterns=[re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),re.compile(rb'github_pat_[A-Za-z0-9_]{30,}'),re.compile(rb'gh[pousr]_[A-Za-z0-9]{30,}'),re.compile(rb'AKIA[0-9A-Z]{16}')]
matches=[];scanned=0
def scan(name,data,depth=0):
 global scanned
 scanned+=1
 if any(x.search(data) for x in patterns):matches.append(name)
 if data[:4]==b'PK\x03\x04' and depth<5:
  with zipfile.ZipFile(io.BytesIO(data)) as archive:
   for member in archive.infolist():
    if not member.is_dir():scan(name+'!'+member.filename,archive.read(member),depth+1)
for name in paths:
 assert pins[name]['bytes']<50_000_000,name;scan(name,(T/name).read_bytes())
save(O/'PUBLICATION-CONTENT-REVIEW.json',dict(files=len(paths),scanned_members=scanned,basic_credential_marker_matches=matches,full_primary_texts_included=False,
 private_cohort_or_manuscript_data_included=False,unrelated_dirty_overlays_included=False,previously_rejected_historical_exports_included=False,security_audit_claim=False));assert not matches,matches
status=json.loads((Q/'STATUS.json').read_bytes())
for name,wanted in status['source_code'].items():assert pin(T/name)==wanted,name
assert not any(n.startswith(('src/','.github/')) for n in paths)
git('-c','core.autocrlf=false','add','--',*paths,cwd=T);git('-c','core.autocrlf=false','add','--renormalize','--',*paths,cwd=T)
p=subprocess.run(['git','diff','--cached','--check'],cwd=T,capture_output=True);(O/'staged-diff-check.stdout').write_bytes(p.stdout);(O/'staged-diff-check.stderr').write_bytes(p.stderr);assert p.returncode==0,p.stdout
for name in paths:assert git('show',':'+name,cwd=T)==(T/name).read_bytes(),'Staged normalization: '+name
message=O/'catalog-commit.txt';message.write_text('Admit smartpca native workflows and six source-specific paper uses\n\n'
 'Preserve per-context package editions, literal program identifiers, separate PCA/FST preprocessing, original observations and all prior reviews.\n'
 'Retain guarded model/source/transaction/UI checks, 21 tests per host, ten CLI refusals, eight saved-reader controls and the same 33 website citation failures.\n'
 'Document 154 scoped families, 1,697 reviewed/1,025 pending uses and remaining high-impact/full-cohort work. Native scientific source, release assets and Pages exclusions remain unchanged; the full goal stays open.\n',encoding='utf-8')
git('commit','--file',str(message),cwd=T);commit=git('rev-parse','HEAD',cwd=T).decode().strip();assert not entries(T)
assert git('rev-list','--parents','-n','1',commit,cwd=T).decode().split()==[commit,base]
assert sorted(git('diff','--name-only',base,commit,cwd=T).decode().splitlines())==paths
data=git('archive','--format=tar',commit,'--',*paths,cwd=T);(O/'catalog-selected-source.tar').write_bytes(data)
with tarfile.open(fileobj=io.BytesIO(data)) as archive:files={i.name:archive.extractfile(i).read() for i in archive.getmembers() if i.isfile()}
assert set(files)==set(paths)
for name,raw in files.items():assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==pins[name],name
save(O/'PUBLICATION-PREPARED.json',dict(base_main=base,main_candidate=commit,branch=branch,git_archive_exact=True,main_changed_files=len(paths),
 catalog_admitted=True,new_native_family_credit=1,new_reviews=6,scoped_families=154,native=29,upstream=125,reviewed_uses=1697,pending_uses=1025,
 whole_goal_complete=False,full_checkWebsite_passed=False,preexisting_missing_citation_panels=33,other_owners_preserved=18,previously_rejected_historical_exports_included=False))
print(json.dumps(dict(status='PREPARED',commit=commit,main_files=len(paths),git_archive_exact=True,other_owners_preserved=18),indent=2))
