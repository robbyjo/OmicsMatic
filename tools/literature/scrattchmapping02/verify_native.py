#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify every native fixture export and re-execute the original R component."""
from pathlib import Path, PurePosixPath
import argparse, csv, hashlib, io, json, os, subprocess, sys, tarfile, traceback, zipfile
sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools/literature'))
from verify_doubletfinder206_native import Values
MODULE = 'tools/literature/scrattchmapping02'
DATA = 'docs/literature/scrattchmapping02-native-development'
FIXTURE = 'src/test/resources/scrattchmapping02-native-reference.zip'
ACTION = ('Native Java normalization needs no R runtime. This reference verifier additionally requires the exact permitted '
          'R 4.5.3/Matrix 1.7.6 profile for full original value/condition replay. Missing or forbidden requirements SKIP3 '
          'skip dependent R verification; FAIL2 admits no complete verification. Retain complete RDS, raw logs, warnings, '
          'errors and all available partial output. Resolve the named cause and retry NEW paths without changing old hashes '
          'or repairing identifiers, NA/NaN/Inf or zero norms. This is one numeric component; full hierarchical mapping, '
          'the package, paper reproduction and the 250-paper goal remain unqualified/open.')


def load(path):
    return json.loads(path.read_bytes())


def identity(raw):
    return dict(bytes=len(raw), sha256=hashlib.sha256(raw).hexdigest())


def pin(path):
    return identity(path.read_bytes())


def save(path, value):
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8', newline='\n')


def safe(name):
    p = PurePosixPath(name)
    if p.is_absolute() or not p.parts or any(x in ['.', '..'] for x in p.parts) or '\\' in name:
        raise ValueError('Unsafe evidence member')
    return p


def decode(value):
    return None if value == '!NA' else bytes.fromhex(value).decode('utf-8')


def names_file(path):
    lines = path.read_text(encoding='utf-8').splitlines()
    if lines == ['ABSENT']:
        return None
    if not lines or lines[0] != 'PRESENT':
        raise ValueError('Invalid original names export')
    return [decode(s) for s in lines[1:]]


def attributes(values, node):
    names = values.value(node['attribute_names']) if node['attribute_names'] is not None else []
    return {names[a['index'] - 1]: values.value(a['value']) for a in node['attributes']}


def matrix_export(values, identifier, rows, columns, target):
    node = values.nodes[identifier]
    if node['type'] != 'double' or node['length'] != rows * columns:
        raise ValueError('Original dense numeric matrix type/size changed')
    attrs = attributes(values, node)
    if attrs.get('dim') != [rows, columns] or set(attrs) - {'dim', 'dimnames'}:
        raise ValueError('Original matrix dimensions or attributes differ')
    raw = (values.path / node['payload']['path']).read_bytes()
    row_major = b''.join(raw[8*(r+rows*c):8*(r+rows*c+1)] for r in range(rows) for c in range(columns))
    if target.read_bytes() != row_major:
        raise ValueError('Complete original matrix export bits differ: ' + str(target))
    return attrs.get('dimnames', [None, None])


def verify(out, rscript=None, wsl_distro=None):
    proof = load(ROOT / DATA / 'VALIDATION.json')
    for name, wanted in proof['sources'].items():
        if pin(ROOT / name) != wanted:
            raise ValueError('Qualified current native source differs: ' + name)
    if pin(ROOT / FIXTURE) != proof['fixture']:
        raise ValueError('Complete original fixture differs')
    target = out / 'references'
    target.mkdir()
    with zipfile.ZipFile(ROOT / FIXTURE) as archive:
        members = json.loads(archive.read('MEMBERS.json'))
        if len(archive.namelist()) != len(members) + 1 or set(archive.namelist()) != set(members) | {'MEMBERS.json'}:
            raise ValueError('Complete fixture inventory differs')
        if len({n.casefold() for n in archive.namelist()}) != len(archive.namelist()):
            raise ValueError('Case-insensitive fixture collision')
        for name in archive.namelist():
            raw = archive.read(name)
            if name != 'MEMBERS.json' and identity(raw) != members[name]:
                raise ValueError('Original fixture member differs: ' + name)
            path = target.joinpath(*safe(name).parts)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
    profile = load(ROOT / MODULE / 'native-source-profile.json')
    if pin(target / 'original-source.tar.gz') != profile['archive']:
        raise ValueError('Complete original upstream archive differs')
    source = out / 'original-source'
    source.mkdir()
    actual = {}
    with tarfile.open(target / 'original-source.tar.gz', 'r:gz') as archive:
        for member in archive.getmembers():
            p = safe(member.name)
            if len(p.parts) == 1:
                continue
            name = PurePosixPath(*p.parts[1:]).as_posix()
            path = source.joinpath(*safe(name).parts)
            if member.isdir():
                path.mkdir(parents=True, exist_ok=True)
                continue
            if not member.isfile():
                raise ValueError('Unexpected nonregular original source entry')
            raw = archive.extractfile(member).read()
            actual[name] = identity(raw)
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(raw)
    if actual != profile['complete_original_regular_members']:
        raise ValueError('All original source members required')
    rows = list(csv.DictReader((target / 'cases.tsv').read_text().splitlines(), delimiter='\t'))
    cases = successful = errors = numeric = nodes = payloads = payload_bytes = warnings = 0
    for case in rows:
        folder = target / case['name']
        values = Values(folder / 'complete-R-values')
        root = values.nodes[values.index['root']]
        if values.value(root['element_names']) != ['inputs', 'result']:
            raise ValueError('Complete native input/result tree required')
        inputs = values.nodes[root['children'][0]]
        if values.value(inputs['element_names']) != ['X', 'by']:
            raise ValueError('Complete typed original input fields required')
        nr, nc = int(case['rows']), int(case['columns'])
        names = matrix_export(values, inputs['children'][0], nr, nc, folder / 'input.bin')
        if names != [names_file(folder / 'row-names.tsv'), names_file(folder / 'column-names.tsv')]:
            raise ValueError('Original missing/literal/duplicate matrix names changed')
        if values.value(inputs['children'][1]) != [decode(case['by'])]:
            raise ValueError('Original normalization selector changed')
        result_id = root['children'][1]
        if case['status'] == 'PASS':
            if matrix_export(values, result_id, nr, nc, folder / 'result.bin') != names:
                raise ValueError('Complete original returned matrix names changed')
            successful += 1
            numeric += nr * nc
        elif case['status'] == 'ERROR' and values.nodes[result_id]['type'] == 'NULL' and not (folder / 'result.bin').exists():
            errors += 1
        else:
            raise ValueError('An original error was dropped or admitted')
        nodes += len(values.index['nodes'])
        for node in values.index['nodes']:
            if node['payload']:
                payloads += 1
                payload_bytes += node['payload']['bytes']
        warnings += int(case['warnings'])
        if int(case['messages']):
            raise ValueError('Unexpected original handler-message population')
        cases += 1
    if (cases, successful, errors, warnings, numeric) != (318, 303, 15, 52, 58131):
        raise ValueError('Complete original comparison population differs')
    artifacts = dict(status='PASS_COMPLETE_NATIVE_SCOPE_R_TREES_AND_ALL_EXPORTED_MATRIX_BITS', cases=cases,
                     successful=successful, original_errors=errors, original_warning_messages=warnings,
                     compared_return_values=numeric, R_nodes=nodes, atomic_payloads=payloads,
                     atomic_payload_bytes=payload_bytes, fixture_members=len(members)+1,
                     original_source_members=len(actual), native_Java_requires_R=False,
                     full_hierarchical_mapping_qualified=False, new_family_credit=0, whole_goal_complete=False)
    save(out / 'ARTIFACTS.json', artifacts)
    if not rscript:
        raise OSError('Exact permitted R executable required for full original result/condition re-execution')
    for name in ['home', 'tmp', 'empty-library']:
        (out / name).mkdir()
    if sys.platform == 'win32':
        if not wsl_distro:
            raise OSError('Provide --wsl-distro for the exact Linux R profile; native Windows replay skipped')
        def linux(path):
            p = Path(path)
            if p.drive.lower() != 'd:':
                raise OSError('Use Linux or the explicit D: WSL route for this replay')
            return '/mnt/d/' + p.as_posix()[3:]
        prefix = PurePosixPath(rscript).parent.parent
        env = dict(HOME=linux(out/'home'), TMPDIR=linux(out/'tmp'), R_LIBS_USER=linux(out/'empty-library'),
                   R_LIBS_SITE=str(prefix/'lib/R/library'), R_ENVIRON_USER='/dev/null', R_PROFILE_USER='/dev/null',
                   OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1', PATH=str(prefix/'bin')+':/usr/local/bin:/usr/bin:/bin')
        command = ['wsl', '-d', wsl_distro, '-u', 'root', '--', '/usr/bin/env', '-u', 'R_HOME',
                   *[k+'='+v for k, v in env.items()], rscript, '--vanilla',
                   linux(ROOT/MODULE/'VerifyNativeCaptured.R'), linux(source), linux(target), linux(out/'COMPLETE-R-COMPARISON.json')]
        environment = None
    elif sys.platform == 'linux':
        prefix = Path(rscript).parent.parent
        environment = dict(os.environ, HOME=str(out/'home'), TMPDIR=str(out/'tmp'), R_LIBS_USER=str(out/'empty-library'),
                           R_LIBS_SITE=str(prefix/'lib/R/library'), R_ENVIRON_USER='/dev/null', R_PROFILE_USER='/dev/null',
                           OMP_NUM_THREADS='1', OPENBLAS_NUM_THREADS='1')
        environment.pop('R_HOME', None)
        command = [rscript, '--vanilla', str(ROOT/MODULE/'VerifyNativeCaptured.R'), str(source), str(target), str(out/'COMPLETE-R-COMPARISON.json')]
    else:
        raise OSError('Exact Linux R profile required for this original reference replay')
    with (out / 'original.stdout').open('xb') as so, (out / 'original.stderr').open('xb') as se:
        child = subprocess.run(command, cwd=out, env=environment, stdin=subprocess.DEVNULL, stdout=so, stderr=se)
    save(out / 'PROCESS.json', dict(command=command, exit_code=child.returncode))
    if child.returncode in [3, 127]:
        raise OSError('Exact original R prerequisite unavailable; dependent re-execution skipped')
    if child.returncode:
        raise ValueError('Complete original result/error/condition re-execution failed; retain raw logs')
    replay = load(out / 'COMPLETE-R-COMPARISON.json')
    if (replay['cases'], replay['original_errors'], replay['warning_messages']) != (318, 15, 52):
        raise ValueError('Complete original object/condition population differs')
    return dict(artifacts, status='PASS_NATIVE_COMPONENT_EXPORTS_AND_ALL318_COMPLETE_ORIGINAL_R_OBJECTS',
                complete_original_R_objects_reexecuted=318, complete_R_condition_equality=True,
                whole_tool_qualified=False, paper_reproduced=False)


def main():
    p = argparse.ArgumentParser(description=__doc__, epilog=ACTION)
    p.add_argument('--out', required=True, type=Path)
    p.add_argument('--rscript')
    p.add_argument('--wsl-distro')
    a = p.parse_args()
    out, owned = a.out.resolve(), False
    try:
        if not out.is_relative_to(ROOT/'build/literature-review/monocle-runtime'):
            raise ValueError('Use a NEW monocle-runtime output')
        out.mkdir(parents=True, exist_ok=False)
        owned = True
        result = verify(out, a.rscript, a.wsl_distro)
        (out/'WARNINGS.md').write_text(ACTION+'\n', encoding='utf-8')
        save(out/'COMPLETED.json', result)
        print(result['status'], flush=True)
        return 0
    except BaseException as error:
        code = 3 if isinstance(error, OSError) and not isinstance(error, FileExistsError) else 2
        status = 'SKIP3_ORIGINAL_NATIVE_REFERENCE_PREREQUISITE_UNAVAILABLE' if code == 3 else 'FAIL2_NATIVE_REFERENCE_UNADMITTED'
        print(status, repr(error), ACTION, flush=True)
        if owned:
            record = dict(status=status, cause=repr(error), traceback=traceback.format_exc(), result_admitted=False, required_action=ACTION)
            for name, raw in [('FAILURE.json',(json.dumps(record,indent=2)+'\n').encode()),
                              ('WARNINGS.md',(status+'\n'+repr(error)+'\n'+ACTION+'\n').encode())]:
                try:
                    (out/name).write_bytes(raw)
                except BaseException as recording:
                    print('FAIL2_VERIFICATION_RECORDING_UNAVAILABLE',name,repr(recording),ACTION,flush=True)
        return code


if __name__ == '__main__':
    raise SystemExit(main())
