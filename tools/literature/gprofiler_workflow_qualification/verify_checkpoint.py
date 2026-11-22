"""Audit original protocol/populations and optionally execute offline saved controls."""
from pathlib import Path, PurePosixPath
from decimal import Decimal
import copy
import hashlib
import json
import re
import subprocess
import zipfile
from verify_gprofiler_workflow_checkpoint import verify as integrity, pin, require

ROOT = Path(__file__).resolve().parents[3]
FOLDER = 'docs/literature/gprofiler-workflow-qualification'
PAYLOAD = ('organism query sources user_threshold all_results ordered combined measure_underrepresentation '
           'no_iea domain_scope numeric_ns significance_threshold_method background output no_evidences highlight').split()
ECHO = [k for k in PAYLOAD if k not in ['query', 'background', 'output']]
LIMITS = dict(timeout_seconds=45, maximum_request_bytes=8388608, maximum_response_bytes=16777216,
              maximum_identifier_occurrences=200000, request_interval_millis=1000,
              maximum_output_bytes=268435456, maximum_result_rows=200000)


def packet(root=ROOT):
    folder = root / FOLDER
    evidence = json.loads((folder / 'EVIDENCE.json').read_bytes())
    with zipfile.ZipFile(folder / 'evidence.zip') as archive:
        return {n: archive.read('objects/' + value['sha256']) for n, value in evidence['files'].items()}


def load(raw):
    def invalid(value):
        raise ValueError('Nonfinite JSON number: ' + value)
    return json.loads(raw, parse_float=Decimal, parse_constant=invalid)


def whole(value, low, high):
    require(not isinstance(value, bool) and isinstance(value, (int, Decimal)) and
            value == int(value) and low <= value <= high, 'Invalid original population count')
    return int(value)


def probability(value):
    require(not isinstance(value, bool) and isinstance(value, (int, Decimal)) and 0 <= value <= 1,
            'Invalid original probability')
    return value


def audit_bundle(files, base):
    """Independent source population/derived-value checks, without statistical refitting."""
    data = lambda name: files[base + '/' + name]
    read = lambda name: load(data(name))
    request = read('request.json')
    source = read('source-request.json')
    expected = copy.deepcopy(source)
    expected['out'] = '.'
    expected['policies'] = {**dict.fromkeys(['mapping_changes', 'server_normalization', 'empty_results'], 'warn'),
                            **expected.get('policies', {})}
    expected['limits'] = {**LIMITS, **expected.get('limits', {})}
    require(expected == request, 'Portable source request differs')
    payload, service, limits = [request[k] for k in ['payload', 'service', 'limits']]
    metadata_only = request['operation'] == 'metadata'
    require(set(payload) == ({'organism'} if metadata_only else set(PAYLOAD)), 'Changed exact scientific field set')
    archive = service['archive']
    require(archive == '' or re.fullmatch(r'e[0-9]+_eg[0-9]+_p[0-9]+', archive), 'Unexpected archive origin')
    origin = 'https://biit.cs.ut.ee/' + ('gprofiler/' if archive == '' else 'gprofiler_archive3/' + archive + '/')
    execution = read('execution.json')
    require(execution['captured_transport_origin'] == 'live-original-api' and
            execution == dict(mode=execution['mode'], transport_origin='live-original-api' if execution['mode'] == 'RUN' else 'saved-protocol',
                              captured_transport_origin='live-original-api', network_executed=execution['mode'] == 'RUN') and
            execution['mode'] in ['RUN', 'REPLAY'], 'Changed execution/origin provenance')
    for i, endpoint in enumerate(['api/util/data_versions'] + ([] if metadata_only else ['api/gost/profile/']), 1):
        stem = 'wire/%03d' % i
        exchange = read(stem + '-exchange.json')
        require(exchange['url'] == origin + endpoint and exchange['method'] == 'POST', 'Changed original service route')
        require(exchange['state'] == 'COMPLETE' and exchange['failure'] is None and exchange['status'] == 200,
                'Incomplete original response')
        require(exchange['timeout_nanos'] == limits['timeout_seconds'] * 1000000000 and
                exchange['maximum_response_bytes'] == limits['maximum_response_bytes'] and
                exchange['transport_origin'] == execution['captured_transport_origin'], 'Changed saved wire contract')
        expected_body = {'organism': payload['organism']} if i == 1 else payload
        require(read(stem + '-request.body') == expected_body, 'Changed submitted scientific payload')
        require(len(data(stem + '-request.body')) <= limits['maximum_request_bytes'] and
                len(data(stem + '-response.body')) <= limits['maximum_response_bytes'], 'Wire capacity differs')
    metadata = read('metadata.json')
    require(data('metadata.json') == data('wire/001-response.body') and metadata['organism'] == payload['organism'],
            'Changed original metadata/population')
    required_build = service['required_build']
    require((metadata_only and required_build is None) or metadata['gprofiler_version'] == required_build,
            'Exact historical build substituted')
    summary = read('summary.json')
    require(summary['execution'] == execution and summary['observed_build'] == metadata['gprofiler_version'] and
            summary['operation'] == request['operation'] and summary['original_calls'] == (1 if metadata_only else 2) and
            summary['authentication'] is False and summary['independent_statistical_refit'] is False,
            'Derived summary provenance differs')
    status = read('analysis-status.json')
    require(status['status'] == 'COMPLETE_REQUIRES_VALID_COMPLETION_MARKER' and status['warnings'] == summary['warnings'] and
            all(w.encode() in data('WARNINGS.md') for w in summary['warnings']), 'Warnings/status differ')
    counts = dict(reported_terms=0, highlighted_terms=0, original_zero_p_values=0,
                  original_combined_absence_cells=0, mapping_changes_reported=False,
                  custom_background_mapping_unobserved=False)
    if not metadata_only:
        response = read('result.json')
        require(data('result.json') == data('wire/002-response.body') and
                response['meta']['version'] == required_build, 'Changed original result/build')
        rows, meta = response['result'], response['meta']
        echo, genes = meta['query_metadata'], meta['genes_metadata']
        require(echo['organism'] == payload['organism'] and echo == read('query-metadata.json') and
                genes == read('gene-mapping.json'), 'Changed original query or gene mapping')
        require(rows == [load(line) for line in data('results.jsonl').splitlines()] and
                len(rows) <= limits['maximum_result_rows'], 'Changed complete original result rows')
        queries = payload['query'] if isinstance(payload['query'], dict) else {'query_1': payload['query']}
        differences = []
        if queries != echo['queries']:
            differences.append(dict(field='queries', requested=queries, observed=echo['queries']))
        if list(queries) != list(echo['queries']):
            differences.append(dict(field='query_name_order', requested=list(queries), observed=list(echo['queries'])))
        # Preserve the producer's explicit echo comparison order.
        order = ('organism all_results ordered no_iea sources combined numeric_ns domain_scope '
                 'measure_underrepresentation significance_threshold_method user_threshold no_evidences highlight').split()
        for key in order:
            if payload[key] != echo[key]:
                differences.append(dict(field=key, requested=payload[key], observed=echo[key]))
        require(differences == read('normalization.json'), 'Changed recorded server normalization')
        counts.update(reported_terms=len(rows), mapping_changes_reported=bool(genes['failed'] or genes['ambiguous'] or genes['duplicates']),
                      custom_background_mapping_unobserved=payload['domain_scope'].startswith('custom'))
        for row in rows:
            require(all(isinstance(row[k], str) and row[k] for k in ['native', 'source', 'name']) and
                    all(isinstance(v, str) and v for v in row['parents']), 'Changed original term identity')
            domain = whole(row['effective_domain_size'], 0, 2**63 - 1)
            size = whole(row['term_size'], 0, domain)
            if payload['combined']:
                vectors = [row[k] for k in ['p_values', 'query_sizes', 'intersection_sizes']]
                require(all(len(v) == len(echo['queries']) for v in vectors), 'Combined query vector dimensions differ')
                for p, q, hits in zip(*vectors, strict=True):
                    probability(p); whole(q, -1, domain); whole(hits, -1, domain)
                    counts['original_zero_p_values'] += p == 0
                    if q == -1 or hits == -1:
                        require(q == hits == -1 and p == 1, 'Invalid combined absence sentinel')
                        counts['original_combined_absence_cells'] += 1
                    else:
                        require(hits <= min(q, size), 'Combined intersection exceeds population')
            else:
                require(row['query'] in echo['queries'] and isinstance(row['significant'], bool), 'Changed result query/significance')
                q = whole(row['query_size'], 0, domain)
                hits = whole(row['intersection_size'], 0, min(q, size))
                counts['original_zero_p_values'] += probability(row['p_value']) == 0
                for key, denominator in [('precision', q), ('recall', size)]:
                    if denominator:
                        actual = probability(row[key]); expected = Decimal(hits) / denominator
                        require(abs(actual - expected) <= Decimal('1e-12') + Decimal('1e-10') * abs(expected),
                                'Original ratio inconsistent with population')
            if 'highlighted' in row:
                require(isinstance(row['highlighted'], bool), 'Invalid original highlight flag')
                counts['highlighted_terms'] += row['highlighted']
            if row['source'].startswith('GO:') and echo['highlight']:
                require(isinstance(row.get('highlighted'), bool), 'Requested GO drivers missing')
        require(not differences or request['policies']['server_normalization'] == 'warn', 'Normalization policy bypassed')
        require(not counts['mapping_changes_reported'] or request['policies']['mapping_changes'] == 'warn', 'Mapping policy bypassed')
        require(rows or request['policies']['empty_results'] == 'warn', 'Empty result policy bypassed')
    require(all(summary[k] == v for k, v in counts.items()), 'Derived original result counts differ')
    require(summary['cookie_response_headers_recorded'] and any('Set-Cookie' in w for w in summary['warnings']),
            'Observed cookie privacy warning missing')
    return dict(mode=execution['mode'], operation=request['operation'], terms=counts['reported_terms'])


def verify(jar=None, root=ROOT, runtime_out=None, java=None):
    result = integrity(root, jar)
    files = packet(root)
    checks = json.loads(files['publication-audit/RESULT.json'])['checks']
    qualified = []
    producers = set()
    for check in checks:
        if check['exit_code'] != 0:
            continue
        base = 'publication/' + check['root']
        qualified.append(dict(root=check['root'], **audit_bundle(files, base)))
        producers.add(json.dumps(json.loads(files[base + '/producer.json'])['selected_class_sha256'], sort_keys=True))
    require(len(qualified) == 21 and len(producers) == 1, 'Changed valid capture producer/operation census')
    classes = json.loads(producers.pop())
    if jar is not None:
        with zipfile.ZipFile(jar) as archive:
            for name, sha in classes.items():
                require(hashlib.sha256(archive.read(name.replace('.', '/') + '.class')).hexdigest() == sha,
                        'Actual producer class differs')
    actual = []
    if runtime_out is not None:
        require(jar is not None and java is not None, 'Actual offline checks require exact JAR and Java')
        runtime_out.mkdir(parents=True, exist_ok=False)
        # Restore declared empty directories as well as closed file inventories.
        for check in checks:
            base = 'publication/' + check['root']
            destination = runtime_out / 'captures' / check['root']
            for name, raw in files.items():
                if name.startswith(base + '/'):
                    part = PurePosixPath(name[len(base) + 1:])
                    require(not part.is_absolute() and '..' not in part.parts, 'Unsafe saved capture path')
                    target = destination / part
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(raw)
            manifest = json.loads(files[base + '/manifest.json'])
            for part in manifest.get('directories', []):
                require(not PurePosixPath(part).is_absolute() and '..' not in PurePosixPath(part).parts and '\\' not in part,
                        'Unsafe saved directory')
                (destination / part).mkdir(parents=True, exist_ok=True)
            before = {p.relative_to(destination).as_posix(): pin(p.read_bytes()) for p in destination.rglob('*') if p.is_file()}
            tag = hashlib.sha256(check['root'].encode()).hexdigest()[:16]
            with (runtime_out / (tag + '.stdout')).open('xb') as out, (runtime_out / (tag + '.stderr')).open('xb') as err:
                command = [str(java), '-Xmx768m', '-Dhttps.proxyHost=127.0.0.1', '-Dhttps.proxyPort=9',
                           '-Dhttp.proxyHost=127.0.0.1', '-Dhttp.proxyPort=9', '-jar', str(jar),
                           'gprofiler-tools', '--verify', str(destination)]
                process = subprocess.Popen(command, cwd=root, stdin=subprocess.DEVNULL, stdout=out, stderr=err)
                code = process.wait()
            require(code == check['expected'], 'Actual saved CLI outcome differs: ' + check['root'])
            require(before == {p.relative_to(destination).as_posix(): pin(p.read_bytes()) for p in destination.rglob('*') if p.is_file()},
                    'Saved verifier mutated capture')
            actual.append(dict(root=check['root'], exit_code=code, expected=check['expected'], log_prefix=tag))
        (runtime_out / 'RESULT.json').write_text(json.dumps(actual, indent=2) + '\n', encoding='utf-8')
    result.update(status='PASS_GPROFILER_PROTOCOL_POPULATION_AND_CHECKPOINT_AUDIT',
                  protocol_audited_bundles=qualified, selected_class_fingerprints=len(classes),
                  original_statistics_refit=False, actual_offline_CLI_checks=actual,
                  actual_runtime_executed=runtime_out is not None, new_family_credit=0)
    return result
