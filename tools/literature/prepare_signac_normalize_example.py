# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify the checked normalization example against its retained original-source control."""
import zipfile
from setup_deseq2_reference import ROOT, pin, save


def main():
    source = ROOT / 'src/test/resources/org/jlinalg/sequence/signac-normalization/original-controls.zip'
    example = ROOT / 'examples/signac-normalize'
    target = example / 'INPUT-PROVENANCE.json'
    assert not target.exists()
    name = 'hand-count-versus-document-frequency'
    with zipfile.ZipFile(source) as archive:
        def read(key):
            return archive.read(name + '/' + key).decode('utf8')
        for key in ['features.tsv', 'cells.tsv']:
            assert (example / key).read_bytes() == read(key).encode('utf8')
        pointers = list(map(int, read('pointers.tsv').strip().split(',')))
        indices = list(map(int, read('indices.tsv').strip().split(',')))
        values = list(map(int, read('values.tsv').strip().split(',')))
        lines = (example / 'counts.mtx').read_text(encoding='utf8').splitlines()
        assert lines[0] == '%%MatrixMarket matrix coordinate integer general'
        rows = [list(map(int, line.split())) for line in lines if not line.startswith('%')]
        assert rows[0] == [len(read('features.tsv').splitlines()), len(read('cells.tsv').splitlines()), len(values)]
        expected = [[indices[k] + 1, cell + 1, values[k]] for cell in range(3)
                    for k in range(pointers[cell], pointers[cell + 1])]
        assert rows[1:] == expected
        idf = (example / 'idf.tsv').read_text(encoding='utf8').splitlines()
        assert idf[0] == 'feature\tidf'
        assert [line.split('\t') for line in idf[1:]] == list(map(list, zip(read('features.tsv').splitlines(), read('idf.tsv').strip().split(','))))
        assert (example / 'existing.tsv').read_text(encoding='utf8').splitlines() == ['chr1:200-280', 'chr1:100-180']
    save(target, dict(schema_version=1, status='exact-retained-original-normalization-control',
        source=source.relative_to(ROOT).as_posix(), source_pin=pin(source), population=name,
        original_collection='docs/literature/signac-normalization-development/VALIDATION.json',
        inputs={name: pin(example / name) for name in ['counts.mtx', 'features.tsv', 'cells.tsv', 'idf.tsv', 'existing.tsv']},
        features=3, cells=3, stored_coordinates=5, feature_totals=[3, 3, 12],
        conversion='Lossless zero-based sparse CSC to one-based integer MatrixMarket; retain original coordinate order.',
        biological_data=False, historical_whole_packages=False))
    print('Verified exact original sparse counts, identities and IDF; wrote input provenance')


if __name__ == '__main__':
    main()
