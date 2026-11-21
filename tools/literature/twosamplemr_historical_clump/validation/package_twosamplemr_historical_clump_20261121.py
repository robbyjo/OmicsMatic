from pathlib import Path
import ast,hashlib,json,os,shutil,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/mr-7';B=T/'build/twosamplemr-historical-clump-1';O=R/'build/git-publish/20261121-historical-clump-89';Q=T/'docs/literature/twosamplemr-historical-clump-checkpoint';D=T/'tools/literature/twosamplemr_historical_clump/validation';I=R/'build/twosamplemr-author-code-intake-20261121-1'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
assert (O/'WORKTREES-BEFORE.json').exists() and not (Q/'PROFILE.json').exists();D.mkdir(exist_ok=False)
helpers=sorted(set([*(R/'build').glob('*twosamplemr_historical_clump*20261121.py'),R/'build/prepare_historical_clump_qualification_20261121.py',R/'build/intake_twosamplemr_author_sources_20261121.py',R/'build/audit_twosamplemr_author_calls_20261121.py',R/'build/audit_twosamplemr_author_calls_retry_20261121.py',R/'build/audit_twosamplemr_author_calls_complete_20261121.py',R/'build/catalog_twosamplemr_author_attribution_20261121.py',Path(__file__)]))
for p in helpers:ast.parse(p.read_text(encoding='utf-8'));shutil.copyfile(p,D/p.name)
(D/'README.md').write_text('''# Retained0.6.8 clumping validation helpers

These scripts retain source preparation, original controls/comparisons, normal
build, packaged hosts, replays/examples, refusals, preservation and static author
attribution. They use historical absolute paths and fail-on-exist outputs.
Inspect/adapt them in NEW bounded directories; never overwrite old evidence or
protected/user R libraries. The first author parser failed on missing arguments;
the second omitted bare mr from its filter; the complete parser is the final
attempt. Downloaded author code is parsed, never evaluated. Exact runtimes and
private full source are transferred separately. Saved readers install/run none.
''',encoding='utf-8',newline='\n')
intro='''**Current: [original0.6.8 local clumping and ordered remaining work](TWOSAMPLEMR_HISTORICAL_CLUMP_CHECKPOINT_2026-11-21.md).**
The normal JAR passes **812 tests** per Windows Java21/25 and Linux Java21,
25 actual expectations (21 completed selections),14 missing/failed/source/lifetime
controls, three real replays, three examples and five Linux R-free verifications.
Fresh original calls match **81 complete returns/RNG**; three error comparisons
match class/static header/RNG while retaining volatile logs. All149 tagged
TwoSampleMR functions and seven ieugwasr functions are guarded; the two observed
provider bindings are restored. All22,357 preceding JAR payloads, private runtimes,
27 canonical files and45 worktrees remain preserved. New static P157/P177 code
attribution is a proposal: archived bidirectional MR for P157; explicit separate
IVW provider and inferred median/mode provider for P177. **Next: remaining
provider/API/source gaps, global registration and guarded admission.** Counts
stay143 scoped families,1,616 reviewed/1,104 pending with zero new credit. Both
reports stay off Pages. Transfer ignored runtimes/JARs/full text/private evidence
separately with checksums. The full goal is unfinished; lower entries retain history.

'''
current=T/'docs/literature/HANDOVER_CURRENT.md';old=current.read_text(encoding='utf-8');assert old.startswith('# Current toolbox handover\n\n');current.write_text('# Current toolbox handover\n\n'+intro+old.split('\n\n',1)[1],encoding='utf-8',newline='\n')
inventory={};blobs={};omitted={}
def add(name,p):
    raw=p.read_bytes();assert name not in inventory and len(raw)<=32*1024*1024 and not p.is_symlink();wanted=pin(raw);inventory[name]=wanted;blobs[wanted['sha256']]=raw
for root,dirs,files in os.walk(B):
    for name in list(dirs):
        if name in {'project-build','project-cache','classes','test-classes'}:dirs.remove(name);omitted['current/'+(Path(root)/name).relative_to(B).as_posix()]=dict(reason='Generated build/cache/classes; raw normal XML separately retained')
    for name in sorted(files):
        p=Path(root)/name;logical='current/'+p.relative_to(B).as_posix()
        if p.suffix=='.exe':omitted[logical]=dict(reason='Deliberately unqualified binary; never executed',**pin(p.read_bytes()));continue
        add(logical,p)
for p in (B/'normal-gradle-1/project-build/test-results/test').glob('TEST-*.xml'):add('current/normal-gradle-1/project-build/test-results/test/'+p.name,p)
for root in sorted(I.glob('static-call-audit-*')):
    for p in root.iterdir():
        if p.is_file() and p.name!='RESULT.json':add('author-static-audit/'+root.name+'/'+p.name,p)
for name in ['WORKTREES-BEFORE.json','CANONICAL-UNCHANGED.json']:shutil.copyfile(O/name,Q/name)
private={p.relative_to(I).as_posix():pin(p.read_bytes()) for p in sorted(I.rglob('*')) if p.is_file()}
save(Q/'OMITTED.json',dict(generated_or_private_files=omitted,private_author_source_intake=dict(root=str(I),files=private,included_bytes=False),preceding_private_transfer='docs/literature/twosamplemr-historical-handover/PRIVATE-TRANSFER.json',producer_jar=dict(path=str(B/'normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar'),**pin((B/'normal-gradle-1/project-build/cli/omicsmatic-0.4.0.jar').read_bytes())),not_a_complete_machine_backup=True))
with zipfile.ZipFile(Q/'checks.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for sha,raw in sorted(blobs.items()):
        info=zipfile.ZipInfo('blobs/'+sha,(2026,11,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw)
save(Q/'INVENTORY.json',inventory)
sources=json.loads((B/'normal-gradle-1/COMMAND.json').read_bytes())['source_inputs']
for name,wanted in sources.items():assert pin((T/name).read_bytes())==wanted,name
for pattern in ['tools/literature/twosamplemr_historical_clump/**/*','examples/twosamplemr-historical-clump/**/*','docs/literature/twosamplemr-author-attribution-2026-11-21/*']:
    for p in T.glob(pattern):
        if p.is_file():sources[p.relative_to(T).as_posix()]=pin(p.read_bytes())
for name in ['tools/literature/verify_twosamplemr_historical_clump_checkpoint.py','docs/literature/TWOSAMPLEMR_HISTORICAL_CLUMP_CHECKPOINT_2026-11-21.md','docs/literature/TWOSAMPLEMR_HISTORICAL_LOCAL_CLUMPING.md']:sources[name]=pin((T/name).read_bytes())
canonical=json.loads((O/'CANONICAL-UNCHANGED.json').read_bytes());profile=dict(status='PASS_ORIGINAL_HISTORICAL_LOCAL_CLUMP812_NOT_CATALOG_ADMISSION',new_catalog_credit=0,whole_goal_complete=False,global_CLI_registered=False,historical_paper_editions_qualified=False,historical_local_clumping_tag_qualified=True,source_inputs=sources,canonical_unchanged=canonical,archive=pin((Q/'checks.zip').read_bytes()),inventory=pin((Q/'INVENTORY.json').read_bytes()),omitted=pin((Q/'OMITTED.json').read_bytes()),logical_files=len(inventory),unique_blobs=len(blobs))
save(Q/'PROFILE.json',profile)
allowed={}
for pattern in ['src/main/java/org/jlinalg/cli/TwoSampleMrHistoricalClump*.java','src/main/resources/org/jlinalg/omics/twosamplemr-historical-clump/*','tools/literature/twosamplemr_historical_clump/**/*','examples/twosamplemr-historical-clump/**/*','docs/literature/twosamplemr-author-attribution-2026-11-21/*','docs/literature/twosamplemr-historical-clump-checkpoint/*']:
    for p in T.glob(pattern):
        if p.is_file():allowed[p.relative_to(T).as_posix()]=pin(p.read_bytes())
for name in ['src/test/java/org/jlinalg/cli/TwoSampleMrHistoricalClumpTest.java','src/test/resources/org/jlinalg/cli/twosamplemr-historical-clump-0.6.8.zip','tools/literature/verify_twosamplemr_historical_clump_checkpoint.py','docs/literature/TWOSAMPLEMR_HISTORICAL_CLUMP_CHECKPOINT_2026-11-21.md','docs/literature/TWOSAMPLEMR_HISTORICAL_LOCAL_CLUMPING.md','docs/literature/HANDOVER_CURRENT.md']:allowed[name]=pin((T/name).read_bytes())
save(O/'ALLOWED.json',allowed);print(json.dumps(dict(public_files=len(allowed),logical_files=len(inventory),unique_blobs=len(blobs),archive=pin((Q/'checks.zip').read_bytes()))),flush=True)
