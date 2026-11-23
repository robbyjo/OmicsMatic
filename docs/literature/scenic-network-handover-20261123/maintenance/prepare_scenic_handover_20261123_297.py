"""Preserve reviewed synthetic SCENIC development and all worktree owners for handover."""
from pathlib import Path
import hashlib, json, subprocess, sys, zipfile
from handover_inventory_20261122_111 import snapshot, save, pin, git

R=Path('D:/projects/JLinAlg'); T=R/'build/fu-1'
O=R/'build/git-publish/20261123-handover-297'
B=T/'build/scenic-2'; P=T/'docs/literature/scenic-network-handover-20261123'
O.mkdir(); P.mkdir()
before=snapshot(); save(O/'WORKTREES-BEFORE.json',before)
previous=json.loads((R/'build/git-publish/20261123-scenic-network-294/WORKTREES-BEFORE.json').read_bytes())
assert len(before)==len(previous)==19
for old in previous:
    now=next(row for row in before if row['worktree']==old['worktree'])
    if Path(old['worktree'])==T:
        assert now['HEAD']==old['HEAD'] and now['status']==old['status']==[]
    else: assert now==old,old['worktree']
indexes={}
for owner in before:
    path=Path(git('rev-parse','--path-format=absolute','--git-path','index',cwd=Path(owner['worktree'])).decode().strip())
    indexes[owner['worktree']]=dict(path=str(path),identity=pin(path))
save(O/'INDEXES-BEFORE.json',indexes)
oldidx=json.loads((R/'build/git-publish/20261123-scenic-network-294/INDEXES-BEFORE.json').read_bytes())
assert indexes==oldidx
summary=json.loads((T/'docs/literature/omics-2023-2026/summary.json').read_bytes())
assert summary['papers']==250 and summary['paper_tool_observations']==2722
assert summary['integration_family_counts']['scoped']==154
assert summary['implementation_status_counts']['missing']==258
entries={}
def add(name,path):
    assert name not in entries
    entries[name]=path.read_bytes()
for run in ('original-smoke-1','original-smoke-2'):
    for f in sorted((B/run).iterdir()):
        if f.is_file(): add(run+'/'+f.name,f)
for name in ('start_scenic_network_20261123_294.py','smoke_original_scenic_20261123_295.py','smoke_original_scenic_20261123_296.py','handover_inventory_20261122_111.py'):
    add('helpers/'+name,R/'build'/name)
add('SOURCE-ROLES-DRAFT.json',T/'build/scenic-1/SOURCE-ROLES-DRAFT.json')
manifest={name:dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()) for name,raw in sorted(entries.items())}
with zipfile.ZipFile(P/'evidence.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for name,raw in sorted(entries.items()):
        info=zipfile.ZipInfo(name,date_time=(2026,11,23,0,0,0)); info.compress_type=zipfile.ZIP_DEFLATED; info.external_attr=0o100644<<16
        z.writestr(info,raw)
save(P/'FILES.json',manifest)
complete=json.loads(entries['original-smoke-2/COMPLETED.json'])
assert complete['adjacency_rows']==412 and complete['activity_shape']==[96,4]
assert [r['step'] for r in complete['steps']]==['grn','ctx','aucell']
assert all(r['exit_code']==0 for r in complete['steps'])
assert json.loads(entries['original-smoke-1/FAILURE.json'])['analysis_admitted'] is False
status=dict(schema=1,status='SCENIC_NETWORK_DEVELOPMENT_HANDOVER_NOT_QUALIFICATION',
    base_commit=git('rev-parse','HEAD',cwd=T).decode().strip(),
    branch=git('branch','--show-current',cwd=T).decode().strip(),
    archive=pin(P/'evidence.zip'),manifest=pin(P/'FILES.json'),files=len(entries),
    catalog=dict(papers=250,window_start=summary['window_start'],window_end=summary['window_end'],
        journals=summary['journals'],scoped_families=154,native_families=29,upstream_families=125,
        including_existing_adapters=157,reviewed_uses=1697,pending_uses=1025,
        positive_observations=2722,named_tools=446,missing=258,method_overlap=19),
    latest_original_probe=complete,
    native_component_qualified=True,full_scenic_workflow_qualified=False,
    catalog_admitted=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False,
    public_archive_contains='Own helper source, wholly synthetic inputs/results and raw probe consoles; draft source-role summaries and locators. No original full paper XML, author data, installed binaries or whole upstream source archives.',
    local_retention=dict(worktrees=19,other_owners_unchanged_since_network_start=18,
        all_indexes_unchanged_at_handover_start=True,
        private_evidence_root=str(B),
        publication_root=str(O),
        required_WSL_distro='JLinAlg-Omics-SAIGE-20261026',
        working_runtime='/var/tmp/omicsmatic-scenic-runtime-20261123-274',
        machine_transfer_complete=False),
    warnings=['SKIP3: missing or forbidden exact prerequisite skips the dependent analysis; supply the named permitted runtime/input or retain the skip.',
        'FAIL2: failed/partial/undefined output is not an admitted complete result; retain evidence, resolve the named cause and retry into a new directory.',
        'First original probe failed before fitting because direct CLI-file execution shadowed the pyscenic package; second uses the original module entry point without source changes.',
        'Synthetic original return is not a qualified project adapter, whole-paper reproduction or biological validation.',
        'GitHub does not contain private evidence, exact executable JARs or installed WSL runtimes. Transfer those privately before machine retirement.',
        'Nine historical snapshot branches and the release-v0.3.1 branch remain restricted after an earlier automatic approval review rejection for possible sensitive content.',
        '33 existing website citation-panel errors remain; both unfinished literature reports remain withheld from Pages.'])
save(P/'STATUS.json',status)
save(O/'PREPARATION.json',dict(public_files=len(entries),archive=pin(P/'evidence.zip'),
    owners_unchanged=True,indexes_unchanged=True,new_scientific_source=False))
print(json.dumps(dict(public_files=len(entries),archive=pin(P/'evidence.zip'),
    owners=len(before),dirty_owners=sum(bool(x['status']) for x in before),
    previous_index_and_other_owner_state_preserved=True),indent=2))
