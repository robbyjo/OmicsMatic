# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain final Salmon workflow catalog/browser gates without rewriting any earlier checkpoint."""
import gzip
import hashlib
import json
from pathlib import Path
import stat
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'build/literature-review/salmon-runtime'
sys.path.insert(0, str(ROOT / 'tools/literature'))
from salmon_workflow_catalog import verify
from catalog_source_integrity import verify_source


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())


def read(path):
    return json.loads(path.read_bytes())


def main():
    if not __debug__:
        raise RuntimeError('Never disable scoped catalog closure checks')
    data = ROOT / 'docs/literature/omics-2023-2026'
    target = data / 'salmon-workflow-catalog-validation.json'; archive = data / 'salmon-workflow-catalog-support.zip'
    assert not target.exists() and not archive.exists()
    proof = verify(ROOT, data)
    summary = read(data / 'summary.json'); counts = summary['implementation_status_counts']
    assert (summary['papers'], summary['distinct_named_tools'], summary['paper_tool_observations']) == (250, 380, 2430)
    assert (counts['native-subset-validated'], counts['upstream-adapter-validated'], counts['existing-adapter'], counts['missing'], counts['excluded-by-user']) == (15, 50, 3, 255, 1)
    before_area = BASE / 'catalog-promotion-1'
    tools = read(data / 'tool-inventory.json'); before = json.loads(gzip.decompress((before_area / 'before-tool-inventory.json.gz').read_bytes()))
    assert len(tools) == len(before) == 380 and [a['id'] for a, b in zip(tools, before) if a != b] == ['salmon']
    salmon = next(t for t in tools if t['id'] == 'salmon')
    assert salmon['project_adapter_available'] and salmon['public_quantification_CLI_available'] and not salmon['canonical_adapter_available'] and salmon['source_roles_reviewed'] == 8
    old_papers = json.loads(gzip.decompress((before_area / 'before-papers.json.gz').read_bytes())); papers = read(data / 'papers.json')
    assert len(old_papers) == len(papers) == 250 and sum(len(p['tools']) for p in papers) == 2430
    for old, new in zip(old_papers, papers):
        assert {k: v for k, v in old.items() if k != 'tools'} == {k: v for k, v in new.items() if k != 'tools'}
        assert [t for t in old['tools'] if t['tool_id'] != 'salmon'] == [t for t in new['tools'] if t['tool_id'] != 'salmon']
    reviews = read(ROOT / 'tools/literature/reviewed-uses.json')['reviews']
    assert len(reviews) == 793 and sum(len(r.get('supporting_evidence', [])) for r in reviews) == 241
    focused_area, broad_area = BASE / 'workflow-ui-2', BASE / 'workflow-broad-ui-1'
    focused, broad = read(focused_area / 'ui-validation.json'), read(broad_area / 'ui-validation.json')
    assert focused['status'] == broad['status'] == 'passed' and len(focused['checks']) == 3 and len(broad['checks']) == 299
    assert min(focused['warning_contrast'].values()) >= 4.5
    rejected = []
    for name in ['src/main/java/org/jlinalg/cli/SalmonQuantWorkflow.java', 'src/main/java/org/jlinalg/sequence/SalmonQuantification.java']:
        try:
            verify_source(ROOT, data, name, '0' * 64)
        except ValueError:
            rejected.append(name)
        else:
            raise AssertionError('Incorrect scientific/controller source hash accepted: ' + name)
    source_names = [Path(__file__).relative_to(ROOT).as_posix(), 'tools/literature/salmon_workflow_catalog.py', 'tools/literature/promote_salmon_workflow_catalog.py',
                    'tools/literature/check_salmon_workflow_review_ui.cjs', 'tools/literature/check_review_ui.cjs', 'tools/literature/render_review.py']
    sources = {name: pin(ROOT / name) for name in source_names}
    entries = {}

    def add(path, name):
        assert path.is_file() and not path.is_symlink() and name not in entries
        entries[name] = path

    for name in sources:
        add(ROOT / name, 'source/' + name)
    for label, area in [('focused', focused_area), ('broad', broad_area), ('historical-contrast-rejected', BASE / 'workflow-ui-1')]:
        for path in sorted(area.rglob('*')):
            if path.is_file():
                add(path, 'browser/' + label + '/' + path.relative_to(area).as_posix())
    for name in ['salmon-dispatch-shared-source-compatibility.json', 'salmon-workflow-shared-source-compatibility.json', 'salmon-role-coverage.json', 'summary.json', 'tool-inventory.json', 'papers.json']:
        add(data / name, 'catalog-snapshot/' + name)
    census = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, path in sorted(entries.items()):
            raw = path.read_bytes(); item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); item.compress_type = zipfile.ZIP_DEFLATED; item.external_attr = (stat.S_IFREG | 0o644) << 16
            z.writestr(item, raw); census[name] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert len(z.infolist()) == len(set(z.namelist())) == len(census) and z.testzip() is None
        for name, expected in census.items():
            raw = z.read(name); assert dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == expected
    assert archive.stat().st_size < 95000000 and all(pin(ROOT / name) == value for name, value in sources.items())
    result = dict(schema_version=1, status='passed-Salmon-bulk-workflow-catalog-role-and-browser-gates',
        workflow_checkpoint='../salmon-bulk-workflow/VALIDATION.json', workflow_checkpoint_pin=pin(ROOT / 'docs/literature/salmon-bulk-workflow/VALIDATION.json'),
        source_pins=sources, archive=dict(path=archive.name, **pin(archive), entries=len(census)), archive_files=census,
        catalog_counts=dict(papers=250, tools=380, observations=2430, reviewed_uses=793, supporting_pointers=241, new_scoped_integrations=65, native=15, upstream=50, including_existing_adapters=68, missing=255),
        preserved=dict(unrelated_tool_objects=379, unrelated_observations=2422, paper_cohort=True, all_reviewed_uses=True), focused_browser_checks=3, broad_browser_checks=299,
        browser_screenshots=len(focused['screenshots']) + len(broad['screenshots']), warning_contrast=focused['warning_contrast'], historical_contrast_failure_retained=True,
        source_hash_rejections=rejected, canonical_executable_unchanged=proof['canonical_executable_unchanged'],
        source_adapter_available=True, canonical_adapter_available=False, released=False, pushed=False, deployed=False, whole_goal_complete=False, biological_accuracy_certified=False,
        required_action=proof['required_action'])
    target.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8', newline='\n')
    # The displayed catalog is already final. Register the two new closure
    # files without needlessly regenerating unchanged pages and snapshots.
    manifest = {path.name: pin(path)['sha256'] for path in sorted(data.iterdir()) if path.is_file() and path.name != 'SHA256SUMS'}
    previous = (data / 'SHA256SUMS').read_text(encoding='utf-8').splitlines()
    order = [line.split('  ', 1)[1] for line in previous if line.split('  ', 1)[1] in manifest]
    assert len(order) == len(set(order))
    order += [name for name in manifest if name not in set(order)]
    (data / 'SHA256SUMS').write_text(''.join(manifest[name] + '  ' + name + '\n' for name in order), encoding='utf-8', newline='\n')
    assert all(pin(data / name)['sha256'] == expected for name, expected in manifest.items())
    print(json.dumps({k: result[k] for k in ['status', 'catalog_counts', 'preserved', 'focused_browser_checks', 'broad_browser_checks']}))


if __name__ == '__main__':
    main()
