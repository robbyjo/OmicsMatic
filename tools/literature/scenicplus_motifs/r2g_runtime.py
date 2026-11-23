"""Our API caller; no SCENIC+ vendor source is included or modified.

Licensed runtime, source hashes, finite prepared TSVs and a Java ACK are required.
This initial operation is region-to-gene GBM, not the complete eGRN pipeline.
"""
from pathlib import Path
import csv
import hashlib
import importlib
import importlib.metadata as metadata
import json
import math
import os
import platform
import sys
import sysconfig
import time
import traceback
import warnings


class Unavailable(Exception):
    pass


def require(condition, message):
    if not condition:
        raise ValueError(message)


def write(path, value):
    with Path(path).open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, ensure_ascii=False, allow_nan=False, sort_keys=True)
        stream.write('\n')


def identity(path):
    digest = hashlib.sha256()
    size = 0
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1048576), b''):
            size += len(block)
            digest.update(block)
    return dict(bytes=size, sha256=digest.hexdigest())


def packages():
    result = []
    for dist in metadata.distributions():
        origin = Path(dist._path).resolve()
        result.append(dict(name=dist.metadata['Name'], version=dist.version,
                           metadata_origin=origin.relative_to(Path(sys.prefix).resolve()).as_posix(),
                           metadata_files={p.name:identity(p) for p in origin.iterdir()
                                           if p.is_file() and p.name in ('METADATA', 'PKG-INFO')}))
    return sorted(result, key=lambda row:(row['name'], row['metadata_origin']))


def runtime(profile):
    if platform.system() != 'Linux' or platform.machine() != 'x86_64' or platform.python_version() != profile['python']:
        raise Unavailable('Restore the permitted Linux x86_64 Python ' + profile['python'] + ' private runtime')
    # Imports such as Ray expose bundled distribution metadata. Installed and
    # post-import package populations are intentionally separate observations.
    actual = packages()
    expected = profile['base_distributions'] if 'ray' not in sys.modules else profile['loaded_distributions']
    if actual != expected:
        raise Unavailable('Exact installed/loaded package population differs; restore the pinned private runtime')
    dist = metadata.distribution('scenicplus')
    source_root = Path(dist.locate_file('scenicplus')).resolve()
    sources = {'scenicplus/' + p.relative_to(source_root).as_posix(): identity(p)
               for p in source_root.rglob('*.py')}
    if sources != profile['original_python_sources']:
        raise Unavailable('Restore unchanged original SCENIC+ source population; no vendor patch is permitted')
    prefix = Path(sys.prefix).resolve()
    git_sources = {}
    git_roots = {prefix.joinpath(*Path(name).parts[:4]) for name in profile['pinned_git_python_sources']}
    for source in git_roots:
        for path in ([source] if source.is_file() else source.rglob('*.py')):
            git_sources[path.relative_to(prefix).as_posix()] = identity(path)
    if git_sources != profile['pinned_git_python_sources']:
        raise Unavailable('Restore unchanged source populations of the four pinned Git dependencies')
    try:
        for name in ('scenicplus', 'scenicplus.cli.scenicplus', 'scenicplus.enhancer_to_gene',
                     'scenicplus.grn_builder.gsea_approach', 'scenicplus.data_wrangling.adata_cistopic_wrangling'):
            importlib.import_module(name)
    except Exception as error:
        raise Unavailable('Private runtime import failed: ' + str(error)) from error
    require(packages() == profile['loaded_distributions'], 'Unexpected post-import metadata origins/population')
    roots = {str(Path(sys.executable).resolve()), str(Path(sysconfig.get_paths()['stdlib']).resolve())}
    roots.update(str(Path(d.locate_file('')).resolve()) for d in metadata.distributions())
    modules = sorted({str(Path(m.__file__).resolve()) for m in tuple(sys.modules.values())
                      if getattr(m, '__file__', None) and Path(m.__file__).is_file()})
    return dict(profile=profile['profile'], python=platform.python_version(),
                python_executable=str(Path(sys.executable).resolve()), packages=packages(),
                original_python_sources=sources, runtime_roots=sorted(roots), loaded_module_files=modules,
                pinned_git_python_sources=git_sources,
                python_hash_seed=os.environ.get('PYTHONHASHSEED'))


def label(value):
    require(bool(value) and len(value.encode('utf-8')) <= 65536 and
            all(ord(c) >= 32 and ord(c) != 127 for c in value), 'Invalid literal TSV label')
    return value


def matrix(path, feature_limit, limits):
    import numpy as np
    import pandas as pd
    with Path(path).open(encoding='utf-8', newline='') as stream:
        reader = csv.reader(stream, delimiter='\t', quoting=csv.QUOTE_NONE, strict=True)
        header = next(reader)
        require(header[0] == 'cell' and 1 < len(header) <= feature_limit + 1, 'Require cell-by-feature TSV header')
        features = [label(v) for v in header[1:]]
        require(len(set(features)) == len(features), 'Duplicate literal feature')
        cells, rows = [], []
        seen = set()
        for row in reader:
            require(len(row) == len(header), 'Ragged TSV row')
            cell = label(row[0])
            require(cell not in seen, 'Duplicate literal cell')
            seen.add(cell)
            require(len(cells) < limits['maximum_cells'] and
                    (len(cells) + 1) * len(features) <= limits['maximum_matrix_values'], 'Matrix capacity exceeded')
            values = [float(v) for v in row[1:]]
            require(all(math.isfinite(v) for v in values), 'Prepared matrix requires finite numeric input; no replacement applied')
            cells.append(cell)
            rows.append(values)
    require(len(cells) >= 2, 'Require at least two explicit cells')
    return pd.DataFrame(np.asarray(rows, dtype=np.float64), index=pd.Index(cells, name='cell'), columns=features)


def execute(root, request):
    import numpy as np
    import pandas as pd
    import scenicplus.enhancer_to_gene as upstream
    limits = request['limits']
    expression = matrix(root / 'inputs/expression.tsv', limits['maximum_genes'], limits)
    accessibility = matrix(root / 'inputs/accessibility.tsv', limits['maximum_regions'], limits)
    require(expression.index.equals(accessibility.index), 'Require identical ordered literal cells; no implicit pairing or reordering')
    with (root / 'inputs/search_space.tsv').open(encoding='utf-8', newline='') as stream:
        reader = csv.reader(stream, delimiter='\t', quoting=csv.QUOTE_NONE, strict=True)
        require(next(reader) == ['Name', 'Gene', 'Distance'], 'Require exact Name/Gene/Distance search-space header')
        rows, seen = [], set()
        for row in reader:
            require(len(row) == 3, 'Ragged search-space row')
            region, gene = label(row[0]), label(row[1])
            require(region in accessibility.columns and gene in expression.columns, 'Unknown candidate region/gene; no dropping or ID repair')
            require((region, gene) not in seen, 'Duplicate candidate pair')
            seen.add((region, gene))
            require(len(rows) < limits['maximum_pairs'], 'Candidate pair capacity exceeded')
            distance = int(row[2])
            require(str(distance) == row[2] and -(2**63) <= distance < 2**63, 'Require literal signed int64 Distance')
            rows.append((region, gene, distance))
    require(rows, 'Explicit nonempty candidate search space required')
    search = pd.DataFrame(rows, columns=['Name', 'Gene', 'Distance'])
    out = root / 'worker/out'
    out.mkdir()
    temporary = root / 'worker/temporary'
    temporary.mkdir()
    write(out / 'invocation.json', dict(operation='REGION_TO_GENE', method='GBM',
          importance_kwargs=dict(upstream.GBM_KWARGS), correlation=request['options']['correlation'],
          mask_expr_dropout=request['options']['mask_expr_dropout'], n_cpu=1, add_distance=True,
          cells=len(expression), genes=len(expression.columns), regions=len(accessibility.columns),
          candidate_pairs=len(rows), author_seed_inferred=False, default_source_seed_behavior=True))
    result = upstream.calculate_regions_to_genes_relationships(
        df_exp_mtx=expression, df_acc_mtx=accessibility, search_space=search, temp_dir=temporary,
        mask_expr_dropout=request['options']['mask_expr_dropout'], importance_scoring_method='GBM',
        correlation_scoring_method=request['options']['correlation'], n_cpu=1, add_distance=True)
    expected = ['target', 'region', 'importance', 'rho', 'importance_x_rho', 'importance_x_abs_rho', 'Distance']
    require(list(result.columns) == expected, 'Unexpected original result fields')
    result.to_csv(out / 'adjacencies.tsv', sep='\t', index=False)
    # Hex encodings retain float64 values, signed zero and nonfinite values.
    # No eligibility inference for undefined derived fields is made from success.
    encoded, ledger, returned = [], [], set()
    for index, values in enumerate(result.itertuples(index=False, name=None)):
        target, region = str(values[0]), str(values[1])
        require((region, target) in seen, 'Original returned an undeclared candidate pair')
        require((region, target) not in returned, 'Original returned a duplicate pair')
        returned.add((region, target))
        numeric = [float(v) for v in values[2:6]]
        encoded.append([target, region, *[v.hex() for v in numeric], int(values[6])])
        ledger.append(dict(row=index, target=target, region=region,
                           finite={key:math.isfinite(value) for key, value in zip(expected[2:6], numeric)},
                           original_call_returned=True, per_target_fit_status='not_exposed_by_public_API'))
    write(out / 'scientific.json', dict(columns=expected, dtypes=[str(v) for v in result.dtypes], rows=encoded))
    write(out / 'field-ledger.json', dict(rows=ledger, unreturned_candidates=[list(v) for v in sorted(seen - returned)]))
    summary = dict(status='ORIGINAL_R2G_RETURNED', rows=len(result), candidate_pairs=len(rows),
                   nonfinite_rho_rows=int((~np.isfinite(result['rho'])).sum()),
                   nonfinite_numerical_fields=sum(not math.isfinite(float(value)) for values in result.iloc[:, 2:6].itertuples(index=False, name=None) for value in values),
                   unreturned_candidates=len(seen - returned), full_eGRN_qualified=False,
                   original_author_environment_reproduced=False, new_family_credit=0, new_reviews=0)
    write(out / 'SUMMARY.json', summary)
    return summary


def network_policy(event, arguments):
    if event == 'socket.connect':
        address = arguments[1]
        if isinstance(address, tuple) and address[0] not in ('127.0.0.1', '::1', 'localhost'):
            raise PermissionError('External scientific network access refused; supply explicit local inputs')


def main():
    root = Path(sys.argv[1]).resolve()
    worker = root / 'worker'
    request = json.loads((worker / 'request.json').read_bytes())
    profile = json.loads(Path(__file__).with_name('SOURCE-PROFILE.json').read_bytes())
    report = dict(status='PREFLIGHT', original_call_returned=False, results_admitted=False)
    observed = []
    try:
        sys.addaudithook(network_policy)
        with warnings.catch_warnings(record=True) as observed:
            warnings.simplefilter('always')
            write(worker / 'runtime.json', runtime(profile))
            (worker / 'READY.pending').write_bytes(b'SCENICPLUS_PREFLIGHT_V1\n')
            (worker / 'READY.pending').replace(worker / 'READY')
            deadline = time.monotonic() + request['limits']['timeout_seconds']
            while not (worker / 'ACK').is_file():
                require(time.monotonic() < deadline, 'Java preflight acknowledgement deadline expired')
                time.sleep(.025)
            require((worker / 'ACK').read_bytes() == b'SCENICPLUS_ACK_V1\n', 'Invalid Java acknowledgement')
            report['summary'] = execute(root, request)
            report['original_call_returned'] = True
            write(worker / 'runtime-after-info.json', runtime(profile))
            report['status'] = 'ORIGINAL_RETURNED_PENDING_JAVA_AUDIT'
        code = 0
    except Unavailable as error:
        report.update(status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED', cause=str(error), private_traceback=traceback.format_exc())
        code = 3
    except Exception as error:
        report.update(status='FAIL2_NO_COMPLETE_RESULT', cause=str(error), private_traceback=traceback.format_exc())
        code = 2
    report['warnings'] = [dict(category=w.category.__name__, message=str(w.message), source_file=Path(w.filename).name, line=w.lineno) for w in observed]
    report['private_capture'] = True
    write(worker / 'report.json', report)
    return code


if __name__ == '__main__':
    sys.exit(main())
