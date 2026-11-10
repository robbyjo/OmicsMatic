# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Retain original scDRS categorical-covariate and zero-control-SD outcomes."""
import argparse
import json
import sys
import traceback
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--input-h5ad', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    assert not a.out.exists()
    a.out.mkdir(parents=True)
    sys.path.insert(0, str(a.source))
    import numpy as np
    import anndata
    import scdrs
    assert Path(scdrs.__file__).resolve().is_relative_to(a.source.resolve())
    ad = anndata.read_h5ad(a.input_h5ad)
    cov = ad.obs[['n_genes', 'age']].copy()
    cov['sex'] = np.where(np.arange(ad.n_obs) % 2, 'F', 'M')
    outcome = {}
    try:
        scdrs.preprocess(ad, cov=cov, n_mean_bin=20, n_var_bin=20)
        outcome = dict(status='ORIGINAL_CATEGORICAL_PREPROCESS_RETURNED',
            covariate_columns=list(ad.uns['SCDRS_PARAM']['COV_MAT'].columns))
    except Exception as error:
        outcome = dict(status='ORIGINAL_CATEGORICAL_PREPROCESS_ERROR',
            error_type=type(error).__name__, error=str(error), traceback=traceback.format_exc())
    if scdrs.__version__ in {'1.0.0', '1.0.1'}:
        assert outcome['status'] == 'ORIGINAL_CATEGORICAL_PREPROCESS_ERROR'
        assert outcome['error_type'] == 'TypeError' and 'multiply sequence' in outcome['error']
    else:
        assert scdrs.__version__ == '1.0.2'
        assert outcome['status'] == 'ORIGINAL_CATEGORICAL_PREPROCESS_RETURNED'
        assert 'sex_M' in outcome['covariate_columns']
    with np.errstate(all='ignore'):
        trait, ctrl = scdrs.method._correct_background(np.array([1., 2, 3, 4]),
            np.array([[1., 1], [2, 2], [3, 3], [4, 4]]), np.ones(2))
    zero_sd = dict(status='ORIGINAL_NONFINITE_BACKGROUND_RETURNED',
        trait_finite=bool(np.isfinite(trait).all()), controls_finite=bool(np.isfinite(ctrl).all()),
        analysis_admitted=False, required_disposition='FAIL2_NO_COMPLETE_RESULT_NO_VARIANCE_REPAIR')
    assert not zero_sd['trait_finite'] and not zero_sd['controls_finite']
    result = dict(status='PASS_ORIGINAL_SCDRS_EDGE_OUTCOMES_RETAINED', version=scdrs.__version__,
        categorical_covariates=outcome, identical_controls=zero_sd,
        source_modified=False, silent_encoding_or_source_repair=False, new_catalog_credit=False)
    (a.out / 'COMPLETED.json').write_text(json.dumps(result, indent=2, allow_nan=False) + '\n')
    print(result['status'] + ' ' + scdrs.__version__)


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Do not disable original-source outcome assertions')
    main()
