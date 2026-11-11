# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native ancestry evidence without redistributing original MR-MEGA code."""
import argparse,json,os,stat
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from mrmega_ancestry_evidence import HOME,STATUS,verify


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    base=ROOT/'build/literature-review/mrmega-runtime';out=args.out.resolve()
    assert not out.exists() and out.is_relative_to(base) and not HOME.exists();out.mkdir(parents=True)
    validation=json.loads((base/'ancestry-validation-1/COMPLETED.json').read_bytes())
    assert validation['complete_ancestry_comparisons']==72 and validation['contract_suites']==6 and validation['status'].startswith('PASS_')
    names=['build_mrmega_ancestry.py','create_mrmega_ancestry_controls.py','mrmega_ancestry_probe.cpp',
        'run_mrmega_ancestry_reference_linux.py','validate_mrmega_ancestry.py','mrmega_ancestry_evidence.py','seal_mrmega_ancestry.py']
    paths=[*(ROOT/'tools/literature'/n for n in names),ROOT/'src/main/java/org/jlinalg/meta/MrMegaAncestry.java',
        ROOT/'src/test/java/org/jlinalg/meta/MrMegaAncestryReferenceProbe.java',ROOT/'docs/literature/MRMEGA_ANCESTRY_DEVELOPMENT.md']
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
                        if item.name!='original-ancestry-probe':files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for n in ['ancestry-controls-1','ancestry-build-21-1','ancestry-build-25-1','ancestry-original-1','ancestry-validation-1']:tree(base/n)
    artifacts={}
    for compiler,folder in [('javac21','ancestry-build-21-1'),('javac25','ancestry-build-25-1')]:
        name='mrmega-ancestry-'+compiler+'.jar';(out/name).write_bytes((base/folder/'mrmega-ancestry-development.jar').read_bytes());artifacts[name]=pin(out/name)
    parent=ROOT/'docs/literature/mrmega-regression-development';parent_pins={p.relative_to(ROOT).as_posix():pin(p) for p in parent.iterdir() if p.is_file()}
    from mrmega_regression_evidence import verify as verify_regression
    verify_regression(False)
    prior=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-mrmega-regression-1/selection.json').read_bytes())
    assert len(prior['preserved'])==18 and all(pin(ROOT/n)==v for n,v in prior['preserved'].items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==prior['canonical']
    packing=pack(out/'controls.zip',files);assert (out/'controls.zip').stat().st_size<95000000
    proof=dict(schema_version=1,status=STATUS,source_pins=pins,frozen_parent_pins=parent_pins,
        archive=pin(out/'controls.zip'),artifacts=artifacts,native_prepared_ancestry_available=True,
        source_cases=12,complete_ancestry_comparisons=72,coupled_regression_comparisons=72,contract_suites=6,
        catalog_credit=False,full_MRMEGA_workflow_available=False,whole_paper_reproduced=False,whole_goal_complete=False,
        canonical_executable_unchanged=prior['canonical'],unrelated_files_preserved=18,
        catalog_at_checkpoint=dict(new_scoped_integrations=91,including_existing=94,missing=250,reviewed_roles=1009,historical_snapshot_only=True),
        upstream_source_not_redistributed=True,
        verification_scope='Stored integrity, full selected populations and numerical consistency only. No C++ or Java execution, authenticity, current user-data refit, complete GWAS file/allele/GC workflow, whole-paper or biological certification.',**packing)
    save(out/'VALIDATION.json',proof);verify(False,out);HOME.mkdir()
    for name in ['VALIDATION.json','controls.zip',*artifacts]:
        with (HOME/name).open('xb') as stream:stream.write((out/name).read_bytes())
    verify(False);print('Sealed72 complete native ancestry/model comparisons; full workflow/catalog credit remains unfinished')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen scope and preservation checks')
    main()
