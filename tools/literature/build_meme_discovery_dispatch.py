# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile the MEME root route; retain every scientific/resource byte of its explicitly selected parent."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from validate_mashr_native import JAVA21, pin


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf8', newline='\n')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--parent-build', type=Path, required=True); parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve(); base = ROOT / 'build/literature-review/meme-runtime'
    assert out.is_relative_to(base) and not out.exists(); out.mkdir()
    proof = json.loads((args.parent_build / 'COMPLETED.json').read_bytes()); parent = ROOT / proof['artifact']['path']
    assert pin(parent) == {k: proof['artifact'][k] for k in ['bytes', 'sha256']}
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar'); root_source = ROOT / 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
    bound = {p.relative_to(ROOT).as_posix(): pin(p) for p in [root_source, Path(__file__)]}
    for p in [root_source, Path(__file__)]:
        target = out / 'source-snapshot' / p.relative_to(ROOT); target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(p, target)
    classes = out / 'classes'; classes.mkdir()
    command = [str(JAVA21 / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8', '-cp', str(parent), '-d', str(classes), str(root_source)]
    result = subprocess.run(command, cwd=ROOT, capture_output=True, timeout=120)
    (out / 'compile.stdout').write_bytes(result.stdout); (out / 'compile.stderr').write_bytes(result.stderr)
    save(out / 'compile.json', dict(command=command, exit_code=result.returncode)); assert result.returncode == 0, result.stderr.decode('utf8', errors='replace')
    updates = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    target_name = 'org/jlinalg/cli/JLinAlgCli.class'; assert set(updates) == {target_name}
    jar = out / 'omicsmatic-0.3.7-meme-discovery-root-development.jar'
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar, 'x') as current:
        for entry in old.infolist(): current.writestr(entry, updates.get(entry.filename, old.read(entry.filename)))
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as current:
        assert current.testzip() is None and old.namelist() == current.namelist()
        changed = [name for name in old.namelist() if old.read(name) != current.read(name)]; assert changed == [target_name]
        unchanged = len(old.namelist()) - 1
    assert all(pin(ROOT / name) == value for name,value in bound.items()) and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    save(out / 'COMPLETED.json', dict(status='compiled-MEME-root-route-with-byte-identical-scientific-backend', artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)),
        parent_artifact=proof['artifact'], source_pins=bound, changed_entries=changed, unchanged_parent_entries=unchanged, canonical_executable_unchanged=canonical,
        original_fitting_in_this_build=False, implementation_credit=False))
    print('Compiled root route; only JLinAlgCli.class changed; all scientific/resource entries preserved')


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable source/entry preservation checks')
    main()
