"""Unchanged upstream SCENIC functions with explicit target/module completion ledgers.

This module implements orchestration and typed export, not regression or motif
statistics. An original return is provisional until the Java owner audits it.
"""
from pathlib import Path
from functools import partial
import csv
import json
import logging
import math
import multiprocessing
import re
import traceback
import warnings


def require(condition, message):
    if not condition:
        raise ValueError(message)


def write_json(path, value):
    with Path(path).open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, ensure_ascii=False, allow_nan=False, separators=(',', ':'))
        stream.write('\n')


def read_json(path):
    with Path(path).open(encoding='utf-8') as stream:
        return json.load(stream)


def label(value):
    require(isinstance(value, str) and value and not any(ord(c) < 32 or ord(c) == 127 or 0xd800 <= ord(c) <= 0xdfff for c in value),
            'Require a nonempty literal label without control characters or unpaired surrogates')
    return value


def encoded(value):
    """Tagged nonfinite values remain explicit; labels are never normalized."""
    import numpy as np
    if value is None or isinstance(value, (str, bool)):
        return value
    if isinstance(value, (int, np.integer)):
        return int(value)
    if isinstance(value, (float, np.floating)):
        number = float(value)
        return number if math.isfinite(number) else {'numeric': 'NaN' if math.isnan(number) else 'Infinity' if number > 0 else '-Infinity'}
    if isinstance(value, dict) or hasattr(value, 'items'):
        return {str(k): encoded(v) for k, v in value.items()}
    if isinstance(value, (set, frozenset)):
        return [encoded(v) for v in sorted(value)]
    if isinstance(value, (tuple, list, np.ndarray)):
        return [encoded(v) for v in value]
    raise TypeError('Unsupported upstream output type: ' + str(type(value)))


def warnings_list(records):
    return [dict(category=w.category.__name__, message=str(w.message), filename=w.filename, line=w.lineno) for w in records]


class Messages(logging.Handler):
    def __init__(self):
        super().__init__(logging.WARNING)
        self.messages = []

    def emit(self, record):
        self.messages.append(dict(level=record.levelname, logger=record.name, message=record.getMessage()))


def read_expression(spec, limits):
    """Strict cell-by-gene TSV. NA-looking labels remain literal strings."""
    import numpy as np
    import pandas as pd
    require(set(spec) == {'format', 'file'} and spec['format'] == 'tsv', 'Declare a complete prepared literal cell-by-gene TSV')
    rows, cells = [], []
    with Path(spec['file']).open(encoding='utf-8', errors='strict', newline=None) as stream:
        header = stream.readline(limits['maximum_line_bytes'] + 1)
        require(header.endswith('\n') and len(header.encode('utf-8')) <= limits['maximum_line_bytes'], 'Missing or over-capacity expression header')
        columns = header[:-1].split('\t')
        require(columns[0] == 'cell' and len(columns) > 1, 'Expression header must start with literal cell followed by gene labels')
        genes = [label(v) for v in columns[1:]]
        require(len(genes) == len(set(genes)) and len(genes) <= limits['maximum_features'], 'Duplicate genes or feature capacity exceeded')
        while True:
            line = stream.readline(limits['maximum_line_bytes'] + 1)
            if not line:
                break
            require(line.endswith('\n') and len(line.encode('utf-8')) <= limits['maximum_line_bytes'], 'Expression row missing newline or exceeds capacity')
            fields = line[:-1].split('\t')
            require(len(fields) == len(columns), 'Ragged expression row; no identifier repair or imputation')
            cells.append(label(fields[0]))
            rows.append([input_number(v) for v in fields[1:]])
            require(len(cells) <= limits['maximum_cells'] and len(cells) * len(genes) <= limits['maximum_dense_entries'], 'Expression capacity exceeded')
            require(len(cells) * len(genes) * 40 <= limits['maximum_working_bytes'], 'Input staging memory estimate exceeds declared capacity')
    require(len(cells) >= 2 and len(cells) == len(set(cells)), 'Require at least two distinct literal fitting cells')
    matrix = np.asarray(rows, dtype=np.float64)
    return pd.DataFrame(matrix, index=cells, columns=genes)


def input_number(value):
    require(re.fullmatch(r'(?:[+-]?(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?|NaN|[+-]?Infinity)', value) is not None,
            'Require a literal decimal, NaN or signed Infinity; no whitespace, hexadecimal or shorthand inf')
    number = float(value)
    require(math.isfinite(number) or value in ('NaN', 'Infinity', '+Infinity', '-Infinity'), 'Finite decimal overflows FP64; supply explicit Infinity if intended')
    return number


def read_tfs(path, genes):
    with Path(path).open(encoding='utf-8', errors='strict', newline=None) as stream:
        names = [label(line.removesuffix('\n')) for line in stream]
    require(names and len(names) == len(set(names)), 'Require distinct literal TF names, one per line, without a header')
    # Original to_tf_matrix takes the intersection in expression-column order.
    selected = [gene for gene in genes if gene in set(names)]
    require(selected, 'The literal TF list has no intersection with expression genes')
    return names, selected


def signature(value):
    import attr
    result = {field.name: encoded(getattr(value, field.name)) for field in attr.fields(type(value))}
    result['gene_weights'] = [[label(gene), encoded(weight)] for gene, weight in value.gene2weight.items()]
    result.pop('gene2weight')
    return result


def export_signatures(path, signatures):
    with Path(path).open('x', encoding='utf-8', newline='\n') as stream:
        for value in signatures:
            json.dump(signature(value), stream, ensure_ascii=False, allow_nan=False, separators=(',', ':'))
            stream.write('\n')


def export_frame(path, data):
    with Path(path).open('x', encoding='utf-8', newline='\n') as stream:
        descriptors = dict(schema_version=1, columns=encoded(list(data.columns)), index_names=encoded(list(data.index.names)),
                           column_names=encoded(list(data.columns.names)), dtypes=[str(v) for v in data.dtypes])
        prefix = json.dumps(descriptors, ensure_ascii=False, allow_nan=False, separators=(',', ':'))
        stream.write(prefix[:-1] + ',"rows":[')
        for at, row in enumerate(data.itertuples(index=True, name=None)):
            if at:
                stream.write(',')
            json.dump(dict(index=encoded(row[0]), values=encoded(row[1:])), stream, ensure_ascii=False, allow_nan=False, separators=(',', ':'))
        stream.write(']}\n')


def fit_init(expression, gene_names, tf_matrix, tf_names, method, seed):
    global FIT
    FIT = (expression, gene_names, tf_matrix, tf_names, method, seed)


def fit_target(index):
    from arboreto.core import infer_partial_network, SGBM_KWARGS, RF_KWARGS, EARLY_STOP_WINDOW_LENGTH
    expression, genes, matrix, names, method, seed = FIT
    handler = Messages(); logger = logging.getLogger('arboreto.core'); logger.addHandler(handler)
    records = []
    try:
        with warnings.catch_warnings(record=True) as records:
            warnings.simplefilter('always')
            links, meta = infer_partial_network('GBM' if method == 'GRNBOOST2' else 'RF',
                SGBM_KWARGS if method == 'GRNBOOST2' else RF_KWARGS, matrix, names, genes[index], expression[:, index],
                include_meta=True, early_stop_window_length=EARLY_STOP_WINDOW_LENGTH, seed=seed)
        success = len(meta) == 1 and meta.iloc[0]['target'] == genes[index] and int(meta.iloc[0]['n_estimators']) > 0
        return dict(index=index, target=genes[index], status='ORIGINAL_FITTED' if success else 'ORIGINAL_RETRY_FALLBACK_NO_FIT',
                    n_estimators=int(meta.iloc[0]['n_estimators']) if success else None,
                    edge_count=len(links), warnings=warnings_list(records), logs=handler.messages), links
    except Exception as error:
        return dict(index=index, target=genes[index], status='ORIGINAL_TASK_EXCEPTION', error=repr(error),
                    traceback=traceback.format_exc(), warnings=warnings_list(records), logs=handler.messages), None
    finally:
        logger.removeHandler(handler)


def ctx_init(database, modules, annotations, options):
    global CTX, CTX_LOADED
    CTX = (database, modules, annotations, options)
    CTX_LOADED = None


def ctx_module(index):
    from ctxcore.rnkdb import FeatherRankingDatabase, MemoryDecorator
    from pyscenic.utils import load_motif_annotations
    from pyscenic.transform import module2df, module2features_auc1st_impl
    global CTX_LOADED
    database, modules, annotation_file, options = CTX
    module = modules[index]
    handler = Messages(); logger = logging.getLogger('pyscenic.transform'); logger.addHandler(handler)
    records = []
    try:
        with warnings.catch_warnings(record=True) as records:
            warnings.simplefilter('always')
            if CTX_LOADED is None:
                db = MemoryDecorator(FeatherRankingDatabase(database['file'], database['name']))
                # Admission checks do not alter ranks or replace any upstream statistic.
                import numpy as np
                full = db.load_full()
                require(full.index.is_unique and full.columns.is_unique, 'Ranking database requires unique literal motif and gene labels')
                require(full.values.dtype in (np.dtype('int16'), np.dtype('int32')), 'Original ranking database must contain int16/int32 ranks')
                for ranks in full.values:
                    require(ranks.min() >= 0 and ranks.max() < db.total_genes and
                            np.all(np.bincount(ranks, minlength=db.total_genes) == 1),
                            'Ranking row is not a complete 0-based gene permutation; original input retained without repair')
                annotations = load_motif_annotations(annotation_file, motif_similarity_fdr=options['motif_similarity_fdr'],
                    orthologous_identity_threshold=options['orthologuous_identity_threshold'])
                CTX_LOADED = (db, annotations)
            db, annotations = CTX_LOADED
            func = partial(module2features_auc1st_impl, rank_threshold=options['rank_threshold'], auc_threshold=options['auc_threshold'],
                           nes_threshold=options['nes_threshold'], filter_for_annotation=options['filter_for_annotation'])
            result = module2df(db, module, annotations, weighted_recovery=options['weighted_recovery'],
                               return_recovery_curves=False, module2features_func=func)
        missing = len(module) - len(set(module.genes).intersection(db.genes))
        errors = any(row['level'] in ('ERROR', 'CRITICAL') for row in handler.messages)
        status = ('ORIGINAL_CAUGHT_ERROR_NO_COMPLETE_MODULE' if errors else
                  'SOURCE_SKIPPED_GENE_COVERAGE' if missing / len(module) >= .20 else
                  'ORIGINAL_ENRICHED' if len(result) else 'SOURCE_NO_ANNOTATED_ENRICHED_MOTIFS')
        return dict(index=index, name=module.name, database=database['name'], status=status, module_genes=len(module),
                    missing_genes=missing, enriched_rows=len(result), warnings=warnings_list(records), logs=handler.messages), result
    except Exception as error:
        return dict(index=index, name=module.name, database=database['name'], status='ORIGINAL_TASK_EXCEPTION',
                    error=repr(error), traceback=traceback.format_exc(), warnings=warnings_list(records), logs=handler.messages), None
    finally:
        logger.removeHandler(handler)


def execute(data, request, output, event):
    import pandas as pd
    from arboreto.core import to_tf_matrix, SGBM_KWARGS, RF_KWARGS, EARLY_STOP_WINDOW_LENGTH
    from pyscenic.utils import modules_from_adjacencies
    from pyscenic.transform import DF_META_DATA, df2regulons
    output.mkdir()
    options, limits = request['options'], request['limits']
    requested_tfs, selected_tfs = read_tfs(request['inputs']['tfs'], list(data.columns))
    write_json(output / 'tf-selection.json', dict(requested=requested_tfs, selected_in_expression_order=selected_tfs,
               absent_from_expression=[v for v in requested_tfs if v not in data.columns]))
    matrix, names = to_tf_matrix(data.values, list(data.columns), requested_tfs)
    trees = SGBM_KWARGS['n_estimators'] if options['method'] == 'GRNBOOST2' else RF_KWARGS['n_estimators']
    work = len(data) * len(data.columns) * len(names) * trees
    require(work <= limits['maximum_work'], 'Conservative GRN tree-work estimate ' + str(work) + ' exceeds maximum_work=' + str(limits['maximum_work']) +
            ' (cells*genes*matching_TFs*maximum_trees). Explicitly raise the declared work budget for the intended data, or retain this failure; no automatic downsampling.')
    require(data.values.nbytes * (4 + 2 * options['workers']) <= limits['maximum_working_bytes'], 'Conservative fit matrix memory estimate exceeds capacity')
    invocation = dict(fitting_shape=list(data.shape), requested_tfs=len(requested_tfs), selected_tfs=len(names),
        target_genes=list(data.columns), conservative_grn_work=work,
        grn=dict(function='arboreto.core.infer_partial_network', include_meta=True, method=options['method'], seed=options['seed'],
                 regressor_kwargs=SGBM_KWARGS if options['method'] == 'GRNBOOST2' else RF_KWARGS,
                 early_stop_window_length=EARLY_STOP_WINDOW_LENGTH), modules=options['modules'], ctx=options['ctx'],
        ctx_composition='Original module2df(module2features_auc1st_impl), MemoryDecorator and df2regulons; per-module task ledgers, Linux fork processes. Independent original prune2df comparison is required for qualification.')
    write_json(output / 'invocation.json', invocation); event('CALL', invocation)
    fits, networks = [], []
    context = multiprocessing.get_context('fork')
    with (output / 'target-ledger.jsonl').open('x', encoding='utf-8', newline='\n') as ledger:
        with context.Pool(options['workers'], initializer=fit_init,
                initargs=(data.values, list(data.columns), matrix, names, options['method'], options['seed'])) as pool:
            for record, links in pool.imap(fit_target, range(len(data.columns)), chunksize=1):
                fits.append(record); ledger.write(json.dumps(record, ensure_ascii=False, allow_nan=False) + '\n'); ledger.flush()
                if links is not None:
                    networks.append(links)
        adjacencies = pd.concat(networks).sort_values('importance', ascending=False) if networks else pd.DataFrame(columns=['TF', 'target', 'importance'])
        export_frame(output / 'adjacencies.json', adjacencies)
    failed_fits = [row for row in fits if row['status'] != 'ORIGINAL_FITTED']
    if len(fits) != len(data.columns) or failed_fits:
        details = [dict(target=row['target'], status=row['status'], cause=row.get('error') or (row['logs'][-1]['message'] if row['logs'] else 'Missing original fit metadata')) for row in failed_fits[:10]]
        raise ValueError('Original target fitting incomplete: ' + json.dumps(details, ensure_ascii=False) +
                         '. Full target ledger and partial adjacency retained; CTX not started. Correct the named predictor/input/runtime cause and retry NEW output; no automatic target omission.')
    modules = list(modules_from_adjacencies(adjacencies, data, **options['modules']))
    require(len(modules) <= limits['maximum_modules'], 'Original coexpression module population exceeds declared capacity')
    require(len(modules) * len(request['inputs']['databases']) <= 1_000_000, 'Complete CTX ledger exceeds the supported million-task bound')
    export_signatures(output / 'modules.jsonl', modules)
    frames, tasks = [], []
    with (output / 'ctx-ledger.jsonl').open('x', encoding='utf-8', newline='\n') as ledger:
        for database in request['inputs']['databases']:
            from ctxcore.rnkdb import FeatherRankingDatabase
            db = FeatherRankingDatabase(database['file'], database['name'])
            motifs = db.ct_db.nbr_total_motif_or_track_ids
            require(db.total_genes * motifs <= limits['maximum_database_entries'], 'Ranking database entries exceed capacity')
            require(motifs <= limits['maximum_motifs'], 'Ranking motif population exceeds capacity')
            require((db.total_genes * motifs * 8 + motifs * options['ctx']['rank_threshold'] * 24) * options['workers'] <= limits['maximum_working_bytes'],
                    'Conservative CTX database/recovery memory estimate exceeds declared capacity')
            require(0 < options['ctx']['rank_threshold'] < db.total_genes, 'Original CTX rank threshold must be below database gene count')
            require(motifs * len(modules) * options['ctx']['rank_threshold'] <= limits['maximum_work'], 'Conservative CTX work estimate exceeds capacity')
            if not modules:
                continue
            with context.Pool(options['workers'], initializer=ctx_init,
                    initargs=(database, modules, request['inputs']['annotations'], options['ctx'])) as pool:
                for record, frame in pool.imap(ctx_module, range(len(modules)), chunksize=1):
                    tasks.append(record); ledger.write(json.dumps(record, ensure_ascii=False, allow_nan=False) + '\n'); ledger.flush()
                    if frame is not None:
                        frames.append(frame)
    enriched = pd.concat(frames) if frames else DF_META_DATA.copy()
    require(len(enriched) <= limits['maximum_motif_rows'], 'Enrichment rows exceed declared export capacity')
    export_frame(output / 'motif-enrichment.json', enriched)
    failed_ctx = [row for row in tasks if row['status'] in ('ORIGINAL_CAUGHT_ERROR_NO_COMPLETE_MODULE', 'ORIGINAL_TASK_EXCEPTION')]
    if len(tasks) != len(modules) * len(request['inputs']['databases']) or failed_ctx:
        details = [dict(module=row['name'], database=row['database'], status=row['status'], cause=row.get('error') or (row['logs'][-1]['message'] if row['logs'] else 'Missing original module completion')) for row in failed_ctx[:10]]
        raise ValueError('Original CTX task failure: ' + json.dumps(details, ensure_ascii=False) +
                         '. Complete module ledger and partial motifs retained; no activity admitted. Resolve the named database/annotation/runtime cause, or explicitly provide adequate memory/fewer workers for a caught MemoryError; retry NEW output without changing data silently.')
    regulons = list(df2regulons(enriched)) if len(enriched) else []
    export_signatures(output / 'regulons.jsonl', regulons)
    result = dict(targets=len(fits), fitted_targets=sum(r['status'] == 'ORIGINAL_FITTED' for r in fits),
        adjacency_rows=len(adjacencies), modules=len(modules), ctx_tasks=len(tasks),
        skipped_gene_coverage=sum(r['status'] == 'SOURCE_SKIPPED_GENE_COVERAGE' for r in tasks),
        enriched_rows=len(enriched), regulons=len(regulons),
        outcome='REGULONS_RETURNED' if regulons else 'NO_SCORABLE_REGULONS', results_admitted=False)
    write_json(output / 'SUMMARY.json', result); event('RETURNED', result)
    return result
