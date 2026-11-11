# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Typed calls to unchanged historical scDRS CLI functions; no replacement estimator."""
import argparse
import csv
import gc
import gzip
import json
import re
import runpy
import sys
import traceback
from pathlib import Path


class Unavailable(RuntimeError):
    pass


def save(path, value):
    raw = (json.dumps(value, sort_keys=True, indent=2, allow_nan=False) + '\n').encode('utf-8')
    assert len(raw) <= 64_000_000, 'Inspection metadata capacity exceeded'
    with path.open('xb') as stream:
        stream.write(raw)


def identifier(value):
    assert isinstance(value, str) and value and len(value) <= 1000
    assert all(ord(c) >= 32 and ord(c) != 127 for c in value), 'Invalid literal identifier'
    return value


def trait(value):
    assert isinstance(value, str) and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,119}', value), 'Unsafe original trait filename label; no renaming inferred'
    return value


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', required=True, type=Path)
    p.add_argument('--request', required=True, type=Path)
    p.add_argument('--out', required=True, type=Path)
    a = p.parse_args()
    root = a.out.resolve()
    assert root.is_dir()
    state = dict(status='FAIL2_NO_ORIGINAL_SCDRS_WORKFLOW_RESULT', result_admitted=False)
    try:
        sys.path.insert(0, str(a.source))
        import numpy as np
        import pandas as pd
        import anndata
        import scdrs
        from scipy import sparse
        assert Path(scdrs.__file__).resolve().is_relative_to(a.source.resolve())
        request = json.loads(a.request.read_bytes())
        assert scdrs.__version__ == request['version']
        operation = request['operation']
        inputs = {k: (a.request.parent / v).resolve() for k, v in request['inputs'].items() if v is not None}
        for path in inputs.values(): assert path.is_relative_to(root / 'inputs')
        output = root / 'original'
        assert not output.exists()
        output.mkdir()
        inspection = root / 'inspection'
        inspection.mkdir()
        warnings = []
        report = dict(schema_version=1, version=scdrs.__version__, operation=operation, warnings=warnings,
            traits=[], cells=[], genes=[], groups={}, correlations=[], source_function_calls=[], expected_outputs=[])
        limits = request['limits']
        prepared = None
        if operation != 'munge-gs':
            expression = request['expression']
            raw = anndata.read_h5ad(inputs['h5ad'])
            assert raw.n_obs >= 2 and raw.n_vars >= 10 and raw.n_obs <= limits['cells'] and raw.n_vars <= limits['genes'], 'Input AnnData shape exceeds limits'
            assert raw.obs_names.is_unique and raw.var_names.is_unique, 'Duplicate cell/gene identifiers; no repair inferred'
            assert raw.n_obs * raw.n_vars <= limits['matrix_elements'], 'Expression matrix logical element capacity exceeded'
            data = raw.X.data if sparse.issparse(raw.X) else raw.X
            assert np.isfinite(data).all() and (data >= 0).all(), 'Nonfinite or negative input expression; no repair'
            nonzeros = int(raw.X.nnz if sparse.issparse(raw.X) else np.count_nonzero(raw.X))
            assert nonzeros <= limits['nonzeros'], 'Expression nonzero capacity exceeded'
            raw_cells, raw_genes = list(raw.obs_names), list(raw.var_names)
            raw_shape = list(raw.shape)
            del raw
            gc.collect()
            prepared = scdrs.util.load_h5ad(str(inputs['h5ad']), flag_filter_data=expression['filter_data'], flag_raw_count=expression['raw_counts'])
            assert prepared.n_obs >= 2 and prepared.n_vars >= 10, 'No assessable retained expression data'
            report['cells'] = [identifier(v) for v in prepared.obs_names]
            report['genes'] = [identifier(v) for v in prepared.var_names]
            report['raw_shape'] = raw_shape
            report['retained_shape'] = list(prepared.shape)
            report['nonzeros_before_source_filter'] = nonzeros
            cells, genes = set(report['cells']), set(report['genes'])
            with (inspection / 'identifier-dispositions.tsv').open('x', encoding='utf-8', newline='\n') as stream:
                writer = csv.writer(stream, delimiter='\t', lineterminator='\n')
                writer.writerow(['kind', 'identifier', 'source_disposition'])
                for kind, original, retained in [('cell', raw_cells, cells), ('gene', raw_genes, genes)]:
                    for value in original:
                        identifier(value)
                        writer.writerow([kind, value, 'retained' if value in retained else 'removed-by-requested-upstream-filter'])
            scoring = request['scoring']
            k = scoring['controls'] if scoring is not None else 0
            if operation == 'downstream':
                assert inputs['scores'].name.endswith('.full_score.gz')
                with gzip.open(inputs['scores'], 'rt', encoding='utf-8', newline='') as stream:
                    reader = csv.reader(stream, delimiter='\t')
                    header = next(reader)
                    assert len(set(header)) == len(header) and 'norm_score' in header
                    assert len(header) <= 20007, 'Score input column capacity exceeded'
                    score_rows = 0
                    for row in reader:
                        if not row: continue
                        score_rows += 1
                        assert score_rows <= limits['cells'], 'Score input cell capacity exceeded'
                    assert score_rows >= 2 and score_rows * (len(header) - 1) <= limits['matrix_elements'], 'Score input logical element capacity exceeded'
                k = sum(c.startswith('ctrl_norm_score') for c in header)
                assert 2 <= k <= 10000, 'Downstream input requires2..10000 normalized control scores'
                report['downstream_controls'] = k
                report['score_input_shape'] = [score_rows, len(header) - 1]
            # Conservative logical allocation estimate; this is not an OS RAM quota.
            estimate = 128 * prepared.n_obs * k + 32 * nonzeros + 256 * prepared.n_obs + 512 * prepared.n_vars
            if not sparse.issparse(prepared.X): estimate += 64 * raw_shape[0] * raw_shape[1]
            if operation == 'downstream': estimate += 32 * score_rows * (len(header) - 1)
            assert prepared.n_obs * k <= limits['matrix_elements'], 'Scoring logical element capacity exceeded'
            assert estimate <= limits['working_bytes'], 'Original expression/scoring logical work estimate exceeds declared working_bytes'
            report['logical_work_estimate_bytes'] = estimate
            if operation == 'downstream':
                score_input = scdrs.util.load_scdrs_score(str(inputs['scores']))
                assert len(score_input) == 1
                frame = next(iter(score_input.values()))
                assert frame.index.is_unique and all(isinstance(v, str) for v in frame.index), 'Historical pandas score-index coercion produced non-string/duplicate IDs; no ID repair inferred'
                assert frame.shape[0] <= limits['cells'] and np.isfinite(frame.to_numpy(dtype=float)).all()
                common = sorted(cells & set(frame.index))
                assert len(common) >= 2, 'Fewer than2 literal cells overlap downstream data/scores'
                report['downstream_cell_ids'] = common
                if len(common) != len(cells) or len(common) != len(frame):
                    warnings.append('Original downstream uses the sorted literal intersection of expression and score cells. Read downstream_cell_ids; no missing-cell imputation or identifier repair was added.')
                del frame, score_input
                gc.collect()
            if 'covariates' in inputs:
                cov = pd.read_csv(inputs['covariates'], sep='\t', index_col=0)
                assert cov.index.is_unique and cov.columns.is_unique and cov.shape[1] > 0
                overlap = set(cov.index) & cells
                assert len(overlap) > .75 * prepared.n_obs, 'Original pandas covariate IDs match <=75% of retained cells; no string coercion or repair inferred'
                report['covariates'] = dict(columns=[identifier(str(x)) for x in cov.columns],
                    matched_cells=len(overlap), absent_cells=prepared.n_obs - len(overlap),
                    missing_values={str(c): int(cov[c].isna().sum()) for c in cov})
                if prepared.n_obs != len(overlap) or cov.isna().any().any():
                    warnings.append('Original scDRS preprocessing handles missing covariates using its own category/dummy and column-mean behavior. Read the covariate inspection and original logs; the adapter adds no imputation.')
                if scdrs.__version__ in ['1.0.0', '1.0.1'] and any(not pd.api.types.is_numeric_dtype(cov[c]) for c in cov):
                    raise ValueError('Historical1.0.0/1.0.1 require explicitly numeric covariates; original categorical preprocessing fails. Define/document encoding yourself, or explicitly select a different version; no automatic conversion.')
            if request['downstream'] is not None:
                down = request['downstream']
                assert all(c in prepared.obs for c in down['groups'] + down['correlations']), 'Requested annotation absent from AnnData.obs'
                for group in down['groups']:
                    assert not prepared.obs[group].isna().any(), 'Missing group labels; no grouping repair'
                    values = list(prepared.obs[group])
                    assert all(isinstance(v, str) for v in values), 'Use explicit string group labels; no label coercion'
                    report['groups'][group] = [identifier(v) for v in values]
                for name in down['correlations']:
                    values = np.asarray(prepared.obs[name], dtype=float)
                    assert np.isfinite(values).all() and np.std(values) > 0, 'Nonfinite/constant correlation annotation'
                report['correlations'] = down['correlations']
        if operation in ['score', 'score-and-downstream']:
            score = request['scoring']
            species, gs_species = request['expression']['species'], score['gene_set_species']
            if species != gs_species:
                species, gs_species = scdrs.util.convert_species_name(species), scdrs.util.convert_species_name(gs_species)
            raw_gs = pd.read_csv(inputs['gene_sets'], sep='\t')
            assert list(raw_gs.columns) == ['TRAIT', 'GENESET'] and raw_gs.TRAIT.is_unique, 'Expected unique TRAIT/GENESET records'
            assert 0 < len(raw_gs) <= limits['traits']
            report['traits'] = [trait(v) for v in raw_gs.TRAIT]
            selected = scdrs.util.load_gs(str(inputs['gene_sets']), src_species=gs_species, dst_species=species, to_intersect=prepared.var_names)
            counts = {}
            for name in report['traits']:
                gene_ids, weights = selected[name]
                if len(gene_ids) < 10:
                    raise Unavailable('Original CLI skips trait=' + name + ' with fewer than10 overlapping genes; dependent workflow skipped, no partial admission')
                assert np.isfinite(weights).all(), 'Nonfinite supplied gene weight'
                counts[name] = len(gene_ids)
            report['overlapping_genes_per_trait'] = counts
            # Original _select_ctrl_geneset retains a control-gene list and
            # weight list for every control, plus the gene-by-control weight
            # matrix. Include these separately from the cell-by-control arrays.
            # NumPy Unicode gene scalars depend on the longest input gene ID.
            gene_slot_bytes = 256 + 4 * max(len(v) for v in report['genes'])
            gene_set_work = gene_slot_bytes * max(counts.values()) * score['controls']
            report['logical_gene_set_work_estimate_bytes'] = gene_set_work
            report['logical_work_estimate_bytes'] += gene_set_work
            assert report['logical_work_estimate_bytes'] <= limits['working_bytes'], 'Original retained control-gene/weight sets exceed declared working_bytes'
            if score['adjust_proportions'] is not None:
                assert score['adjust_proportions'] in prepared.obs, 'Requested proportion-adjustment annotation absent'
            for name in report['traits']:
                report['expected_outputs'].append(name + '.score.gz')
                if score['return_control_raw'] or score['return_control_normalized']:
                    report['expected_outputs'].append(name + '.full_score.gz')
            report['source_function_calls'].append('compute_score')
        elif operation == 'downstream':
            score_file = inputs['scores']
            assert score_file.name.endswith('.full_score.gz')
            report['traits'] = [trait(score_file.name[:-len('.full_score.gz')])]
        elif operation == 'munge-gs':
            stats = pd.read_csv(inputs['gene_statistics'], sep='\t', index_col=0)
            assert stats.index.is_unique and stats.columns.is_unique and len(stats) > 0 and len(stats) <= limits['genes']
            assert 0 < len(stats.columns) <= limits['traits']
            report['traits'] = [trait(v) for v in sorted(stats.columns)]
            report['genes'] = [identifier(v) for v in stats.index]
            report['gene_statistics_shape'] = list(stats.shape)
            assert 32 * stats.size <= limits['working_bytes'], 'Gene-statistic logical capacity exceeded'
            for column in stats:
                v = stats[column].dropna().to_numpy(dtype=float)
                assert len(v) > 0 and np.isfinite(v).all(), 'Unassessable/nonfinite gene statistic'
                if request['gene_sets']['input_kind'] == 'pvalue': assert ((v >= 0) & (v <= 1)).all(), 'P outside[0,1]'
            report['source_missing_gene_statistics'] = {str(c): int(stats[c].isna().sum()) for c in stats}
            warnings.append('Original munge_gs drops missing gene statistics, caps selected gene counts, clips P below1e-100 and Z above10, and writes five significant digits. These are original source rules, not added repairs.')
            report['expected_outputs'] = ['gene_sets.gs']
            report['source_function_calls'] = ['munge_gs']
        if request['downstream'] is not None:
            for name in report['traits']:
                for group in request['downstream']['groups']:
                    report['expected_outputs'].append(name + '.scdrs_group.' + group.replace(' ', '_').replace(',', '_'))
                if request['downstream']['correlations']: report['expected_outputs'].append(name + '.scdrs_cell_corr')
                if request['downstream']['gene_analysis']: report['expected_outputs'].append(name + '.scdrs_gene')
            report['source_function_calls'].append('perform_downstream')
        assert len(set(report['expected_outputs'])) == len(report['expected_outputs'])
        save(inspection / 'input-contract.json', report)
        del prepared
        gc.collect()
        namespace = runpy.run_path(str(a.source / 'bin/scdrs'), run_name='_omicsmatic_original_scdrs_cli_')
        save(root / 'source-analysis-started.json', dict(source_functions=report['source_function_calls'], version=scdrs.__version__))
        if operation == 'munge-gs':
            spec = request['gene_sets']
            kwargs = dict(out_file=str(output / 'gene_sets.gs'), weight=spec['weighting'], fdr=spec['fdr'],
                          fwer=spec['fwer'], n_min=spec['minimum_genes'], n_max=spec['maximum_genes'])
            kwargs['pval_file' if spec['input_kind'] == 'pvalue' else 'zscore_file'] = str(inputs['gene_statistics'])
            namespace['munge_gs'](**kwargs)
        if operation in ['score', 'score-and-downstream']:
            spec = request['scoring']
            namespace['compute_score'](h5ad_file=str(inputs['h5ad']), h5ad_species=request['expression']['species'],
                gs_file=str(inputs['gene_sets']), gs_species=spec['gene_set_species'], out_folder=str(output),
                cov_file=str(inputs['covariates']) if 'covariates' in inputs else None,
                ctrl_match_opt=spec['match'], weight_opt=spec['weighting'], adj_prop=spec['adjust_proportions'],
                flag_filter_data=request['expression']['filter_data'], flag_raw_count=request['expression']['raw_counts'],
                n_ctrl=spec['controls'], flag_return_ctrl_raw_score=spec['return_control_raw'],
                flag_return_ctrl_norm_score=spec['return_control_normalized'])
        if request['downstream'] is not None:
            spec = request['downstream']
            for name in report['traits']:
                score_file = output / (name + '.full_score.gz') if operation == 'score-and-downstream' else inputs['scores']
                namespace['perform_downstream'](h5ad_file=str(inputs['h5ad']), score_file=str(score_file), out_folder=str(output),
                    group_analysis=','.join(spec['groups']) if spec['groups'] else None,
                    corr_analysis=','.join(spec['correlations']) if spec['correlations'] else None,
                    gene_analysis=True if spec['gene_analysis'] else None,
                    flag_filter_data=request['expression']['filter_data'], flag_raw_count=request['expression']['raw_counts'],
                    knn_n_neighbors=spec['knn_neighbors'], knn_n_pcs=spec['knn_pcs'])
        assert sorted(p.name for p in output.iterdir()) == sorted(report['expected_outputs']), 'Missing/extra original outputs; no partial trait admission'
        state = dict(status='PASS_UNCHANGED_SCDRS_CLI_FUNCTIONS_EXECUTED', result_admitted=False,
                     version=scdrs.__version__, source_functions=report['source_function_calls'])
        save(root / 'source-workflow-completed.json', state)
    except (ModuleNotFoundError, ImportError, Unavailable) as error:
        state.update(status='SKIP3_ORIGINAL_SCDRS_DEPENDENT_ANALYSIS_UNAVAILABLE', cause=repr(error),
            required_action='Supply the named permitted runtime/dependency/capability prerequisite, preserve this attempt and retry NEW paths. No partial workflow result is admitted.')
    except Exception as error:
        state.update(cause=repr(error), traceback=traceback.format_exc(),
            required_action='Resolve the stated input, capacity, source-process or numerical cause; preserve inputs/attempt and retry NEW paths. Do not repair scientific values to hide failure.')
    if not state['status'].startswith('PASS_'):
        save(root / 'source-worker-failure.json', state)
        print(state['status'] + ': ' + state['cause'], file=sys.stderr)
    return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__ == '__main__':
    if not __debug__: raise RuntimeError('Do not disable input/source contract checks')
    raise SystemExit(main())
