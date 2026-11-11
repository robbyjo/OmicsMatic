# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain complete original CoveragePlot and heterogeneous scaling/pruning controls."""
import gzip
import json
from setup_deseq2_reference import ROOT,pin,save


def main():
    folder=ROOT/'build/literature-review/signac-runtime/coverage-original-edges-3'
    fixture=ROOT/'src/test/resources/org/jlinalg/sequence/signac-coverage'
    assert (folder/'exit.txt').read_text()=='0' and not (fixture/'edges-v2.json.gz').exists()
    source=folder/'original/RESULT.json';value=json.loads(source.read_bytes())
    assert len(value['full'])==3 and all('error' not in row['result']['value'] for row in value['full'])
    assert len(value['scaling'])==len(value['pruning'])==6
    assert all(row['result']['value']['rows_from_complete_plot'] for row in value['full'])
    (fixture/'edges-v2.json.gz').write_bytes(gzip.compress(source.read_bytes(),mtime=0))
    save(fixture/'EDGES-CAPTURE-v2.json',dict(original=pin(source),compressed=pin(fixture/'edges-v2.json.gz'),
         source_profiles=['1.9.0','1.14.0','1.17.1'],actual_complete_original_plots=3,historical_whole_environments=False,
         source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/collect_signac_coverage_edges.R']}))
    print('Retained3 complete original coverage plots plus12 scaling/pruning controls')


if __name__=='__main__':
    from pathlib import Path
    main()
