#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate retained preparation evidence and live source bindings; no analysis execution."""
import hashlib
import json
from pathlib import Path,PurePosixPath
import stat
import sys
import zipfile
sys.path.insert(0,str(Path(__file__).resolve().parent))
from mashr_original_runtime import pin

ROOT=Path(__file__).resolve().parents[2]
PROOF=ROOT/'docs/literature/mashr-original-preparation/VALIDATION.json'


def parse(raw):
    def unique(pairs):
        value={}
        for key,item in pairs:
            assert key not in value,'Duplicate checkpoint key';value[key]=item
        return value
    return json.loads(raw,object_pairs_hook=unique,parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite checkpoint JSON')))


def verify():
    if not __debug__:raise RuntimeError('Never disable evidence assertions')
    proof=parse(PROOF.read_bytes());assert proof['schema_version']==1 and proof['status']=='passed-original-mashr-preparation-and-native-fit-development-checkpoint'
    assert proof['artifact']['path']=='controls.zip';archive=PROOF.parent/'controls.zip';assert pin(archive)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    for key in ['source_pins','protected_native_source_pins','baseline_dependencies']:
        for path,value in proof[key].items():assert pin(ROOT/path)==value,path
    native=parse((ROOT/'docs/literature/mashr-native-workflow/VALIDATION.json').read_bytes())
    assert len(proof['protected_native_source_pins'])==81 and proof['protected_native_source_pins']==native['source_pins']
    assert proof['native_artifact']==native['artifact'] and pin(ROOT/native['artifact']['path'])=={k:native['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    assert pin(ROOT/'docs/literature/omics-2023-2026/summary.json')==proof['catalog_summary_unchanged']
    assert proof['claims']==dict(original_covariance_functions_executed=True,native_fitter_executed=True,source_to_binary_reproduction_verified=False,whole_paper_reproduced=False,biological_accuracy_certified=False,sharing_and_composite_hypotheses_complete=False,paper_role_review_complete=False,study_routes_available=False,catalog_promoted=False,released_or_deployed=False)
    assert len(proof['archive_files'])==proof['artifact']['entries'] and len(proof['archive_files'])<100000
    with zipfile.ZipFile(archive) as bundle:
        entries=bundle.infolist();names=[e.filename for e in entries]
        assert len(names)==len(set(names))==len(proof['archive_files']) and set(names)==set(proof['archive_files'])
        total=0
        for entry in entries:
            path=PurePosixPath(entry.filename);assert not path.is_absolute() and '..' not in path.parts and '\\' not in entry.filename and not entry.is_dir() and stat.S_IFMT(entry.external_attr>>16) in [0,stat.S_IFREG]
            expected=proof['archive_files'][entry.filename];assert entry.file_size==expected['bytes'] and entry.file_size<=268_435_456
            digest=hashlib.sha256();size=0
            with bundle.open(entry) as stream:
                while block:=stream.read(1_048_576):size+=len(block);digest.update(block)
            total+=size;assert total<=2_147_483_648 and size==expected['bytes'] and digest.hexdigest()==expected['sha256'],entry.filename
        for path,value in proof['source_pins'].items():assert proof['archive_files']['source/'+path]==value
        records={k:parse(bundle.read(name)) for k,name in proof['reports'].items()}
        for key,record in records.items():
            prefix='evidence/'+key+'/'
            actual={n[len(prefix):]:v for n,v in proof['archive_files'].items() if n.startswith(prefix) and n!=prefix+'COMPLETED.json'}
            assert actual==record['report_files' if key in ['setup','earlier_setup'] else 'files'],key
        cases=records['controls']['controls'];assert len(cases)==13
        positive=[c for c in cases if c['expected_exit_code']==0];negative=[c for c in cases if c['expected_exit_code']==2];assert len(positive)==9 and len(negative)==4
        assert {c['name'] for c in negative}=={'ed-limit','work-limit','insufficient-strong','zero-SE'}
        comparisons=[c['original_numeric_comparison'] for c in positive]
        assert sum(c['count'] for c in comparisons)==proof['counts']['original_numeric_values_compared']==65234
        assert max(c['maximum_absolute_error'] for c in comparisons)==proof['maximum_original_absolute_error']<1e-10
        assert max(c['training_independent_original_dual_gap'] for c in comparisons)==proof['maximum_independent_original_training_dual_gap']<3e-8
        for case in positive:
            prefix='evidence/controls/'+case['name']+'/'
            stdout=parse(bundle.read(prefix+'controller.stdout'));assert stdout['status']=='COMPLETED_ORIGINAL_COVARIANCES_NATIVE_FIT'
            replay=stdout['verification'];assert replay['source_unchanged'] is True and replay['comparison']['maximum_absolute_error']==0
            marker=parse(bundle.read(prefix+'result/COMPLETED.json'));assert marker['scoped_result_admitted'] is True and marker['original_covariance_functions_executed'] is True and marker['native_fitter_executed'] is True
        for case in negative:
            prefix='evidence/controls/'+case['name']+'/result/'
            assert prefix+'COMPLETED.json' not in proof['archive_files']
            assert parse(bundle.read(prefix+'FAILURE.json'))['status']=='FAIL2_NO_RESULT_ADMITTED'
            warning=bundle.read(prefix+'WARNINGS.md').decode('utf-8');assert 'FAIL2' in warning and 'Required action' in warning
        runtime=records['runtime_controls']['controls'];assert len(runtime)==8
        assert {c['name'] for c in runtime}=={'no-install','offline-missing','corrupt-cache','timeout','log-limit','rss-limit','temp-churn','linux-unavailable'}
        assert records['setup']['user_analysis_admitted'] is False and len(records['setup']['packages'])==17
        evidence=records['evidence_controls']['controls'];assert len(evidence)==12
        altered=[c for c in evidence if c.get('all_outer_hashes_recomputed')];assert len(altered)==8 and all(c['exit_code']==2 and c['source_unchanged'] is True and c['untrusted_code_executed'] is False for c in altered)
        assert any(c['name']=='posterior' and c['child_hashes_recomputed'] is True for c in altered)
        assert any(c['name']=='target-separation' and c['target_posterior_changed'] is True and c['original_ED_log_run_timestamp_only_normalized'] is True and len(c['all_covariances_fit_histories_and_model_unchanged'])==4 for c in evidence)
        assert any(c['name']=='linux-parent' and c['expected_exit_code']==3 and c['source_unchanged'] is True for c in evidence)
        assert any(c['name']=='linux-child' and c['expected_exit_code']==0 and c['source_unchanged'] is True for c in evidence)
        assert any(c['name']=='workspace-junction' and c['observed_junction_rejected'] is True and c['target_untouched'] is True for c in evidence)
        admission=records['admission_controls']['controls'];assert len(admission)==5
        assert {c['name'] for c in admission}=={'output-in-report','scratch-in-report','scratch-in-input','changed-admitted-setup-graph','unavailable-after-admission'}
        assert all(c['exit_code']==2 and c['analysis_output_created'] is False and c['completed_runtime_report_unchanged'] is True and c['input_unchanged'] is True for c in admission if 'exit_code' in c)
    result=dict(status='passed-retained-mashr-preparation-checkpoint-integrity',archive_files=len(names),archive_uncompressed_bytes=total,counts=proof['counts'],source_files=len(proof['source_pins']),protected_native_sources=81,analysis_reexecuted=False,catalog_promoted=False)
    print(json.dumps(result,indent=2));return result


if __name__=='__main__':
    try:verify()
    except Exception as failure:print('FAIL2_PREPARATION_CHECKPOINT: '+str(failure)+'\nRequired action: retain the checkpoint and correct the named source/archive mismatch. Do not promote incomplete evidence.',file=sys.stderr);sys.exit(2)
