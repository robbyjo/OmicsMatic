from pathlib import Path
R=Path('D:/projects/JLinAlg');O=R/'build/git-publish/20261122-squidpy-catalog-149'
previous=(R/'build/publish_squidpy_qualified_handover_20261122_148.py').read_text()
functions=previous[previous.index('def commit_selected('):previous.index('\ncommit,tree,identities=commit_selected(')]
prefix='''from pathlib import Path
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
patterns=[rb'-----BEGIN [A-Z ]*PRIVATE KEY-----',rb'gh[pousr]_[A-Za-z0-9]{30,}',rb'github_pat_[A-Za-z0-9_]{30,}',rb'AKIA[0-9A-Z]{16}',rb'(?im)^set-cookie\\s*:',rb'(?im)^authorization\\s*:\\s*(?:bearer|basic)\\s+\\S+']
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
    for target in re.findall(r'\\]\\(([^)]+)\\)',(T/name).read_text(encoding='utf-8')):
        if not re.match(r'^[a-z]+:',target) and not target.startswith('#'):
            assert ((T/name).parent/target.split('#')[0]).exists(),(name,target)
            links.append(dict(source=name,target=target))
save(O/'PREPUBLICATION.json',dict(current_reader=json.loads(reader.stdout),files=len(allowed),sensitive_pattern_findings=findings,archive_members_scanned=members,
    links_checked=links,other18_worktrees_and_indexes_preserved=True,scientific_source_and_workflows_unchanged=True,
    full_checkWebsite_passed=False,preexisting_missing_citation_panels=33,historical_sensitive_exports_withheld=True,whole_goal_complete=False))
save(O/'PUBLICATION-FILES.json',allowed)
'''
tail='''
commit,tree,identities=commit_selected(allowed,'Admit six Squidpy source roles and qualified upstream workflows',BASE,'admission')
push_and_readback(commit,tree,identities,BASE,'admission')
preserve('WORKTREES-AFTER-PUSH');assert not git('status','--porcelain=v1','-uall',cwd=T).strip()
save(O/'PUBLISHED.json',dict(status='PUSHED_SQUIDPY_ADMISSION_AND_ALL_SELECTED_PUBLIC_BYTES_VERIFIED',commit=commit,tree=tree,files=len(allowed),
    other18_worktrees_and_indexes_preserved=True,pending=['exact-commit Pages deployment and live boundary','verified redundant reader-copy cleanup'],whole_goal_complete=False))
print(json.dumps(dict(status='ADMISSION_PUBLISHED',commit=commit,files=len(allowed))),flush=True)
'''
out=R/'build/publish_squidpy_catalog_20261122_151.py';assert not out.exists();out.write_text(prefix+functions+tail,encoding='utf-8',newline='\n')
pages=(R/'build/check_fusion_pages_20261122_127.py').read_text().replace('20261122-fusion-catalog-127','20261122-squidpy-catalog-149').replace("O/'COMMITTED.json'","O/'admission-COMMITTED.json'").replace('OmicsMatic-FUSION-Pages-verification','OmicsMatic-Squidpy-Pages-verification')
out=R/'build/check_squidpy_catalog_pages_20261122_151.py';assert not out.exists();out.write_text(pages,encoding='utf-8',newline='\n')
print('PREPARED_REVIEWED_PUBLICATION_AND_EXACT_COMMIT_PAGES_CHECK',flush=True)
