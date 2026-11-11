# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze original numerical outputs and independently implemented Java kernel only."""
import argparse,json,os,stat
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from mrmega_regression_evidence import HOME,STATUS,verify


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    base=ROOT/'build/literature-review/mrmega-runtime';out=a.out.resolve();assert not out.exists() and out.is_relative_to(base) and not HOME.exists();out.mkdir(parents=True)
    validated=json.loads((base/'kernel-validation-1/COMPLETED.json').read_bytes())
    assert validated['status']=='PASS_BOTH_MRMEGA_KERNEL_BUILDS_ON_THREE_JVMS_WITH_ORIGINAL_WLS_AND_SEPARATE_R_TAILS_ONLY'
    paths=[*sorted((ROOT/'tools/literature').glob('*mrmega*.py')),ROOT/'tools/literature/mrmega_reference_probe.cpp',
        ROOT/'src/main/java/org/jlinalg/meta/MrMegaRegression.java',ROOT/'src/test/java/org/jlinalg/meta/MrMegaRegressionReferenceProbe.java',
        ROOT/'docs/literature/MRMEGA_REGRESSION_DEVELOPMENT.md',ROOT/'LICENSE']
    pins={path.relative_to(ROOT).as_posix():pin(path) for path in paths};files={'current-source/'+name:(ROOT/name).read_bytes() for name in pins}
    def tree(root):
        pending=[root]
        while pending:
            parent=pending.pop()
            with os.scandir(parent) as stream:
                for item in stream:
                    path=Path(item.path);mode=item.stat(follow_symlinks=False).st_mode
                    if item.name=='__pycache__':continue
                    if stat.S_ISDIR(mode):pending.append(path)
                    else:assert stat.S_ISREG(mode);files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    for name in ['kernel-controls-1','kernel-build-21-1','kernel-build-25-1','kernel-validation-1','primary-roles-1']:tree(base/name)
    for name in ['reference-build-1','kernel-original-1']:
        home=base/name
        for path in home.iterdir():
            if path.is_file() and path.name!='mrmega-regression-reference':files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
        for folder in ['compile','version','run']:
            if (home/folder).is_dir():tree(home/folder)
    # Capture source provenance manifests/receipts, never upstream source, ZIP or binary.
    for folder in ['source-intake-1','source-intake-2']:
        for name in ['DOWNLOAD.json','MEMBERS.json','COMPLETED.json','WARNINGS.md']:
            path=base/folder/name
            if path.is_file():files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
    artifacts={}
    for folder,name in [('kernel-build-21-1','mrmega-regression-javac21.jar'),('kernel-build-25-1','mrmega-regression-javac25.jar')]:
        (out/name).write_bytes((base/folder/'mrmega-regression-development.jar').read_bytes());artifacts[name]=pin(out/name)
    prior=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-sniffles-calling-1/selection.json').read_bytes())
    assert len(prior['preserved'])==18 and all(pin(ROOT/name)==value for name,value in prior['preserved'].items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==prior['canonical']
    files['preserved-unrelated-worktree-pins.json']=(json.dumps(prior['preserved'],indent=2)+'\n').encode()
    packing=pack(out/'controls.zip',files);assert (out/'controls.zip').stat().st_size<95000000
    proof=dict(schema_version=1,status=STATUS,source_pins=pins,archive=pin(out/'controls.zip'),artifacts=artifacts,
        native_regression_kernel_available=True,implementation_credit=False,full_MRMEGA_workflow_available=False,catalog_credit=False,
        whole_paper_reproduced=False,whole_goal_complete=False,source_control_cases=54,complete_regression_comparisons=324,contract_suites=6,
        probability_scope='Stable native survival fields have a separate base-R4.6.1 reference. Three original -inf probability fields and four original undefined-test boundaries remain separately retained. No original value is overwritten/relabelled.',
        external_Java_dependency=dict(path='build/cli/omicsmatic-0.3.7.jar',**prior['canonical']),
        canonical_executable_unchanged=prior['canonical'],unrelated_files_preserved=18,
        upstream_source_archive=dict(url='https://tools.gi.ut.ee/tools/MR-MEGA_v0.2.zip',sha256='e91eb5bdba0a398619e6cd98c351ea72d94e09d6f5a1d25bb7da8a66da938ffe',private_only_not_redistributed=True),
        catalog_at_checkpoint=dict(new_scoped_integrations=91,including_existing=94,missing=250,reviewed_roles=1009,historical_snapshot_only=True),
        verification_scope='Frozen integrity and archived numerical consistency only; no original/Java/R execution, authentication, current user-data refit, full MR-MEGA file/ancestry workflow, biological or whole-paper certification.',**packing)
    save(out/'VALIDATION.json',proof);verify(False,out);HOME.mkdir()
    for name in ['VALIDATION.json','controls.zip',*artifacts]:
        with (HOME/name).open('xb') as stream:stream.write((out/name).read_bytes())
    verify(False);print('Sealed native regression component and all324 original comparisons; MR-MEGA/full-goal availability remains unfinished')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable frozen scope, source or unrelated-work checks')
    main()
