#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify retained complete MAFFT workflows and current source; launches no aligner."""
from contextlib import contextmanager
from functools import lru_cache
import hashlib
import io
import json
import lzma
from pathlib import Path, PurePosixPath
import re
import stat
import struct
import sys
import tempfile
import zipfile
from catalog_source_integrity import verify_source

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'docs/literature/omics-2023-2026'
VERIFICATION_SOURCES = {'tools/literature/mafft_workflow_validation.py', 'tools/literature/test_mafft_workflow_validation.py', 'tools/literature/mafft_catalog.py'}
VERSIONS = {'7.313', '7.475', '7.487', '7.490', '7.505', '7.526'}
RUNTIME = 'src/main/java/org/jlinalg/cli/MafftRuntime.java'
OLD_MESSAGE = b'MAFFT private runtime setup only; public user analysis remains unfinished.'
NEW_MESSAGE = b'MAFFT private runtime setup only. Use current-source mafft-tools for separately admitted user-data alignment; broader capabilities remain unfinished.'
SCIENCE = {p.relative_to(ROOT).as_posix() for directory, pattern in [
    ('src/main/java/org/jlinalg/cli', 'Mafft*.java'), ('src/main/java/org/jlinalg/sequence', 'Mafft*.java'),
    ('src/test/java/org/jlinalg/cli', 'Mafft*Test.java'), ('src/test/java/org/jlinalg/sequence', 'Mafft*Test.java')]
    for p in (ROOT / directory).glob(pattern)}
SCIENCE |= {'src/main/java/org/jlinalg/cli/' + name + '.java' for name in [
    'JLinAlgCli', 'SimpleJson', 'IqtreeFiles', 'RegenieRuntime', 'RsemHost', 'RsemElf', 'RsemProcess',
    'RsemProvider', 'RsemJson', 'RsemPayload', 'RepeatHostFiles', 'RepeatBundle', 'RepeatInputs']}
SCIENCE |= {'tools/literature/install_mafft.py', 'tools/literature/mafft-runtime-lock.json',
            'tools/literature/collect_mafft_workflow_controls.py', 'tools/literature/curate_mafft_uses.py',
            'tools/literature/mafft-use-reviews.json',
            'src/test/resources/org/jlinalg/sequence/mafft-original/original-controls.zip',
            'src/test/resources/org/jlinalg/cli/mafft-workflow/initial-controls.zip'}
SCIENCE |= {'src/main/resources/org/jlinalg/omics/mafft-runtime-lock.json'}
SCIENCE |= {p.relative_to(ROOT).as_posix() for p in (ROOT / 'src/main/resources/org/jlinalg/omics/mafft-runtime-setup').rglob('*') if p.is_file() and '__pycache__' not in p.parts}
sha = lambda raw: hashlib.sha256(raw).hexdigest()


def safe_path(name):
    path = PurePosixPath(name)
    assert name and name != '.' and not path.is_absolute() and '..' not in path.parts and '.' not in path.parts
    assert path.as_posix() == name and '\\' not in name and not re.match(r'^[A-Za-z]:', name), name


def json_object(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            assert key not in result, 'Duplicate JSON key'
            result[key] = value
        return result
    return json.loads(raw.decode('utf-8'), object_pairs_hook=pairs)


def patch_setup_class(raw):
    """Replace one UTF8 constant; every class header, instruction and other byte stays exact."""
    assert raw[:4] == b'\xca\xfe\xba\xbe'
    count = struct.unpack_from('>H', raw, 8)[0]
    offset, index, replacement = 10, 1, None
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4,
             12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = raw[offset]; offset += 1
        if tag == 1:
            length = struct.unpack_from('>H', raw, offset)[0]
            body = raw[offset + 2:offset + 2 + length]
            assert len(body) == length
            if OLD_MESSAGE in body:
                assert replacement is None and body.count(OLD_MESSAGE) == 1
                updated = body.replace(OLD_MESSAGE, NEW_MESSAGE)
                replacement = (offset, offset + 2 + length, struct.pack('>H', len(updated)) + updated)
            offset += 2 + length
        else:
            assert tag in sizes, 'Unknown class constant tag'
            offset += sizes[tag]
            if tag in {5, 6}: index += 1
        index += 1
    assert replacement is not None
    start, end, updated = replacement
    return raw[:start] + updated + raw[end:]


def verify_setup_source(old, current, proof):
    assert proof['status'] == 'passed-generated-setup-instruction-literal-only-source-delta'
    assert proof['path'] == RUNTIME and proof['before_sha256'] == sha(old) and proof['after_sha256'] == sha(current)
    assert proof['before_literal'].encode() == OLD_MESSAGE and proof['after_literal'].encode() == NEW_MESSAGE
    assert old.count(OLD_MESSAGE) == current.count(NEW_MESSAGE) == 1
    assert current.replace(NEW_MESSAGE, OLD_MESSAGE) == old, 'A scientific/unreviewed source edit cannot use a setup-message proof'


class Evidence:
    def __init__(self, bundle):
        self.zip = bundle
        infos = bundle.infolist(); names = [item.filename for item in infos]
        assert len(names) == len(set(names)) and len(names) <= 60000
        for item in infos:
            safe_path(item.filename)
            assert not item.is_dir() and stat.S_IFMT(item.external_attr >> 16) in {0, stat.S_IFREG}
            assert item.file_size <= 134217728
        self.record = json_object(bundle.read('record.json'))
        self.objects = self.record['objects']
        assert set(names) == {'record.json', 'README.txt'} | {'objects/' + key for key in self.objects}
        for key, size in self.objects.items():
            assert re.fullmatch(r'[a-f0-9]{64}', key) and 0 <= size <= 67108864
            value = bundle.read('objects/' + key)
            assert len(value) == size and sha(value) == key, 'Object content/hash mismatch'
    @lru_cache(maxsize=96)
    def content(self, key):
        return self.zip.read('objects/' + key)
    def raw(self, pin):
        assert self.objects[pin['sha256']] == pin['bytes']
        return self.content(pin['sha256'])
    def obj(self, files, path):
        return json_object(self.raw(files[path]))


@contextmanager
def open_evidence(path, pin):
    assert path.stat().st_size == pin['bytes'] and sha(path.read_bytes()) == pin['sha256']
    # Seekable temporary storage avoids retaining the expanded ZIP in RAM.
    with tempfile.TemporaryFile() as temporary:
        total = 0
        with lzma.open(path, 'rb') as compressed:
            while True:
                block = compressed.read(1048576)
                if not block: break
                total += len(block); assert total <= 1073741824, 'Expanded archive capacity exceeded'
                temporary.write(block)
        assert total == pin['inner_bytes']
        temporary.seek(0)
        with zipfile.ZipFile(temporary) as bundle:
            evidence = Evidence(bundle)
            try: yield evidence
            finally: evidence.content.cache_clear()


def fasta(raw, policy='WHOLE_HEADER'):
    rows = []; header = None; sequence = []
    for line in raw.decode('utf-8').splitlines():
        if line.startswith('>'):
            if header is not None: rows.append((header, ''.join(sequence)))
            header, sequence = line[1:], []
        elif line.strip():
            assert header is not None
            sequence.append(line.replace(' ', '').replace('\t', ''))
    if header is not None: rows.append((header, ''.join(sequence)))
    result = {}
    for header, sequence in rows:
        identity = re.split(r'[ \t]', header, maxsplit=1)[0] if policy == 'FIRST_FIELD' else header
        assert identity and sequence and identity not in result
        result[identity] = (header, sequence)
    return result


def verify_matrix(evidence):
    captured = evidence.record
    assert captured['schema_version'] == 1 and captured['format'] == 'complete-file-families-content-addressed-v1'
    assert captured['status'] == 'passed-complete-synthetic-workflow-controls'
    assert captured['bytes_are_original_and_losslessly_referenced'] is True
    for field in ['upstream_executables_redistributed', 'biological_accuracy_certified', 'whole_paper_reproduced']: assert captured[field] is False
    protocols = captured['protocols']; assert len(protocols) == 57 and len({p['name'] for p in protocols}) == 57
    assert set(captured['bundles']) == {p['name'] for p in protocols}
    outcomes = {0: 0, 2: 0, 3: 0}; residues = deleted = golden = source_fits = logical = 0
    versions = set(); populations = set()
    original = zipfile.ZipFile(io.BytesIO(evidence.raw(captured['source_snapshot']['src/test/resources/org/jlinalg/sequence/mafft-original/original-controls.zip'])))
    try:
        for protocol in protocols:
            name, code = protocol['name'], protocol['exit_code']; assert code == protocol['expected'] and code in outcomes
            outcomes[code] += 1
            family = captured['bundles'][name]; files, dirs = family['files'], family['directories']
            assert len(dirs) == len(set(dirs)) and '' in dirs; logical += len(files)
            for path, pin in files.items(): safe_path(path); assert evidence.objects[pin['sha256']] == pin['bytes']
            for path in dirs:
                if path: safe_path(path)
            session = evidence.obj(files, 'session.json'); assert session['scientific_result_admitted'] is False
            assert b'Required action' in evidence.raw(files['WARNINGS.md'])
            if 'process/process.json' in files:
                process = evidence.obj(files, 'process/process.json'); assert process['owned_live_after_cleanup'] == []
                source_fits += process['status'] == 'completed-process-only'
            if code:
                assert 'COMPLETED.json' not in files
                if protocol['controlled_fault_injected']:
                    assert name == 'controlled-finalization-failure' and 'FINALIZATION_FAILED.md' in files and 'COMPLETION-PROVISIONAL.json' in dirs
                else:
                    assert session['exit_code'] == code and session['status'].startswith('SKIP3' if code == 3 else 'FAIL2')
                continue
            completion = evidence.obj(files, 'COMPLETED.json'); manifest = evidence.obj(files, 'manifest.json')
            assert completion['schema_version'] == manifest['schema_version'] == 1
            assert completion['tool'] == manifest['tool'] == 'MAFFT' and completion['scope'] == manifest['scope'] == 'complete-alignment'
            assert completion['status'] == 'completed-audited-alignment' and manifest['status'] == session['status'] == 'audited-provisional-awaiting-finalization'
            assert session['exit_code'] == 0
            assert completion['manifest_sha256'] == files['manifest.json']['sha256'] and completion['scientific_result_admitted'] is True and completion['biological_accuracy_certified'] is False
            assert manifest['files'] == {p: v for p, v in files.items() if p not in {'COMPLETED.json', 'manifest.json'}}
            assert set(manifest['directories']) == set(dirs) and len(manifest['directories']) == len(dirs)
            request = evidence.obj(files, 'recipe-request.json'); versions.add(request['profile'].removeprefix('linux-x86_64-mafft-'))
            inputs = fasta(evidence.raw(files['inputs/input.original.fasta']), request['headers']); addition_sha = None
            if request['operation'] == 'ADD':
                added = fasta(evidence.raw(files['inputs/additions.original.fasta']), request['headers'])
                assert not set(inputs) & set(added); inputs.update(added); addition_sha = files['inputs/additions.original.fasta']['sha256']
            populations.add((files['inputs/input.original.fasta']['sha256'], addition_sha))
            mapping = evidence.obj(files, 'taxon-map.json'); identities = {r['original_taxon']: r['opaque_taxon'] for r in mapping}
            assert len(mapping) == len(inputs) == len(identities) == len(set(identities.values())) and set(inputs) == set(identities)
            source = fasta(evidence.raw(files['process/process.stdout'])); restored = fasta(evidence.raw(files['alignment.fasta']), request['headers'])
            assert set(source) == set(identities.values()) and set(restored) == set(inputs)
            assert list(restored) == [next(k for k, v in identities.items() if v == identifier) for identifier in source]
            if request['parameters']['output_order'] == 'INPUT': assert list(restored) == list(inputs)
            widths = {len(s) for h, s in source.values()}; assert len(widths) == 1; width = next(iter(widths))
            for identifier, (header, sequence) in inputs.items():
                assert restored[identifier][0] == header and restored[identifier][1].upper() == source[identities[identifier]][1].upper()
            occupied = set(); seen = set(); removed = changes = 0
            lines = evidence.raw(files['residue-map.tsv']).decode().splitlines()
            assert lines[0] == 'original_taxon_json\topaque_taxon\toriginal_ungapped_position\toriginal_character_column\toriginal_character_codepoint\tsource_character_codepoint\taligned_column_or_minus1\tstate'
            for line in lines[1:]:
                fields = line.split('\t'); assert len(fields) == 8
                identifier, opaque = json.loads(fields[0]), fields[1]
                position, column, before, printed, target = map(int, fields[2:7]); state = fields[7]
                assert identities[identifier] == opaque
                sequence = inputs[identifier][1]; coordinates = [i + 1 for i, c in enumerate(sequence) if c != '-']
                assert 1 <= position <= len(coordinates) and column == coordinates[position - 1] and before == ord(sequence[column - 1])
                assert (identifier, position) not in seen; seen.add((identifier, position)); assert chr(before).upper() == chr(printed).upper()
                if target == -1:
                    assert state == 'SOURCE_DELETED_INSERTION' and request['parameters']['keep_length']; removed += 1
                else:
                    assert state == 'RETAINED' and 1 <= target <= width and (opaque, target) not in occupied
                    occupied.add((opaque, target)); assert ord(source[opaque][1][target - 1]) == printed and ord(restored[identifier][1][target - 1]) == before
                    changes += before != printed
            assert seen == {(k, i + 1) for k, (h, s) in inputs.items() for i in range(sum(c != '-' for c in s))}
            assert occupied == {(identifier, column + 1) for identifier, (h, s) in source.items() for column, c in enumerate(s) if c != '-'}
            audit = evidence.obj(files, 'output-audit.json'); assert audit['biological_alignment_accuracy_certified'] is False
            assert audit['status'] == 'complete-original-alignment-and-residue-audit' and audit['all_rows_and_output_cells_checked'] is True and audit['original_headers_and_retained_residue_case_restored'] is True
            process = evidence.obj(files, 'process/process.json')
            assert process['status'] == 'completed-process-only' and process['exit_code'] == 0 and process['accepted_process_exit_codes'] == [0]
            result = audit['result']
            for field, expected in dict(taxa=len(inputs), columns=width, originalResidues=len(seen), deliberatelyDeletedResidues=removed, retainedResidues=len(occupied), residueLedgerRows=len(seen), sourceCaseChanges=changes).items(): assert result[field] == expected, (name, field)
            residues += len(seen); deleted += removed
            if protocol['golden_comparisons']:
                role = 'AA-add-keep-map' if name.endswith('-AA-keep-map') else 'AA-local-1000' if name.endswith('-AA-local1000') else 'DNA-auto'
                prefix = 'original/' + request['profile'].removeprefix('linux-x86_64-mafft-') + '/' + role + '/'
                assert evidence.raw(files['process/process.stdout']) == original.read(prefix + 'process.stdout'); golden += 1
                if role == 'AA-add-keep-map': assert evidence.raw(files['staged/added.fasta.map']) == original.read(prefix + 'added.fasta.map'); golden += 1
    finally:
        original.close()
    assert (outcomes, versions, residues, deleted, golden, source_fits, len(populations), logical) == ({0: 35, 2: 9, 3: 13}, VERSIONS, 1678, 37, 24, 36, 9, 77687)
    return dict(protocols=57, completed_alignments=35, expected_skips=13, expected_failures=9, completed_original_processes=36, original_residue_cells=residues, deliberate_deletions=deleted, original_byte_comparisons=golden, input_byte_populations=9, logical_files=logical)


def verify_current_case(raw):
    """Check the additional final-JAR de novo bundle without its original host."""
    files = {}; dirs = {''}; total = 0
    with zipfile.ZipFile(io.BytesIO(raw)) as bundle:
        for item in bundle.infolist():
            name = item.filename.rstrip('/') if item.is_dir() else item.filename
            safe_path(name); total += item.file_size
            assert total <= 134217728 and item.file_size <= 67108864
            if item.is_dir(): assert name not in dirs; dirs.add(name)
            else:
                assert name not in files and stat.S_IFMT(item.external_attr >> 16) in {0, stat.S_IFREG}
                files[name] = bundle.read(item)
    completed = json_object(files['COMPLETED.json']); manifest = json_object(files['manifest.json'])
    assert completed['status'] == 'completed-audited-alignment' and completed['manifest_sha256'] == sha(files['manifest.json'])
    assert completed['scientific_result_admitted'] is True and completed['biological_accuracy_certified'] is False
    assert set(manifest['directories']) == dirs and len(manifest['directories']) == len(dirs)
    assert set(manifest['files']) == set(files) - {'COMPLETED.json', 'manifest.json'}
    for name, pin in manifest['files'].items(): assert len(files[name]) == pin['bytes'] and sha(files[name]) == pin['sha256']
    process = json_object(files['process/process.json']); assert process['exit_code'] == 0 and process['owned_live_after_cleanup'] == []
    request = json_object(files['recipe-request.json']); assert request['profile'] == 'linux-x86_64-mafft-7.526' and request['operation'] == 'DE_NOVO'
    inputs = fasta(files['inputs/input.original.fasta']); restored = fasta(files['alignment.fasta'])
    assert list(restored) == list(inputs) and len(inputs) == 3
    for identifier, (header, sequence) in inputs.items(): assert restored[identifier][0] == header and restored[identifier][1].replace('-', '') == sequence.replace('-', '')
    audit = json_object(files['output-audit.json']); assert audit['biological_alignment_accuracy_certified'] is False
    assert audit['result']['originalResidues'] == audit['result']['residueLedgerRows'] == 48 and audit['result']['deliberatelyDeletedResidues'] == 0
    return dict(additional_current_candidate_alignment=1, current_candidate_original_residues=48)


def verify(root=ROOT, data=DATA, emit=True):
    if not __debug__: raise RuntimeError('Run validation with assertions enabled, never Python -O')
    root, data = Path(root), Path(data)
    proof = json_object((data / 'mafft-workflow-validation.json').read_bytes())
    assert proof['status'] == 'passed-scoped-MAFFT-public-workflow'
    assert set(proof['scientific_sources']) == SCIENCE
    assert set(proof['verification_sources']) == VERIFICATION_SOURCES
    for field in ['whole_paper_reproduced', 'biological_accuracy_certified', 'unresolved_paper_settings_inferred', 'released_or_deployed']: assert proof[field] is False
    assert proof['artifact']['path'] == 'mafft-workflow-controls.zip.xz'
    with open_evidence(data / proof['artifact']['path'], proof['artifact']) as evidence:
        summary = verify_matrix(evidence)
        captured = evidence.record['source_snapshot']
        locked = evidence.raw(captured['tools/literature/mafft-runtime-lock.json'])
        assert (root / 'src/main/resources/org/jlinalg/omics/mafft-runtime-lock.json').read_bytes() == locked
        assert (root / 'src/main/resources/org/jlinalg/omics/mafft-runtime-setup/install_mafft.py').read_bytes() == evidence.raw(captured['tools/literature/install_mafft.py'])
        for name, pin in json_object(locked)['bundled'].items():
            asset = (root / 'src/main/resources/org/jlinalg/omics/mafft-runtime-setup/mafft-runtime' / name).read_bytes()
            assert len(asset) == pin['bytes'] and sha(asset) == pin['sha256']
        with zipfile.ZipFile(data / 'mafft-validation-supplement.zip') as supplement:
            assert sha((data / 'mafft-validation-supplement.zip').read_bytes()) == proof['supplement_sha256']
            for path in SCIENCE:
                digest = proof['scientific_sources'][path]; current = (root / path).read_bytes()
                assert sha(supplement.read('source/' + path)) == digest
                if path == 'src/main/java/org/jlinalg/cli/JLinAlgCli.java': verify_source(root, data, path, digest)
                else: assert sha(current) == digest, path
                if path in captured:
                    original = evidence.raw(captured[path])
                    if path == RUNTIME and original != current:
                        verify_setup_source(original, current, proof['setup_message_delta'])
                        old_class = evidence.raw(evidence.record['owned_packaged_classes']['org/jlinalg/cli/MafftRuntime.class'])
                        assert patch_setup_class(old_class) == supplement.read('current-classes/org/jlinalg/cli/MafftRuntime.class')
                    elif path == 'src/main/java/org/jlinalg/cli/JLinAlgCli.java': verify_source(root, data, path, sha(original))
                    else: assert original == current, 'Current scientific source differs from the recorded workflow: ' + path
            for path, digest in proof['verification_sources'].items():
                assert sha(supplement.read('source/' + path)) == sha((root / path).read_bytes()) == digest, path
            native = json_object(supplement.read('validation/packaged-native.json'))
            assert native['status'] == 'passed' and native['tests_per_JVM'] == 180
            assert native['windows21_passed'] == native['windows25_passed'] == 159 and native['windows_platform_aborts'] == 21
            assert native['linux21_passed'] == 179 and native['linux_platform_aborts'] == 1
            assert native['candidate_sha256'] == proof['packaged_candidate_sha256']
            current = verify_current_case(supplement.read('validation/current-candidate-case.zip'))
            assert proof['additional_current_candidate_alignment'] == current['additional_current_candidate_alignment'] == 1
    if emit: print(json.dumps(dict(status='passed', **summary, exact_editions=sorted(VERSIONS), scope='Complete retained source/file families, every original residue ledger cell and current scientific-source bindings. No aligner execution, download, biological-accuracy or whole-paper certification.'), indent=2))
    return summary


if __name__ == '__main__':
    try: verify()
    except Exception as failure:
        print('MAFFT EVIDENCE FAIL2: ' + str(failure) + '\nRequired action: preserve the evidence, correct the named source/archive mismatch and repeat validation. Do not promote the tool or admit a partial result.', file=sys.stderr)
        sys.exit(2)
