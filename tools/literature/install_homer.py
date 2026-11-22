#!/usr/bin/env python3
"""Admit an original private HOMER core after actual scan/enrichment/discovery health.

Installation and download permissions are separate. Existing homes, archives,
failed reports and partial downloads are preserved. No OS package is installed.
"""
import argparse
from collections import Counter
import datetime as dt
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import random
import re
import shutil
import signal
import stat
import subprocess
import sys
import urllib.request
import zipfile

HERE = Path(__file__).resolve().parent
LOCK_RAW = (HERE / 'homer-runtime-lock.json').read_bytes()
LOCK = json.loads(LOCK_RAW)
LOCK_SHA = hashlib.sha256(LOCK_RAW).hexdigest()
MARKER = '.jlinalg-homer.json'
HEALTH = 'passed-original-fixed-scan-known-two-statistics-and-discovery-health'
RECOVERY = ('Required action: use Linux x86_64 Python for setup and Linux Java for analysis; on Windows use WSL. '
    'Where permitted, run python3 tools/literature/install_homer.py --profile linux-x86_64-5.1 '
    '--home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT --allow-install. '
    'Choose linux-x86_64-4.11 or linux-x86_64-4.11.1 explicitly for those releases. '
    'Use --archive-dir OFFLINE_ARCHIVE_DIRECTORY, or add --allow-download only where download is permitted. '
    'Official archives use HTTP with recorded content pins, not independently authenticated signatures. '
    'If installation or execution is forbidden, dependencies are absent, unsupported or changed, SKIP the affected '
    'upstream analysis and retain the named cause. Preserve old homes, corrupt caches and failed reports; '
    'correct the cause and use new paths. Analysis never installs. The core profile does not admit Perl/genome, '
    'annotation, peak, coverage or RNA wrappers. Native homer-scan is a separate supplied-matrix method, '
    'not a substitute for those workflows. Cell Ranger remains excluded.')

class Unavailable(Exception):
    pass

def sha(path):
    with Path(path).open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()

def write(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')

def private(path):
    path = Path(path).absolute()
    if any(p.is_symlink() for p in [path, *path.parents]):
        raise ValueError('Linked path/ancestor is unsupported: ' + str(path))
    return path.resolve()

def host():
    if platform.system() != 'Linux' or platform.machine().lower() not in {'x86_64', 'amd64'}:
        raise Unavailable('Original HOMER profiles need Linux x86_64; use Linux Python/Java inside WSL on Windows')
    return dict(os='Linux', architecture='x86_64', kernel=platform.release())

def env(work):
    return dict(PATH='/usr/bin:/bin', HOME=str(work), TMPDIR=str(work), LANG='C', LC_ALL='C', OMP_NUM_THREADS='1')

def run(command, work, label, seconds=90, maximum=33554432):
    out, err = work / (label + '.stdout'), work / (label + '.stderr')
    with out.open('xb') as stdout, err.open('xb') as stderr:
        child = subprocess.Popen(command, cwd=work, env=env(work), stdout=stdout, stderr=stderr, start_new_session=True)
        try:
            deadline = __import__('time').monotonic() + seconds
            while child.poll() is None:
                if sum(p.stat().st_size for p in work.rglob('*') if p.is_file()) > maximum:
                    raise ValueError('Original health output byte bound exceeded')
                if __import__('time').monotonic() > deadline:
                    raise Unavailable('Actual original health exceeded its timeout; inspect retained diagnostics')
                __import__('time').sleep(.05)
            code = child.returncode
        except BaseException:
            try:
                os.killpg(child.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            child.wait(timeout=10)
            raise
    if code:
        raise Unavailable(f'Original health failed (exit {code}): inspect {out} and {err}')
    return dict(command=command, exit_code=code, stdout_sha256=sha(out), stderr_sha256=sha(err))

def closure(home, pin, report):
    program = Path('/usr/bin/ldd')
    if not program.is_file():
        raise Unavailable('ldd is needed to admit the actual dynamic-library closure')
    call = run([str(program), str(home / 'bin/homer2')], report, 'ldd', 30)
    text = (report / 'ldd.stdout').read_text()
    if 'not found' in text:
        raise Unavailable('An original HOMER dynamic library is absent; inspect ldd.stdout')
    paths = set()
    for line in text.splitlines():
        if 'linux-vdso' in line:
            continue
        match = re.search(r'(?:=>\s*)?(/\S+)\s+\(', line)
        if not match:
            raise Unavailable('Unexpected HOMER dynamic-library binding: ' + line)
        paths.add(match.group(1))
    if {Path(p).name for p in paths} != set(pin['external_library_names']) or len(paths) != len(pin['external_library_names']):
        raise Unavailable('Original HOMER dynamic-library family differs from the pinned reference')
    external = {}
    for name in sorted(paths | {str(program)}):
        path = Path(name)
        real = path.resolve(strict=True)
        if not real.is_file():
            raise Unavailable('Missing host runtime file: ' + name)
        external[name] = dict(real_path=str(real), bytes=real.stat().st_size, sha256=sha(real))
    return dict(external_files=external, binary='bin/homer2', external_libraries=sorted(paths), ldd=call,
        host_boundary='Linux kernel, CPU, OS loader semantics and Python setup runtime remain host dependencies. No global installation or OS settings are changed.')

def verify_home(home, profile, expected_host):
    marker = home / MARKER
    if not home.is_dir() or home.is_symlink() or not marker.is_file() or marker.is_symlink() or marker.stat().st_size > 1048576:
        raise Unavailable('Existing HOMER home is absent, linked or unmanaged; preserve it and use a new home')
    record = json.loads(marker.read_text(encoding='utf-8'))
    pin = LOCK['profiles'][profile]
    if record.get('schema_version') != 1 or record.get('tool') != 'HOMER' or record.get('profile') != profile or record.get('version') != pin['version'] or record.get('lock_sha256') != LOCK_SHA or record.get('host') != expected_host or record.get('health', {}).get('status') != HEALTH:
        raise Unavailable('HOMER version, profile, lock, host/kernel or actual scientific health differs')
    expected = set(pin['members']) | {MARKER}
    found = set()
    for path in home.rglob('*'):
        if path.is_symlink() or not (path.is_file() or path.is_dir()):
            raise Unavailable('Linked/nonregular runtime member')
        if path.is_file():
            found.add(path.relative_to(home).as_posix())
        if len(found) > 16:
            raise Unavailable('Unexpected HOMER runtime file count')
    if found != expected or set(record.get('files', {})) != set(pin['members']):
        raise Unavailable('HOMER runtime contains extra, missing or unmanaged files')
    for name, entry in pin['members'].items():
        path = home / name
        if path.stat().st_size != entry['bytes'] or sha(path) != entry['sha256'] or record['files'][name] != entry['sha256'] or name.startswith('bin/') and not os.access(path, os.X_OK):
            raise Unavailable('HOMER member changed or lacks executable permission: ' + name)
    external = record.get('closure', {}).get('external_files', {})
    if {Path(name).name for name in external} != set(pin['external_library_names']) | {'ldd'} or len(external) != len(pin['external_library_names']) + 1:
        raise Unavailable('HOMER host-library closure is incomplete')
    for name, entry in external.items():
        path = Path(name)
        real = path.resolve(strict=True)
        if not path.is_absolute() or not real.is_file() or str(real) != entry['real_path'] or real.stat().st_size != entry['bytes'] or sha(real) != entry['sha256']:
            raise Unavailable('HOMER host dependency changed: ' + name)
    return record

def obtain(name, cache, references, download):
    pin = LOCK['archives'][name]
    def check(path):
        if path.is_symlink() or not path.is_file() or path.stat().st_size != pin['bytes'] or sha(path) != pin['sha256']:
            raise ValueError('Corrupt/linked pinned archive; preserve it and use a new cache or source: ' + str(path))
    path = cache / name
    if path.exists():
        check(path)
        return path
    for directory in references:
        source = directory / name
        if source.exists():
            check(source)
            with source.open('rb') as src, path.open('xb') as dst:
                shutil.copyfileobj(src, dst)
            check(path)
            return path
    if not download:
        raise Unavailable('Missing pinned original archive and --allow-download absent: ' + name)
    partial = cache / (name + '.part')
    if partial.exists():
        raise ValueError('Retained partial download exists; use a new cache: ' + str(partial))
    request = urllib.request.Request(pin['url'], headers={'User-Agent': 'JLinAlg-private-HOMER-runtime/1.0'})
    with urllib.request.urlopen(request, timeout=60) as response, partial.open('xb') as dst:
        total = 0
        while chunk := response.read(1048576):
            total += len(chunk)
            if total > pin['bytes']:
                raise ValueError('Pinned HOMER archive byte bound exceeded')
            dst.write(chunk)
    check(partial)
    partial.rename(path)
    return path

def extract(archive, pin, home):
    with zipfile.ZipFile(archive) as source:
        for dest, entry in pin['members'].items():
            found = [member for member in source.infolist() if member.filename == entry['member']]
            if len(found) != 1:
                raise ValueError('Missing/duplicate HOMER archive member: ' + entry['member'])
            member = found[0]
            if member.is_dir() or stat.S_ISLNK(member.external_attr >> 16) or member.file_size != entry['bytes']:
                raise ValueError('Invalid HOMER archive member type/size')
            path = home / dest
            path.parent.mkdir(parents=True, exist_ok=True)
            with source.open(member) as src, path.open('xb') as dst:
                shutil.copyfileobj(src, dst)
            if sha(path) != entry['sha256']:
                raise ValueError('HOMER extracted member checksum differs')
            path.chmod(0o755 if dest.startswith('bin/') else 0o644)

def health(home, report):
    work = report / 'health'
    work.mkdir()
    rng = random.Random(20261028)
    sequences = {}
    motif = 'ACGTAC'
    reverse = 'GTACGT'
    for role in ['targets', 'background']:
        rows = []
        for ordinal in range(1, 41):
            sequence = ''.join(rng.choice('ACGT') for _ in range(120))
            if role == 'targets':
                sequence = sequence[:50] + motif + sequence[56:]
            rows.append((role + '_' + str(ordinal), sequence))
        sequences[role] = rows
        (work / (role + '.fa')).write_text(''.join(f'>{name}\n{sequence}\n' for name, sequence in rows), encoding='ascii')
    (work / 'motifs.motif').write_text('>ACGTAC\tfixture-motif\t5.0\t0\n' + ''.join('\t'.join('.97' if b == base else '.01' for b in 'ACGT') + '\n' for base in motif), encoding='ascii')
    executable = str(home / 'bin/homer2')
    calls = [run([executable, 'find', '-i', 'targets.fa', '-m', 'motifs.motif', '-offset', '0', '-strand', 'both', '-p', '1'], work, 'find')]
    expected = Counter()
    hits_by_role = {}
    for role, rows in sequences.items():
        present = 0
        for name, sequence in rows:
            sites = []
            for pos in range(len(sequence) - 5):
                word = sequence[pos:pos+6]
                if word == motif:
                    sites.append((name, pos, word, 'fixture-motif', '+'))
                if word == reverse:
                    sites.append((name, pos+5, word, 'fixture-motif', '-'))
            present += bool(sites)
            if role == 'targets':
                expected.update(sites)
        hits_by_role[role] = present
    actual = Counter()
    for line in (work / 'find.stdout').read_text().splitlines():
        row = line.split('\t')
        if len(row) != 6 or abs(float(row[5]) - 6*math.log(.97/.25)) > .000000501:
            raise ValueError('Original health motif score/schema differs')
        actual[(row[0], int(row[1]), row[2], row[3], row[4])] += 1
    if actual != expected:
        raise ValueError('Original health complete strand/coordinate/site set differs from independent sequence matching')
    t, b = hits_by_role['targets'], hits_by_role['background']
    for statistic in ['hypergeo', 'binomial']:
        calls.append(run([executable, 'known', '-i', 'targets.fa', '-b', 'background.fa', '-m', 'motifs.motif', '-stat', statistic, '-nlen', '0', '-strand', 'both', '-p', '1', '-cache', '8'], work, statistic))
        lines = (work / (statistic + '.stdout')).read_text().splitlines()
        row = lines[1].split('\t') if len(lines) == 2 else []
        if len(row) != 9 or int(row[5]) != t or int(row[7]) != b or '(total=40)' not in lines[0]:
            raise ValueError('Original known health presence counts/populations/schema differ')
        if statistic == 'hypergeo':
            probability = sum(math.comb(40, k)*math.comb(40, t+b-k) for k in range(t, min(40, t+b)+1)) / math.comb(80, t+b)
        else:
            rate = max(1/40, min(39/40, b/40))
            probability = sum(math.comb(40, k)*rate**k*(1-rate)**(40-k) for k in range(t, 41))
        logp = math.log(probability)
        ratio = math.log2((max(1,t)/40)/(max(1,b)/40))
        if abs(float(row[3])-logp) > .000501 or row[2] != '1e'+str(int(logp/2.3026)) or abs(float(row[4])-ratio) > .000501:
            raise ValueError('Original known health statistic or coarse display differs from independent arithmetic')
    calls.append(run([executable, 'denovo', '-i', 'targets.fa', '-b', 'background.fa', '-len', '6', '-S', '1', '-mis', '1', '-p', '1', '-cache', '8'], work, 'denovo'))
    lines = (work / 'denovo.stdout').read_text().splitlines()
    header = lines[0].split('\t') if lines else []
    if len(lines) != 7 or len(header) < 6 or header[0] not in {'>ACGTAC', '>GTACGT'} or not math.isfinite(float(header[2])) or not math.isfinite(float(header[3])) or 'T:40.0(100.00%)' not in header[5]:
        raise ValueError('Original discovery health did not recover the implanted six-base motif with complete model metadata')
    for line in lines[1:]:
        row = [float(v) for v in line.split('\t')]
        if len(row) != 4 or any(not math.isfinite(v) or v < 0 or v > 1 for v in row) or abs(sum(row)-1) > .002000001:
            raise ValueError('Original discovery health probability row is invalid')
    return dict(status=HEALTH, targets=40, background=40, fixed_motif_hits=sum(actual.values()), target_with_motif=t, background_with_motif=b, calls=calls,
        files={p.name:sha(p) for p in work.iterdir() if p.is_file()},
        scope='Actual same-binary scan and two unweighted fixed-known upper tails checked independently; discovery health recovers an implanted motif. Printed discovery model is rounded and is not used to replace its original internal-precision statistics. No genome/wrapper/full-paper or biological-accuracy certification.')

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', choices=sorted(LOCK['profiles']), required=True)
    for key in ['home', 'cache', 'report']:
        parser.add_argument('--'+key, type=Path, required=True)
    parser.add_argument('--archive-dir', type=Path, action='append', default=[])
    parser.add_argument('--allow-install', action='store_true')
    parser.add_argument('--allow-download', action='store_true')
    args = parser.parse_args(argv)
    record = dict(status='starting', profile=args.profile, lock_sha256=LOCK_SHA, permissions=dict(install=args.allow_install, download=args.allow_download), started_utc=dt.datetime.now(dt.timezone.utc).isoformat(), required_action=RECOVERY)
    report, owns = None, False
    try:
        home, cache, report = [private(p) for p in [args.home, args.cache, args.report]]
        references = [private(p) for p in args.archive_dir]
        if any(a == b or a in b.parents or b in a.parents for a,b in [(home,cache),(home,report),(cache,report)]):
            raise ValueError('Home, cache and report must be distinct non-nested directories')
        if report.exists():
            raise ValueError('Report exists; preserve it and choose a new report')
        report.mkdir(parents=True)
        owns = True
        actual_host = host()
        record.update(host=actual_host, home=str(home))
        if home.exists():
            meta = verify_home(home, args.profile, actual_host)
            record.update(status='reused-verified-admitted-runtime', health=meta['health'], runtime_marker_sha256=sha(home/MARKER))
        else:
            if not args.allow_install:
                raise Unavailable('Installation is not permitted: --allow-install absent; no runtime home was created')
            cache.mkdir(parents=True, exist_ok=True)
            pin = LOCK['profiles'][args.profile]
            name = next(iter({v['archive'] for v in pin['members'].values()}))
            archive = obtain(name, cache, references, args.allow_download)
            home.mkdir(parents=True, mode=0o700)
            extract(archive, pin, home)
            dependencies = closure(home, pin, report)
            passed = health(home, report)
            meta = dict(schema_version=1, tool='HOMER', profile=args.profile, version=pin['version'], lock_sha256=LOCK_SHA, host=actual_host, files={name:v['sha256'] for name,v in pin['members'].items()}, closure=dependencies, health=passed, transport_warning=LOCK['transport_warning'], global_host_configuration_changed=False)
            write(home/MARKER, meta)
            verify_home(home, args.profile, actual_host)
            record.update(status='installed-and-admitted-original-runtime', health=passed, runtime_marker_sha256=sha(home/MARKER))
        code = 0
    except Unavailable as error:
        record.update(status='skipped-unavailable-dependency', error=str(error))
        code = 3
    except Exception as error:
        record.update(status='failed-setup-retained', error=repr(error))
        code = 2
    record['exit_code'] = code
    if owns:
        write(report/'installation.json', record)
        (report/'WARNINGS.md').write_text('# HOMER '+('SETUP WARNINGS REQUIRE REVIEW' if code==0 else 'SETUP SKIPPED' if code==3 else 'SETUP FAILED')+'\n\n'+record.get('error','Original core admitted after actual scientific health. '+LOCK['transport_warning'])+'\n\n'+RECOVERY+'\n', encoding='utf-8')
    print(json.dumps({key:record[key] for key in ['status','exit_code','profile','lock_sha256']})+(' '+record['error'] if 'error' in record else ''), flush=True)
    if code:
        print(RECOVERY, file=sys.stderr)
    return code

if __name__ == '__main__':
    raise SystemExit(main())
