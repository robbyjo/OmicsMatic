"""Save compact public readback receipts without private worktree payloads."""
from pathlib import Path
import hashlib, json, shutil, subprocess
from handover_inventory_20261122_111 import snapshot,save,pin,git
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261123-handover-297';P=T/'docs/literature/scenic-network-handover-20261123'
free_before=shutil.disk_usage(R).free
for name in ('prepare_scenic_handover_20261123_297.py','check_scenic_handover_20261123_298.py','check_scenic_handover_20261123_299.py','finalize_scenic_handover_20261123_300.py'):
    dest=P/'maintenance'/name;dest.parent.mkdir(exist_ok=True);dest.write_bytes((R/'build'/name).read_bytes())
checks={};receipts={}
for host,folder in [('windows','checks-windows'),('linux-initial-failed','checks-linux'),('linux','checks-linux-2')]:
    source=O/folder;target=P/'checks'/host;target.mkdir(parents=True)
    for f in source.iterdir():
        if f.is_file():
            (target/f.name).write_bytes(f.read_bytes());receipts[(target/f.name).relative_to(P).as_posix()]=pin(f)
    rows=json.loads((source/'RESULTS.json').read_bytes())
    checks[host]=dict(results=rows,completed=(source/'COMPLETED.json').exists())
assert checks['windows']['completed'] and checks['linux']['completed'] and not checks['linux-initial-failed']['completed']
for host in ('windows','linux'):
    assert len(checks[host]['results'])==5 and all(r['exit_code']==r['expected_exit_code'] for r in checks[host]['results'])
assert checks['linux-initial-failed']['results'][-1]['exit_code']==1
jar=T/'build/scenic-1/normal-build-2/project-build/cli/omicsmatic-0.4.0.jar'
assert pin(jar)==dict(bytes=198987629,sha256='925ef8573b06bd0547a9c1aaa38b339e41720144ee1b4dbc1f9e8089b6c2f585')
save(P/'CHECKS.json',dict(valid_saved_evidence_readers=6,expected_refusals=4,results=checks,
    receipt_files=receipts,first_linux_git_path_failure_retained=True,
    correction='Explicit Linux GIT_DIR/GIT_WORK_TREE environment for the Windows-created worktree; no metadata/source rewrites.',
    exact_native_producer=pin(jar),scientific_fits_reexecuted_by_handover_checks=False,
    previous_normal_test_suite_reexecuted=False,new_release=False))
owners=snapshot();save(O/'WORKTREES-BEFORE-COMMIT.json',owners)
initial=json.loads((O/'WORKTREES-BEFORE.json').read_bytes());assert len(owners)==len(initial)==19
for old in initial:
    now=next(r for r in owners if r['worktree']==old['worktree'])
    if Path(old['worktree'])!=T:assert old==now,old['worktree']
save(P/'PRESERVATION.json',dict(worktrees_retained=19,other_owners_unchanged=18,
    checked='HEAD, branch/detached state, exact status entries and dirty file identities; all indexes unchanged at handover start.',
    private_full_inventory=str(O/'WORKTREES-BEFORE.json'),
    full_dirty_payloads_exported=False,private_runtimes_exported=False,
    restricted_historical_branches_exported=False))
save(P/'CLEANUP.json',dict(removed_paths=[],removed_files=0,removed_bytes=0,
    reason='Documentation/evidence handover added no compiler outputs, duplicate runtime installs or disposable Git checkouts. All probe inputs, original/failed outputs and negative-control evidence remain required for continuation.',
    previous_feature_cleanup='../pyscenic-aucell-component-checkpoint/CLEANUP.json',
    all_19_worktrees_and_private_runtime_roots_retained=True,
    available_before=free_before,available_after=shutil.disk_usage(R).free,
    recovered_space_attributable_to_this_handover=0))
refs=git('for-each-ref','--no-merged=refs/remotes/origin/main','--format=%(refname) %(objectname)','refs/heads').decode().splitlines()
save(O/'UNPUBLISHED-REFS-BEFORE.json',dict(main=git('rev-parse','refs/remotes/origin/main').decode().strip(),refs=refs))
print(json.dumps(dict(public_receipt_files=len(receipts),valid_readers=6,expected_refusals=4,retained_first_linux_failure=True,unmerged_local_refs=len(refs),other_owners_preserved=18),indent=2))
