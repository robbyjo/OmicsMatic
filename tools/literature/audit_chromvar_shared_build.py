# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record a shared build change without extending the native chromVAR test claim."""
import json,zipfile
from pathlib import Path
from validate_mashr_native import pin
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/signac-runtime'
CHANGED={
 'org/jlinalg/cli/'+n+'.class' for n in [
 'StudyBinaryRareVariants$HashMapKey','StudyBinaryRareVariants','StudyCli$Options',
 'StudyCli','StudyGeneticPairCache','StudyImpactAssessment','StudyLiteraturePriority',
 'StudyVariantBatchCache']}
CHANGED|={'org/jlinalg/study/'+n+'.class' for n in [
 'StudyEvidenceArchive','StudyEvidenceIndex','StudyExecutionEngine','StudyProject']}
ADDED={'org/jlinalg/study/StudyCleanup'+n+'.class' for n in ['', '$Catalog','$Replacement','$Result']}

def audit():
    build=json.loads((BASE/'chromvar-workflow-build-1/BUILD.json').read_bytes())
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';tested=ROOT/build['artifact']['path']
    initial=build['canonical_executable_unchanged']
    assert initial==dict(bytes=162107844,sha256='bd1a3b2448d7016ae4ded3f30a6c5ae92e05a0078ae688f4b93d046cc355eeb2')
    assert pin(tested)=={k:build['artifact'][k] for k in ['bytes','sha256']}
    observed=pin(canonical)
    assert observed==dict(bytes=164898617,sha256='545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2')
    with zipfile.ZipFile(tested) as a,zipfile.ZipFile(canonical) as b:
        assert a.testzip() is None and b.testzip() is None
        assert len(a.namelist())==len(set(a.namelist())) and len(b.namelist())==len(set(b.namelist()))
        aa={n for n in a.namelist() if not n.endswith('/')};bb={n for n in b.namelist() if not n.endswith('/')}
        changed={n for n in aa&bb if a.read(n)!=b.read(n)}
        assert aa-bb==set() and bb-aa==ADDED and changed==CHANGED
        chromvar={n for n in aa if 'ChromVar' in n or '/chromvar-deviations/' in n}
        assert chromvar and all(a.read(n)==b.read(n) for n in chromvar)
        directories=sorted(set(b.namelist())-set(a.namelist())-ADDED)
        assert all(n.endswith('/') for n in directories)
    assert pin(canonical)==observed
    return dict(schema_version=1,status='passed-exact-shared-build-content-audit',
        canonical_executable_at_validation=initial,canonical_executable_at_freeze=observed,
        tested_artifact=build['artifact'],changed_entries=sorted(changed),added_entries=sorted(ADDED),
        added_directory_entries=directories,removed_entries=[],identical_file_entries=len(aa)-len(changed),
        chromvar_identical_entries=sorted(chromvar),all_chromvar_entries_byte_identical=True,
        canonical_changed_since_validation=True,canonical_modified_by_this_audit=False,
        complete_current_canonical_test_claim=False,
        scope='Tests remain bound to the pinned development JAR. The shared JAR has twelve changed study classes, four added study-cleanup classes and directory entries; all other file entries are identical. No attribution of the other build and no whole-JAR test claim are made.')

def main():
    out=BASE/'chromvar-freeze-attempt-1/canonical-observation.json'
    assert not out.exists()
    result=audit();out.write_text(json.dumps(result,indent=2)+'\n',encoding='utf8',newline='\n')
    print(json.dumps(dict(status=result['status'],changed_entries=len(result['changed_entries']),added_entries=len(result['added_entries']),identical_file_entries=result['identical_file_entries'],chromvar_entries=len(result['chromvar_identical_entries']))),flush=True)
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable shared build audit checks')
    main()
