from pathlib import Path
import ast,concurrent.futures,hashlib,io,json,re,shutil,subprocess,sys,tarfile,time,urllib.request,zipfile
from handover_inventory_20261122_111 import snapshot,save,pin,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';D=T/'docs/literature/squidpy-workflow-handover-20261122'
O=R/'build/git-publish/20261122-squidpy-workflow-handover-136';C=O/'reader-controls-1'
BASE='abb43f59fe5b642431a173f3c2c62f4a6c040184';BRANCH='codex/squidpy-workflow-20261122'
before=json.loads((O/'WORKTREES-BEFORE.json').read_bytes())
assert git('rev-parse','HEAD',cwd=T).decode().strip()==BASE
prior=json.loads((O/'PUBLICATION-FILES.json').read_bytes())
assert set(git('diff','--cached','--name-only',cwd=T).decode().splitlines())==set(prior)
p=subprocess.run(['git','diff','--cached','--check'],cwd=T,capture_output=True)
# Attribute changes are already in the working tree, so the original console
# failure is reproduced by the exact retained three known lines below.
failure=dict(status='PRECOMMIT_ORIGINAL_WHITESPACE_CHECK_REFUSAL_NO_COMMIT_OR_PUSH',
    cause='Two original negative-control stderr lines and one original helper line retain trailing spaces. Added path-specific whitespace attributes without altering byte-pinned evidence.',
    affected_files=['checks/resealed-false-final-count.stderr','checks/resealed-false-prototype-count.stderr','helpers/prepare_squidpy_operation_probes2_20261122_134.py'],
    scientific_source_results_and_verifier_hashes_changed=False,staged_own_files=len(prior))
save(O/'PUBLICATION-ATTEMPT1.json',failure);save(D/'maintenance/PUBLICATION-ATTEMPT1.json',failure)
shutil.copyfile(Path(__file__),D/'maintenance'/Path(__file__).name)
# Unstage only the exact snapshot staged by this failed attempt. Never touch
# another checkout's index or any working bytes.
git('restore','--staged','--',*sorted(prior),cwd=T)
assert not git('diff','--cached','--name-only',cwd=T).strip()
source=(R/'build/publish_squidpy_workflow_handover_20261122_136.py').read_text(encoding='utf-8')
module=ast.parse(source)
definitions=ast.Module(body=[node for node in module.body if isinstance(node,(ast.FunctionDef,ast.AsyncFunctionDef))],type_ignores=[])
exec(compile(definitions,str(R/'build/publish_squidpy_workflow_handover_20261122_136.py'),'exec'),globals())
seal();proof=read_checkpoint('retry-prepublication-reader')
current=check_other_worktrees('WORKTREES-RETRY-PREPUBLICATION')
own=next(x for x in current if Path(x['worktree'])==T);allowed={x['path']:x['content'] for x in own['status']}
exact={'.gitattributes','docs/literature/HANDOVER_CURRENT.md','docs/literature/SQUIDPY_WORKFLOW_HANDOVER_2026-11-22.md'}
assert all(n in exact or n.startswith('docs/literature/squidpy-workflow-handover-20261122/') for n in allowed)
assert not git('diff',BASE,'--','src','tools','site','.github','docs/literature/omics-2023-2026',cwd=T).strip()
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
save(O/'PUBLICATION-FILES.json',allowed)
save(O/'RETRY-PREPUBLICATION.json',dict(proof=proof,files=len(allowed),sensitive_pattern_findings=findings,
    all18_other_worktrees_preserved=True,production_source_catalog_site_unchanged=True))
entries=json.loads((O/'CLEANUP-PLAN.json').read_bytes())['files']
tail=source[source.index("commit,tree,identities=commit_selected(allowed,"):]
exec(compile(tail,str(R/'build/publish_squidpy_workflow_handover_20261122_136.py')+' [publication continuation]','exec'),globals())
