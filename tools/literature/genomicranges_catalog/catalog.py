"""Plan, inspect, transactionally admit and verify one source-bound provider."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import sys
import traceback
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from genomicranges_catalog import model as m, primary as p
from genomicranges_workflow_qualification.verify_checkpoint import verify as workflow_verify
from seurat_catalog.catalog import clean_manifest, manifest_bytes, install
from qtltools_catalog.catalog import zipped, read_before

ROOT = p.ROOT
BASE = '3c9cc8a71b8adc8eb9d1ff22f915e7bb1dfa999f'
FOLDER = 'docs/literature/genomicranges-catalog-admission'
STATUS = 'PASS_ONE_GENOMICRANGES_UPSTREAM_FAMILY_FIVE_NEW_REVIEWS_TWO_SCOPE_NOTES'
OWN = ['tools/literature/genomicranges_catalog/' + n for n in [
    '__init__.py', 'primary.py', 'decisions.py', 'P094-AUXILIARY.json', 'model.py',
    'catalog.py', 'test_model.py', 'test_transaction.py', 'check_ui.cjs', 'ADMISSION.md']]
OWN += ['tools/literature/audit_genomicranges_sources.py', 'tools/literature/close_genomicranges_catalog.py']


def git(*args):
    return subprocess.check_output(['git', '--no-replace-objects', *args], cwd=ROOT, stdin=subprocess.DEVNULL)


def sources():
    return {n: p.pin((ROOT / n).read_bytes()) for n in OWN}


def baseline(before):
    p.require(set(before) == set(m.META), 'Different predecessor metadata population')
    for name, raw in before.items():
        p.require(raw == git('show', BASE + ':' + name), 'Changed exact predecessor: ' + name)


def qualified(args):
    report = workflow_verify(args.jar)
    q = json.loads((ROOT / 'docs/literature/genomicranges-qualification/QUALIFICATION.json').read_bytes())
    source_audit = False
    author_files = 0
    if args.cache is not None:
        current, _ = p.audit(args.cache)
        p.require(current == p.frozen(), 'Fresh primary sources differ from frozen reviewed roles')
        source_audit = True
    if args.author_code_cache is not None:
        aux = json.loads((ROOT / 'tools/literature/genomicranges_catalog/P094-AUXILIARY.json').read_bytes())
        for row in aux['files']:
            raw = (args.author_code_cache / row['path']).read_bytes()
            p.require(p.pin(raw) == row['raw'] and hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == row['git_blob'],
                      'Changed linked author notebook: ' + row['path'])
            notebook = json.loads(raw)
            for cell in row['selected_code_cells']:
                code = ''.join(notebook['cells'][cell['cell_index']]['source'])
                p.require(hashlib.sha256(code.encode()).hexdigest() == cell['source_sha256'], 'Changed selected author code cell')
            author_files += 1
        record = aux['recorded_session']
        notebook = json.loads((args.author_code_cache / record['path']).read_bytes())
        cell = notebook['cells'][record['cell_index']]
        text = '\n'.join(''.join(o.get('text', o.get('data', {}).get('text/plain', []))) for o in cell.get('outputs', []))
        p.require(hashlib.sha256(text.encode()).hexdigest() == record['output_sha256'], 'Changed recorded software-session output')
    return q, dict(workflow=report, fresh_primary_XML_checked=250 if source_audit else 0,
                   linked_author_notebooks_read_only=author_files, author_code_executed=False)


def preflight():
    p.require(not (ROOT / FOLDER).exists() and not (ROOT / m.PROOF).exists()
              and not (ROOT / m.COVERAGE).exists(), 'Preserve existing admission; do not reapply')


def plan(args):
    preflight()
    before = {name: (ROOT / name).read_bytes() for name in m.META}
    baseline(before)
    p.require(clean_manifest()['entries'] == 849, 'Changed complete predecessor manifest')
    q, measured = qualified(args)
    primary = p.frozen()
    after = m.transition(before, primary, q)
    result = m.check(before, after, primary, q)
    coverage = p.encoded(dict(status=STATUS, counts=result, roles=primary['roles'],
        qualification=p.pin(p.encoded(q)), new_reviews=5, existing_scope_notes_updated=2,
        whole_tool_qualified=False, whole_paper_reproduced=False, whole_goal_complete=False))
    preview = {**after, m.COVERAGE: coverage}
    (args.out / 'metadata-before.zip').write_bytes(zipped(before))
    (args.out / 'PREVIOUS-SHA256SUMS').write_bytes((ROOT / m.DATA / 'SHA256SUMS').read_bytes())
    (args.out / 'QUALIFICATION.json').write_bytes(p.encoded(q))
    (args.out / 'REGISTRY.json').write_bytes(p.encoded(m.registry()))
    for name, raw in preview.items():
        path = args.out / 'preview' / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(raw)
    record = dict(schema_version=1, status='PLANNED_' + STATUS, base=BASE, source_code=sources(),
        before={n: p.pin(raw) for n, raw in before.items()}, preview={n: p.pin(raw) for n, raw in preview.items()},
        result=result, actual=measured, qualification=p.pin(p.encoded(q)),
        registry=p.pin((args.out / 'REGISTRY.json').read_bytes()), canonical_modified=False)
    (args.out / 'PLAN.json').write_bytes(p.encoded(record))
    return record


def inspect(args):
    preflight()
    folder = args.plan_dir.resolve()
    p.require(folder.is_relative_to(ROOT / 'build'), 'Use saved current-owner plan')
    record = json.loads((folder / 'PLAN.json').read_bytes())
    before = read_before(folder / 'metadata-before.zip')
    baseline(before)
    p.require(record['status'] == 'PLANNED_' + STATUS and record['base'] == BASE
              and record['source_code'] == sources(), 'Changed plan or admission code')
    p.require(all((ROOT / n).read_bytes() == raw for n, raw in before.items())
              and record['before'] == {n: p.pin(raw) for n, raw in before.items()}, 'Concurrent predecessor change')
    p.require((ROOT / m.DATA / 'SHA256SUMS').read_bytes() == (folder / 'PREVIOUS-SHA256SUMS').read_bytes()
              and clean_manifest()['entries'] == 849, 'Changed complete predecessor manifest')
    q, measured = qualified(args)
    p.require((folder / 'QUALIFICATION.json').read_bytes() == p.encoded(q)
              and record['qualification'] == p.pin(p.encoded(q)), 'Changed qualification')
    p.require((folder / 'REGISTRY.json').read_bytes() == p.encoded(m.registry())
              and record['registry'] == p.pin((folder / 'REGISTRY.json').read_bytes()), 'Changed planned registry')
    primary = p.frozen()
    after = m.transition(before, primary, q)
    result = m.check(before, after, primary, q)
    expected = {**after, m.COVERAGE: p.encoded(dict(status=STATUS, counts=result, roles=primary['roles'],
        qualification=p.pin(p.encoded(q)), new_reviews=5, existing_scope_notes_updated=2,
        whole_tool_qualified=False, whole_paper_reproduced=False, whole_goal_complete=False))}
    p.require(record['result'] == result and record['preview'] == {n: p.pin(raw) for n, raw in expected.items()}, 'Changed deterministic preview')
    for name, raw in expected.items():
        p.require((folder / 'preview' / name).read_bytes() == raw, 'Changed preview bytes: ' + name)
    return record, before, q, expected, measured


def apply(args):
    record, before, q, preview, measured = inspect(args)
    result = m.check(before, {n: preview[n] for n in m.META}, p.frozen(), q)
    evidence = {'.gitattributes': b'* -text whitespace=cr-at-eol\n',
        'metadata-before.zip': (args.plan_dir / 'metadata-before.zip').read_bytes(),
        'PREVIOUS-SHA256SUMS': (args.plan_dir / 'PREVIOUS-SHA256SUMS').read_bytes(),
        'PLAN.json': p.encoded(record), 'REGISTRY.json': (args.plan_dir / 'REGISTRY.json').read_bytes(),
        'QUALIFICATION.json': p.encoded(q),
        'README.md': ('# GenomicRanges catalog admission\n\n' + m.CURRENT + '\n\n' + m.SCOPE
                      + '\n\n' + m.NOTE + '\n\n' + m.ACTION + '\n').encode()}
    updates = {FOLDER + '/' + n: raw for n, raw in evidence.items()}
    updates.update(preview)
    proof = dict(schema_version=1, status=STATUS, base_commit=BASE, source_code=sources(),
        before={n: p.pin(raw) for n, raw in before.items()}, after={n: p.pin(preview[n]) for n in m.META},
        qualification=p.pin(p.encoded(q)), result=result,
        qualified_packet_status=p.pin((ROOT / 'docs/literature/genomicranges-qualification/STATUS-v2.json').read_bytes()),
        evidence={n: p.pin(raw) for n, raw in updates.items() if n.startswith(FOLDER + '/') or n == m.COVERAGE},
        whole_goal_complete=False)
    updates[m.PROOF] = p.encoded(proof)
    updates[m.DATA + '/SHA256SUMS'] = manifest_bytes(updates)
    expected = {n: None for n in updates}
    expected.update(before)
    expected[m.DATA + '/SHA256SUMS'] = (args.plan_dir / 'PREVIOUS-SHA256SUMS').read_bytes()
    for name in updates:
        (ROOT / name).parent.mkdir(parents=True, exist_ok=True)

    def post():
        p.require(m.check(before, {n: (ROOT / n).read_bytes() for n in m.META}, p.frozen(), q) == result,
                  'Installed metadata differs')
        current = clean_manifest()
        p.require(current['entries'] == 851, 'Incorrect complete after manifest')
        return current

    current = install(ROOT, args.out, expected, updates, post)
    (args.out / 'COMPLETED.json').write_bytes(p.encoded(dict(status=STATUS, result=result,
        current_manifest=current, actual=measured, canonical_modified=True, scientific_source_changed=False)))
    return result


def verify(args):
    proof = json.loads((ROOT / m.PROOF).read_bytes())
    p.require(proof['status'] == STATUS and proof['base_commit'] == BASE and proof['source_code'] == sources(), 'Changed admitted scope/code')
    for name, wanted in proof['evidence'].items():
        p.require(p.pin((ROOT / name).read_bytes()) == wanted, 'Changed admission evidence: ' + name)
    before = read_before(ROOT / FOLDER / 'metadata-before.zip')
    baseline(before)
    p.require(proof['before'] == {n: p.pin(raw) for n, raw in before.items()}, 'Changed retained predecessor')
    p.require((ROOT / FOLDER / 'PREVIOUS-SHA256SUMS').read_bytes() == git('show', BASE + ':' + m.DATA + '/SHA256SUMS'), 'Changed predecessor manifest')
    q, measured = qualified(args)
    p.require(proof['qualification'] == p.pin(p.encoded(q))
              and proof['qualified_packet_status'] == p.pin((ROOT / 'docs/literature/genomicranges-qualification/STATUS-v2.json').read_bytes()), 'Changed qualified packet')
    p.require((ROOT / FOLDER / 'REGISTRY.json').read_bytes() == p.encoded(m.registry()), 'Changed admitted registry')
    after = {n: (ROOT / n).read_bytes() for n in m.META}
    result = m.check(before, after, p.frozen(), q)
    p.require(proof['after'] == {n: p.pin(raw) for n, raw in after.items()} and proof['result'] == result, 'Changed installed catalog')
    current = clean_manifest()
    p.require(current['entries'] == 851, 'Changed complete 851-entry manifest')
    record = dict(status=STATUS, result=result, current_manifest=current, actual=measured,
                  original_R_reexecuted=False, whole_goal_complete=False)
    (args.out / 'COMPLETED.json').write_bytes(p.encoded(record))
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    for name in ['plan', 'inspect', 'apply', 'verify']:
        mode.add_argument('--' + name, action='store_true')
    for name in ['out', 'cache', 'author-code-cache', 'jar', 'plan-dir']:
        parser.add_argument('--' + name, type=Path, required=name == 'out')
    args = parser.parse_args()
    owned = False
    try:
        p.require(__debug__, 'Run Python without -O')
        p.require(args.out.resolve().is_relative_to(ROOT / 'build') and args.out.resolve() != ROOT / 'build', 'Use NEW current-owner build output')
        args.out.mkdir(parents=True, exist_ok=False)
        owned = True
        if not args.verify:
            p.require(all(value is not None for value in [args.cache, args.author_code_cache, args.jar]),
                      'Plan/inspect/apply require actual primary cache,linked author-code cache and producer')
        if args.apply or args.inspect:
            p.require(args.plan_dir is not None, 'Supply saved --plan-dir')
        result = plan(args) if args.plan else inspect(args)[0] if args.inspect else apply(args) if args.apply else verify(args)
        if args.inspect:
            (args.out / 'COMPLETED.json').write_bytes(p.encoded(dict(status='PASS_INSPECTED_' + STATUS, result=result['result'])))
        print(json.dumps(dict(status='PASS_GENOMICRANGES_CATALOG_' + ('plan' if args.plan else 'inspect' if args.inspect else 'apply' if args.apply else 'verify'))))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        if owned:
            (args.out / 'FAILURE.json').write_bytes(p.encoded(dict(status='SKIP3' if code == 3 else 'FAIL2',
                error=repr(error), traceback=traceback.format_exc(), complete_result=False, required_action=m.ACTION)))
        print(('SKIP3: ' if code == 3 else 'FAIL2: ') + str(error), file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
