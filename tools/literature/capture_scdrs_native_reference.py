# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run unchanged historical scDRS functions on retained synthetic numerical controls.

Run this helper inside the private Linux reference environment. It is not a
production scDRS adapter and grants no catalog credit.
"""
import argparse
import json
import sys
from pathlib import Path


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    a = p.parse_args()
    assert not a.out.exists()
    a.out.mkdir(parents=True)
    sys.path.insert(0, str(a.source))
    import numpy as np
    import pandas as pd
    import scipy as sp
    import anndata
    import scanpy as sc
    import scdrs
    from statsmodels.stats.multitest import multipletests
    assert scdrs.__version__ in {'1.0.0', '1.0.1', '1.0.2'}
    assert Path(scdrs.__file__).resolve().is_relative_to(a.source.resolve())
    cases = []

    def save(name, value):
        (a.out / name).write_text(json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + '\n')

    def numeric(name, raw, ctrl, ratio, mode, full_score=None):
        rng = np.random.RandomState(12345)
        n, k = ctrl.shape
        expression = rng.gamma(1.2, 2, size=(n, 5))
        slots = [4, 1, 4, 0]
        weights = [1, 2, -0.25, 0.5]
        variance = np.array([0, .25, .6, 2, 1.0])
        tech = np.array([.8, 0, 1, .3, .2])
        ad = anndata.AnnData(expression, dtype=np.float64)
        genes = ['gene-' + str(i) for i in range(5)]
        ad.var_names = genes
        ad.uns['SCDRS_PARAM'] = dict(FLAG_SPARSE=False, FLAG_COV=False,
            GENE_STATS=pd.DataFrame(dict(var=variance, var_tech=tech), index=genes))
        rs, w = scdrs.method._compute_raw_score(ad, [genes[i] for i in slots], weights, mode)
        trait, controls = scdrs.method._correct_background(raw.copy(), ctrl.copy(), ratio.copy())
        mc_p = (1 + (controls.T >= trait).sum(axis=0)) / (1 + k)
        pval = scdrs.method._get_p_from_empi_null(trait, controls.flatten())
        table = np.column_stack([raw, trait, mc_p, pval, -np.log10(pval),
            -sp.stats.norm.ppf(pval).clip(min=-10, max=10), controls]).astype(np.float32)
        if full_score is not None:
            np.testing.assert_array_equal(table, full_score.values)
        annotation = rng.normal(size=n)
        cells = ['cell-' + str(i) for i in range(n)]
        ad = anndata.AnnData(np.zeros((n, 2)), dtype=np.float64)
        ad.obs_names = cells
        ad.obs['group'] = 'all'
        ad.obs['annotation'] = annotation
        # A fixed graph only supplies the unchanged downstream API's required
        # heterogeneity input. The native component does not claim that test.
        ad.obsp['connectivities'] = sp.sparse.csr_matrix(
            (np.ones(n), (np.arange(n), (np.arange(n) + 1) % n)), shape=(n, n))
        df = pd.DataFrame(np.column_stack([trait, controls]), index=cells,
            columns=['norm_score'] + ['ctrl_norm_score_' + str(i) for i in range(k)])
        df['pval'] = pval
        group = scdrs.method.downstream_group_analysis(ad, df, ['group'])['group'].loc['all']
        corr = scdrs.method.downstream_corr_analysis(ad, df, ['annotation']).loc['annotation']
        q95 = np.quantile(trait, .95)
        pearson = np.corrcoef(annotation, trait)[0, 1]
        pvalues = [.03, .005, .03, .9, 1, 0, .0001]
        observed = [-1, 0, 1, 3, 5]
        null = [0, 1, 1, 4]
        fixture = dict(raw=raw.tolist(), controls=ctrl.tolist(), ratio=ratio.tolist(),
            expression=expression.tolist(), slots=slots, weights=weights,
            variance=variance[slots].tolist(), var_tech=tech[slots].tolist(),
            weighting={'uniform': 'UNIFORM', 'vs': 'VS', 'inv_std': 'INV_STD'}[mode],
            annotation=annotation.tolist(), pvalues=pvalues,
            empirical_observed=observed, empirical_null=null)
        expected = dict(trait=trait.tolist(), controls=controls.tolist(), scores=table.astype(float).tolist(),
            raw=rs.tolist(), weights=w.tolist(), independent_variance=[float(np.sum(variance[slots] * w ** 2))],
            # Downstream stores the final MC quantities in float32, while the
            # native API exposes double intermediates. Retain both, without
            # disguising that precision boundary.
            group=[float(q95), float(group['assoc_mcp']), float(group['assoc_mcz'])],
            correlation=[float(pearson), float(corr['corr_mcp']), float(corr['corr_mcz'])],
            bh=multipletests(pvalues, method='fdr_bh')[1].tolist(),
            empirical=scdrs.method._get_p_from_empi_null(observed, null).tolist())
        assert all(np.isfinite(np.asarray(v)).all() for v in expected.values())
        save(name + '.input.json', fixture)
        save(name + '.expected.json', expected)
        cases.append(dict(case=name, input=name + '.input.json', expected=name + '.expected.json',
            cells=n, controls=k, actual_score_cell=full_score is not None))

    for idx, (n, k) in enumerate([(5, 7), (17, 23), (129, 200), (257, 1000)]):
        rng = np.random.RandomState(20261110 + idx)
        raw = rng.normal(size=n)
        ctrl = rng.normal(size=(n, k))
        ratio = rng.uniform(.1, 5, size=k)
        if idx != 1:
            raw[0] = 0
            ctrl[0, 0] = 0
            ctrl[-1, -1] = 0
        numeric('numeric-' + str(idx), raw, ctrl, ratio, ['uniform', 'vs', 'inv_std', 'vs'][idx])

    # Full scoring references exercise the genuine preprocessing, covariate
    # handling, mean/variance bins, matched NumPy sampling and score_cell API.
    for idx, (use_cov, n_ctrl) in enumerate([(False, 200), (True, 200), (True, 1000)]):
        rng = np.random.RandomState(20261110)
        n, g = 96, 4200
        rates = np.exp(rng.uniform(-1, 3, size=g))
        counts = rng.poisson(rates[None, :] * rng.lognormal(0, .3, size=(n, 1))).astype(np.float64)
        ad = anndata.AnnData(sp.sparse.csr_matrix(counts), dtype=np.float64)
        ad.var_names = ['gene-%04d' % i for i in range(g)]
        ad.obs_names = ['cell-%03d' % i for i in range(n)]
        ad.obs['age'] = rng.uniform(20, 80, size=n)
        # The synthetic fixture defines sex as numeric0/1 at creation. Historical
        # 1.0.0/1.0.1 preprocess do not convert text covariates; do not silently
        # encode a user's categorical inputs on their behalf.
        ad.obs['sex'] = (np.arange(n) % 2).astype(float)
        ad.obs['n_genes'] = (counts > 0).sum(axis=1)
        cov = ad.obs[['n_genes', 'age', 'sex']].copy() if use_cov else None
        sc.pp.normalize_total(ad, target_sum=10000)
        sc.pp.log1p(ad)
        # Preserve normalized expression and source-side metadata as a reusable
        # synthetic input. No original article data or text is redistributed.
        ad.write_h5ad(a.out / ('workflow-' + str(idx) + '.input.h5ad'))
        scdrs.preprocess(ad, cov=cov, n_mean_bin=20, n_var_bin=20)
        selected = [ad.var_names[i] for i in range(3, 603, 10)]
        gene_weights = rng.uniform(.3, 2, size=len(selected)).tolist()
        df_gene = ad.uns['SCDRS_PARAM']['GENE_STATS'].loc[ad.var_names].copy()
        df_gene['gene'] = df_gene.index
        selected = sorted(selected)
        ctrl_list, ctrl_weight = scdrs.method._select_ctrl_geneset(df_gene, selected,
            gene_weights, 'mean_var', n_ctrl, 200, 0)
        raw, weights = scdrs.method._compute_raw_score(ad, selected, gene_weights, 'vs')
        control_raw = np.zeros((n, n_ctrl))
        ratios = np.zeros(n_ctrl)
        for j in range(n_ctrl):
            cr, cw = scdrs.method._compute_raw_score(ad, ctrl_list[j], ctrl_weight[j], 'vs')
            control_raw[:, j] = cr
            ratios[j] = (df_gene.loc[ctrl_list[j], 'var'] * cw ** 2).sum()
        ratios /= (df_gene.loc[selected, 'var'] * weights ** 2).sum()
        full_score = scdrs.score_cell(ad, selected, gene_weight=gene_weights,
            ctrl_match_key='mean_var', n_ctrl=n_ctrl, weight_opt='vs',
            return_ctrl_raw_score=False, return_ctrl_norm_score=True, random_seed=0, verbose=False)
        full_score.to_csv(a.out / ('workflow-' + str(idx) + '.full_score.tsv'), sep='\t')
        numeric('workflow-' + str(idx), raw, control_raw, ratios, 'vs', full_score)
    save('COMPLETED.json', dict(status='PASS_UNCHANGED_SCDRS_NUMERICAL_AND_SCORING_REFERENCE_CAPTURE_ONLY',
        version=scdrs.__version__, source=str(a.source), cases=cases, new_catalog_credit=False,
        source_module=str(Path(scdrs.__file__).resolve()), full_adapter_validated=False))
    print('Captured %d controls for original scDRS%s' % (len(cases), scdrs.__version__))


if __name__ == '__main__':
    if not __debug__:
        raise RuntimeError('Do not disable differential reference assertions')
    main()
