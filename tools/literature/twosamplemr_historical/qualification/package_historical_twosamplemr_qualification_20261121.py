from pathlib import Path
import ast,hashlib,json,os,shutil,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/mr-6';B=T/'build/twosamplemr-historical-qualification-1';P=R/'build/mr-5/build/twosamplemr-historical-1';O=R/'build/git-publish/20261121-historical-qualification-88';Q=T/'docs/literature/twosamplemr-historical-checkpoint';D=T/'tools/literature/twosamplemr_historical/qualification'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
assert (O/'WORKTREES-BEFORE.json').exists() and not (Q/'PROFILE.json').exists();D.mkdir(exist_ok=False)
helpers=['run_normal_historical_twosamplemr_20261121.py','run_normal_historical_twosamplemr_20261121-attempt-1.py','qualify_historical_twosamplemr_cli_20261121.py','qualify_historical_twosamplemr_replays_20261121.py','protect_historical_twosamplemr_qualification_20261121.py',Path(__file__).name]
for name in helpers:
    p=R/'build'/name;ast.parse(p.read_text(encoding='utf-8'));shutil.copyfile(p,D/name)
(D/'README.md').write_text('''# Historical workflow qualification helpers

These scripts retain the actual normal-JAR execution, original comparisons,
private negative package build, real replays/examples and runtime protection.
They use historical absolute Windows paths and fail-on-exist output directories.
Inspect/adapt to NEW bounded outputs before running; never overwrite evidence
or protected/user R libraries. The attempt-1 helper failed before any analysis
because its new build parent was absent. Runtime binaries and private full
source are transferred separately; no installation occurs during saved readback.
''',encoding='utf-8',newline='\n')
intro='''**Current: [historical TwoSampleMR normal-JAR execution and remaining work](TWOSAMPLEMR_HISTORICAL_CHECKPOINT_2026-11-21.md).**
All49 normal-JAR cases match expected outcomes (45 successful, four original rank
errors); fresh tagged namespaces match121 complete returns and four errors/RNG.
Four real replays preserve parent files/exports, four public examples pass,16
missing/failed/source/lifetime controls reject result admission and seven Linux
R-free verifications pass. Direct replay/example comparisons bring the total to
**147 complete original returns**. The unchanged producer retains769 packaged
tests per Windows Java21/25 and Linux Java21. All44 worktrees,27 canonical files
and the preceding private runtimes remain preserved. **Next: original0.6.8 local
clumping, provider/dependency profiles, P157/P177 attribution, global registration
and guarded admission.** Counts stay143 scoped families,1,616 reviewed/1,104
pending, with zero new credit. Transfer ignored runtimes/JARs/full text/private
evidence separately with checksums. Both reports stay off Pages; the full goal
remains unfinished. Lower entries retain prior checkpoints.

'''
current=T/'docs/literature/HANDOVER_CURRENT.md';old=current.read_text(encoding='utf-8');assert old.startswith('# Current toolbox handover\n\n');current.write_text('# Current toolbox handover\n\n'+intro+old.split('\n\n',1)[1],encoding='utf-8',newline='\n')
inventory={};blobs={};private={}
def add(name,p):
    raw=p.read_bytes();assert name not in inventory and len(raw)<=32*1024*1024 and not p.is_symlink();wanted=pin(raw);inventory[name]=wanted;blobs[wanted['sha256']]=raw
for root,dirs,files in os.walk(B):
    for name in list(dirs):
        p=Path(root)/name
        if p.parent.name=='private-source-guard-control' and name in {'source','library'}:
            dirs.remove(name);private[p.relative_to(B).as_posix()]={x.relative_to(p).as_posix():pin(x.read_bytes()) for x in sorted(p.rglob('*')) if x.is_file()}
    for name in sorted(files):add('current/'+(Path(root)/name).relative_to(B).as_posix(),Path(root)/name)
for name,path in [('COMPLETED.json',P/'normal-gradle-3/COMPLETED.json'),('COMMAND.json',P/'normal-gradle-3/COMMAND.json'),('packaged-RESULT.json',P/'packaged-1/RESULT.json'),('JAR-COMPARISON.json',P/'packaged-1/JAR-COMPARISON.json')]:add('preceding-build/'+name,path)
for path in sorted((P/'normal-gradle-3/project-build/test-results/test').glob('TEST-*.xml')):add('preceding-build/test-results/'+path.name,path)
for host in ['windows21','windows25','linux21']:
    for path in sorted((P/'packaged-1'/host).iterdir()):
        if path.is_file():add('preceding-build/packaged/'+host+'/'+path.name,path)
for name in ['WORKTREES-BEFORE.json','CANONICAL-UNCHANGED.json']:shutil.copyfile(O/name,Q/name)
save(Q/'OMITTED.json',dict(private_negative_control_files=private,reason='Deliberately modified negative package source and compiled library retained privately; public mutation declaration/logs and SHA256s suffice to identify the refusal control',preceding_private_transfer='docs/literature/twosamplemr-historical-handover/PRIVATE-TRANSFER.json',producer_jar=str(P/'normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar'),runtime_binaries_primary_text_not_included=True,not_a_complete_machine_backup=True))
with zipfile.ZipFile(Q/'evidence.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for sha,raw in sorted(blobs.items()):
        info=zipfile.ZipInfo('blobs/'+sha,(2026,11,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw)
save(Q/'INVENTORY.json',inventory)
source_inputs=json.loads((B/'UNCHANGED-PRODUCER-SOURCES.json').read_bytes())['normal_source_inputs']
for name in source_inputs:assert pin((T/name).read_bytes())==source_inputs[name]
for pattern in ['examples/twosamplemr-historical/**/*','tools/literature/twosamplemr_historical/qualification/**/*']:
    for path in sorted(T.glob(pattern)):
        if path.is_file():source_inputs[path.relative_to(T).as_posix()]=pin(path.read_bytes())
for name in ['docs/literature/TWOSAMPLEMR_HISTORICAL_WORKFLOWS.md','docs/literature/TWOSAMPLEMR_HISTORICAL_CHECKPOINT_2026-11-21.md','tools/literature/verify_twosamplemr_historical_checkpoint.py']:source_inputs[name]=pin((T/name).read_bytes())
canonical=json.loads((O/'CANONICAL-UNCHANGED.json').read_bytes());files=['README.md','WORKTREES-BEFORE.json','CANONICAL-UNCHANGED.json','OMITTED.json','evidence.zip','INVENTORY.json']
profile=dict(status='QUALIFIED_HISTORICAL_PREPARED_WORKFLOWS_CATALOG_UNFINISHED',new_catalog_credit=0,whole_goal_complete=False,scientific_sources_unchanged=True,historical_clumping_qualified=False,source_inputs=source_inputs,canonical_unchanged=canonical,packet_files={name:pin((Q/name).read_bytes()) for name in files},logical_files=len(inventory),unique_blobs=len(blobs),worktrees_preserved=44,whole_tool_paper_parity_claimed=False)
save(Q/'PROFILE.json',profile)
allowed={}
for pattern in ['examples/twosamplemr-historical/**/*','tools/literature/twosamplemr_historical/qualification/**/*','docs/literature/twosamplemr-historical-checkpoint/*']:
    for p in T.glob(pattern):
        if p.is_file():allowed[p.relative_to(T).as_posix()]=pin(p.read_bytes())
for name in ['docs/literature/HANDOVER_CURRENT.md','docs/literature/TWOSAMPLEMR_HISTORICAL_WORKFLOWS.md','docs/literature/TWOSAMPLEMR_HISTORICAL_CHECKPOINT_2026-11-21.md','tools/literature/verify_twosamplemr_historical_checkpoint.py']:allowed[name]=pin((T/name).read_bytes())
save(O/'ALLOWED.json',allowed)
print(json.dumps(dict(public_files=len(allowed),logical_files=len(inventory),unique_blobs=len(blobs),unique_bytes=sum(map(len,blobs.values())),archive=pin((Q/'evidence.zip').read_bytes()),scientific_source_changes=False)),flush=True)
