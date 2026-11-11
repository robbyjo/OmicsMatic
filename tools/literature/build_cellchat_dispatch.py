# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the CellChat root supplement while preserving every scientific class/resource byte."""
import argparse, json, subprocess, traceback, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--compiler', choices=['javac21', 'javac25'], required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/cellchat-runtime')
    out.mkdir(parents=True)
    state = dict(status='FAIL2_CELLCHAT_ROOT_BUILD', implementation_credit=False, whole_goal_complete=False)
    try:
        canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
        before = pin(canonical)
        parent_home = ROOT / 'docs/literature/cistarget-catalog-development'
        parent = parent_home / ('cistarget-root-'+a.compiler+'.jar')
        predecessor = json.loads((parent_home/'VALIDATION.json').read_bytes())
        assert pin(parent) == predecessor['published_root_artifacts'][parent.relative_to(ROOT).as_posix()]
        families = ['inference', 'legacy', 'network', 'strength', 'workflow']
        jars = [ROOT/f'docs/literature/cellchat-{f}-development/cellchat-{f}-{a.compiler}.jar' for f in families]
        jars += [ROOT/f'docs/literature/cellchat-development/cellchat-rna-{a.compiler}.jar']
        dependencies = {p.relative_to(ROOT).as_posix(): pin(p) for p in [canonical, parent, *jars]}
        for f, jar in zip(families, jars):
            proof_path = ROOT/f'docs/literature/cellchat-{f}-development/VALIDATION.json'
            proof = json.loads(proof_path.read_bytes())
            if 'development_jars' in proof:
                assert pin(jar) == proof['development_jars'][jar.name]
            else:
                assert pin(jar) == proof['artifacts'][jar.name]
            dependencies[proof_path.relative_to(ROOT).as_posix()] = pin(proof_path)
        native_proof = ROOT/'docs/literature/cellchat-development/VALIDATION.json'
        assert pin(jars[-1]) == json.loads(native_proof.read_bytes())['development_jars'][jars[-1].name]
        dependencies[native_proof.relative_to(ROOT).as_posix()] = pin(native_proof)
        with zipfile.ZipFile(parent) as z:
            payload = {n: z.read(n) for n in z.namelist()}
        old = dict(payload)
        added = {}
        for jar in jars:
            with zipfile.ZipFile(jar) as z:
                for name in z.namelist():
                    raw = z.read(name)
                    if name.endswith('.class') or name.startswith('org/jlinalg/omics/cellchat-'):
                        assert name not in payload or payload[name] == raw, ('Scientific overlap differs', jar, name)
                        payload[name] = raw
                        added[name] = raw
                    elif name.startswith('META-INF/'):
                        payload['META-INF/cellchat/'+jar.stem+'/'+name[len('META-INF/'):]] = raw
        compiler = 'C:/Program Files/'+('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/javac.exe' if a.compiler == 'javac21' else 'Common Files/Oracle/Java/javapath/javac.exe')
        sources = [ROOT/'src/main/java/org/jlinalg/cli'/n for n in ['CellChatToolsCli.java', 'JLinAlgCli.java']]
        files = [Path(__file__).resolve(), *sources]
        pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in files}
        for p in files:
            target = out/'source-snapshot'/p.relative_to(ROOT)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(p.read_bytes())
        classes = out/'classes'
        classes.mkdir()
        cmd = [compiler, '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8',
               '-cp', ';'.join(map(str, [*jars, parent, canonical])), '-d', str(classes), *map(str, sources)]
        r = subprocess.run(cmd, capture_output=True, timeout=120)
        save(out/'compile.json', dict(command=cmd, exit_code=r.returncode))
        (out/'compile.stdout').write_bytes(r.stdout)
        (out/'compile.stderr').write_bytes(r.stderr)
        assert r.returncode == 0, r.stderr.decode(errors='replace')
        compiled = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
        assert set(compiled) == {'org/jlinalg/cli/CellChatToolsCli.class', 'org/jlinalg/cli/JLinAlgCli.class'}
        payload.update(compiled)
        payload['META-INF/cellchat/DEPENDENCIES.json'] = (json.dumps(dependencies, indent=2)+'\n').encode()
        jar = out/'cellchat-root-supplement.jar'
        with zipfile.ZipFile(jar, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name, raw in sorted(payload.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                info.compress_type, info.external_attr = zipfile.ZIP_DEFLATED, 0o100644 << 16
                z.writestr(info, raw)
        with zipfile.ZipFile(jar) as z:
            assert all(z.read(n) == raw for n, raw in old.items() if n != 'org/jlinalg/cli/JLinAlgCli.class')
            assert all(z.read(n) == raw for n, raw in added.items())
        assert pin(canonical) == before and all(pin(ROOT/n) == v for n, v in pins.items())
        state.update(status='PASS_CELLCHAT_ROOT_ALL_PREDECESSOR_AND_SCIENTIFIC_BYTES_UNCHANGED', compiler=a.compiler,
            artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)), source_pins=pins, dependencies=dependencies,
            predecessor_entries=len(old), scientific_entries_preserved=len(added), canonical_executable_unchanged=before)
    except Exception as error:
        state['error'] = repr(error)
        (out/'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out/'COMPLETED.json', state)
    print(state['status'], state.get('error', '')[:600], flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable scientific-byte preservation checks')
    raise SystemExit(main())
