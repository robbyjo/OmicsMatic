#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Observe all RidgeCV penalties through sklearn's public store_cv_values option."""
from pathlib import Path
import argparse, hashlib, importlib.util, json
import numpy as np
import pandas as pd
import sklearn


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--upstream', type=Path, required=True)
    parser.add_argument('--original-controls', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    source = args.upstream.resolve() / 'pops.py'
    before = source.read_bytes()
    assert hashlib.sha256(before).hexdigest() == '35e5af04748d6477e47a8a99a941de086dab39bc829655218a4817242507e3fc'
    assert sklearn.__version__ == '0.24.2'
    spec = importlib.util.spec_from_file_location('original_pops', str(source))
    original = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(original)
    records = []
    for case in json.loads((args.original_controls / 'CASES.json').read_text()):
        if case['expected_failure']:
            continue
        folder = args.original_controls / case['name']
        config = dict(s.split('=', 1) for s in (folder / 'params.properties').read_text().splitlines())
        if config['method'] != 'ridge':
            continue
        # Source-exported, fully transformed data. This changes observation only:
        # store_cv_values does not change the estimator or its CV scoring.
        data = pd.read_csv(folder / 'original.traindata', sep='\t', index_col=0)
        x, y = data.iloc[:, 1:].values, data.iloc[:, 0].values
        reg = original.initialize_regressor('ridge', 42)
        reg.set_params(store_cv_values=True)
        reg.fit(x, y)
        coefs = pd.read_csv(folder / 'original.coefs', sep='\t', index_col=0)
        assert float(coefs.loc['SELECTED_CV_ALPHA', 'beta']) == reg.alpha_
        assert np.allclose(reg.coef_, coefs.loc[data.columns[1:], 'beta'].astype(float).values, rtol=1e-9, atol=1e-10)
        expected = -np.mean(reg.cv_values_, axis=0)
        np.savetxt(out / (case['name'] + '.cv.tsv'), expected, fmt='%.17g')
        records.append(dict(case=case['name'], penalties=len(expected), alpha=float(reg.alpha_), best_score=float(reg.best_score_)))
    assert len(records) == 9
    assert source.read_bytes() == before
    status = dict(status='PASS_ORIGINAL_POPS_RIDGE_225_LOO_CV_SCORES', models=len(records), penalties_per_model=25,
                  public_observation_option='RidgeCV.set_params(store_cv_values=True)', source_modified=False,
                  function_replacement_performed=False, original_cli_model_and_refit_match=True, records=records)
    (out / 'STATUS.json').write_text(json.dumps(status, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(status))


if __name__ == '__main__':
    main()
