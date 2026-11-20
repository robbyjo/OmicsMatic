"""Exact predecessor and qualification evidence; no historical-reader overlays."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
import uuid
from edger_catalog import model as m
from edger_catalog.primary import require
from verify_edger_dispatch import verify as global_qualification
from edger_dispatch_transition import BEFORE, DISPATCH, dispatcher_before
from catalog_source_integrity import reverse_delta

ROOT = Path(__file__).resolve().parents[3]
BASE = 'dbfc2e4c1971736552d13dfdf7681bfa31f5dd0d'
HISTORICAL_HEAD = '33db0648621a359e03fb6da3e048c409b45588c4'
FOLDER = 'docs/literature/edger-catalog-admission'
PROOF = m.DATA + '/edger-catalog-validation.json'
COMPAT = m.DATA + '/edger-catalog-shared-source-compatibility.json'
OLD_PROOF = m.DATA + '/susie-dispatch-validation.json'
OLD_COMPAT = m.DATA + '/susie-dispatch-shared-source-compatibility.json'
GUIDES = [m.GUIDE, 'docs/vignettes/edger-tools.md']
OWN = ['tools/literature/edger_catalog/' + n for n in ['__init__.py', 'primary.py', 'coverage.py',
    'model.py', 'evidence.py', 'catalog.py', 'test_coverage.py', 'test_model.py', 'test_admission.py', 'ADMISSION.md']]
OWN += ['tools/literature/' + n for n in ['close_edger_catalog.py', 'edger_role_crosswalk.py',
    'edger_dispatch_transition.py', 'verify_edger_dispatch.py', 'check_edger_catalog_ui.cjs']]


def pin(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def git(root, *args):
    return subprocess.check_output(['git', '--no-replace-objects', *args], cwd=root)


def sources():
    return {name: pin((ROOT / name).read_bytes()) for name in OWN}


def qualification():
    result, _ = global_qualification()
    result.pop('global_dispatcher_currently_installed')
    path = ROOT / 'docs/literature/edger-dispatch-checks/PORTABLE-CONTROLS.json'
    portable = json.loads(path.read_bytes())
    require(portable['status'] == 'PASS_GLOBAL_EDGER_PORTABLE_READER_MATERIALIZATION_AND_SIX_REFUSALS_WINDOWS_LINUX'
        and portable['original_inputs_outputs_and_hashes_not_refreshed'] is True,
        'Missing exact portable global-reader controls')
    expected = {'existing-output': 2, 'missing-global': 3, 'missing-predecessor': 3,
        'changed-archive': 2, 'changed-dispatcher': 2, 'resealed-stock-failure': 2}
    require(set(portable['results']) == {'windows', 'linux'}, 'Different receiving reader hosts')
    for row in portable['results'].values():
        require(row['valid_without_Git_R_or_JVM'] is True and row['PATH_empty'] is True
            and set(row['materialized_global_Java_VERIFY']) == {'edger-tools', 'edger-normalize'}
            and {r['mode']: r['exit_code'] for r in row['refusals']} == expected,
            'Portable reader/control or actual materialized Java result differs')
    return dict(result=result, portable_controls=portable,
        proof_files={name: pin((ROOT / name).read_bytes()) for name in [
            'docs/literature/edger-dispatch-checks/QUALIFICATION.json',
            'docs/literature/edger-dispatch-checks/evidence.zip',
            'docs/literature/edger-dispatch-checks/PORTABLE-CONTROLS.json']})


def baseline(before):
    require(set(before) == set(m.META), 'Exactly eight preceding metadata files required')
    for name, raw in before.items():
        require(raw == git(ROOT, 'show', BASE + ':' + name), 'Canonical predecessor differs from explicit base: ' + name)
    require(pin((ROOT / DISPATCH).read_bytes()) == BEFORE, 'Plan only against exact preceding dispatcher')
    return dict(base_commit=BASE, prepared_head=git(ROOT, 'rev-parse', 'HEAD').decode().strip(),
        before={name: pin(raw) for name, raw in before.items()}, dispatcher_before=BEFORE,
        previous_manifest=pin((ROOT / m.DATA / 'SHA256SUMS').read_bytes()))


def retained(before, old_root, receipt):
    """Validate saved exact old state, never run an old owner on new metadata."""
    require(pin(old_root) == BEFORE, 'Different preceding dispatcher source')
    try:
        git(ROOT, 'cat-file', '-e', HISTORICAL_HEAD + '^{commit}')
    except subprocess.CalledProcessError as error:
        raise FileNotFoundError('Restore exact historical Git commit ' + HISTORICAL_HEAD
            + ' with a permitted fetch; the catalog owner cannot verify its preceding sources without it.') from error
    changed = set(git(ROOT, 'diff', '--no-ext-diff', '--name-only', '--diff-filter=MDRT', HISTORICAL_HEAD,
        '--', 'src', 'tools/literature').decode().splitlines())
    require(changed <= {DISPATCH, m.META[5]}, 'Tracked preceding scientific/control source changed: '
        + ', '.join(sorted(changed - {DISPATCH, m.META[5]})))
    proof = json.loads((ROOT / OLD_PROOF).read_bytes())
    require(proof['after'] == {name: pin(raw) for name, raw in before.items()}, 'Before is not the qualified SuSiE-dispatch stage')
    for name, wanted in proof['source_code'].items():
        require(pin((ROOT / name).read_bytes()) == wanted, 'Preceding owner source changed: ' + name)
    for name, wanted in proof['evidence'].items():
        require(pin((ROOT / name).read_bytes()) == wanted, 'Preceding owner evidence changed: ' + name)
    compat = json.loads((ROOT / OLD_COMPAT).read_bytes())
    require(len(compat['paths']) == 1 and compat['paths'][0]['path'] == DISPATCH
        and pin(reverse_delta(old_root, compat['paths'][0])) == proof['dispatcher_before'],
        'Preceding dispatcher relation differs')
    require(receipt['status'] == 'PASS_UNCHANGED_PREDECESSOR_OWNER_IN_EXACT_MODEL_CHECKOUT'
        and receipt['historical_head'] == HISTORICAL_HEAD and receipt['exit_code'] == 0
        and receipt['source_overlay_used'] is False and receipt['working_tree_unchanged'] is True,
        'Exact unchanged old-owner execution required')
    completed = receipt['completed']
    require(completed['status'] == proof['status'] and completed['result'] == proof['result']
        and completed['current_manifest']['entries'] == 820, 'Preceding actual verification result differs')
    return dict(status='PASS_EXACT_BEFORE_METADATA_AND_RETAINED_PREDECESSOR_EVIDENCE',
        old_proof=pin((ROOT / OLD_PROOF).read_bytes()), old_owner_sources=len(proof['source_code']),
        old_evidence_files=len(proof['evidence']), metadata_files=8, manifest_entries=820,
        unchanged_old_owner_receipt=pin(m.encoded(receipt)), historical_marker_full_verifiers_still_SKIPPED=True,
        no_old_reader_source_overlay=True, no_new_R_refit_claim=True)


def run_predecessor(historical, before, output):
    historical = historical.resolve()
    require(historical != ROOT.resolve(), 'Use a separate exact unchanged predecessor checkout')
    if not historical.is_dir():
        raise FileNotFoundError('Supply an exact permitted historical checkout at ' + HISTORICAL_HEAD)
    require(git(historical, 'rev-parse', 'HEAD').decode().strip() == HISTORICAL_HEAD,
        'Historical checkout must be the exact original-model checkpoint')
    status = git(historical, 'status', '--porcelain=v1', '-z', '--untracked-files=all')
    require(not status, 'Historical checkout must remain clean; preserve its unrelated changes')
    for name, raw in before.items():
        require((historical / name).read_bytes() == raw, 'Historical canonical metadata differs: ' + name)
    require(pin((historical / DISPATCH).read_bytes()) == BEFORE, 'Historical dispatcher differs')
    old_output = historical / 'build' / ('edger-admission-predecessor-' + uuid.uuid4().hex)
    command = [sys.executable, '-B', str(historical / 'tools/literature/close_susie_dispatch.py'),
        '--verify', '--out', str(old_output)]
    process = subprocess.run(command, cwd=historical, stdin=subprocess.DEVNULL, capture_output=True, timeout=300)
    (output / 'predecessor.stdout').write_bytes(process.stdout)
    (output / 'predecessor.stderr').write_bytes(process.stderr)
    (output / 'PREDECESSOR-PROCESS.json').write_bytes(m.encoded(dict(command=command,
        exit_code=process.returncode, evidence_output=str(old_output))))
    require(process.returncode == 0, 'Unchanged predecessor verification failed; inspect retained logs and its NEW output')
    require(git(historical, 'status', '--porcelain=v1', '-z', '--untracked-files=all') == status
        and git(historical, 'rev-parse', 'HEAD').decode().strip() == HISTORICAL_HEAD,
        'Historical tracked state changed')
    receipt = dict(status='PASS_UNCHANGED_PREDECESSOR_OWNER_IN_EXACT_MODEL_CHECKOUT',
        historical_head=HISTORICAL_HEAD, command=command, exit_code=0, evidence_output=str(old_output),
        source_overlay_used=False, working_tree_unchanged=True,
        completed=json.loads((old_output / 'COMPLETED.json').read_bytes()))
    retained(before, (historical / DISPATCH).read_bytes(), receipt)
    return receipt
