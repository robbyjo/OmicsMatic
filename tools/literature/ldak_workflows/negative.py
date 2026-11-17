#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise captured LDAK integrity and native-audit refusals on independent copies."""
from pathlib import Path
import argparse
import base64
import hashlib
import json
import os
import shutil
import struct
import subprocess
import sys
import time
import traceback
import zipfile

sys.dont_write_bytecode = True
if not __debug__:
    raise RuntimeError('Qualification assertions must remain enabled; rerun Python without -O')


def pin(path):
    with Path(path).open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    return dict(bytes=Path(path).stat().st_size, sha256=digest)


def inventory(root):
    result = {}
    for path in sorted(Path(root).rglob('*')):
        if path.is_symlink() or not (path.is_file() or path.is_dir()):
            raise ValueError('Linked/special evidence member: '+str(path))
        name = path.relative_to(root).as_posix()
        if path.is_dir():
            result[name+'/'] = dict(bytes=0, sha256='directory')
        else:
            result[name] = pin(path)
    return result


def simple_properties(path):
    result = {}
    for line in Path(path).read_text(encoding='ascii').splitlines():
        if not line or line.startswith(('#', '!')):
            continue
        key, separator, value = line.partition('=')
        if not separator or '\\' in key or key in result:
            raise ValueError('Not the expected simple generated properties: '+str(path))
        result[key] = value
    return result


def write_properties(path, values):
    def escape(value):
        result = []
        raw = str(value).encode('utf-16-be')
        for index in range(0, len(raw), 2):
            unit = int.from_bytes(raw[index:index+2], 'big')
            char = chr(unit)
            result.append('\\'+char if char in '\\ :=#!' else '\\u'+format(unit, '04x') if unit < 32 or unit > 126 else char)
        return ''.join(result)
    Path(path).write_text('# CONTRIVED NEGATIVE COPY; never a qualified result\n'+''.join(key+'='+escape(value)+'\n' for key, value in sorted(values.items())), encoding='ascii', newline='\n')


def encoded_pins(members):
    result = {}
    for name, identity in members.items():
        key = base64.urlsafe_b64encode(name.encode()).rstrip(b'=').decode()
        result['bytes.'+key] = str(identity['bytes'])
        result['sha256.'+key] = identity['sha256']
    return result


def contrived_reseal(bundle):
    """Rehash ONLY a newly copied negative fixture to exercise checks beyond integrity.

    No source bundle, saved reader, implementation fingerprint or accepted result
    is edited. These explicitly labelled copies must fail native/process checks.
    """
    assert bundle.name == 'bundle' and bundle.parent.name.startswith('negative-')
    science = {}
    for name, identity in inventory(bundle).items():
        basename = Path(name).name
        if not name.endswith('/') and ((name.startswith('stages/') and basename.startswith('result.') and not basename.endswith('.progress')) or name.startswith('native/') or name in ['summary.tsv', 'DIAGNOSTICS.tsv']):
            science[name] = identity
    write_properties(bundle/'scientific.properties', encoded_pins(science))
    members = inventory(bundle)
    for name in ['manifest.properties', 'COMPLETED.properties']:
        members.pop(name)
    write_properties(bundle/'manifest.properties', encoded_pins(members))
    completed = simple_properties(bundle/'COMPLETED.properties')
    completed['manifest_sha256'] = pin(bundle/'manifest.properties')['sha256']
    write_properties(bundle/'COMPLETED.properties', completed)


def replace_line(path, prefix, replacement):
    lines = Path(path).read_text(encoding='ascii').splitlines()
    if sum(line.startswith(prefix) for line in lines) != 1:
        raise ValueError('Expected one fixture field: '+prefix)
    Path(path).write_text(''.join((replacement if line.startswith(prefix) else line)+'\n' for line in lines), encoding='ascii', newline='\n')


def flip(path):
    with Path(path).open('r+b') as stream:
        byte = stream.read(1)
        assert byte
        stream.seek(0)
        stream.write(bytes([byte[0] ^ 1]))


def changed_reader(source, target, member, old, new):
    """Independent private verifier copy; one valid class-file constant changes."""
    assert len(old) == len(new) and source.resolve() != target.resolve()
    with zipfile.ZipFile(source) as before, zipfile.ZipFile(target, 'x') as after:
        assert before.namelist().count(member) == 1
        for entry in before.infolist():
            raw = before.read(entry)
            if entry.filename == member:
                assert raw.count(old) == 1
                raw = raw.replace(old, new)
            after.writestr(entry, raw)
    with zipfile.ZipFile(source) as before, zipfile.ZipFile(target) as after:
        assert before.namelist() == after.namelist()
        changed = [name for name in before.namelist() if before.read(name) != after.read(name)]
        assert changed == [member]
    return dict(source=pin(source), negative_copy=pin(target), changed_member=member, original_result=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ['jar', 'sumher', 'genotype', 'liability', 'old-reader', 'out']:
        parser.add_argument('--'+key, type=Path, required=True)
    parser.add_argument('--java', required=True, type=Path)
    args = parser.parse_args()
    out = args.out.absolute()
    sources = [p.resolve() for p in [args.sumher, args.genotype, args.liability]]
    if out.exists() or any(out.resolve().is_relative_to(p) or p.is_relative_to(out.resolve()) for p in sources):
        parser.error('Require NEW output disjoint from every source bundle')
    out.mkdir(parents=True)
    before = {str(p):inventory(p) for p in sources}
    readers = {str(p.resolve()):pin(p) for p in [args.jar, args.old_reader]}
    records = []
    prefix = [str(args.java.resolve()), '-Xmx2g', '-jar', str(args.jar.resolve()), 'ldak-tools']
    def execute(name, command, expected, message='', clean_path=False):
        started = time.monotonic()
        environment = os.environ.copy()
        if clean_path:
            environment['PATH'] = ''
        with (out/(name+'.stdout')).open('xb') as so, (out/(name+'.stderr')).open('xb') as se:
            result = subprocess.run(command, cwd=out, env=environment, stdin=subprocess.DEVNULL, stdout=so, stderr=se, timeout=180)
        stderr = (out/(name+'.stderr')).read_text(encoding='utf-8')
        row = dict(name=name, command=command, exit_code=result.returncode, expected_exit=expected, required_diagnostic=message, diagnostic_found=message in stderr, path_empty=clean_path, seconds=round(time.monotonic()-started, 3))
        (out/(name+'.PROCESS.json')).write_text(json.dumps(row, indent=2)+'\n', encoding='utf-8')
        records.append(row)
        assert result.returncode == expected and message in stderr, 'Inspect retained '+name+' diagnostics'
        print('PASS '+name, flush=True)
    def case(name, source, mutate, message, reseal=False):
        folder = out/('negative-'+name)
        folder.mkdir()
        bundle = folder/'bundle'
        shutil.copytree(source, bundle, copy_function=shutil.copyfile)
        mutate(bundle)
        if reseal:
            contrived_reseal(bundle)
        (folder/'CASE.json').write_text(json.dumps(dict(negative_fixture_only=True, source=str(source), contrived_outer_hashes_rebuilt=reseal, implementation_fingerprints_refreshed=False, expected_exit=2, required_diagnostic=message), indent=2)+'\n', encoding='utf-8')
        execute(name, prefix+['verify', '--bundle', str(bundle), '--out', str(folder/'verification')], 2, message)
        assert not (folder/'verification/VERIFIED.properties').exists()

    try:
        sumher, genotype, liability = sources
        relocated = out/'relocated-complete-bundle'
        shutil.copytree(genotype, relocated, copy_function=shutil.copyfile)
        execute('relocated-path-empty', prefix+['verify', '--bundle', str(relocated), '--out', str(out/'relocated-verification')], 0, clean_path=True)
        case('extra-empty-directory', sumher, lambda p:(p/'unexpected-empty').mkdir(), 'Complete directory membership/content differs')
        case('missing-console', sumher, lambda p:(p/'stages/sumher/original.stdout').unlink(), 'Complete directory membership/content differs')
        case('changed-raw-matrix', genotype, lambda p:flip(p/'stages/kinship-000/result.grm.bin'), 'Complete directory membership/content differs')
        case('completion-count', sumher, lambda p:replace_line(p/'COMPLETED.properties', 'analyses=', 'analyses=999'), 'Completion/native result count differs')
        case('completion-execution', sumher, lambda p:replace_line(p/'COMPLETED.properties', 'original_engine_executed=', 'original_engine_executed=false'), 'Unknown/incomplete original LDAK profile')
        case('replay-lineage', sumher, lambda p:replace_line(p/'COMPLETED.properties', 'genuine_replay=', 'genuine_replay=true'), 'Replay lineage/membership contradicts completion')
        case('missing-completion', sumher, lambda p:(p/'COMPLETED.properties').unlink(), 'Require a regular properties file')
        case('resealed-binary', sumher, lambda p:flip(p/'runtime/ldak5.2.linux'), 'Captured original software/license identity differs', True)
        case('resealed-license', sumher, lambda p:flip(p/'runtime/LICENSE-UPSTREAM.txt'), 'Captured original software/license identity differs', True)
        def matrix(p):
            path = p/'stages/kinship-000/result.grm.bin'
            with path.open('r+b') as stream:
                value = struct.unpack('<f', stream.read(4))[0]
                stream.seek(0)
                stream.write(struct.pack('<f', value+0.01))
        case('resealed-native-matrix', genotype, matrix, 'Original GRM differs from native hard-call recomputation', True)
        def overlap(p):
            row = (p/'stages/thin/result.in').read_text(encoding='ascii').splitlines()[0]
            with (p/'stages/thin/result.out').open('a', encoding='ascii') as stream:
                stream.write(row+'\n')
        case('resealed-thinning-overlap', genotype, overlap, 'Original thinning dispositions overlap', True)
        def inconsistent_lrt(p):
            path = p/'stages/reml-000/result.reml'
            value = next(line.split()[1] for line in path.read_text(encoding='ascii').splitlines() if line.startswith('LRT_Stat '))
            replace_line(path, 'LRT_Stat ', 'LRT_Stat '+str(float(value)+1))
        case('resealed-reml-lrt', genotype, inconsistent_lrt, 'Original REML likelihood/LRT fields disagree', True)
        case('resealed-nonconvergence', sumher, lambda p:replace_line(p/'stages/sumher/result.extra', 'Converged ', 'Converged NO'), 'Original model did not converge', True)
        def wrong_liability(p):
            path = p/'stages/sumher/result.hers.liab'
            lines = path.read_text(encoding='ascii').splitlines()
            fields = lines[1].split()
            fields[1] = str(float(fields[1])+0.1)
            lines[1] = ' '.join(fields)
            path.write_text('\n'.join(lines)+'\n', encoding='ascii', newline='\n')
        case('resealed-liability', liability, wrong_liability, 'Original liability conversion differs', True)
        case('resealed-process-arguments', sumher, lambda p:replace_line(p/'stages/sumher/PROCESS-STARTED.properties', 'argument.1=', 'argument.1=--reml'), 'Original command differs from captured typed request', True)
        case('resealed-process-failure', sumher, lambda p:replace_line(p/'stages/sumher/PROCESS-FINISHED.properties', 'exit_code=', 'exit_code=1'), 'Original stage failed or was interrupted', True)
        def error_console(p):
            with (p/'stages/sumher/original.stdout').open('a', encoding='ascii') as stream:
                stream.write('\nError, CONTRIVED NEGATIVE COPY with zero recorded process exit\n')
        case('resealed-zero-exit-error', sumher, error_console, 'Original Error/Fatal diagnostics prevent completion', True)
        execute('wrong-reader', [str(args.java.resolve()), '-jar', str(args.old_reader.resolve()), 'ldak-tools', 'verify', '--bundle', str(sumher), '--out', str(out/'wrong-reader')], 2, 'Matching native/workflow method code is required')
        for name, member, old, new in [
                ('anonymous-helper-code', 'org/jlinalg/cli/LdakWorkflowFiles$1.class', b'Reference/workspace entry budget exceeded', b'Reference/workspace entry budget EXCEEDED'),
                ('normal-arithmetic-dependency', 'jdistlib/math/MathFunctions.class', b'MathFunctions.java', b'MathFunctionS.java')]:
            target = out/(name+'.jar')
            change = changed_reader(args.jar, target, member, old, new)
            (out/(name+'.READER.json')).write_text(json.dumps(change, indent=2)+'\n', encoding='utf-8')
            execute(name, [str(args.java.resolve()), '-jar', str(target), 'ldak-tools', 'verify', '--bundle', str(sumher), '--out', str(out/name)], 2, 'Matching native/workflow method code is required', True)
        for name, values in [('missing-reference',dict(tagging=(out/'absent-reference').as_posix())), ('missing-runtime',dict(executable=(out/'absent-executable').as_posix())), ('execution-forbidden',dict(execution='forbid'))]:
            request_path = out/(name+'.properties')
            request_values = simple_properties(sumher/'request.properties')
            for key in ['executable', 'license', 'tagging', 'summary']:
                request_values[key] = (sumher/request_values[key]).as_posix()
            request_values.update(values)
            write_properties(request_path, request_values)
            execute(name, prefix+['run', '--request', str(request_path), '--out', str(out/name)], 3, 'SKIP3: dependent analysis skipped')
            assert not (out/name/'stages').exists() and not (out/name/'COMPLETED.properties').exists()
        execute('existing-output', prefix+['verify', '--bundle', str(sumher), '--out', str(sumher)], 2, 'Output already exists')
        execute('overlapping-output', prefix+['verify', '--bundle', str(sumher), '--out', str(sumher/'forbidden-child')], 2, 'Output overlaps an input/runtime/reference')
        assert not (sumher/'forbidden-child').exists()
        for path, identity in before.items():
            assert inventory(Path(path)) == identity, 'Source evidence changed: '+path
        for path, identity in readers.items():
            assert pin(Path(path)) == identity, 'Preserved reader changed: '+path
        value = dict(status='PASS_RELOCATED_VERIFY_NEGATIVE_INTEGRITY_AND_NATIVE_SCIENTIFIC_AUDITS_PREREQUISITE_SKIPS', processes=records, source_bundles_unchanged=True, readers_unchanged=True, contrived_copies_are_not_results=True, whole_goal_complete=False)
        (out/'COMPLETED.json').write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8')
        print(value['status'], flush=True)
        return 0
    except Exception:
        (out/'FAILURE.txt').write_text(traceback.format_exc(), encoding='utf-8')
        print('FAIL2: inspect retained qualification diagnostics; source evidence is not repaired.', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
