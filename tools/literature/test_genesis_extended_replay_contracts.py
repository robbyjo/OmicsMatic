# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise public extended GENESIS saved replays and explicit prerequisite/refusal paths."""
import argparse
import json
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import DISTRO, linux
from verify_genesis_extended_evidence import HOME


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', type=Path, required=True); a = p.parse_args(); out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(ROOT / 'build/literature-review/genesis-runtime'); out.mkdir(parents=True)
    helper = ROOT / 'tools/literature/verify_genesis_extended_evidence.py'
    python = Path('C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe')
    protected = {str(f): pin(f) for f in [helper, Path(__file__), *[p for p in HOME.iterdir() if p.is_file()]]}
    hosts = [
        ('windows21', [str(python), str(helper)], 'C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe', '21'),
        ('windows25', [str(python), str(helper)], 'C:/Program Files/Common Files/Oracle/Java/javapath/java.exe', '25'),
        ('linux21', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/python3', linux(helper)], '/usr/bin/java', '21')]
    records = []

    def run(command, label, expected):
        result = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, timeout=360)
        (out / (label + '.stdout')).write_bytes(result.stdout); (out / (label + '.stderr')).write_bytes(result.stderr)
        record = dict(label=label, command=command, expected_exit=expected, exit_code=result.returncode)
        save(out / (label + '.process.json'), record); records.append(record)
        assert result.returncode == expected, (label, result.stdout[-6000:], result.stderr[-6000:])
        print('PASS', label, flush=True)

    for host, prefix, java, compiler in hosts:
        home = out / ('saved-' + host); path = linux(home) if host.startswith('linux') else str(home)
        command = [*prefix, '--out', path, '--java', java, '--jar-compiler', compiler]
        run(command, 'saved-' + host, 0)
        done = json.loads((home / 'COMPLETED.json').read_bytes())
        assert done['status'] == 'PASS_NATIVE_RANK_AND_EXTENDED_GENESIS_SAVED_REPLAYS_WITHOUT_R'
        assert len(done['saved_workflows']) == 10 and done['native_rank']['complete_scores_compared'] == 252088
        assert done['original_R_analysis_executed'] is False and done['downloads_or_installation'] is False
        skipped = out / ('missing-java-' + host); path = linux(skipped) if host.startswith('linux') else str(skipped)
        absent = '/no-such-java/omicsmatic-java17' if host.startswith('linux') else str(out / 'no-such-java.exe')
        run([*prefix, '--out', path, '--java', absent], 'missing-java-' + host, 3)
        status = json.loads((skipped / 'COMPLETED.json').read_bytes())
        assert status['status'] == 'SKIP3_GENESIS_EXTENDED_JAVA_UNAVAILABLE'
        assert 'REQUIRED ACTION' in (skipped / 'WARNINGS.md').read_text(encoding='utf8')
        before = {str(f): pin(f) for f in home.rglob('*') if f.is_file()}
        run(command, 'existing-output-' + host, 2)
        assert all(pin(Path(n)) == v for n, v in before.items())
    assert all(pin(Path(n)) == v for n, v in protected.items())
    save(out / 'COMPLETED.json', dict(status='PASS30_PUBLIC_WORKFLOW_REPLAYS_756264_RANK_SCORES_AND_EXPLICIT_JAVA_REFUSALS', records=records,
         producer=pin(Path(__file__)), source_proof=pin(HOME / 'VALIDATION.json'), saved_workflow_replays=30, complete_rank_scores=756264,
         missing_java_skips=3, existing_output_refusals=3, new_catalog_credit=False, whole_goal_complete=False))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable frozen evidence preservation or expected dispositions')
    main()
