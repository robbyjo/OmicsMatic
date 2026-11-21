from pathlib import Path
import ast,hashlib,json,os,shutil,zipfile
R=Path('D:/projects/JLinAlg');T=R/'build/mr-8';B=T/'build/twosamplemr-global-1';O=R/'build/git-publish/20261121-twosamplemr-global-90';Q=T/'docs/literature/twosamplemr-global-checkpoint';D=T/'tools/literature/twosamplemr_global/validation'
def pin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
assert (O/'WORKTREES-BEFORE.json').exists() and not (Q/'PROFILE.json').exists();D.mkdir(exist_ok=False)
for p in sorted(set([*(R/'build').glob('*twosamplemr_global*20261121.py'),Path(__file__)])):
    ast.parse(p.read_text(encoding='utf-8'));shutil.copyfile(p,D/p.name)
(D/'README.md').write_text('''# Global qualification helpers

These retained helpers run packaged tests, global examples/replays, refusals,
neighbor help and preservation checks. Inspect absolute Windows paths and
fail-on-exist outputs before adapting to NEW bounded directories. The normal
build helper and both raw build attempts are in the evidence packet. The first
failed before tests because the new wrapper lacked its UTF8 output helper;
normal build2 is final. Do not modify frozen scientific workers/verifiers,
older root-help fixtures, protected/user R libraries or retained worktrees.
Saved readers run no scientific analyses and install nothing.
''',encoding='utf-8',newline='\n')
intro='''**Current: [global TwoSampleMR command and catalog admission next](TWOSAMPLEMR_GLOBAL_CHECKPOINT_2026-11-21.md).**
The source-built JAR exposes **twosamplemr-tools** for all seven exact profiles.
All858 tests pass per Windows Java21/25 and Linux Java21; seven global examples,
seven real replays and seven Linux R-free verifications pass. Fresh original
calls match50 complete returns/RNG. Ten CLI refusals admit no results; six
neighbor help outputs remain exact. Only the reviewed root dispatcher changes
among22,375 preceding JAR payloads; all original scientific classes/resources
remain exact. Fresh all250/14-role audits and16 source refusals pass per host;
the unchanged preceding PEER catalog owner verifies independently. All46
worktrees,27 canonical files and private runtimes remain preserved. **Next:
guarded TwoSampleMR catalog admission**, then remaining high-impact scopes and
individual reviews. The linked plan's proposed counts are unapplied: current
totals stay143 scoped families,1,616 reviewed/1,104 pending with zero new credit.
Both reports stay off Pages; transfer ignored runtimes/JARs/full text/private
evidence separately with checksums. The full goal is unfinished; lower entries
retain prior checkpoints.

'''
current=T/'docs/literature/HANDOVER_CURRENT.md';old=current.read_text(encoding='utf-8');assert old.startswith('# Current toolbox handover\n\n');current.write_text('# Current toolbox handover\n\n'+intro+old.split('\n\n',1)[1],encoding='utf-8',newline='\n')
inventory={};blobs={};omitted={}
def add(name,p):
    raw=p.read_bytes();assert name not in inventory and len(raw)<=32*1024*1024 and not p.is_symlink();wanted=pin(raw);inventory[name]=wanted;blobs[wanted['sha256']]=raw
for root,dirs,files in os.walk(B):
    for name in list(dirs):
        if name in {'project-build','project-cache','classes','test-classes'}:dirs.remove(name);omitted['current/'+(Path(root)/name).relative_to(B).as_posix()]=dict(reason='Generated build/cache/classes; completed raw normal XML separately retained')
    for name in sorted(files):add('current/'+(Path(root)/name).relative_to(B).as_posix(),Path(root)/name)
for p in (B/'normal-gradle-2/project-build/test-results/test').glob('TEST-*.xml'):add('current/normal-gradle-2/test-results/'+p.name,p)
for name in ['WORKTREES-BEFORE.json','CANONICAL-UNCHANGED.json']:shutil.copyfile(O/name,Q/name)
save(Q/'OMITTED.json',dict(generated=omitted,private_runtime_and_text_transfer='docs/literature/twosamplemr-historical-handover/PRIVATE-TRANSFER.json',private_author_code_transfer='docs/literature/twosamplemr-historical-clump-checkpoint/OMITTED.json',producer_jar=dict(path=str(B/'normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar'),**pin((B/'normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar').read_bytes())),not_a_complete_machine_backup=True))
with zipfile.ZipFile(Q/'checks.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for sha,raw in sorted(blobs.items()):
        info=zipfile.ZipInfo('blobs/'+sha,(2026,11,21,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw)
save(Q/'INVENTORY.json',inventory)
sources=json.loads((B/'normal-gradle-2/COMMAND.json').read_bytes())['source_inputs']
for name,wanted in sources.items():assert pin((T/name).read_bytes())==wanted,name
for pattern in ['tools/literature/twosamplemr_global/**/*','examples/twosamplemr-global/**/*']:
    for p in T.glob(pattern):
        if p.is_file():sources[p.relative_to(T).as_posix()]=pin(p.read_bytes())
names=['tools/literature/twosamplemr_dispatch_transition.py','tools/literature/twosamplemr_global_context.py','tools/literature/verify_twosamplemr_global_checkpoint.py','docs/literature/TWOSAMPLEMR_TOOLS.md','docs/literature/TWOSAMPLEMR_GLOBAL_CHECKPOINT_2026-11-21.md','docs/literature/TWOSAMPLEMR_CATALOG_PLAN.md']
for name in names:sources[name]=pin((T/name).read_bytes())
canonical=json.loads((O/'CANONICAL-UNCHANGED.json').read_bytes());files=['README.md','WORKTREES-BEFORE.json','CANONICAL-UNCHANGED.json','OMITTED.json','checks.zip','INVENTORY.json'];profile=dict(status='PASS_GLOBAL_TWOSAMPLEMR858_CATALOG_UNFINISHED',new_catalog_credit=0,whole_goal_complete=False,global_command_registered=True,source_inputs=sources,canonical_unchanged=canonical,packet_files={n:pin((Q/n).read_bytes()) for n in files},logical_files=len(inventory),unique_blobs=len(blobs),worktrees_preserved=46)
save(Q/'PROFILE.json',profile)
allowed={}
for pattern in ['tools/literature/twosamplemr_global/**/*','examples/twosamplemr-global/**/*','docs/literature/twosamplemr-global-checkpoint/*']:
    for p in T.glob(pattern):
        if p.is_file():allowed[p.relative_to(T).as_posix()]=pin(p.read_bytes())
for name in [*names,'src/main/java/org/jlinalg/cli/JLinAlgCli.java','src/main/java/org/jlinalg/cli/TwoSampleMrToolsCli.java','src/test/java/org/jlinalg/cli/TwoSampleMrGlobalCliTest.java','src/test/resources/org/jlinalg/cli/twosamplemr-global-preceding-root-help.txt','docs/literature/HANDOVER_CURRENT.md']:allowed[name]=pin((T/name).read_bytes())
save(O/'ALLOWED.json',allowed);print(json.dumps(dict(public_files=len(allowed),logical_files=len(inventory),unique_blobs=len(blobs),archive=pin((Q/'checks.zip').read_bytes()))),flush=True)
