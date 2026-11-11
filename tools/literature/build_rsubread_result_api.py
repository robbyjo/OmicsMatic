# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build a JDK-only result-value API, preserving every previous matrix class exactly."""
import argparse, json, shutil, subprocess, traceback, zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import byte_pin


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True); parser.add_argument('--javac', default='javac')
    args = parser.parse_args(); out = args.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/rsubread-runtime'); out.mkdir(parents=True)
    state = dict(status='FAIL2_RSUBREAD_RESULT_API_BUILD', implementation_credit=False, analysis_admitted=False,
                 full_adapter_available=False, whole_goal_complete=False)
    try:
        from rsubread_matrix_evidence import verify
        prior, _ = verify(False); assert prior['native_matrix_component_available']
        previous = ROOT / 'docs/literature/rsubread-matrix-development/rsubread-count-matrix-api.jar'
        sources = [ROOT / ('src/main/java/org/jlinalg/sequence/' + name + '.java')
                   for name in ['RsubreadCountMatrix', 'RsubreadDataFrame', 'RsubreadCountResult']]
        wire = ROOT / 'src/main/resources/org/jlinalg/omics/rsubread_result_wire.R'
        javac = Path(shutil.which(args.javac) or args.javac).resolve(); assert javac.is_file()
        classes = out / 'classes'; classes.mkdir()
        command = [str(javac), '--release', '17', '-Xlint:all', '-Werror', '-g', '-encoding', 'UTF-8', '-d', str(classes), *map(str, sources)]
        run = subprocess.run(command, capture_output=True, timeout=120)
        (out / 'compile.stdout').write_bytes(run.stdout); (out / 'compile.stderr').write_bytes(run.stderr)
        save(out / 'compile.process.json', dict(command=command, exit_code=run.returncode))
        assert run.returncode == 0, run.stderr.decode(errors='replace')
        payload = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
        with zipfile.ZipFile(previous) as archive:
            original = {n: archive.read(n) for n in archive.namelist() if n.endswith('.class')}
            assert len(original) == 6 and all(payload[n] == b for n, b in original.items())
            license_bytes = archive.read('META-INF/LICENSE')
        assert len(payload) == 14
        manifest = b'Manifest-Version: 1.0\nImplementation-Title: OmicsMatic native Rsubread result values\nImplementation-Version: 0.3.7-development\n\n'
        jar = out / 'rsubread-result-values-api.jar'
        entries = dict(payload, **{'META-INF/MANIFEST.MF': manifest, 'META-INF/LICENSE': license_bytes,
                                  'org/jlinalg/omics/rsubread_result_wire.R': wire.read_bytes()})
        with zipfile.ZipFile(jar, 'x', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
            for name, raw in sorted(entries.items()):
                info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0)); info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o100644 << 16; archive.writestr(info, raw)
        state.update(status='passed-JDK-only-native-Rsubread-result-value-API-build',
                     artifact=dict(path=jar.relative_to(ROOT).as_posix(), **pin(jar)),
                     class_entries={n: byte_pin(b) for n, b in payload.items()},
                     previous_matrix_class_family_unchanged=True, previous_api=dict(path=previous.relative_to(ROOT).as_posix(), **pin(previous)),
                     source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), *sources, wire]},
                     runtime_dependencies='JDK only; the packaged optional value exporter requires R only when invoked')
    except Exception as error:
        state['error'] = repr(error); (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('error', ''))
    return 0 if state['status'].startswith('passed-') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete source/class-family checks')
    raise SystemExit(main())
