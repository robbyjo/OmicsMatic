"""Private, unmodified MAGMA1.10 runtime. Upstream forbids redistribution/modification."""
import argparse
import datetime as dt
import hashlib
import json
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[2]
URL='https://vu.data.surf.nl/index.php/s/TOH4SuvczAKE29d/download'
ARCHIVE_SHA='953a2a88eb3a7047b2963712a5fce5d74ab82ca63c9ac32bc3184b3055020507'
FILES={'magma.exe':'e05e3d963215a973064852ec33640118d65745884cc800a5119ca7cffe6cb859','manual_v1.10.pdf':'2528587cb151177ab693aacc274bb5c528974e3ceee77f4cb6b60e2e9b4d4ffe','readme.txt':'0ee7257e5e8e87642a853c63ba24a3b7a4c12b6690416d7914a3016f60d4419a','changelog.txt':'cf2a5221bce26abf5ccf4553a525b815690965ee87a09739aea26c0cc48d89d9'}
MARKER='.jlinalg-magma.json'
def digest(p):
    with p.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()
def identity():return dict(version='1.10',profile='windows-x86_64-static',archive_sha256=ARCHIVE_SHA,files=FILES)
def available(home):
    try:
        d=json.loads((home/MARKER).read_text(encoding='utf-8'))
        return all(d.get(k)==v for k,v in identity().items()) and all(digest(home/k)==v for k,v in FILES.items())
    except (OSError,ValueError):return False
def extract(archive,dest):
    with zipfile.ZipFile(archive) as z:
        names=[i.filename for i in z.infolist()]
        if len(names)!=len(FILES) or set(names)!=set(FILES):raise ValueError('Unexpected archive members; no extraction performed')
        for info in z.infolist():
            if info.file_size>25_000_000 or info.external_attr>>16 & 0o170000==0o120000:raise ValueError('Unexpected size or symbolic link')
            body=z.read(info)
            if hashlib.sha256(body).hexdigest()!=FILES[info.filename]:raise ValueError('Pinned upstream member differs: '+info.filename)
            (dest/info.filename).write_bytes(body)
def probe(home,log):
    result=subprocess.run([str(home/'magma.exe'),'--version'],capture_output=True,text=True,timeout=60);log.write_text(result.stdout+result.stderr,encoding='utf-8')
    if result.returncode or result.stdout.strip()!='MAGMA version: v1.10 (win/s)':raise RuntimeError('Pinned Windows MAGMA version probe failed; inspect '+str(log))
def download(path):
    with urllib.request.urlopen(URL,timeout=180) as stream,path.open('wb') as out:shutil.copyfileobj(stream,out)
def install(args):
    home=args.environment.resolve();cache=args.archive.resolve();report=args.report.resolve();report.mkdir(parents=True,exist_ok=True);attempts=report/'attempts';attempts.mkdir(exist_ok=True);attempt=Path(tempfile.mkdtemp(prefix=dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ-'),dir=attempts))
    record=dict(**identity(),source=URL,official_page='https://cncr.nl/research/magma/',environment=str(home),download_authorized=args.allow_download,recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat(),attempt=str(attempt))
    try:
        if sys.platform!='win32' or platform.machine().lower() not in {'amd64','x86_64'}:record.update(status='skipped-platform-unavailable',error='This exact runtime profile requires Windows x86_64; obtain the unmodified upstream binary and validate on a supported permitted host')
        elif available(home):probe(home,attempt/'version.log');record.update(status='available-not-yet-analysis-validated',runtime_reused=True)
        elif home.exists():raise RuntimeError('Existing unmanaged or integrity-failed runtime preserved; choose a new --environment')
        elif not args.allow_download:record.update(status='skipped-install-not-permitted',error='Explicit --allow-download is required')
        else:
            cache.parent.mkdir(parents=True,exist_ok=True)
            if not cache.exists():
                with tempfile.TemporaryDirectory(dir=cache.parent) as t:
                    temp=Path(t)/'download.zip';download(temp)
                    if digest(temp)!=ARCHIVE_SHA:raise ValueError('Downloaded archive hash differs; no executable was installed')
                    shutil.copyfile(temp,cache)
            if digest(cache)!=ARCHIVE_SHA:raise ValueError('Existing cached archive failed integrity; preserved without execution')
            home.parent.mkdir(parents=True,exist_ok=True)
            with tempfile.TemporaryDirectory(dir=home.parent) as t:
                stage=Path(t);extract(cache,stage);probe(stage,attempt/'version.log');(stage/MARKER).write_text(json.dumps(identity(),indent=2)+'\n',encoding='utf-8');stage.rename(home)
            record.update(status='installed-not-yet-analysis-validated')
    except Exception as failure:record.update(status='skipped-dependency-unavailable',error=str(failure))
    record['required_action']='Where permitted run python tools/literature/install_magma.py --allow-download, then supply --home build/omics-magma. If unavailable/forbidden, skip dependent analyses and provide the pinned runtime on a permitted supported host. Preserve changed/unmanaged homes and failed attempts; choose a new destination after correcting the named cause. Scientific validation is separate from this version check.'
    record['upstream_terms']='MAGMA1.10 source and binaries may not be redistributed or modified. This installer downloads original files directly into an ignored private build directory; no upstream binary/source is bundled in the JLinAlg JAR or repository. Cite de Leeuw et al.2015 doi:10.1371/journal.pcbi.1004219. Official page requests mailing-list subscription; this tool sends no email.'
    previous=report/'dependency-status.json'
    if previous.exists():
        prior=json.loads(previous.read_text(encoding='utf-8'));record['history']=prior.get('history',[])+[{k:v for k,v in prior.items() if k!='history'}]
    for p in [previous,attempt/'dependency-status.json']:p.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    skipped=record['status'].startswith('skipped');(report/'WARNINGS.md').write_text(('# ANALYSIS SKIPPED - MAGMA RUNTIME UNAVAILABLE\n\n'+record.get('error','') if skipped else '# Private MAGMA runtime available; scientific validation is separate')+'\n\nRequired action: '+record['required_action']+'\n\nUPSTREAM TERMS: '+record['upstream_terms']+'\n',encoding='utf-8');print(json.dumps(record));return 3 if skipped else 0
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--environment',type=Path,default=ROOT/'build/omics-magma');ap.add_argument('--archive',type=Path,default=ROOT/'build/literature-review/magma-runtime/magma-1.10-windows.zip');ap.add_argument('--report',type=Path,default=ROOT/'build/literature-review/magma-dependency');ap.add_argument('--allow-download',action='store_true');return install(ap.parse_args())
if __name__=='__main__':raise SystemExit(main())
