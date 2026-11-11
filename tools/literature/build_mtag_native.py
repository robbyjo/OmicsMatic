# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile an isolated MTAG estimator and external consumer without rebuilding the canonical executable."""
import argparse
import shutil
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save

BASE = ROOT / 'build/literature-review/mtag-runtime'
DEPENDENCY = ROOT / 'build/dependencies/jdistlib-all-0.10.2.jar'
DEPENDENCY_SHA = '8b50aaa11d2cbd512525e0d74f8e609f40db9f4fc179ccb7a885a82257e0ac81'


def jar(path, classes, main=None):
    payload = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    payload['META-INF/LICENSE'] = (ROOT / 'LICENSE').read_bytes()
    payload['META-INF/NOTICE'] = b'Native finite positive-definite MTAG1.0.8 estimator component; algorithms attributed to Maghzian, Walters and Turley, GPL3. Commit9e17f3cf1fbcf57b6bc466daefdc51fd0de3c5dc. Separate pinned JDistlib0.10.2 dependency. No input harmonization, LDSC, covariance adjustment, maxFDR, complete-tool or whole-goal claim.\n'
    payload['META-INF/MANIFEST.MF'] = ('Manifest-Version: 1.0\n' + ('Main-Class: ' + main + '\n' if main else '') + '\n').encode()
    with zipfile.ZipFile(path, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, raw in sorted(payload.items()):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, raw)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--compiler', choices=['javac21', 'javac25'], required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    state = dict(status='FAIL2_NATIVE_MTAG_BUILD', new_catalog_credit=False, whole_goal_complete=False)
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    protected = pin(canonical)
    try:
        assert pin(DEPENDENCY)['sha256'] == DEPENDENCY_SHA
        tools = Path('C:/Program Files') / ('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler == 'javac21' else 'Common Files/Oracle/Java/javapath')
        sources = [ROOT / 'src/main/java/org/jlinalg/genetics/MtagEstimator.java', ROOT / 'src/test/java/org/jlinalg/genetics/MtagReferenceProbe.java',
                   ROOT / 'src/test/java/org/jlinalg/genetics/MtagEstimatorTest.java', Path(__file__)]
        pins = {f.relative_to(ROOT).as_posix(): pin(f) for f in sources}
        for name in pins:
            target = out / 'source-snapshot' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, target)
        artifacts = {}
        for kind, source, cp in [('api', sources[0], [DEPENDENCY]), ('consumer', sources[1], [out / 'mtag-api.jar', DEPENDENCY])]:
            classes = out / (kind + '-classes')
            classes.mkdir()
            command = [str(tools / 'javac.exe'), '--release', '17', '-sourcepath', '', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8',
                       '-cp', ';'.join(map(str, cp)), '-d', str(classes), str(source)]
            result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=120)
            (out / (kind + '.stdout')).write_bytes(result.stdout)
            (out / (kind + '.stderr')).write_bytes(result.stderr)
            save(out / (kind + '.process.json'), dict(command=command, exit_code=result.returncode))
            assert result.returncode == 0, result.stderr[-4000:]
            artifact = out / ('mtag-' + kind + '.jar')
            jar(artifact, classes, 'org.jlinalg.genetics.MtagReferenceProbe' if kind == 'consumer' else None)
            artifacts[artifact.name] = dict(path=artifact.relative_to(ROOT).as_posix(), **pin(artifact))
        assert all(pin(ROOT / name) == wanted for name, wanted in pins.items())
        state.update(status='PASS_NATIVE_MTAG_API_AND_EXTERNAL_CONSUMER_BUILD_ONLY', source_pins=pins, compiler=a.compiler,
                     artifacts=artifacts, dependency=dict(path=DEPENDENCY.relative_to(ROOT).as_posix(), **pin(DEPENDENCY)))
    except Exception as error:
        state['cause'] = repr(error)
    assert pin(canonical) == protected
    state['canonical_executable_unchanged'] = protected
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''))
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source/dependency/canonical integrity checks')
    raise SystemExit(main())
