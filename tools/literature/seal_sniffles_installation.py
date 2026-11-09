# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze tested clean runtime installation and separate original format/source experiments."""
import json,os,stat,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from sniffles_installation_evidence import HOME,verify,byte_pin

BASE=ROOT/'build/literature-review/sniffles-runtime'


def main():
    assert not HOME.exists();inventory={}
    def add(path,name=None):
        assert stat.S_ISREG(path.lstat().st_mode) and not path.is_symlink();name=name or path.relative_to(ROOT).as_posix()
        assert name not in inventory;inventory[name]=path.read_bytes()
    def flat(root):
        with os.scandir(root) as stream:
            for item in stream:
                if stat.S_ISREG(item.stat(follow_symlinks=False).st_mode):add(Path(item.path))
    def tree(root):
        pending=[root]
        while pending:
            parent=pending.pop()
            with os.scandir(parent) as stream:
                for item in stream:
                    info=item.stat(follow_symlinks=False);path=Path(item.path)
                    if stat.S_ISDIR(info.st_mode):pending.append(path)
                    else:assert stat.S_ISREG(info.st_mode);add(path)
    checked=json.loads((BASE/'install-validation-1/COMPLETED.json').read_bytes())
    assert checked['status']=='PASS_SNIFFLES_CLEAN_INSTALL_AND_FIVE_PUBLIC_REFUSALS_ONLY'
    assert all(pin(ROOT/name)==value for name,value in checked['source_pins'].items())
    source_names=set(checked['source_pins'])|{
        'docs/literature/SNIFFLES_RUNTIME_INSTALLATION.md','tools/literature/prepare_sniffles_install_assets.py',
        'tools/literature/run_sniffles_extended_reference_linux.py','tools/literature/create_sniffles_extended_controls_linux.py',
        'tools/literature/create_sniffles_extended_workflow_request.py','tools/literature/compare_sniffles_workflow_reference.py',
        'tools/literature/sniffles_installation_evidence.py','tools/literature/seal_sniffles_installation.py'}
    source_pins={name:pin(ROOT/name) for name in sorted(source_names)}
    companions={name:value for name,value in source_pins.items() if name.endswith(('.zip','.jar'))}
    for name in sorted(source_names):
        if name not in companions:add(ROOT/name,'current-source/'+name)
    validation=BASE/'install-validation-1';flat(validation)
    for name in ['permission-absent','download-only','install-only','unsupported-windows','existing-output','fresh-install']:tree(validation/name)
    capsule=validation/'clean-project/build/literature-review/sniffles-runtime'
    for name in ['permission-absent','download-only','install-only','unsupported-windows','existing-output']:tree(capsule/name)
    installed=capsule/'installed';flat(installed);tree(installed/'notices')
    # Runtime/dependency payloads and downloaded wheels are not duplicated. Their
    # complete file manifests and real pip logs/report are retained; original
    # source and binary bytes are already in published repository artifacts.
    for name in ['extended-fixtures-1','extended-original-1','extended-original-2']:tree(BASE/name)
    for name in ['workflow-fatal-request-1','workflow-extended-request-1']:tree(BASE/name)
    for name in ['COMPLETED.json','WARNINGS.md','error.traceback']:add(BASE/'install-denied-1'/name)
    candidate=BASE/'sealed-installation-1';assert not candidate.exists();candidate.mkdir()
    index={name:byte_pin(raw) for name,raw in sorted(inventory.items())};index_raw=(json.dumps(index,sort_keys=True,indent=2)+'\n').encode();blobs={value['sha256']:inventory[name] for name,value in index.items()}
    with zipfile.ZipFile(candidate/'controls.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for name,raw in [('files.json',index_raw),*sorted(('blobs/'+sha,raw) for sha,raw in blobs.items())]:
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16;archive.writestr(info,raw)
    proof=dict(status='PASS_PRIVATE_SNIFFLES_CLEAN_INSTALL_AND_ORIGINAL_FORMAT_EXPERIMENTS_ONLY',implementation_credit=False,SV_calling_adapter_available=False,
        Java_calling_workflow_validated=False,whole_paper_reproduced=False,whole_goal_complete=False,source_pins=source_pins,
        archive=pin(candidate/'controls.zip'),file_index=byte_pin(index_raw),logical_files=len(index),unique_content_blobs=len(blobs),companion_artifacts=companions,
        installed_runtime_files=1721,private_site_files=573,official_downloaded_wheels=4,public_installation_refusals=5,
        complete_original_cases=10,complete_original_records=25,original_zero_exit_fatal_probes=1,
        original_behavior_warnings=['Legacy 1.0.7 emitted INV for a declared DUP with zero supporting reads. Preserve and inspect original types; do not relabel.',
            'Original 2.0.2 fatal annotation error returned exit0 and left a zero-byte VCF. Reject it, retain diagnostics, and admit no earlier partial case.'],
        development_workflow_requests_prepared_only=True,workflow_comparator_execution_not_yet_validated=True,
        canonical_executable_unchanged=checked['canonical_executable_unchanged'],catalog_at_checkpoint=dict(new_scoped_integrations=90,including_existing=93,missing=251,historical_snapshot_only=True),
        verification_scope='Frozen integrity and archived consistency only. Does not download/install software, execute Java/Sniffles, authenticate prior execution, refit SVs or certify biological/paper reproduction.')
    save(candidate/'VALIDATION.json',proof);verify(False,candidate)
    HOME.mkdir()
    for path in candidate.iterdir():(HOME/path.name).write_bytes(path.read_bytes())
    verify(False)
    print('Sealed clean installation and original format/source experiments; no calling/catalog availability credit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source-bound installation sealing checks')
    main()
