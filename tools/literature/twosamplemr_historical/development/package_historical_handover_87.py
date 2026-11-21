from pathlib import Path
import ast,collections,hashlib,json,os,shutil,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1';O=R/'build/git-publish/20261121-historical-handover-87';Q=T/'docs/literature/twosamplemr-historical-handover';D=T/'tools/literature/twosamplemr_historical/development'
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
assert (B/'packaged-1/RESULT.json').exists() and not (Q/'PROFILE.json').exists()
D.mkdir(parents=True,exist_ok=False)
helpers=sorted(set([*(R/'build').glob('*twosamplemr_historical*20261121*.py'),*(R/'build').glob('*twosamplemr_049*20261121*.py'),R/'build/run_packaged_twosamplemr_historical_handover_87.py',R/'build/prepare_historical_handover_87.py',Path(__file__)]))
for p in helpers:
    ast.parse(p.read_text(encoding='utf-8'));shutil.copyfile(p,D/p.name)
(D/'README.md').write_text('''# Historical development helpers

These are retained development attempts, not a one-command installer. Inspect
absolute Windows paths and fail-on-exist outputs before adapting to a NEW
directory. Earlier generators retain their original failed setup assumptions;
the checked-in Java/resources and normal-gradle-3 source snapshot are current.
The handover distinguishes quick-compiled pilot results from normal-JAR tests
and unfinished actual normal-JAR runtime qualification. Never overwrite old
evidence or write to protected preceding/user R libraries.
''',encoding='utf-8',newline='\n')
intro='''**Current handover: [historical TwoSampleMR and exact next actions](HANDOVER_2026-11-21_HISTORICAL_TWOSAMPLEMR.md).**
Four source-tagged editions now have compiled development adapters for formatting,
harmonization, MR/sensitivity/modes and multivariable preparation. All **769 focused
tests** pass in the normal build and per Windows Java21/25 and Linux Java21 packaged
JVM; all **22,335 prior JAR payloads** remain exact. An earlier quick-compiled pilot
matches 120 full original returns and four raised errors, including RNG endpoints;
that comparison is **not normal-JAR live execution qualification**. Actual normal-JAR
analyses/replays/timeout controls, historical clumping, provider/source attribution,
global registration and guarded catalog admission remain open. **0.4.9 uses a
documented documentation-only build; dependency-era reproduction is not established.**
Counts stay **143 scoped families, 1,616 reviewed uses and 1,104 pending**, with zero
new credit. Preserve all **43 worktrees**. Ignored exact runtimes, JARs, full text
and complete private evidence need a separate checksummed transfer. Both literature
reports stay off Pages; the full goal is unfinished. Use this linked handover first;
lower entries retain earlier checkpoints.

'''
current=T/'docs/literature/HANDOVER_CURRENT.md';old=current.read_text(encoding='utf-8');assert old.startswith('# Current toolbox handover\n\n');current.write_text('# Current toolbox handover\n\n'+intro+old.split('\n\n',1)[1],encoding='utf-8',newline='\n')
inventory={};blobs={};omitted=collections.defaultdict(lambda:dict(files=0,bytes=0))
def add(name,p):
    assert p.is_file() and not p.is_symlink();raw=p.read_bytes();wanted=rawpin(raw)
    assert name not in inventory and len(raw)<=32*1024*1024,name
    inventory[name]=wanted;blobs[wanted['sha256']]=raw
for root,dirs,files in os.walk(B):
    for name in list(dirs):
        if name in {'project-build','project-cache','classes','test-classes'}:
            dirs.remove(name);omitted['current/'+(Path(root)/name).relative_to(B).as_posix()]=dict(reason='Generated build/cache/classes kept privately; normal XML separately retained')
    for name in sorted(files):add('current/'+(Path(root)/name).relative_to(B).as_posix(),Path(root)/name)
for attempt in range(1,4):
    base=B/f'normal-gradle-{attempt}/project-build/test-results/test'
    files=sorted(base.glob('TEST-*.xml'));assert len(files)==4
    for p in files:add(f'current/normal-gradle-{attempt}/test-results/'+p.name,p)
for prefix,root in [('source-intake',R/'build/twosamplemr-historical-intake-20261121-1'),('private-runtime-install',R/'build/twosamplemr-historical-runtime-20261121-1')]:
    for p in sorted(root.glob('*')):
        if p.is_file() and p.suffix not in {'.zip','.gz','.rds'}:add(prefix+'/'+p.name,p)
    if prefix=='source-intake':
        for p in sorted(root.glob('*/RESULT.json')):add(prefix+'/'+p.relative_to(root).as_posix(),p)
    else:
        for p in sorted(root.glob('versions/*/*')):
            if p.is_file():add(prefix+'/'+p.relative_to(root).as_posix(),p)
add('author-link-intake/RESULT.json',R/'build/twosamplemr-author-intake-20261121-1/RESULT.json')
for name in ['WORKTREES-BEFORE.json','PREDECESSOR-PRESERVATION.json']:shutil.copyfile(O/name,Q/name)
private={}
for label,root in [('historical-source',R/'build/twosamplemr-historical-intake-20261121-1'),('historical-runtime',R/'build/twosamplemr-historical-runtime-20261121-1'),('author-fulltext',R/'build/twosamplemr-author-intake-20261121-1')]:
    files={p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()}
    private[label]=dict(root=str(root),files=files,total_files=len(files),total_bytes=sum(v['bytes'] for v in files.values()),included_bytes=False)
    print('Private census',label,len(files),flush=True)
protected=R/'build/twosamplemr-runtime-intake-20261121-1';prior=json.loads((R/'build/mr-2/build/twosamplemr-workflow-1/runtime-census-1/FILES.json').read_bytes())
actual={p.relative_to(protected).as_posix():pin(p) for p in sorted(protected.rglob('*')) if p.is_file()};assert actual==prior and len(actual)==5765
private['protected-predecessor-runtime']=dict(root=str(protected),files=actual,total_files=len(actual),total_bytes=sum(v['bytes'] for v in actual.values()),unchanged=True,included_bytes=False)
for name in ['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','C:/Program Files/R/R-4.6.1/bin/R.exe',str(B/'normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar')]:
    private[name]=dict(path=name,**pin(Path(name)),included_bytes=False)
save(Q/'PRIVATE-TRANSFER.json',private);save(Q/'OMITTED.json',dict(generated=dict(omitted),private_roots='PRIVATE-TRANSFER.json',not_a_complete_machine_backup=True))
with zipfile.ZipFile(Q/'evidence.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for sha,raw in sorted(blobs.items()):
        info=zipfile.ZipInfo('blobs/'+sha,(2026,11,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw)
save(Q/'INVENTORY.json',inventory)
sources=[]
for pattern in ['src/main/java/org/jlinalg/cli/TwoSampleMrHistorical*.java','src/main/resources/org/jlinalg/omics/twosamplemr-historical/**/*','src/test/java/org/jlinalg/cli/TwoSampleMrHistoricalTest.java','src/test/resources/org/jlinalg/cli/twosamplemr-historical.zip','tools/literature/twosamplemr_historical/**/*']:
    sources.extend(p for p in T.glob(pattern) if p.is_file())
sources.extend(T/p for p in ['tools/literature/verify_twosamplemr_historical_handover.py','docs/literature/HANDOVER_2026-11-21_HISTORICAL_TWOSAMPLEMR.md','docs/literature/HANDOVER_CURRENT.md'])
source_inputs={p.relative_to(T).as_posix():pin(p) for p in sorted(set(sources))}
canonical=json.loads((O.parent/'20261121-twosamplemr-clump-86/CANONICAL-UNCHANGED.json').read_bytes());assert len(canonical)==27
for name,wanted in canonical.items():assert pin(T/name)==wanted,name
save(O/'CANONICAL-UNCHANGED.json',canonical)
files=['README.md','PRIVATE-TRANSFER.json','OMITTED.json','WORKTREES-BEFORE.json','PREDECESSOR-PRESERVATION.json','evidence.zip','INVENTORY.json']
profile=dict(status='UNFINISHED_HISTORICAL_TWOSAMPLEMR_HANDOVER',base='00b29b144709d2bcda23f6edabdc69af68818adb',new_catalog_credit=0,whole_goal_complete=False,live_normal_JAR_analyses_qualified=False,normal_and_packaged_tests=769,source_inputs=source_inputs,canonical_unchanged=canonical,packet_files={n:pin(Q/n) for n in files},logical_files=len(inventory),unique_blobs=len(blobs),private_runtime_bytes_included=False,worktrees_preserved=43)
save(Q/'PROFILE.json',profile)
allowed={n:pin(T/n) for n in source_inputs}
for p in Q.iterdir():
    if p.is_file():allowed[p.relative_to(T).as_posix()]=pin(p)
save(O/'ALLOWED.json',allowed)
print(json.dumps(dict(logical_files=len(inventory),unique_blobs=len(blobs),unique_bytes=sum(len(b) for b in blobs.values()),archive=pin(Q/'evidence.zip'),public_files=len(allowed),protected_runtime_files=5765)),flush=True)
