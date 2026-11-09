# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal the Sniffles VCF component's complete original-record validation checkpoint.

This deliberately grants no Sniffles calling-adapter or catalog availability credit.
"""
import hashlib
import json
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from sniffles_native_evidence import verify, byte_pin

BASE=ROOT/'build/literature-review/sniffles-runtime'
HOME=ROOT/'docs/literature/sniffles-vcf-development'


def main():
    assert not HOME.exists()
    inventory={}
    def add(path,name=None):
        assert path.is_file() and not path.is_symlink()
        name=name or path.relative_to(ROOT).as_posix()
        raw=path.read_bytes()
        assert name not in inventory
        inventory[name]=raw
    def tree(root):
        for path in sorted(root.rglob('*')):
            if path.is_file():add(path)
    def flat(root):
        for path in sorted(root.iterdir()):
            if path.is_file():add(path)
    source_paths=[ROOT/'docs/literature/SNIFFLES_VCF_COMPONENT.md',
        ROOT/'src/main/java/org/jlinalg/genetics/SnifflesVcf.java',
        ROOT/'src/main/java/org/jlinalg/cli/SnifflesJson.java',
        ROOT/'src/main/java/org/jlinalg/cli/SnifflesVcfCli.java',
        ROOT/'src/test/java/org/jlinalg/genetics/SnifflesVcfTest.java',ROOT/'LICENSE']
    source_paths.extend(ROOT/'tools/literature'/n for n in [
        'intake_sniffles_reference.py','audit_sniffles_roles.py','setup_sniffles_reference_linux.py',
        'create_sniffles_controls_linux.py','run_sniffles_reference_linux.py','build_sniffles_native.py',
        'validate_sniffles_native.py','sniffles_native_evidence.py','seal_sniffles_native.py',
        'setup_deseq2_reference.py','collect_omics_review.py'])
    source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in source_paths}
    for path in source_paths:add(path,'current-source/'+path.relative_to(ROOT).as_posix())
    for name in ['tag-intake-1','source-intake-3','primary-roles-2','fixtures-1','reference-denial-1','original-controls-1','original-controls-2',
                 'native-build-2','native-build-25-2','native-validation-1','native-validation-25-1']:
        root=BASE/name
        if name=='source-intake-3':
            flat(root)
            for edition in sorted(root.iterdir()):
                if edition.is_dir():flat(edition) # Complete immutable archives; no redundant unpacked files.
        else:tree(root)
    # Preserve unsuccessful preparation attempts and full compiler diagnostics.
    # Installed environments and mutable build trees are not redistributed.
    for name in ['reference-setup-1','reference-setup-2','reference-setup-3']:
        root=BASE/name;flat(root)
        for metadata in [root/'legacy-build/CMakeCache.txt',
                         root/'legacy-build/src/CMakeFiles/sniffles.dir/flags.make',
                         root/'legacy-build/lib/bamtools-2.3.0/src/api/CMakeFiles/BamTools-static.dir/flags.make']:
            if metadata.is_file():add(metadata)
    data=ROOT/'docs/literature/omics-2023-2026'
    census=json.loads((BASE/'primary-roles-2/COMPLETED.json').read_bytes())
    for name,value in census['catalog_before'].items():
        assert pin(ROOT/name)==value
        add(ROOT/name,'catalog-at-checkpoint/'+name)
    summary=json.loads((data/'summary.json').read_bytes())
    assert summary['implementation_status_counts']['missing']==251
    tools=json.loads((data/'tool-inventory.json').read_bytes())
    if isinstance(tools,dict):tools=tools['tools']
    row=next(t for t in tools if t['id']=='sniffles')
    assert row['implementation_status']=='missing'
    canonical=pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')
    expected_canonical=dict(bytes=164898617,sha256='545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2')
    assert canonical==expected_canonical
    artifacts={}
    payload={}
    for build,name in [('native-build-2','sniffles-vcf-development.jar'),
                       ('native-build-25-2','sniffles-vcf-javac25-development.jar')]:
        proof=json.loads((BASE/build/'COMPLETED.json').read_bytes())
        assert all(pin(ROOT/n)==v for n,v in proof['source_pins'].items())
        jar=ROOT/proof['artifact']['path']
        payload[name]=jar.read_bytes();artifacts[name]=byte_pin(payload[name])
    index={name:byte_pin(raw) for name,raw in sorted(inventory.items())}
    index_raw=(json.dumps(index,sort_keys=True,indent=2)+'\n').encode()
    blobs={value['sha256']:inventory[name] for name,value in index.items()}
    candidate=BASE/'sealed-native-1'
    assert not candidate.exists();candidate.mkdir()
    for name,raw in payload.items():(candidate/name).write_bytes(raw)
    with zipfile.ZipFile(candidate/'controls.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as opened:
        for name,raw in [('files.json',index_raw),*sorted(('blobs/'+sha,raw) for sha,raw in blobs.items())]:
            info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=0o100644<<16
            opened.writestr(info,raw)
    intake=json.loads((BASE/'source-intake-3/COMPLETED.json').read_bytes())
    proof=dict(status='PASS_SOURCE_BOUND_NATIVE_SNIFFLES_VCF_COMPONENT_ONLY',
        scope='Native complete-record Sniffles 1/2 VCF data access only; original synthetic caller references are separate experiments.',
        implementation_credit=False,SV_calling_adapter_available=False,whole_package_reproduced=False,
        whole_paper_reproduced=False,whole_goal_complete=False,source_pins=source_pins,artifacts=artifacts,
        archive=pin(candidate/'controls.zip'),file_index=byte_pin(index_raw),logical_files=len(index),
        unique_content_blobs=len(blobs),original_editions={tag:r['commit'] for tag,r in intake['editions'].items()},
        original_reference_cases=14,original_reference_records=39,complete_record_comparisons=234,
        format_controls=246,public_CLI_failure_controls=24,
        compiler_cases=[['native-build-2','native-validation-1','sniffles-vcf-development.jar'],
                        ['native-build-25-2','native-validation-25-1','sniffles-vcf-javac25-development.jar']],
        compiler_byte_differences=['org/jlinalg/cli/SnifflesVcfCli.class'],
        canonical_executable_unchanged=canonical,catalog_unchanged_at_checkpoint=census['catalog_before'],
        historical_catalog_snapshot_only=True,source_editions_are_not_author_binaries=True,
        verification_scope='Integrity and archived consistency only; no independent SV refit, authenticity or biological certification.')
    save(candidate/'VALIDATION.json',proof)
    verify(False,candidate)
    HOME.mkdir()
    for path in candidate.iterdir():
        assert path.is_file()
        (HOME/path.name).write_bytes(path.read_bytes())
    verify(False,HOME)
    print('Sealed native Sniffles VCF checkpoint; no calling-adapter or catalog credit')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete component sealing checks')
    main()
