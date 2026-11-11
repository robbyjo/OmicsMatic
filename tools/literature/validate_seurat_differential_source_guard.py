# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Reject process-local original body/formal changes without editing installed libraries."""
import argparse
import json
import shutil
import subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT, RSCRIPT, environment, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', type=Path, required=True)
    out = p.parse_args().out.resolve()
    base = ROOT / 'build/literature-review/signac-runtime'
    assert out.is_relative_to(base) and not out.exists()
    out.mkdir()
    original = base / 'differential-workflow-matrix-2/windows-java21-pair_wilcox-结果'
    wrapper = ROOT / 'tools/literature/probe_seurat_differential_source_guard.R'
    sources = {str(p.relative_to(ROOT)): pin(p) for p in [Path(__file__), wrapper]}
    runs = []
    try:
        for kind in ['body', 'formals']:
            folder = out / kind
            folder.mkdir()
            shutil.copytree(original / 'source', folder / 'source')
            worker = folder / 'worker'
            worker.mkdir()
            for name in ['runtime-paths.txt', 'parameters.json']:
                shutil.copyfile(original / 'worker' / name, worker / name)
            argv = list(map(str, [RSCRIPT, '--vanilla', wrapper, kind]))
            with (folder / 'stdout').open('xb') as stdout, (folder / 'stderr').open('xb') as stderr:
                result = subprocess.run(argv, cwd=worker, env=environment(), stdin=subprocess.DEVNULL,
                                        stdout=stdout, stderr=stderr, timeout=120)
            save(folder / 'PROCESS.json', dict(command=argv, exit_code=result.returncode))
            assert result.returncode == 2
            report = json.loads((worker / 'worker-report.json').read_bytes())
            assert report['status'] == 'FAIL2_NO_RESULT_ADMITTED'
            assert report['error'] == 'Loaded source function body/formals differ: FindMarkers.Assay', report
            assert not (worker / 'raw-group-markers.tsv').exists()
            runs.append(dict(control=kind, status=report['status'], error=report['error']))
        assert all(pin(ROOT / n) == value for n, value in sources.items())
        save(out / 'COMPLETED.json', dict(status='passed-original-worker-body-and-formal-guard-refusals',
                                         source_pins=sources, runs=runs,
                                         mutation_scope='Test-only R process memory; installed libraries unchanged',
                                         statistical_tests_executed=False))
        print('Original worker refused process-local body and formal changes before testing')
    except Exception as error:
        save(out / 'FAILURE.json', dict(status='FAIL2_SEURAT_SOURCE_GUARD_CONTROLS', error=str(error)))
        raise


if __name__ == '__main__':
    main()
