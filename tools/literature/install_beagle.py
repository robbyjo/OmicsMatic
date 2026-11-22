"""Install an exact private official Beagle profile only where explicitly permitted."""
from pathlib import Path
import argparse
import datetime as dt
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
LOCK_PATH = Path(__file__).with_name('beagle-runtime-lock.json')
LOCK = json.loads(LOCK_PATH.read_text(encoding='utf-8'))
MARKER = '.jlinalg-beagle.json'
ACTION = ('Where permitted, run python tools/literature/install_beagle.py --home NEW_PRIVATE_HOME '
          '--cache OFFICIAL_ARTIFACT_CACHE --java JAVA_EXECUTABLE --allow-install. '
          'Add --allow-download only when missing official downloads are allowed. '
          'Use a new home/cache for changed or unmanaged files; preserve the existing files. '
          'If installation, downloads or Java are unavailable or forbidden, skip the dependent '
          'analysis until an administrator supplies the exact profile and a permitted Java runtime. '
          'Setup/version probes do not validate phasing or imputation analyses.')


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def identity():
    return dict(profile=LOCK['profile'], runtime_lock_sha256=digest(LOCK_PATH), files=LOCK['files'])


def reparse(path):
    return path.is_symlink() or bool(getattr(path.lstat(), 'st_file_attributes', 0) & 0x400)


def available(home):
    try:
        home = Path(home)
        marker = home / MARKER
        if reparse(home) or reparse(marker) or marker.stat().st_size > 65536:
            return False
        values = json.loads(marker.read_text(encoding='utf-8'))
        if any(values.get(key) != value for key, value in identity().items()):
            return False
        members = set()
        for ordinal, path in enumerate(home.iterdir(), 1):
            if ordinal > len(LOCK['files']) + 1 or reparse(path) or not path.is_file():
                return False
            if path != marker:
                members.add(path.name)
        return members == set(LOCK['files']) and all(digest(home/name) == value for name,value in LOCK['files'].items())
    except (OSError, ValueError, KeyError, TypeError):
        return False


def child_environment():
    environment = os.environ.copy()
    for name in ['JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH']:
        environment.pop(name, None)
    return environment


def probe(home, java, attempt):
    java = Path(java).absolute()
    if not java.is_file():
        raise RuntimeError('Java executable is missing; supply --java with a permitted Java 8+ executable')
    environment = child_environment()
    version_command = [str(java), '-version']
    version = subprocess.run(version_command, cwd=attempt, env=environment, capture_output=True, timeout=30)
    (attempt/'java-version.stdout').write_bytes(version.stdout)
    (attempt/'java-version.stderr').write_bytes(version.stderr)
    match = re.search(r'version\s+"(?:1\.)?(\d+)', (version.stdout+version.stderr).decode('utf-8', errors='replace'))
    if version.returncode or match is None or int(match.group(1)) < 8:
        raise RuntimeError('Java probe failed or reports an unsupported version; require Java 8+')
    record = dict(java_command=version_command, java_version_stdout_sha256=digest(attempt/'java-version.stdout'),
                  java_version_stderr_sha256=digest(attempt/'java-version.stderr'), profiles={})
    for profile, settings in LOCK['profiles'].items():
        jar = home/settings['jar']
        if digest(jar) != LOCK['files'][settings['jar']]:
            raise RuntimeError('Original Beagle JAR differs before execution: '+profile)
        command = [str(java), '-Dfile.encoding=UTF-8', '-Duser.language=en', '-Duser.country=US', '-Xmx128m', '-jar', str(jar)]
        result = subprocess.run(command, cwd=attempt, env=environment, capture_output=True, timeout=60)
        (attempt/(profile+'.stdout')).write_bytes(result.stdout)
        (attempt/(profile+'.stderr')).write_bytes(result.stderr)
        lines = result.stdout.decode('utf-8').splitlines()
        if result.returncode or result.stderr or not lines or lines[0] != settings['version_line']:
            raise RuntimeError('Original Beagle usage/version probe differs: '+profile)
        if digest(jar) != LOCK['files'][settings['jar']]:
            raise RuntimeError('Original Beagle JAR changed during version probe: '+profile)
        record['profiles'][profile] = dict(command=command, exit_code=result.returncode,
            stdout_sha256=digest(attempt/(profile+'.stdout')), stderr_sha256=digest(attempt/(profile+'.stderr')))
    return record


def download(url, target, maximum):
    total = 0
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent':'JLinAlg-toolbox/1.0'}), timeout=60) as response, target.open('wb') as stream:
        while block := response.read(65536):
            total += len(block)
            if total > maximum:
                raise ValueError('Official artifact exceeds declared byte bound')
            stream.write(block)


def overlap(a, b):
    return a == b or a in b.parents or b in a.parents


def install(args):
    requested = args.home.absolute()
    home, cache, report = requested.resolve(), args.cache.resolve(), args.report.resolve()
    if any(overlap(a,b) for a,b in [(home,cache),(home,report),(cache,report)]):
        raise ValueError('Choose separate, non-overlapping runtime, cache and report directories; no setup mutation was attempted')
    attempt = report/'attempts'/(dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')+'-'+uuid.uuid4().hex[:10])
    attempt.mkdir(parents=True, exist_ok=False)
    record = dict(**identity(), environment=str(home), allow_install=args.allow_install,
        allow_download=args.allow_download, recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(), attempt=str(attempt),
        source_scope=LOCK['source_scope'], artifacts=LOCK['artifacts'])
    try:
        if requested != home or os.path.lexists(requested) and reparse(requested):
            raise RuntimeError('Linked runtime home/ancestor is preserved; select a new real private path')
        if available(home):
            record.update(status='available-admission-only;scientific-validation-separate', runtime_reused=True,
                          version_probes=probe(home,args.java,attempt))
            if not available(home):
                raise RuntimeError('Sealed Beagle home changed during probes')
        elif home.exists():
            raise RuntimeError('Existing unmanaged or changed home preserved; use a new --home')
        elif not args.allow_install:
            record.update(status='skipped-install-not-permitted', error='A new private installation requires --allow-install')
        else:
            for name, artifact in LOCK['artifacts'].items():
                cached = cache/artifact['cache_name']
                if not cached.exists():
                    if not args.allow_download:
                        raise RuntimeError('Missing cached artifact '+str(cached)+'; download requires --allow-download')
                    cache.mkdir(parents=True, exist_ok=True)
                    with tempfile.TemporaryDirectory(prefix='.beagle-download-',dir=cache) as temporary:
                        stage = Path(temporary).resolve()
                        if stage.parent != cache:
                            raise RuntimeError('Download staging escaped the named cache')
                        candidate = stage/'artifact.part'
                        download(artifact['url'],candidate,artifact['maximum_bytes'])
                        if digest(candidate) != artifact['sha256']:
                            raise RuntimeError('Official download differs from the locally recorded exact-version pin')
                        shutil.copy2(candidate,cached)
                if reparse(cached) or not cached.is_file() or cached.stat().st_size > artifact['maximum_bytes'] or digest(cached) != artifact['sha256']:
                    raise RuntimeError('Changed/linked cached artifact preserved without execution: '+str(cached))
            home.parent.mkdir(parents=True, exist_ok=True)
            with tempfile.TemporaryDirectory(prefix='.beagle-install-',dir=home.parent) as temporary:
                stage = Path(temporary).resolve()
                if stage.parent != home.parent:
                    raise RuntimeError('Installation staging escaped the named parent directory')
                for name,artifact in LOCK['artifacts'].items():
                    shutil.copy2(cache/artifact['cache_name'],stage/name)
                record['version_probes'] = probe(stage,args.java,attempt)
                (stage/MARKER).write_text(json.dumps(identity(),indent=2)+'\n',encoding='utf-8')
                if not available(stage):
                    raise RuntimeError('Staged Beagle profile failed complete admission')
                stage.rename(home)
            record['status'] = 'installed-admission-only;scientific-validation-separate'
    except Exception as failure:
        record.update(status='skipped-dependency-unavailable',error=str(failure))
    record['required_action'] = ACTION
    record['scope'] = ('Private original JARs and GPL text only; no OS/global installation or persistent environment change. '
                       'The publisher identifies bundled net.sf.samtools portions as MIT-licensed; '
                       'the unchanged publisher source archives/pages provide their notices. '
                       'No Beagle estimator, inference analysis or project-analysis adapter is validated by this installer.')
    success = record['status'].startswith(('installed','available'))
    (attempt/'dependency-status.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    (attempt/'WARNINGS.md').write_text('# BEAGLE SETUP '+('ADMITTED — ANALYSIS VALIDATION SEPARATE' if success else 'SKIPPED')+
        '\n\n'+record.get('error','Only setup/usage probes completed; no scientific analysis was run.')+'\n\n'+ACTION+'\n',encoding='utf-8')
    shutil.copy2(attempt/'dependency-status.json',report/'dependency-status.json')
    shutil.copy2(attempt/'WARNINGS.md',report/'WARNINGS.md')
    return record, 0 if success else 3


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--home',type=Path,default=ROOT/'build/omics-beagle-official')
    parser.add_argument('--cache',type=Path,default=ROOT/'build/omics-beagle-download')
    parser.add_argument('--report',type=Path,default=ROOT/'build/literature-review/beagle-runtime/dependency')
    parser.add_argument('--java',type=Path,required=True)
    parser.add_argument('--allow-install',action='store_true')
    parser.add_argument('--allow-download',action='store_true')
    args = parser.parse_args()
    try:
        record,code = install(args)
        print(json.dumps(dict(status=record['status'],report=str(args.report.resolve()/'WARNINGS.md'))))
        if code:
            print('BEAGLE SETUP SKIPPED: '+record.get('error','')+'. '+ACTION,file=sys.stderr)
        return code
    except Exception as failure:
        print('BEAGLE SETUP SKIPPED: '+str(failure)+'. '+ACTION,file=sys.stderr)
        return 3


if __name__=='__main__':
    raise SystemExit(main())
