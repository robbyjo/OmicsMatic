from pathlib import Path
import hashlib,json,shutil
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';O=R/'build/git-publish/20261122-squidpy-workflow-handover-136'
plan=json.loads((O/'RELOCATION-PLAN.json').read_bytes())
script=O/'relocate-drafts.ps1'
shutil.copyfile(script,O/'relocate-drafts-attempt1.ps1')
recovered=[]
for origin,entry in plan['files'].items():
    source=Path(entry['destination']);raw=source.read_bytes()
    assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())=={k:entry[k] for k in ('bytes','sha256')}
    target=Path(origin);assert target.is_relative_to(T) and not target.exists()
    target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw);assert target.read_bytes()==raw
    recovered.append(dict(origin=origin,recovery=str(source),bytes=len(raw),sha256=entry['sha256']))
(O/'RELOCATION-ATTEMPT1-RECOVERY.json').write_text(json.dumps(dict(
    status='POST_REMOVAL_RECEIPT_FAILURE_RECOVERED_EXACTLY_BEFORE_CORRECTED_RELOCATION',
    failure='PowerShell5.1 Measure-Object -Property bytes does not read Hashtable keys; receipt construction failed after all14 verified redundant draft copies were removed.',
    original_error='The property "bytes" cannot be found in the input for any objects.',
    first_attempt_free_space_change_unknown=True,raw_and_failed_scientific_evidence_affected=False,
    exact_saved_copies_restored=recovered),indent=2)+'\n',encoding='utf-8')
text=script.read_text(encoding='utf-8').replace("$before=[IO.DriveInfo]", "$logicalBytes=0L\nforeach($entry in $checked) { $logicalBytes+=$entry.bytes }\n$before=[IO.DriveInfo]")
text=text.replace("($checked | Measure-Object -Property bytes -Sum).Sum",'$logicalBytes')
assert 'Measure-Object' not in text
script.write_text(text,encoding='utf-8')
print(json.dumps(dict(restored_exact_files=len(recovered),safe_to_run_corrected_guarded_relocation=True)),flush=True)
