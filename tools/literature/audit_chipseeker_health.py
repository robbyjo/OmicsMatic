# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Diagnose original health-fixture assumptions without changing any original result."""
import argparse
import json
import os
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, RSCRIPT, environment, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--runtime-setup', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args(); out = a.out.resolve(); setup = a.runtime_setup.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists()
    out.mkdir()
    script = ROOT / 'tools/literature/audit_chipseeker_health.R'
    libraries = [setup / 'R-lib', *map(Path, json.loads((setup / 'STARTED.json').read_bytes())['libraries'])]
    bounds = {file.relative_to(ROOT).as_posix(): pin(file) for file in (Path(__file__).resolve(), script)}
    save(out / 'STARTED.json', dict(source_pins=bounds, libraries=list(map(str, libraries)), analysis_admitted=False))
    command = [str(RSCRIPT), '--vanilla', str(script), str(out / 'audit'), os.pathsep.join(map(str, libraries))]
    with (out / 'stdout.log').open('xb') as stdout, (out / 'stderr.log').open('xb') as stderr:
        run = subprocess.run(command, cwd=ROOT, env=environment(), stdin=subprocess.DEVNULL,
                             stdout=stdout, stderr=stderr, timeout=600)
    save(out / 'process.json', dict(command=command, exit_code=run.returncode))
    assert run.returncode == 0 and all(pin(ROOT / file) == wanted for file, wanted in bounds.items())
    rows = {}
    for file in (out / 'audit').glob('*.json'):
        result = json.loads(file.read_bytes())
        rows[file.stem] = dict(transcript=result['selected_transcript'], annotation=result['original_annotation'],
                               matching_gene_associated_TSS_count=len(result['matching_gene_associated_TSS']))
    save(out / 'COMPLETED.json', dict(status='original-health-fixture-audit-not-analysis-admission', cases=rows,
                                     source_pins=bounds))
    print(json.dumps(rows))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable source bindings')
    main()
