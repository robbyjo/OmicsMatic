from pathlib import Path
import concurrent.futures,hashlib,io,json,re,shutil,subprocess,sys,tarfile,time,urllib.request,zipfile
from handover_inventory_20261122_111 import snapshot,save,pin,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261122-squidpy-catalog-149'
Q=T/'docs/literature/squidpy-catalog-checkpoint'
BASE='483805ba165efd89e632a32394f23d25ae822cac';BRANCH='codex/squidpy-catalog-20261122'
before=json.loads((O/'WORKTREES-BEFORE.json').read_bytes());indexes=json.loads((O/'INDEXES-BEFORE.json').read_bytes())
assert git('rev-parse','HEAD',cwd=T).decode().strip()==BASE
assert git('branch','--show-current',cwd=T).decode().strip()==BRANCH
assert git('ls-remote','origin','refs/heads/main',cwd=T).decode().split()[0]==BASE
assert not git('diff','--cached','--name-only',cwd=T).strip()
def preserve(label):
    current=snapshot();by={r['worktree']:r for r in current}
    assert len(current)==19 and all(by[r['worktree']]==r for r in before if Path(r['worktree'])!=T)
    for name,row in indexes.items():
        if Path(name)!=T:assert pin(Path(row['path']))==row['identity']
    save(O/(label+'.json'),current);return current
def seal():save(Q/'FILES.json',{p.relative_to(Q).as_posix():pin(p) for p in sorted(Q.rglob('*')) if p.is_file() and p.name!='FILES.json' and '__pycache__' not in p.parts})
for name in ('prepare_squidpy_catalog_publication_20261122_151.py','publish_squidpy_catalog_20261122_151.py'):
    shutil.copyfile(R/'build'/name,Q/name)
seal()
reader=subprocess.run([sys.executable,'-X','utf8','-B',str(T/'tools/literature/verify_squidpy_catalog_checkpoint.py')],cwd=T,capture_output=True,timeout=60)
(O/'prepublication-reader.stdout').write_bytes(reader.stdout);(O/'prepublication-reader.stderr').write_bytes(reader.stderr);assert reader.returncode==0,reader.stderr
current=preserve('WORKTREES-PREPUBLICATION');own=next(r for r in current if Path(r['worktree'])==T);allowed={r['path']:r['content'] for r in own['status']}
sys.path.insert(0,str(T/'tools/literature'));from squidpy_catalog import model as m
exact={'.gitattributes','HANDOVER_2026-11-22.md','docs/literature/HANDOVER_CURRENT.md','docs/literature/SQUIDPY_CATALOG_CHECKPOINT_2026-11-22.md',
       'tools/literature/audit_squidpy_sources.py','tools/literature/close_squidpy_catalog.py','tools/literature/verify_squidpy_catalog_checkpoint.py',
       m.PROOF,m.COVERAGE,m.DATA+'/SHA256SUMS',*m.META}
prefixes=('docs/literature/squidpy-catalog-checkpoint/','docs/literature/squidpy-catalog-sources/','docs/literature/squidpy-catalog-admission/','tools/literature/squidpy_catalog/')
assert all(n in exact or n.startswith(prefixes) for n in allowed),set(allowed)-exact
assert not git('diff',BASE,'--','src','.github',cwd=T).strip()
patterns=[rb'-----BEGIN [A-Z ]*PRIVATE KEY-----',rb'gh[pousr]_[A-Za-z0-9]{30,}',rb'github_pat_[A-Za-z0-9_]{30,}',rb'AKIA[0-9A-Z]{16}',rb'(?im)^set-cookie\s*:',rb'(?im)^authorization\s*:\s*(?:bearer|basic)\s+\S+']
findings=[];members=0
for name in allowed:
    parts=[(name,(T/name).read_bytes())]
    if name.endswith('.zip'):
        with zipfile.ZipFile(T/name) as archive:
            for member in archive.namelist():parts.append((name+'!'+member,archive.read(member)));members+=1
    for label,raw in parts:
        for pattern in patterns:
            if re.search(pattern,raw):findings.append(dict(path=label,pattern=pattern.decode()))
assert not findings,findings
links=[]
for name in ('docs/literature/SQUIDPY_CATALOG_CHECKPOINT_2026-11-22.md','docs/literature/squidpy-catalog-checkpoint/README.md','tools/literature/squidpy_catalog/README.md','tools/literature/squidpy_catalog/ADMISSION.md'):
    for target in re.findall(r'\]\(([^)]+)\)',(T/name).read_text(encoding='utf-8')):
        if not re.match(r'^[a-z]+:',target) and not target.startswith('#'):
            assert ((T/name).parent/target.split('#')[0]).exists(),(name,target)
            links.append(dict(source=name,target=target))
save(O/'PREPUBLICATION.json',dict(current_reader=json.loads(reader.stdout),files=len(allowed),sensitive_pattern_findings=findings,archive_members_scanned=members,
    links_checked=links,other18_worktrees_and_indexes_preserved=True,scientific_source_and_workflows_unchanged=True,
    full_checkWebsite_passed=False,preexisting_missing_citation_panels=33,historical_sensitive_exports_withheld=True,whole_goal_complete=False))
save(O/'PUBLICATION-FILES.json',allowed)
def commit_selected(names,message,expected_base,tag):
    identities={n:pin(T/n) for n in names}
    assert git('rev-parse','HEAD',cwd=T).decode().strip()==expected_base
    assert not git('diff','--cached','--name-only',cwd=T).strip()
    git('add','--',*sorted(names),cwd=T)
    assert set(git('diff','--cached','--name-only',cwd=T).decode().splitlines())==set(names)
    for name,expected in identities.items():
        raw=git('show',':'+name,cwd=T);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==expected,name
    git('diff','--cached','--check',cwd=T)
    git('commit','-m',message,cwd=T)
    commit=git('rev-parse','HEAD',cwd=T).decode().strip();tree=git('rev-parse','HEAD^{tree}',cwd=T).decode().strip()
    assert git('rev-parse','HEAD^',cwd=T).decode().strip()==expected_base
    archive_path=O/(tag+'-source.tar')
    with archive_path.open('xb') as out:p=subprocess.run(['git','archive',commit,'--',*sorted(names)],cwd=T,stdout=out,stderr=subprocess.PIPE)
    assert p.returncode==0,p.stderr
    with tarfile.open(archive_path) as archive:
        members=[m for m in archive if m.isfile()];assert {m.name for m in members}==set(names)
        for member in members:
            raw=archive.extractfile(member).read();assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==identities[member.name],member.name
    save(O/(tag+'-COMMITTED.json'),dict(commit=commit,tree=tree,base=expected_base,files=len(names),archive_bytes_exact=True))
    return commit,tree,identities

def fetch(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-reviewed-handover','Cache-Control':'no-cache'}),timeout=35) as response:return response.read()
        except Exception:
            if attempt==2:raise
            time.sleep(1)

def push_and_readback(commit,tree,identities,expected_base,tag):
    assert git('ls-remote','origin','refs/heads/main',cwd=T).decode().split()[0]==expected_base
    with (O/(tag+'-push.stdout')).open('xb') as out,(O/(tag+'-push.stderr')).open('xb') as err:
        p=subprocess.run(['git','push','--atomic','origin','HEAD:refs/heads/main','HEAD:refs/heads/'+BRANCH],cwd=T,stdout=out,stderr=err,timeout=120)
    assert p.returncode==0,(O/(tag+'-push.stderr')).read_text(errors='replace')
    refs=git('ls-remote','origin','refs/heads/main','refs/heads/'+BRANCH,cwd=T).decode().splitlines()
    assert len(refs)==2 and all(row.split()[0]==commit for row in refs)
    api='https://api.github.com/repos/robbyjo/OmicsMatic'
    assert json.loads(fetch(api+'/git/ref/heads/main?nocache='+commit))['object']['sha']==commit
    assert json.loads(fetch(api+'/git/commits/'+commit+'?nocache='+commit))['tree']['sha']==tree
    def check(name):
        raw=fetch('https://raw.githubusercontent.com/robbyjo/OmicsMatic/'+commit+'/'+name)
        identity=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());assert identity==identities[name],name
        return name,identity
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:public=dict(pool.map(check,sorted(identities)))
    save(O/(tag+'-PUBLIC-READBACK.json'),dict(commit=commit,files=public))
    print(json.dumps(dict(status='PUSHED_MAIN_AND_ALL_PUBLIC_BYTES_VERIFIED',phase=tag,commit=commit,files=len(public))),flush=True)

commit,tree,identities=commit_selected(allowed,'Admit six Squidpy source roles and qualified upstream workflows',BASE,'admission')
push_and_readback(commit,tree,identities,BASE,'admission')
preserve('WORKTREES-AFTER-PUSH');assert not git('status','--porcelain=v1','-uall',cwd=T).strip()
save(O/'PUBLISHED.json',dict(status='PUSHED_SQUIDPY_ADMISSION_AND_ALL_SELECTED_PUBLIC_BYTES_VERIFIED',commit=commit,tree=tree,files=len(allowed),
    other18_worktrees_and_indexes_preserved=True,pending=['exact-commit Pages deployment and live boundary','verified redundant reader-copy cleanup'],whole_goal_complete=False))
print(json.dumps(dict(status='ADMISSION_PUBLISHED',commit=commit,files=len(allowed))),flush=True)
