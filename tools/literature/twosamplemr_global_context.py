"""Check the exact new route inverse and execute the unchanged preceding PEER catalog owner."""
from pathlib import Path
import argparse, json, subprocess, sys, uuid
from twosamplemr_dispatch_transition import BASE, DISPATCH, dispatcher_before, pin, require
from peer_catalog import model as m, primary as p, evidence as e
from peer_catalog.catalog import read_before
from seurat_catalog.catalog import clean_manifest

ROOT = Path(__file__).resolve().parents[2]
CATALOG_OWNER = 'ac75e6958d7cf4cf6ec6372ec0548e1dcb1dcee1'


def git(root, *args):
    return subprocess.check_output(['git', '--no-replace-objects', *args], cwd=root)


def current_before():
    before = {name: (ROOT / name).read_bytes() for name in m.META}
    for name, raw in before.items():
        require(raw == git(ROOT, 'show', BASE + ':' + name), 'Changed preceding catalog: ' + name)
    current = (ROOT / DISPATCH).read_bytes()
    previous = dispatcher_before(current)
    require(previous == git(ROOT, 'show', BASE + ':' + DISPATCH), 'Different exact preceding dispatcher')
    require(git(ROOT, 'diff', '--name-only', '--diff-filter=MDRT', BASE, '--', 'src/main').decode().splitlines()
            == [DISPATCH], 'Changed old scientific main source beyond reviewed route')
    proof = json.loads((ROOT / e.PROOF).read_bytes())
    require(proof['after'] == {name: pin(raw) for name, raw in before.items()}, 'Different exact PEER catalog state')
    require(proof['qualified_dispatcher'] == pin(previous), 'Different qualified PEER dispatcher inverse')
    for group in ['source_code', 'evidence']:
        for name, wanted in proof[group].items():
            require(pin((ROOT / name).read_bytes()) == wanted, 'Changed preceding source/evidence: ' + name)
    result = m.check(read_before(ROOT / e.FOLDER / 'metadata-before.zip'), before, p.frozen(), proof['qualification']['result'])
    require(result['integrations'] == 143 and result['reviewed_uses'] == 1616 and result['pending_uses'] == 1104,
            'Different preceding catalog counts')
    manifest = clean_manifest()
    require(manifest['entries'] == 835, 'Different preceding manifest')
    return dict(status='PASS_PRECEDING_PEER_CATALOG_AND_EXACT_TWOSAMPLEMR_ROUTE_INVERSE',
                catalog_files={name: pin(raw) for name, raw in before.items()},
                dispatcher_before=pin(previous), dispatcher_after=pin(current),
                current_pure_model_check=result, current_manifest=manifest,
                source_overlay_used=False, old_hashes_refreshed=False)


def execute(out, owner, cache):
    current = current_before()
    owner = owner.resolve()
    require(owner != ROOT and git(owner, 'rev-parse', 'HEAD').decode().strip() == CATALOG_OWNER,
            'Use the separate exact preceding PEER catalog owner')
    status = git(owner, 'status', '--porcelain=v1', '-z', '--untracked-files=all')
    require(not status, 'Preserve dirty owners; use the exact clean retained PEER catalog checkout')
    for name, wanted in current['catalog_files'].items():
        require(pin((owner / name).read_bytes()) == wanted, 'Historical metadata differs')
    require((owner / DISPATCH).read_bytes() == dispatcher_before((ROOT / DISPATCH).read_bytes()),
            'Historical dispatcher differs')
    destination = owner / 'build' / ('twosamplemr-global-predecessor-' + uuid.uuid4().hex)
    commands = {
        'catalog': [sys.executable, '-B', str(owner / 'tools/literature/close_peer_catalog.py'),
                    '--verify', '--primary-cache', str(cache), '--out', str(destination)],
        'saved': [sys.executable, '-B', str(owner / 'tools/literature/verify_peer_catalog_checkpoint.py'),
                  '--out', str(destination) + '-saved'],
    }
    processes = {}
    for name, command in commands.items():
        with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
            process = subprocess.run(command, cwd=owner, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=180)
        processes[name] = dict(command=command, exit_code=process.returncode)
        require(process.returncode == 0, 'Unchanged preceding owner failed: ' + name)
    completed = json.loads((destination / 'COMPLETED.json').read_bytes())
    require(completed['result'] == current['current_pure_model_check'] and completed['fresh_source_audit_performed'],
            'Different fresh preceding PEER verification')
    require(git(owner, 'status', '--porcelain=v1', '-z', '--untracked-files=all') == status, 'Historical source/status changed')
    result = dict(status='PASS_TWOSAMPLEMR_ROUTE_INVERSE_AND_UNCHANGED_PEER_CATALOG_OWNER', current=current,
                  processes=processes, historical_head=CATALOG_OWNER, historical_root=str(owner),
                  historical_new_evidence=str(destination), historical_status_unchanged=True,
                  fresh_all250_preceding_source_audit=True, source_overlay_used=False,
                  old_hashes_refreshed=False, new_catalog_credit=0, whole_goal_complete=False)
    (out / 'RESULT.json').write_bytes(p.encoded(result))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--historical-checkout', type=Path, required=True)
    parser.add_argument('--primary-cache', type=Path, required=True)
    args = parser.parse_args()
    require(__debug__ and sys.version_info >= (3, 11), 'Use Python3.11+ without -O')
    require(args.out.resolve().is_relative_to(ROOT / 'build') and args.out.resolve() != ROOT / 'build',
            'Use NEW workspace build output')
    args.out.mkdir(parents=True, exist_ok=False)
    execute(args.out, args.historical_checkout, args.primary_cache)
    print('PASS exact TwoSampleMR route inverse and unchanged preceding PEER owner')


if __name__ == '__main__':
    main()
