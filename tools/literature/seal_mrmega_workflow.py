# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze tested standalone Java workflow and complete original/reference controls."""
import argparse,json,os,stat
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from mrmega_workflow_evidence import HOME,STATUS,verify


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    base=ROOT/'build/literature-review/mrmega-runtime';out=args.out.resolve();assert not out.exists() and out.is_relative_to(base) and not HOME.exists();out.mkdir(parents=True)
    precision=json.loads((base/'preparation-validation-2/COMPLETED.json').read_bytes());contracts=json.loads((base/'workflow-contracts-3/COMPLETED.json').read_bytes())
    assert precision['status']=='PASS_ALL_132_FULL_PRECISION_NATIVE_FILE_PREPARATIONS_REGRESSIONS_AND_SEPARATE_R_PROBABILITIES'
    assert contracts['status']=='PASS_TWENTY_FOUR_REAL_CLI_REFUSALS_AND_ANALYTIC_ODD_MEDIAN_ON_BOTH_BUILDS_THREE_JVMS'
    helpers=['build_mrmega_workflow.py','create_mrmega_workflow_controls.py','run_mrmega_workflow_reference_linux.py','validate_mrmega_workflow.py',
        'mrmega_preparation_probe.cpp','run_mrmega_preparation_reference_linux.py','validate_mrmega_preparation.py','test_mrmega_workflow_failures.py',
        'mrmega_workflow_evidence.py','seal_mrmega_workflow.py']
    paths=[*(ROOT/'tools/literature'/n for n in helpers),ROOT/'src/main/java/org/jlinalg/meta/MrMegaWorkflow.java',
        ROOT/'src/main/java/org/jlinalg/cli/MrMegaWorkflowCli.java',ROOT/'docs/literature/MRMEGA_WORKFLOW_DEVELOPMENT.md']
    pins={p.relative_to(ROOT).as_posix():pin(p) for p in paths};files={'current-source/'+n:(ROOT/n).read_bytes() for n in pins}
    def tree(root):
        pending=[root]
        while pending:
            with os.scandir(pending.pop()) as stream:
                for item in stream:
                    path=Path(item.path);mode=item.stat(follow_symlinks=False).st_mode
                    if item.name=='__pycache__':continue
                    if stat.S_ISDIR(mode):pending.append(path)
                    else:
                        assert stat.S_ISREG(mode)
                        if item.name=='original-preparation-probe' or item.name.endswith('.o'):continue
                        files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['workflow-controls-1','workflow-original-1','preparation-original-1','workflow-build-21-4','workflow-build-25-3','workflow-validation-4','workflow-contracts-3','preparation-validation-2']:tree(base/name)
    artifacts={}
    for c,folder in [('javac21','workflow-build-21-4'),('javac25','workflow-build-25-3')]:
        name='mrmega-workflow-'+c+'.jar';(out/name).write_bytes((base/folder/'mrmega-workflow-development.jar').read_bytes());artifacts[name]=pin(out/name)
    from mrmega_ancestry_evidence import verify as verify_parent
    parent_proof,_=verify_parent(False);parent=ROOT/'docs/literature/mrmega-ancestry-development'
    parent_pins={p.relative_to(ROOT).as_posix():pin(p) for p in parent.iterdir() if p.is_file()}
    previous=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-mrmega-ancestry-1/selection.json').read_bytes())
    assert len(previous['preserved'])==18 and all(pin(ROOT/n)==v for n,v in previous['preserved'].items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==previous['canonical']==parent_proof['canonical_executable_unchanged']
    packing=pack(out/'controls.zip',files);assert (out/'controls.zip').stat().st_size<95000000
    proof=dict(schema_version=1,status=STATUS,source_pins=pins,frozen_parent_pins=parent_pins,archive=pin(out/'controls.zip'),artifacts=artifacts,
        standalone_native_file_workflow_available=True,main_dispatcher_registered=False,catalog_credit=False,whole_paper_reproduced=False,whole_goal_complete=False,
        original_families=22,complete_workflow_comparisons=132,full_precision_fit_comparisons=4134,stable_R_probability_fields=12402,
        real_CLI_refusals=144,analytic_odd_median_comparisons=6,canonical_executable_unchanged=previous['canonical'],unrelated_files_preserved=18,
        original_source_objects_archives_and_binaries_not_redistributed=True,
        catalog_at_checkpoint=dict(new_scoped_integrations=91,including_existing=94,missing=250,reviewed_roles=1009,historical_snapshot_only=True),
        verification_scope='Stored source/parent bindings, complete prepared populations, original CLI/file and full-precision public API values, independent recorded R probabilities and actual CLI failure ledgers. No execution/refit/authentication/whole-paper or biological certification.',**packing)
    save(out/'VALIDATION.json',proof);verify(False,out);HOME.mkdir()
    for name in ['VALIDATION.json','controls.zip',*artifacts]:
        with (HOME/name).open('xb') as stream:stream.write((out/name).read_bytes())
    verify(False);print('Sealed132 complete GWAS workflows/4134 full-precision fits/12402 R probabilities/144 refusals; catalog registration pending')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete workflow evidence and preservation checks')
    main()
