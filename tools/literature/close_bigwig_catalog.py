# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze only the native bigWig catalog credit and preserve every unrelated source role."""
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path, PurePosixPath
import stat
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from catalog_source_integrity import verify_source, reverse_delta
from catalog_fixed_cohort import verify_fixed_cohort
from bigwig_catalog import verify


def read(path):
    return json.loads(path.read_bytes())


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def main():
    base = ROOT / 'build/literature-review/bigwig-native/workflow-development-1'
    before = base / 'catalog-promotion-1'
    data = ROOT / 'docs/literature/omics-2023-2026'
    target = data / 'bigwig-catalog-validation.json'
    archive = data / 'bigwig-catalog-support.zip'
    assert not target.exists() and not archive.exists()
    proof = verify(ROOT, data)
    papers, inventory, summary = (read(data / n) for n in ['papers.json', 'tool-inventory.json', 'summary.json'])
    assert len(papers) == 250 and len(inventory) == 387 and sum(len(p['tools']) for p in papers) == 2442
    verify_fixed_cohort(ROOT, data, papers)
    counts = dict(Counter(t['implementation_status'] for t in inventory))
    assert counts == summary['implementation_status_counts'] == {
        'native-subset-validated': 16, 'upstream-adapter-validated': 51, 'existing-adapter': 3,
        'missing': 260, 'excluded-by-user': 1, 'method-overlap': 48, 'existing-lookup': 7, 'existing-export': 1}
    old, new = ({t['id']: t for t in rows} for rows in [read(before / 'before-tool-inventory.json'), inventory])
    assert set(old) == set(new) and [name for name in old if old[name] != new[name]] == ['ucsc-bedgraphtobigwig']
    tool = new['ucsc-bedgraphtobigwig']
    assert old['ucsc-bedgraphtobigwig']['implementation_status'] == 'missing'
    assert tool['implementation_status'] == 'native-subset-validated' and tool['project_native_available']
    assert not tool['canonical_adapter_available'] and tool['development_candidate_sha256'] == proof['artifact']['sha256']

    roles = read(data / 'bigwig-role-coverage.json')
    reviews = read(ROOT / 'tools/literature/reviewed-uses.json')
    saved_reviews_raw = gzip.decompress((base / 'role-apply-1/before-reviewed-uses.json.gz').read_bytes())
    saved_reviews = json.loads(saved_reviews_raw)
    assert reviews['reviews'][:815] == saved_reviews['reviews'] and len(reviews['reviews']) == 819
    assert {k: v for k, v in reviews.items() if k != 'reviews'} == {k: v for k, v in saved_reviews.items() if k != 'reviews'}
    use_delta = read(data / 'bigwig-use-shared-source-compatibility.json')
    assert len(use_delta['paths']) == 1 and reverse_delta((ROOT / 'tools/literature/reviewed-uses.json').read_bytes(), use_delta['paths'][0]) == saved_reviews_raw
    assert sum(len(r.get('supporting_evidence', [])) for r in reviews['reviews']) == roles['supporting_pointers'] == 248
    old_papers = json.loads(gzip.decompress((base / 'role-apply-1/before-papers.json.gz').read_bytes()))
    changed_roles, preserved_roles = [], 0
    for a, b in zip(old_papers, papers):
        assert {k: v for k, v in a.items() if k != 'tools'} == {k: v for k, v in b.items() if k != 'tools'}
        assert [t['tool_id'] for t in a['tools']] == [t['tool_id'] for t in b['tools']]
        for x, y in zip(a['tools'], b['tools']):
            if x != y:
                assert x['tool_id'] == 'ucsc-bedgraphtobigwig'
                changed_roles.append(b['paper_id'])
            else:
                preserved_roles += 1
    assert changed_roles == ['P013', 'P050', 'P110', 'P138'] and preserved_roles == 2438
    assert roles['reviewed_roles'] == 5 and roles['new_reviews'] == 4 and roles['existing_role_P163_unchanged']

    focused = read(base / 'catalog-focused-ui-1/ui-validation.json')
    broad = read(base / 'catalog-broad-ui-1/ui-validation.json')
    assert focused['status'] == broad['status'] == 'passed' and len(focused['checks']) == 5 and len(broad['checks']) == 299
    assert 'BUILD SUCCESSFUL' in (before / 'checkWebsite.stdout').read_text() and not (before / 'checkWebsite.stderr').read_bytes()
    for name in ['catalog-focused-ui-1', 'catalog-broad-ui-1']:
        assert not (base / (name + '.stderr')).read_bytes()
    delta = read(data / 'bigwig-catalog-shared-source-compatibility.json')
    assert len(delta['paths']) == 1 and len(delta['paths'][0]['edits']) == 6
    assert reverse_delta((ROOT / 'tools/literature/render_review.py').read_bytes(), delta['paths'][0]) == (before / 'before-render_review.py').read_bytes()
    dispatch = read(data / 'bigwig-dispatch-shared-source-compatibility.json')
    assert len(dispatch['paths']) == 1 and len(dispatch['paths'][0]['edits']) == 2
    assert reverse_delta((ROOT / 'src/main/java/org/jlinalg/cli/JLinAlgCli.java').read_bytes(), dispatch['paths'][0]) == (base / 'before-JLinAlgCli.java').read_bytes()
    prior = read(data / 'tomtom-catalog-validation.json')
    unchanged = ['tomtom_catalog.py', 'nextflow_current_catalog.py', 'salmon_native_current_catalog.py',
                 'salmon_current_catalog.py', 'check_review_ui.cjs', 'catalog_source_integrity.py', 'catalog_fixed_cohort.py']
    assert all(pin(ROOT / ('tools/literature/' + name)) == prior['source_pins']['tools/literature/' + name] for name in unchanged)
    rejected = []
    for name in ['src/main/java/org/jlinalg/genetics/BigWigWriter.java', 'src/main/java/org/jlinalg/cli/BigWigText.java',
                 'src/main/java/org/jlinalg/cli/BigWigWorkflow.java']:
        try:
            verify_source(ROOT, data, name, '0' * 64)
        except ValueError:
            rejected.append(name)
        else:
            raise AssertionError('Incorrect scientific source hash accepted: ' + name)

    source_names = ['tools/literature/' + n for n in [
        'close_bigwig_catalog.py', 'bigwig_catalog.py', 'bigwig_catalog_bridge.py', 'bigwig_dispatch_bridge.py',
        'check_bigwig_review_ui.cjs', 'render_review.py', 'check_review_ui.cjs', 'catalog_source_integrity.py',
        'catalog_fixed_cohort.py', 'curate_bigwig_uses.py', 'bigwig-use-reviews.json', 'audit_bigwig_paper_uses.py',
        'tomtom_catalog.py', 'nextflow_current_catalog.py', 'salmon_native_current_catalog.py', 'salmon_current_catalog.py']]
    sources = {name: pin(ROOT / name) for name in source_names}
    entries = {}

    def add(path, name):
        member = PurePosixPath(name)
        assert path.is_file() and not path.is_symlink() and name not in entries
        assert not member.is_absolute() and '..' not in member.parts and '\\' not in name
        entries[name] = path

    def tree(folder, prefix):
        assert folder.is_dir() and not folder.is_symlink()
        for path in sorted(folder.rglob('*')):
            assert not path.is_symlink()
            if path.is_file():
                add(path, prefix + '/' + path.relative_to(folder).as_posix())

    for name in sources:
        add(ROOT / name, 'source/' + name)
    for name in ['catalog-promotion-1', 'catalog-focused-ui-1', 'catalog-broad-ui-1', 'role-dry-run-1', 'role-apply-1']:
        tree(base / name, 'evidence/' + name)
    tree(ROOT / 'build/literature-review/bigwig-native/primary-use-audit-1', 'evidence/primary-use-audit-1')
    for name in ['catalog-focused-ui-1.stdout', 'catalog-focused-ui-1.stderr', 'catalog-broad-ui-1.stdout', 'catalog-broad-ui-1.stderr', 'before-JLinAlgCli.java']:
        add(base / name, 'evidence/' + name)
    for name in ['summary.json', 'papers.json', 'tool-inventory.json', 'catalog-enrichment.json', 'WARNINGS.md',
                 'bigwig-role-coverage.json', 'bigwig-catalog-shared-source-compatibility.json',
                 'bigwig-dispatch-shared-source-compatibility.json', 'bigwig-use-shared-source-compatibility.json']:
        add(data / name, 'catalog-snapshot/' + name)
    for name in ['literature-review.html', 'literature-warnings.html']:
        add(ROOT / 'site' / name, 'local-site/' + name)
    workflow = ROOT / 'docs/literature/bigwig-typed-workflow'
    native = ROOT / 'docs/literature/bigwig-native-development'
    for folder, prefix in [(workflow, 'workflow-checkpoint'), (native, 'native-checkpoint')]:
        for name in ['VALIDATION.json', 'SHA256SUMS']:
            add(folder / name, prefix + '/' + name)
    members = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, path in sorted(entries.items()):
            raw = path.read_bytes()
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (stat.S_IFREG | 0o644) << 16
            z.writestr(info, raw)
            members[name] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.infolist()) == len(set(z.namelist())) == len(members)
        for name, value in members.items():
            raw = z.read(name)
            assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == value
    assert archive.stat().st_size < 95000000 and all(pin(ROOT / name) == value for name, value in sources.items())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == proof['canonical_executable_unchanged']
    result = dict(schema_version=1, status='passed-scoped-native-bigWig-catalog-and-browser-closure',
                  source_pins=sources, archive=dict(path=archive.name, **pin(archive), entries=len(members)),
                  archive_files=members, workflow_validation=pin(workflow / 'VALIDATION.json'),
                  workflow_archive=pin(workflow / 'controls.zip'), native_validation=pin(native / 'VALIDATION.json'),
                  native_archive=pin(native / 'controls.zip'), artifact=proof['artifact'],
                  canonical_executable_unchanged=proof['canonical_executable_unchanged'],
                  catalog_counts=dict(papers=250, tools=387, observations=2442, reviewed_uses=819,
                                      supporting_pointers=248, new_scoped_integrations=67, including_existing=70, missing=260),
                  preserved_unrelated_tool_objects=386, preserved_prior_reviews=815, preserved_paper_tool_objects=2438,
                  reviewed_converter_roles=5, changed_converter_roles=4, existing_P163_unchanged=True,
                  focused_browser_checks=5, broad_browser_checks=299, website_check_passed=True,
                  scientific_source_hash_rejections=rejected, generic_scientific_source_waivers_added=False,
                  prior_provider_files_changed=False, new_scoped_native_implementation_credit=True,
                  whole_paper_reproduced=False, biological_accuracy_certified=False,
                  released=False, pushed=False, deployed=False, whole_goal_complete=False,
                  required_action='Use the exact development bigWig candidate and examples/bigwig/README.md; older canonical/released JARs lack bigwig-tools. Declare local build/units, float32 exact|round, dictionary used|all and compression zlib|none. Read every precision/reference ledger and warning. Native double summaries and absent zoom approximations differ from original UCSC output; check the intended consumer. No UCSC/Python runtime installation is required. Forbidden required access means SKIP3 and skip dependent conversion; input/precision/integrity/capacity/I/O/cancellation/finalization failures mean FAIL2 with no admitted result. Preserve partials, correct the cause and use NEW paths. Require overall COMPLETED.json, no INCOMPLETE.json/FAILURE.json and successful complete --verify. Capacities are not JVM heap guarantees. Literal P110 v4 remains release-versus-format ambiguous. Hashes prove integrity, not authenticity or biology. Cell Ranger remains excluded;260 integrations remain missing.')
    target.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    manifest = {p.name: pin(p)['sha256'] for p in sorted(data.iterdir()) if p.is_file() and p.name != 'SHA256SUMS'}
    (data / 'SHA256SUMS').write_text(''.join(value + '  ' + name + '\n' for name, value in manifest.items()), encoding='ascii', newline='\n')
    assert all(pin(data / name)['sha256'] == value for name, value in manifest.items())
    print(json.dumps({k: result[k] for k in ['status', 'catalog_counts', 'preserved_unrelated_tool_objects',
                                          'focused_browser_checks', 'broad_browser_checks', 'archive', 'artifact']}))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable scoped native bigWig catalog closure checks')
    main()
