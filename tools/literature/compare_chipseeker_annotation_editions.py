# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain exact original annotation differences between editions without merging their semantics."""
import argparse
from collections import Counter
import itertools
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--original', type=Path, required=True); p.add_argument('--out', type=Path, required=True)
    args = p.parse_args(); original = args.original.resolve(); out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists()
    completed = json.loads((original / 'COMPLETED.json').read_bytes())
    assert completed['calls'] == 200 and completed['successes'] == 152 and completed['original_errors'] == 48
    out.mkdir(); inputs = {}; versions = {}; source_lines = {}
    def read(path):
        inputs[path.relative_to(ROOT).as_posix()] = pin(path)
        return json.loads(path.read_bytes())
    source_record = read(original / 'RESULT.json')
    for name, expected in source_record['source_pins'].items():
        assert pin(ROOT / name) == expected, name
    for edition in source_record['editions']:
        version = edition['version']; folder = original / version
        result = read(folder / 'controls/ORIGINAL.json'); values = {}
        for case in result['cases']:
            if case['source_error'] is None:
                values[case['id']] = read(folder / 'controls' / case['id'] / 'original-output.json')
        versions[version] = values
        source = ROOT / 'build/literature-review/chipseeker-runtime/source-intake-2' / version / 'selected-source/ChIPseeker/R/getGenomicAnnotation.R'
        inputs[source.relative_to(ROOT).as_posix()] = pin(source)
        source_lines[version] = [dict(line=i + 1, text=line) for i, line in enumerate(source.read_text(encoding='utf-8').splitlines())
            if any(text in line for text in ('idx <- precede(', 'idx <- follow(', 'for(i in 1:3)', 'dsd <- getOption(', 'abs(dd2) <= dsd'))]
    summaries = []; all_deltas = []
    for left, right in itertools.combinations(versions, 2):
        assert versions[left].keys() == versions[right].keys()
        changed_cases = 0; field_counts = Counter()
        for case_id, a in versions[left].items():
            b = versions[right][case_id]; deltas = []
            assert a['level'] == b['level'] and a['tss_region'] == b['tss_region'] and a['peak_num'] == b['peak_num']
            assert [r['input_ordinal'] for r in a['annotations']] == [r['input_ordinal'] for r in b['annotations']]
            for section in ('annotations', 'detail'):
                assert len(a[section]) == len(b[section])
                for index, (x, y) in enumerate(zip(a[section], b[section])):
                    for key in sorted(x.keys() | y.keys()):
                        if key not in x or key not in y or x[key] != y[key]:
                            deltas.append(dict(section=section, input_ordinal=a['annotations'][index]['input_ordinal'],
                                field=key, left=x.get(key, {'absent': True}), right=y.get(key, {'absent': True})))
                            field_counts[section + '.' + key] += 1
            da = {r['Feature']: r for r in a['distribution']}; db = {r['Feature']: r for r in b['distribution']}
            for feature in sorted(da.keys() | db.keys()):
                if da.get(feature) != db.get(feature):
                    deltas.append(dict(section='distribution', feature=feature, left=da.get(feature, {'absent': True}),
                                       right=db.get(feature, {'absent': True})))
                    field_counts['distribution'] += 1
            if deltas:
                changed_cases += 1
                all_deltas.append(dict(left_version=left, right_version=right, case_id=case_id, differences=deltas))
        summaries.append(dict(left_version=left, right_version=right, common_returned_cases=len(versions[left]),
                              differing_cases=changed_cases, changed_fields=dict(field_counts)))
    script = Path(__file__).resolve(); inputs[script.relative_to(ROOT).as_posix()] = pin(script)
    save(out / 'DELTAS.json', dict(status='captured-original-edition-differences-not-repaired', source_pins=inputs,
         summaries=summaries, original_source_lines=source_lines, case_differences=all_deltas,
         historical_dependency_environment_reproduced=False, project_adapter_admitted=False))
    assert all(pin(ROOT / name) == expected for name, expected in inputs.items())
    save(out / 'COMPLETED.json', dict(status='audited-six-pairs-of-original-ChIPseeker-annotation-editions',
         summaries=summaries, exact_deltas=pin(out / 'DELTAS.json'), source_pins=inputs))
    (out / 'WARNINGS.md').write_text('# EDITION-SPECIFIC ORIGINAL ANNOTATION: REQUIRED ACTION\n\n'
        'The nearest-TSS component is shared by these sources, while their genomic classification differs. '
        'The older sources use precede() in downstream annotation; the newer sources use follow(). '
        'Keep each exact requested edition and its source/defaults. Do not replace historical annotation '
        'with a modern label or merge these outputs as interchangeable. Read DELTAS.json for every exact '
        'changed label, detail flag and distribution cell. All runs use current recorded dependencies, '
        'so historical dependency environments and whole papers are not reproduced.\n', encoding='utf-8')
    print(json.dumps(summaries))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original source checks')
    main()
