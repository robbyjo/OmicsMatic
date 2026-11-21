from pathlib import Path
import hashlib,importlib.util,json,os,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-7';B=T/'build/twosamplemr-historical-clump-1';O=R/'build/git-publish/20261121-historical-clump-89';O.mkdir(parents=True,exist_ok=False)
def save(p,v):
    with p.open('x',encoding='utf-8',newline='\n') as f:json.dump(v,f,indent=2);f.write('\n')
def pin(p):
    with p.open('rb') as f:return dict(bytes=p.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())
prior=json.loads((R/'build/mr-5/docs/literature/twosamplemr-historical-handover/PRIVATE-TRANSFER.json').read_bytes());rows={}
for label in ['historical-source','historical-runtime','author-fulltext','protected-predecessor-runtime']:
    row=prior[label];root=Path(row['root']);actual={p.relative_to(root).as_posix():pin(p) for p in sorted(root.rglob('*')) if p.is_file()};assert actual==row['files'],label;rows[label]=dict(root=str(root),files=len(actual),bytes=sum(x['bytes'] for x in actual.values()),unchanged=True);print('Protected',label,len(actual),flush=True)
save(B/'RUNTIME-PROTECTION.json',dict(status='PASS_ALL_PREDECESSOR_PRIVATE_ROOTS_EXACT',roots=rows,negative_integrity_control_lives_outside_protected_roots=True))
spec=importlib.util.spec_from_file_location('inv',O.parent/'20261115-all-main-3/inventory.py');inv=importlib.util.module_from_spec(spec);spec.loader.exec_module(inv)
previous=json.loads((O.parent/'20261121-historical-qualification-88/WORKTREES-EXPECTED.json').read_bytes());states=[]
for block in subprocess.check_output(['git','worktree','list','--porcelain'],cwd=R).decode().strip().split('\n\n'):
    root=Path(block.splitlines()[0][9:]);state=inv.snapshot(root);states.append(state)
    if root.resolve()!=T.resolve():assert state==next(x for x in previous if Path(x['root']).resolve()==root.resolve()),str(root)
assert len(states)==45 and len(previous)==44;save(O/'WORKTREES-BEFORE.json',states)
old=json.loads((O.parent/'20261121-historical-handover-87/CANONICAL-UNCHANGED.json').read_bytes())
for name,wanted in old.items():assert pin(T/name)==wanted,name
save(O/'CANONICAL-UNCHANGED.json',old)
source=B/'normal-gradle-1/COMMAND.json';inputs=json.loads(source.read_bytes())['source_inputs']
for name,wanted in inputs.items():assert pin(T/name)==wanted,name
save(B/'UNCHANGED-PRODUCER-SOURCES.json',dict(normal_source_inputs=inputs,canonical_files=27,source_owner_base='ce944536b71d38f65a5230ec1c5d630b26238502',normal_build_inputs_unchanged=True))
print('PASS protected runtimes, all45 worktrees,27 canonical files and exact producer sources',flush=True)
