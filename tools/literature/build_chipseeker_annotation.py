# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build additive original annotation adapter; preserve every selected scientific parent entry."""
import argparse
import json
from pathlib import Path
import subprocess
import zipfile
from setup_deseq2_reference import ROOT, pin, save
from validate_mashr_native import JAVA21


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--parent-build', type=Path, required=True); p.add_argument('--out', type=Path, required=True)
    args = p.parse_args(); out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists()
    out.mkdir()
    proof = json.loads((args.parent_build / 'COMPLETED.json').read_bytes()); parent = ROOT / proof['artifact']['path']
    assert pin(parent) == {k: proof['artifact'][k] for k in ('bytes', 'sha256')}
    sources = [ROOT / 'src/main/java/org/jlinalg/cli' / (name + '.java') for name in
               ('ChipSeekerAnnotationRequest', 'ChipSeekerAnnotationRuntime', 'ChipSeekerAnnotationCli')]
    resource_root = ROOT / 'src/main/resources/org/jlinalg/omics/chipseeker-annotation'
    resources = sorted(p for p in resource_root.rglob('*') if p.is_file())
    assert len(resources) == 105
    bindings = {path.relative_to(ROOT).as_posix(): pin(path) for path in [Path(__file__).resolve(), *sources, *resources]}
    for name in bindings:
        copy = out / 'source-snapshot' / name; copy.parent.mkdir(parents=True, exist_ok=True); copy.write_bytes((ROOT / name).read_bytes())
    classes = out / 'classes'; classes.mkdir()
    command = [str(JAVA21 / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8', '-cp', str(parent), '-d', str(classes), *map(str, sources)]
    compile_result = subprocess.run(command, cwd=ROOT, capture_output=True, timeout=120)
    (out / 'compile.stdout').write_bytes(compile_result.stdout); (out / 'compile.stderr').write_bytes(compile_result.stderr)
    save(out / 'compile.json', dict(command=command, exit_code=compile_result.returncode))
    assert compile_result.returncode == 0, compile_result.stderr.decode('utf-8', errors='replace')
    additions = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    assert additions and all(name.startswith('org/jlinalg/cli/ChipSeekerAnnotation') for name in additions)
    additions.update({'org/jlinalg/omics/chipseeker-annotation/' + p.relative_to(resource_root).as_posix(): p.read_bytes() for p in resources})
    jar = out / 'omicsmatic-0.3.7-chipseeker-annotation-development.jar'
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar, 'x') as current:
        assert set(additions).isdisjoint(old.namelist())
        for entry in old.infolist():current.writestr(entry, old.read(entry.filename))
        for name, raw in additions.items():current.writestr(name, raw, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as old, zipfile.ZipFile(jar) as current:
        assert current.testzip() is None and all(old.read(n) == current.read(n) for n in old.namelist())
        assert set(current.namelist()) - set(old.namelist()) == set(additions)
        count = len(old.namelist())
    assert all(pin(ROOT / name) == expected for name, expected in bindings.items())
    save(out / 'COMPLETED.json', dict(status='compiled-additive-original-ChIPseeker-annotation-adapter-not-admitted',
        artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)), parent_artifact=proof['artifact'],
        source_pins=bindings, unchanged_parent_entries=count, added_entries=sorted(additions), implementation_credit=False))
    print(json.dumps(dict(status='compiled-additive-original-annotation-adapter', unchanged_parent_entries=count, added_entries=len(additions))))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source or scientific-parent identity checks')
    main()
