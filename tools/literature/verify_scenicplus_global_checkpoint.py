"""Check saved source/producer identities and the precise dispatch-only extension.

This reader does not rerun Java tests or original scientific computation.
"""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile


FOLDER = 'docs/literature/scenicplus-global-checkpoint-20261123'


def pin(path):
    digest, size = hashlib.sha256(), 0
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            digest.update(block)
            size += len(block)
    return dict(bytes=size, sha256=digest.hexdigest())


def require(value, message):
    if not value:
        raise ValueError(message)


def normalized(raw):
    return raw.replace(b'\r\n', b'\n')


def verify(root, jar=None, private_root=None):
    status = json.loads((root / FOLDER / 'STATUS.json').read_bytes())
    require(status['status'] == 'FOUR_PREPARED_SCENICPLUS_PROFILES_TESTED_THROUGH_MAIN_COMMAND_CATALOG_PENDING', 'Changed checkpoint scope')
    require(status['new_family_credit'] == status['new_reviews'] == 0 and not status['whole_goal_complete'], 'Expanded admission claim')
    require(status['normal_tests'] == dict(tests=2069, failures=0, errors=0, skipped=0), 'Changed test summary')
    require(status['global_dispatch_integrated'] and status['actual_modes'] == ['RUN', 'VERIFY', 'REPLAY'], 'Missing main-route qualification')
    require(status['historical_readers_on_archived_context'] == 6 and not status['historical_hashes_refreshed'], 'Changed historical boundary')
    for name, identity in status['source_files'].items():
        require(not Path(name).is_absolute() and '..' not in Path(name).parts, 'Unsafe source path')
        require(pin(root / name) == identity, 'Changed source: ' + name)
    baseline = root / FOLDER / 'pre-extension-source.zip'
    require(pin(baseline) == status['pre_extension_source'], 'Changed pre-extension context')
    with zipfile.ZipFile(baseline) as archive:
        expected = set(status['extension']['before_files'])
        require(set(archive.namelist()) == expected and len(archive.namelist()) == len(expected), 'Changed baseline members')
        for name, identity in status['extension']['before_files'].items():
            raw = archive.read(name)
            require(dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest()) == identity, 'Changed baseline source')
            current = normalized((root / name).read_bytes())
            if name.endswith('/JLinAlgCli.java'):
                for line in status['extension']['exact_added_main_lines']:
                    body = line.encode() + b'\n'
                    require(current.count(body) == 1, 'Changed exact dispatcher addition')
                    current = current.replace(body, b'', 1)
            else:
                for edit in status['extension']['message_edits'][Path(name).name]:
                    require(current.count(edit['after'].encode()) == 1, 'Changed current message')
                    current = current.replace(edit['after'].encode(), edit['before'].encode(), 1)
            require(current == normalized(raw), 'Extension exceeds routing/messages: ' + name)
    if jar is not None:
        require(pin(jar) == status['producer'], 'Restore exact producer; no historical hash refresh')
        with zipfile.ZipFile(jar) as archive:
            require('org/jlinalg/cli/ScenicPlusCommandCli.class' in archive.namelist(), 'Missing global router')
            for source, resource in status['packaged_resources'].items():
                require(archive.read(resource) == (root / source).read_bytes(), 'Changed packaged scientific caller/profile')
    if private_root is not None:
        for name, identity in status['private_evidence'].items():
            require(not Path(name).is_absolute() and '..' not in Path(name).parts, 'Unsafe private evidence path')
            require(pin(private_root / name) == identity, 'Changed private evidence: ' + name)
    return dict(status='PASS_SOURCE_SAVED_IDENTITIES_AND_EXACT_DISPATCH_EXTENSION_ONLY',
                source_files=len(status['source_files']),
                private_evidence_checked=len(status['private_evidence']) if private_root is not None else 0,
                exact_JAR_checked=jar is not None, original_computation_reexecuted=False,
                tests_rerun=False, new_family_credit=0, new_reviews=0, whole_goal_complete=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--private-root', type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.root, args.jar, args.private_root), indent=2))
