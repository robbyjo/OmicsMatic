"""Recompile/recheck the five saved synthetic GRM cases; no integration credit.

Uses only Python's standard library and an explicitly selected JDK. This is a
development probe, not a GCTA execution adapter or a paper reproduction.
"""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile


def pin(data):
    return dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--java', required=True, type=Path)
    compilation = parser.add_mutually_exclusive_group(required=True)
    compilation.add_argument('--javac', type=Path)
    compilation.add_argument('--compiled-classes', type=Path,
        help='Use the exact pinned Windows-JDK-21 probe classes; source is still checked')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[3]
    evidence = root / 'docs/literature/gcta-development'
    profile = json.loads((evidence / 'PROTOTYPE.json').read_bytes())
    source = root / 'src/main/java/org/jlinalg/genetics/GctaHardcallGrm.java'
    if pin(source.read_bytes()) != profile['native_source']:
        raise ValueError('prototype source differs from the captured checkpoint')
    archive = evidence / 'synthetic-evidence.zip'
    if pin(archive.read_bytes()) != profile['synthetic_evidence']:
        raise ValueError('synthetic evidence integrity mismatch')
    for executable in [args.java] + ([args.javac] if args.javac else []):
        if not executable.is_file():
            raise FileNotFoundError('SKIP3: supply the permitted JDK executable: ' + str(executable))
    args.out.mkdir(parents=True, exist_ok=False)
    out = args.out.resolve()
    try:
        with zipfile.ZipFile(archive) as bundle:
            entries = bundle.infolist()
            if len(entries) > 2000 or sum(item.file_size for item in entries) > 30_000_000:
                raise ValueError('saved evidence exceeds development-probe capacity')
            seen = set()
            for item in entries:
                name = PurePosixPath(item.filename)
                if (item.filename in seen or name.is_absolute() or '\\' in item.filename
                        or any(part in ('..', '') for part in name.parts)
                        or not name.parts or ':' in item.filename
                        or item.is_dir() or (item.external_attr >> 16) & 0o170000 == 0o120000):
                    raise ValueError('unexpected saved evidence member')
                seen.add(item.filename)
                target = out / 'evidence' / str(name)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(bundle.read(item))

        def run(name, command):
            with (out / (name + '.stdout')).open('xb') as stdout, (out / (name + '.stderr')).open('xb') as stderr:
                result = subprocess.run(command, cwd=out, stdin=subprocess.DEVNULL,
                    stdout=stdout, stderr=stderr, timeout=180,
                    creationflags=subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0)
            (out / (name + '.PROCESS.json')).write_text(json.dumps(dict(
                command=command, exit_code=result.returncode), indent=2) + '\n', encoding='utf-8')
            if result.returncode:
                raise RuntimeError('FAIL2: NO COMPLETE RESULT: inspect ' + name + ' diagnostics')

        classes = out / 'classes'
        classes.mkdir()
        probe = out / 'evidence/native/NativeGctaGrmProbe.java'
        if args.javac:
            run('javac', [str(args.javac.resolve()), '-encoding', 'UTF-8', '-d', str(classes), str(source), str(probe)])
        else:
            original = args.compiled_classes.resolve()
            found = {p.relative_to(original).as_posix(): pin(p.read_bytes())
                for p in original.rglob('*') if p.is_file()}
            if found != profile['windows21_probe_classes']:
                raise ValueError('compiled probe classes differ from the pinned source build')
            for name in found:
                target = classes / name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(original / name, target)
        rows = []
        for case in profile['cases']:
            run(case, [str(args.java.resolve()), '-cp', str(classes), 'NativeGctaGrmProbe',
                str(out / ('evidence/native/' + case + '.input.bin')), str(out / case)])
            reference = out / 'evidence/original-core-2/windows' / case
            fields = 0
            for name in ['result.grm.bin', 'result.grm.N.bin']:
                actual = (out / case / name).read_bytes()
                expected = (reference / name).read_bytes()
                if actual != expected or len(actual) % 4:
                    raise ValueError('FAIL2: source float32 mismatch: ' + case + '/' + name)
                fields += len(actual) // 4
            if (out / case / 'result.grm.id').read_bytes().split() != (reference / 'result.grm.id').read_bytes().split():
                raise ValueError('FAIL2: ordered composite identities differ')
            rows.append(dict(case=case, exact_float32_fields=fields))
        result = dict(status='PASS_FIVE_INITIAL_SYNTHETIC_GRM_CASES_ONLY', cases=rows,
            exact_float32_fields=sum(row['exact_float32_fields'] for row in rows),
            compiled_here=bool(args.javac),
            new_catalog_credit=0, workflow_qualified=False, complete_tool_qualified=False)
        (out / 'RESULT.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(result))
    except BaseException as error:
        (out / 'FAILURE.json').write_text(json.dumps(dict(error=repr(error),
            status='FAIL2: NO COMPLETE RESULT; retained diagnostic development attempt'), indent=2) + '\n', encoding='utf-8')
        raise


if __name__ == '__main__':
    main()
