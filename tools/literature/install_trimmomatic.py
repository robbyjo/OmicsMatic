"""Pinned private Trimmomatic0.39/0.41 plus optional private Windows Java25; no OS install."""
import argparse,datetime as dt,hashlib,json,os,platform,shutil,subprocess,sys,tempfile,urllib.request,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];LOCK=Path(__file__).with_name('trimmomatic-runtime-lock.json');MARKER='.jlinalg-trimmomatic.json';JAVA_MARKER='.jlinalg-java-runtime.json'
def digest(p):
    with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()

def contained(home,name):
    relative=Path(name)
    if not name or '\\' in name or relative.is_absolute() or any(part in {'..','.'} or ':' in part for part in relative.parts):raise ValueError('Unsafe managed runtime member path')
    target=(home/relative).resolve()
    if not target.is_relative_to(home.resolve()):raise ValueError('Managed runtime member escapes private home')
    return target

def verify_members(home,files):
    import re
    return isinstance(files,dict) and bool(files) and all(isinstance(n,str) and isinstance(h,str) and re.fullmatch('[0-9a-f]{64}',h) and digest(contained(home,n))==h for n,h in files.items())
def download(url,path):
    with urllib.request.urlopen(url,timeout=180) as source,path.open('wb') as out:shutil.copyfileobj(source,out)
def fetch(record,cache,allowed):
    target=cache/record['archive'];cache.mkdir(parents=True,exist_ok=True)
    if not target.exists():
        if not allowed:raise RuntimeError('Archive absent and download not permitted: '+str(target))
        with tempfile.TemporaryDirectory(dir=cache) as t:
            p=Path(t)/'download';download(record['url'],p)
            if digest(p)!=record['sha256']:raise ValueError('Downloaded archive differs from pinned SHA256; no installation performed')
            shutil.copyfile(p,target)
    if digest(target)!=record['sha256']:raise ValueError('Cached archive integrity failed; original retained without extraction')
    return target
def extract(archive,dest,prefix,expected=None):
    result={}
    with zipfile.ZipFile(archive) as z:
        if len(z.infolist())>5000 or sum(i.file_size for i in z.infolist())>1_000_000_000:raise ValueError('Archive exceeds pinned-profile size limits')
        for info in z.infolist():
            name=info.filename
            if not name.startswith(prefix) or info.external_attr>>16 & 0o170000==0o120000:raise ValueError('Unexpected archive prefix/link')
            name=name[len(prefix):];relative=Path(name)
            if '\\' in name or relative.is_absolute() or any(part in {'..','.'} or ':' in part for part in relative.parts):raise ValueError('Unsafe archive member path')
            target=(dest/relative).resolve()
            if not target.is_relative_to(dest.resolve()):raise ValueError('Archive path escapes destination')
            if info.is_dir():target.mkdir(parents=True,exist_ok=True);continue
            if name in result:raise ValueError('Duplicate archive member')
            if expected is not None and name not in expected:raise ValueError('Unexpected program archive member')
            target.parent.mkdir(parents=True,exist_ok=True);body=z.read(info);h=hashlib.sha256(body).hexdigest()
            if expected is not None and h!=expected[name]:raise ValueError('Pinned member differs: '+name)
            target.write_bytes(body);result[name]=h
    if expected is not None and result!=expected:raise ValueError('Archive member set differs from pinned profile')
    return result
def java_version(executable,log):
    p=subprocess.run([str(executable),'-version'],capture_output=True,text=True,timeout=60);text=p.stdout+p.stderr;log.write_text(text,encoding='utf-8')
    import re
    m=re.search(r'(?:java|openjdk) version "(\d+)(?:\.(\d+))?',text)
    if p.returncode or not m:raise RuntimeError('Java version/load check failed; inspect '+str(log))
    major=int(m[1]);return int(m[2]) if major==1 else major
def available(home,profile):
    try:
        m=json.loads((home/MARKER).read_text(encoding='utf-8'));return m['version']==profile['version'] and m['archive_sha256']==profile['sha256'] and m['files']==profile['files'] and verify_members(home,profile['files'])
    except (OSError,ValueError,KeyError):return False
def private_java(home,profile,cache,allowed,attempt):
    if home.exists():
        m=json.loads((home/JAVA_MARKER).read_text(encoding='utf-8'))
        if m.get('archive_sha256')!=profile['sha256'] or not verify_members(home,m.get('files')):raise RuntimeError('Existing unmanaged/changed private Java preserved; choose a new --java-home')
    else:
        if not allowed:raise RuntimeError('Private Java absent and download/install not permitted')
        archive=fetch(profile,cache,allowed);home.parent.mkdir(parents=True,exist_ok=True)
        with tempfile.TemporaryDirectory(dir=home.parent) as t:
            stage=Path(t);files=extract(archive,stage,profile['prefix']);m=dict(archive_sha256=profile['sha256'],version=profile['version'],files=files);(stage/JAVA_MARKER).write_text(json.dumps(m,indent=2)+'\n');stage.rename(home)
    exe=home/'bin/java.exe'
    if java_version(exe,attempt/'private-java-version.log')!=25:raise RuntimeError('Private runtime is not Java25')
    return exe
def probe(home,profile,java,attempt):
    major=java_version(java,attempt/'java-version.log')
    if major<profile['minimum_java']:raise RuntimeError('Trimmomatic'+profile['version']+' requires Java'+str(profile['minimum_java'])+'+; actual Java major '+str(major))
    p=subprocess.run([str(java),'-jar',str(home/profile['jar']),'-version'],capture_output=True,text=True,timeout=60);(attempt/'program-version.log').write_text(p.stdout+p.stderr,encoding='utf-8')
    if p.returncode or p.stdout.strip()!=profile['version']:raise RuntimeError('Pinned Trimmomatic version/load probe failed; inspect program-version.log')
    return major
def install(args):
    lock=json.loads(LOCK.read_text(encoding='utf-8'));profile=lock['profiles'][args.version];home=args.environment.resolve();cache=args.cache.resolve();report=args.report.resolve();report.mkdir(parents=True,exist_ok=True);attempt=report/'attempts'/dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ');attempt.mkdir(parents=True);record=dict(version=args.version,environment=str(home),download_authorized=args.allow_download,private_java_requested=args.private_java,archive_sha256=profile['sha256'],source=profile['url'],attempt=str(attempt),recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat())
    try:
        reused=available(home,profile)
        if home.exists() and not reused:raise RuntimeError('Existing unmanaged or integrity-failed program home preserved; choose a new --environment')
        if not reused and not args.allow_download:record.update(status='skipped-install-not-permitted',error='Explicit --allow-download is required')
        else:
            if args.private_java:
                if sys.platform!='win32' or platform.machine().lower() not in {'amd64','x86_64'}:raise RuntimeError('Private pinned Java profile requires Windows x86_64; supply an existing supported --java executable')
                java=private_java(args.java_home.resolve(),lock['java25'],cache,args.allow_download,attempt)
            else:
                supplied=args.java or (json.loads((home/MARKER).read_text())['java_executable'] if reused else shutil.which('java'))
                if supplied is None:raise RuntimeError('Java executable unavailable; supply --java or, where permitted, --private-java --allow-download')
                java=Path(supplied).resolve()
            if not reused:
                if java_version(java,attempt/'java-preflight.log')<profile['minimum_java']:raise RuntimeError('Selected Java is too old; supply Java'+str(profile['minimum_java'])+'+ or use the private runtime where permitted')
                archive=fetch(profile,cache,args.allow_download);home.parent.mkdir(parents=True,exist_ok=True)
                with tempfile.TemporaryDirectory(dir=home.parent) as t:
                    stage=Path(t);extract(archive,stage,profile['prefix'],profile['files']);probe(stage,profile,java,attempt);m=dict(version=args.version,archive_sha256=profile['sha256'],files=profile['files'],jar=profile['jar'],java_executable=str(java));(stage/MARKER).write_text(json.dumps(m,indent=2)+'\n');stage.rename(home)
            record.update(status='available-not-yet-analysis-validated' if reused else 'installed-not-yet-analysis-validated',runtime_reused=reused,java_executable=str(java),java_major=probe(home,profile,java,attempt),java_executable_sha256=digest(java),files=profile['files'])
    except Exception as e:record.update(status='skipped-dependency-unavailable',error=str(e))
    record['required_action']='Skip dependent upstream trimming while unavailable. Where permitted run python tools/literature/install_trimmomatic.py --version '+args.version+' --allow-download --private-java (Windows x86_64) or supply an existing --java executable of the required version. Use the same private --environment and optional --java path in trimmomatic-tools. Preserve changed/unmanaged homes, caches and failed logs; correct the exact cause and use a new destination. Native quality/length trimming requires no extra runtime.'
    record['scope']='Private original program and optional portable Java; no OS/global installation or PATH changes. Adapter sequences have separate Illumina rights and are kept private, not bundled in JLinAlg. Version checks are separate from scientific validation.'
    p=report/'dependency-status.json'
    if p.exists():
        old=json.loads(p.read_text(encoding='utf-8'));record['history']=old.get('history',[])+[{k:v for k,v in old.items() if k!='history'}]
    for p in [p,attempt/'dependency-status.json']:p.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    skipped=record['status'].startswith('skipped');(report/'WARNINGS.md').write_text(('# ANALYSIS SKIPPED - TRIMMOMATIC RUNTIME UNAVAILABLE\n\n'+record.get('error','') if skipped else '# Trimmomatic runtime available; scientific validation is separate')+'\n\nRequired action: '+record['required_action']+'\n\n'+record['scope']+'\n',encoding='utf-8');print(json.dumps(record));return 3 if skipped else 0
def main():
    ap=argparse.ArgumentParser();ap.add_argument('--version',choices=['0.39','0.41'],default='0.41');ap.add_argument('--environment',type=Path);ap.add_argument('--report',type=Path);ap.add_argument('--cache',type=Path,default=ROOT/'build/literature-review/track-trimming-runtime');ap.add_argument('--java',type=Path);ap.add_argument('--private-java',action='store_true');ap.add_argument('--java-home',type=Path,default=ROOT/'build/omics-java25');ap.add_argument('--allow-download',action='store_true');args=ap.parse_args();args.environment=args.environment or ROOT/('build/omics-trimmomatic/'+args.version);args.report=args.report or ROOT/('build/literature-review/trimmomatic-'+args.version+'-dependency');return install(args)
if __name__=='__main__':raise SystemExit(main())
