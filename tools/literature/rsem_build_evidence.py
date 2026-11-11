"""Verify retained source-build links and complete fixed synthetic populations.

This verifies evidence integrity and scope, not an admission ticket, signature,
OS sandbox or whole-tool/biological equivalence.
"""
from contextlib import contextmanager
import hashlib
import io
import json
from pathlib import PurePosixPath
import re
import zipfile

INPUTS = '742c4d5feb9118d5bed6b9c6e2b6f5ae89b7924f083be490823f6045fe123338'
LOCK = 'c7fcb6272bb0ee9f34207d4e221e68c017af1e6174b9492310ada68c1aa55fb7'
VERSIONS = {'1.2.28', '1.3.0', '1.3.1', '1.3.2', '1.3.3'}
PROCESS = {'process.stdout', 'process.stderr', 'process.json', 'launch.json', 'WARNINGS.md'}


@contextmanager
def open_evidence(path, expected_sha256):
    if path.stat().st_size > 134217728:
        raise ValueError('RSEM control archive exceeds 128 MiB')
    with path.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != expected_sha256:
            raise ValueError('RSEM control archive checksum differs')
        stream.seek(0)
        with zipfile.ZipFile(stream) as archive:
            entries = archive.infolist()
            names = {entry.filename for entry in entries}
            if len(names) != len(entries) or 'index.json' not in names or archive.getinfo('index.json').file_size > 67108864:
                raise ValueError('Repeated, missing or oversized RSEM evidence index')
            index = json.loads(archive.read('index.json'))
            if index['schema'] != 'omicsmatic-rsem-source-build-controls-v1':
                raise ValueError('Unsupported RSEM control archive schema')
            logical = index['logical_files']
            if not isinstance(logical, dict) or not 1 <= len(logical) <= 50000:
                raise ValueError('RSEM logical evidence population exceeds capacity')
            objects = {}
            for name, row in logical.items():
                if not name or name.startswith('/') or '\\' in name or any(x in ('', '.', '..') for x in name.split('/')):
                    raise ValueError('Invalid logical RSEM evidence path')
                sha, size = row['sha256'], row['bytes']
                if not re.fullmatch('[0-9a-f]{64}', sha) or not isinstance(size, int) or not 0 <= size <= 268435456:
                    raise ValueError('Invalid RSEM evidence object identity/capacity')
                if sha in objects and objects[sha] != size:
                    raise ValueError('Inconsistent shared RSEM evidence object size')
                objects[sha] = size
            if names != {'index.json'} | {'objects/' + sha for sha in objects} or sum(objects.values()) > 1073741824:
                raise ValueError('RSEM evidence archive has extra/missing objects or exceeds capacity')
            for sha, size in objects.items():
                name = 'objects/' + sha
                if archive.getinfo(name).file_size != size:
                    raise ValueError('RSEM evidence object length differs')
                with archive.open(name) as member:
                    if hashlib.file_digest(member, 'sha256').hexdigest() != sha:
                        raise ValueError('RSEM evidence object content differs')

            def read(name):
                row = logical[name]
                return archive.read('objects/' + row['sha256'])

            yield index, read


def verify_source_build_bundle(root, path, expected_sha256, candidate_sha256):
    fixture = (root / 'src/main/resources/org/jlinalg/omics/rsem-health/inputs.zip').read_bytes()
    lock_raw = (root / 'src/main/resources/org/jlinalg/omics/rsem-source-profiles.json').read_bytes()
    assert hashlib.sha256(fixture).hexdigest() == INPUTS and hashlib.sha256(lock_raw).hexdigest() == LOCK
    profiles = {row['version']: row for row in json.loads(lock_raw)['profiles']}
    with zipfile.ZipFile(io.BytesIO(fixture)) as inputs:
        golden = json.loads(inputs.read('expected-science.json'))
    with open_evidence(path, expected_sha256) as (index, read):
        assert index['candidate_sha256'] == candidate_sha256 and index['original_input_fixture_sha256'] == INPUTS
        summary = json.loads(read('matrix/observed.json'))
        assert summary['status'] == 'passed-five-exact-source-builds-and-bound-original-controls'
        assert set(summary['versions']) == VERSIONS and summary['candidate_sha256'] == candidate_sha256
        logical, private = index['logical_files'], index['private_artifacts']
        successes = failures = scientific = references = 0
        for version in sorted(VERSIONS):
            b, r = 'build/' + version + '/', 'runtime/' + version + '/'
            row = summary['versions'][version]
            assert row['exit_code'] == 0
            build_raw = read(b + 'build.json')
            build = json.loads(build_raw)
            receipt_sha = hashlib.sha256(build_raw).hexdigest()
            runtime = json.loads(read(r + 'observed.json'))
            assert receipt_sha == row['build_receipt_sha256'] == runtime['source_build_receipt_sha256']
            assert build['status'] == 'COMPLETED_EXACT_ORIGINAL_SOURCE_BUILD' and build['source_profile_lock_sha256'] == LOCK
            assert build['profile']['version'] == version and build['profile']['archiveSha256'] == profiles[version]['source_archive_sha256']
            assert build['compiler_prefix'] == build['compiler_prefix_after']
            for name, sha in build['evidence'].items():
                assert (private if b + name in private else logical)[b + name]['sha256'] == sha
            for name in ['compiler-prefix-before.jsonl', 'compiler-prefix-after.jsonl']:
                assert logical[b + 'metadata/' + name]['sha256'] == build['compiler_prefix']['sha256']
            assert logical[b + 'metadata/built-source.jsonl']['sha256'] == build['source_graph']['sha256']
            for phase in build['phases']:
                assert phase['exit'] == 0 and phase['owned_live_after_cleanup'] == []
                relative = PurePosixPath(phase['ledger']).relative_to(PurePosixPath(row['build_home'])).as_posix()
                command = json.loads(read(b + relative))
                assert command['status'] == 'completed-process-only' and command['exit_code'] == 0 and command['owned_live_after_cleanup'] == []
            binding = json.loads(read(r + 'binding/runtime-binding.json'))
            payload = json.loads(read(r + 'payload/payload.json'))
            for record in [build, runtime, binding, payload]:
                assert record['source_to_build_provenance_verified'] is True and record['scientific_runtime_admitted'] is False
            assert runtime['candidate_sha256'] == candidate_sha256 and not runtime['public_fitting_workflow_available']
            assert binding['source_build_receipt_sha256'] == payload['source_build_receipt_sha256'] == receipt_sha
            graph = {item['path']: item for item in map(json.loads, read(b + 'metadata/built-source.jsonl').splitlines()) if item.get('kind') == 'FILE'}
            assert len(payload['files']) == 14
            for name, pin in payload['files'].items():
                source_path = row['build_home'] + '/outputs/omicsmatic-rsem-decode' if name == 'bin/omicsmatic-rsem-decode' else payload['original_build_home'] + '/' + name.removeprefix('bin/')
                assert graph[source_path]['sha256'] == pin['sha256'] and graph[source_path]['bytes'] == pin['bytes']
            assert runtime['negative_dependency_controls'] == 6 and runtime['all_positive_contexts_currently_reverified']
            for key in ['undeclared_provider_refused_before_launch', 'changed_input_refused_before_launch', 'new_library_shadow_refused_before_launch', 'changed_payload_refused', 'changed_build_receipt_refused_before_launch', 'changed_build_evidence_refused_before_launch']:
                assert runtime[key] is True
            outcomes = {item['case']: item for item in runtime['outcomes']}
            assert set(outcomes) == {'paired-em', 'reverse-em', 'single-em', 'paired-posterior', 'noise'}
            for stage in ['prepare', *sorted(outcomes)]:
                prefix = r + stage + '/'
                actual = {n.removeprefix(prefix): pin['sha256'] for n, pin in logical.items() if n.startswith(prefix) and n.removeprefix(prefix) not in PROCESS}
                expected = golden['references'][version] if stage == 'prepare' else golden['cases'].get(version + '/' + stage)
                if expected is None:
                    assert outcomes[stage]['status'] == 'retained-expected-original-failure' and outcomes[stage]['exit_code'] != 0
                    failures += 1
                else:
                    assert actual == expected, version + '/' + stage
                    if stage == 'prepare':
                        references += len(actual)
                    else:
                        assert outcomes[stage]['status'] == 'passed-exact-original-scientific-population-and-native-audit'
                        successes += 1
                        scientific += len(actual)
            assert runtime['complete_scientific_files'] == sum(len(golden['cases'].get(version + '/' + name, {})) for name in outcomes)
        assert (successes, failures, scientific, references) == (22, 3, 364, 30)
        return dict(completed_source_builds=5, completed_original_fits=successes, retained_expected_original_failures=failures,
                    complete_scientific_files=scientific, complete_reference_files=references, negative_dependency_controls=30,
                    build_to_staged_payload_links_verified=True)
