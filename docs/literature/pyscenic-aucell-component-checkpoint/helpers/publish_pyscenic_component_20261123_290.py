from pathlib import Path
import hashlib,io,json,re,subprocess,tarfile,zipfile,shutil
from handover_inventory_20261122_111 import entries,git,pin,save,snapshot
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-scenic-270';Q=T/'docs/literature/pyscenic-aucell-component-checkpoint'
base='db45f34b42d0cff4d6393a9b1e460ce5bb5a67e1';branch='codex/scenic-workflows-20261123'
assert git('rev-parse','HEAD',cwd=T).decode().strip()==base and git('symbolic-ref','--short','HEAD',cwd=T).decode().strip()==branch
assert set(git('diff','--cached','--name-only',cwd=T).decode().splitlines())==set(json.loads((O/'PUBLICATION-FILES.json').read_bytes()))
assert git('ls-remote','--heads','origin','main',cwd=T).decode().split()[0]==base
shutil.copyfile(Path(__file__),Q/'helpers'/Path(__file__).name)
owners=snapshot();save(O/'WORKTREES-PREPUBLICATION.json',owners)
old={x['worktree']:x for x in json.loads((O/'WORKTREES-BEFORE.json').read_bytes())};current={x['worktree']:x for x in owners};others=[x for x in old if Path(x)!=T];indexes=json.loads((O/'INDEXES-BEFORE.json').read_bytes())
assert len(others)==18 and set(old)==set(current) and all(old[x]==current[x] for x in others)
assert all(pin(Path(indexes[x]['path']))==indexes[x]['identity'] for x in others)
paths=sorted(x['path'] for x in entries(T));assert 40<len(paths)<90,len(paths)
exact={'.gitattributes','docs/literature/HANDOVER_CURRENT.md','docs/literature/HANDOVER_2026-11-23.md','docs/literature/PYSCENIC_AUCELL_COMPONENT.md','docs/literature/PYSCENIC_COMPONENT_CHECKPOINT_2026-11-23.md',
 'src/main/java/org/jlinalg/enrichment/PyScenicAucell.java','src/test/java/org/jlinalg/enrichment/PyScenicAucellReferenceTest.java','src/test/resources/org/jlinalg/enrichment/pyscenic-original-aucell-reference.zip',
 'tools/literature/generate_pyscenic_aucell_reference.py','tools/literature/verify_pyscenic_aucell_component.py'}
prefixes=('docs/literature/pyscenic-aucell-component-checkpoint/','examples/pyscenic-aucell/','src/main/resources/org/jlinalg/omics/pyscenic-ce41b61/')
assert all(n in exact or n.startswith(prefixes) for n in paths),paths
assert not any(n.startswith(('site/','.github/','docs/literature/omics-2023-2026/')) for n in paths)
pins={n:pin(T/n) for n in paths};save(O/'PUBLICATION-FILES.json',pins)
patterns=[re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),re.compile(rb'github_pat_[A-Za-z0-9_]{30,}'),re.compile(rb'gh[pousr]_[A-Za-z0-9]{30,}'),re.compile(rb'AKIA[0-9A-Z]{16}')]
matches=[];scanned=0
def scan(name,raw,depth=0):
 global scanned
 scanned+=1
 if any(p.search(raw) for p in patterns):matches.append(name)
 if raw[:4]==b'PK\x03\x04' and depth<5:
  with zipfile.ZipFile(io.BytesIO(raw)) as archive:
   for item in archive.infolist():
    if not item.is_dir():scan(name+'!'+item.filename,archive.read(item),depth+1)
for name in paths:
 assert pins[name]['bytes']<50_000_000,name;scan(name,(T/name).read_bytes())
save(O/'CONTENT-REVIEW.json',dict(files=len(paths),scanned_members=scanned,basic_credential_marker_matches=matches,full_primary_XML_included=False,private_cohort_data_included=False,installed_original_runtime_binaries_included=False,selected_original_source_notices_GPL_BSD=True,previously_rejected_historical_exports_included=False,security_audit_claim=False));assert not matches,matches
state=json.loads((Q/'STATUS.json').read_bytes())
for name,wanted in state['source_code'].items():assert pin(T/name)==wanted,name
git('-c','core.autocrlf=false','add','--',*paths,cwd=T);git('-c','core.autocrlf=false','add','--renormalize','--',*paths,cwd=T)
p=subprocess.run(['git','diff','--cached','--check'],cwd=T,capture_output=True);(O/'staged-diff-check.stdout').write_bytes(p.stdout);(O/'staged-diff-check.stderr').write_bytes(p.stderr);assert p.returncode==0,p.stdout
for name in paths:assert git('show',':'+name,cwd=T)==(T/name).read_bytes(),'Staged bytes differ: '+name
message=O/'component-commit.txt';message.write_text('Add source-specific native pySCENIC AUCell activity scoring\n\n'
 'Implement NumPy-seeded ranking, weighted/unweighted source AUC, strict literal axes, source coverage/window and normalization diagnostics, bounded work and immutable results. Preserve the existing deterministic AUCell profile and all predecessor payloads.\n'
 'Validate 2,059 selected normal tests and exact packaged original-reference tests/examples on three JVMs; retain 37 original cases, 114 permutations, unsafe original returns, compatible private setup and all earlier failures.\n'
 'Record five paper-role drafts, missed P153 SCENIC+ benchmarking and exact next GRN/ctx/capture/replay work. Full SCENIC integration and catalog admission remain pending, with zero new family or review credit.\n',encoding='utf-8')
git('commit','--file',str(message),cwd=T);commit=git('rev-parse','HEAD',cwd=T).decode().strip();assert not entries(T)
assert git('rev-list','--parents','-n','1',commit,cwd=T).decode().split()==[commit,base]
data=git('archive','--format=tar',commit,'--',*paths,cwd=T);(O/'selected-source.tar').write_bytes(data)
with tarfile.open(fileobj=io.BytesIO(data)) as archive:files={m.name:archive.extractfile(m).read() for m in archive.getmembers() if m.isfile()}
assert set(files)==set(paths)
for name,raw in files.items():assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==pins[name]
save(O/'PUBLICATION-PREPARED.json',dict(commit=commit,base=base,branch=branch,files=len(paths),git_archive_exact=True,normal_tests=2059,previous_payloads_unchanged=22995,
 native_component_qualified=True,full_SCENIC_workflow_qualified=False,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False,other_owners_preserved=18))
print(json.dumps(dict(status='PREPARED',commit=commit,files=len(paths),other_owners_preserved=18),indent=2))
