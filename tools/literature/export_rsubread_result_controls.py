# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export complete literal result values from both frozen original Rsubread families; no refitting."""
import argparse, json, os, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save, environment


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/rsubread-runtime')
    out.mkdir(parents=True)
    state = dict(status='FAIL2_RSUBREAD_RESULT_VALUE_EXPORT', analysis_admitted=False,
                 implementation_credit=False, full_adapter_available=False, whole_goal_complete=False)
    try:
        from rsubread_matrix_evidence import verify
        proof, _ = verify(False)
        assert proof['native_matrix_component_available'] and not proof['full_adapter_available']
        base = ROOT / 'build/literature-review/rsubread-runtime'
        script = ROOT / 'tools/literature/export_rsubread_result_controls.R'
        wire = ROOT / 'src/main/resources/org/jlinalg/omics/rsubread_result_wire.R'
        source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), script, wire]}
        profiles = [
            ('2260', base / 'original-controls-3', Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe'), '4.6.1', None),
            ('2232-R441', base / 'original2232-controls-1', base / 'r441-setup-1/R441/root/bin/x64/Rscript.exe', '4.4.1', base / 'r441-setup-1/R441/root')]
        references = {}
        for name, original, rscript, version, rhome in profiles:
            receipt = json.loads((original / 'COMPLETED.json').read_bytes())
            assert receipt['status'].startswith('passed-sixteen-complete-original-Rsubread')
            before = {p.relative_to(original).as_posix(): pin(p) for p in original.rglob('*') if p.is_file()}
            runtime_pin = pin(rscript); home = out / name; home.mkdir()
            env = environment()
            if rhome: env['R_HOME'] = rhome.as_posix()
            command = [str(rscript), '--vanilla', str(script), str(wire), str(original / 'original'), str(home), version]
            with (out / (name + '.stdout')).open('xb') as first, (out / (name + '.stderr')).open('xb') as second:
                run = subprocess.run(command, cwd=ROOT, env=env, stdin=subprocess.DEVNULL, stdout=first,
                                     stderr=second, timeout=120, creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
            save(out / (name + '.process.json'), dict(command=command, exit_code=run.returncode))
            assert run.returncode == 0, 'Preserve all partial outputs and original diagnostics; no export admitted'
            assert (home / 'R-status.txt').read_text().strip() == 'PASS16_ORIGINAL_RESULT_VALUE_EXPORTS_3_SCALAR_CONTROLS_2_ATTRIBUTE_REJECTIONS'
            assert pin(rscript) == runtime_pin and all(pin(original / p) == v for p, v in before.items())
            references[name] = dict(original_receipt=dict(path=(original / 'COMPLETED.json').relative_to(ROOT).as_posix(), **pin(original / 'COMPLETED.json')),
                                    original_objects={p: v for p, v in before.items() if p.endswith('/complete-original-result.rds')},
                                    rscript=dict(path=str(rscript), **runtime_pin), r_version=version)
        assert all(pin(ROOT / p) == v for p, v in source_pins.items())
        state.update(status='passed-two-original-Rsubread-result-value-export-families-without-refitting',
                     references=references, original_results=32, scalar_controls=6, attribute_rejections=4,
                     source_pins=source_pins, original_evidence_unchanged=True, Rsubread_loaded=False,
                     original_R_object_attributes_preserved_by='Retained original complete-original-result.rds, not the scalar wire')
    except Exception as error:
        state['error'] = repr(error); (out / 'error.traceback').write_text(traceback.format_exc(), encoding='utf8')
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('error', ''))
    return 0 if state['status'].startswith('passed-') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable source, original-object and runtime guards')
    raise SystemExit(main())
