# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Summarize retained original errors and raw metadata without repairing source outputs."""
import argparse
from collections import Counter
import json
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--original', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args(); original = args.original.resolve(); out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists()
    completed = json.loads((original / 'COMPLETED.json').read_bytes())
    record = json.loads((original / 'RESULT.json').read_bytes())
    for name, expected in record['source_pins'].items():
        assert pin(ROOT / name) == expected, name
    out.mkdir(); bindings = {Path(__file__).resolve().relative_to(ROOT).as_posix(): pin(Path(__file__))}
    def read(path):
        bindings[path.relative_to(ROOT).as_posix()] = pin(path)
        return json.loads(path.read_bytes())
    failures = []; editions = []; labels = Counter(); coding = set(); vectors = {}
    for edition in record['editions']:
        folder = original / edition['version']; result = read(folder / 'controls/ORIGINAL.json')
        plan = {p['id']: p for p in read(folder / 'PLAN.json')['cases']}
        assert len(result['cases']) == len(plan) == 50
        nrows = 0; nfail = 0; promoter_checks = 0
        for case in result['cases']:
            if case['source_error'] is not None:
                nfail += 1
                failures.append(dict(version=edition['version'], case_id=case['id'],
                    original_error=case['source_error'], original_warnings=case['warnings'], settings=plan[case['id']]))
                continue
            path = folder / 'controls' / case['id']
            output = read(path / 'original-output.json'); nearest = read(path / 'original-nearest.json')
            rows = output['annotations']; nrows += len(rows)
            assert len(rows) == len(output['detail']) == len(nearest['retained_input_ordinals']) == case['returned_rows']
            assert [r['input_ordinal'] for r in rows] == nearest['retained_input_ordinals']
            assert [str(r['distanceToTSS']) for r in rows] == nearest['distance_tokens']
            if '-promoter-' in case['id']:
                lower, upper = plan[case['id']]['tss_region']
                for row, detail in zip(rows, output['detail']):
                    assert detail['Promoter'] == (lower <= row['distanceToTSS'] <= upper)
                    assert row['annotation'].startswith('Promoter') == detail['Promoter']
                    promoter_checks += 1
            labels.update(r['annotation'] for r in rows)
            for row, index in zip(rows, nearest['index_tokens']):
                ordinal = int(index)
                if ordinal > len(nearest['features']):
                    continue
                feature = nearest['features'][ordinal - 1]
                coding.add((str(row.get('geneChr')), feature['seqnames'], str(row.get('geneStrand')), feature['strand']))
            # Compare editions as captured. Differences are retained, never repaired.
            vectors.setdefault(case['id'], {})[edition['version']] = output
        editions.append(dict(version=edition['version'], original_calls=50,
            original_errors=nfail, retained_rows_across_returned_cases=nrows, exact_promoter_boundary_checks=promoter_checks))
    cross_edition_differences = []
    for case_id, values in vectors.items():
        first = next(iter(values.values()))
        if len(values) != 4 or any(value != first for value in values.values()):
            cross_edition_differences.append(dict(case_id=case_id, editions_with_results=list(values)))
    assert len(failures) == completed['original_errors'] == 48
    assert all(pin(ROOT / name) == expected for name, expected in bindings.items())
    result = dict(status='audited-original-ChIPseeker-controls-no-adapter-admission', source_pins=bindings,
        editions=editions, original_calls=completed['calls'], original_returned=completed['successes'],
        original_errors=failures, observed_annotation_labels=dict(sorted(labels.items())),
        raw_original_geneChr_and_geneStrand_pairs=[dict(raw_geneChr=a, literal_feature_contig=b,
            raw_geneStrand=c, literal_feature_strand=d) for a, b, c, d in sorted(coding)],
        cross_edition_scientific_output_differences=cross_edition_differences,
        whole_package_or_paper_reproduced=False, historical_dependencies_recreated=False,
        full_annotation_adapter_admitted=False)
    save(out / 'COMPLETED.json', result)
    grouped = Counter(f['original_error']['message'] for f in failures)
    text = '# ORIGINAL CHIPSEEKER ERRORS AND REQUIRED ACTIONS\n\n'
    text += '200 captured calls: 152 returned and 48 original errors. An original error is not an empty or admitted result.\n\n'
    for message, count in grouped.items():
        text += str(count) + ' cases: ' + message + '\n\n'
    text += ('Required action: read COMPLETED.json for exact case IDs/settings and the original per-case WARNINGS.md. '
             'Preserve failed/partial controls; reproduce the named cause before changing a request. Do not drop '
             'queries, change directions or substitute a generic annotation to force a result.\n\n'
             'The raw original geneChr and geneStrand fields have numeric codes in these controls. '
             'Read the separately captured literal ordered features and input ordinals. Do not silently relabel '
             'these original fields as chromosome names or +/- strands.\n\n'
             'Synthetic TxDb chromosome genome lengths have no real assembly version. The fixture constructor '
             'warning remains in fixture-process/stderr.log. These tests do not reproduce a paper or historical '
             'dependency environment, and full project annotation remains pending.\n')
    (out / 'WARNINGS.md').write_text(text, encoding='utf-8')
    print(json.dumps(dict(status=result['status'], editions=editions, error_groups=dict(grouped),
        distinct_labels=len(labels), cross_edition_differing_cases=len(cross_edition_differences))))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Never disable original source checks')
    main()
