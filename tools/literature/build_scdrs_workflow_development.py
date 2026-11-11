# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a standalone scDRS workflow development artifact; no canonical rebuild or catalog credit."""
import argparse
import shutil
import subprocess
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    p.add_argument('--compiler', choices=['javac21', 'javac25'], required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    state = dict(status='FAIL2_SCDRS_WORKFLOW_BUILD', new_catalog_credit=False, full_adapter_validated=False)
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    before = pin(canonical)
    try:
        assert before['sha256'] == '545917aaf820f2fe404873e1f0bc72186ebe90d3a11fd7f6f26f592115fbf2f2'
        native = ROOT / 'docs/literature/scdrs-native-development/scdrs-api-javac21.jar'
        assert pin(native)['sha256'] == '239039f7e3f84c4836955aab69a3ea255d7672528d62b37f3728f83699f450ac'
        sources = sorted((ROOT / 'src/main/java/org/jlinalg/cli').glob('Scdrs*.java'))
        resources = sorted(p for p in (ROOT / 'src/main/resources/org/jlinalg/omics/scdrs').iterdir() if p.is_file())
        tracked = [*sources, *resources, Path(__file__)]
        pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in tracked}
        for name in pins:
            target = out / 'source-snapshot' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / name, target)
        tools = Path('C:/Program Files') / ('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler == 'javac21' else 'Common Files/Oracle/Java/javapath')
        classes = out / 'classes'
        classes.mkdir()
        command = [str(tools / 'javac.exe'), '--release', '17', '-sourcepath', '', '-encoding', 'UTF-8', '-Xlint:all', '-Werror',
                   '-cp', str(canonical), '-d', str(classes), *map(str, sources)]
        result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=180)
        (out / 'javac.stdout').write_bytes(result.stdout)
        (out / 'javac.stderr').write_bytes(result.stderr)
        save(out / 'javac.process.json', dict(command=command, exit_code=result.returncode))
        assert result.returncode == 0, result.stderr[-4000:]
        payload = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
        with zipfile.ZipFile(canonical) as z:
            payload['org/jlinalg/cli/SimpleJson.class'] = z.read('org/jlinalg/cli/SimpleJson.class')
        with zipfile.ZipFile(native) as z:
            for name in z.namelist():
                if name.startswith('org/jlinalg/genetics/Scdrs') and name.endswith('.class'): payload[name] = z.read(name)
        for p in resources:
            if p.name != '.gitattributes': payload['org/jlinalg/omics/scdrs/' + p.name] = p.read_bytes()
        payload['META-INF/LICENSE'] = (ROOT / 'LICENSE').read_bytes()
        payload['META-INF/NOTICE'] = (b'Historical scDRS workflow development only, no catalog admission or whole-goal completion claim. '
            b'Original algorithms Zhang and Hou MIT. Native numerical predecessor bytes preserved; its Java API needs separate JDistlib0.10.2. '
            b'Native pairwise reduction NumPy1.23.5 BSD3; optional group BH follows statsmodels0.13.5 fdrcorrection arithmetic. '
            b'Included small fire/pynndescent/umap wheels retain original packaged Python source bytes and their bundled upstream licenses.\n')
        payload['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.ScdrsToolsCli\n\n'
        artifact = out / 'scdrs-workflow-development.jar'
        with zipfile.ZipFile(artifact, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name, raw in sorted(payload.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16
                z.writestr(info, raw)
        assert all(pin(ROOT / name) == wanted for name, wanted in pins.items())
        state.update(status='PASS_STANDALONE_SCDRS_WORKFLOW_DEVELOPMENT_BUILD_ONLY', compiler=a.compiler,
            source_pins=pins, artifact=dict(path=artifact.relative_to(ROOT).as_posix(), **pin(artifact)),
            native_predecessor=pin(native), source_predecessors_modified=False)
    except Exception as error:
        state['cause'] = repr(error)
    assert pin(canonical) == before
    state['canonical_executable_unchanged'] = before
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''))
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable source/predecessor/canonical integrity checks')
    raise SystemExit(main())
