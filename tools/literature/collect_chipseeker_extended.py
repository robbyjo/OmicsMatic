# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture original strand, priority and one-query classification controls with raw objects retained."""
import argparse
import csv
import json
import os
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT, RSCRIPT, environment, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--out', type=Path, required=True)
    out = p.parse_args().out.resolve(); assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    original = ROOT / 'build/literature-review/chipseeker-runtime/annotation-original-2'
    libraries = json.loads((original / 'STARTED.json').read_bytes())['libraries']
    fixture = original / 'fixture'; script = ROOT / 'tools/literature/collect_chipseeker_extended.R'
    bindings = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), script, fixture / 'peaks.tsv', fixture / 'synthetic-txdb.sqlite']}
    with (fixture / 'peaks.tsv').open(encoding='utf-8', newline='') as stream:peaks = list(csv.DictReader(stream, delimiter='\t'))
    for row in peaks:row.update(start=int(row['start']), end=int(row['end']), strand='*')
    priority = ['Promoter', '5UTR', '3UTR', 'Exon', 'Intron', 'Downstream', 'Intergenic']
    stranded = [dict(row, strand=['+', '-', '*'][i % 3]) for i, row in enumerate(peaks)]
    cases = [dict(id='stranded-same', group='stranded', same_strand=True, priority=priority, peaks=stranded),
        dict(id='stranded-unrestricted', group='stranded', same_strand=False, priority=priority, peaks=stranded),
        dict(id='priority-subset', group='base', same_strand=False, priority=['Exon', 'Intron'], peaks=peaks),
        dict(id='priority-duplicate', group='base', same_strand=False, priority=['Promoter', 'Exon', 'Promoter', '5UTR', '3UTR', 'Intron', 'Downstream', 'Intergenic'], peaks=peaks),
        dict(id='single-intergenic', group='intergenic', same_strand=False, priority=priority, peaks=[dict(peak_id='lone', contig='chr1', start=90000, end=90000, strand='*')]),
        dict(id='single-overlap', group='overlap', same_strand=False, priority=priority, peaks=[dict(peak_id='spanning', contig='chr1', start=10000, end=18000, strand='*')])]
    summaries = []
    for version in ['1.22.1', '1.26.2', '1.34.1', '1.48.1']:
        folder = out / version;folder.mkdir(); libs = libraries
        if version != '1.48.1':libs = [json.loads((original / version / 'reused-installation.json').read_bytes())['path'], *libraries]
        source = ROOT / 'build/literature-review/chipseeker-runtime/source-intake-2' / version
        receipt = json.loads((source / 'SOURCE.json').read_bytes())
        for name, expected in receipt['source_members'].items():
            file = source / 'selected-source' / name;assert pin(file) == expected;bindings[file.relative_to(ROOT).as_posix()] = expected
        save(folder / 'PLAN.json', dict(version=version, source_R=(source / 'selected-source/ChIPseeker/R').as_posix(), txdb=str(fixture / 'synthetic-txdb.sqlite'), cases=cases, libraries=libs))
        command = [str(RSCRIPT), '--vanilla', str(script), str(folder / 'PLAN.json'), str(folder), os.pathsep.join(libs)]
        with (folder / 'stdout.log').open('xb') as stdout, (folder / 'stderr.log').open('xb') as stderr:
            run = subprocess.run(command, cwd=ROOT, env=environment(), stdin=subprocess.DEVNULL, stdout=stdout, stderr=stderr, timeout=300)
        save(folder / 'process.json', dict(command=command, exit_code=run.returncode));assert run.returncode == 0, str(folder)
        result = json.loads((folder / 'ORIGINAL.json').read_bytes());assert len(result['cases']) == 6
        summary = dict(version=version, original_calls=6, returned=sum(c['source_error'] is None for c in result['cases']), original_errors=sum(c['source_error'] is not None for c in result['cases']))
        summaries.append(summary);print(json.dumps(summary), flush=True)
    assert all(pin(ROOT / name) == expected for name, expected in bindings.items())
    for name in bindings:
        copy = out / 'source-snapshot' / name;copy.parent.mkdir(parents=True, exist_ok=True);copy.write_bytes((ROOT / name).read_bytes())
    save(out / 'COMPLETED.json', dict(status='captured-original-extended-ChIPseeker-controls-not-adapter-admission', source_pins=bindings, editions=summaries,
        source_metadata_or_ids_repaired=False, historical_dependencies_recreated=False))


if __name__ == '__main__':
    if not __debug__:raise RuntimeError('Never disable original source checks')
    main()
