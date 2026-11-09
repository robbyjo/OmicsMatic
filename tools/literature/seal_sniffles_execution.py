# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze complete tested calling paths and failures without changing earlier evidence."""
import argparse,json,os,stat
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack
from sniffles_execution_evidence import HOME,STATUS,verify

BASE=ROOT/'build/literature-review/sniffles-runtime'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE) and not HOME.exists();out.mkdir(parents=True)
    assert json.loads((BASE/'interface-adoption-1/COMPLETED.json').read_bytes())['status']=='PASS_EXACT_TESTED_SNIFFLES_INTERFACE_ADOPTION_AND_FULL_REBUILDS_ONLY'
    files={};special={}
    def tree(root):
        assert root.is_dir();pending=[root]
        while pending:
            parent=pending.pop()
            with os.scandir(parent) as stream:
                for item in stream:
                    path=Path(item.path);mode=item.stat(follow_symlinks=False).st_mode
                    if item.name=='__pycache__':continue
                    if stat.S_ISDIR(mode):pending.append(path)
                    elif stat.S_ISREG(mode):files[path.relative_to(ROOT).as_posix()]=path.read_bytes()
                    else:
                        # Never open/follow a FIFO, symlink or other special control.
                        special[path.relative_to(ROOT).as_posix()]=dict(mode=mode,link=os.readlink(path) if stat.S_ISLNK(mode) else None,content_not_opened=True)
    directories=['execution-build-14','execution-build-25-5','option-interface-preview-1','diagnostic-interface-preview-1',
        'option-interface-method-comparison-1','option-interface-method-comparison-2','option-interface-method-comparison-3',
        'diagnostic-interface-method-comparison-1','option-source-confirmation-1','option-original-1',
        'workflow-current-2','option-interface-validation-1','workflow-extended-2','public-example-1','public-example-audit-1',
        'workflow-current-reference-comparison-1','workflow-current-reference-comparison-2','option-complete-reference-comparison-1',
        'extended-complete-reference-comparison-1','default-frozen-controls-1','option-frozen-controls-1',
        'current-boundary-controls-2','cancellation-diagnostic-controls-1','combined-failure-controls-1',
        'obsolete-format-cancellation-1','obsolete-refinement-cancellation-2','workflow-extended-1','workflow-current-1','current-boundary-controls-1',
        'interface-adoption-1','execution-final-build-21','execution-final-build-25','runtime-binding-4']
    for name in directories:tree(BASE/name)
    metadata_only={'sniffles_catalog.py','prepare_sniffles_catalog.py','curate_sniffles_uses.py'}
    sources={path for path in ROOT.glob('tools/literature/*sniffles*.py') if path.name not in metadata_only and 'catalog' not in path.name}
    sources.update((ROOT/'src/main/java/org/jlinalg/cli').glob('Sniffles*.java'))
    sources.update([ROOT/'src/main/java/org/jlinalg/cli/SimpleJson.java',ROOT/'src/main/java/org/jlinalg/genetics/SnifflesVcf.java',
        ROOT/'docs/literature/SNIFFLES_WORKFLOWS.md',ROOT/'docs/literature/SNIFFLES_OPTION_INTERFACE_DEVELOPMENT.md',ROOT/'LICENSE'])
    sources.update(path for path in (ROOT/'src/main/resources/org/jlinalg/omics/sniffles-execution').rglob('*') if path.is_file() and '__pycache__' not in path.parts)
    pins={path.relative_to(ROOT).as_posix():pin(path) for path in sorted(sources)}
    files.update({'current-source/'+name:(ROOT/name).read_bytes() for name in pins})
    if special:files['special-control-metadata.json']=(json.dumps(special,indent=2)+'\n').encode()
    artifacts={}
    for folder,name in [('execution-final-build-21','sniffles-workflow.jar'),('execution-final-build-25','sniffles-workflow-javac25.jar')]:
        raw=(BASE/folder/'sniffles-execution-development.jar').read_bytes();(out/name).write_bytes(raw);artifacts[name]=pin(out/name)
    parents={}
    for folder in ['sniffles-vcf-development','sniffles-execution-development','sniffles-installation-validation','sniffles-runtime-distribution']:
        for path in (ROOT/'docs/literature'/folder).iterdir():
            if path.is_file():parents[path.relative_to(ROOT).as_posix()]=pin(path)
    selected=json.loads((ROOT/'build/literature-review/push-checkpoint-2026-11-09-sniffles-installation-1/selection.json').read_bytes())
    held=selected['preserved'];assert len(held)==18 and all(pin(ROOT/name)==value for name,value in held.items())
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==selected['canonical']
    files['preserved-unrelated-worktree-pins.json']=(json.dumps(held,indent=2)+'\n').encode()
    packing=pack(out/'controls.zip',files);assert (out/'controls.zip').stat().st_size<95000000
    proof=dict(schema_version=1,status=STATUS,source_pins=pins,parent_proofs=parents,archive=pin(out/'controls.zip'),artifacts=artifacts,
        artifact=dict(path=HOME.relative_to(ROOT).as_posix()+'/sniffles-workflow.jar',**artifacts['sniffles-workflow.jar']),
        declared_calling_workflow_available=True,implementation_credit=False,canonical_adapter_available=False,
        independent_SV_calling=False,whole_package_implemented=False,whole_paper_reproduced=False,whole_goal_complete=False,
        complete_workflow_batches=4,complete_cases=35,complete_records=86,portable_frozen_verifications=12,rehash_false_value_rejections=2,
        actual_corrected_cancellation_controls=2,actual_original_fatal_and_data_mutation_controls=2,actual_preflight_controls=7,
        canonical_executable_unchanged=selected['canonical'],unrelated_files_preserved=18,
        successful_execution_scope='Default14 cases/39 records use base14; corrected-option10/20 and extended10/25 use option candidate; relocated example1/2 uses the second compiler option candidate. Both compilers have explicit method/class/bootstrap comparisons: option changes affect only argument construction; diagnostic changes affect only failure formatting. Both full source builds match the final actually cancellation-tested diagnostic candidates byte for byte. These relationships are retained rather than relabeling old executions.',
        retained_failures='Earlier header comparators and cancellation diagnostic suite remain FAIL. New complete-header comparisons, corrected actual cancellations and explicit combined receipt audits are separate receipts. Superseded original outputs remain unadmitted and retained.',
        verification_scope='Frozen integrity/structural consistency only. No download/install, current runtime check, Java/caller execution, authenticity, independent SV refit, biological accuracy or whole-paper certification.',
        catalog_at_checkpoint=dict(new_scoped_integrations=90,including_existing=93,missing=251,historical_snapshot_only=True),**packing)
    save(out/'VALIDATION.json',proof);verify(False,out)
    assert all(pin(ROOT/name)==value for name,value in pins.items()) and all(pin(ROOT/name)==value for name,value in held.items())
    HOME.mkdir()
    for name in ['VALIDATION.json','controls.zip',*artifacts]:
        with (HOME/name).open('xb') as stream:stream.write((out/name).read_bytes())
    verify(False);print('Sealed35 complete cases/86 original records, explicit failures and12 portable frozen checks; catalog not yet credited')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/evidence/admission checks')
    main()
