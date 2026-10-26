"""Private Windows featureCounts, minimap2 and HISAT2 runtimes; no system changes."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import platform
import re
import shutil
import stat
import sys
import tarfile
import tempfile
import zipfile

from install_aligner_dependencies import ROOT, digest, download, extract_tar, target, verified
from install_regenie import execute, process_environment

MARKER = '.jlinalg-rna-aligner.json'
HISAT_TEST_LINKS = {'hisat2-2.2.3/evaluation/tests/CODIS/genome.fa': '../HLA_novel/genome.fa',
 'hisat2-2.2.3/evaluation/tests/CODIS/genome.fa.fai': '../HLA_novel/genome.fa.fai',
 'hisat2-2.2.3/evaluation/tests/CODIS/grch38': '../HLA_novel/grch38',
 'hisat2-2.2.3/evaluation/tests/CODIS/hisatgenotype_convert_codis.py': 'hisatgenotype_modules/hisatgenotype_convert_codis.py',
 'hisat2-2.2.3/evaluation/tests/CODIS/hisatgenotype_extract_codis_data.py': 'hisatgenotype_modules/hisatgenotype_extract_codis_data.py',
 'hisat2-2.2.3/evaluation/tests/CODIS/hisatgenotype_extract_vars.py': '../../../hisatgenotype_extract_vars.py',
 'hisat2-2.2.3/evaluation/tests/CODIS/hisatgenotype_locus.py': '../../../hisatgenotype_locus.py',
 'hisat2-2.2.3/evaluation/tests/CODIS/hisatgenotype_modules': '../../../hisatgenotype_modules',
 'hisat2-2.2.3/evaluation/tests/CYP/hisatgenotype_extract_vars.py': '../../../hisatgenotype_extract_vars.py',
 'hisat2-2.2.3/evaluation/tests/CYP/hisatgenotype_locus.py': '../../../hisatgenotype_locus.py',
 'hisat2-2.2.3/evaluation/tests/HLA_novel/hisatgenotype.py': '../../../hisatgenotype.py',
 'hisat2-2.2.3/evaluation/tests/HLA_novel/hisatgenotype_build_genome.py': '../../../hisatgenotype_build_genome.py',
 'hisat2-2.2.3/evaluation/tests/HLA_novel/hisatgenotype_extract_vars.py': '../../../hisatgenotype_extract_vars.py',
 'hisat2-2.2.3/evaluation/tests/HLA_novel/hisatgenotype_locus.py': '../../../hisatgenotype_locus.py',
 'hisat2-2.2.3/evaluation/tests/HLA_novel/hisatgenotype_modules': '../../../hisatgenotype_modules',
 'hisat2-2.2.3/evaluation/tests/genotype_genome/hisatgenotype.py': '../../../hisatgenotype.py',
 'hisat2-2.2.3/evaluation/tests/genotype_genome/hisatgenotype_build_genome.py': '../../../hisatgenotype_build_genome.py',
 'hisat2-2.2.3/evaluation/tests/genotype_genome/hisatgenotype_extract_vars.py': '../../../hisatgenotype_extract_vars.py',
 'hisat2-2.2.3/evaluation/tests/genotype_genome/hisatgenotype_locus.py': '../../../hisatgenotype_locus.py',
 'hisat2-2.2.3/evaluation/tests/genotype_genome/hisatgenotype_modules': '../../../hisatgenotype_modules'}
PACKAGES = {
    'featurecounts': dict(version='2.1.1', filename='subread-2.1.1-Windows-x86_64.zip', size=48084495,
        url='https://downloads.sourceforge.net/project/subread/subread-2.1.1/subread-2.1.1-Windows-x86_64.zip',
        sha256='16f04778c1ade1bfca0c594016527a7e73eaecd1b6724f4eace00c674921ab3e',
        binary='featureCounts.exe', folder='subread-2.1.1-Windows-x86_64'),
    'minimap2': dict(version='2.31-r1302', filename='minimap2-v2.31.tar.gz',
        url='https://github.com/lh3/minimap2/archive/refs/tags/v2.31.tar.gz',
        sha256='bff334a0e4512644e2f3e29944aeec408f49450f4f74dc39fe89e5273869255b',
        binary='minimap2.exe', folder='minimap2-2.31'),
    'hisat2': dict(version='2.2.3', filename='hisat2-v2.2.3.tar.gz',
        url='https://github.com/DaehwanKimLab/hisat2/archive/refs/tags/v2.2.3.tar.gz',
        sha256='b53107422e5b44ebea4b20b1a77bb9e240d6b92d654fcd7e6a6ab5d1aae86c45',
        binary='hisat2-align-s.exe', folder='hisat2-2.2.3'),
}


def identity(profile):
    return PACKAGES[profile]['sha256'] + ':windows-x86_64:build-protocol-1'


def extract_zip(archive, staging):
    with zipfile.ZipFile(archive) as source:
        total = 0
        for entry in source.infolist():
            target(staging, entry.filename)
            total += entry.file_size
            if stat.S_ISLNK(entry.external_attr >> 16) or total > 1024**3:
                raise ValueError('Unsafe or excessive ZIP content')
        source.extractall(staging)


def extract_source(archive, staging, profile):
    omitted = []
    with tarfile.open(archive, 'r:gz') as source:
        def members():
            for entry in source:
                if profile == 'hisat2' and entry.name in HISAT_TEST_LINKS:
                    if not entry.issym() or entry.linkname != HISAT_TEST_LINKS[entry.name]:
                        raise ValueError('Unexpected HISAT2 test-data link identity')
                    omitted.append({'name': entry.name, 'target': entry.linkname,
                                    'reason': 'Dangling external research test data; not required to build the six runtime programs'})
                else:
                    yield entry
        # Streaming wrapper keeps extractfile available without unsafe tar extraction.
        class Selected:
            def __iter__(self): return members()
            def extractfile(self, entry): return source.extractfile(entry)
        extract_tar(Selected(), staging)
    if profile == 'hisat2' and len(omitted) != len(HISAT_TEST_LINKS):
        raise ValueError('Pinned HISAT2 archive is missing expected test-data links')
    return omitted


def probe(profile, environment, report, record):
    item = PACKAGES[profile]
    text = execute([environment/'bin'/item['binary'], '-v' if profile == 'featurecounts' else '--version'],
                   report, process_environment(environment/'bin'), report, 'version', record, 30)
    expected = 'featureCounts v2.1.1' if profile == 'featurecounts' else item['version']
    if expected not in text:
        raise ValueError('Runtime version differs from the pinned version: ' + expected)


def copy_cygwin_programs(programs, cyg, staging, environment, report, record):
    pending = list(programs) + [cyg/'cygpath.exe']
    seen = set()
    dest = staging/'bin'
    dest.mkdir()
    while pending:
        source = pending.pop()
        key = source.name.casefold()
        if key in seen:
            continue
        if not source.is_file():
            raise FileNotFoundError('Required Cygwin runtime file is absent: ' + str(source))
        seen.add(key)
        shutil.copy2(source, dest/source.name)
        text = execute([cyg/'objdump.exe', '-p', source], staging, environment, report,
                       'imports-'+str(len(seen)), record, 60)
        pending.extend(cyg/name for name in re.findall(r'DLL Name:\s*(\S+)', text) if name.casefold().startswith('cyg'))


def install(args):
    item = PACKAGES[args.profile]
    env, report_root = args.environment.resolve(), args.report.resolve()
    attempts = report_root/'attempts'
    attempts.mkdir(parents=True, exist_ok=True)
    # Clock resolution alone cannot distinguish consecutive attempts on every
    # host. Atomically reserve a fresh directory and never reuse old logs.
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    report = Path(tempfile.mkdtemp(prefix=stamp+'-', dir=attempts))
    record = dict(profile=args.profile, version=item['version'], source=item['url'],
                  archive_sha256=item['sha256'], environment=str(env),
                  recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
                  attempt_directory=str(report),
                  download_authorized=args.allow_download, source_build_authorized=args.allow_source_build)
    try:
        if verified(env, MARKER, identity(args.profile)):
            probe(args.profile, env, report, record)
            marker = json.loads((env/MARKER).read_text(encoding='utf-8'))
            provenance = marker.get('provenance')
            if provenance is None and (report_root/'dependency-status.json').exists():
                old = json.loads((report_root/'dependency-status.json').read_text(encoding='utf-8'))
                candidates = old.get('history', []) + [old]
                found = [r for r in candidates if r.get('archive_sha256') == item['sha256']
                         and r.get('environment') == str(env) and r.get('status') == 'installed-not-yet-analysis-validated']
                if found:
                    provenance = build_provenance(found[-1])
                    marker['provenance'] = provenance
                    (env/MARKER).write_text(json.dumps(marker, indent=2)+'\n', encoding='utf-8')
            record['installed_provenance'] = provenance or {'source': item['url'], 'archive_sha256': item['sha256'], 'note': 'Original build record unavailable; scientific validation is separate'}
            if provenance:
                record.update({k:provenance[k] for k in ['compiler_warning_count','compiler_warnings_first_100'] if k in provenance})
            record.update(status='available-not-yet-analysis-validated', runtime_reused=True)
        elif env.exists():
            raise ValueError('Existing unmanaged or integrity-failed runtime preserved; choose a new --environment')
        elif not args.allow_download:
            record.update(status='skipped-install-not-permitted', error='Explicit --allow-download is required')
        elif sys.platform != 'win32' or platform.machine().lower() not in {'amd64', 'x86_64'}:
            record.update(status='skipped-platform-unavailable', error='This installer supports Windows x86_64; install pinned upstream software natively on other hosts')
        elif args.profile != 'featurecounts' and not args.allow_source_build:
            record.update(status='skipped-source-build-not-permitted', error='Explicit --allow-source-build is required')
        else:
            archive = download(item, args.cache.resolve(), report)
            env.parent.mkdir(parents=True, exist_ok=True)
            staging = Path(tempfile.mkdtemp(prefix='.jlinalg-rna-' + args.profile + '-', dir=env.parent)).resolve()
            record['staging'] = str(staging)
            if args.profile == 'featurecounts':
                extract_zip(archive, staging)
                shutil.copytree(staging/item['folder']/'bin', staging/'bin')
                record['source_policy'] = 'Unmodified official Windows binaries; publisher SHA-256 checked'
            else:
                cyg = (args.cygwin_root/'bin').resolve()
                tools = ['gcc', 'g++', 'make', 'cygpath', 'objdump']
                for name in tools:
                    if not (cyg/(name+'.exe')).is_file():
                        raise FileNotFoundError('Missing existing Cygwin ' + name + '; supply --cygwin-root with gcc-core, gcc-g++, make and zlib-devel already installed')
                before = {str(cyg/(name+'.exe')): digest(cyg/(name+'.exe')) for name in tools}
                record['omitted_test_data_links'] = extract_source(archive, staging, args.profile)
                source = staging/item['folder']
                original = {p.relative_to(source).as_posix(): digest(p) for p in source.rglob('*') if p.is_file()}
                build_env = process_environment(cyg)
                command = [cyg/'make.exe', '-j'+str(args.build_threads)]
                if args.profile == 'hisat2':
                    command += ['hisat2-align-s', 'hisat2-align-l', 'hisat2-build-s', 'hisat2-build-l', 'hisat2-inspect-s', 'hisat2-inspect-l']
                execute(command, source, build_env, report, 'build', record, args.build_timeout)
                programs = [item['binary']] if args.profile == 'minimap2' else [name+'.exe' for name in command[2:]]
                copy_cygwin_programs([source/program for program in programs], cyg, staging, build_env, report, record)
                if any(digest(source/name) != sha for name, sha in original.items()):
                    raise ValueError('Upstream source files changed during the build')
                if any(digest(Path(name)) != sha for name, sha in before.items()):
                    raise ValueError('Existing read-only toolchain files changed during the build')
                record.update(source_policy='Unmodified tagged source; existing read-only Cygwin toolchain',
                              original_source_files=len(original), toolchain_sha256=before)
            probe(args.profile, staging, report, record)
            compiler_warnings(record, report/'build.log')
            from install_aligner_dependencies import mark
            mark(staging, MARKER, identity(args.profile))
            marker = json.loads((staging/MARKER).read_text(encoding='utf-8'))
            marker['provenance'] = build_provenance(record)
            (staging/MARKER).write_text(json.dumps(marker, indent=2)+'\n', encoding='utf-8')
            staging.replace(env)
            record.update(status='installed-not-yet-analysis-validated')
    except Exception as failure:
        record.update(status='skipped-dependency-unavailable', error=str(failure))
    compiler_warnings(record, report/'build.log')
    record['required_action'] = ('Skip dependent analyses when unavailable. Where permitted run python tools/literature/install_rna_aligners.py --profile '
        + args.profile + ' --allow-download' + ('' if args.profile == 'featurecounts' else ' --allow-source-build --cygwin-root C:/cygwin64')
        + '. Missing build prerequisites require an existing permitted Cygwin toolchain (gcc-core, gcc-g++, make, zlib-devel), or a supported native host. '
        'Preserve failed logs/staging and existing runtimes; select a new destination after correcting the named cause. Version probes do not establish analysis validity.')
    previous = report_root/'dependency-status.json'
    if previous.exists():
        old = json.loads(previous.read_text(encoding='utf-8'))
        record['history'] = old.get('history', []) + [{k:v for k,v in old.items() if k != 'history'}]
    previous.write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
    (report/'dependency-status.json').write_text(json.dumps(record, indent=2)+'\n', encoding='utf-8')
    skipped = record['status'].startswith('skipped')
    warning = '# ANALYSIS SKIPPED - REQUIRED RUNTIME UNAVAILABLE\n\n' + record.get('error', '') if skipped else '# Runtime available; analysis validation remains separate'
    if record.get('compiler_warning_count'):
        warning += '\n\n## UPSTREAM COMPILER WARNINGS\n\n' + str(record['compiler_warning_count']) + ' compiler warnings retained in build.log; review before extending the validated input/operation scope.'
    (report_root/'WARNINGS.md').write_text(warning+'\n\nRetained attempt: '+str(report)+'\n\nRequired action: '+record['required_action']+'\n', encoding='utf-8')
    print(json.dumps(record))
    return 3 if skipped else 0


def compiler_warnings(record, log):
    if log.exists():
        warnings = [line for line in log.read_text(encoding='utf-8', errors='replace').splitlines() if 'warning:' in line.lower()]
        record.update(compiler_warning_count=len(warnings), compiler_warnings_first_100=warnings[:100])


def build_provenance(record):
    fields = ['source','archive_sha256','source_policy','original_source_files','toolchain_sha256',
              'omitted_test_data_links','compiler_warning_count','compiler_warnings_first_100','commands']
    return {k:record[k] for k in fields if k in record}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--profile', choices=PACKAGES, required=True)
    ap.add_argument('--allow-download', action='store_true')
    ap.add_argument('--allow-source-build', action='store_true')
    ap.add_argument('--environment', type=Path)
    ap.add_argument('--cache', type=Path, default=ROOT/'build/omics-rna-aligner-download')
    ap.add_argument('--report', type=Path)
    ap.add_argument('--cygwin-root', type=Path, default=Path('C:/cygwin64'))
    ap.add_argument('--build-threads', type=int, choices=range(1, 9), default=2)
    ap.add_argument('--build-timeout', type=int, choices=range(60, 14401), default=3600)
    args = ap.parse_args()
    args.environment = args.environment or ROOT/('build/omics-'+args.profile)
    args.report = args.report or ROOT/('build/literature-review/'+args.profile+'-dependency')
    return install(args)


if __name__ == '__main__':
    raise SystemExit(main())
