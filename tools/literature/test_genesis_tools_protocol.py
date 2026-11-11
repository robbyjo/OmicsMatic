# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify saved GENESIS controls and reject coherently resealed scientific mutations, without running R."""
import argparse
import csv
import json
import shutil
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
from probe_king_runtime import DISTRO, linux
BASE = ROOT / 'build/literature-review/genesis-runtime'


def rows(file):
    with file.open(encoding='utf8', newline='') as stream:
        reader = csv.DictReader(stream, delimiter='\t'); return reader.fieldnames, list(reader)


def change(file, column, value, row=0):
    header, data = rows(file); data[row][column] = value(data[row][column]) if callable(value) else value
    with file.open('w', encoding='utf8', newline='') as stream:
        writer = csv.DictWriter(stream, fieldnames=header, delimiter='\t', lineterminator='\n'); writer.writeheader(); writer.writerows(data)


def inventory(home): return {p.relative_to(home).as_posix(): pin(p) for p in sorted(home.rglob('*')) if p.is_file() and p != home/'COMPLETED.json'}


def mutate_unrelated_basis(home):
    _, partition = rows(home/'worker/original-PC-partition.tsv'); alias = next(r['sample_alias'] for r in partition if r['set'] == 'unrelated')
    _, labels = rows(home/'worker/original-PC-vectors.rows.tsv'); source_row = next(r['row'] for r in labels if bytes.fromhex(r['row_name_utf8_hex']).decode('utf8') == alias)
    path = home/'worker/original-PC-vectors.values.tsv'; _, cells = rows(path); position = next(i for i,r in enumerate(cells) if r['row'] == source_row and r['column'] == '1')
    change(path, 'value_hex', lambda value: (float.fromhex(value)+.125).hex(), row=position)


def main():
    p = argparse.ArgumentParser(description=__doc__); p.add_argument('--controls', type=Path, required=True)
    p.add_argument('--jars', type=Path, nargs=2, required=True); p.add_argument('--out', type=Path, required=True); a = p.parse_args()
    controls = a.controls.resolve(); jars = [f.resolve() for f in a.jars]; out = a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE); out.mkdir(parents=True)
    state = dict(status='FAIL2_GENESIS_SAVED_PROTOCOL', original_analysis_executed=False, new_catalog_credit=False, whole_goal_complete=False)
    try:
        canonical = ROOT/'build/cli/omicsmatic-0.3.7.jar'; protected = {str(f): pin(f) for f in [canonical, *jars, Path(__file__).resolve()]}
        names = ['pcair', 'pcrelate', 'null-model-gaussian', 'association-gaussian', 'null-model-binomial', 'association-binomial']
        for name in names:
            home = controls/name; done = json.loads((home/'COMPLETED.json').read_bytes()); assert done['result_admitted'] is True and done['status'] == 'completed-original-GENESIS-with-explicit-warnings'
            protected.update({str(f): pin(f) for f in home.rglob('*') if f.is_file()})
        hosts = [('windows21', [str(Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe')), '-Xmx2g', '-jar', str(jars[0])]),
            ('windows25', [str(Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')), '-Xmx2g', '-jar', str(jars[1])]),
            ('linux21', ['wsl.exe', '-d', DISTRO, '--exec', '/usr/bin/java', '-Xmx2g', '-jar', linux(jars[0])])]
        records = []
        def verify(home, label, expected):
            for host, command in hosts:
                path = linux(home) if host.startswith('linux') else str(home); arguments = command + ['--verify', path]
                r = subprocess.run(arguments, cwd=ROOT, stdin=subprocess.DEVNULL, capture_output=True, timeout=180)
                stem = label+'-'+host; (out/(stem+'.stdout')).write_bytes(r.stdout); (out/(stem+'.stderr')).write_bytes(r.stderr)
                record = dict(label=label, host=host, expected_exit=expected, exit_code=r.returncode, command=arguments); save(out/(stem+'.json'), record); records.append(record)
                assert r.returncode == expected, (stem, r.stdout[-2500:], r.stderr[-5000:])
            print('PASS', label, flush=True)
        for name in names: verify(controls/name, 'saved-'+name, 0)
        mutants = [
            ('pca-unrelated-basis', 'pcair', mutate_unrelated_basis),
            ('pca-ordered-partition', 'pcair', lambda h: change(h/'worker/original-PC-partition.tsv','sample_alias','999')),
            ('pca-qc-population', 'pcair', lambda h: change(h/'worker/original-PC-QC-variants.tsv','input_variant_row','512')),
            ('pca-eigenvalue', 'pcair', lambda h: change(h/'worker/original-PC-eigenvalues.tsv','value_hex','0x1p+8')),
            ('pca-variance-placeholder', 'pcair', lambda h: change(h/'worker/original-PC-variance-proportions.tsv','is_nan','FALSE',row=3)),
            ('source-genotype', 'pcair', lambda h: change(h/'worker/original-complete-genotypes.tsv','1',lambda v: '0' if v != '0' else '2')),
            ('native-genotype', 'pcair', lambda h: change(h/'inputs/native-calls.tsv','1',lambda v: '0' if v != '0' else '2')),
            ('native-variant-census', 'pcair', lambda h: change(h/'native-variant-qc.tsv','called','0')),
            ('source-version', 'pcair', lambda h: change(h/'worker/original-package-versions.tsv','version','2.16.1')),
            ('source-parameter', 'pcair', lambda h: change(h/'worker/original-effective-parameters.tsv','value','pcrelate')),
            ('source-object-missing', 'pcair', lambda h: (h/'worker/original-result.rds').unlink()),
            ('source-worker', 'pcair', lambda h: (h/'worker.R').write_bytes((h/'worker.R').read_bytes()+b'\n# changed worker\n')),
            ('pcrelate-pair', 'pcrelate', lambda h: change(h/'worker/original-kinship-between.cells.tsv','value_hex','0x1p+2',row=4032)),
            ('pcrelate-covariance', 'pcrelate', lambda h: change(h/'worker/original-derived-covariance.values.tsv','value_hex','0x1p+8')),
            ('pcrelate-unassessable', 'pcrelate', lambda h: change(h/'worker/original-kinship-self.cells.tsv','is_na','TRUE')),
            ('model-design', 'null-model-gaussian', lambda h: change(h/'worker/original-design.values.tsv','value_hex','0x1p+2')),
            ('model-beta', 'null-model-gaussian', lambda h: change(h/'worker/original-fixed-effects.cells.tsv','value_hex','0x1p+2')),
            ('model-fitted-outcome', 'null-model-gaussian', lambda h: change(h/'worker/original-sample-fits.cells.tsv','value_hex','0x1p+2')),
            ('model-not-converged', 'null-model-gaussian', lambda h: change(h/'worker/original-model-status.tsv','converged','FALSE')),
            ('model-effective-engine', 'null-model-binomial', lambda h: change(h/'worker/original-model-status.tsv','effective_mixed','TRUE')),
            ('model-prominent-warnings', 'null-model-binomial', lambda h: (h/'WARNINGS.md').write_text('Completed.\n',encoding='utf8')),
            ('association-variant', 'association-gaussian', lambda h: change(h/'worker/original-association.cells.tsv','value_hex','0x1p+9')),
            ('association-frequency', 'association-gaussian', lambda h: change(h/'worker/original-association.cells.tsv','value_hex','0x1p-1',row=2040)),
            ('association-statistic', 'association-gaussian', lambda h: change(h/'worker/original-association.cells.tsv','value_hex','0x1p+5',row=4080)),
            ('association-dispositions', 'association-gaussian', lambda h: (h/'native-association-variant-dispositions.tsv').unlink()),
        ]
        mutant_home = out/'resealed-scientific-mutations'; mutant_home.mkdir()
        for name, source, mutation in mutants:
            home = mutant_home/name; shutil.copytree(controls/source,home); mutation(home)
            done = json.loads((home/'COMPLETED.json').read_bytes()); done['files'] = inventory(home); save(home/'COMPLETED.json',done)
            before = {str(f): pin(f) for f in home.rglob('*') if f.is_file()}; verify(home,'reject-'+name,2)
            assert all(pin(Path(n)) == v for n,v in before.items()), 'Saved verification changed the rejected evidence'
        assert all(pin(Path(n)) == v for n,v in protected.items()), 'Saved verification changed an original control, source, JAR or canonical executable'
        state.update(status='PASS_GENESIS_SAVED_CONTROLS_AND_RESEALED_SCIENTIFIC_REFUSALS', records=records,
            positive_verifications=len(names)*len(hosts), resealed_scientific_refusals=len(mutants)*len(hosts), protected=protected,
            scope='Saved Java replay of stated native contracts only. Mutations have refreshed file inventories, so byte inventory alone cannot reject them. No original R execution, producer authenticity or independent estimator refit.')
    except Exception as error:
        state['error'] = repr(error); (out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state); print(state['status'],state.get('error','')[:7000],flush=True); return 0 if state['status'].startswith('PASS_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Never disable source, canonical or rejected-evidence preservation checks')
    raise SystemExit(main())
