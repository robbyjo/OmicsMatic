from pathlib import Path
import hashlib,io,json,re,subprocess,tarfile,zipfile,shutil
from handover_inventory_20261122_111 import entries,git,pin,save,snapshot
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-smartpca-fst-240';Q=T/'docs/literature/smartpca-fst-qualification'
base='eb167472e9e71cc51483e338ee3b8d32ca7bdcd8';branch='codex/smartpca-fst-20261123'
assert git('rev-parse','HEAD',cwd=T).decode().strip()==base and git('symbolic-ref','--short','HEAD',cwd=T).decode().strip()==branch
assert not git('diff','--cached','--name-only',cwd=T)
assert git('ls-remote','--heads','origin','main',cwd=T).decode().split()[0]==base
for name in ('prepare_smartpca_fst_handover_20261123_250.py',Path(__file__).name):shutil.copyfile(R/'build'/name,Q/'helpers'/name)
owners=snapshot();save(O/'WORKTREES-PREPUBLICATION.json',owners)
old={x['worktree']:x for x in json.loads((O/'WORKTREES-BEFORE.json').read_bytes())};current={x['worktree']:x for x in owners};others=[x for x in old if Path(x)!=T];indexes=json.loads((O/'INDEXES-BEFORE.json').read_bytes())
assert len(others)==18 and set(old)==set(current) and all(old[x]==current[x] for x in others)
assert all(pin(Path(indexes[x]['path']))==indexes[x]['identity'] for x in others)
paths=sorted(x['path'] for x in entries(T));state=json.loads((Q/'STATUS.json').read_bytes())
exact={'.gitattributes','docs/literature/HANDOVER_CURRENT.md','docs/literature/HANDOVER_2026-11-23.md','docs/literature/SMARTPCA_TOOLS.md','docs/literature/HANDOVER_SMARTPCA_FST_2026-11-23.md',
 'src/main/java/org/jlinalg/cli/JLinAlgCli.java','src/main/java/org/jlinalg/cli/SmartpcaCommandCli.java','src/main/java/org/jlinalg/genetics/SmartpcaFst.java',
 'src/test/java/org/jlinalg/cli/SmartpcaFstWorkflowTest.java','src/test/java/org/jlinalg/genetics/SmartpcaFstReferenceTest.java','src/test/resources/org/jlinalg/genetics/smartpca-original-fst-reference.zip',
 'tools/literature/check_smartpca_fst_workflows.py','tools/literature/generate_smartpca_fst_reference.py','tools/literature/verify_smartpca_fst_checkpoint.py','tools/literature/smartpca_dispatch_compatibility.py','tools/literature/smartpca_reference/jackknife_harness.c'}
exact.update('src/main/java/org/jlinalg/cli/'+n+'.java' for n in ('SmartpcaFstAudit','SmartpcaFstCli','SmartpcaFstContracts','SmartpcaFstEvaluation','SmartpcaFstTables'))
assert len(paths)>30 and all(p in exact or p.startswith(('docs/literature/smartpca-fst-qualification/','examples/smartpca-fst/')) for p in paths),paths
pins={name:pin(T/name) for name in paths};save(O/'PUBLICATION-FILES.json',pins)
patterns=[re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),re.compile(rb'github_pat_[A-Za-z0-9_]{30,}'),re.compile(rb'gh[pousr]_[A-Za-z0-9]{30,}'),re.compile(rb'AKIA[0-9A-Z]{16}')]
matches=[];scanned=0
def scan(name,raw,depth=0):
 global scanned
 scanned+=1
 if any(p.search(raw) for p in patterns):matches.append(name)
 if raw[:4]==b'PK\x03\x04' and depth<5:
  with zipfile.ZipFile(io.BytesIO(raw)) as archive:
   for member in archive.infolist():
    if not member.is_dir():scan(name+'!'+member.filename,archive.read(member),depth+1)
for name in paths:
 assert pins[name]['bytes']<50_000_000,name;scan(name,(T/name).read_bytes())
save(O/'CONTENT-REVIEW.json',dict(files=len(paths),scanned_members=scanned,basic_credential_marker_matches=matches,synthetic_reference_inputs_only=True,full_primary_texts_included=False,private_cohort_data_included=False,original_runtime_binaries_included=False,previously_rejected_historical_exports_included=False,security_audit_claim=False));assert not matches,matches
for name,wanted in state['source_code'].items():assert pin(T/name)==wanted,name
for folder in ('smartpca-native-development','smartpca-analysis-handover','smartpca-workflow-qualification'):
 frozen=json.loads((T/'docs/literature'/folder/'STATUS.json').read_bytes())
 for name,wanted in frozen['source_code'].items():assert pin(T/name)==wanted,name
git('-c','core.autocrlf=false','add','--',*paths,cwd=T);git('-c','core.autocrlf=false','add','--renormalize','--',*paths,cwd=T)
checked=subprocess.run(['git','diff','--cached','--check'],cwd=T,capture_output=True);(O/'staged-diff-check.stdout').write_bytes(checked.stdout);(O/'staged-diff-check.stderr').write_bytes(checked.stderr);assert checked.returncode==0,checked.stdout
for name in paths:assert git('show',':'+name,cwd=T)==(T/name).read_bytes(),'Staged bytes differ: '+name
message=O/'source-commit.txt';message.write_text('Add native smartpca FST workflows and current handover\n\n'
 'Provide bounded source-specific FST, inbreeding and weighted jackknife with complete recomputation, replay, per-field admission, and global PCA/FST dispatch.\n'
 'Retain original program/function comparisons, failed attempts and 1,906 passing normal tests plus 362 packaged protocol commands across three JVMs. Preserve predecessor source contexts through an explicit three-line dispatcher bridge.\n'
 'Document exact remaining catalog work, private transfer and warning remedies. Guarded six-role admission and the full 250-paper goal remain open; no new family or review credit.\n',encoding='utf-8')
git('commit','--file',str(message),cwd=T);commit=git('rev-parse','HEAD',cwd=T).decode().strip();assert not entries(T)
assert git('rev-list','--parents','-n','1',commit,cwd=T).decode().split()==[commit,base]
data=git('archive','--format=tar',commit,'--',*paths,cwd=T);(O/'published-selected-source.tar').write_bytes(data)
with tarfile.open(fileobj=io.BytesIO(data)) as archive:files={m.name:archive.extractfile(m).read() for m in archive.getmembers() if m.isfile()}
assert set(files)==set(paths)
for name,raw in files.items():assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==pins[name],name
save(O/'PUBLICATION-PREPARED.json',dict(commit=commit,base=base,branch=branch,files=len(paths),git_archive_exact=True,normal_tests=1906,packaged_protocol_commands=362,native_fst_workflow_qualified=True,global_dispatch_integrated=True,catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False,other_owners_preserved=18))
print(json.dumps(dict(status='PREPARED',commit=commit,files=len(paths),other_owners_preserved=18),indent=2))
