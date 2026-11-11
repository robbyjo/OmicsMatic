# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a staged two-edit scDRS root route while retaining every predecessor scientific class byte."""
import argparse
import json
import subprocess
import traceback
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE
from build_mtag_dispatch import without_line_numbers
from curate_shapeit_uses import inverse
from catalog_source_integrity import reverse_delta
from signac_scan_evidence import byte_pin


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--build', type=Path, required=True)
    p.add_argument('--compiler', choices=['javac21', 'javac25'], required=True)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    (out / 'builder-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    state = dict(status='FAIL2_SCDRS_STAGED_ROOT_BUILD', live_root_modified=False, new_catalog_credit=False)
    source = ROOT / 'src/main/java/org/jlinalg/cli/JLinAlgCli.java'
    before = source.read_bytes()
    canonical = ROOT / 'build/cli/omicsmatic-0.3.7.jar'
    canonical_before = pin(canonical)
    try:
        prior_home = ROOT / 'docs/literature/mtag-workflow-development'
        prior = prior_home / ('mtag-root-' + a.compiler + '.jar')
        previous_proof = json.loads((prior_home / 'VALIDATION.json').read_bytes())
        assert pin(prior) == previous_proof['artifacts'][prior.name]
        assert canonical_before == previous_proof['canonical_executable_unchanged']
        catalog = json.loads((ROOT / 'docs/literature/omics-2023-2026/mtag-catalog-validation.json').read_bytes())
        assert pin(source) == catalog['source_pins']['src/main/java/org/jlinalg/cli/JLinAlgCli.java']
        build = json.loads((a.build / 'COMPLETED.json').read_bytes())
        assert build['status'] == 'PASS_STANDALONE_SCDRS_WORKFLOW_DEVELOPMENT_BUILD_ONLY' and build['compiler'] == a.compiler
        standalone = ROOT / build['artifact']['path']
        assert pin(standalone) == {k: build['artifact'][k] for k in ['bytes', 'sha256']}
        for name, wanted in build['source_pins'].items(): assert pin(ROOT / name) == wanted, name
        newline = b'\r\n' if b'\r\n' in before else b'\n'
        def block(value): return value.replace('\n', newline.decode()).encode()
        anchor = block('        if (arguments.length > 0 && arguments[0].equals("mtag-tools"))\n')
        insertion = block('        if (arguments.length > 0 && arguments[0].equals("scdrs-tools"))\n            return ScdrsToolsCli.run(Arrays.copyOfRange(arguments, 1, arguments.length), output, errorOutput);\n')
        help_anchor = b'              java -jar omicsmatic-<version>.jar mtag-tools --help'
        assert before.count(anchor) == before.count(help_anchor) == 1 and b'arguments[0].equals("scdrs-tools")' not in before
        after = before.replace(anchor, insertion + anchor).replace(help_anchor, b'              java -jar omicsmatic-<version>.jar scdrs-tools --help' + newline + help_anchor)
        delta = inverse(before, after)
        delta['path'] = source.relative_to(ROOT).as_posix()
        assert len(delta['edits']) == 2 and reverse_delta(after, delta) == before
        save(out / 'staged-root-delta.json', dict(schema_version=1, status='STAGED_SCDRS_DISPATCH_HELP_ONLY_NOT_APPLIED', paths=[delta]))
        (out / 'root.before.java').write_bytes(before)
        staged = out / 'source-snapshot/src/main/java/org/jlinalg/cli/JLinAlgCli.java'
        staged.parent.mkdir(parents=True)
        staged.write_bytes(after)
        with zipfile.ZipFile(prior) as z: payload = {n: z.read(n) for n in z.namelist()}
        previous = dict(payload)
        additions = {}
        debug_deltas = []
        with zipfile.ZipFile(standalone) as z:
            for name in z.namelist():
                raw = z.read(name)
                if name.endswith('.class') or name.startswith('org/jlinalg/omics/scdrs/'):
                    if name in payload and payload[name] != raw:
                        assert name == 'org/jlinalg/cli/SimpleJson.class' and without_line_numbers(payload[name]) == without_line_numbers(raw)
                        debug_deltas.append(dict(path=name, predecessor=byte_pin(payload[name]), standalone=byte_pin(raw),
                            only_line_number_debug_metadata_differs=True, predecessor_retained_byte_identical=True))
                        continue
                    payload[name] = raw
                    additions[name] = raw
                elif name.startswith('META-INF/'):
                    payload['META-INF/scdrs/' + name[len('META-INF/'):]] = raw
        tools = Path('C:/Program Files') / ('Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin' if a.compiler == 'javac21' else 'Common Files/Oracle/Java/javapath')
        classes = out / 'classes'
        classes.mkdir()
        command = [str(tools / 'javac.exe'), '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8',
                   '-sourcepath', '', '-cp', ';'.join(map(str, [standalone, prior, canonical])), '-d', str(classes), str(staged)]
        result = subprocess.run(command, stdin=subprocess.DEVNULL, capture_output=True, timeout=180)
        (out / 'javac.stdout').write_bytes(result.stdout)
        (out / 'javac.stderr').write_bytes(result.stderr)
        save(out / 'javac.process.json', dict(command=command, exit_code=result.returncode))
        assert result.returncode == 0, result.stderr[-2000:]
        compiled = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
        assert set(compiled) == {'org/jlinalg/cli/JLinAlgCli.class'}
        payload.update(compiled)
        dependencies = {p.relative_to(ROOT).as_posix(): pin(p) for p in [prior, standalone, canonical]}
        payload['META-INF/scdrs/DEPENDENCIES.json'] = (json.dumps(dependencies, indent=2) + '\n').encode()
        artifact = out / 'scdrs-root-supplement.jar'
        with zipfile.ZipFile(artifact, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name, raw in sorted(payload.items()):
                member = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
                member.compress_type = zipfile.ZIP_DEFLATED
                member.external_attr = 0o100644 << 16
                z.writestr(member, raw)
        with zipfile.ZipFile(artifact) as z:
            assert all(z.read(n) == raw for n, raw in previous.items() if n != 'org/jlinalg/cli/JLinAlgCli.class')
            assert all(z.read(n) == raw for n, raw in additions.items())
        state.update(status='PASS_STAGED_SCDRS_ROOT_ALL_PREDECESSOR_SCIENTIFIC_BYTES_RETAINED', compiler=a.compiler,
            artifact=pin(artifact), dependencies=dependencies, source_before=byte_pin(before), staged_source=byte_pin(after),
            predecessor_entries=len(previous), new_entries=len(set(additions) - set(previous)), retained_shared_debug_deltas=debug_deltas,
            every_predecessor_entry_except_root_dispatch_byte_identical=True, builder=pin(Path(__file__)))
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
    assert source.read_bytes() == before and pin(canonical) == canonical_before
    state['canonical_executable_unchanged'] = canonical_before
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable staged root/source/predecessor preservation checks')
    raise SystemExit(main())
