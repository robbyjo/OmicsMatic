"""Plan, inspect, transactionally admit and verify the 17 edgeR source roles."""
from pathlib import Path
import argparse
import io
import json
import sys
import traceback
import zipfile
from edger_catalog import model as m
from edger_catalog.primary import audit, require
from edger_catalog import evidence as e
from edger_dispatch_transition import DISPATCH, BEFORE, ROUTE, HELP, ANCHOR, HELP_ANCHOR, dispatcher_before
from seurat_catalog.catalog import clean_manifest, manifest_bytes, install
from wire_signac_catalog import delta

ROOT = Path(__file__).resolve().parents[3]
STATUS = 'PASS_EDGER_CUMULATIVE_ADMISSION_17_SOURCE_ROLES_AND_QUALIFIED_GLOBAL_COMPONENTS'


def encoded(value):
    return m.encoded(value)


def read_before(path):
    with zipfile.ZipFile(path) as z:
        require(len(z.namelist()) == 8 and set(z.namelist()) == set(m.META), 'Different exact-before archive population')
        return {name: z.read(name) for name in m.META}


def zipped(files):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, raw in sorted(files.items()):
            z.writestr(name, raw)
    return stream.getvalue()


def proposed_dispatcher(before):
    require(e.pin(before) == BEFORE, 'Different original dispatcher')
    after = before.replace(ANCHOR, ROUTE + ANCHOR, 1).replace(HELP_ANCHOR, HELP + HELP_ANCHOR, 1)
    require(dispatcher_before(after) == before, 'Unqualified dispatcher change')
    return after


def coverage_bytes(primary, result):
    return encoded(dict(schema_version=1, status='PASS_ADMITTED_EDGER_SOURCE_ROLES_WITH_EXPLICIT_AUTHOR_GAPS',
        source_crosswalk=m.build(primary), admitted_result=result,
        current_family_status='upstream-adapter-validated', canonical_reviews_appended=17,
        canonical_observations_added=['P088'], new_upstream_family_credit=1, new_native_family_credit=0,
        whole_tool_qualified=False, whole_paper_reproduced=False, whole_goal_complete=False))


def plan(args, out):
    require(args.primary_cache is not None and args.historical_checkout is not None,
        'Plan needs --primary-cache and --historical-checkout for the exact unchanged original-model stage')
    before = {n: (ROOT / n).read_bytes() for n in m.META}
    foundation = e.baseline(before)
    manifest = clean_manifest()
    require(manifest['entries'] == 820, 'Different preceding manifest')
    primary = audit(args.primary_cache)
    qualification = e.qualification()
    after = m.transition(before, primary, qualification['result'])
    result = m.check(before, after, primary, qualification['result'])
    receipt = e.run_predecessor(args.historical_checkout, before, out)
    old_root = (ROOT / DISPATCH).read_bytes()
    prior = e.retained(before, old_root, receipt)
    (out / 'metadata-before.zip').write_bytes(zipped(before))
    (out / 'dispatcher-before.java').write_bytes(old_root)
    (out / 'PREVIOUS-SHA256SUMS').write_bytes((ROOT / m.DATA / 'SHA256SUMS').read_bytes())
    for name, value in [('PRIMARY.json', primary), ('QUALIFICATION.json', qualification),
        ('PREDECESSOR.json', receipt), ('RETAINED.json', prior)]:
        (out / name).write_bytes(encoded(value))
    preview = dict(after)
    preview[DISPATCH] = proposed_dispatcher(old_root)
    preview[m.COVERAGE] = coverage_bytes(primary, result)
    for name, raw in preview.items():
        p = out / 'preview' / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(raw)
    record = dict(schema_version=1, status='PLANNED_EDGER_CUMULATIVE_ADMISSION', foundation=foundation,
        source_code=e.sources(), result=result, before={n: e.pin(v) for n, v in before.items()},
        expected_preview={n: e.pin(v) for n, v in preview.items()},
        files={name: e.pin((out / name).read_bytes()) for name in ['metadata-before.zip',
            'dispatcher-before.java', 'PREVIOUS-SHA256SUMS', 'PRIMARY.json', 'QUALIFICATION.json',
            'PREDECESSOR.json', 'RETAINED.json', 'predecessor.stdout', 'predecessor.stderr', 'PREDECESSOR-PROCESS.json']})
    (out / 'PLAN.json').write_bytes(encoded(record))
    (out / 'COMPLETED.json').write_bytes(encoded(dict(status='PASS_PREPARED_EDGER_PLAN_WITHOUT_CANONICAL_MUTATION', result=result)))
    return record


def inspect(folder, primary_cache=None):
    record = json.loads((folder / 'PLAN.json').read_bytes())
    require(record['schema_version'] == 1 and record['status'] == 'PLANNED_EDGER_CUMULATIVE_ADMISSION', 'Different plan schema/status')
    require(record['source_code'] == e.sources(), 'Owner/control source changed after plan')
    for name, wanted in record['files'].items():
        require(e.pin((folder / name).read_bytes()) == wanted, 'Changed plan evidence: ' + name)
    before = read_before(folder / 'metadata-before.zip')
    require(record['foundation'] == e.baseline(before), 'Git/head/source/canonical baseline changed after plan')
    require(all((ROOT / n).read_bytes() == raw for n, raw in before.items()), 'Canonical metadata changed after plan')
    require((ROOT / m.DATA / 'SHA256SUMS').read_bytes() == (folder / 'PREVIOUS-SHA256SUMS').read_bytes(), 'Preceding manifest changed')
    require(clean_manifest()['entries'] == 820, 'Current preceding manifest differs')
    primary = json.loads((folder / 'PRIMARY.json').read_bytes())
    if primary_cache is not None:
        require(audit(primary_cache) == primary, 'Fresh 250-source audit differs from plan')
    qualification = e.qualification()
    require(qualification == json.loads((folder / 'QUALIFICATION.json').read_bytes()), 'Global/source qualification changed')
    after = m.transition(before, primary, qualification['result'])
    result = m.check(before, after, primary, qualification['result'])
    require(result == record['result'], 'Planned scope/counts differ')
    old_root = (folder / 'dispatcher-before.java').read_bytes()
    receipt = json.loads((folder / 'PREDECESSOR.json').read_bytes())
    require(e.retained(before, old_root, receipt) == json.loads((folder / 'RETAINED.json').read_bytes()), 'Retained predecessor differs')
    preview = dict(after)
    preview[DISPATCH] = proposed_dispatcher(old_root)
    preview[m.COVERAGE] = coverage_bytes(primary, result)
    require(record['expected_preview'] == {n: e.pin(raw) for n, raw in preview.items()}, 'Different deterministic preview')
    for name, raw in preview.items():
        require((folder / 'preview' / name).read_bytes() == raw, 'Preview file changed: ' + name)
    return record, before, primary, qualification, receipt, preview


def apply(args, out):
    require(args.plan_dir is not None and args.primary_cache is not None, 'Apply needs --plan-dir and --primary-cache; source changes must be rechecked')
    record, before, primary, qualification, receipt, preview = inspect(args.plan_dir, args.primary_cache)
    folder = ROOT / e.FOLDER
    require(not folder.exists() or folder.is_dir() and not any(folder.iterdir()), 'Preserve existing admission evidence; choose the correct new stage')
    folder.mkdir(parents=True, exist_ok=True)
    old_root = (args.plan_dir / 'dispatcher-before.java').read_bytes()
    after = {name: preview[name] for name in m.META}
    result = m.check(before, after, primary, qualification['result'])
    retained = e.retained(before, old_root, receipt)
    artifacts = {'.gitattributes': b'* -text whitespace=cr-at-eol\n',
        'metadata-before.zip': (args.plan_dir / 'metadata-before.zip').read_bytes(),
        'dispatcher-before.java': old_root,
        'PREVIOUS-SHA256SUMS': (args.plan_dir / 'PREVIOUS-SHA256SUMS').read_bytes(),
        'PRIMARY.json': encoded(primary), 'QUALIFICATION.json': encoded(qualification),
        'PREDECESSOR.json': encoded(receipt), 'RETAINED.json': encoded(retained),
        'predecessor.stdout': (args.plan_dir / 'predecessor.stdout').read_bytes(),
        'predecessor.stderr': (args.plan_dir / 'predecessor.stderr').read_bytes(),
        'PREDECESSOR-PROCESS.json': (args.plan_dir / 'PREDECESSOR-PROCESS.json').read_bytes(),
        'PLAN.json': encoded(record), 'owner-source.zip': zipped({n: (ROOT / n).read_bytes() for n in e.OWN}),
        'WARNINGS.md': ('# Warnings and required actions\n\n' + m.NOTE + '\n\n' + m.ACTION + '\n').encode()}
    artifacts['README.md'] = ('# edgeR cumulative admission evidence\n\n'
        'Eight exact preceding metadata files, original dispatcher/manifest, unchanged old-owner execution, '
        'fresh 250-source function audit, saved global qualification and exact owner source are retained. '
        'The source crosswalk distinguishes tested components from unqualified author workflows.\n\n'
        'Run `python -B tools/literature/close_edger_catalog.py --verify --out build/edger-verify-NEW`. '
        'This catalog owner uses Git for exact preceding source identity; the standalone saved global '
        'proof reader executes no Git/R/JVM. No fresh R refit or old full-marker success is claimed here.\n\n'
        + m.ACTION + '\n').encode()
    updates = {e.FOLDER + '/' + name: raw for name, raw in artifacts.items()}
    updates.update(after)
    updates[DISPATCH] = preview[DISPATCH]
    updates[m.COVERAGE] = preview[m.COVERAGE]
    changes = [delta(DISPATCH, old_root, preview[DISPATCH]), delta(m.META[5], before[m.META[5]], after[m.META[5]])]
    updates[e.COMPAT] = encoded(dict(schema_version=1, status='passed-dispatch-catalog-only-source-deltas', tool_id='edger',
        scope='Exactly six additive dispatcher/help lines and the 17 appended source reviews. No inverse for scientific code or runtime behavior.', paths=changes))
    proof = dict(schema_version=1, status=STATUS, base_commit=e.BASE, source_code=e.sources(),
        before={n: e.pin(raw) for n, raw in before.items()}, after={n: e.pin(raw) for n, raw in after.items()},
        dispatcher_before=BEFORE, dispatcher_after=e.pin(preview[DISPATCH]), result=result,
        qualification=qualification, retained_predecessor=retained,
        evidence={n: e.pin(raw) for n, raw in updates.items() if n.startswith(e.FOLDER + '/')},
        whole_goal_complete=False)
    proof['evidence'].update({n: e.pin((ROOT / n).read_bytes()) for n in e.GUIDES})
    proof['evidence'].update({m.COVERAGE: e.pin(updates[m.COVERAGE]), e.COMPAT: e.pin(updates[e.COMPAT])})
    updates[e.PROOF] = encoded(proof)
    updates[m.DATA + '/SHA256SUMS'] = manifest_bytes(updates)
    expected = {n: None for n in updates}
    expected.update(before)
    expected[DISPATCH] = old_root
    expected[m.DATA + '/SHA256SUMS'] = (args.plan_dir / 'PREVIOUS-SHA256SUMS').read_bytes()
    def post():
        require(e.pin((ROOT / DISPATCH).read_bytes()) == proof['dispatcher_after'], 'Installed dispatcher differs')
        require(m.check(before, {n: (ROOT / n).read_bytes() for n in m.META}, primary, qualification['result']) == result,
            'Installed metadata differs')
        manifest = clean_manifest()
        require(manifest['entries'] == 823, 'Different after manifest population')
        return manifest
    manifest = install(ROOT, out, expected, updates, post)
    (out / 'COMPLETED.json').write_bytes(encoded(dict(status=STATUS, result=result, current_manifest=manifest)))


def verify(out, primary_cache=None):
    proof = json.loads((ROOT / e.PROOF).read_bytes())
    require(proof['schema_version'] == 1 and proof['status'] == STATUS and proof['base_commit'] == e.BASE,
        'Different admitted proof schema/status/base')
    require(proof['source_code'] == e.sources(), 'Admitted owner/control source changed')
    for name, wanted in proof['evidence'].items():
        require(e.pin((ROOT / name).read_bytes()) == wanted, 'Admitted evidence changed: ' + name)
    folder = ROOT / e.FOLDER
    before = read_before(folder / 'metadata-before.zip')
    for name, raw in before.items():
        require(raw == e.git(ROOT, 'show', e.BASE + ':' + name), 'Exact retained base bytes differ: ' + name)
    require((folder / 'PREVIOUS-SHA256SUMS').read_bytes() == e.git(ROOT, 'show', e.BASE + ':' + m.DATA + '/SHA256SUMS'),
        'Exact preceding manifest differs')
    with zipfile.ZipFile(folder / 'owner-source.zip') as z:
        require(len(z.namelist()) == len(e.OWN) and set(z.namelist()) == set(e.OWN), 'Different owner-source archive')
        for name, wanted in proof['source_code'].items():
            require(e.pin(z.read(name)) == wanted, 'Archived owner source differs: ' + name)
    primary = json.loads((folder / 'PRIMARY.json').read_bytes())
    if primary_cache is not None:
        require(audit(primary_cache) == primary, 'Fresh exact 250-source audit differs')
    qualification = e.qualification()
    require(qualification == proof['qualification'] == json.loads((folder / 'QUALIFICATION.json').read_bytes()),
        'Current global/source qualification differs')
    old_root = (folder / 'dispatcher-before.java').read_bytes()
    receipt = json.loads((folder / 'PREDECESSOR.json').read_bytes())
    require(e.retained(before, old_root, receipt) == proof['retained_predecessor']
        == json.loads((folder / 'RETAINED.json').read_bytes()), 'Different retained predecessor')
    after = {n: (ROOT / n).read_bytes() for n in m.META}
    result = m.check(before, after, primary, qualification['result'])
    require(result == proof['result'] and proof['before'] == {n: e.pin(raw) for n, raw in before.items()}
        and proof['after'] == {n: e.pin(raw) for n, raw in after.items()}, 'Admitted metadata/counts differ')
    require((ROOT / m.COVERAGE).read_bytes() == coverage_bytes(primary, result), 'Admitted role-coverage wrapper differs')
    current_root = (ROOT / DISPATCH).read_bytes()
    require(e.pin(current_root) == proof['dispatcher_after'] and dispatcher_before(current_root) == old_root,
        'Current root route differs')
    from catalog_source_integrity import reverse_delta, verify_source
    compat = json.loads((ROOT / e.COMPAT).read_bytes())
    require(len(compat['paths']) == 2 and {r['path'] for r in compat['paths']} == {DISPATCH, m.META[5]}, 'Unexpected source inverse')
    for row in compat['paths']:
        expected = old_root if row['path'] == DISPATCH else before[m.META[5]]
        require(reverse_delta((ROOT / row['path']).read_bytes(), row) == expected, 'Source inverse differs')
        verify_source(ROOT, ROOT / m.DATA, row['path'], e.pin(expected)['sha256'])
    manifest = clean_manifest()
    require(manifest['entries'] == 823, 'Current manifest differs')
    result = dict(status=STATUS, result=result, current_manifest=manifest,
        fresh_source_audit_performed=primary_cache is not None, original_R_refit_performed=False)
    (out / 'COMPLETED.json').write_bytes(encoded(result))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    for name in ['plan', 'inspect', 'apply', 'verify']:
        mode.add_argument('--' + name, action='store_true')
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--plan-dir', type=Path)
    parser.add_argument('--primary-cache', type=Path)
    parser.add_argument('--historical-checkout', type=Path)
    args = parser.parse_args()
    owned = False
    try:
        require(__debug__ and sys.version_info >= (3, 11), 'Use Python3.11+ without -O')
        require(args.out.resolve().is_relative_to(ROOT / 'build') and args.out.resolve() != ROOT / 'build', 'Use NEW workspace build output')
        args.out.mkdir(parents=True, exist_ok=False)
        owned = True
        if args.plan:
            plan(args, args.out)
        elif args.inspect:
            require(args.plan_dir is not None, 'Inspect needs --plan-dir')
            record = inspect(args.plan_dir, args.primary_cache)[0]
            (args.out / 'COMPLETED.json').write_bytes(encoded(dict(status='PASS_INSPECTED_EDGER_ADMISSION_PLAN', result=record['result'])))
        elif args.apply:
            apply(args, args.out)
        else:
            verify(args.out, args.primary_cache)
        print('PASS edgeR guarded ' + ('plan' if args.plan else 'inspect' if args.inspect else 'apply' if args.apply else 'verify'))
        return 0
    except Exception as error:
        code = 3 if isinstance(error, (FileNotFoundError, PermissionError)) else 2
        record = dict(status='SKIP3' if code == 3 else 'FAIL2', error=repr(error), detail=traceback.format_exc(),
            complete_result=False, required_action=m.ACTION)
        if owned:
            (args.out / 'FAILURE.json').write_bytes(encoded(record))
        print(record['status'] + ': ' + str(error) + '\n' + m.ACTION, file=sys.stderr)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
