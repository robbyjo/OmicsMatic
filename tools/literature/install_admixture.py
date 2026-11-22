#!/usr/bin/env python3
"""Admit pinned private ADMIXTURE binaries after an actual K1 likelihood health check.

Analysis never installs. Installation and downloads have separate explicit flags.
Existing homes, corrupt caches and failed reports are preserved. No OS package,
kernel setting or binary-format handler is installed or changed.
"""
import argparse,datetime as dt,hashlib,json,math,os,platform,random,re,shutil,signal,subprocess,sys,tarfile,urllib.request
from pathlib import Path,PurePosixPath

HERE=Path(__file__).resolve().parent
LOCK_RAW=(HERE/'admixture-runtime-lock.json').read_bytes();LOCK=json.loads(LOCK_RAW)
LOCK_SHA=hashlib.sha256(LOCK_RAW).hexdigest();MARKER='.jlinalg-admixture.json'
RECOVERY=('Required action: use Linux x86_64 and Python3 for setup; on Windows use WSL and Linux Java for analysis. '
 'Where permitted, run python3 tools/literature/install_admixture.py --profile linux-x86_64-1.4.0 '
 '--home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT --allow-install; add --allow-download only '
 'where permitted, or provide repeated --archive-dir OFFLINE_ARCHIVE_DIRECTORY arguments. Legacy1.3.0 requires '
 'the explicit linux-x86_64-1.3.0-qemu8.2.2 profile and dpkg-deb to extract its pinned private QEMU package. '
 'If installation/execution is forbidden or dependencies are unavailable,unsupported or changed, SKIP the '
 'dependent analysis. Preserve changed/unmanaged homes,failed attempts and corrupt caches; correct the '
 'named cause and use new home/report/output paths. Analysis never installs. No global host settings are changed.')

class Unavailable(Exception):pass
def sha(p):
    with Path(p).open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()
def write(p,v):p.write_text(json.dumps(v,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
def private(p):
    p=Path(p).absolute()
    if any(v.is_symlink() for v in [p,*p.parents]):raise ValueError('Linked path/ancestor is unsupported: '+str(p))
    return p.resolve()
def host():
    if platform.system()!='Linux' or platform.machine().lower() not in {'x86_64','amd64'}:raise Unavailable('Original runtime needs Linux x86_64; use Linux Python/Java inside WSL on Windows')
    return dict(os='Linux',architecture='x86_64',kernel=platform.release())
def verify_home(home,profile,expected_host):
    if not home.is_dir() or home.is_symlink() or not (home/MARKER).is_file() or (home/MARKER).is_symlink():raise Unavailable('Existing home is missing,linked or unmanaged; preserve it and use a new home')
    marker=home/MARKER
    if marker.stat().st_size>1048576:raise Unavailable('Oversized runtime marker')
    m=json.loads(marker.read_text(encoding='utf-8'));pin=LOCK['profiles'][profile]
    if m.get('schema_version')!=1 or m.get('profile')!=profile or m.get('version')!=pin['version'] or m.get('lock_sha256')!=LOCK_SHA or m.get('host')!=expected_host or m.get('health',{}).get('status')!='passed-original-K1-likelihood-and-matrix-health':raise Unavailable('Admitted runtime version,lock,host/kernel or scientific health differs')
    expected=set(pin['members'])|{MARKER};found=set()
    for p in home.rglob('*'):
        if p.is_symlink() or not (p.is_file() or p.is_dir()):raise Unavailable('Linked/nonregular runtime member')
        if p.is_file():found.add(p.relative_to(home).as_posix())
        if len(found)>32:raise Unavailable('Unexpected runtime member count')
    if found!=expected or set(m.get('files',{}))!=set(pin['members']):raise Unavailable('Runtime contains extra,missing or unmanaged files')
    for name,entry in pin['members'].items():
        p=home/name
        if not p.is_file() or p.stat().st_size!=entry['bytes'] or sha(p)!=entry['sha256'] or m['files'][name]!=entry['sha256'] or name.startswith('bin/') and not os.access(p,os.X_OK):raise Unavailable('Changed runtime member or executable permission: '+name)
    return m
def obtain(name,cache,references,download):
    pin=LOCK['archives'][name];p=cache/name
    def check(value):
        if value.is_symlink() or not value.is_file() or value.stat().st_size!=pin['bytes'] or sha(value)!=pin['sha256']:raise ValueError('Corrupt/linked pinned archive; preserve it and choose another cache/source: '+str(value))
    if p.exists():check(p);return p
    for reference in references:
        candidate=reference/name
        if candidate.exists():
            check(candidate)
            with candidate.open('rb') as src,p.open('xb') as dst:shutil.copyfileobj(src,dst)
            check(p);return p
    if not download:raise Unavailable('Missing pinned archive and --allow-download absent: '+name)
    partial=cache/(name+'.part')
    if partial.exists():raise ValueError('Retained partial download exists; use a new cache: '+str(partial))
    request=urllib.request.Request(pin['url'],headers={'User-Agent':'JLinAlg-private-ADMIXTURE-runtime/1.0'})
    with urllib.request.urlopen(request,timeout=60) as response,partial.open('xb') as dst:
        total=0
        while chunk:=response.read(1048576):
            total+=len(chunk)
            if total>pin['bytes']:raise ValueError('Pinned download byte bound exceeded')
            dst.write(chunk)
    check(partial);partial.rename(p);return p
def extract_selected(archive,members,home,report):
    source=archive
    if archive.name.endswith('.deb'):
        program=shutil.which('dpkg-deb')
        if not program:raise Unavailable('dpkg-deb is required for this pinned private QEMU package; install it where permitted or use the current native profile')
        source=report/'qemu-package-data.tar'
        with source.open('xb') as out:
            result=subprocess.run([program,'--fsys-tarfile',str(archive)],stdout=out,stderr=subprocess.PIPE,timeout=60)
        (report/'dpkg-deb.stderr').write_bytes(result.stderr)
        if result.returncode or source.stat().st_size>512*1048576:raise ValueError('Pinned QEMU package extraction failed or exceeded the expanded byte bound')
    selected={v['member']:(name,v) for name,v in members.items()};seen=set()
    with tarfile.open(source,'r:*') as tar:
        for member in tar:
            if member.name not in selected:continue
            name,pin=selected[member.name]
            if name in seen or not member.isfile() or member.size!=pin['bytes']:raise ValueError('Pinned member type,size or multiplicity differs: '+name)
            seen.add(name);p=home/name;p.parent.mkdir(parents=True,exist_ok=True)
            with tar.extractfile(member) as src,p.open('xb') as dst:shutil.copyfileobj(src,dst)
            if sha(p)!=pin['sha256']:raise ValueError('Extracted member checksum differs: '+name)
            p.chmod(0o755 if name.startswith('bin/') else 0o644)
    if seen!=set(members):raise ValueError('Pinned archive member family is incomplete')
def health(home,profile,report):
    work=report/'health';work.mkdir();n=12;m=32;rng=random.Random(20261027);genotypes=[]
    for j in range(m):
        row=[rng.randrange(3) for _ in range(n)]
        if sum(row)==0:row[0]=1
        if sum(row)==2*n:row[0]=1
        genotypes.append(row)
    with (work/'cohort.bed').open('xb') as f:
        f.write(bytes([0x6c,0x1b,1]))
        for row in genotypes:
            for start in range(0,n,4):f.write(bytes([sum({0:0,1:2,2:3}[g]<<(2*i) for i,g in enumerate(row[start:start+4]))]))
    (work/'cohort.bim').write_text(''.join(f'1 m{j+1} 0 {j+1} A C\n' for j in range(m)),encoding='ascii')
    (work/'cohort.fam').write_text(''.join(f'f s{i+1} 0 0 {1+i%2} -9\n' for i in range(n)),encoding='ascii')
    pin=LOCK['profiles'][profile];command=[]
    if pin['emulated']:command.extend([str(home/'bin/qemu-x86_64-static'),'-cpu','qemu64'])
    command.extend([str(home/'bin/admixture'),'--seed=11','-j1','-C','0.000001','-c','3','cohort.bed','1'])
    env=dict(PATH='/usr/bin:/bin',HOME=str(work),TMPDIR=str(work),LANG='C',LC_ALL='C',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1')
    with (work/'stdout').open('xb') as out,(work/'stderr').open('xb') as err:
        child=subprocess.Popen(command,cwd=work,env=env,stdout=out,stderr=err,start_new_session=True)
        try:code=child.wait(timeout=60)
        except BaseException:
            try:os.killpg(child.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            child.wait(timeout=10);raise
    log=(work/'stdout').read_text(errors='replace')
    if code or f'ADMIXTURE Version {pin["version"]}' not in log:raise Unavailable('Actual original fitting health failed; help-only success is insufficient. Inspect retained health/stdout and stderr')
    def matrix(name,rows):
        lines=(work/name).read_text().splitlines()
        if len(lines)!=rows or any(len(line.split())!=1 for line in lines):raise ValueError('Incomplete original health parameter matrix')
        values=[float(line) for line in lines]
        if any(not math.isfinite(x) or x<0 or x>1 for x in values):raise ValueError('Invalid health parameter')
        return values
    q=matrix('cohort.1.Q',n);p=matrix('cohort.1.P',m)
    if any(abs(x-1)>0.0000005 for x in q):raise ValueError('K1health Q rows are not unit rows')
    matches=re.findall(r'^Loglikelihood: ([^\r\n]+)$',log,re.M)
    if len(matches)!=1:raise ValueError('Missing/ambiguous original health likelihood')
    reported=float(matches[0]);lower=0.;upper=0.
    for row,frequency in zip(genotypes,p):
        counted=sum(row);total=2*n;mle=counted/total
        if abs(frequency-mle)>0.000000500001:raise ValueError('Original health P does not match counted BIMallele2 K1MLE')
        a=max(0.,frequency-0.0000005);b=min(1.,frequency+0.0000005)
        def term(f):return counted*math.log(f)+(total-counted)*math.log1p(-f)
        lower+=min(term(a),term(b));upper+=term(max(a,min(b,mle)))
    if not math.isfinite(reported) or reported+0.0000005<lower-1e-9 or reported-0.0000005>upper+1e-9:raise ValueError('Original health likelihood differs from independent counted-allele binomial bounds')
    return dict(status='passed-original-K1-likelihood-and-matrix-health',command=command,exit_code=code,samples=n,markers=m,counted_P_allele='BIM allele2',original_loglikelihood=reported,native_likelihood_bounds=[lower,upper],files={p.name:sha(p) for p in work.iterdir() if p.is_file()},scope='Deterministic actual fitting and K1 likelihood/shape health; not multicomponent accuracy,convergence/calibration or historical/full-paper certification')
def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--profile',choices=sorted(LOCK['profiles']),required=True)
    for key in ['home','cache','report']:parser.add_argument('--'+key,type=Path,required=True)
    parser.add_argument('--archive-dir',type=Path,action='append',default=[]);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');args=parser.parse_args(argv)
    report=None;owns_report=False;record=dict(status='starting',profile=args.profile,lock_sha256=LOCK_SHA,permissions=dict(install=args.allow_install,download=args.allow_download),started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),required_action=RECOVERY)
    try:
        home=private(args.home);cache=private(args.cache);report=private(args.report);references=[private(p) for p in args.archive_dir]
        if home==cache or home==report or cache==report or any(a in b.parents or b in a.parents for a,b in [(home,cache),(home,report),(cache,report)]):raise ValueError('Home,cache and report must be distinct,non-nested directories')
        if report.exists():raise ValueError('Report already exists; preserve it and choose a new report')
        report.mkdir(parents=True);owns_report=True;actual_host=host();record['host']=actual_host;record['home']=str(home)
        if home.exists():
            meta=verify_home(home,args.profile,actual_host);record.update(status='reused-verified-admitted-runtime',health=meta['health'],runtime_marker_sha256=sha(home/MARKER));code=0
        else:
            if not args.allow_install:raise Unavailable('Installation is not authorized: --allow-install absent; no runtime home was created')
            cache.mkdir(parents=True,exist_ok=True)
            if not cache.is_dir():raise ValueError('Cache must be a directory')
            pin=LOCK['profiles'][args.profile];archives={name:obtain(name,cache,references,args.allow_download) for name in sorted({v['archive'] for v in pin['members'].values()})}
            home.mkdir(parents=True,mode=0o700)
            for name,archive in archives.items():extract_selected(archive,{k:v for k,v in pin['members'].items() if v['archive']==name},home,report)
            passed=health(home,args.profile,report);meta=dict(schema_version=1,tool='ADMIXTURE',profile=args.profile,version=pin['version'],lock_sha256=LOCK_SHA,host=actual_host,files={name:v['sha256'] for name,v in pin['members'].items()},health=passed,upstream_binaries_redistributed=False,global_host_configuration_changed=False)
            write(home/MARKER,meta);verify_home(home,args.profile,actual_host);record.update(status='installed-and-admitted-original-runtime',health=passed,runtime_marker_sha256=sha(home/MARKER));code=0
    except Unavailable as e:record.update(status='skipped-unavailable-dependency',error=str(e));code=3
    except Exception as e:record.update(status='failed-setup-retained',error=repr(e));code=2
    record['exit_code']=code
    if owns_report and report is not None and report.is_dir() and not (report/'installation.json').exists():
        write(report/'installation.json',record);(report/'WARNINGS.md').write_text('# ADMIXTURE '+('SETUP WARNINGS REQUIRE REVIEW' if code==0 else 'SETUP SKIPPED' if code==3 else 'SETUP FAILED')+'\n\n'+record.get('error','Pinned private binaries admitted after actual scientific health. Legacy is unsupported and explicitly emulated; originals are not redistributed.')+'\n\n'+RECOVERY+'\n',encoding='utf-8')
    print(json.dumps({k:record[k] for k in ['status','exit_code','profile','lock_sha256']})+(' '+record['error'] if 'error' in record else ''))
    if code:print(RECOVERY,file=sys.stderr)
    return code
if __name__=='__main__':raise SystemExit(main())
