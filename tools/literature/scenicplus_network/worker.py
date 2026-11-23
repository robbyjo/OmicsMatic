"""Our prepared-network API orchestrator; licensed vendor code stays unmodified.

This imports our unchanged R2G runtime checker. All outputs and traces are private.
"""
from pathlib import Path
import csv
import json
import math
import numbers
import sys
import time
import traceback
import warnings
import r2g_runtime as common


def scalar(value):
    import numpy as np
    import pandas as pd
    if value is None:
        return None
    if value is pd.NA:
        return {'source_kind':'pandas.NA'}
    if isinstance(value, (bool, np.bool_)):
        return bool(value)
    if isinstance(value, str):
        return value
    if isinstance(value, numbers.Integral):
        return int(value)
    if isinstance(value, numbers.Real):
        return {'float_hex':float(value).hex()}
    if isinstance(value, np.ndarray):
        return {'source_kind':'ndarray', 'dtype':str(value.dtype), 'values':scalar(value.tolist())}
    if isinstance(value, (list, tuple)):
        return [scalar(v) for v in value]
    if isinstance(value, (set, frozenset)):
        common.require(all(isinstance(v, str) for v in value), 'Unexpected source set element type')
        return {'source_kind':type(value).__name__, 'values':sorted(value)}
    raise TypeError('Unsupported source scalar; no repr/coercion applied: ' + type(value).__name__)


def closed_h5ad(path, limits):
    import h5py
    seen = set()
    with h5py.File(path, 'r') as handle:
        def visit(group):
            address = h5py.h5o.get_info(group.id).addr
            if address in seen:
                return
            seen.add(address)
            common.require(len(seen) <= limits['maximum_files'], 'HDF5 object capacity exceeded')
            if isinstance(group, h5py.Dataset):
                common.require(not group.is_virtual and not group.external, 'Materialize virtual/external HDF5 data before capturing this input')
                return
            for name in group:
                link = group.get(name, getlink=True)
                common.require(isinstance(link, h5py.HardLink), 'Cistrome input must contain no external/soft HDF5 links')
                visit(group[name])
        visit(handle)


def search_space(path, expression, accessibility, limits):
    import pandas as pd
    genes, regions = set(expression.columns), set(accessibility.columns)
    seen, rows = set(), []
    with Path(path).open(encoding='utf-8', newline='') as stream:
        reader = csv.reader(stream, delimiter='\t', quoting=csv.QUOTE_NONE, strict=True)
        common.require(next(reader) == ['Name', 'Gene', 'Distance'], 'Require exact Name/Gene/Distance search-space header')
        for row in reader:
            common.require(len(row) == 3, 'Ragged candidate row')
            region, gene = common.label(row[0]), common.label(row[1])
            common.require(region in regions and gene in genes, 'Unknown candidate region/gene; no silent dropping or ID repair')
            common.require((region, gene) not in seen and len(rows) < limits['maximum_pairs'], 'Duplicate candidate or pair capacity exceeded')
            distance = int(row[2])
            common.require(str(distance) == row[2] and -(2**63) <= distance < 2**63, 'Require canonical signed int64 distance')
            seen.add((region, gene))
            rows.append((region, gene, distance))
    common.require(rows, 'Explicit nonempty search space required')
    return pd.DataFrame(rows, columns=['Name', 'Gene', 'Distance'])


class Exports:
    """Stream complete typed values and field-finiteness flags; retain original TSVs."""
    def __init__(self, out):
        self.values = (out / 'scientific.jsonl').open('x', encoding='utf-8', newline='\n')
        self.flags = (out / 'field-ledger.tsv').open('x', encoding='utf-8', newline='\n')
        self.flags.write('table\trow\tfinite_mask\n')
        self.schemas = {}
        self.nonfinite = 0

    def frame(self, name, frame, index=False):
        import pandas as pd
        columns = list(frame.columns)
        numerical = [i for i, dtype in enumerate(frame.dtypes)
                     if pd.api.types.is_numeric_dtype(dtype) and not pd.api.types.is_bool_dtype(dtype)]
        schema = dict(table=name, columns=columns, dtypes=[str(v) for v in frame.dtypes],
                      shape=list(frame.shape), index_included=index,
                      numerical_columns=[columns[i] for i in numerical])
        if index:
            schema.update(index_name=frame.index.name, index_dtype=str(frame.index.dtype))
        self.schemas[name] = schema
        self.values.write(json.dumps(schema, ensure_ascii=False, allow_nan=False, sort_keys=True) + '\n')
        for ordinal, row in enumerate(frame.itertuples(index=False, name=None)):
            data = dict(table=name, row=ordinal, values=[scalar(v) for v in row])
            if index:
                data['index'] = scalar(frame.index[ordinal])
            self.values.write(json.dumps(data, ensure_ascii=False, allow_nan=False, sort_keys=True) + '\n')
            finite = [math.isfinite(float(row[i])) for i in numerical]
            self.nonfinite += sum(not v for v in finite)
            self.flags.write(name + '\t' + str(ordinal) + '\t' + ''.join('1' if v else '0' for v in finite) + '\n')

    def close(self):
        self.values.close()
        self.flags.close()


def cli_table(path, original, id_columns, observations):
    """Preserve the actual original CLI parser, without repairing its float changes."""
    import numpy as np
    import pandas as pd
    parsed = pd.read_table(path)
    common.require(list(parsed.columns) == list(original.columns) and len(parsed) == len(original), 'Original CLI parse changed table shape/schema')
    for name in id_columns:
        common.require(all(isinstance(after, str) and before == after for before, after in
                           zip(original[name], parsed[name])),
                       'Original CLI parser changed literal IDs in ' + path.name + ':' + name +
                       '; raw output retained. Qualify the intended author interface; no ID repair is applied.')
    changes = {}
    for name in original.columns:
        if pd.api.types.is_float_dtype(original[name].dtype) and pd.api.types.is_float_dtype(parsed[name].dtype):
            before, after = original[name].to_numpy(dtype=np.float64), parsed[name].to_numpy(dtype=np.float64)
            finite = np.isfinite(before) & np.isfinite(after)
            changes[name] = int(((before.view(np.uint64) != after.view(np.uint64)) & finite).sum())
    observations.append(dict(table=path.name, parser='Unmodified pandas.read_table defaults as in original CLI',
                             changed_finite_values=changes, original_dtypes=[str(v) for v in original.dtypes],
                             parsed_dtypes=[str(v) for v in parsed.dtypes]))
    return parsed


def execute(root, request, report):
    import numpy as np
    import pandas as pd
    import anndata as ad
    import mudata as md
    from scenicplus.TF_to_gene import calculate_TFs_to_genes_relationships
    from scenicplus.enhancer_to_gene import calculate_regions_to_genes_relationships, GBM_KWARGS
    from scenicplus.grn_builder.gsea_approach import build_grn
    from scenicplus.cli.commands import _format_egrns
    from scenicplus.triplet_score import calculate_triplet_score
    from scenicplus.eregulon_enrichment import score_eRegulons
    options, limits = request['options'], request['limits']
    out = root / 'worker/out'
    out.mkdir()
    expression = common.matrix(root / 'inputs/expression.tsv', limits['maximum_genes'], limits)
    accessibility = common.matrix(root / 'inputs/accessibility.tsv', limits['maximum_regions'], limits)
    common.require(expression.index.equals(accessibility.index), 'Identical ordered literal cells required; no implicit pairing or sorting')
    search = search_space(root / 'inputs/search_space.tsv', expression, accessibility, limits)
    tfs = (root / 'inputs/tfs.txt').read_text(encoding='utf-8').splitlines()
    common.require(tfs and len(set(tfs)) == len(tfs), 'Require nonempty unique literal TF list')
    for tf in tfs:
        common.label(tf)
    common.require(len(tfs) <= limits['maximum_genes'], 'TF capacity exceeded')
    closed_h5ad(root / 'inputs/cistromes.h5ad', limits)
    cistromes = ad.read_h5ad(root / 'inputs/cistromes.h5ad')
    common.require(cistromes.X.dtype == np.dtype(bool), 'Original cistrome membership must be Boolean, not implicitly cast integer/score data')
    common.require(cistromes.n_obs <= limits['maximum_regions'] and cistromes.n_vars <= limits['maximum_genes'], 'Cistrome axis capacity exceeded')
    common.require(cistromes.obs_names.is_unique and cistromes.var_names.is_unique and 'motifs' in cistromes.var, 'Unique cistrome axes and explicit motifs annotation required')
    for name in [*cistromes.obs_names, *cistromes.var_names]:
        common.label(name)
    common.require(all(isinstance(v, str) and v for v in cistromes.var['motifs']), 'Cistrome motifs must be explicit nonempty comma-separated strings')
    common.write(out / 'tf-selection.json', dict(requested=tfs,
                 present_in_expression=[tf for tf in tfs if tf in expression.columns],
                 absent_from_expression=[tf for tf in tfs if tf not in expression.columns],
                 original_api_receives_unmodified_requested_list=True,
                 per_target_fit_status='not_exposed_by_public_API'))
    invocation = dict(operation='NETWORK', options=options, R2G_method='GBM', R2G_importance_options=dict(GBM_KWARGS),
                      n_cpu=1, add_distance=True, merge_eRegulons=options['egrn']['merge_eRegulons'],
                      cells=len(expression), genes=len(expression.columns), regions=len(accessibility.columns),
                      candidate_pairs=len(search), original_author_settings_inferred=False,
                      activity_ranking_seed='Unchanged original rank_data default 123',
                      triplet_tie_rng='Unchanged original module default; one complete invocation per fresh worker')
    common.write(out / 'invocation.json', invocation)
    report['original_steps'] = []
    boundaries = []
    def phase(name, fn):
        report['current_step'] = name
        start = time.monotonic()
        value = fn()
        report['original_steps'].append(dict(step=name, original_call_returned=True, seconds=time.monotonic() - start))
        return value
    for name in ('tf2g-temporary', 'r2g-temporary', 'egrn-temporary'):
        (root / 'worker' / name).mkdir()
    exports = Exports(out)
    try:
        tf2g = phase('TF2G', lambda:calculate_TFs_to_genes_relationships(
            df_exp_mtx=expression, tf_names=tfs, temp_dir=root / 'worker/tf2g-temporary',
            method=options['tf_method'], n_cpu=1, seed=options['tf_seed']))
        tf2g.to_csv(out / 'tf2g.tsv', sep='\t', index=False)
        exports.frame('TF2G', tf2g)
        r2g = phase('R2G', lambda:calculate_regions_to_genes_relationships(
            df_exp_mtx=expression, df_acc_mtx=accessibility, search_space=search,
            temp_dir=root / 'worker/r2g-temporary', mask_expr_dropout=options['mask_expr_dropout'],
            importance_scoring_method='GBM', correlation_scoring_method=options['r2g_correlation'], n_cpu=1, add_distance=True))
        r2g.to_csv(out / 'r2g.tsv', sep='\t', index=False)
        exports.frame('R2G', r2g)
        tf_input, r2g_input = tf2g, r2g
        if options['interface'] == 'CLI_TSV':
            tf_input = cli_table(out / 'tf2g.tsv', tf2g, ['TF','target'], boundaries)
            r2g_input = cli_table(out / 'r2g.tsv', r2g, ['target','region'], boundaries)
        common.write(out / 'interface-boundaries.json', dict(interface=options['interface'], observations=boundaries))
        modules = phase('EGRN', lambda:build_grn(tf_to_gene=tf_input, region_to_gene=r2g_input,
            cistromes=cistromes, temp_dir=str(root / 'worker/egrn-temporary'),
            n_cpu=1, disable_tqdm=False, **options['egrn']))
        unavailable_statistics = 0
        with (out / 'module-ledger.jsonl').open('x', encoding='utf-8', newline='\n') as stream:
            for ordinal, module in enumerate(modules):
                attrs = {}
                for name in ('transcription_factor','is_extended','context','n_target_genes','n_target_regions',
                             'gsea_enrichment_score','gsea_pval','gsea_adj_pval','in_leading_edge'):
                    attrs[name] = scalar(getattr(module, name)) if hasattr(module, name) else {'source_attribute_absent':True}
                finite_statistics = {}
                for name in ('gsea_enrichment_score','gsea_pval','gsea_adj_pval'):
                    value = getattr(module, name, None)
                    finite_statistics[name] = isinstance(value, numbers.Real) and math.isfinite(float(value))
                unavailable_statistics += sum(not v for v in finite_statistics.values())
                stream.write(json.dumps(dict(module=ordinal, source_attributes=attrs, source_numeric_finite=finite_statistics), ensure_ascii=False, allow_nan=False, sort_keys=True) + '\n')
        egrn = phase('FORMAT_EGRN', lambda:_format_egrns(eRegulons=modules, tf_to_gene=tf_input))
        egrn = phase('TRIPLET_SCORE', lambda:calculate_triplet_score(cistromes=cistromes,
                     eRegulon_metadata=egrn, ranking_db_fname=str(root / 'inputs/motifs.regions_vs_motifs.rankings.feather')))
        egrn.to_csv(out / 'eRegulons.tsv', sep='\t', index=False)
        exports.frame('EGRN', egrn)
        activity_input = cli_table(out / 'eRegulons.tsv', egrn,
                                  ['TF','Gene','Region','Gene_signature_name','Region_signature_name'], boundaries) if options['interface'] == 'CLI_TSV' else egrn
        common.write(out / 'interface-boundaries-complete.json', dict(interface=options['interface'], observations=boundaries))
        activity = phase('ACTIVITY', lambda:score_eRegulons(eRegulons=activity_input,
                    gex_mtx=expression, acc_mtx=accessibility, n_cpu=1, **options['activity']))
        mdata = md.MuData({name:ad.AnnData(X=frame) for name, frame in activity.items()})
        mdata.write_h5mu(out / 'activity.h5mu')
        for name, filename in [('Gene_based','gene-activity.tsv'), ('Region_based','region-activity.tsv')]:
            frame = activity[name]
            frame.to_csv(out / filename, sep='\t')
            exports.frame(name, frame, index=True)
        returned = set(zip(r2g['region'], r2g['target']))
        candidates = set(zip(search['Name'], search['Gene']))
        common.require(returned.issubset(candidates), 'Original R2G returned an undeclared candidate; preserve source error')
        with (out / 'unreturned-candidates.tsv').open('x', encoding='utf-8', newline='\n') as stream:
            writer = csv.writer(stream, delimiter='\t', lineterminator='\n')
            writer.writerow(['region','target','source_status'])
            for region, gene in sorted(candidates - returned):
                writer.writerow([region,gene,'not_returned; reason_not_exposed_by_public_API'])
        summary = dict(status='ORIGINAL_PREPARED_NETWORK_RETURNED', interface=options['interface'], TF2G_rows=len(tf2g), R2G_rows=len(r2g),
            eGRN_rows=len(egrn), returned_modules=len(modules), rows=len(egrn), candidate_pairs=len(search),
            gene_activity_shape=list(activity['Gene_based'].shape), region_activity_shape=list(activity['Region_based'].shape),
            nonfinite_numerical_fields=exports.nonfinite, nonfinite_rho_rows=int((~np.isfinite(r2g['rho'])).sum()),
            module_statistics_unavailable_fields=unavailable_statistics,
            unreturned_candidates=len(candidates - returned), absent_requested_TFs=sum(tf not in expression.columns for tf in tfs),
            full_SCENICplus_family_qualified=False, original_author_environment_reproduced=False,
            new_family_credit=0, new_reviews=0, frame_schemas=exports.schemas)
        common.write(out / 'SUMMARY.json', summary)
        return summary
    finally:
        exports.close()


def main():
    root = Path(sys.argv[1]).resolve()
    worker = root / 'worker'
    request = json.loads((worker / 'request.json').read_bytes())
    profile = json.loads(Path(__file__).with_name('SOURCE-PROFILE.json').read_bytes())
    report = dict(status='PREFLIGHT', original_call_returned=False, results_admitted=False, private_capture=True)
    observed = []
    try:
        sys.addaudithook(common.network_policy)
        with warnings.catch_warnings(record=True) as observed:
            warnings.simplefilter('always')
            common.write(worker / 'runtime.json', common.runtime(profile))
            (worker / 'READY.pending').write_bytes(b'SCENICPLUS_PREFLIGHT_V1\n')
            (worker / 'READY.pending').replace(worker / 'READY')
            deadline = time.monotonic() + request['limits']['timeout_seconds']
            while not (worker / 'ACK').is_file():
                common.require(time.monotonic() < deadline, 'Java acknowledgement deadline expired')
                time.sleep(.025)
            common.require((worker / 'ACK').read_bytes() == b'SCENICPLUS_ACK_V1\n', 'Invalid Java acknowledgement')
            report['summary'] = execute(root, request, report)
            report['original_call_returned'] = True
            common.write(worker / 'runtime-after-info.json', common.runtime(profile))
            report['status'] = 'ORIGINAL_RETURNED_PENDING_JAVA_AUDIT'
        code = 0
    except common.Unavailable as error:
        report.update(status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED', cause=str(error), private_traceback=traceback.format_exc())
        code = 3
    except Exception as error:
        report.update(status='FAIL2_NO_COMPLETE_RESULT', cause=str(error), private_traceback=traceback.format_exc())
        code = 2
    report['warnings'] = [dict(category=w.category.__name__, message=str(w.message), source_file=Path(w.filename).name, line=w.lineno) for w in observed]
    common.write(worker / 'report.json', report)
    return code


if __name__ == '__main__':
    sys.exit(main())
