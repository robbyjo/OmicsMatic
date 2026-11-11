# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a small dependency-free Java library for the two source-bound numerical methods."""
import argparse, json, shutil, subprocess, traceback, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_nfcore_engine import BASE, JAVA, JAVA25


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True); parser.add_argument('--compiler', choices=['21', '25'], required=True)
    parser.add_argument('--lowess-controls', type=Path, required=True); parser.add_argument('--filter-controls', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve(); assert out.is_relative_to(BASE) and not out.exists(); out.mkdir()
    canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar'); state = dict(status='FAIL2_SOURCE_NUMERICS_BUILD', new_tool_credit=0)
    try:
        lowess = json.loads((args.lowess_controls.resolve() / 'COMPLETED.json').read_bytes()); filtering = json.loads((args.filter_controls.resolve() / 'COMPLETED.json').read_bytes())
        assert lowess['status'] == 'PASS_NATIVE_LOWESS_AGAINST_ORIGINAL_R362_ONLY' and len(lowess['platforms']) == 6
        assert filtering['status'] == 'PASS_COMPLETE_NATIVE_DESEQ126_DEFAULT_FILTERING_ONLY' and len(filtering['platforms']) == 6
        assert all(pin(ROOT / n) == v for data in [lowess, filtering] for n, v in data['source_pins'].items())
        sources = [ROOT / 'src/main/java/org/jlinalg/loess/RLowess.java', ROOT / 'src/main/java/org/jlinalg/sequence/Deseq2IndependentFiltering.java', ROOT / 'src/main/java/org/jlinalg/sequence/Deseq2SizeFactors.java']
        resources = sorted((ROOT / 'src/main/resources/org/jlinalg/source-numerics').iterdir())
        source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in [*sources, *resources, Path(__file__)]}
        for name in source_pins:
            target = out / 'sources' / name; target.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(ROOT / name, target)
        classes = out / 'classes'; classes.mkdir(); java = JAVA if args.compiler == '21' else JAVA25
        command = [str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-Xlint:all', '-Werror', '-sourcepath', '', '-d', str(classes), *map(str, sources)]
        result = subprocess.run(command, capture_output=True, timeout=120)
        (out / 'javac.stdout').write_bytes(result.stdout); (out / 'javac.stderr').write_bytes(result.stderr); save(out / 'javac.process.json', dict(command=command, exit_code=result.returncode)); assert result.returncode == 0, result.stderr
        for data in [lowess, filtering]:
            for name, identity in data['native_classes'][args.compiler].items(): assert pin(classes / name) == identity, ('Packaged method differs from original-software-tested class', name)
        payload = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
        for p in resources:
            if p.name != '.gitattributes': payload['META-INF/source-numerics/' + p.name] = p.read_bytes()
        for p in sources: payload['META-INF/source-numerics/sources/' + p.relative_to(ROOT).as_posix()] = p.read_bytes()
        payload['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\nImplementation-Title: OmicsMatic source-specific native numerics\nImplementation-Version: development-r362-deseq126\n\n'
        artifact = out / 'source-numerics-development.jar'
        with zipfile.ZipFile(artifact, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as z:
            for name, raw in sorted(payload.items()):
                item = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); item.compress_type = zipfile.ZIP_DEFLATED; item.external_attr = 0o100644 << 16; z.writestr(item, raw)
        assert all(pin(ROOT / n) == v for n, v in source_pins.items())
        state.update(status='PASS_SOURCE_SPECIFIC_NATIVE_NUMERICS_BUILD_ONLY', compiler=args.compiler, source_pins=source_pins,
            artifact=dict(path=artifact.relative_to(ROOT).as_posix(), **pin(artifact)), tested_method_classes_byte_identical=True,
            lowess_controls=pin(args.lowess_controls.resolve() / 'COMPLETED.json'), filter_controls=pin(args.filter_controls.resolve() / 'COMPLETED.json'),
            external_runtime_required_for_native_computation=False, executable_entrypoint=False, full_nfcore_adapter=False)
    except Exception as error: state.update(cause=repr(error), traceback=traceback.format_exc())
    assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical; state['canonical_executable_unchanged'] = canonical
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable scientific class binding, source or canonical checks')
    raise SystemExit(main())
