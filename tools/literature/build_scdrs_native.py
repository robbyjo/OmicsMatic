# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build isolated native scDRS numerical components and an external test consumer."""
import argparse
import shutil
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from build_mtag_native import DEPENDENCY, DEPENDENCY_SHA
from setup_scdrs_reference import BASE


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    p.add_argument('--compiler', required=True, choices=['javac21', 'javac25'])
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    state = dict(status='FAIL2_SCDRS_NATIVE_BUILD', new_catalog_credit=False, full_adapter_available=False)
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    before = pin(canonical)
    try:
        assert before['sha256'] == '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
        assert pin(DEPENDENCY)['sha256'] == DEPENDENCY_SHA
        tools = Path('C:/Program Files') / ('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler == 'javac21' else 'Common Files/Oracle/Java/javapath')
        sources = [ROOT / 'src/main/java/org/jlinalg/genetics/ScdrsScores.java',
                   ROOT / 'src/test/java/org/jlinalg/cli/ScdrsReferenceProbe.java',
                   ROOT / 'src/test/java/org/jlinalg/genetics/ScdrsScoresTest.java']
        resources = sorted((ROOT / 'src/main/resources/org/jlinalg/omics/scdrs').iterdir())
        pins = {f.relative_to(ROOT).as_posix(): pin(f) for f in [*sources, *resources, Path(__file__)]}
        for name in pins:
            target = out / 'source-snapshot' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, target)
        artifacts = {}
        for kind, source, cp in [('api', sources[0], [DEPENDENCY]), ('consumer', sources[1], [out / 'scdrs-api.jar', canonical])]:
            classes = out / (kind + '-classes')
            classes.mkdir()
            command = [str(tools / 'javac.exe'), '--release', '17', '-sourcepath', '', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8',
                       '-cp', ';'.join(map(str, cp)), '-d', str(classes), str(source)]
            result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=120)
            (out / (kind + '.stdout')).write_bytes(result.stdout)
            (out / (kind + '.stderr')).write_bytes(result.stderr)
            save(out / (kind + '.process.json'), dict(command=command, exit_code=result.returncode))
            assert result.returncode == 0, result.stderr[-3000:]
            payload = {f.relative_to(classes).as_posix(): f.read_bytes() for f in classes.rglob('*.class')}
            if kind == 'consumer':
                with zipfile.ZipFile(canonical) as z:
                    payload['org/jlinalg/cli/SimpleJson.class'] = z.read('org/jlinalg/cli/SimpleJson.class')
            for f in resources:
                if f.name != '.gitattributes': payload['org/jlinalg/omics/scdrs/' + f.name] = f.read_bytes()
            payload['META-INF/LICENSE'] = (ROOT / 'LICENSE').read_bytes()
            payload['META-INF/NOTICE'] = (b'Native scDRS numerical components only. Algorithms Zhang and Hou, MIT, scDRS1.0.2 '
                b'commit7f8f8d5754e1e3057916cd3fbe720b96f90c1c1b. Pairwise reduction NumPy1.23.5, BSD3. '
                b'Separate JDistlib0.10.2 dependency. No H5AD preprocessing, matched control sampling, graph '
                b'heterogeneity, complete adapter, catalog credit or whole-goal completion claim.\n')
            payload['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\n\n'
            artifact = out / ('scdrs-' + kind + '.jar')
            with zipfile.ZipFile(artifact, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
                for name, raw in sorted(payload.items()):
                    info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                    info.compress_type = zipfile.ZIP_DEFLATED
                    info.external_attr = 0o100644 << 16
                    z.writestr(info, raw)
            artifacts[kind] = dict(path=artifact.relative_to(ROOT).as_posix(), **pin(artifact))
        assert all(pin(ROOT / name) == wanted for name, wanted in pins.items())
        state.update(status='PASS_NATIVE_SCDRS_API_AND_EXTERNAL_CONSUMER_BUILD_ONLY', source_pins=pins,
            compiler=a.compiler, artifacts=artifacts, dependency=dict(path=DEPENDENCY.relative_to(ROOT).as_posix(), **pin(DEPENDENCY)))
    except Exception as error:
        state['cause'] = repr(error)
    assert pin(canonical) == before
    state['canonical_executable_unchanged'] = before
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''))
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Do not disable build integrity checks')
    raise SystemExit(main())
