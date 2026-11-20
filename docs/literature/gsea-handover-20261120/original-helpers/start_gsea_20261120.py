from pathlib import Path
import importlib.util,json,subprocess
R=Path('D:/projects/JLinAlg');K=R/'build/edg-3';G=R/'build/gs-1';P=R/'build/git-publish/20261120-edger-admission-60'
O=R/'build/gsea-start-20261120-1';O.mkdir(exist_ok=False)
BASE='0de612c6182b87d42a1d574af5f06a99630a0924'
def git(root,*args):return subprocess.check_output(['git','--no-replace-objects',*args],cwd=root)
def save(n,v):(O/n).write_bytes((json.dumps(v,indent=2)+'\n').encode())
receipt=json.loads((P/'PUBLISHED.json').read_bytes());trees=json.loads((P/'WORKTREES-EXPECTED.json').read_bytes())
assert receipt['head']==BASE and receipt['pages_deployment_successful'] and len(trees)==28
assert git(K,'rev-parse','HEAD').decode().strip()==BASE and git(K,'ls-remote','origin','refs/heads/main').decode().split()[0]==BASE
spec=importlib.util.spec_from_file_location('inventory',P.parent/'20261115-all-main-3/inventory.py');inv=importlib.util.module_from_spec(spec);spec.loader.exec_module(inv)
for row in trees:assert inv.snapshot(Path(row['root']))==row,row['root']
save('WORKTREES-BEFORE.json',trees)
assert not G.exists()
p=subprocess.run(['git','worktree','add','-b','codex/gsea-high-impact-20261120',str(G),BASE],cwd=R,capture_output=True)
(O/'worktree.stdout').write_bytes(p.stdout);(O/'worktree.stderr').write_bytes(p.stderr);assert p.returncode==0,p.stderr[-1500:]
assert git(G,'rev-parse','HEAD').decode().strip()==BASE and not git(G,'status','--porcelain=v1','-z','--untracked-files=all')
(G/'build/gsea-high-impact-1').mkdir(parents=True)
save('COMPLETED.json',dict(status='PASS_EXACT_EDGER_ADMITTED_BASE_AND_NEW_GSEA_WORKTREE',base=BASE,root=str(G),worktrees=29))
print('PASS exact admitted base and new GSEA checkout',G,flush=True)
