# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze isolated native cisTarget artifacts/evidence without promoting catalog availability."""
import argparse,json,os,stat
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin
from cistarget_development_evidence import HOME,STATUS,verify


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();base=ROOT/'build/literature-review/cistarget-runtime';out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(base) and not HOME.exists();out.mkdir(parents=True)
    checkpoints=['recovery-validation-3','pairwise-validation-1','workflow-validation-4','feather-validation-3','literal-example-2','primary-roles-3']
    assert all(json.loads((base/n/'COMPLETED.json').read_bytes())['status'].startswith('PASS') for n in checkpoints)
    paths=[*sorted((ROOT/'tools/literature').glob('*cistarget*.*')),ROOT/'src/main/java/org/jlinalg/enrichment/CisTargetRecovery.java',ROOT/'src/main/java/org/jlinalg/cli/CisTargetWorkflowCli.java',ROOT/'src/test/java/org/jlinalg/enrichment/CisTargetRecoveryProbe.java',ROOT/'docs/literature/CISTARGET_DEVELOPMENT.md',ROOT/'docs/literature/CISTARGET_NUMPY_LICENSE.txt',*sorted((ROOT/'examples/cistarget-ranked-features').iterdir())]
    assert all(p.is_file() and not p.is_symlink() for p in paths);pins={p.relative_to(ROOT).as_posix():pin(p) for p in paths};files={};objects={}
    def add(name,path):
        raw=path.read_bytes();value=byte_pin(raw);files[name]=objects.setdefault(value['sha256'],raw)
    for name in pins:add('current-source/'+name,ROOT/name)
    def tree(root):
        for folder,dirs,names in os.walk(root,followlinks=False):
            dirs[:]=[d for d in dirs if d not in ['__pycache__','venv']]
            for name in names:
                path=Path(folder)/name;assert stat.S_ISREG(path.stat(follow_symlinks=False).st_mode)
                if path.suffix=='.feather':continue
                add(path.relative_to(ROOT).as_posix(),path)
    names=['controls-1','reference-controls-1','recovery-build21-1','recovery-build25-1','recovery-build21-2','recovery-build25-2','recovery-validation-1','recovery-validation-2','workflow-controls-1','workflow-build21-1','workflow-build25-1','workflow-validation-1','workflow-validation-2','feather-source-conversion-1','feather-validator-before-positive-control','feather-validation-1','feather-validation-2','literal-example-1','primary-roles-3']
    for name in names:tree(base/name)
    for name in ['numpy-source-1','recovery-build21-3','recovery-build25-3','recovery-license21-1','recovery-license25-1','recovery-validation-3','pairwise-controls-1','pairwise-reference-controls-1','pairwise-validation-1','workflow-build21-2','workflow-build25-2','workflow-validation-3','workflow-validation-4','feather-validation-3','literal-example-2','unpublished-before-pairwise-correction']:tree(base/name)
    for folder in ['reference-setup-1','reference-setup-2','reference-setup-3']:
        for name in ['COMPLETED.json','WARNINGS.md','health.stdout','health.stderr','health.process.json','freeze.stdout']:
            path=base/folder/name
            if path.is_file():add(path.relative_to(ROOT).as_posix(),path)
    for name in ['COMPLETED.json','ctxcore-pypi.json','ctxcore-download.json','rcistarget-download.json','ctxcore-members.json','rcistarget-members.json']:
        path=base/'source-intake-1'/name;add(path.relative_to(ROOT).as_posix(),path)
    catalog=[ROOT/'docs/literature/omics-2023-2026'/n for n in ['papers.json','tool-inventory.json','summary.json']]+[ROOT/'tools/literature/reviewed-uses.json']
    catalog_pins={p.relative_to(ROOT).as_posix():pin(p) for p in catalog}
    for path in catalog:add('checkpoint-catalog/'+path.relative_to(ROOT).as_posix(),path)
    parent=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-mrmega-catalog-1/selection.json').read_bytes())
    assert len(parent['preserved'])==18 and all(pin(ROOT/n)==v for n,v in parent['preserved'].items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==parent['canonical']
    artifacts={}
    for kind,folders in [('recovery',['recovery-license21-1','recovery-license25-1']),('workflow',['workflow-build21-2','workflow-build25-2'])]:
        for compiler,folder in zip(['javac21','javac25'],folders,strict=True):
            source=base/folder/('cistarget-'+kind+'-development.jar');name='cistarget-'+kind+'-'+compiler+'.jar';(out/name).write_bytes(source.read_bytes());artifacts[name]=pin(out/name)
    packing=pack(out/'controls.zip',files);assert pin(out/'controls.zip')['bytes']<95000000
    proof=dict(schema_version=1,status=STATUS,source_pins=pins,artifacts=artifacts,archive=pin(out/'controls.zip'),
        standalone_native_tsv_workflow_available=True,feather_converter_available=True,root_command_available=False,catalog_credit=False,
        complete_kernel_comparisons=672,kernel_contract_suites=12,complete_tsv_workflows=96,actual_cli_refusals=60,original_Feather_native_flows=6,literal_example_runs=6,
        actual_missing_runtime_skips=1,actual_converter_capacity_refusals=1,source_profiles=['CTXCORE_0_2_0','RCISTARGET_1_29_0_ICISTARGET'],
        canonical_executable_unchanged=parent['canonical'],unrelated_files_preserved=18,checkpoint_catalog_pins=catalog_pins,
        catalog_at_checkpoint=dict(new_scoped_integrations=92,including_existing=95,missing=249,reviewed_roles=1012,observations=2465,historical_snapshot_only=True),
        reviewed_role_drafts=6,pending_new_source_observations=1,full_R_package_installed=False,restricted_pycistarget_wrapper_installed=False,
        whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,
        verification_scope='Frozen integrity and stored numeric/selection/leading-membership relationships only. No runtime analysis, independent refit, execution authentication, historical paper reproduction or biological certification. Earlier failed setup/kernel runs and the all-zero initial file fixture remain labeled evidence; only corrected/positive current controls establish this development checkpoint.',**packing)
    save(out/'VALIDATION.json',proof);verify(False,out)
    assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(ROOT/n)==v for n,v in catalog_pins.items())
    HOME.mkdir()
    for name in ['VALIDATION.json','controls.zip',*artifacts]:(HOME/name).write_bytes((out/name).read_bytes())
    # Candidate verification above covers identical publication bytes. No repeated numeric pass is needed.
    assert all(pin(HOME/n)==pin(out/n) for n in ['VALIDATION.json','controls.zip',*artifacts])
    save(out/'COMPLETED.json',dict(status='PASS_FROZEN_CISTARGET_CANDIDATE_AND_IDENTICAL_PUBLISHED_BYTES',
        published_files={p.relative_to(ROOT).as_posix():pin(p) for p in HOME.iterdir() if p.is_file()},
        all_current_source_pins=pins,complete_kernel_comparisons=672,complete_file_and_example_runs=108,
        catalog_credit=False,whole_goal_complete=False))
    print('Sealed native cisTarget development:672 kernel comparisons;108 file/example flows; catalog credit remains pending',flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/evidence preservation checks')
    main()
