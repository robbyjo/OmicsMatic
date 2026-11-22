"""Locked private Scrublet Python312/Windows runtime; installs only with explicit permission."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import tempfile
import venv

ROOT=Path(__file__).resolve().parents[2]
LOCK=Path(__file__).with_name('scrublet-python-lock.json')
MARKER='.jlinalg-scrublet-tools.json'


def digest(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def run(command,report,name,timeout=900):
    log=report/(name+'.log')
    with log.open('w',encoding='utf-8') as out:
        process=subprocess.Popen([str(x) for x in command],stdout=out,stderr=subprocess.STDOUT,start_new_session=sys.platform!='win32')
        try:code=process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            if sys.platform=='win32':subprocess.run([str(Path(os.environ['SystemRoot'])/'System32/taskkill.exe'),'/PID',str(process.pid),'/T','/F'],stdout=out,stderr=subprocess.STDOUT,timeout=60)
            else:
                import signal
                os.killpg(process.pid,signal.SIGKILL)
            process.wait(timeout=60);raise RuntimeError(name+' timed out; child processes terminated; retained '+str(log))
    return dict(command=[str(x) for x in command],exit_code=code,log=str(log),log_sha256=digest(log))


def probe(python,report):
    script='import importlib.metadata as m,json;import scrublet,annoy,numpy,scipy,sklearn,skimage;print(json.dumps({p:m.version(p) for p in '+repr([p['name'] for p in json.loads(LOCK.read_text(encoding='utf-8'))['packages']])+"}))"
    result=run([python,'-I','-c',script],report,'imports',120)
    if result['exit_code']:raise RuntimeError('Pinned runtime import probe failed; inspect imports.log')
    got=json.loads((report/'imports.log').read_text(encoding='utf-8').strip());wanted={p['name']:p['version'] for p in json.loads(LOCK.read_text(encoding='utf-8'))['packages']}
    if got!=wanted:raise RuntimeError('Runtime package versions differ from the exact lock; preserve it and choose a new environment')
    result=run([python,'-m','pip','check'],report,'pip-check',60)
    if result['exit_code']:raise RuntimeError('Runtime pip dependency check failed; inspect pip-check.log')
    return got


def install(args):
    environment=args.environment.resolve();root_report=args.report.resolve();root_report.mkdir(parents=True,exist_ok=True)
    report=root_report/'attempts'/dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ');report.mkdir(parents=True)
    python=environment/('Scripts/python.exe' if sys.platform=='win32' else 'bin/python');marker=environment/MARKER;record=dict(environment=str(environment),lock_sha256=digest(LOCK),install_authorized=args.allow_install,source_build_authorized=args.allow_source_build,recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),attempt=str(report))
    try:
        if marker.is_file():
            prior=json.loads(marker.read_text(encoding='utf-8'))
            if prior['lock_sha256']!=digest(LOCK):raise RuntimeError('Existing environment lock differs; preserve it and choose a new environment')
            record['packages']=probe(python,report);record.update(status='available-not-yet-analysis-validated',runtime_reused=True)
        elif environment.exists():raise RuntimeError('Existing unmanaged environment preserved; choose a new --environment')
        elif not args.allow_install:record.update(status='skipped-install-not-permitted',error='Explicit --allow-install is required')
        elif sys.platform!='win32' or platform.machine().lower() not in {'amd64','x86_64'} or sys.version_info[:2]!=(3,12):record.update(status='skipped-platform-unavailable',error='This exact wheel lock requires Windows x86_64 Python3.12; on other permitted hosts install pinned Scrublet/scientific versions natively and validate them')
        elif not args.allow_source_build:record.update(status='skipped-source-build-not-permitted',error='Annoy1.17.3 has no matching Windows wheel; explicit --allow-source-build and a working native C++ compiler are required')
        else:
            environment.parent.mkdir(parents=True,exist_ok=True);venv.EnvBuilder(with_pip=True).create(environment)
            # Retain ownership/lock evidence even after failure; changed or partial homes are preserved.
            marker.write_text(json.dumps(dict(lock_sha256=digest(LOCK),created_utc=record['recorded_utc']))+'\n',encoding='utf-8')
            requirements=report/'requirements.txt';requirements.write_text(''.join(p['url']+' --hash=sha256:'+p['sha256']+'\n' for p in json.loads(LOCK.read_text(encoding='utf-8'))['packages']),encoding='utf-8')
            result=run([python,'-m','pip','install','--disable-pip-version-check','--require-hashes','--no-deps','--report',report/'pip-report.json','-r',requirements],report,'install')
            record['installation']=result
            if result['exit_code']:raise RuntimeError('Locked upstream package/source installation failed; inspect install.log for compiler, download or package cause')
            record['packages']=probe(python,report);record.update(status='installed-not-yet-analysis-validated')
    except Exception as failure:record.update(status='skipped-dependency-unavailable',error=str(failure))
    record['required_action']='Skip the dependent analysis until the runtime is available. Where permitted use a Windows x86_64 Python3.12 interpreter to run tools/literature/install_scrublet.py --allow-install --allow-source-build; Annoy requires an existing native C++ compiler. No OS compiler is provisioned automatically. Preserve failed logs and unmanaged/changed environments; select a new destination after correcting the exact cause. Import/pip checks are separate from scientific validation.'
    prior=root_report/'dependency-status.json'
    if prior.exists():
        old=json.loads(prior.read_text(encoding='utf-8'));record['history']=old.get('history',[])+[{k:v for k,v in old.items() if k!='history'}]
    prior.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8');(report/'dependency-status.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    skipped=record['status'].startswith('skipped');(root_report/'WARNINGS.md').write_text(('# ANALYSIS SKIPPED - SCRUBLET RUNTIME UNAVAILABLE\n\n'+record.get('error','')) if skipped else '# Scrublet runtime available; analysis validation remains separate',encoding='utf-8')
    with (root_report/'WARNINGS.md').open('a',encoding='utf-8') as out:out.write('\n\nRequired action: '+record['required_action']+'\n')
    print(json.dumps(record));return 3 if skipped else 0


def main():
    ap=argparse.ArgumentParser();ap.add_argument('--environment',type=Path,default=ROOT/'build/omics-scrublet-env-locked');ap.add_argument('--report',type=Path,default=ROOT/'build/literature-review/scrublet-dependency');ap.add_argument('--allow-install',action='store_true');ap.add_argument('--allow-source-build',action='store_true');return install(ap.parse_args())


if __name__=='__main__':raise SystemExit(main())
