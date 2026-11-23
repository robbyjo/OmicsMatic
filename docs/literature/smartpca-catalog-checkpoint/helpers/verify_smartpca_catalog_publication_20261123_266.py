from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import hashlib,json,urllib.request,urllib.parse
from handover_inventory_20261122_111 import git,save,snapshot,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-smartpca-catalog-255'
prepared=json.loads((O/'PUBLICATION-PREPARED.json').read_bytes());expected=json.loads((O/'MAIN-PUBLICATION-FILES.json').read_bytes());commit=prepared['main_candidate']
refs={line.split()[1]:line.split()[0] for line in git('ls-remote','--heads','origin','main',prepared['branch'],cwd=T).decode().splitlines()}
assert refs['refs/heads/main']==refs['refs/heads/'+prepared['branch']]==commit
def read(url):
 request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-handover-readback','Cache-Control':'no-cache'})
 with urllib.request.urlopen(request,timeout=45) as response:return response.read()
def check(item):
 name,wanted=item;raw=read('https://raw.githubusercontent.com/robbyjo/OmicsMatic/'+commit+'/'+urllib.parse.quote(name,safe='/'))
 actual=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());assert actual==wanted,(name,actual,wanted)
 return dict(path=name,identity=actual)
with ThreadPoolExecutor(max_workers=8) as pool:results=list(pool.map(check,expected.items()))
save(O/'GITHUB-READBACK.json',dict(status='PASS',commit=commit,remote_refs=refs,raw_files=results,catalog_admitted=True,whole_goal_complete=False,
 previously_rejected_historical_exports_performed=False))
runs=json.loads(read('https://api.github.com/repos/robbyjo/OmicsMatic/actions/runs?head_sha='+commit+'&per_page=20'))
jobs=[dict(id=x['id'],head_sha=x['head_sha'],name=x['name'],status=x['status'],conclusion=x['conclusion'],url=x['html_url']) for x in runs['workflow_runs']]
save(O/'ACTIONS-AFTER-PUSH.json',dict(commit=commit,runs=jobs))
after=snapshot();save(O/'WORKTREES-AFTER-PUSH.json',after)
old={x['worktree']:x for x in json.loads((O/'WORKTREES-BEFORE.json').read_bytes())};current={x['worktree']:x for x in after};others=[x for x in old if Path(x)!=T]
assert len(others)==18 and set(old)==set(current) and all(old[x]==current[x] for x in others)
indexes=json.loads((O/'INDEXES-BEFORE.json').read_bytes());assert all(pin(Path(indexes[x]['path']))==indexes[x]['identity'] for x in others)
assert not git('status','--porcelain=v1',cwd=T)
save(O/'PRESERVATION-AFTER-PUSH.json',dict(status='PASS',other_owners_heads_dirty_bytes_indexes_preserved=18,registered_worktrees=19,current_checkout_clean=True))
print(json.dumps(dict(status='PASS',commit=commit,public_files_byte_exact=len(results),other_owners_preserved=18,workflow_runs=jobs),indent=2))
