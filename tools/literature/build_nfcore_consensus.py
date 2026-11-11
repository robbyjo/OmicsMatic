# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package native consensus computation/auditing without changing frozen scientific artifacts."""
import argparse, json, shutil, subprocess, traceback, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_nfcore_engine import BASE, JAVA, JAVA25


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True); parser.add_argument('--compiler', choices=['21', '25'], required=True)
    parser.add_argument('--controls', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve(); assert out.is_relative_to(BASE) and not out.exists(); out.mkdir()
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar'); state = dict(status='FAIL2_NATIVE_CONSENSUS_BUILD', new_tool_credit=0)
    try:
        directory = ROOT / 'docs/literature/nfcore-captured-development'; proof = json.loads((directory / 'VALIDATION.json').read_bytes())
        predecessor = directory / 'nfcore-chipseq121-audit-javac21.jar'; assert pin(predecessor) == proof['artifacts'][predecessor.name]
        controls = json.loads((args.controls.resolve() / 'COMPLETED.json').read_bytes())
        assert controls['status'] == 'PASS_COMPLETE_NATIVE_CONSENSUS_AGAINST_ORIGINAL_TOOLS_ONLY'
        assert len(controls['cases']) == 12 and all(all(c['passed'] for c in row['checks']) for row in controls['cases'])
        assert controls['original_full_peak_population_replayed_byte_exactly']
        assert all(pin(ROOT / n) == v for n, v in controls['source_pins'].items())
        sources = [ROOT / 'src/main/java/org/jlinalg/workflow/NfcoreChipseq121Consensus.java', ROOT / 'src/main/java/org/jlinalg/cli/NfcoreConsensusAuditCli.java']
        source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in [*sources, Path(__file__)]}
        for name in source_pins:
            target = out / 'sources' / name; target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(ROOT / name, target)
        classes = out / 'classes'; classes.mkdir(); java = JAVA if args.compiler == '21' else JAVA25
        command = [str(java / 'javac.exe'), '--release', '17', '-sourcepath', '', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-cp', str(predecessor), '-d', str(classes), *map(str, sources)]
        result = subprocess.run(command, capture_output=True, timeout=120)
        (out / 'javac.stdout').write_bytes(result.stdout); (out / 'javac.stderr').write_bytes(result.stderr); save(out / 'javac.process.json', dict(command=command, exit_code=result.returncode)); assert result.returncode == 0, result.stderr
        for name, identity in controls['native_classes'][args.compiler].items(): assert pin(classes / name) == identity, ('Packaged computation differs from original-tool-tested classes', name)
        with zipfile.ZipFile(predecessor) as z: old = {n: z.read(n) for n in z.namelist()}
        payload = dict(old)
        for p in classes.rglob('*.class'):
            name = p.relative_to(classes).as_posix(); assert name not in payload; payload[name] = p.read_bytes()
        payload['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\nMain-Class: org.jlinalg.cli.NfcoreConsensusAuditCli\n\n'
        payload['META-INF/nfcore-consensus/NOTICE'] = b'Native original-source consensus computation and component auditing only; zero nf-core tool credit. Source merge-before-filter and unchanged BED-to-SAF Start conventions are retained. All predecessor classes/resources are unchanged. See the original runtime/tool controls and warnings before scientific use.\n'
        artifact = out / 'nfcore-consensus-development.jar'
        with zipfile.ZipFile(artifact, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name, raw in sorted(payload.items()):
                item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); item.compress_type = zipfile.ZIP_DEFLATED; item.external_attr = 0o100644 << 16; z.writestr(item, raw)
        with zipfile.ZipFile(artifact) as z: assert all(z.read(n) == raw for n, raw in old.items() if n != 'META-INF/MANIFEST.MF')
        assert all(pin(ROOT / n) == v for n, v in source_pins.items())
        state.update(status='PASS_NATIVE_CONSENSUS_BUILD_ONLY', compiler=args.compiler, source_pins=source_pins,
            artifact=dict(path=artifact.relative_to(ROOT).as_posix(), **pin(artifact)), predecessor=pin(predecessor),
            controls=pin(args.controls.resolve() / 'COMPLETED.json'), tested_native_classes_byte_identical=True,
            all_predecessor_members_except_manifest_unchanged=True, full_nfcore_adapter=False)
    except Exception as error: state.update(cause=repr(error), traceback=traceback.format_exc())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
    state['canonical_executable_unchanged'] = canonical; save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''), flush=True); return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable original-tool class binding or inherited-byte checks')
    raise SystemExit(main())
