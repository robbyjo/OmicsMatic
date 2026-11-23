"""Original SCENIC worker. Java must fingerprint and acknowledge before fitting."""
from pathlib import Path
import hashlib
import importlib
import importlib.metadata as metadata
import json
import os
import platform
import sys
import sysconfig
import time
import traceback
import warnings
from operations import execute, label, read_expression, read_json, require, warnings_list, write_json


class Unavailable(Exception):
    pass


def identity(path):
    digest = hashlib.sha256(); size = 0
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block); size += len(block)
    return dict(bytes=size, sha256=digest.hexdigest())


def network_policy(event, arguments):
    if event == 'socket.connect':
        address = arguments[1]
        if isinstance(address, tuple) and address[0] not in ('127.0.0.1', '::1', 'localhost'):
            raise PermissionError('External scientific network access refused; provide captured local reference inputs')


def runtime(request, profile):
    if platform.system() != 'Linux' or platform.machine() != 'x86_64':
        raise Unavailable('This original-worker profile requires Linux x86_64; run the current Java producer inside Linux/WSL with host-local paths')
    if platform.python_version() != request['expected_python']:
        raise Unavailable('Restore exact Python ' + request['expected_python'] + '; actual ' + platform.python_version())
    packages = []
    for name, expected in request['expected_packages'].items():
        try:
            actual = metadata.version(name)
        except metadata.PackageNotFoundError as error:
            raise Unavailable('Install permitted exact dependency ' + name + '==' + expected + '; dependent analysis skipped') from error
        if actual != expected:
            raise Unavailable('Restore exact dependency ' + name + '==' + expected + '; actual ' + actual)
    sources = {}
    for name, expected in profile['editions'][request['expected_pyscenic']]['members'].items():
        distribution = metadata.distribution(name.split('/')[0])
        path = Path(distribution.locate_file(name)).resolve()
        if not path.is_file() or identity(path) != expected:
            raise Unavailable('Restore unchanged original source member: ' + str(path))
        sources[name] = expected
    for package in ('pyscenic', 'ctxcore', 'arboreto'):
        dist = metadata.distribution(package); root = Path(dist.locate_file(package)).resolve()
        actual = {package + '/' + p.relative_to(root).as_posix() for p in root.rglob('*.py')}
        if actual != {name for name in sources if name.startswith(package + '/')}:
            raise Unavailable('Unexpected original Python source population for ' + package)
    try:
        for name in ('numpy', 'pandas', 'pyarrow', 'numba', 'sklearn', 'arboreto.core', 'pyscenic.utils', 'pyscenic.transform', 'ctxcore.rnkdb'):
            importlib.import_module(name)
        import pyarrow
        pyarrow.set_cpu_count(1)
        pyarrow.set_io_thread_count(1)
    except Exception as error:
        raise Unavailable('Exact original runtime cannot import; retain the error and restore compatible permitted dependencies: ' + repr(error)) from error
    for dist in metadata.distributions():
        packages.append(dict(name=dist.metadata['Name'], version=dist.version, location=str(Path(dist.locate_file('')).resolve())))
    closure = {str(Path(sys.executable).resolve()), str(Path(sysconfig.get_paths()['stdlib']).resolve())}
    closure.update(row['location'] for row in packages)
    modules = sorted({str(Path(module.__file__).resolve()) for module in list(sys.modules.values())
                      if getattr(module, '__file__', None) and Path(module.__file__).is_file()})
    return dict(profile=profile['profile'], python=platform.python_version(), pyscenic=metadata.version('pyscenic'),
        python_executable=str(Path(sys.executable).resolve()), python_hash_seed=os.environ.get('PYTHONHASHSEED'),
        prefix=sys.prefix, base_prefix=sys.base_prefix, original_source_files=sources, packages=packages,
        arrow_cpu_count=pyarrow.cpu_count(), arrow_io_thread_count=pyarrow.io_thread_count(),
        scientific_environment={key:os.environ.get(key) for key in ('OMP_NUM_THREADS','OPENBLAS_NUM_THREADS','MKL_NUM_THREADS','NUMBA_NUM_THREADS','NUMEXPR_NUM_THREADS','VECLIB_MAXIMUM_THREADS','BLIS_NUM_THREADS')},
        loaded_module_files=modules, runtime_roots=sorted(closure),
        python_flags=dict(no_user_site=sys.flags.no_user_site, dont_write_bytecode=sys.dont_write_bytecode, pycache_prefix=sys.pycache_prefix),
        network_policy='Python audit rejects external socket.connect; local IPC allowed. Not an OS network namespace.')


def validate(request, profile):
    require(set(request) == {'schema_version', 'operation', 'expected_python', 'expected_pyscenic', 'expected_packages', 'inputs', 'options', 'limits', 'ack_timeout_seconds'}, 'Unknown/missing worker fields')
    require(request['schema_version'] == 1 and request['operation'] == 'NETWORK', 'Unknown source workflow schema/operation')
    require(request['expected_python'] == profile['python'] and request['expected_pyscenic'] == '0.12.1' and request['expected_packages'] == profile['expected_packages'], 'Worker request differs from exact packaged source/dependency profile')
    require(type(request['ack_timeout_seconds']) is int and 1 <= request['ack_timeout_seconds'] <= 86400, 'Positive bounded ACK deadline required')
    require(type(request['limits']) is dict and request['limits'] and all(type(v) is int and v > 0 for v in request['limits'].values()), 'Positive integer worker resource limits required')
    options = request['options']
    require(set(options) == {'method', 'seed', 'workers', 'modules', 'ctx'}, 'Unknown/missing fitting options')
    require(options['method'] in ('GRNBOOST2', 'GENIE3') and type(options['seed']) is int and 0 <= options['seed'] <= 0xffffffff, 'Explicit GRN method and uint32 source seed required')
    require(type(options['workers']) is int and 1 <= options['workers'] <= request['limits']['threads'] <= 32, 'Worker population exceeds declared process budget')
    require(set(options['modules']) == {'thresholds', 'top_n_targets', 'top_n_regulators', 'min_genes', 'absolute_thresholds', 'rho_dichotomize', 'keep_only_activating', 'rho_threshold', 'rho_mask_dropouts'}, 'Unknown/missing original coexpression parameters')
    require(set(options['ctx']) == {'rank_threshold', 'auc_threshold', 'nes_threshold', 'motif_similarity_fdr', 'orthologuous_identity_threshold', 'weighted_recovery', 'filter_for_annotation'}, 'Unknown/missing original CTX parameters')
    inputs = request['inputs']
    require(set(inputs) == {'fit_expression', 'tfs', 'annotations', 'databases'} and 1 <= len(inputs['databases']) <= 64, 'Explicit complete fitting inputs and database array required')
    names = [label(v['name']) for v in inputs['databases']]
    require(len(names) == len(set(names)) and all(set(v) == {'name', 'file'} for v in inputs['databases']), 'Unique literal database names required')


def main():
    require(len(sys.argv) == 2, 'Use worker.py NEW-CAPTURE-ROOT')
    root = Path(sys.argv[1]).resolve(); worker = root / 'worker'
    request = read_json(worker / 'request.json'); profile = read_json(Path(__file__).with_name('SOURCE-PROFILE.json'))
    report = dict(status='PREFLIGHT', original_call_started=False, original_call_returned=False, results_admitted=False, warnings=[])
    recorded = []
    try:
        validate(request, profile); sys.addaudithook(network_policy)
        with warnings.catch_warnings(record=True) as recorded:
            warnings.simplefilter('always')
            info = runtime(request, profile); write_json(worker / 'runtime.json', info)
            data = read_expression(request['inputs']['fit_expression'], request['limits'])
            report['input_shape'] = list(data.shape)
            require(not (worker / 'READY').exists() and not (worker / 'ACK').exists(), 'Preexisting handshake marker')
            (worker / 'READY.pending').write_text('SCENIC_PREFLIGHT_V1\n', encoding='ascii')
            (worker / 'READY.pending').replace(worker / 'READY')
            deadline = time.monotonic() + request['ack_timeout_seconds']
            while not (worker / 'ACK').is_file():
                if time.monotonic() > deadline:
                    raise TimeoutError('Java owner did not acknowledge source/input/runtime capture; no fitting started')
                time.sleep(.025)
            require((worker / 'ACK').read_bytes() == b'SCENIC_ACK_V1\n', 'Invalid source/runtime acknowledgement')
            def event(kind, detail):
                if kind == 'CALL':
                    report['original_call_started'] = True
                elif kind == 'RETURNED':
                    report['original_call_returned'] = True
                    report['source_return'] = detail
            report['original_output'] = execute(data, request, worker / 'out', event)
            write_json(worker / 'runtime-after-info.json', runtime(request, profile))
            report['status'] = 'ORIGINAL_RETURNED_PENDING_JAVA_AUDIT'
        code = 0
    except Unavailable as error:
        report.update(status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED', cause=str(error), traceback=traceback.format_exc(),
            required_action='Install/restore the exact named permitted input or private runtime; otherwise retain the skip. Retry NEW output.')
        code = 3
    except Exception as error:
        report.update(status='FAIL2_NO_COMPLETE_RESULT', cause=str(error), traceback=traceback.format_exc(),
            required_action='Retain original/raw/partial/failed/undefined evidence, fix the named error and retry NEW output. No complete result admitted.')
        code = 2
    report['warnings'] = warnings_list(recorded)
    write_json(worker / 'report.json', report)
    print(report['status'] + (': ' + report['cause'] if code else '; Java admission still required'), file=sys.stderr if code else sys.stdout, flush=True)
    return code


if __name__ == '__main__':
    raise SystemExit(main())
