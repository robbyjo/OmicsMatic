# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate the packaged native consensus audit on the complete original captured result."""
import argparse, json, subprocess, traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from validate_nfcore_engine import BASE, DISTRO, JAVA, JAVA25, linux


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True); parser.add_argument('--complete-attempt', type=Path, required=True)
    parser.add_argument('--artifact21', type=Path, required=True); parser.add_argument('--artifact25', type=Path, required=True)
    args = parser.parse_args(); out = args.out.resolve(); attempt = args.complete_attempt.resolve()
    assert out.is_relative_to(BASE) and not out.exists(); out.mkdir()
    sources = [ROOT / 'src/main/java/org/jlinalg/workflow/NfcoreChipseq121Consensus.java', ROOT / 'src/main/java/org/jlinalg/cli/NfcoreConsensusAuditCli.java', Path(__file__)]
    source_pins = {p.relative_to(ROOT).as_posix(): pin(p) for p in sources}
    state = dict(status='FAIL2_NATIVE_CONSENSUS_CLI_CONTROLS', new_tool_credit=0, scientific_result_admitted=False)
    try:
        artifacts = {'21': args.artifact21.resolve(), '25': args.artifact25.resolve()}; artifact_pins = {c: pin(p) for c, p in artifacts.items()}
        canonical = pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar'); controls = []; reports = []

        def run(label, command, expected):
            command = list(map(str, command))
            with (out / (label + '.stdout')).open('xb') as so, (out / (label + '.stderr')).open('xb') as se:
                result = subprocess.run(command, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=300)
            save(out / (label + '.process.json'), dict(command=command, expected_exit=expected, exit_code=result.returncode))
            assert result.returncode == expected, (label, result.returncode, (out / (label + '.stderr')).read_bytes()[-3000:])
            controls.append(dict(label=label, expected_exit=expected, exit_code=result.returncode)); print('PASS', label, flush=True)

        for label, compiler, vm in [('windows21', '21', JAVA / 'java.exe'), ('windows25', '25', JAVA25 / 'java.exe')]:
            target = out / label
            cmd = [vm, '-Xmx1024m', '-jar', artifacts[compiler], '--engine-result', attempt, '--peak-type', 'broadPeak', '--min-replicates', '1', '--out', target]
            run(label, cmd, 0); report = json.loads((target / 'audit.json').read_bytes()); reports.append(report)
            assert report['antibodies'][0]['native_consensus']['source_peak_occurrences'] == 3357
            assert report['antibodies'][0]['native_consensus']['retained_consensus_intervals'] == 1472
            assert len(report['antibodies']) == 1 and report['nfcore_tool_credit'] == 0 and not report['scientific_result_admitted']
            warnings = (target / 'WARNINGS.md').read_text(encoding='utf8'); assert 'zero-based Start directly to SAF' in warnings and 'separate declared analysis' in warnings
            before = pin(target / 'audit.json'); run(label + '-existing-output', cmd, 2); assert pin(target / 'audit.json') == before
            wrong = list(cmd); wrong[wrong.index('--min-replicates') + 1] = '2'; wrong[-1] = out / (label + '-wrong-threshold')
            run(label + '-wrong-threshold', wrong, 2)
            fail = json.loads((Path(wrong[-1]) / 'FAILURE.json').read_bytes()); assert fail['status'] == 'FAIL2_NO_CONSENSUS_COMPONENT_RESULT'
            wrong = list(cmd); wrong[wrong.index('--peak-type') + 1] = 'narrowPeak'; wrong[-1] = out / (label + '-wrong-kind')
            run(label + '-wrong-kind', wrong, 2)
            fail = json.loads((Path(wrong[-1]) / 'FAILURE.json').read_bytes()); assert fail['status'] == 'FAIL2_NO_CONSENSUS_COMPONENT_RESULT'
            assert 'narrowPeak' in fail['error'] and 'Required action:' in fail['error']
        for compiler in ['21', '25']:
            label = 'linux21-compiler' + compiler; target = out / label
            run(label, ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-Xmx1024m', '-jar', linux(artifacts[compiler]), '--engine-result', linux(attempt), '--peak-type', 'broadPeak', '--min-replicates', '1', '--out', linux(target)], 0)
            reports.append(json.loads((target / 'audit.json').read_bytes()))
        assert all(d == reports[0] for d in reports)
        assert all(pin(p) == artifact_pins[c] for c, p in artifacts.items()) and all(pin(ROOT / n) == v for n, v in source_pins.items())
        assert pin(ROOT / 'build/cli/omicsmatic-0.3.7.jar') == canonical
        state.update(status='PASS_PACKAGED_COMPLETE_NATIVE_CONSENSUS_AUDITS_ONLY', source_pins=source_pins, artifacts=artifact_pins,
            controls=controls, positive_platforms=4, refusals=6, full_reports_identical=True, source_peak_rows=3357,
            retained_consensus_intervals=1472, coordinate_warning_prominent=True, canonical_executable_unchanged=canonical,
            full_nfcore_adapter=False)
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc())
        (out / 'WARNINGS.md').write_text('# FAIL2 NO CONSENSUS COMPONENT RESULT\n\n' + repr(error) + '\n\nRequired action: preserve failed controls and original complete data. Correct the named cause and rerun to new paths. Do not alter source coordinate conventions, truncate inputs or weaken verification.\n', encoding='utf8')
    save(out / 'COMPLETED.json', state); print(state['status'], state.get('cause', ''), flush=True)
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable complete original population, required-action or packaged audit checks')
    raise SystemExit(main())
