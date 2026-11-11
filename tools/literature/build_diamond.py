# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a standalone Java17 original DIAMOND adapter without changing the canonical executable."""
import argparse, hashlib, json, subprocess, traceback, zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]


def pin(path):
    raw = path.read_bytes()
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def save(path, value):
    path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf8')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--compiler', choices=['javac21', 'javac25'], required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/diamond-runtime')
    out.mkdir(parents=True)
    state = dict(status='FAIL2_DIAMOND_ADAPTER_BUILD', implementation_credit=False, whole_goal_complete=False)
    try:
        canonical = ROOT/'build/cli/omicsmatic-0.3.7.jar'
        before = pin(canonical)
        sources = [ROOT/'src/main/java/org/jlinalg/cli'/(n+'.java') for n in ['NextflowParameters',
            'NextflowFiles', 'NextflowJson', 'SimpleJson', 'DiamondRequest', 'DiamondSequences',
            'DiamondAudit', 'DiamondToolsCli']]
        resource_home = ROOT/'src/main/resources/org/jlinalg/omics/diamond'
        resources = {p.relative_to(ROOT/'src/main/resources').as_posix(): p for p in resource_home.iterdir() if p.is_file()}
        assert set(p.name for p in resources.values()) == {'SOURCE-LOCK.json', 'setup_diamond_runtime.py', 'NOTICE.txt', 'LICENSE.txt'}
        files = [Path(__file__).resolve(), *sources, *resources.values()]
        pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in files}
        for p in files:
            dest = out/'source-snapshot'/p.relative_to(ROOT)
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(p.read_bytes())
        compiler = 'C:/Program Files/'+('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe'
            if a.compiler == 'javac21' else 'Common Files/Oracle/Java/javapath/javac.exe')
        classes = out/'classes'
        classes.mkdir()
        cmd = [compiler, '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8',
               '-d', str(classes), *map(str, sources)]
        r = subprocess.run(cmd, capture_output=True, timeout=120)
        save(out/'compile.json', dict(command=cmd, exit_code=r.returncode))
        (out/'compile.stdout').write_bytes(r.stdout)
        (out/'compile.stderr').write_bytes(r.stderr)
        assert r.returncode == 0, r.stderr.decode(errors='replace')
        payload = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
        payload.update({name: p.read_bytes() for name, p in resources.items()})
        payload['META-INF/LICENSE'] = (ROOT/'LICENSE').read_bytes()
        payload['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\r\nMain-Class: org.jlinalg.cli.DiamondToolsCli\r\n\r\n'
        jar = out/'diamond-development.jar'
        with zipfile.ZipFile(jar, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name, raw in sorted(payload.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type, info.external_attr = zipfile.ZIP_DEFLATED, 0o100644 << 16
                z.writestr(info, raw)
        assert pin(canonical) == before and all(pin(ROOT/n) == v for n, v in pins.items())
        state.update(status='PASS_ISOLATED_DIAMOND_ADAPTER_BUILD_ONLY', compiler=a.compiler,
            artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)), source_pins=pins,
            canonical_executable_unchanged=before)
    except Exception as error:
        state['error'] = repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:1200], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source/canonical preservation checks')
    raise SystemExit(main())
