# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Restore exact historical validation fixtures without changing the working tree.

The preserved producers calculate ROOT from their own location. This explicit,
isolated fixture project lets them check their original byte-pinned inputs and
README, while accepting a separately supplied current scientific JAR/runtime.
It is not a Git checkout or an overlay of current catalog/production sources.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import stat
import sys
import traceback
import zipfile

ROOT = Path(__file__).resolve().parents[3]
PACKET = ROOT / 'docs/literature/plink-component-qualification'
STATUS = 'EXACT_FROZEN_FIXTURE_VIEW_NOT_A_WORKTREE_OVERLAY'
MAX_FILES = 500
MAX_FILE = 16 * 1024 * 1024
MAX_TOTAL = 64 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def fingerprint(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def safe_name(name):
    require(isinstance(name, str) and name and not any(c in name for c in '\\:\0'),
            'Unsafe fixture path')
    path = PurePosixPath(name)
    require(not path.is_absolute() and '..' not in path.parts and path.as_posix() == name,
            'Fixture path escapes or aliases its project')
    require(path.parts[0] in ['examples', 'src', 'tools'], 'Unexpected fixture project root')
    require(not any(part.endswith((' ', '.')) for part in path.parts), 'Ambiguous fixture filename')
    reserved = {'CON', 'PRN', 'AUX', 'NUL'} | {p + str(i) for p in ['COM', 'LPT'] for i in range(1, 10)}
    require(not any(part.split('.')[0].upper() in reserved for part in path.parts),
            'Reserved Windows fixture filename')
    return path


def read_archive(manifest, archive):
    """Verify every member before creating an output directory; no extraction API."""
    require(manifest['schema_version'] == 1 and manifest['status'] == STATUS,
            'Wrong fixture project scope')
    for key in ['current_working_tree_modified', 'scientific_data_fields_changed',
                'old_verifier_sources_or_expected_hashes_changed',
                'runtimes_or_third_party_programs_bundled', 'whole_goal_complete']:
        require(manifest.get(key) is False, 'Unproved fixture/project claim: ' + key)
    wanted = manifest['source_files']
    require(isinstance(wanted, dict) and 0 < len(wanted) <= MAX_FILES, 'Invalid fixture population')
    require(len({n.casefold() for n in wanted}) == len(wanted), 'Case-aliased fixture paths')
    for name in wanted:
        safe_name(name)
    archive = Path(archive)
    require(archive.stat().st_size <= MAX_TOTAL, 'Fixture archive exceeds byte limit')
    require(fingerprint(archive.read_bytes()) == manifest['archive'], 'Changed fixture archive')
    files, total = {}, 0
    with zipfile.ZipFile(archive) as source:
        require(len(source.namelist()) == len(set(source.namelist())) == len(wanted)
                and set(source.namelist()) == set(wanted), 'Fixture archive population differs')
        for item in source.infolist():
            name = item.filename
            safe_name(name)
            require(not item.is_dir() and stat.S_IFMT(item.external_attr >> 16) != stat.S_IFLNK,
                    'Directory or symlink is not a fixture file')
            require(0 <= item.file_size <= MAX_FILE, 'Fixture member exceeds byte limit')
            total += item.file_size
            require(total <= MAX_TOTAL, 'Expanded fixtures exceed byte limit')
            raw = source.read(item)
            require(fingerprint(raw) == wanted[name], 'Changed fixture member: ' + name)
            files[name] = raw
    require(set(manifest['original_working_files']) == set(files), 'Missing working-file lineage')
    restored = manifest['restored']
    require(len({r['path'] for r in restored}) == len(restored), 'Duplicate fixture restoration')
    changed = {n for n in files if fingerprint(files[n]) != manifest['original_working_files'][n]}
    require({r['path'] for r in restored} == changed, 'Restoration lineage does not cover exact changes')
    for row in restored:
        name = row['path']
        require(row['restored'] == wanted[name] and
                row['working_copy_unchanged'] == manifest['original_working_files'][name],
                'Fixture restoration pin differs')
        reason = row['reason']
        if reason == 'captured-current-fixture-with-original-CRLF':
            # The original content is exactly recoverable, including its hash.
            require(b'\r\n' in files[name] and b'\n' not in files[name].replace(b'\r\n', b''),
                    'Restored fixture does not have uniform original CRLF')
            require(fingerprint(files[name].replace(b'\r\n', b'\n')) == row['working_copy_unchanged'],
                    'Restoration changes more than recorded line endings')
        else:
            require(reason == 'exact-retained-validated-README' and
                    name == 'examples/plink-tools/README.md', 'Unqualified fixture transformation')
    return files


def restore(files, destination):
    """Only write into a new directory, retaining a partial attempt on failure."""
    destination = Path(destination).resolve()
    require(not destination.exists(), 'Output exists; choose a NEW directory')
    destination.mkdir(parents=True, exist_ok=False)
    project = destination / 'project'
    project.mkdir()
    for name, raw in sorted(files.items()):
        parts = safe_name(name).parts
        target = project.joinpath(*parts)
        require(target.resolve().is_relative_to(project), 'Fixture destination escapes project')
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open('xb') as stream:
            stream.write(raw)
    return project


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--packet', type=Path, default=PACKET)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    result = dict(status='FAIL2_FROZEN_FIXTURE_PROJECT', scientific_software_executed=False,
                  canonical_catalog_written=False, new_family_credit=0, whole_goal_complete=False)
    code = 2
    owned = False
    try:
        args.out.mkdir(parents=True, exist_ok=False)
        owned = True
        proof = json.loads((args.packet / 'QUALIFICATION.json').read_bytes())
        require(proof['status'].startswith('PASS_PLINK_COMPONENT_') and proof['new_family_credit'] == 0,
                'Different qualification scope or false family credit')
        for name, wanted in proof['restorer_source_pins'].items():
            require(fingerprint((ROOT / name).read_bytes()) == wanted,
                    'Changed frozen fixture restorer source: ' + name)
        for name in ['FIXTURES.json', 'fixtures.zip']:
            require(fingerprint((args.packet / name).read_bytes()) == proof['fixture_artifacts'][name],
                    'Fixture packet proof differs: ' + name)
        manifest = json.loads((args.packet / 'FIXTURES.json').read_bytes())
        files = read_archive(manifest, args.packet / 'fixtures.zip')
        project = restore(files, args.out / 'view')
        require(all(fingerprint((project / n).read_bytes()) == p
                    for n, p in manifest['source_files'].items()), 'Written fixture bytes differ')
        result.update(status='PASS_RESTORED_EXACT_FROZEN_FIXTURE_PROJECT', files=len(files),
                      restorations=len(manifest['restored']), project=str(project),
                      historical_README_restored=True, current_working_tree_modified=False,
                      original_verifier_sources_or_expected_hashes_changed=False,
                      note='Explicit frozen validation inputs only. Supply a permitted exact current JAR/runtime to the unchanged producers; no scientific analysis was executed by this command.')
        code = 0
    except FileNotFoundError as error:
        result.update(status='SKIP3_MISSING_FROZEN_FIXTURE_PACKET', error=str(error))
        code = 3
    except Exception as error:
        result.update(error=repr(error), detail=traceback.format_exc())
    if code:
        result['affected_analysis'] = 'Dependent frozen-fixture GLM/pruning/PCA/confidence validation is not admitted'
        result['required_action'] = ('Restore the exact published fixture packet for SKIP3; for FAIL2 inspect the named integrity/path cause. '
                                     'Keep existing files and diagnostics, correct the cause and retry NEW output. Do not refresh old expected hashes.')
    if owned:
        with (args.out / 'COMPLETED.json').open('x', encoding='utf-8') as stream:
            stream.write(json.dumps(result, indent=2) + '\n')
    print(result['status'], result.get('error', ''), result.get('required_action', ''),
          file=sys.stdout if code == 0 else sys.stderr)
    return code


if __name__ == '__main__':
    raise SystemExit(main())
