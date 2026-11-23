from pathlib import Path
import datetime,hashlib,json,shutil,subprocess,zipfile
from handover_inventory_20261122_111 import save,pin
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';S=T/'build/squidpy-1'
O=R/'build/git-publish/20261122-squidpy-workflow-handover-136';D=T/'docs/literature/squidpy-workflow-handover-20261122'
assert not (D/'FINAL-STATUS.json').exists()
script="""import json,os,pathlib
found=[]
for p in pathlib.Path('/proc').iterdir():
 if not p.name.isdigit() or int(p.name)==os.getpid():continue
 try:
  args=(p/'cmdline').read_bytes().split(b'\\0'); command=b' '.join(args)
  if args and any(s in pathlib.Path(os.fsdecode(args[0])).name for s in ('java','python')) and any(s in command for s in (b'workflow-cases-',b'workflow-compile-5')):
   found.append(dict(pid=int(p.name),arguments=[os.fsdecode(a) for a in args if a],stat=(p/'stat').read_text()))
 except (FileNotFoundError,PermissionError,ProcessLookupError):pass
print(json.dumps(dict(matching_owned_validation_processes=found)))
"""
p=subprocess.run(['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--','/usr/bin/python3','-B','-c',script],capture_output=True)
(O/'final-wsl-process-check.stdout').write_bytes(p.stdout);(O/'final-wsl-process-check.stderr').write_bytes(p.stderr)
assert p.returncode==0,p.stderr
processes=json.loads(p.stdout);assert not processes['matching_owned_validation_processes'],processes
files={};objects={};jobs=[]
with zipfile.ZipFile(D/'evidence.zip') as z:base_objects={n[8:] for n in z.namelist()}
def capture(path):
 raw=path.read_bytes();i=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest());name='private-evidence/'+path.relative_to(S).as_posix()
 files[name]=i
 if i['sha256'] not in base_objects:objects[i['sha256']]=raw
for version in ('1.1.2','1.2.2','1.8.4'):
 directory=S/f'workflow-cases-{version}-1';progress=json.loads((directory/'PROGRESS.json').read_bytes())
 result=json.loads((directory/'RESULT.json').read_bytes()) if (directory/'RESULT.json').exists() else None
 assert len(progress)==(18 if version=='1.1.2' else 12)
 mismatches=[c for c in progress if c['exit_code']!=c['expected']]
 assert [c['case'] for c in mismatches]==([] if version=='1.1.2' else ['nanostring'])
 assert (result is not None)==(version=='1.1.2')
 started={x.name[:-len('.PROCESS.json')] for x in directory.glob('*.PROCESS.json')}
 assert started=={x['case'] for x in progress}
 for path in sorted(directory.rglob('*')):
  if path.is_file():capture(path)
 failure=None
 if mismatches:failure=json.loads((directory/'nanostring/FAILURE.json').read_bytes())
 jobs.append(dict(version=version,completed_cases=progress,result=result,unexpected_failures=mismatches,
                  failure=failure,terminal=True,success_receipt=result is not None))
save(D/'FINAL-BATCH-RECEIPTS.json',dict(captured_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),jobs=jobs,
    process_check=processes,all_three_launchers_ended=True,
    note='Follow-up after original snapshot. All42 cases are closed; two batches stopped at the Nanostring image audit failure. Later cases in those batches were never attempted. No job was stopped for handover.'))
save(D/'FINAL-EVIDENCE.json',dict(files=files,base_archive='evidence.zip',additional_archive='final-batch-evidence.zip',
    selection='All closed final three-edition batch cases, including both unexpected Nanostring failures and three actual1.1.2 replays. Shared content objects resolve against the original archive; original snapshot is unchanged.'))
with zipfile.ZipFile(D/'final-batch-evidence.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for digest,raw in sorted(objects.items()):
  info=zipfile.ZipInfo('objects/'+digest,(2026,11,22,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,raw,compresslevel=9)
save(D/'FINAL-STATUS.json',dict(status='TERMINAL_DEVELOPMENT_BATCH_FOLLOWUP_WITH_TWO_KNOWN_FAILURES',
    final_cases=42,matched_expected_outcome=40,unexpected_failures=2,
    complete_batches=['1.1.2'],stopped_failed_batches=['1.2.2','1.8.4'],
    unfinished_cases_in_failed_batches_not_run=True,active_owned_batch_processes=0,
    source_1_1_2_replays_completed=['knn-replay','moran-replay','ligrec-replay'],
    normal_project_build_run=False,full_adapter_qualified=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False,
    final_evidence_index=pin(D/'FINAL-EVIDENCE.json'),additional_evidence_archive=pin(D/'final-batch-evidence.zip')))
maintenance=D/'maintenance';maintenance.mkdir()
for path in (R/'build/prepare_squidpy_workflow_handover_20261122_136.py',R/'build/finalize_squidpy_handover_evidence_20261122_136.py',
             R/'build/recover_squidpy_relocation_receipt_20261122_136.py',R/'build/handover_inventory_20261122_111.py'):
 shutil.copyfile(path,maintenance/path.name)
for name in ('RELOCATION-PLAN.json','RELOCATION-ATTEMPT1-RECOVERY.json','RELOCATION-RESULT.json','relocate-drafts-attempt1.ps1','relocate-drafts.ps1'):
 shutil.copyfile(O/name,maintenance/name)
print(json.dumps(dict(final_cases=42,matched_expected=40,unexpected_failures=2,additional_logical_files=len(files),additional_objects=len(objects),additional_archive_bytes=(D/'final-batch-evidence.zip').stat().st_size)),flush=True)
