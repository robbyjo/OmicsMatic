# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build the GENESIS root supplement while preserving predecessor scientific class bytes and the released executable."""
import argparse
import json
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save

ACTION = 'SKIP this build if the selected permitted compiler, pinned canonical dependency or matching development artifacts are unavailable. Supply the exact prerequisites and retry NEW paths. FAIL2: preserve the attempt and resolve compilation/integrity causes. A root build admits no original analysis or catalog credit.'


class Unavailable(RuntimeError):
    pass


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', type=Path, required=True); p.add_argument('--compiler', choices=['javac21', 'javac25'], required=True)
    a = p.parse_args(); out = a.out.resolve(); assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/genesis-runtime'); out.mkdir(parents=True)
    state = dict(status='FAIL2_GENESIS_ROOT_BUILD', new_catalog_credit=False, whole_goal_complete=False, required_action=ACTION); code = 2
    try:
        canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
        prior_home = ROOT / 'docs/literature/snprelate-tools-development'; prior = prior_home / ('snprelate-root-' + a.compiler + '.jar')
        home = ROOT / 'docs/literature/genesis-extended-workflow-development'; standalone = home / ('genesis-workflows-' + a.compiler + '.jar')
        tools = Path('C:/Program Files') / ('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler == 'javac21' else 'Common Files/Oracle/Java/javapath')
        if not all(f.is_file() for f in [canonical, prior, standalone, tools / 'javac.exe']):
            raise Unavailable('A selected exact root build prerequisite is unavailable')
        old_proof = json.loads((prior_home / 'VALIDATION.json').read_bytes()); proof = json.loads((home / 'VALIDATION.json').read_bytes())
        assert pin(prior) == old_proof['artifacts'][prior.name] and pin(standalone) == proof['artifacts'][standalone.name]
        assert pin(canonical) == proof['canonical_executable_unchanged'] and all(pin(ROOT / n) == v for n, v in proof['source_pins'].items() if n != '.gitattributes')
        dependencies = {f.relative_to(ROOT).as_posix(): pin(f) for f in [canonical, prior, standalone]}
        with zipfile.ZipFile(prior) as z:
            payload = {n: z.read(n) for n in z.namelist()}
        old = dict(payload); added = {}
        with zipfile.ZipFile(standalone) as z:
            for name in z.namelist():
                raw = z.read(name)
                if name.endswith('.class') or name.startswith('org/jlinalg/omics/genesis/'):
                    assert name not in payload or payload[name] == raw, ('Scientific/common overlap differs', name)
                    payload[name] = raw; added[name] = raw
                elif name.startswith('META-INF/'):
                    payload['META-INF/genesis/' + name[len('META-INF/'):]] = raw
        source = ROOT / 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'; source_pins = {f.relative_to(ROOT).as_posix(): pin(f) for f in [source, Path(__file__)]}
        for name in source_pins:
            target = out / 'source-snapshot' / name; target.parent.mkdir(parents=True, exist_ok=True); target.write_bytes((ROOT / name).read_bytes())
        classes = out / 'classes'; classes.mkdir()
        command = [str(tools / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8', '-sourcepath', '', '-cp', ';'.join(map(str, [standalone, prior, canonical])), '-d', str(classes), str(source)]
        result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=120)
        (out / 'compile.stdout').write_bytes(result.stdout); (out / 'compile.stderr').write_bytes(result.stderr); save(out / 'compile.process.json', dict(command=command, exit_code=result.returncode))
        assert result.returncode == 0, result.stderr[-6000:]
        compiled = {f.relative_to(classes).as_posix(): f.read_bytes() for f in classes.rglob('*.class')}
        assert set(compiled) == {'org/jlinalg/cli/JLinAlgCli.class'}; payload.update(compiled)
        payload['META-INF/genesis/DEPENDENCIES.json'] = (json.dumps(dependencies, indent=2) + '\n').encode('utf8')
        artifact = out / 'genesis-root-supplement.jar'
        with zipfile.ZipFile(artifact, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name, raw in sorted(payload.items()):
                item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); item.compress_type = zipfile.ZIP_DEFLATED; item.external_attr = 0o100644 << 16; z.writestr(item, raw)
        with zipfile.ZipFile(artifact) as z:
            assert all(z.read(name) == raw for name, raw in old.items() if name != 'org/jlinalg/cli/JLinAlgCli.class')
            assert all(z.read(name) == raw for name, raw in added.items())
        assert all(pin(ROOT / n) == value for n, value in dependencies.items()) and all(pin(ROOT / n) == value for n, value in source_pins.items())
        state.update(status='PASS_GENESIS_ROOT_PREDECESSOR_SCIENTIFIC_BYTES_UNCHANGED', compiler=a.compiler, artifact=dict(path=artifact.relative_to(ROOT).as_posix(), **pin(artifact)),
                     dependencies=dependencies, source_pins=source_pins, predecessor_entries=len(old), added_scientific_entries=len(added), canonical_executable_unchanged=dependencies[canonical.relative_to(ROOT).as_posix()])
        code = 0
    except Unavailable as error:
        state.update(status='SKIP3_GENESIS_ROOT_BUILD_UNAVAILABLE', cause=repr(error)); code = 3
    except Exception as error:
        state['cause'] = repr(error); (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    (out / 'WARNINGS.md').write_text('# ' + state['status'] + '\n\n' + state.get('cause', 'Root supplement built; no original analysis is admitted.') + '\n\nREQUIRED ACTION: ' + ACTION + '\n', encoding='utf8', newline='\n')
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('cause', '')[:6000], flush=True)
    return code


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable predecessor scientific-byte and canonical executable preservation')
    raise SystemExit(main())
