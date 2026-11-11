# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain original grouped-peak preparation controls without claiming peak fits."""
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from signac_scan_evidence import pack


def main():
    base = ROOT / 'build/literature-review/signac-runtime'
    reference = base / 'peaks-split-linux-original-1/COMPLETED.json'
    value = json.loads(reference.read_bytes())
    assert value['status'] == 'passed-seven-original-Linux-fragment-split-controls'
    assert value['source_editions']['1.9.0'] == value['source_editions']['1.14.0'] == value['source_editions']['1.17.1']
    folder = ROOT / 'src/test/resources/org/jlinalg/sequence/signac-peak-preparation'
    assert not folder.exists()
    folder.mkdir()
    save(folder / 'original-cases.json', dict(schema_version=1, profile='Signac-1.9-1.14-1.17-Linux-split-source', cases=value['cases']))
    sources = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__),
        ROOT / 'tools/literature/collect_signac_peak_preparation.R',
        ROOT / 'tools/literature/collect_signac_peak_split_linux.py']}
    files = {}
    for name in ['peaks-preparation-original-1', 'peaks-preparation-original-2', 'peaks-split-linux-original-1']:
        for p in (base / name).rglob('*'):
            if p.is_file():
                files[p.relative_to(ROOT).as_posix()] = p.read_bytes()
    for version in ['1.9.0', '1.14.0', '1.17.1']:
        for name in ['src/split.cpp', 'R/peaks.R', 'R/fragments.R', 'LICENSE']:
            p = base / ('source-intake-1/' + version + '/source/Signac/' + name)
            files[p.relative_to(ROOT).as_posix()] = p.read_bytes()
    for n in sources:
        files['collector-source/' + n] = (ROOT / n).read_bytes()
    layout = pack(folder / 'original-controls.zip', files)
    save(folder / 'CAPTURE.json', dict(schema_version=1,
        status='retained-original-grouped-peak-preparation-not-MACS-fits',
        profile='Signac-1.9-1.14-1.17-Linux-split-source', archive=pin(folder / 'original-controls.zip'),
        cases=pin(folder / 'original-cases.json'), source_pins=sources, **layout,
        original_split_cases=7, original_Windows_cpp_controls=True, original_Linux_cpp_controls=True,
        unchanged_Linux_cpp_Rcpp_stub_scope=value['Rcpp_stub_scope'],
        newline_difference_preserved=True, actual_MACS_peak_fits=False,
        historical_whole_package_reproduction=False, catalog_implementation_credit=False))
    print('Frozen7 original split cases and original parsing/reduction controls for later peak integration')


if __name__ == '__main__':
    main()
