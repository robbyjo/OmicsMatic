# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Collect immutable original-version scDRS edge-control receipts and process logs."""
import argparse
import json
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from setup_scdrs_reference import BASE, linux
from intake_scdrs_reference import VERSIONS


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', required=True, type=Path)
    p.add_argument('--setup', required=True, type=Path)
    p.add_argument('--native-validation', required=True, type=Path)
    a = p.parse_args()
    out = a.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists()
    out.mkdir(parents=True)
    state = dict(status='FAIL2_SCDRS_SOURCE_EDGE_CONTROLS', new_catalog_credit=False)
    helper = ROOT / 'tools/literature/capture_scdrs_source_edges.py'
    (out / 'capture-at-attempt.py').write_bytes(helper.read_bytes())
    (out / 'validator-at-attempt.py').write_bytes(Path(__file__).read_bytes())
    try:
        setup = json.loads((a.setup / 'COMPLETED.json').read_bytes())
        assert setup['status'] == 'PASS_THREE_ORIGINAL_SCDRS_SOURCE_IMPORTS_SETUP_ONLY'
        records = {}
        for version, commit in VERSIONS.items():
            source = BASE / 'source-intake-1' / version / 'source'
            profile = setup['source_inventory'][version]
            assert profile['commit'] == commit
            for name, wanted in profile['files'].items(): assert pin(source / name) == wanted
            command = setup['clean_environment'] + [setup['python'], linux(helper), '--source', linux(source),
                '--input-h5ad', linux(a.native_validation / ('reference-' + version) / 'workflow-0.input.h5ad'),
                '--out', linux(out / ('version-' + version))]
            save(out / (version + '.command.json'), dict(command=command))
            with (out / (version + '.stdout')).open('xb') as stdout, (out / (version + '.stderr')).open('xb') as stderr:
                result = subprocess.run(command, cwd=ROOT, stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=180)
            save(out / (version + '.process.json'), dict(exit_code=result.returncode))
            assert result.returncode == 0
            record = json.loads((out / ('version-' + version) / 'COMPLETED.json').read_bytes())
            assert record['status'] == 'PASS_ORIGINAL_SCDRS_EDGE_OUTCOMES_RETAINED' and record['version'] == version
            records[version] = record
        state.update(status='PASS_THREE_ORIGINAL_SCDRS_CATEGORICAL_AND_ZERO_SD_EDGE_CONTROLS',
                     records=records, helper=pin(helper))
    except Exception as error:
        state['cause'] = repr(error)
    save(out / 'COMPLETED.json', state)
    print(state['status'], state.get('cause', ''))
    return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable original-source edge controls')
    raise SystemExit(main())
