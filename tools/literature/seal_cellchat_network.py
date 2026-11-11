# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze the original network workflow evidence with lossless blob deduplication."""
import argparse, json, shutil, traceback, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from verify_cellchat_network_evidence import verify, fingerprint
BASE = ROOT / 'build/literature-review/cellchat-runtime'
PUBLIC = ROOT / 'docs/literature/cellchat-network-development'
COMPLETE = [n + '/analysis' for n in ['network-controls-javac21-parallel-2',
    'network-controls-javac25-parallel-2', 'network-controls-javac21-sequential-2', 'network-variant-fixed-k-1']]
PORTABLE = [f'network-portable-{vm}-j{co}-2' for co in [21, 25] for vm in ['win21', 'win25', 'linux21']]


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    state = dict(status='FAIL2_NETWORK_EVIDENCE_SEAL', new_catalog_credit=False, whole_goal_complete=False)
    try:
        previous = json.loads((ROOT / 'docs/literature/cellchat-strength-development/VALIDATION.json').read_bytes())
        catalog, canonical = previous['catalog_unchanged'], previous['canonical_executable_unchanged']
        assert all(pin(ROOT/n) == v for n, v in catalog.items())
        assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
        names = ['src/main/java/org/jlinalg/cli/'+n+'.java' for n in ['CellChatNetworkRequest',
            'CellChatNetworkAudit', 'CellChatNetworkCli', 'CellChatLegacyRequest', 'CellChatLegacyTables',
            'CellChatLegacyAudit', 'CellChatLegacyCli', 'NextflowParameters', 'NextflowFiles', 'NextflowJson', 'SimpleJson']]
        names += ['src/main/java/org/jlinalg/omics/'+n+'.java' for n in ['CellChatProjection', 'CellChatNetworkSimilarity']]
        names += ['tools/literature/'+n for n in ['build_cellchat_network.py', 'cellchat_network_workflow.R',
            'setup_cellchat_network_reference.py', 'install_cellchat_network_reference.R',
            'export_cellchat_network_runtime.py', 'inventory_cellchat_network_python.py',
            'reference_cellchat_network.R', 'test_cellchat_network.py', 'check_cellchat_network_portable.py',
            'seal_cellchat_network.py', 'verify_cellchat_network_evidence.py', 'verify_cellchat_strength_evidence.py',
            'setup_deseq2_reference.py', 'setup_chipseeker_reference.py', 'cellchat_legacy_workflow.R',
            'cellchat-legacy-source-lock.json']]
        names += ['src/main/resources/org/jlinalg/omics/cellchat-network/'+n for n in ['worker.R', 'SOURCE-LOCK.json']]
        names += ['docs/literature/CELLCHAT_NETWORK_DEVELOPMENT.md']
        pins = {n: pin(ROOT/n) for n in names}
        files = {}

        def add(name, file):
            assert name not in files
            files[name] = file

        for name in names:
            add('source/'+name, ROOT/name)
        add('LICENSES/GPL-3.0.txt', ROOT / 'LICENSE')
        add('upstream/CELLCHAT_UPSTREAM_NOTICE.txt', ROOT / 'docs/literature/CELLCHAT_UPSTREAM_NOTICE.txt')
        original = BASE / 'source-intake-1/1.1.3'
        members = json.loads((original / 'original-members.json').read_bytes())
        assert all(pin(original/'source'/n) == v for n, v in members.items())
        for file in sorted((original/'source').rglob('*')):
            if file.is_file():
                add('upstream/1.1.3/source/'+file.relative_to(original/'source').as_posix(), file)
        for name in ['original-members.json', 'download.json']:
            add('upstream/1.1.3/'+name, original/name)
        site = ROOT / 'build/omics-cell-tools-env/Lib/site-packages'
        packages = {'umap-learn', 'numpy', 'numba', 'llvmlite', 'scipy', 'scikit-learn'}
        for directory in sorted(site.glob('*.dist-info')):
            if directory.name.split('-')[0].lower().replace('_', '-') in packages:
                for file in sorted(directory.rglob('*')):
                    if file.is_file() and any(t in file.relative_to(directory).as_posix().lower() for t in ['license', 'copying', 'notice']):
                        add('LICENSES/python/'+directory.name+'/'+file.relative_to(directory).as_posix(), file)
        for directory in sorted(BASE.glob('network-*')):
            if not directory.is_dir() or directory.name.startswith(('network-seal-', 'network-materialized-', 'network-primary-scope-audit-')):
                continue
            for file in sorted(directory.rglob('*')):
                if file.is_file() and not any(n in file.relative_to(directory).parts for n in ['R-lib', 'tmp']):
                    add('evidence/'+directory.name+'/'+file.relative_to(directory).as_posix(), file)
        for pattern in ['network-portable-*.stdout', 'network-portable-*.stderr']:
            for file in sorted(BASE.glob(pattern)):
                add('evidence/'+file.name, file)
        jars = {}
        for co in [21, 25]:
            report = json.loads((BASE/f'network-build{co}-5/COMPLETED.json').read_bytes())
            assert report['status'] == 'PASS_ISOLATED_CELLCHAT_NETWORK_WORKFLOW_BUILD_ONLY'
            assert all(pin(ROOT/n) == v for n, v in report['source_pins'].items())
            target = PUBLIC/f'cellchat-network-javac{co}.jar'
            assert not target.exists()
            shutil.copyfile(BASE/f'network-build{co}-5/cellchat-network-development.jar', target)
            jars[target.name] = pin(target)
        logical, blobs, archives = {}, {}, {}
        z, number, logical_bytes = None, 0, 0
        try:
            for name, file in sorted(files.items()):
                assert file.is_file() and not file.is_symlink()
                raw = file.read_bytes()
                value = fingerprint(raw)
                logical[name] = value
                logical_bytes += len(raw)
                digest = value['sha256']
                if digest in blobs:
                    continue
                if z is None or z.fp.tell() > 28000000:
                    if z is not None:
                        z.close()
                        archives[archive.name] = pin(archive)
                    number += 1
                    archive = PUBLIC/f'controls-{number:02d}.zip'
                    assert not archive.exists()
                    z = zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9)
                info = zipfile.ZipInfo('blobs/'+digest, (1980, 1, 1, 0, 0, 0))
                info.compress_type, info.external_attr = zipfile.ZIP_DEFLATED, 0o100644 << 16
                z.writestr(info, raw)
                blobs[digest] = dict(archive=archive.name, **value)
        finally:
            if z is not None:
                z.close()
                archives[archive.name] = pin(archive)
        assert all(v['bytes'] < 90000000 for v in archives.values())
        examples = {p.relative_to(PUBLIC).as_posix(): pin(p) for p in PUBLIC.rglob('*') if p.is_file()
                    and ('example' in p.relative_to(PUBLIC).parts or p.name == 'P085-network-source-addendum.json')}
        proof = dict(status='PASS_ORIGINAL_CELLCHAT_NETWORK_DEVELOPMENT_ONLY', new_catalog_credit=False,
            whole_goal_complete=False, all_cellchat_roles_completed=False, new_scoped_integrations=93,
            including_existing_adapters=96, missing=248, source_pins=pins, catalog_snapshot=catalog,
            canonical_executable_unchanged=canonical, development_jars=jars, example_and_addendum_files=examples,
            archives=archives, blobs=blobs, members=logical, logical_bytes=logical_bytes,
            portable_suites=PORTABLE, complete_bundles=COMPLETE, historical_paper_reproduced=False,
            private_exclusions=['Installed R/Python libraries and compilers; selected file inventories retained',
                'Primary article text and unlicensed author notebook; source identifiers, hashes and factual role notes retained'])
        assert not (PUBLIC/'VALIDATION.json').exists()
        save(PUBLIC/'VALIDATION.json', proof)
        verify(PUBLIC, True)
        assert all(pin(ROOT/n) == v for n, v in pins.items())
        assert all(pin(ROOT/n) == v for n, v in catalog.items())
        assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar') == canonical
        state.update(status='PASS_FROZEN_ORIGINAL_CELLCHAT_NETWORK_CHECKPOINT', source_pins=pins,
            published_files={p.relative_to(ROOT).as_posix(): pin(p) for p in PUBLIC.rglob('*') if p.is_file()},
            canonical_executable_unchanged=canonical, logical_members=len(logical), unique_blobs=len(blobs),
            archive_bytes=sum(v['bytes'] for v in archives.values()))
    except Exception as error:
        state['error'] = repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable frozen source/evidence checks')
    raise SystemExit(main())
