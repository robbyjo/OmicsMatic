"""Install the checksum-pinned official local GATK JAR privately, when permitted."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[2]
VERSION='4.7.0.0'
URL='https://github.com/broadinstitute/gatk/releases/download/4.7.0.0/gatk-4.7.0.0.zip'
ARCHIVE_SHA256='d093d2693b1626361a413ca59d6d4a0bf968717f280a8fd9ce060b25eb2ed1db'
ARCHIVE_BYTES=699681649
MEMBER='gatk-4.7.0.0/gatk-package-4.7.0.0-local.jar'
JAR_SHA256='882e180707e0e6887885853fc486fa54098620fe9a80bdcdaaa655c7e3861365'
JAR_BYTES=426730186
MARKER='.jlinalg-gatk.json'


def digest(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()


def available(environment):
    try:
        marker=json.loads((environment/MARKER).read_text(encoding='utf-8'))
        return marker['version']==VERSION and marker['archive_sha256']==ARCHIVE_SHA256 and marker['jar_sha256']==JAR_SHA256 and digest(environment/'gatk.jar')==JAR_SHA256
    except (OSError,ValueError,KeyError):return False


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--allow-download',action='store_true')
    parser.add_argument('--environment',type=Path,default=ROOT/'build/omics-gatk')
    parser.add_argument('--archive',type=Path,default=ROOT/'build/omics-gatk-download/gatk-4.7.0.0.zip')
    parser.add_argument('--report',type=Path,default=ROOT/'build/literature-review/gatk-dependency')
    parser.add_argument('--java',default=shutil.which('java') or 'java');args=parser.parse_args()
    environment,archive,report=args.environment.resolve(),args.archive.resolve(),args.report.resolve();report.mkdir(parents=True,exist_ok=True)
    record={'version':VERSION,'source':URL,'archive_sha256':ARCHIVE_SHA256,'jar_sha256':JAR_SHA256,
        'environment':str(environment),'download_authorized':args.allow_download,'recorded_utc':dt.datetime.now(dt.timezone.utc).isoformat()}
    if available(environment):record['status']='available-not-yet-analysis-validated'
    elif not args.allow_download:record['status']='skipped-install-not-permitted'
    elif environment.exists():record.update(status='skipped-existing-environment',error='Unmanaged/integrity-failed runtime preserved; choose a new environment')
    else:
        try:
            if not archive.exists():
                archive.parent.mkdir(parents=True,exist_ok=True)
                with tempfile.NamedTemporaryFile(prefix='gatk-',suffix='.part',dir=report,delete=False) as stream:
                    temporary=Path(stream.name);record['download_staging_file']=str(temporary)
                    with urllib.request.urlopen(URL,timeout=60) as response:
                        total=0
                        while block:=response.read(1024*1024):
                            total+=len(block)
                            if total>ARCHIVE_BYTES:raise ValueError('Release exceeds pinned size')
                            stream.write(block)
                if temporary.stat().st_size!=ARCHIVE_BYTES or digest(temporary)!=ARCHIVE_SHA256:raise ValueError('Downloaded archive failed publisher integrity checks')
                temporary.replace(archive)
            if archive.stat().st_size!=ARCHIVE_BYTES or digest(archive)!=ARCHIVE_SHA256:raise ValueError('Cached release failed publisher integrity checks; retained')
            environment.parent.mkdir(parents=True,exist_ok=True);staging=Path(tempfile.mkdtemp(prefix='.jlinalg-gatk-',dir=environment.parent));record['installation_staging_directory']=str(staging)
            with zipfile.ZipFile(archive) as source:
                candidates=[x for x in source.infolist() if x.filename==MEMBER]
                if len(candidates)!=1 or candidates[0].file_size!=JAR_BYTES:raise ValueError('Release local JAR member mismatch')
                with source.open(candidates[0]) as stream,(staging/'gatk.jar').open('xb') as target:shutil.copyfileobj(stream,target)
            if digest(staging/'gatk.jar')!=JAR_SHA256:raise ValueError('Extracted local JAR integrity mismatch')
            (staging/MARKER).write_text(json.dumps({'version':VERSION,'archive_sha256':ARCHIVE_SHA256,'jar_sha256':JAR_SHA256,'source':URL},indent=2)+'\n')
            staging.replace(environment);record['status']='installed-not-yet-analysis-validated'
        except Exception as failure:record.update(status='skipped-dependency-unavailable',error=str(failure))
    if not record['status'].startswith('skipped'):
        try:
            p=subprocess.run([args.java,'-jar',str(environment/'gatk.jar'),'--version'],capture_output=True,text=True,timeout=60)
            record['version_probe']={'exit_code':p.returncode,'stdout':p.stdout,'stderr':p.stderr}
            if p.returncode!=0 or 'v'+VERSION not in p.stdout+p.stderr:raise ValueError('Java could not load pinned GATK version')
        except Exception as failure:record.update(status='skipped-runtime-unavailable',error=str(failure))
    skipped=record['status'].startswith('skipped')
    record['action']=('Skip dependent analyses. Where permitted run python tools/literature/install_gatk.py --allow-download and supply --gatk build/omics-gatk/gatk.jar with a working Java 17+ runtime. If installation is forbidden, have an administrator provide the pinned JAR/runtime. Inspect the retained error/cache/staging, preserve existing files, and use a new environment after correcting the cause.' if skipped else 'Supply --gatk '+str(environment/'gatk.jar')+' and Java 17+. Installation/version loading is distinct from analysis validation; no Conda, Spark or Python installation was performed.')
    path=report/'dependency-status.json'
    if path.exists():
        old=json.loads(path.read_text());record['history']=old.get('history',[])+[{k:v for k,v in old.items() if k!='history'}]
    path.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    (report/'WARNINGS.md').write_text(('# GATK UNAVAILABLE - ANALYSIS SKIPPED\n\n'+record.get('error',record['status'])+'\n\nRequired action: '+record['action']+'\n') if skipped else '# GATK runtime available\n\nActual analysis validation remains separate.\n',encoding='utf-8')
    print(json.dumps(record));return 3 if skipped else 0


if __name__=='__main__':raise SystemExit(main())
