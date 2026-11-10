# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal native MTAG and unchanged original synthetic controls without adding catalog credit."""
import argparse
import json
import shutil
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack
from setup_mtag_reference import BASE, COMMIT
from verify_mtag_native_evidence import DEST, SOURCE_FILES, inspect


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    assert not (DEST/'validation.json').exists() and not (DEST/'controls.zip').exists()
    canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';canonical_before=pin(canonical)
    assert json.loads((BASE/'native-validation-3/COMPLETED.json').read_bytes())['status']=='PASS_NATIVE_MTAG_ESTIMATOR_AND_GMM_COMPONENT_ONLY'
    assert json.loads((BASE/'full-output-audit-3/COMPLETED.json').read_bytes())['status']=='PASS_COMPLETE_SYNTHETIC_MTAG_ESTIMATOR_OUTPUT_AUDIT_ONLY'
    files={}
    groups=['native-build21-1','native-build25-1','native-build21-2','native-build25-2','native-validation-1','native-validation-2','native-validation-3','full-output-audit-1','full-output-audit-2','full-output-audit-3',
            'original-workflow-probe-1','original-parser-control-1','paper-role-drafts-1']
    for group in groups:
        for path in sorted((BASE/group).rglob('*')):
            if path.is_file():files[group+'/'+path.relative_to(BASE/group).as_posix()]=path.read_bytes()
    for group in ['reference-setup-1','reference-setup-2','reference-setup-3','reference-setup-4']:
        for path in sorted((BASE/group).iterdir()):
            if path.is_file() and path.name!='micromamba':files[group+'/'+path.name]=path.read_bytes()
    for name in SOURCE_FILES:files['current-source/'+name]=(ROOT/name).read_bytes()
    source=BASE/'source-intake-1'
    files['upstream/COMPLETED.json']=(source/'COMPLETED.json').read_bytes()
    for name in ['LICENSE','README.md','mtag.py','mtag_munge.py','environment.yml']:
        files['upstream/source/'+name]=(source/'source'/name).read_bytes()
    for path in sorted((source/'source/ldsc_mod/ldscore').glob('*.py')):
        files['upstream/source/ldsc_mod/ldscore/'+path.name]=path.read_bytes()
    evidence=pack(DEST/'controls.zip',files)
    artifacts={}
    for compiler in ['21','25']:
        for kind in ['api','consumer']:
            name='mtag-'+kind+'-javac'+compiler+'.jar'
            shutil.copyfile(BASE/('native-build'+compiler+'-2')/('mtag-'+kind+'.jar'),DEST/name)
            artifacts[name]=pin(DEST/name)
    shutil.copyfile(BASE/'paper-role-drafts-1/paper-use-reviews.json',DEST/'paper-use-reviews.json')
    artifacts['paper-use-reviews.json']=pin(DEST/'paper-use-reviews.json')
    prior=BASE/'public-before-capacity-fix-1'
    for source_name,target_name in [('controls.zip','prior-capacity-controls.zip'),('validation.json','prior-capacity-validation.json')]:
        shutil.copyfile(prior/source_name,DEST/target_name)
        assert pin(prior/source_name)==pin(prior/('original-'+source_name))
        artifacts[target_name]=pin(DEST/target_name)
    summary=json.loads((ROOT/'docs/literature/omics-2023-2026/summary.json').read_bytes())
    proof=dict(schema_version=1,status='PASS_NATIVE_MTAG_COMPONENT_AND_ORIGINAL_SYNTHETIC_CONTROLS_ONLY',source_commit=COMMIT,
        new_catalog_credit=False,project_adapter_validated=False,mtag_remains_missing=True,whole_goal_complete=False,
        native_estimator_available=True,native_unadjusted_gmm_available=True,native_positive_definite_profile_only=True,
        original_full_ldsc_and_maxfdr_executed=True,independently_fitted_ldsc=False,independently_computed_maxfdr=False,
        original_matrix_cases=26,native_finite_cases=22,native_refusals=4,source_numeric_comparisons=257574,
        full_synthetic_output_numeric_comparisons=720000,total_numeric_comparisons=977574,junit_passes=24,
        source_parser_changed_cells=31497,source_parser_max_ulp_distance=2,source_parser_values_repaired=False,
        capacity_preflight_corrected=True,previous_numerical_results_byte_identical=True,prior_capacity_evidence_retained=True,
        paper_role_drafts=5,roles_applied_to_catalog=False,original_article_prose_redistributed=False,
        artifacts=artifacts,archive=pin(DEST/'controls.zip'),evidence=evidence,
        source_pins={name:pin(ROOT/name) for name in SOURCE_FILES},canonical_executable_unchanged=canonical_before,
        historical_catalog_snapshot=dict(new_scoped_integrations=97,including_existing_adapters=100,missing=246,summary=summary),
        failed_attempts_retained=['reference-setup-1','reference-setup-2','native-validation-1','full-output-audit-1'],
        expired_private_runtime_attempt_retained='reference-setup-3',current_reference_setup='reference-setup-4',
        required_action='MTAG remains missing until typed workflow, exact runtime admission, supported QC/covariance/maxFDR contracts, source failures, saved verification, root dispatch and reviewed paper roles pass. SKIP dependent analysis when required software cannot be installed or is forbidden; FAIL2 admits no repaired or partial result. Preserve every attempt and retry NEW paths after resolving the named cause.')
    save(DEST/'validation.json',proof)
    _,_,result=inspect()
    assert pin(canonical)==canonical_before
    save(out/'COMPLETED.json',dict(**result,archive=pin(DEST/'controls.zip'),canonical_executable_unchanged=True,files_sealed=len(files)))
    sums=''.join(pin(path)['sha256']+'  '+path.name+'\n' for path in sorted(DEST.iterdir()) if path.is_file() and path.name not in ['.gitattributes','SHA256SUMS'])
    (DEST/'SHA256SUMS').write_text(sums,encoding='ascii',newline='\n')
    print(json.dumps(dict(**result,archive_bytes=pin(DEST/'controls.zip')['bytes'],logical_files=len(files))))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/evidence/canonical checks')
    main()
