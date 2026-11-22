#!/usr/bin/env python3
"""Explicit private installation of the original HOMER genomic motif workflow.

The extraction helper is built from the pinned upstream release because its
compiled home path is installation-specific. No algorithmic source is patched.
The original prebuilt motif scorer and interval merger remain byte-identical.
"""
import argparse
import base64
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import random
import re
import signal
import stat
import subprocess
import sys
import time
import zipfile
import install_homer as core

HERE = Path(__file__).resolve().parent
RAW = (HERE / 'homer-genome-lock.json').read_bytes()
LOCK = json.loads(RAW)
LOCK_SHA = hashlib.sha256(RAW).hexdigest()
MARKER = '.jlinalg-homer-genome.json'
HEALTH = 'passed-original-genomic-known-denovo-comparison-health'
UTILITIES = {'perl', 'env', 'rm', 'mv', 'cat', 'cp', 'cut', 'ls', 'mkdir', 'wc', 'sleep', 'sort', 'head', 'tail', 'grep', 'tr', 'ldd'}
RECOVERY = ('Required action: use Linux x86_64 Python and Java, inside WSL on Windows. '
    'Where permitted run python3 tools/literature/install_homer_genome.py '
    '--profile linux-x86_64-genome-5.1 --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE '
    '--report NEW_REPORT --allow-install --compiler /PATH/TO/g++ --make /PATH/TO/make. '
    'Select genome-4.11 or genome-4.11.1 explicitly when needed. Supply --archive-dir '
    'VERIFIED_OFFLINE_ARCHIVES or add --allow-download where permitted. The compiler '
    'needs C++11 headers, pthread, math and static C++/GCC runtime libraries. If unavailable '
    'or installation is forbidden, SKIP the affected analysis, exit3. Preserve failed homes '
    'and reports; correct the named cause and use NEW paths. No OS packages or genome '
    'data are installed. Analysis never installs or substitutes versions. Core and annotation '
    'homes do not admit the genomic workflow. Cell Ranger remains excluded.')


def prefix(home):
    return ('#!/usr/bin/env perl\nuse warnings;\nuse lib "' + str(home) + '/bin";\nmy $homeDir = "' + str(home) + '/";\n').encode()


def environment(home, work):
    value = core.env(work)
    value.update(PATH=str(home / 'bin') + ':/usr/bin:/bin', PERL_HASH_SEED='0', PERL_PERTURB_KEYS='0')
    return value


def execute(command, home, work, label, seconds=180, maximum=1073741824, build_env=None):
    out, err = work / (label + '.stdout'), work / (label + '.stderr')
    with out.open('xb') as stdout, err.open('xb') as stderr:
        child = subprocess.Popen(command, cwd=work, env=build_env or environment(home, work),
                                 stdout=stdout, stderr=stderr, start_new_session=True)
        try:
            deadline = time.monotonic() + seconds
            while child.poll() is None:
                total = 0
                for path in work.rglob('*'):
                    try:
                        if path.is_file():
                            total += path.stat().st_size
                    except FileNotFoundError:
                        continue  # Original Perl helpers delete their temporary files.
                if total > maximum:
                    raise ValueError('Original genomic setup workspace capacity exceeded')
                if time.monotonic() > deadline:
                    raise core.Unavailable('Original genomic setup timed out; inspect ' + str(err))
                time.sleep(.1)
            code = child.returncode
        except BaseException:
            try:
                os.killpg(child.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            child.wait(timeout=10)
            raise
    if code:
        raise core.Unavailable('Original genomic setup command exited ' + str(code) + '; inspect ' + str(err))
    return dict(command=command, exit_code=code, stdout_sha256=core.sha(out), stderr_sha256=core.sha(err))


def member_bytes(archive, pin):
    found = [i for i in archive.infolist() if i.filename == pin['member']]
    if len(found) != 1 or found[0].is_dir() or stat.S_ISLNK(found[0].external_attr >> 16) or found[0].file_size != pin['bytes']:
        raise ValueError('Invalid pinned original genomic archive member: ' + pin['member'])
    raw = archive.read(found[0])
    if hashlib.sha256(raw).hexdigest() != pin['sha256']:
        raise ValueError('Original genomic archive member checksum differs: ' + pin['member'])
    return raw


def installed(home, name, pin):
    path = home / name
    if path.is_symlink() or not path.is_file():
        raise core.Unavailable('Missing/linked genomic runtime member: ' + name)
    raw = path.read_bytes()
    if pin['relocate']:
        expected = prefix(home)
        if not raw.startswith(expected):
            raise core.Unavailable('Original genomic installation prefix changed: ' + name)
        raw = base64.b64decode(pin['original_prefix_base64']) + raw[len(expected):]
    if len(raw) != pin['bytes'] or hashlib.sha256(raw).hexdigest() != pin['sha256']:
        raise core.Unavailable('Original genomic scientific body changed: ' + name)
    if name.startswith('bin/') and not os.access(path, os.X_OK):
        raise core.Unavailable('Genomic helper is not executable: ' + name)
    return core.sha(path)


def build_extractor(archive, home, pin, report, compiler, make):
    for path in [compiler, make]:
        if not re.fullmatch(r'/[A-Za-z0-9_./+-]+', str(path)) or not path.is_file() or not os.access(path, os.X_OK):
            raise core.Unavailable('Missing/non-executable or unsupported compiler/make path: ' + str(path))
    tools = {str(p): dict(real_path=str(p.resolve()), sha256=core.sha(p.resolve())) for p in [compiler, make]}
    work = report / 'source'; work.mkdir()
    for name, p in pin['build_sources'].items():
        raw = member_bytes(archive, p)
        if name == 'cpp/SeqTag.cpp':
            lines = raw.splitlines(keepends=True)
            if lines[2] != base64.b64decode(p['original_home_line_base64']):
                raise ValueError('Original C++ installation configuration differs')
            lines[2] = ('const char* HomerConfig::homeDirectory = "' + str(home) + '/";\n').encode()
            raw = b''.join(lines)
        (work / Path(name).name).write_bytes(raw)
    env = core.env(work); env['PATH'] = str(compiler.parent) + ':' + str(make.parent) + ':/usr/bin:/bin'
    recipe = LOCK['build_recipe']
    version = execute([str(compiler), '--version'], home, work, 'compiler-version', build_env=env)
    make_version = execute([str(make), '--version'], home, work, 'make-version', build_env=env)
    compile_call = execute([str(make), '-B', 'COMPILER=' + str(compiler), 'DEBUG=' + recipe['compile_flags'],
                            *recipe['objects']], home, work, 'compile', seconds=1200, build_env=env)
    link = execute([str(compiler), *[str(work / p) for p in recipe['objects']], *recipe['link_flags'],
                    '-o', str(home / 'bin/homerTools')], home, work, 'link', build_env=env)
    for name, value in tools.items():
        if core.sha(Path(name).resolve()) != value['sha256']:
            raise ValueError('Compiler/make changed during build')
    return dict(status='built-original-extractor-installation-configuration-only', recipe=recipe,
                original_sources={n: p['sha256'] for n, p in pin['build_sources'].items()},
                installed_sources={p.name: core.sha(p) for p in work.iterdir() if p.suffix in {'.cpp', '.h'} or p.name == 'Makefile'},
                tool_executables=tools, compiler_version=version, make_version=make_version,
                compile=compile_call, link=link, binary_sha256=core.sha(home / 'bin/homerTools'),
                note='Tool executable identity and original/configured sources retained; build toolchain/sysroot is not a byte-reproducibility certificate.')


def external_files(home, report):
    code = ('use POSIX; use HomerConfig; use Statistics; use HomerSVGLogo; '
            'POSIX::floor(1.2); HomerConfig::loadConfigFile(); '
            'print "F\\t$_\\n" for values %INC; print "F\\t$_\\n" for @DynaLoader::dl_shared_objects;')
    execute(['/usr/bin/perl', '-I' + str(home / 'bin'), '-e', code], home, report, 'perl-modules')
    paths = {Path('/usr/bin') / name for name in UTILITIES} | {Path('/bin/sh')}
    for line in (report / 'perl-modules.stdout').read_text().splitlines():
        if not line.startswith('F\t'):
            raise ValueError('Unexpected genomic Perl closure output')
        path = Path(line[2:])
        if path.parent == home / 'bin':
            continue
        if not path.is_absolute():
            raise core.Unavailable('Relative external genomic Perl binding')
        paths.add(path)
    initial = sorted(paths - {Path('/usr/bin/ldd')}) + [home / 'bin' / name for name in ['homerTools', 'homer2', 'mergePeaks']]
    for i, path in enumerate(initial):
        if path.suffix in {'.pm', '.pl', '.al'}:
            continue
        execute(['/usr/bin/ldd', str(path)], home, report, 'ldd-' + str(i))
        output = (report / ('ldd-' + str(i) + '.stdout')).read_text()
        if 'not found' in output:
            raise core.Unavailable('Missing original genomic shared library')
        if path == home / 'bin/homerTools' and ('libstdc++' in output or 'libgcc_s' in output):
            raise core.Unavailable('Extractor still depends on a private dynamic C++/GCC runtime')
        for line in output.splitlines():
            paths.update(Path(p) for p in re.findall(r'(?:=>\s+|^\s*)(/[^\s]+)', line))
    if len(paths) > 256:
        raise ValueError('Genomic external dependency inventory capacity exceeded')
    result = {}
    for path in sorted(paths):
        real = path.resolve(strict=True)
        if not str(path).startswith(('/usr/', '/lib/', '/lib64/', '/bin/')) or not real.is_file():
            raise core.Unavailable('Unsupported external genomic binding: ' + str(path))
        result[str(path)] = dict(real_path=str(real), bytes=real.stat().st_size, sha256=core.sha(real))
    return result


def health(home, report, version):
    work = report / 'health'; work.mkdir()
    rng = random.Random(20261028)
    dna = list(''.join(rng.choice('ACGT') for _ in range(200000)))
    for role, count, first in [('targets', 64, 1000), ('background', 128, 80000)]:
        rows = []
        for i in range(count):
            start = first + i * 500; gc = [.25, .4, .5, .6, .75][i % 5]
            seq = ''.join(rng.choice('GC') if rng.random() < gc else rng.choice('AT') for _ in range(120))
            if role == 'targets' and i < 48:
                seq = seq[:35] + 'ACGTACGA' + seq[43:]
            dna[start:start + 120] = seq
            rows.append(f'chr1\t{start}\t{start+120}\t{"T" if role=="targets" else "B"}{i+1:04d}\t1\t{"-" if i%3==0 else "+"}\n')
        (work / (role + '.bed')).write_text(''.join(rows))
    (work / 'genome.fa').write_text('>chr1 synthetic\n' + ''.join(''.join(dna[i:i + 80]) + '\n' for i in range(0, len(dna), 80)))
    (work / 'models.motif').write_text(''.join('>' + seq + '\t' + name + '\t10\n' + ''.join('\t'.join('.97' if b == base else '.01' for b in 'ACGT') + '\n' for base in seq) for seq, name in [('ACGTACGA', 'implanted'), ('CCCCCCCC', 'polyC')]))
    args = ['/usr/bin/perl', str(home / 'bin/findMotifsGenome.pl'), 'targets.bed', 'genome.fa', 'original-out',
            '-size', 'given', '-bg', 'background.bed', '-len', '8', '-S', '1', '-mis', '1',
            '-mknown', 'models.motif', '-mcheck', 'models.motif', '-gc', '-nlen', '2', '-cache', '8',
            '-p', '1', '-nofacts', '-keepFiles', '-dumpFasta', '-preparsedDir', 'preparsed']
    if version == '5.1':
        args.append('-useOldBg')
    call = execute(args, home, work, 'original')
    directory = work / 'original-out'
    for name in ['seq.tsv', 'group.adj', 'knownResults.txt', 'knownResults.html', 'homerMotifs.motifs8', 'homerResults.html']:
        if not (directory / name).is_file() or (directory / name).stat().st_size == 0:
            raise core.Unavailable('Original genomic health omitted final output: ' + name)
    rows = (directory / 'knownResults.txt').read_text().splitlines()
    if len(rows) != 3 or rows[1].split('\t')[0] != 'implanted' or float(rows[1].split('\t')[5]) < 45 or float(rows[1].split('\t')[3]) > -100:
        raise core.Unavailable('Original genomic health did not recover known implanted motif')
    groups = [line.split('\t') for line in (directory / 'group.adj').read_text().splitlines()]
    if sum(row[1] == '1' for row in groups) != 64 or not any(row[1] == '0' for row in groups):
        raise core.Unavailable('Original genomic health analytic population differs')
    stderr = (work / 'original.stderr').read_text(errors='strict')
    if 'Job finished' not in stderr or any(s in stderr for s in ['HOMER not configured', 'not found', 'uninitialized', 'Illegal division', 'Could not open', 'Can\'t open']):
        raise core.Unavailable('Original genomic health did not finalize cleanly')
    if not list((directory / 'homerResults').glob('*.svg')):
        raise core.Unavailable('Original genomic health SVG comparison report missing')
    return dict(status=HEALTH, targets=64, supplied_background=128, known_motifs=2,
                implanted_target_count=float(rows[1].split('\t')[5]), known_logp=float(rows[1].split('\t')[3]),
                de_novo_and_comparison=True, call=call, report_sha256=core.sha(directory / 'knownResults.txt'))


def verify_home(home, profile, actual_host):
    if not re.fullmatch(r'/[A-Za-z0-9_./-]+', str(home)):
        raise core.Unavailable('Genomic private home needs a shell/Perl-safe ASCII path')
    marker = home / MARKER
    if marker.is_symlink() or not marker.is_file() or marker.stat().st_size > 1048576:
        raise core.Unavailable('Original genomic runtime marker absent/linked/oversized')
    meta = json.loads(marker.read_text()); pin = LOCK['profiles'][profile]
    if any(meta.get(k) != v for k, v in dict(schema_version=1, tool='HOMER genomic motifs', home=str(home),
            profile=profile, version=pin['version'], lock_sha256=LOCK_SHA, host=actual_host).items()):
        raise core.Unavailable('Genomic profile/version/home/host/lock changed')
    expected = {n: installed(home, n, p) for n, p in pin['members'].items()}
    if (home / 'config.txt').read_bytes() != LOCK['generated_config'].encode():
        raise core.Unavailable('Genomic named-resource configuration changed')
    binary = home / 'bin/homerTools'
    if binary.is_symlink() or not binary.is_file() or not os.access(binary, os.X_OK) or not 100000 <= binary.stat().st_size <= 100000000:
        raise core.Unavailable('Source-built original genomic extractor missing/linked/nonexecutable/oversized')
    expected.update({'config.txt': core.sha(home / 'config.txt'), 'bin/homerTools': core.sha(binary)})
    build = meta.get('build', {})
    if build.get('status') != 'built-original-extractor-installation-configuration-only' or build.get('binary_sha256') != expected['bin/homerTools'] or build.get('recipe') != LOCK['build_recipe'] or build.get('original_sources') != {n: p['sha256'] for n, p in pin['build_sources'].items()} or any(build.get(k, {}).get('exit_code') != 0 for k in ['compile', 'link']):
        raise core.Unavailable('Original genomic extractor build evidence differs')
    found = set()
    for path in home.rglob('*'):
        if path.is_symlink() or not (path.is_file() or path.is_dir()):
            raise core.Unavailable('Linked/nonregular genomic home member')
        if path.is_file():
            found.add(path.relative_to(home).as_posix())
    if found != set(expected) | {MARKER} or meta.get('files') != expected:
        raise core.Unavailable('Genomic home inventory changed')
    checked = meta.get('health', {})
    if any(checked.get(k) != v for k, v in dict(status=HEALTH, targets=64, supplied_background=128,
            known_motifs=2, de_novo_and_comparison=True).items()) or checked.get('call', {}).get('exit_code') != 0:
        raise core.Unavailable('Actual genomic scientific health incomplete')
    closure = meta.get('external_files', {})
    if not 20 <= len(closure) <= 256 or not {str(Path('/usr/bin') / p) for p in UTILITIES} | {'/bin/sh'} <= set(closure):
        raise core.Unavailable('Original genomic Perl/helper/library closure missing')
    for name, value in closure.items():
        path = Path(name); real = path.resolve(strict=True)
        if not str(path).startswith(('/usr/', '/lib/', '/lib64/', '/bin/')) or str(real) != value.get('real_path') or not real.is_file() or real.stat().st_size != value.get('bytes') or core.sha(real) != value.get('sha256'):
            raise core.Unavailable('Genomic host/Perl/library binding changed: ' + name)
    return meta


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile', choices=sorted(LOCK['profiles']), required=True)
    for key in ['home', 'cache', 'report']:
        parser.add_argument('--' + key, type=Path, required=True)
    parser.add_argument('--archive-dir', type=Path, action='append', default=[])
    parser.add_argument('--compiler', type=Path, default=Path('/usr/bin/g++'))
    parser.add_argument('--make', type=Path, default=Path('/usr/bin/make'))
    parser.add_argument('--allow-install', action='store_true'); parser.add_argument('--allow-download', action='store_true')
    args = parser.parse_args(argv)
    record = dict(status='starting', profile=args.profile, lock_sha256=LOCK_SHA,
                  permissions=dict(install=args.allow_install, download=args.allow_download),
                  started_utc=dt.datetime.now(dt.timezone.utc).isoformat(), required_action=RECOVERY)
    report = None; owns = False; code = 0
    try:
        home, cache, report = [core.private(p) for p in [args.home, args.cache, args.report]]
        references = [core.private(p) for p in args.archive_dir]
        if any(a == b or a in b.parents or b in a.parents for a, b in [(home, cache), (home, report), (cache, report)]):
            raise ValueError('Home, cache and report need distinct nonnested paths')
        if report.exists():
            raise ValueError('Report exists; choose a new report')
        report.mkdir(parents=True); owns = True; actual_host = core.host()
        if not re.fullmatch(r'/[A-Za-z0-9_./-]+', str(home)):
            raise core.Unavailable('Genomic private home needs a shell/Perl-safe ASCII path')
        record.update(host=actual_host, home=str(home))
        if home.exists():
            meta = verify_home(home, args.profile, actual_host); record['status'] = 'reused-verified-admitted-genomic-runtime'
        else:
            if not args.allow_install:
                raise core.Unavailable('Installation not permitted: --allow-install absent; no home created')
            # Fail before making a home when the required build tools are unavailable.
            for tool in [args.compiler.absolute(), args.make.absolute()]:
                if not tool.is_file() or not os.access(tool, os.X_OK):
                    raise core.Unavailable('Required compiler/make unavailable: ' + str(tool))
            cache.mkdir(parents=True, exist_ok=True); pin = LOCK['profiles'][args.profile]
            archive = core.obtain(pin['archive'], cache, references, args.allow_download); home.mkdir(parents=True)
            with zipfile.ZipFile(archive) as source:
                for name, p in pin['members'].items():
                    raw = member_bytes(source, p)
                    if p['relocate']:
                        prior = base64.b64decode(p['original_prefix_base64'])
                        if not raw.startswith(prior):
                            raise ValueError('Original genomic installation prefix differs')
                        raw = prefix(home) + raw[len(prior):]
                    dest = home / name; dest.parent.mkdir(parents=True, exist_ok=True); dest.write_bytes(raw)
                    dest.chmod(0o755 if name.startswith('bin/') else 0o644)
                (home / 'config.txt').write_text(LOCK['generated_config'])
                build = build_extractor(source, home, pin, report, args.compiler.absolute(), args.make.absolute())
            closure = external_files(home, report); science = health(home, report, pin['version'])
            meta = dict(schema_version=1, tool='HOMER genomic motifs', home=str(home), profile=args.profile,
                        version=pin['version'], lock_sha256=LOCK_SHA, host=actual_host,
                        files={n: core.sha(home / n) for n in [*pin['members'], 'config.txt', 'bin/homerTools']},
                        external_files=closure, health=science, build=build, source_archive_sha256=core.sha(archive),
                        installation_relocation='Original Perl installation prefixes and C++ HomerConfig home only; original scientific bodies unchanged; original scorer and merger binaries unchanged.')
            core.write(home / MARKER, meta); verify_home(home, args.profile, actual_host)
            record['status'] = 'installed-and-admitted-original-genomic-runtime'
        record.update(health=meta['health'], runtime_marker_sha256=core.sha(home / MARKER))
    except (core.Unavailable, PermissionError, FileNotFoundError) as failure:
        code = 3; record.update(status='skipped-dependency-unavailable', cause=str(failure))
    except Exception as failure:
        code = 2; record.update(status='failed-retained', cause=str(failure))
    record['exit_code'] = code
    if owns:
        core.write(report / 'installation.json', record)
        (report / 'WARNINGS.md').write_text('# HOMER GENOMIC ' + ('ANALYSIS SKIPPED' if code == 3 else 'SETUP FAILED' if code else 'REVIEW WARNINGS') + '\n\n' + record.get('cause', 'Original synthetic health passed; review genomic background, weighting, source build and host dependency binding.') + '\n\n' + RECOVERY + '\n\n' + LOCK['transport_warning'] + '\n')
    print(json.dumps(record, ensure_ascii=False), file=sys.stderr if code else sys.stdout)
    return code


if __name__ == '__main__':
    raise SystemExit(main())
