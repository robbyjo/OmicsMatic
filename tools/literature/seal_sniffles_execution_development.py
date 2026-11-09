# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal development builds, runtime binding and refusal/process controls without SV credit."""
import json, os, stat, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from sniffles_execution_development_evidence import HOME,BASE as PREFIX,byte_pin,verify
from sniffles_native_evidence import verify as native_verify

BASE=ROOT/'build/literature-review/sniffles-runtime'


def main():
    assert not HOME.exists()
    native,_=native_verify(False);inventory={}
    def add(path,name=None):
        info=path.lstat();assert stat.S_ISREG(info.st_mode) and not path.is_symlink()
        raw=path.read_bytes();assert len(raw)==info.st_size
        name=name or path.relative_to(ROOT).as_posix();assert name not in inventory;inventory[name]=raw
    def tree(root):
        pending=[root]
        while pending:
            parent=pending.pop()
            with os.scandir(parent) as stream:
                for item in stream:
                    info=item.stat(follow_symlinks=False);path=Path(item.path)
                    if stat.S_ISDIR(info.st_mode):pending.append(path)
                    else:assert stat.S_ISREG(info.st_mode);add(path)
    builds=[('execution-build-12','sniffles-execution-development.jar','sniffles-vcf-development.jar'),
        ('execution-build-25-3','sniffles-execution-javac25-development.jar','sniffles-vcf-javac25-development.jar')]
    source_paths=set()
    for build,_,_ in builds:
        proof=json.loads((BASE/build/'COMPLETED.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items())
        source_paths.update(proof['source_pins'])
    source_paths.update('tools/literature/'+name for name in ['prepare_sniffles_runtime_profiles.py','export_sniffles_runtime.py',
        'create_sniffles_workflow_request.py','create_sniffles_extended_controls_linux.py','validate_sniffles_execution_development.py',
        'seal_sniffles_execution_development.py','sniffles_execution_development_evidence.py','setup_deseq2_reference.py',
        'java/org/jlinalg/cli/SnifflesProcessControls.java'])
    source_paths.update(['docs/literature/SNIFFLES_EXECUTION_DEVELOPMENT.md',
        'src/main/java/org/jlinalg/cli/RsemProcess.java','src/main/java/org/jlinalg/cli/RepeatHostFiles.java','src/main/java/org/jlinalg/cli/RsemElf.java'])
    source_pins={name:pin(ROOT/name) for name in sorted(source_paths)}
    for name in sorted(source_paths):add(ROOT/name,'current-source/'+name)
    for name in ['execution-sources-1','execution-profiles-2','runtime-binding-1','runtime-binding-2','runtime-binding-3',
        'execution-build-12','execution-build-25-3','extended-fixtures-1']:tree(BASE/name)
    for name in ['COMPLETED.json','WARNINGS.md']:add(BASE/'runtime-export-1'/name)
    validation=BASE/'execution-development-validation-3'
    for child in validation.iterdir():
        if child.name.endswith(('-process-controls','-process-classes')):continue
        if child.is_dir():tree(child)
        else:add(child)
    # Retain failed attempts and the invalidated FIFO-coverage claim without
    # traversing their special-node workspaces or rewriting their old receipts.
    for attempt in ['execution-development-validation-1','execution-development-validation-2']:
        for name in ['COMPLETED.json','error.traceback']:add(BASE/attempt/name)
    for attempt,relative in [('process-controls-1','fifo'),('execution-development-validation-2','javac21-process-controls/fifo')]:
        for name in ['process.json','process.stderr','process.stdout']:add(BASE/attempt/relative/name)
    add(BASE/'process-controls-1/COMPLETED.json')
    for name in ['summary.json','tool-inventory.json']:
        add(ROOT/'docs/literature/omics-2023-2026'/name,'catalog-at-checkpoint/'+name)
    candidate=BASE/'sealed-execution-development-1';assert not candidate.exists();candidate.mkdir()
    artifacts={}
    for build,artifact,_ in builds:
        raw=(BASE/build/'sniffles-execution-development.jar').read_bytes();(candidate/artifact).write_bytes(raw);artifacts[artifact]=byte_pin(raw)
    index={name:byte_pin(raw) for name,raw in sorted(inventory.items())};index_raw=(json.dumps(index,sort_keys=True,indent=2)+'\n').encode()
    blobs={value['sha256']:inventory[name] for name,value in index.items()}
    with zipfile.ZipFile(candidate/'controls.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in [('files.json',index_raw),*sorted(('blobs/'+sha,raw) for sha,raw in blobs.items())]:
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,raw)
    profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/sniffles-execution/profiles.json').read_bytes())
    proof=dict(status='PASS_SNIFFLES_EXECUTION_DEVELOPMENT_WITH_DECLARED_LIMITATIONS_ONLY',
        implementation_credit=False,SV_calling_adapter_available=False,scientific_calling_workflow_validated=False,
        whole_paper_reproduced=False,whole_goal_complete=False,source_pins=source_pins,artifacts=artifacts,
        archive=pin(candidate/'controls.zip'),file_index=byte_pin(index_raw),logical_files=len(index),
        unique_content_blobs=len(blobs),compiler_cases=builds,public_refusal_controls=30,passed_process_controls=22,skipped_process_controls=2,
        original_editions={tag:row['commit'] for tag,row in profile['editions'].items()},
        native_component_validation=pin(ROOT/'docs/literature/sniffles-vcf-development/VALIDATION.json'),
        canonical_executable_unchanged=native['canonical_executable_unchanged'],clean_checkout_runtime_provisioning_unfinished=True,
        FIFO_coverage='Skipped: mkfifo errno95 on permitted Windows-mounted workspace. Earlier generic failure receipts do not establish FIFO rejection.',
        pending='Complete calling batches, extended CRAM/reference/CSI and source-fatal/late-failure controls, current-artifact full-record comparison, successful frozen verification and clean-checkout provisioning remain unvalidated. No catalog availability credit.',
        catalog_at_checkpoint=dict(new_scoped_integrations=90,including_existing=93,missing=251,historical_snapshot_only=True),
        verification_scope='Frozen integrity and archived consistency only; no current runtime health, independent SV refit, authenticity or biological certification.')
    save(candidate/'VALIDATION.json',proof);verify(False,candidate)
    HOME.mkdir()
    for file in candidate.iterdir():(HOME/file.name).write_bytes(file.read_bytes())
    verify(False)
    print('Sealed development checkpoint: 30 public refusal controls, 22 process passes, 2 explicit FIFO skips; no calling/catalog credit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable development sealing or special-node checks')
    main()
