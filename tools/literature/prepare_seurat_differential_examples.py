# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Create copyable examples from the independently retained synthetic count population."""
import copy
import json
import shutil
from setup_deseq2_reference import ROOT, pin, save


def main():
    out = ROOT / 'examples/seurat-differential'
    assert not out.exists()
    out.mkdir()
    base = ROOT / 'build/literature-review/signac-runtime'
    source = base / 'differential-requests-2'
    for name in ['counts.mtx', 'features.txt', 'cells.txt', 'metadata.tsv']:
        shutil.copyfile(source / 'input' / name, out / name)
    prototype = json.loads((source / 'pair_wilcox.json').read_bytes())
    prototype.update(data_source='native_tfidf', counts='counts.mtx', features='features.txt',
                     cells='cells.txt', metadata='metadata.tsv',
                     r_libraries=['../../build/literature-review/signac-runtime/reference-setup-1/R-lib',
                                  '../../build/omics-sctransform-r'])
    prototype.pop('data')
    settings = {
        'paired': {},
        'paired-lr': dict(test='LR', covariates=[dict(name='depth', kind='numeric'),
                                               dict(name='batch', kind='factor', levels=['batch1', 'batch2'])]),
        'all-lr': dict(mode='ALL', test='LR', only_positive=True, return_threshold=1,
                       covariates=[dict(name='depth', kind='numeric')]),
        'count-means': dict(data_source='counts', test_slot='counts', fc_slot='counts',
                            min_pct=0, logfc_threshold=0)
    }
    for name, values in settings.items():
        request = copy.deepcopy(prototype)
        request.update(values, out='../../build/seurat-differential-example-' + name)
        if request['mode'] == 'ALL':
            request.pop('contrast')
        save(out / (name + '.json'), request)
        save(out / ('verify-' + name + '.json'), dict(schema_version=1, mode='VERIFY', analysis=request['out']))
    save(out / 'PROVENANCE.json', dict(
        status='synthetic-120-feature-36-cell-example-not-biological-data',
        source_population='differential-original-reference-3/original/original-matrices.rds',
        source_population_pin=pin(base / 'differential-original-reference-3/original/original-matrices.rds'),
        fixture_preparation=pin(source / 'INPUT-PROVENANCE.json'),
        files={name: pin(out / name) for name in ['counts.mtx', 'features.txt', 'cells.txt', 'metadata.tsv']},
        counts_rounded=False, metadata_values_or_order_changed=False,
        metadata_header='The original R row-name header was explicitly labelled cell during fixture preparation.',
        native_tfidf_method=1, native_tfidf_scale=10000, biological_validation=False))
    print('Prepared4 typed marker-analysis examples and their Java-only verification requests')


if __name__ == '__main__':
    main()
