# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build additive Java-only supplied-prediction subsetting; preserve every validated Cicero entry."""
import argparse
import json
from pathlib import Path
import subprocess
import zipfile
from setup_deseq2_reference import ROOT, pin, save
from validate_mashr_native import JAVA21


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    base = ROOT / 'build/literature-review/monocle-runtime'
    out = args.out.resolve()
    assert out.is_relative_to(base) and not out.exists()
    out.mkdir()
    parent_proof = json.loads((ROOT / 'docs/literature/cicero-coaccessibility-development/VALIDATION.json').read_bytes())
    assert parent_proof['adapter_admitted'] and parent_proof['audited_connection_rows'] == 19548
    record = parent_proof['artifact']
    parent = ROOT / record['path']
    assert pin(parent) == {k: record[k] for k in ['bytes', 'sha256']}
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar')
    sources = [ROOT / 'src/main/java/org/jlinalg/cli/CiceroPredictionCli.java',
               ROOT / 'src/main/java/org/jlinalg/singlecell/CiceroPredictionSubset.java']
    pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), *sources]}
    for name in pins:
        path = out / 'source-snapshot' / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes((ROOT / name).read_bytes())
    classes = out / 'classes'
    classes.mkdir()
    command = [str(JAVA21 / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8', '-cp', str(parent), '-d', str(classes), *map(str, sources)]
    run = subprocess.run(command, cwd=ROOT, capture_output=True, timeout=120)
    (out / 'compile.stdout').write_bytes(run.stdout)
    (out / 'compile.stderr').write_bytes(run.stderr)
    save(out / 'compile.json', dict(command=command, exit_code=run.returncode))
    assert run.returncode == 0, run.stderr.decode('utf8', errors='replace')
    additions = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    artifact = out / 'omicsmatic-0.3.7-cicero-prediction-development.jar'
    with zipfile.ZipFile(parent) as before, zipfile.ZipFile(artifact, 'x') as after:
        assert set(additions).isdisjoint(before.namelist())
        for entry in before.infolist():
            after.writestr(entry, before.read(entry.filename))
        for name, raw in sorted(additions.items()):
            after.writestr(name, raw, compress_type=zipfile.ZIP_DEFLATED)
    with zipfile.ZipFile(parent) as before, zipfile.ZipFile(artifact) as after:
        assert after.testzip() is None and len(after.namelist()) == len(set(after.namelist()))
        assert all(before.read(n) == after.read(n) for n in before.namelist())
        assert set(after.namelist()) - set(before.namelist()) == set(additions)
        count = len(before.namelist())
    assert all(pin(ROOT / n) == v for n, v in pins.items()) and pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    save(out / 'COMPLETED.json', dict(status='compiled-additive-Cicero-prediction-not-validation', artifact=dict(path=artifact.relative_to(ROOT).as_posix(), **pin(artifact)),
        parent_artifact=record, source_pins=pins, unchanged_parent_entries=count, added_entries=sorted(additions), canonical_executable_unchanged=canonical,
        implementation_credit=False, whole_goal_complete=False))
    print('Built', artifact)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source checks')
    main()
