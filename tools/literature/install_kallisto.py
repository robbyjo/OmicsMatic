"""Private official kallisto0.48.0 Windows profile; no OS/global installation.

0.48.0 retains the sequence-bias option used by the reviewed papers. The failed
0.52.0 Cygwin source attempt is separate and never reused by this installer.
"""
import argparse
import datetime as dt
import hashlib
import json
import platform
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[2]
LOCK_PATH=Path(__file__).with_name('kallisto-runtime-lock.json')
LOCK=json.loads(LOCK_PATH.read_text(encoding='utf-8'))
MARKER='.jlinalg-kallisto.json'


def digest(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def identity():return {key:LOCK[key] for key in ['version','profile','archive_sha256','files']}


def available(home):
    try:
        metadata=json.loads((home/MARKER).read_text(encoding='utf-8'))
        if any(metadata.get(key)!=value for key,value in identity().items()):return False
        files={p.relative_to(home).as_posix() for p in home.rglob('*') if p.is_file() and p.name!=MARKER}
        return files==set(LOCK['files']) and all(not (home/name).is_symlink() and (home/name).resolve().is_relative_to(home) and digest(home/name)==value for name,value in LOCK['files'].items())
    except (OSError,ValueError):return False


def extract(archive,destination):
    with zipfile.ZipFile(archive) as stream:
        members=[entry for entry in stream.infolist() if not entry.is_dir()]
        names=[entry.filename for entry in members]
        if len(names)!=len(LOCK['files']) or set(names)!={'kallisto/'+name for name in LOCK['files']}:raise ValueError('Unexpected/duplicate archive members; nothing was extracted')
        for entry in members:
            if entry.file_size>50_000_000 or entry.external_attr>>16 & 0o170000==0o120000:raise ValueError('Unexpected archive size/symbolic link')
            name=entry.filename.removeprefix('kallisto/');body=stream.read(entry)
            if hashlib.sha256(body).hexdigest()!=LOCK['files'][name]:raise ValueError('Pinned archive member differs: '+name)
            path=destination/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(body)


def probe(home,log):
    process=subprocess.run([(home/'kallisto.exe').as_posix(),'version'],capture_output=True,text=True,timeout=60)
    log.write_text(process.stdout+process.stderr,encoding='utf-8')
    if process.returncode or (process.stdout+process.stderr).strip()!='kallisto, version 0.48.0':raise RuntimeError('Actual version probe differs; inspect '+str(log))


def download(path):
    with urllib.request.urlopen(LOCK['url'],timeout=180) as source,path.open('wb') as target:
        size=0
        while data:=source.read(1024*1024):
            size+=len(data)
            if size>10_000_000:raise ValueError('Archive exceeds pinned download budget')
            target.write(data)


def install(args):
    home=args.environment.resolve();archive=args.archive.resolve();report=args.report.resolve();report.mkdir(parents=True,exist_ok=True)
    attempt=report/'attempts'/dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ');attempt.mkdir(parents=True)
    record=dict(**identity(),source=LOCK['url'],official_release=LOCK['official_release'],environment=str(home),attempt=str(attempt),download_authorized=args.allow_download,lock_sha256=digest(LOCK_PATH),recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat())
    try:
        if sys.platform!='win32' or platform.machine().lower() not in {'amd64','x86_64'}:record.update(status='skipped-platform-unavailable',error='This pinned official profile requires Windows x86_64; no untested host installation is inferred')
        elif available(home):probe(home,attempt/'version.log');record.update(status='available-not-yet-analysis-validated',runtime_reused=True)
        elif home.exists():raise RuntimeError('Existing unmanaged/changed runtime preserved; select a new --environment')
        elif not args.allow_download:record.update(status='skipped-install-not-permitted',error='Explicit --allow-download is required to create the private runtime')
        else:
            archive.parent.mkdir(parents=True,exist_ok=True)
            if not archive.exists():
                with tempfile.TemporaryDirectory(dir=archive.parent) as temporary:
                    downloaded=Path(temporary)/'download.zip';download(downloaded)
                    if digest(downloaded)!=LOCK['archive_sha256']:raise ValueError('Downloaded archive hash differs; no program installed')
                    shutil.copy2(downloaded,archive)
            if digest(archive)!=LOCK['archive_sha256']:raise ValueError('Cached archive differs; preserved without execution')
            home.parent.mkdir(parents=True,exist_ok=True)
            with tempfile.TemporaryDirectory(dir=home.parent) as temporary:
                stage=Path(temporary);extract(archive,stage);probe(stage,attempt/'version.log');(stage/MARKER).write_text(json.dumps(identity(),indent=2)+'\n');stage.rename(home)
            record.update(status='installed-not-yet-analysis-validated')
    except Exception as failure:record.update(status='skipped-dependency-unavailable',error=str(failure))
    record['required_action']='Where permitted run python tools/literature/install_kallisto.py --allow-download,then supply --home build/omics-kallisto-0.48.0 or JLINALG_KALLISTO_HOME. If installation is forbidden/unavailable,skip the dependent analysis until this exact permitted profile is supplied. Preserve existing/changed homes and failed logs; rerun into a new environment after correcting the cause. A version probe alone does not validate quantification.'
    record['scope']='Original unmodified official0.48.0 Windows binary and all12archive members,private only. Retains legacy bias correction. No source build,global PATH/OS change,bundled executable or reuse of the failed0.52.0 Cygwin profile. Scientific/Java workflow checks are separate.'
    target=report/'dependency-status.json'
    if target.exists():
        prior=json.loads(target.read_text(encoding='utf-8'));record['history']=prior.get('history',[])+[{key:value for key,value in prior.items() if key!='history'}]
    for path in [target,attempt/'dependency-status.json']:path.write_text(json.dumps(record,indent=2)+'\n')
    skipped=record['status'].startswith('skipped');(report/'WARNINGS.md').write_text(('# ANALYSIS SKIPPED - KALLISTO RUNTIME UNAVAILABLE\n\n'+record.get('error','') if skipped else '# Private original kallisto runtime; scientific validation is separate')+'\n\nRequired action: '+record['required_action']+'\n\n'+record['scope']+'\n')
    print(json.dumps(record));return 3 if skipped else 0


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--environment',type=Path,default=ROOT/'build/omics-kallisto-0.48.0');parser.add_argument('--archive',type=Path,default=ROOT/'build/literature-review/kallisto-runtime/kallisto_windows-v0.48.0.zip');parser.add_argument('--report',type=Path,default=ROOT/'build/literature-review/kallisto-dependency');parser.add_argument('--allow-download',action='store_true');return install(parser.parse_args())


if __name__=='__main__':raise SystemExit(main())
