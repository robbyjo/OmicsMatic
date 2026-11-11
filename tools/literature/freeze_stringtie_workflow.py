# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze actual typed workflows, complete portable checks, recovery, example and unapplied source roles."""
import hashlib
import json
from pathlib import Path,PurePosixPath
import stat
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/stringtie-runtime'
def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())
def rawpin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def portable(value):return {n:{k:v for k,v in row.items() if k!='mode'} for n,row in value.items()}

def main():
    target=ROOT/'docs/literature/stringtie-typed-workflow';assert not target.exists()
    matrix=BASE/'workflow-matrix-3';proof=read(matrix/'COMPLETED.json');recovery=BASE/'workflow-recovery-2';recovered=read(recovery/'COMPLETED.json');example=BASE/'workflow-example-1';ex=read(example/'COMPLETED.json')
    assert proof['status']=='passed-typed-StringTie-short-read-assembly-estimation-merge-and-portable-verification' and not(matrix/'INCOMPLETE.json').exists() and not(matrix/'FAILURE.json').exists()
    assert len(proof['new_classes'])==12 and proof['unchanged_parent_entries']==19607
    assert len(proof['platform_validation'])==3 and all(row['passed']==1353 and row['skipped']==0 for row in proof['platform_validation'])
    assert len(proof['portable_validation'])==3 and sum(row['complete_captured_verifications'] for row in proof['portable_validation'])==72
    assert proof['standalone_public_fitting_workflow_available'] and not any(proof[k] for k in ['root_dispatch_available','implementation_credit','released','pushed','deployed','whole_goal_complete'])
    assert proof['native']['complete_fit_bundles']==proof['native']['independent_original_comparisons']==24
    assert proof['native']['fresh_native_runtime_health_fits']==1 and len(proof['native']['byte_identical_candidate_reused_fit_labels'])==23
    assert all(pin(ROOT/n)==p for n,p in proof['source_pins'].items()) and all(pin(ROOT/n)==p for n,p in proof['dependency_pins'].items())
    artifact=ROOT/proof['artifact']['path'];assert pin(artifact)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    with zipfile.ZipFile(ROOT/proof['parent_artifact']['path']) as old,zipfile.ZipFile(artifact) as new:
        assert new.testzip() is None and len(new.namelist())==len(set(new.namelist())) and len(old.namelist())==19607
        assert set(new.namelist())-set(old.namelist())==set(proof['new_classes']) and all(new.read(n)==old.read(n) for n in old.namelist())
        assert all(rawpin(new.read(n))==p for n,p in proof['new_classes'].items())
    # Bind the independent comparison files back to the prior frozen original
    # control archive, rather than trusting mutable cache copies as new oracles.
    original_fixture=ROOT/'src/test/resources/org/jlinalg/sequence/stringtie-gtf';original=read(original_fixture/'CAPTURE.json')
    with zipfile.ZipFile(original_fixture/'original-gtf-controls.zip') as zipped:
        assert zipped.testzip() is None
        for n,p in original['archive_files'].items():
            assert rawpin(zipped.read(n))==p
            if n=='GTF-CASES.json':assert json.loads(zipped.read(n))=={k:v for k,v in original.items() if k not in ['archive','archive_files']}
            elif n=='official-intake.json':assert pin(BASE/'source-intake-1/COMPLETED.json')==p
            elif n.startswith('licenses/'):assert pin(ROOT/'src/main/resources/org/jlinalg/omics/stringtie-runtime-setup'/n)==p
            else:assert pin(BASE/'original-controls-1'/n)==p
    for bundle in (matrix/'bundles').iterdir():
        complete=read(bundle/'COMPLETED.json');manifest=read(bundle/'manifest.json');assert complete['manifest']==pin(bundle/'manifest.json') and not(bundle/'INCOMPLETE.json').exists() and not(bundle/'FAILURE.json').exists()
        actual={'.':dict(kind='directory')}
        for file in bundle.rglob('*'):
            n=file.relative_to(bundle).as_posix();actual[n]=dict(kind='directory') if file.is_dir() else dict(kind='file',**pin(file))
        actual.pop('manifest.json');actual.pop('COMPLETED.json');assert actual==portable(manifest['files'])
        proc=read(bundle/'fit/process.json');assert proc['status']=='completed-process-only' and proc['exit_code']==0 and not proc['owned_live_after_cleanup']
    assert len(list((matrix/'bundles').iterdir()))==24
    for file in matrix.glob('*-portable-*.process.json'):assert read(file)['exit_code']==0
    assert len(list(matrix.glob('*-portable-*.process.json')))==72
    assert recovered['status']=='passed-24-public-rehashed-invalid-bundle-rejections-and-actual-owned-user-fit-cancellation' and recovered['actual_cli_rejections']==24 and recovered['invalid_cases']==8 and recovered['actual_user_fit_cancellation'] and recovered['cancelled_process_cleanup_complete']
    assert recovered['artifact']==pin(artifact) and all(pin(ROOT/n)==p for n,p in recovered['sources'].items())
    cancelled=recovery/'retained-cancelled-fit';assert (cancelled/'INCOMPLETE.json').exists() and not(cancelled/'COMPLETED.json').exists()
    assert read(cancelled/'FAILURE.json')['status']=='FAIL2_NO_RESULT_ADMITTED' and not read(cancelled/'fit/process.json')['owned_live_after_cleanup']
    prior_recovery=ROOT/recovered['retained_prior_attempt'];assert len(recovered['retained_passed_rejections'])==24
    for case in recovered['retained_passed_rejections']:
        assert pin(prior_recovery/(case['label']+'.process.json'))==case['process'] and pin(prior_recovery/(case['label']+'.stderr'))==case['stderr']
        assert read(prior_recovery/(case['label']+'.process.json'))['exit_code']==2
    assert ex['status']=='passed-documented-standalone-kit-export-example-preparation-three-user-fits-and-copied-verification' and ex['actual_example_fits']==3 and ex['independent_fresh_original_merge_comparison']==1 and ex['copied_windows_verifications']==3
    assert ex['artifact']==pin(artifact) and all(pin(ROOT/n)==p for n,p in ex['sources'].items())
    for label in ['assembly','estimation','merged']:assert read(example/(label+'-windows-verify.process.json'))['exit_code']==0 and read(example/(label+'.process.json'))['exit_code']==0
    for name in ['workflow-matrix-1','workflow-matrix-2','workflow-recovery-1']:assert (BASE/name/'FAILURE.json').exists() and (BASE/name/'INCOMPLETE.json').exists()
    assert read(BASE/'workflow-compile-1/FAILURE.json')['status']=='initial-development-compilation-failed-no-tests-run'
    empty=read(matrix/'bundles/3.0.3-empty-estimate/metadata/OUTPUT-AUDIT.json')['TPM_denominator'];assert empty['resolution']=='NO_POSITIVE_NUMERATOR' and empty['positive_numerators']==empty['positive_outputs']==0
    # Retain native partials from the two host-change failures. No runtime home
    # or original input is modified, and no worker is restarted by this copy.
    destinations=[('workflow-matrix-1','retained-native-host-change','/var/tmp/omicsmatic-stringtie-workflow-20261103-1/3.0.3-assembly'),('workflow-recovery-1','retained-native-host-change','/var/tmp/omicsmatic-stringtie-recovery-20261103-1/cancelled-user-fit')]
    script='import shutil\nfrom pathlib import Path\n'
    for folder,name,source in destinations:
        destination=BASE/folder/name;assert not destination.exists();linux='/mnt/d/projects/JLinAlg/'+destination.relative_to(ROOT).as_posix()
        script+=f"source=Path({source!r});target=Path({linux!r});assert source.is_dir() and not target.exists();shutil.copytree(source,target)\n"
    process=subprocess.run(['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B','-c',script],cwd=ROOT,capture_output=True,timeout=120,check=False);assert process.returncode==0,process.stderr
    for folder,name,_ in destinations:assert 'Host member changed during its complete census: /etc/resolv.conf' in read(BASE/folder/name/'FAILURE.json')['error']
    draft=read(BASE/'role-draft-1/VALIDATION.json');assert draft['reviewed_papers']==8 and draft['named_paragraphs']==13 and draft['supporting_pointers']==5 and draft['catalog_unchanged'] and not draft['implementation_promoted']
    assert pin(ROOT/draft['draft']['path'])=={k:draft['draft'][k] for k in ['bytes','sha256']}
    data=ROOT/'docs/literature/omics-2023-2026';sums=dict(line.split('  ',1)[::-1] for line in (data/'SHA256SUMS').read_text().splitlines())
    assert len(sums)==396 and all(pin(data/n)['sha256']==p for n,p in sums.items()) and next(t for t in read(data/'tool-inventory.json') if t['id']=='stringtie')['implementation_status']=='missing'
    assert len(read(ROOT/'tools/literature/reviewed-uses.json')['reviews'])==819
    sources=dict(proof['source_pins']);sources.update(recovered['sources']);sources.update(ex['sources'])
    for n in ['docs/literature/STRINGTIE_WORKFLOW.md','tools/literature/freeze_stringtie_workflow.py','tools/literature/prepare_stringtie_use_reviews.py','tools/literature/stringtie-use-reviews.json','tools/literature/collect_omics_review.py','tools/literature/catalog_fixed_cohort.py','tools/literature/catalog_source_integrity.py']:sources[n]=pin(ROOT/n)
    entries={}
    def add(path,name):
        p=PurePosixPath(name);assert path.is_file() and not path.is_symlink() and name not in entries and not p.is_absolute() and '..' not in p.parts and '\\' not in name;entries[name]=path
    def tree(folder,prefix):
        for path in sorted(folder.rglob('*')):
            assert not path.is_symlink()
            if path.is_file() and path.suffix not in ['.jar','.part'] and path.name!='portable-bundles.zip' and 'javadoc' not in path.relative_to(folder).parts:add(path,prefix+'/'+path.relative_to(folder).as_posix())
    for n in sources:add(ROOT/n,'source/'+n)
    for name in ['workflow-compile-1','workflow-matrix-1','workflow-matrix-2','workflow-matrix-3','workflow-recovery-1','workflow-recovery-2','workflow-example-1','workflow-freeze-attempt-1','role-draft-1']:tree(BASE/name,'evidence/'+name)
    parent=ROOT/'docs/literature/stringtie-runtime-development'
    for n in ['VALIDATION.json','SHA256SUMS']:add(parent/n,'prior-runtime-checkpoint/'+n)
    for n in ['summary.json','tool-inventory.json','SHA256SUMS']:add(data/n,'unchanged-catalog/'+n)
    target.mkdir();archive=target/'controls.zip';members={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as zipped:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16;zipped.writestr(info,raw);members[name]=rawpin(raw)
    with zipfile.ZipFile(archive) as zipped:
        assert zipped.testzip() is None and len(zipped.namelist())==len(set(zipped.namelist()))==len(members) and all(rawpin(zipped.read(n))==p for n,p in members.items())
    assert archive.stat().st_size<95000000 and all(pin(ROOT/n)==p for n,p in sources.items())
    result=dict(proof,schema_version=1,source_pins=sources,archive=dict(path=archive.name,**pin(archive),entries=len(members)),archive_files=members,parent_validation=pin(parent/'VALIDATION.json'),parent_archive=pin(parent/'controls.zip'),
        recovery_validation=pin(recovery/'COMPLETED.json'),documented_example_validation=pin(example/'COMPLETED.json'),recovery=recovered,documented_example=ex,unapplied_role_reviews=draft,
        complete_user_fit_cases=27,independent_retained_original_comparisons=24,independent_fresh_original_merge_comparisons=1,complete_main_portable_verifications=72,example_copied_windows_verifications=3,
        retained_environment_warning=dict(path='evidence/workflow-example-1/native.stderr',warning='WSL systemd-user-session startup warning',native_launcher_and_all_fits_passed=True,required_action='No systemd user service is required by this workflow. Read the retained stderr. If another WSL service depends on that session, inspect its journalctl diagnostics separately; StringTie failures still follow their named SKIP3/FAIL2 stage.'),
        reused_successful_fits_not_claimed_as_new=True,all_failed_attempts_retained=True,original_StringTie_executable_bundled=False,scientific_parent_sources_unchanged=True,
        unchanged_catalog=dict(new_scoped_integrations=67,including_existing=70,missing=260,reviewed_uses=819,checksummed_files=396,stringtie_status='missing'),
        required_action='Use STRINGTIE_WORKFLOW.md and examples/stringtie/README.md with the exact development JAR. Standalone typed assembly/estimation/merge and complete copied verification are available. Install/download only where permitted via the separate exported kit. SKIP3 means skip dependent analysis until the named requirement is permitted and available; FAIL2 means no result, retained diagnostics and correction into NEW paths. Read all warnings, input/output audits and original process logs. Root dispatch, source-role application, catalog promotion and UI/provider checks remain; this is not whole-paper reproduction or whole-goal completion. Cell Ranger remains excluded.')
    (target/'VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n');(target/'SHA256SUMS').write_text(''.join(pin(target/n)['sha256']+'  '+n+'\n' for n in ['VALIDATION.json','controls.zip']),encoding='ascii',newline='\n');(target/'.gitattributes').write_text('controls.zip -text\nVALIDATION.json -text\nSHA256SUMS -text\n',encoding='ascii',newline='\n')
    print(json.dumps(dict(status=result['status'],archive=result['archive'],source_pins=len(sources),complete_user_fit_cases=27,copied_complete_verifications=75,root_dispatch=False,implementation_credit=False)))

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete workflow freezing checks')
    main()
