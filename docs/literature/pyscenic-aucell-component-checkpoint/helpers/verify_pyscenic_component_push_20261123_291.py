from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import hashlib,json,urllib.request,urllib.parse
from handover_inventory_20261122_111 import git,pin,save,snapshot
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-scenic-270'
prepared=json.loads((O/'PUBLICATION-PREPARED.json').read_bytes());commit=prepared['commit'];expected=json.loads((O/'PUBLICATION-FILES.json').read_bytes())
refs={row.split()[1]:row.split()[0] for row in git('ls-remote','--heads','origin','main',prepared['branch'],cwd=T).decode().splitlines()};assert set(refs.values())=={commit}
def get(url):
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-smartpca-fst-handover-readback','Cache-Control':'no-cache'}),timeout=45) as response:return response.read()
def check(item):
 name,wanted=item;raw=get('https://raw.githubusercontent.com/robbyjo/OmicsMatic/'+commit+'/'+urllib.parse.quote(name,safe='/'))
 actual=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());assert actual==wanted,name;return dict(path=name,identity=actual)
with ThreadPoolExecutor(max_workers=8) as pool:files=list(pool.map(check,expected.items()))
runs=json.loads(get('https://api.github.com/repos/robbyjo/OmicsMatic/actions/runs?head_sha='+commit+'&per_page=20'))
jobs=[dict(id=r['id'],name=r['name'],status=r['status'],conclusion=r['conclusion'],url=r['html_url']) for r in runs['workflow_runs']]
save(O/'GITHUB-READBACK.json',dict(status='PASS',commit=commit,refs=refs,files=files,workflow_runs=jobs,new_release_published=False,native_component_qualified=True,full_SCENIC_workflow_qualified=False,catalog_admitted=False))
after=snapshot();save(O/'WORKTREES-AFTER-PUSH.json',after)
old={x['worktree']:x for x in json.loads((O/'WORKTREES-BEFORE.json').read_bytes())};current={x['worktree']:x for x in after};others=[x for x in old if Path(x)!=T]
indexes=json.loads((O/'INDEXES-BEFORE.json').read_bytes());assert len(others)==18 and set(old)==set(current)
assert all(old[x]==current[x] for x in others) and all(pin(Path(indexes[x]['path']))==indexes[x]['identity'] for x in others)
assert not git('status','--porcelain=v1',cwd=T)
save(O/'PRESERVATION-AFTER-PUSH.json',dict(status='PASS',other_owners_preserved=18,current_checkout_clean=True,registered_worktrees=19))
print(json.dumps(dict(status='PASS',commit=commit,files_byte_exact=len(files),other_owners_preserved=18,workflow_runs=jobs),indent=2))
