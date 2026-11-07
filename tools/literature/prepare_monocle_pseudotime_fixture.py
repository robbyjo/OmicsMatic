# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package exact original helper references; exclude the invalidated first transport run."""
import argparse
import gzip
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists()
    out.mkdir(parents=True)
    original = ROOT/'build/literature-review/monocle-runtime/pseudotime-original-2'
    proof = json.loads((original/'COMPLETED.json').read_bytes())
    assert proof['status'] == 'executed-original-Monocle3-prepared-graph-helper-references'
    assert all(pin(ROOT/n) == v for n,v in proof['source_pins'].items())
    plan = json.loads((original/'PLAN.json').read_bytes())
    assert pin(original/'PLAN.json') == proof['plan'] and len(plan['cases']) == 173
    controls = []
    source_pins = {}
    versions = ['1.0.0', '1.3.1', '1.4.27']
    resources = ROOT/'src/main/resources/org/jlinalg/omics/monocle3-pseudotime'
    assert not resources.exists()
    resources.mkdir(parents=True)
    for version in versions:
        folder = ROOT/'build/literature-review/monocle-runtime/source-intake-1'/version
        receipt = json.loads((folder/'SOURCE.json').read_bytes())
        result = json.loads((original/version/'ORIGINAL.json').read_bytes())
        assert result['original_whole_package_loaded'] is False and result['original_fit_performed'] is False
        assert sum(r['returned'] for r in result['cases']) == 172
        errors = [r for r in result['cases'] if not r['returned']]
        assert len(errors) == 1 and errors[0]['id'] == 'one-dimensional-multiple-roots'
        assert errors[0]['source_error']['message'] == 'x and y must be conform in columns.'
        assert [r['id'] for r in result['cases']] == [r['id'] for r in plan['cases']]
        for row in result['cases']:
            assert json.loads((original/version/row['id']/'original.json').read_bytes()) == row
            controls.append(dict(version=version, **row))
        for name in ['DESCRIPTION', 'LICENSE', 'LICENSE.md', 'R/cell_data_set.R', 'R/generics.R', 'R/learn_graph.R', 'R/order_cells.R']:
            source = folder/'source'/name
            if not source.exists() and name == 'LICENSE.md':
                continue
            assert pin(source) == receipt['source_members'][name]
            target = resources/version/name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(source.read_bytes())
            source_pins[version+'/'+name] = pin(target)
    save(resources/'SOURCE-PROFILES.json', dict(schema_version=1, official_repository='https://github.com/cole-trapnell-lab/monocle3',
        profiles={v: json.loads((ROOT/'build/literature-review/monocle-runtime/source-intake-1'/v/'SOURCE.json').read_bytes())['commit'] for v in versions},
        source_members=source_pins, functions=['find_nearest_vertex', 'extract_general_graph_ordering'],
        original_class_and_accessor_declarations=True, full_package_reimplemented=False, full_package_fitted=False))
    target = ROOT/'src/test/resources/org/jlinalg/singlecell/monocle3-pseudotime/original-controls-v1.json.gz'
    assert not target.exists()
    target.parent.mkdir(parents=True, exist_ok=True)
    raw = (json.dumps(dict(schema_version=1, plan=plan, controls=controls, original_whole_package_loaded=False,
        original_fit_performed=False, excluded_transport='pseudotime-original-1: invalid SimpleList fixture transport'),
        ensure_ascii=False, sort_keys=True, separators=(',',':'), allow_nan=False)+'\n').encode('utf8')
    target.write_bytes(gzip.compress(raw, mtime=0))
    save(out/'COMPLETED.json', dict(status='prepared-three-profile-original-helper-reference-fixture',
        fixture=dict(path=target.relative_to(ROOT).as_posix(), **pin(target)),
        raw_bytes=len(raw), original_calls=519, original_returns=516, original_errors=3,
        resource_files=len(source_pins)+1, resource_pins=source_pins,
        source_pins={p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), original/'COMPLETED.json', original/'PLAN.json']},
        full_package_fit=False, workflow_admitted=False, implementation_credit=False))
    print(json.dumps(dict(fixture=pin(target), raw_bytes=len(raw), original_returns=516, original_errors=3, resource_files=len(source_pins)+1)))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable exact original reference checks')
    main()
