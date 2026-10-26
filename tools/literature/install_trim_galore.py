"""Private original Trim Galore profiles referencing permitted existing runtimes.

No global install, PATH mutation or modification of the referenced dependencies.
Installation and network access require separate explicit flags. Scientific
analysis validation is separate from these integrity/version checks.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
LOCK = Path(__file__).with_name('trim-galore-runtime-lock.json')
MARKER = '.jlinalg-trim-galore.json'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def environment(perl, python):
    result = dict(os.environ)
    for key in ['PERL5LIB', 'PERLLIB', 'PERL5OPT', 'PYTHONPATH', 'PYTHONHOME', 'CLASSPATH', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS']:
        result.pop(key, None)
    result.update(LC_ALL='C', PERL_HASH_SEED='0', PERL_PERTURB_KEYS='0', PYTHONNOUSERSITE='1', PYTHONDONTWRITEBYTECODE='1')
    result['PATH'] = os.pathsep.join([str(perl.parent), str(python.parent), str(Path(os.environ.get('SystemRoot', 'C:/Windows')) / 'System32')])
    return result


def execute(command, attempt, name, env):
    result = subprocess.run(list(map(str, command)), capture_output=True, env=env, timeout=120)
    (attempt / (name + '.stdout')).write_bytes(result.stdout)
    (attempt / (name + '.stderr')).write_bytes(result.stderr)
    if result.returncode:
        raise RuntimeError(name + ' failed, exit ' + str(result.returncode) + '; inspect retained stdout/stderr')
    return result.stdout.decode('utf-8')


def pe_imports(path):
    """Read PE import names without executing or installing a compiler tool."""
    data = path.read_bytes()
    if data[:2] != b'MZ' or len(data) < 64:
        raise ValueError('Expected Windows PE executable/library: ' + str(path))
    start = struct.unpack_from('<I', data, 60)[0]
    if data[start:start+4] != b'PE\0\0':
        raise ValueError('Invalid PE header')
    sections, optional_size = struct.unpack_from('<H', data, start+6)[0], struct.unpack_from('<H', data, start+20)[0]
    optional = start+24
    magic = struct.unpack_from('<H', data, optional)[0]
    if magic not in [0x10b, 0x20b]:
        raise ValueError('Unsupported PE optional header')
    directory = optional + (96 if magic == 0x10b else 112)
    import_rva, import_size = struct.unpack_from('<II', data, directory+8)
    if not import_rva or not import_size:
        return []
    mappings = []
    for i in range(sections):
        entry = optional + optional_size + 40*i
        virtual_size, virtual_address, raw_size, raw_start = struct.unpack_from('<IIII', data, entry+8)
        mappings.append((virtual_address, max(virtual_size, raw_size), raw_start))
    def offset(rva):
        for address, size, raw in mappings:
            if address <= rva < address+size:
                value = raw+rva-address
                if value >= len(data):
                    break
                return value
        raise ValueError('PE RVA outside file sections')
    result = []
    entry = offset(import_rva)
    for _ in range(4096):
        descriptor = struct.unpack_from('<IIIII', data, entry)
        if not any(descriptor):
            return result
        name = offset(descriptor[3]); end = data.find(b'\0', name)
        if end < name or end-name > 512:
            raise ValueError('Invalid PE import name')
        value = data[name:end].decode('ascii')
        if '/' in value or '\\' in value:
            raise ValueError('Unexpected path in PE import')
        result.append(value); entry += 20
    raise ValueError('PE import count exceeds bound')


def inventory(roots):
    result = {}
    for spec in roots:
        root = Path(spec['path'])
        if root.is_file():
            paths = [root]
        elif root.is_dir():
            paths = []
            for current, directories, files in os.walk(root):
                directories[:] = sorted(d for d in directories if d != '__pycache__' and d not in spec.get('excluded_directories', []))
                paths.extend(Path(current) / name for name in sorted(files) if not name.endswith(('.pyc', '.pyo')))
        elif spec.get('allow_absent'):
            paths = []
        else:
            raise FileNotFoundError('Required dependency root missing: ' + str(root))
        for path in paths:
            resolved = path.resolve()
            if path.is_symlink():
                raise ValueError('Unexpected dependency symlink: ' + str(path))
            result[resolved.as_posix()] = digest(resolved)
    return dict(sorted(result.items()))


def binary_dependencies(seeds, search):
    pending = list(seeds); seen = {}; windows = Path(os.environ.get('SystemRoot', 'C:/Windows')).resolve()
    while pending:
        path = pending.pop().resolve()
        if path.as_posix() in seen:
            continue
        if not path.is_file():
            raise FileNotFoundError('Required native dependency missing: ' + str(path))
        seen[path.as_posix()] = digest(path)
        for name in pe_imports(path):
            if name.lower().startswith(('api-ms-win-', 'ext-ms-win-')):
                continue  # Operating-system API-set contracts.
            found = next((base / name for base in [path.parent, *search] if (base / name).is_file()), None)
            if found is None:
                raise FileNotFoundError('Native dependency ' + name + ' required by ' + str(path))
            if not found.resolve().is_relative_to(windows):
                pending.append(found)
    return seen


def verified(home, lock):
    try:
        marker = json.loads((home / MARKER).read_text(encoding='utf-8'))
        if marker['schema_version'] != 1 or marker['profile'] != lock['profile'] or marker['lock_sha256'] != digest(LOCK):
            return False
        expected = {v + '/' + n: h for v, p in lock['profiles'].items() for n, h in p['files'].items()}
        if marker['files'] != expected or {p.relative_to(home).as_posix() for p in home.rglob('*') if p.is_file()} != set(expected) | {MARKER}:
            return False
        if 'wheels' in lock:
            if set(marker.get('original_wheels', {})) != set(lock['wheels']) or not marker.get('original_wheel_files'):
                return False
            if any(marker['original_wheels'][name]['archive_sha256'] != pin['sha256'] for name, pin in lock['wheels'].items()):
                return False
            if any(marker['external_files'].get(name) != value for name, value in marker['original_wheel_files'].items()):
                return False
            if marker.get('fastqc') and marker['fastqc'].get('archive_sha256') != lock['fastqc_archive']['sha256']:
                return False
        if any(digest(home / name) != value for name, value in expected.items()):
            return False
        current = inventory(marker['external_roots'])
        for name in marker['external_fixed_files']:
            current[name] = digest(Path(name))
        return dict(sorted(current.items())) == marker['external_files']
    except (OSError, ValueError, KeyError, TypeError):
        return False


def source(profile, version, name, cache, allowed, supplied):
    expected = profile['files'][name]
    cached = cache / (version + '.' + name)
    if supplied is not None and (supplied / version / name).is_file():
        path = supplied / version / name
        if digest(path) != expected:
            raise ValueError('Supplied original source differs: ' + str(path))
        return path
    if cached.exists():
        if digest(cached) != expected:
            raise ValueError('Cached original source changed; preserve it and choose a new cache')
        return cached
    if not allowed:
        raise RuntimeError('Original source absent; --allow-download required or supply a verified --source-cache')
    cache.mkdir(parents=True, exist_ok=True)
    url = 'https://raw.githubusercontent.com/FelixKrueger/TrimGalore/' + profile['commit'] + '/' + name
    with tempfile.TemporaryDirectory(dir=cache) as temporary:
        path = Path(temporary) / 'download'
        with urllib.request.urlopen(url, timeout=180) as remote, path.open('wb') as output:
            shutil.copyfileobj(remote, output)
        if digest(path) != expected:
            raise ValueError('Downloaded original source differs from pinned SHA-256: ' + url)
        shutil.copyfile(path, cached)
    return cached


def archive(pin, cache, allowed, supplied=None):
    cache.mkdir(parents=True, exist_ok=True)
    target = cache / pin['url'].rsplit('/', 1)[-1]
    if supplied is not None and supplied.is_file():
        if digest(supplied) != pin['sha256']:
            raise ValueError('Supplied upstream archive differs from pinned checksum')
        return supplied
    if not target.exists():
        if not allowed:
            raise RuntimeError('Original distribution archive absent; --allow-download required for authentication')
        with tempfile.TemporaryDirectory(dir=cache) as temporary:
            download = Path(temporary) / 'archive'
            with urllib.request.urlopen(pin['url'], timeout=180) as remote, download.open('wb') as output:
                shutil.copyfileobj(remote, output)
            if digest(download) != pin['sha256']:
                raise ValueError('Downloaded original distribution archive differs from pinned checksum')
            shutil.copyfile(download, target)
    if digest(target) != pin['sha256']:
        raise ValueError('Cached upstream archive changed; preserve it and choose a new cache')
    return target


def compare_archive(path, installed, prefix='', wheel=False):
    verified = {}; seen = set(); installed = installed.resolve()
    with zipfile.ZipFile(path) as archive:
        for item in archive.infolist():
            name = item.filename
            if item.is_dir():
                continue
            if not name.startswith(prefix) or '\\' in name or item.external_attr >> 16 & 0o170000 == 0o120000:
                raise ValueError('Unexpected original distribution member/prefix/link')
            relative = name[len(prefix):]
            if not relative or Path(relative).is_absolute() or any(part in ['.', '..'] or ':' in part for part in Path(relative).parts):
                raise ValueError('Unsafe upstream archive member path')
            if wheel and relative.endswith('.dist-info/RECORD'):
                continue  # Pip rewrites installation paths and generated launcher hashes.
            if wheel and '.data/' in relative:
                raise ValueError('Unexpected wheel installation scheme; prepare an explicitly supported profile')
            target = (installed / relative).resolve()
            if not target.is_relative_to(installed) or relative in seen or not target.is_file():
                raise ValueError('Original distribution member missing or repeated: ' + relative)
            seen.add(relative)
            expected = hashlib.sha256(archive.read(item)).hexdigest()
            if digest(target) != expected:
                raise ValueError('Installed program differs from the original archive: ' + str(target))
            verified[target.as_posix()] = expected
    if not verified:
        raise ValueError('Original distribution archive has no verified members')
    return verified


def prepare_python(args, attempt, lock):
    """Create only a new owned environment; never pip-repair an existing one."""
    target = args.python_environment.resolve(); owner = target / '.jlinalg-trim-python.json'
    if target.exists():
        if not owner.is_file():
            raise RuntimeError('Existing unmanaged Python environment preserved; choose a new --python-environment')
        metadata = json.loads(owner.read_text(encoding='utf-8'))
        if metadata.get('lock_sha256') != digest(LOCK):
            raise RuntimeError('Existing Python environment belongs to a different profile; choose a new --python-environment')
        python = target / 'Scripts/python.exe'
        if not python.is_file():
            raise RuntimeError('Existing private Python is incomplete; preserve it and select a new environment')
        value = execute([python.parent / 'cutadapt.exe', '--version'], attempt, 'private-python-reused-console', environment(args.perl.resolve(), python)).strip()
        if value != '5.2':
            raise RuntimeError('Existing private Cutadapt launcher failed its version check; preserve it and choose a new --python-environment')
        return python
    if not args.allow_install:
        raise RuntimeError('Creating a private Python environment requires --allow-install')
    base = args.python_base.resolve() if args.python_base else None
    if base is None or not base.is_file():
        raise RuntimeError('--prepare-python requires an existing permitted --python-base CPython3.13 executable; no OS/global interpreter install is attempted')
    env = environment(args.perl.resolve(), base)
    version = execute([base, '-I', '-c', 'import sys;print("%d.%d"%sys.version_info[:2])'], attempt, 'python-base-version', env).strip()
    if version != '3.13':
        raise RuntimeError('Pinned Windows wheel profile requires CPython3.13; supplied base is ' + version)
    wheels = [archive(pin, args.cache.resolve(), args.allow_download) for pin in lock['wheels'].values()]
    target.parent.mkdir(parents=True, exist_ok=True)
    target.mkdir()  # Exclusive reservation; existing environments are never modified.
    (attempt / 'python-attempt-directory.txt').write_text(str(target) + '\n', encoding='utf-8')
    execute([base, '-I', '-B', '-m', 'venv', target], attempt, 'private-python-create', env)
    python = target / 'Scripts/python.exe'
    execute([python, '-I', '-B', '-m', 'pip', 'install', '--no-index', '--no-deps', '--no-compile', '--disable-pip-version-check', *wheels], attempt, 'private-python-install', env)
    verified_files = {}
    for wheel in wheels:
        verified_files.update(compare_archive(wheel, target / 'Lib/site-packages', wheel=True))
    value = execute([python.parent / 'cutadapt.exe', '--version'], attempt, 'private-python-console', env).strip()
    if value != '5.2':
        raise RuntimeError('New private Cutadapt launcher did not report5.2;retain this incomplete environment and choose a new destination')
    # Pip-generated Windows console launchers embed the absolute environment
    # path. Keep the newly reserved directory in place, marking ownership only
    # after all archive comparisons and actual launcher checks pass.
    (target / '.jlinalg-trim-python.json').write_text(json.dumps(dict(owner='JLinAlg original Trim Galore private Python', lock_sha256=digest(LOCK),
        python_base=str(base), original_wheel_archives={p.name:digest(p) for p in wheels}, verified_original_members=len(verified_files)), indent=2) + '\n', encoding='utf-8')
    return target / 'Scripts/python.exe'


def discover(args, attempt, lock):
    perl, python = args.perl.resolve(), args.python.resolve()
    env = environment(perl, python)
    if not perl.is_file() or not python.is_file() or not perl.parent.joinpath('sh.exe').is_file():
        raise RuntimeError('Permitted Cygwin Perl/sh and native Python are required; no system install is attempted')
    perl_info = json.loads(execute([perl, '-MJSON::PP', '-MConfig', '-e', 'print encode_json({version=>"$^V",inc=>[map {Cygwin::posix_to_win_path($_,1)} @INC]})'], attempt, 'perl', env))
    site = python.parent.parent / 'Lib/site-packages'
    code = ('import sys,json,importlib.metadata as m;from pathlib import Path;sys.path.insert(0,sys.argv[1]);names=json.loads(sys.argv[2]);'
            'print(json.dumps(dict(base=sys.base_prefix,version=sys.version,packages={n:dict(version=m.version(n),files=[str(m.distribution(n).locate_file(p).resolve()) for p in m.files(n) if not str(p).endswith((".pyc",".pyo"))]) for n in names})))')
    info = json.loads(execute([python, '-I', '-S', '-B', '-X', 'pycache_prefix=' + str(attempt / 'ignored-cache'), '-c', code, site, json.dumps(list(lock['cutadapt_packages']))], attempt, 'python-packages', env))
    assert {n: p['version'] for n, p in info['packages'].items()} == lock['cutadapt_packages'], 'Cutadapt dependency versions differ from the pinned profile'
    base = Path(info['base']).resolve(); venv = python.parent.parent.resolve()
    roots = [{'path': str(Path(p).resolve()), 'allow_absent': True} for p in perl_info['inc']]
    roots.extend([dict(path=str(base / 'Lib'), excluded_directories=['site-packages']), dict(path=str(base / 'DLLs')), dict(path=str(venv / 'pyvenv.cfg')), dict(path=str(python)), dict(path=str(base / 'python313.dll'))])
    if '3.13.' not in info['version']:
        raise RuntimeError('This prepared native Python profile requires Python 3.13')
    for item in info['packages'].values():
        for filename in item['files']:
            path = Path(filename).resolve()
            if not path.is_relative_to(venv):
                raise ValueError('Package record escapes the declared private Python environment')
            relative = path.relative_to(site) if path.is_relative_to(site) else None
            top = site / relative.parts[0] if relative is not None else path
            if not any(Path(r['path']).resolve() == top for r in roots):
                roots.append(dict(path=str(top)))
    wheel_origins = {}; wheel_files = {}
    for name, pin in lock['wheels'].items():
        wheel = archive(pin, args.cache.resolve(), args.allow_download)
        members = compare_archive(wheel, site, wheel=True); wheel_files.update(members)
        wheel_origins[name] = dict(archive_sha256=pin['sha256'], url=pin['url'], verified_members=len(members))
    files = inventory(roots)
    search = [perl.parent, python.parent, base, base / 'DLLs', base / 'Library/bin', Path(os.environ.get('SystemRoot', 'C:/Windows')) / 'System32']
    seeds = [perl, perl.parent / 'sh.exe', python, base / 'python313.dll']
    seeds.extend(Path(p) for p in files if p.lower().endswith(('.pyd', '.dll')))
    fixed = binary_dependencies(seeds, search); files.update(fixed)
    fastqc = None
    if args.fastqc_home is not None:
        qc = args.fastqc_home.resolve()
        java = args.java.resolve() if args.java else None
        if java is None or not java.is_file() or not (qc / 'uk/ac/babraham/FastQC/FastQCApplication.class').is_file():
            raise RuntimeError('Requested FastQC requires its original compiled distribution and an explicit --java executable')
        jars = ['htsjdk.jar', 'commons-io-2.22.0.jar', 'commons-compress-1.28.0.jar', 'cisd-jhdf5.jar']
        if not all((qc / p).is_file() for p in jars):
            raise RuntimeError('FastQC dependency JAR is absent')
        classpath = os.pathsep.join(map(str, [qc, *[qc / p for p in jars]]))
        value = execute([java, '-Djava.awt.headless=true', '-Dfastqc.show_version=true', '-cp', classpath, 'uk.ac.babraham.FastQC.FastQCApplication'], attempt, 'fastqc-version', env)
        if value.strip() != 'FastQC v0.13.0':
            raise RuntimeError('FastQC version differs from 0.13.0')
        java_home = java.parent.parent
        added = [dict(path=str(qc)), *[dict(path=str(java_home / p)) for p in ['bin', 'lib', 'conf', 'release']]]
        roots.extend(added); files.update(inventory(added))
        upstream = archive(lock['fastqc_archive'], args.cache.resolve(), args.allow_download, ROOT / 'build/omics-fastqc-download/fastqc_v0.13.0.zip')
        members = compare_archive(upstream, qc, prefix='FastQC/')
        fastqc = dict(home=str(qc), java=str(java), version='0.13.0', jars=jars, archive_sha256=lock['fastqc_archive']['sha256'], verified_original_members=len(members))
    if not all(files.get(path) == value for path, value in wheel_files.items()):
        raise RuntimeError('Authenticated original Python files are absent from the sealed runtime inventory')
    return dict(perl=str(perl), perl_version=perl_info['version'], shell=str(perl.parent / 'sh.exe'), python=str(python), python_site=str(site), python_base=str(base), python_version=info['version'], package_versions=lock['cutadapt_packages'], fastqc=fastqc,
                original_wheels=wheel_origins, original_wheel_files=wheel_files,
                external_roots=roots, external_fixed_files=sorted(fixed), external_files=dict(sorted(files.items())), cache_policy='Python runs isolated without site startup; private pycache prefix plus -B ignores existing caches. Only explicit original package/metadata trees and base stdlib/native dependencies are referenced. Windows system DLLs remain the operating-system boundary.')


def install(args):
    home, report = args.environment.resolve(), args.report.resolve()
    attempts = report / 'attempts'; attempts.mkdir(parents=True, exist_ok=True)
    attempt = Path(tempfile.mkdtemp(prefix=dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ-'), dir=attempts))
    lock = json.loads(LOCK.read_text(encoding='utf-8')); stage = None
    record = dict(profile=lock['profile'], environment=str(home), attempt=str(attempt), install_authorized=args.allow_install, download_authorized=args.allow_download, recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat())
    try:
        if home.exists():
            if not verified(home, lock):
                raise RuntimeError('Existing unmanaged, incomplete or changed runtime preserved; choose a new --environment after correcting the dependency')
            record.update(status='available-not-yet-analysis-validated', runtime_reused=True, sealed_external_files=len(json.loads((home / MARKER).read_text())['external_files']))
        else:
            if not args.allow_install:
                record.update(status='skipped-install-not-permitted', error='Explicit --allow-install is required for a new private profile')
            elif sys.platform != 'win32' or platform.machine().lower() not in ['amd64', 'x86_64']:
                record.update(status='skipped-platform-unavailable', error='This prepared profile requires Windows x86_64, Cygwin Perl and native Python; use a separately validated native profile on other hosts')
            else:
                if getattr(args, 'prepare_python', False):
                    args.python = prepare_python(args, attempt, lock)
                    record['prepared_python_environment'] = str(args.python_environment.resolve())
                dependencies = discover(args, attempt, lock)
                home.parent.mkdir(parents=True, exist_ok=True)
                stage = Path(tempfile.mkdtemp(prefix='.jlinalg-trimgalore-', dir=home.parent)); record['retained_staging'] = str(stage)
                files = {}
                for version, profile in lock['profiles'].items():
                    (stage / version).mkdir()
                    for name, expected in profile['files'].items():
                        origin = source(profile, version, name, args.cache.resolve(), args.allow_download, args.source_cache.resolve() if args.source_cache else None)
                        shutil.copy2(origin, stage / version / name); files[version + '/' + name] = expected
                    value = execute([dependencies['perl'], str(stage / version / 'trim_galore').replace('\\', '/'), '--version'], attempt, 'original-version-' + version, environment(args.perl.resolve(), args.python.resolve()))
                    if 'version ' + version not in value:
                        raise RuntimeError('Actual original Trim Galore version differs')
                marker = dict(schema_version=1, profile=lock['profile'], lock_sha256=digest(LOCK), files=files, **dependencies)
                (stage / MARKER).write_text(json.dumps(marker, indent=2) + '\n', encoding='utf-8')
                if not verified(stage, lock):
                    raise RuntimeError('New private profile failed exact inventory/integrity verification')
                stage.rename(home); record.pop('retained_staging', None)
                record.update(status='installed-not-yet-analysis-validated', runtime_reused=False, sealed_external_files=len(dependencies['external_files']))
    except Exception as failure:
        record.update(status='skipped-dependency-unavailable', error=str(failure))
    record['required_action'] = ('Skip affected upstream analysis while unavailable. Where permitted run python tools/literature/install_trim_galore.py --allow-install --allow-download '
        '--perl C:/cygwin64/bin/perl.exe --python build/omics-sequence-tools-env/Scripts/python.exe '
        '[--fastqc-home build/omics-fastqc/FastQC --java PATH_TO_JAVA]. '
        'For a fresh pinned private Python environment, add --prepare-python --python-base PATH_TO_PERMITTED_PYTHON313 --python-environment NEW_PRIVATE_DIR;the existing interpreter/global packages are not modified. '
        'Missing Cutadapt dependencies: use install_cell_dependencies.py --profile sequence --allow-install where allowed; '
        'missing FastQC: use install_fastqc.py --allow-download. An existing permitted Cygwin Perl/sh is required; no global Perl install is attempted. '
        'Preserve changed homes, source caches and failed staging/logs; correct the named cause and select a new private --environment. '
        'Original version probes and dependency availability do not establish scientific analysis validity.')
    record['scope'] = 'Pinned unmodified legacy0.6.7/0.6.10 original scripts and GPL notices; references existing permitted dependencies read-only. No OS/global install or persistent PATH changes; no Rust rewrite or historical Cutadapt substitution.'
    destination = report / 'dependency-status.json'
    if destination.exists():
        previous = json.loads(destination.read_text()); record['history'] = previous.get('history', []) + [{k: v for k, v in previous.items() if k != 'history'}]
    for target in [destination, attempt / 'dependency-status.json']:
        target.write_text(json.dumps(record, indent=2) + '\n', encoding='utf-8')
    skipped = record['status'].startswith('skipped')
    warning = ('# ANALYSIS SKIPPED - TRIM GALORE DEPENDENCY UNAVAILABLE\n\n' + record.get('error', '') if skipped else '# Original Trim Galore dependency profile available; scientific validation is separate')
    (report / 'WARNINGS.md').write_text(warning + '\n\nRequired action: ' + record['required_action'] + '\n\n' + record['scope'] + '\n', encoding='utf-8')
    print(json.dumps(record)); return 3 if skipped else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--environment', type=Path, default=ROOT / 'build/omics-trim-galore')
    parser.add_argument('--report', type=Path, default=ROOT / 'build/literature-review/trimgalore-runtime/dependency')
    parser.add_argument('--cache', type=Path, default=ROOT / 'build/literature-review/trimgalore-runtime/downloads')
    parser.add_argument('--source-cache', type=Path)
    parser.add_argument('--perl', type=Path, default=Path('C:/cygwin64/bin/perl.exe'))
    parser.add_argument('--python', type=Path, default=ROOT / 'build/omics-sequence-tools-env/Scripts/python.exe')
    parser.add_argument('--prepare-python', action='store_true')
    parser.add_argument('--python-base', type=Path)
    parser.add_argument('--python-environment', type=Path, default=ROOT / 'build/omics-trim-galore-python')
    parser.add_argument('--fastqc-home', type=Path)
    parser.add_argument('--java', type=Path)
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    return install(parser.parse_args())


if __name__ == '__main__':
    raise SystemExit(main())
