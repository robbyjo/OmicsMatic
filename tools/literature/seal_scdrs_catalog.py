# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Seal scoped scDRS catalog admission, complete primary audit and unchanged predecessor scientific gates."""
import argparse
import hashlib
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE
from signac_scan_evidence import pack
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta, verify_source
from verify_scdrs_catalog import DATA, STATUS, verify


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    out = p.parse_args().out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    required = {
        'catalog-render-4': 'PASS_SCDRS_CATALOG_RENDER401_OTHER_TOOL_OBJECTS_UNCHANGED',
        'catalog-source-audit-3': 'PASS250_PRIMARY_SOURCES_2476_OBSERVATIONS_1049_REVIEWS_AND_SIX_SCDRS_ROLES',
        'catalog-shared-gates-2': 'PASS57_SCDRS_AND55_UNCHANGED_PREDECESSOR_SCIENTIFIC_GATES',
        'catalog-ui-4': 'PASS299_GENERAL18_GENESIS11_MTAG12_SCDRS_BROWSER_CHECKS',
        'catalog-visual-review-1': 'PASS_SCDRS_DESKTOP_AND_MOBILE_BROWSER_ARTIFACTS_VISUALLY_REVIEWED',
        'catalog-contracts-2': 'PASS_SCDRS_METADATA_IDEMPOTENCY_AND_PUBLISHED_TYPED_REQUEST_THREE_JVMS'}
    for n, status in required.items(): assert json.loads((BASE / n / 'COMPLETED.json').read_bytes())['status'] == status, n
    assert not any((DATA / n).exists() for n in ['scdrs-catalog-validation.json', 'scdrs-catalog-support.zip'])
    derivation = json.loads((BASE / 'catalog-validation-derivation.json').read_bytes())
    for row in derivation['records'][:2]:
        raw = (ROOT / row['before']).read_bytes()
        assert hashlib.sha256(raw).hexdigest() == row['before_sha256']
        text = raw.decode()
        for edit in row['edits']:
            assert text.count(edit['before']) == edit['occurrences']
            text = text.replace(edit['before'], edit['after'])
        assert text.encode() == (ROOT / row['after']).read_bytes()
        assert hashlib.sha256(text.encode()).hexdigest() == row['after_sha256']
    # Existing global byte-preservation rules already cover tools/literature.
    # Add only the new standalone JSON example, with an exact append-only inverse.
    attrs = ROOT / '.gitattributes'
    before = attrs.read_bytes()
    after = before + b'\n# Preserve the scoped scDRS typed request example as evidence bytes.\ndocs/literature/scdrs-request-example.json -text whitespace=cr-at-eol\n'
    delta = inverse(before, after)
    delta['path'] = '.gitattributes'
    assert reverse_delta(after, delta) == before
    descriptor = DATA / 'scdrs-catalog-sourcebytes-shared-source-compatibility.json'
    assert not descriptor.exists()
    save(descriptor, dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='scdrs', paths=[delta],
        scope='Append-only exact-byte attribute for the new scDRS JSON request. No scientific source or source-allowlist changes.'))
    (out / 'gitattributes.before').write_bytes(before)
    attrs.write_bytes(after)
    verify_source(ROOT, DATA, '.gitattributes', delta['before_sha256'])
    metadata = ['scdrs_catalog.py', 'wire_scdrs_dispatch.py', 'wire_scdrs_catalog.py', 'wire_scdrs_legacy_metadata.py',
        'refresh_scdrs_catalog_metadata.py', 'render_scdrs_catalog.py', 'audit_scdrs_catalog.py', 'run_scdrs_catalog_checks.py',
        'validate_scdrs_catalog_ui.py', 'validate_scdrs_catalog_contracts.py', 'verify_scdrs_catalog.py', 'seal_scdrs_catalog.py',
        'check_scdrs_catalog_ui.cjs', 'check_scdrs_general_review_ui.cjs', 'check_scdrs_genesis_review_ui.cjs', 'check_scdrs_mtag_review_ui.cjs',
        'curate_scdrs_uses.py', 'scdrs_registry.py', 'catalog_source_integrity.py', 'verify_mtag_catalog.py',
        'check_snprelate_general_review_ui.cjs', 'check_mtag_genesis_review_ui.cjs', 'check_mtag_catalog_ui.cjs', 'check_genesis_catalog_ui.cjs', 'check_cistarget_general_review_ui.cjs']
    sources = [ROOT / 'tools/literature' / n for n in metadata] + [ROOT / n for n in ['.gitattributes',
        'src/main/java/org/jlinalg/cli/JLinAlgCli.java', 'tools/literature/render_review.py', 'tools/literature/reviewed-uses.json',
        'docs/literature/SCDRS_INTEGRATION.md', 'docs/literature/scdrs-request-example.json']]
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in sources}
    files = {}
    def add(name, path):
        assert name not in files and path.is_file() and not path.is_symlink()
        files[name] = path.read_bytes()
    for n in source_pins: add('current-source/' + n, ROOT / n)
    folders = [*required, 'paper-roles-applied-1', 'dispatch-live-1', 'catalog-wire-1', 'catalog-legacy-view-1',
        'catalog-render-1', 'catalog-render-2', 'catalog-render-3', 'catalog-source-audit-1', 'catalog-source-audit-2',
        'catalog-ui-1', 'catalog-ui-2', 'catalog-ui-3', 'catalog-shared-gates-1', 'catalog-contracts-1', 'card-display-refinement-1']
    for name in folders:
        for path in sorted((BASE / name).rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts: add(path.relative_to(ROOT).as_posix(), path)
    for name in ['derive_catalog_validation.py', 'update_validation_derivation.py', 'finalize_validation_derivation.py', 'update_browser_scheduling_derivation.py', 'catalog-validation-derivation.json']:
        add((BASE / name).relative_to(ROOT).as_posix(), BASE / name)
    snapshots = ['papers.json', 'tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md', 'catalog.md', 'implementation-plan.md']
    snapshots += [p.name for p in sorted(DATA.glob('scdrs-*.json'))] + [p.name for p in sorted(DATA.glob('scdrs-*.gz'))]
    assert len(snapshots) == len(set(snapshots))
    snapshot = {n: pin(DATA / n) for n in snapshots}
    for n in snapshots: add('catalog-snapshot/' + n, DATA / n)
    for n in ['tool-inventory.json', 'summary.json', 'README.md', 'WARNINGS.md']:
        add('catalog-before/' + n, BASE / 'catalog-render-1' / (n + '.before'))
    pages = {n: pin(ROOT / n) for n in ['site/literature-review.html', 'site/literature-warnings.html']}
    for n in pages: add('pages/' + n, ROOT / n)
    record = pack(DATA / 'scdrs-catalog-support.zip', files)
    assert (DATA / 'scdrs-catalog-support.zip').stat().st_size < 95_000_000
    components = {}
    for folder in ['scdrs-native-development', 'scdrs-workflow-development']:
        for path in sorted((ROOT / 'docs/literature' / folder).iterdir()):
            if path.is_file(): components[path.relative_to(ROOT).as_posix()] = pin(path)
    previous = json.loads((ROOT / 'build/literature-review/push-checkpoint-2026-11-10-scdrs-workflow-1/selection.json').read_bytes())
    assert all(pin(ROOT / n) == v for n, v in previous['preserved'].items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == previous['canonical']
    proof = dict(schema_version=1, status=STATUS, new_catalog_credit=True, new_tool_id='scdrs',
        new_scoped_integrations=99, including_existing=102, missing=244, papers=250, observed_tools=402,
        observations=2476, reviewed_roles=1049, supporting_pointers=375, source_roles_reviewed=6,
        whole_package_implemented=False, whole_paper_reproduced=False, whole_goal_complete=False,
        predecessor_scientific_verifiers_changed=False, scientific_source_allowlist_changed=False,
        source_pins=source_pins, component_files=components, catalog_snapshot=snapshot, page_pins=pages,
        canonical_executable_unchanged=previous['canonical'], unrelated_files_preserved=18, analysis_installs_software=False,
        participant_data_distributed=False, primary_article_prose_distributed=False,
        scope='One scoped original scDRS1.0.0/1.0.1/1.0.2 adapter with exact private runtime setup and explicit inputs. Separate native numerical components and BH are validated; original fitting, scoring and graphs remain upstream. Six primary roles include the previously missed P169 plural. Unreported settings, author commits, biological validity, atlas performance and full-paper reproduction are not inferred. All401 other tool objects,1043 earlier reviews and2470 unrelated observations are preserved; the full250-paper goal remains open.', **record)
    proof['archive'] = pin(DATA / 'scdrs-catalog-support.zip')
    save(DATA / 'scdrs-catalog-validation.json', proof)
    (DATA / 'SHA256SUMS').write_text(''.join(pin(p)['sha256'] + '  ' + p.name + '\n' for p in sorted(DATA.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'), encoding='utf8', newline='\n')
    verify()
    save(out / 'COMPLETED.json', dict(status=STATUS, validation=pin(DATA / 'scdrs-catalog-validation.json'), archive=proof['archive'], whole_goal_complete=False))
    print(STATUS, proof['archive']['bytes'], flush=True)


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable full source, role, scientific, browser or unrelated-work preservation checks')
    main()
