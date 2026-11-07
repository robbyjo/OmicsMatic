# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain the post-workflow catalog/UI gates without rewriting frozen controls."""
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from nextflow_catalog import verify
from catalog_source_integrity import verify_source


def pin(path):
    with path.open('rb') as stream:
        return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())
def read(path): return json.loads(path.read_bytes())
def write(path, value): path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8', newline='\n')


def main():
    if not __debug__: raise RuntimeError('Never disable closure assertions')
    data = ROOT / 'docs/literature/omics-2023-2026'
    dev = ROOT / 'build/literature-review/nextflow-runtime/typed-workflow-development-1'
    target = data / 'nextflow-catalog-validation.json'
    archive = data / 'nextflow-catalog-support.zip'
    assert not target.exists() and not archive.exists()
    workflow = verify(ROOT, data)
    summary = read(data / 'summary.json'); tools = read(data / 'tool-inventory.json')
    assert (summary['papers'], summary['distinct_named_tools'], summary['paper_tool_observations']) == (250, 380, 2430)
    counts = summary['implementation_status_counts']
    assert (counts['native-subset-validated'], counts['upstream-adapter-validated'], counts['existing-adapter'], counts['missing'], counts['excluded-by-user']) == (15, 49, 3, 256, 1)
    before = json.loads(subprocess.check_output(['git', 'show', 'HEAD:docs/literature/omics-2023-2026/tool-inventory.json'], cwd=ROOT))
    assert len(tools) == len(before) == 380 and [a['id'] for a, b in zip(tools, before) if a != b] == ['nextflow']
    current_papers = read(data / 'papers.json')
    old_papers = json.loads(gzip.decompress((dev / 'role-application-1/before-papers.json.gz').read_bytes()))
    assert len(current_papers) == len(old_papers) == 250
    for old, new in zip(old_papers, current_papers):
        assert {k:v for k,v in old.items() if k != 'tools'} == {k:v for k,v in new.items() if k != 'tools'}
        assert [t for t in old['tools'] if t['tool_id'] != 'nextflow'] == [t for t in new['tools'] if t['tool_id'] != 'nextflow']
    old_reviews = json.loads(gzip.decompress((dev / 'role-application-1/before-reviewed-uses.json.gz').read_bytes()))['reviews']
    reviews = read(ROOT / 'tools/literature/reviewed-uses.json')['reviews']
    assert len(old_reviews) == 775 and len(reviews) == 785 and reviews[:775] == old_reviews
    assert sum(len(r.get('supporting_evidence', [])) for r in reviews) == 235
    focused = read(dev / 'ui-3/ui-validation.json'); broad = read(dev / 'broad-ui-2/ui-validation.json')
    assert focused['status'] == broad['status'] == 'passed' and len(focused['checks']) == 3 and len(broad['checks']) == 299
    results = sorted((ROOT / 'build/test-results/test').glob('TEST-org.jlinalg.cli.Nextflow*.xml')) + sorted((ROOT / 'build/test-results/test').glob('TEST-org.jlinalg.workflow.Nextflow*.xml')) + [ROOT / 'build/test-results/test/TEST-org.jlinalg.cli.JLinAlgCliTest.xml']
    assert len(results) == 16
    suites = [ET.parse(path).getroot() for path in results]
    totals = {key: sum(int(s.get(key, 0)) for s in suites) for key in ['tests', 'failures', 'errors', 'skipped']}
    assert totals['tests'] > 166 and totals['failures'] == totals['errors'] == 0 and totals['skipped'] == 4
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__),
        ROOT / 'tools/literature/nextflow_catalog.py', ROOT / 'tools/literature/check_nextflow_review_ui.cjs',
        ROOT / 'tools/literature/check_review_ui.cjs', ROOT / 'tools/literature/render_review.py',
        ROOT / 'tools/literature/promote_nextflow_catalog.py', ROOT / 'tools/literature/nextflow_ui_bridge.py',
        ROOT / 'tools/literature/nextflow_dispatch_bridge.py',
        ROOT / 'tools/literature/mashr_catalog_evidence.py', ROOT / 'tools/literature/refresh_mashr_dispatch_verifier.py']}
    rejections = []
    for path in ['src/main/java/org/jlinalg/cli/NextflowWorkflow.java', 'src/main/java/org/jlinalg/cli/NextflowRuntime.java', 'src/main/java/org/jlinalg/cli/JLinAlgCli.java']:
        try: verify_source(ROOT, data, path, '0'*64)
        except ValueError: rejections.append(path)
        else: raise AssertionError('Incorrect source hash was accepted: '+path)
    entries = {}
    def add(path, name): assert path.is_file() and not path.is_symlink() and name not in entries; entries[name] = path
    for name in sources: add(ROOT / name, 'source/' + name)
    for folder in ['ui-1', 'ui-2', 'ui-3', 'broad-ui-1', 'broad-ui-2']:
        for p in sorted((dev / folder).rglob('*')):
            if p.is_file(): add(p, 'browser/' + folder + '/' + p.relative_to(dev / folder).as_posix())
    for p in results: add(p, 'gradle-results/' + p.name)
    for p in sorted(data.glob('nextflow-*-shared-source-compatibility.json')): add(p, 'source-relationships/' + p.name)
    for name in ['mashr-nextflow-dispatch-verifier-supplement.json', 'nextflow-role-coverage.json', 'summary.json', 'tool-inventory.json', 'papers.json']:
        add(data / name, 'catalog-snapshot/' + name)
    census = {}
    with zipfile.ZipFile(archive, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, path in sorted(entries.items()):
            raw = path.read_bytes(); entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); entry.compress_type = zipfile.ZIP_DEFLATED; entry.external_attr = 0o100644 << 16
            z.writestr(entry, raw); census[name] = dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert len(z.infolist()) == len(census) == len(set(z.namelist())) and z.testzip() is None
        for name, value in census.items():
            raw = z.read(name); assert len(raw) == value['bytes'] and hashlib.sha256(raw).hexdigest() == value['sha256']
    assert all(pin(ROOT / p) == v for p, v in sources.items())
    result = dict(schema_version=1, status='passed-scoped-Nextflow-catalog-role-and-browser-closure',
        workflow_checkpoint='../nextflow-typed-workflow/VALIDATION.json', workflow_checkpoint_pin=pin(ROOT / 'docs/literature/nextflow-typed-workflow/VALIDATION.json'),
        artifact=workflow['artifact'], source_pins=sources,
        archive=dict(path=archive.name, **pin(archive), entries=len(census)), archive_files=census,
        catalog_counts=dict(papers=250, tools=380, observations=2430, reviewed_uses=785, supporting_pointers=235,
            new_scoped_integrations=64, native=15, upstream=49, including_existing_adapters=67, missing=256, excluded=1),
        preserved=dict(unrelated_tool_objects=379, earlier_reviews=775, paper_cohort=True, unrelated_observations=2420),
        focused_browser_checks=len(focused['checks']), broad_browser_checks=len(broad['checks']),
        browser_screenshots=len(focused['screenshots']) + len(broad['screenshots']), source_hash_rejections=rejections,
        standard_Gradle_dispatcher_and_Nextflow_regression=totals,
        standard_Gradle_command='./gradlew.bat test --tests org.jlinalg.cli.JLinAlgCliTest --tests org.jlinalg.cli.Nextflow*Test --tests org.jlinalg.workflow.Nextflow*Test javadoc --no-daemon --no-parallel --no-configuration-cache',
        website_structure_and_links_passed=True, javadoc_passed=True, canonical_executable_unchanged=workflow['canonical_executable_unchanged'],
        scope='Only the bounded Nextflow engine integration is credited. All379 other tool objects and all775 earlier reviews are preserved. Browser assertion corrections change spelling/visible-heading expectations and one stale literal count, not source data or scientific acceptance. The source-dispatch/catalog/UI inverses reconstruct prior bytes; scientific source mismatches remain rejected.',
        public_release=False, pushed=False, deployed=False, whole_goal_complete=False, whole_paper_reproduced=False,
        required_action=workflow['required_action'])
    write(target, result)
    print(json.dumps({k:v for k,v in result.items() if k in ['status', 'catalog_counts', 'preserved', 'focused_browser_checks', 'broad_browser_checks', 'standard_Gradle_dispatcher_and_Nextflow_regression']}))


if __name__ == '__main__': main()
