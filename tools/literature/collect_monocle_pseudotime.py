# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run exact original prepared-pseudotime helpers on deterministic independent graph controls."""
import argparse
import json
import os
from pathlib import Path
import random
import subprocess
from setup_deseq2_reference import ROOT, pin, save


def plan():
    cases = []
    def add(name, positions, principal, roots, edges, graph_order=None, cell_ids=None, unsupported=None):
        ids = cell_ids or ['cell-'+str(i) for i in range(len(positions))]
        vertices = graph_order or ids[:]
        cases.append(dict(id=name, cell_ids=ids, cell_coordinates=positions,
            principal_ids=['Y_'+str(i+1) for i in range(len(principal))], principal_coordinates=principal,
            roots=['Y_'+str(i+1) for i in roots], graph_vertex_ids=vertices,
            edges=[dict(from_=a, to=b, weight=w) for a,b,w in edges], native_unsupported=unsupported))
        for edge in cases[-1]['edges']:
            edge['from'] = edge.pop('from_')
    add('nearest-cell-is-origin', [[0,0],[4,0],[8,0]], [[2.1,0]], [0], [(0,1,3),(1,2,5)])
    add('nearest-tie-and-permuted-vertices', [[0,0],[2,0],[4,0]], [[1,0]], [0],
        [(0,1,2),(1,2,7)], ['cell-2','cell-0','cell-1'])
    add('duplicate-principal-and-cell-roots', [[0,0],[2,0],[4,0]], [[0,0],[0,0],[4,0]], [0,1,0,2], [(0,1,2),(1,2,3)])
    add('literal-IDs-and-disconnected', [[0,0],[1,0],[2,0]], [[0,0]], [0], [(0,1,1)],
        ['NA','重复"λ','unused-projection','zero'], ['NA','重复"λ','zero'])
    add('loops-parallel-zero-weight-edges', [[0,0],[1,0],[2,0],[3,0]], [[0,0]], [0],
        [(0,0,0),(0,1,2),(0,1,0),(1,2,0),(2,3,4),(3,0,10)])
    add('extra-projection-vertices', [[0,0],[10,0]], [[0,0]], [0], [(0,2,3),(2,1,7)], ['cell-0','cell-1','projection-node'])
    add('one-cell-no-edges', [[1,2]], [[1,2]], [0], [])
    add('several-cells-no-edges', [[0,0],[1,0],[2,0]], [[0,0]], [0], [])
    add('one-dimensional-single-root', [[0],[1],[2]], [[0]], [0], [(0,1,1),(1,2,2)])
    add('one-dimensional-multiple-roots', [[0],[1],[2]], [[0],[2]], [0,1], [(0,1,1),(1,2,2)], unsupported='original-one-dimensional-multiple-root-shape')
    add('source-distance-overflow-tie', [[-1e308,0],[0,0],[1e308,0]], [[1e308,1e308]], [0], [(0,1,1),(1,2,1)])
    add('path-sum-overflow', [[0,0],[1,0],[2,0]], [[0,0]], [0], [(0,1,1e308),(1,2,1e308)])
    add('subnormal-distance-tie', [[1e-300,0],[0,0],[1,0]], [[0,0]], [0], [(0,1,2),(1,2,3)])
    rng = random.Random(20261107)
    for number in range(160):
        n = rng.randint(2,34); dimensions = rng.randint(2,8); count = rng.randint(1,6)
        positions = [[rng.randint(-80,80)/8 for _ in range(dimensions)] for _ in range(n)]
        principal = [[rng.randint(-80,80)/8 for _ in range(dimensions)] for _ in range(count)]
        roots = [rng.randrange(count) for _ in range(rng.randint(1,7))]
        ids = ['c-'+str(i) for i in range(n)]
        vertices = ids+['projection-'+str(i) for i in range(rng.randint(0,4))]
        rng.shuffle(vertices)
        edges = []
        for _ in range(rng.randint(0,len(vertices)*3)):
            a = rng.randrange(len(vertices)); b = rng.randrange(len(vertices))
            if number % 4 == 0 and (a < len(vertices)//2) != (b < len(vertices)//2):
                continue
            edges.append((a,b,rng.randint(0,50)/8))
        add('random-'+str(number).zfill(3), positions, principal, roots, edges, vertices, ids)
    assert len(cases) == 173
    return dict(schema_version=1, scope='Caller-prepared graph ordering only; original class/accessors/helper bodies, no complete package fit', cases=cases)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', type=Path, required=True)
    out = parser.parse_args().out.resolve()
    assert out.is_relative_to(ROOT/'build/literature-review/monocle-runtime') and not out.exists()
    out.mkdir(parents=True)
    proxy = json.loads((ROOT/'build/literature-review/monocle-runtime/proxy-setup-1/process.json').read_bytes())
    assert proxy['exit_code'] == 0 and proxy['preexisting_libraries_unchanged']
    libraries = proxy['libraries']
    save(out/'PLAN.json', plan())
    worker = ROOT/'tools/literature/collect_monocle_pseudotime.R'
    bindings = {p.relative_to(ROOT).as_posix(): pin(p) for p in [Path(__file__).resolve(), worker]}
    records = []
    for version in ['1.0.0', '1.3.1', '1.4.27']:
        intake = ROOT/'build/literature-review/monocle-runtime/source-intake-1'/version
        receipt = json.loads((intake/'SOURCE.json').read_bytes())
        for name, wanted in receipt['source_members'].items():
            assert pin(intake/'source'/name) == wanted
        target = out/version
        target.mkdir()
        command = ['C:/Program Files/R/R-4.6.1/bin/Rscript.exe', '--vanilla', str(worker),
            str(intake/'source'), str(out/'PLAN.json'), str(target), *libraries]
        env = dict(os.environ, LC_ALL='C', LANG='C', LANGUAGE='en')
        result = subprocess.run(command, cwd=ROOT, env=env, capture_output=True, timeout=240)
        (target/'stdout.txt').write_bytes(result.stdout)
        (target/'stderr.txt').write_bytes(result.stderr)
        save(target/'process.json', dict(command=command, exit_code=result.returncode))
        assert result.returncode == 0, version+': inspect retained original stdout/stderr'
        original = json.loads((target/'ORIGINAL.json').read_bytes())
        assert len(original['cases']) == 173 and original['original_whole_package_loaded'] is False
        assert sum(r['returned'] for r in original['cases']) >= 160, 'Transport or source domain did not admit the baseline controls'
        assert next(r for r in original['cases'] if r['id']=='nearest-cell-is-origin')['returned']
        row = dict(version=version, cases=173, returned=sum(r['returned'] for r in original['cases']),
            errors=[dict(id=r['id'], message=r['source_error']['message']) for r in original['cases'] if not r['returned']])
        records.append(row)
        print(json.dumps(row), flush=True)
    assert all(pin(ROOT/n) == wanted for n,wanted in bindings.items())
    save(out/'COMPLETED.json', dict(status='executed-original-Monocle3-prepared-graph-helper-references',
        profiles=records, source_pins=bindings, plan=pin(out/'PLAN.json'), original_whole_package_loaded=False,
        original_fit_performed=False, implementation_credit=False, whole_goal_complete=False))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original source checks')
    main()
