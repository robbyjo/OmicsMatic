"""Typed I/O boundary to the unmodified, pinned original CellBender full-count workflow."""
import sys
from pathlib import Path
sys.path.insert(0, sys.argv[1])
sys.prefix = sys.exec_prefix = str(Path(sys.argv[1]).parents[2])
import csv
import gzip
import json
import math
import traceback
import h5py
import numpy as np
import scipy.sparse as sp


def strings(dataset):
    if dataset.ndim != 1 or dataset.size > 10000000:
        raise ValueError('Unexpected HDF5 identifier array shape/bound')
    result = []
    for value in dataset[:]:
        text = value.decode('utf-8', errors='strict') if isinstance(value, bytes) else str(value)
        if not text or any(ord(c) < 32 or ord(c) == 127 for c in text):
            raise ValueError('Empty/control-containing original output identifier')
        result.append(text)
    return result


def matrix(path, expected_features, max_cells, max_nnz):
    with h5py.File(path, 'r') as stream:
        group = stream['matrix']
        shape = tuple(int(n) for n in group['shape'][:])
        if len(shape) != 2 or shape[0] != expected_features or not 0 <= shape[1] <= max_cells:
            raise ValueError('Original HDF5 dimensions differ from declared feature/barcode bounds')
        data, indices, pointers = group['data'], group['indices'], group['indptr']
        if data.ndim != 1 or data.size > max_nnz or indices.shape != data.shape or pointers.shape != (shape[1]+1,):
            raise ValueError('Original HDF5 sparse allocation bounds or array lengths differ')
        values = data[:]
        row_indices = indices[:]
        column_pointers = pointers[:]
        if values.dtype.kind not in 'iu' or np.any(values < 0) or np.any(values > 9007199254740991):
            raise ValueError('Original MCKP output counts must be nonnegative exact integers')
        if row_indices.dtype.kind not in 'iu' or column_pointers.dtype.kind not in 'iu' or column_pointers[0] != 0 or column_pointers[-1] != values.size or np.any(column_pointers > values.size) or np.any(np.diff(column_pointers.astype(np.int64)) < 0) or np.any(row_indices < 0) or np.any(row_indices >= shape[0]):
            raise ValueError('Original HDF5 sparse indices/pointers are invalid')
        for i in range(shape[1]):
            selected = row_indices[column_pointers[i]:column_pointers[i+1]]
            if np.any(np.diff(selected.astype(np.int64)) <= 0):
                raise ValueError('Original HDF5 sparse coordinates are duplicated or unordered')
        barcodes = strings(group['barcodes'])
        features = strings(group['features/id'])
        names = strings(group['features/name'])
        types = strings(group['features/feature_type'])
        if len(barcodes) != shape[1] or len(features) != shape[0] or len(names) != shape[0] or len(types) != shape[0]:
            raise ValueError('Original HDF5 feature/barcode identifiers differ from dimensions')
        if len(set(barcodes)) != len(barcodes) or len(set(features)) != len(features):
            raise ValueError('Original HDF5 identifiers are duplicated')
        counts = sp.csc_matrix((values, row_indices, column_pointers), shape=shape).T.tocsr()
    return counts, barcodes, list(zip(features, names, types))


def write_matrix(path, counts):
    """Cell-major canonical integer Matrix Market; retain empty rows/columns in dimensions."""
    counts.eliminate_zeros()
    with path.open('w', encoding='utf-8', newline='\n') as stream:
        stream.write('%%MatrixMarket matrix coordinate integer general\n')
        stream.write(f'{counts.shape[1]} {counts.shape[0]} {counts.nnz}\n')
        for cell in range(counts.shape[0]):
            for index in range(counts.indptr[cell], counts.indptr[cell+1]):
                stream.write(f'{int(counts.indices[index])+1} {cell+1} {int(counts.data[index])}\n')


def write_table(path, rows):
    with path.open('w', encoding='utf-8', newline='\n') as stream:
        for row in rows:
            if any('\t' in str(v) or '\n' in str(v) or '\r' in str(v) for v in row):
                raise ValueError('Control-containing output table field')
            stream.write('\t'.join(map(str, row))+'\n')


def run(cfg, out):
    counts = Path(cfg['counts'])
    inputs = out/'original-input'
    inputs.mkdir()
    source_features = [line.split('\t') for line in (counts/'features.tsv').read_text(encoding='utf-8').splitlines()]
    expected = [(row[0], row[1], row[2] if len(row) == 3 else cfg['untyped_feature_type']) for row in source_features]
    barcodes = (counts/'barcodes.tsv').read_text(encoding='utf-8').splitlines()
    for name in ['matrix.mtx', 'barcodes.tsv']:
        with (counts/name).open('rb') as source, (inputs/(name+'.gz')).open('xb') as target:
            with gzip.GzipFile(filename='', mode='wb', fileobj=target, mtime=0) as compressed:
                while chunk := source.read(1048576):
                    compressed.write(chunk)
    with (inputs/'features.tsv.gz').open('xb') as target:
        with gzip.GzipFile(filename='', mode='wb', fileobj=target, mtime=0) as compressed:
            compressed.write(''.join('\t'.join(row)+'\n' for row in expected).encode('utf-8'))
    original = out/'original'
    original.mkdir()
    command = ['cellbender', 'remove-background', '--input', str(inputs), '--output', str(original/'corrected.h5'),
               '--model', cfg['model'], '--estimator', 'mckp', '--epochs', str(cfg['epochs']),
               '--fpr', str(cfg['fpr']), '--learning-rate', str(cfg['learning_rate']), '--cpu-threads', str(cfg['threads']),
               '--num-training-tries', '1', '--checkpoint', str(original/'ckpt.tar.gz'),
               '--low-count-threshold', str(cfg['low_count_threshold']), '--z-dim', str(cfg['z_dim']),
               '--training-fraction', str(cfg['training_fraction']), '--empty-drop-training-fraction', str(cfg['empty_fraction']),
               '--posterior-batch-size', str(cfg['posterior_batch_size']), '--z-layers', *map(str, cfg['z_layers'])]
    for key, flag in [('expected_cells', '--expected-cells'), ('total_droplets', '--total-droplets-included')]:
        if cfg[key] is not None:
            command.extend([flag, str(cfg[key])])
    (out/'original-command.json').write_text(json.dumps(command, indent=2)+'\n', encoding='utf-8')
    import cellbender.base_cli
    sys.argv = command
    if cellbender.base_cli.main() != 0:
        raise ValueError('Original CellBender did not complete')
    required = ['corrected.h5', 'corrected_filtered.h5', 'corrected_cell_barcodes.csv', 'corrected_metrics.csv',
                'corrected_posterior.h5', 'corrected.log', 'corrected.pdf', 'corrected_report.html', 'ckpt.tar.gz']
    for name in required:
        path = original/name
        if not path.is_file() or path.stat().st_size == 0:
            raise ValueError('Original result family is incomplete: '+name)
    full, output_barcodes, output_features = matrix(original/'corrected.h5', len(expected), len(barcodes), cfg['maximum_nonzeros'])
    selected, selected_barcodes, selected_features = matrix(original/'corrected_filtered.h5', len(expected), len(barcodes), cfg['maximum_nonzeros'])
    if output_barcodes != barcodes or output_features != expected or selected_features != expected:
        raise ValueError('Original feature/barcode identity/order differs from explicit canonical inputs')
    write_matrix(out/'full-corrected.mtx', full)
    write_matrix(out/'filtered-corrected.mtx', selected)
    write_table(out/'full-features.tsv', output_features)
    write_table(out/'full-barcodes.tsv', [(b,) for b in output_barcodes])
    write_table(out/'filtered-barcodes.tsv', [(b,) for b in selected_barcodes])
    with h5py.File(original/'corrected.h5', 'r') as stream:
        p = stream['droplet_latents/cell_probability'][:]
        indices = stream['droplet_latents/barcode_indices_for_latents'][:]
        fraction = stream['droplet_latents/background_fraction'][:]
        if p.ndim != 1 or p.shape != indices.shape or p.shape != fraction.shape or indices.dtype.kind not in 'iu' or len(p) > len(barcodes):
            raise ValueError('Original probability/index/fraction shape differs')
        if len(set(indices)) != len(indices) or np.any(indices < 0) or np.any(indices >= len(barcodes)):
            raise ValueError('Original analyzed indices are duplicated or outside original barcode universe')
        if not np.all(np.isfinite(p)) or np.any(p < 0) or np.any(p > 1) or not np.all(np.isfinite(fraction)):
            raise ValueError('Original probabilities/fractions contain invalid values')
        if not np.array_equal(indices, stream['metadata/barcodes_analyzed_inds'][:]) or strings(stream['metadata/barcodes_analyzed']) != [barcodes[i] for i in indices]:
            raise ValueError('Original analyzed barcode metadata disagrees with probability indices')
        metadata = stream['metadata']
        train = metadata['learning_curve_train_elbo'][:]
        test = metadata['learning_curve_test_elbo'][:]
        if train.ndim != 1 or len(train) != cfg['epochs'] or not np.all(np.isfinite(train)) or not np.all(np.isfinite(test)):
            raise ValueError('Original learning curve is incomplete/nonfinite')
        if metadata['estimator'][0] != b'mckp' or float(metadata['target_false_positive_rate'][0]) != cfg['fpr']:
            raise ValueError('Original estimator/FPR differs from explicit configuration')
        write_table(out/'model-probabilities.tsv', [('cell_id', 'input_index', 'probability', 'upstream_background_fraction'),
                    *[(barcodes[i], int(i)+1, repr(float(q)), repr(float(f))) for i, q, f in zip(indices, p, fraction)]])
        analyzed_features = metadata['features_analyzed_inds'][:]
        if analyzed_features.ndim != 1 or analyzed_features.dtype.kind not in 'iu' or len(set(analyzed_features)) != len(analyzed_features) or np.any(analyzed_features < 0) or np.any(analyzed_features >= len(expected)):
            raise ValueError('Original analyzed feature indices are invalid')
        analyzed = set(analyzed_features.tolist())
        write_table(out/'feature-disposition.tsv', [('feature_id', 'model_analyzed'), *[(row[0], str(i in analyzed).lower()) for i, row in enumerate(expected)]])
        learning = [('kind', 'epoch', 'value')]
        for kind in ['train', 'test']:
            values = metadata['learning_curve_'+kind+'_elbo'][:]
            epochs = metadata['learning_curve_'+kind+'_epoch'][:]
            if epochs.shape != values.shape:
                raise ValueError('Original epoch/value arrays differ')
            learning.extend((kind, int(e), repr(float(v))) for e, v in zip(epochs, values))
        write_table(out/'learning-curve.tsv', learning)
    metrics = []
    with (original/'corrected_metrics.csv').open(encoding='utf-8', newline='') as source:
        seen = set()
        for row in csv.reader(source):
            if len(row) != 2 or row[0] in seen or not math.isfinite(float(row[1])):
                raise ValueError('Original metric row is duplicated/invalid/nonfinite')
            seen.add(row[0])
            metrics.append(row)
    write_table(out/'upstream-metrics.tsv', [('metric', 'value'), *metrics])
    return dict(status='completed-original-results-exported;native-audit-required', engine='CellBender', version='0.4.0', estimator='mckp',
                full_barcodes=len(barcodes), filtered_barcodes=len(selected_barcodes), features=len(expected), analyzed_barcodes=len(p),
                source_cell_threshold='strictly>0.5 for published filtered/CSV;metrics use>=0.5', model=cfg['model'], fpr=cfg['fpr'],
                seed='unmodified upstream constant1234', source_format='explicit gzip-v3 MTX;untyped RNA labels require an explicit declaration',
                limitations='Original float32 model training and approximate inference;count-integrity checks do not establish convergence,real-data accuracy,historical release or full-paper equivalence.')


config_path = Path(sys.argv[2])
cfg = json.loads(config_path.read_text(encoding='utf-8'))
out = Path(cfg['out'])
try:
    status = run(cfg, out)
except Exception as error:
    status = dict(status='failed', error=type(error).__name__+': '+str(error))
    traceback.print_exc()
    (out/'worker-status.json').write_text(json.dumps(status, indent=2)+'\n', encoding='utf-8')
    sys.exit(2)
(out/'worker-status.json').write_text(json.dumps(status, indent=2)+'\n', encoding='utf-8')
