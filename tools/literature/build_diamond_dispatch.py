# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the DIAMOND root supplement with every predecessor scientific byte unchanged."""
import argparse, json, subprocess, traceback, zipfile
from pathlib import Path
from validate_diamond_execution import ROOT, BASE, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    p.add_argument('--compiler', choices=['javac21', 'javac25'], required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True)
    state = dict(status='FAIL2_DIAMOND_ROOT_BUILD', implementation_credit=False, whole_goal_complete=False)
    try:
        canonical = ROOT/'build/cli/omicsmatic-0.3.7.jar'
        prior = ROOT/f'docs/literature/cellchat-catalog-development/cellchat-root-{a.compiler}.jar'
        standalone = BASE/('build21-3' if a.compiler == 'javac21' else 'build25-3')/'diamond-development.jar'
        dependencies = {p.relative_to(ROOT).as_posix(): pin(p) for p in [canonical, prior, standalone]}
        proof = json.loads((prior.parent/'VALIDATION.json').read_bytes())
        assert pin(prior) == proof['published_root_artifacts'][prior.relative_to(ROOT).as_posix()]
        with zipfile.ZipFile(prior) as z:
            payload = {n: z.read(n) for n in z.namelist()}
        old = dict(payload)
        added = {}
        with zipfile.ZipFile(standalone) as z:
            for name in z.namelist():
                raw = z.read(name)
                if name.endswith('.class') or name.startswith('org/jlinalg/omics/diamond/'):
                    assert name not in payload or payload[name] == raw, ('Scientific/common overlap differs', name)
                    payload[name] = raw
                    added[name] = raw
                elif name.startswith('META-INF/'):
                    payload['META-INF/diamond/'+name[len('META-INF/'):]] = raw
        source = ROOT/'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
        pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), source]}
        for name in pins:
            target = out/'source-snapshot'/name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT/name).read_bytes())
        classes = out/'classes'
        classes.mkdir()
        compiler = 'C:/Program Files/'+('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe'
            if a.compiler == 'javac21' else 'Common Files/Oracle/Java/javapath/javac.exe')
        cmd = [compiler, '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8',
            '-cp', ';'.join(map(str, [standalone, prior, canonical])), '-d', str(classes), str(source)]
        r = subprocess.run(cmd, capture_output=True, timeout=120)
        save(out/'compile.json', dict(command=cmd, exit_code=r.returncode))
        (out/'compile.stdout').write_bytes(r.stdout)
        (out/'compile.stderr').write_bytes(r.stderr)
        assert r.returncode == 0, r.stderr.decode(errors='replace')
        compiled = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
        assert set(compiled) == {'org/jlinalg/cli/JLinAlgCli.class'}
        payload.update(compiled)
        payload['META-INF/diamond/DEPENDENCIES.json'] = (json.dumps(dependencies, indent=2)+'\n').encode()
        artifact = out/'diamond-root-supplement.jar'
        with zipfile.ZipFile(artifact, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name, raw in sorted(payload.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type, info.external_attr = zipfile.ZIP_DEFLATED, 0o100644 << 16
                z.writestr(info, raw)
        with zipfile.ZipFile(artifact) as z:
            assert all(z.read(n) == raw for n, raw in old.items() if n != 'org/jlinalg/cli/JLinAlgCli.class')
            assert all(z.read(n) == raw for n, raw in added.items())
        assert all(pin(ROOT/n) == v for n, v in dependencies.items())
        assert all(pin(ROOT/n) == v for n, v in pins.items())
        state.update(status='PASS_DIAMOND_ROOT_PREDECESSOR_AND_SCIENTIFIC_BYTES_UNCHANGED',
            artifact=dict(path=artifact.relative_to(ROOT).as_posix(), **pin(artifact)), compiler=a.compiler,
            dependencies=dependencies, source_pins=pins, predecessor_entries=len(old),
            scientific_entries_preserved=len(added), canonical_executable_unchanged=dependencies[canonical.relative_to(ROOT).as_posix()])
    except Exception as error:
        state['error'] = repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:600], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact scientific-byte preservation checks')
    raise SystemExit(main())
