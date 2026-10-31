#!/usr/bin/env python3
"""Build/reuse a pinned private original SAIGE runtime on Ubuntu24.04 x86_64.

Only setup invokes Pixi/compiler/package installation. Scientific commands use
direct Rscript. Downloads and installation require their explicit permissions.
Failed/changed/unmanaged homes and corrupt caches are preserved.
"""
import argparse,datetime as dt,hashlib,json,os,platform,re,shutil,signal,subprocess,sys,tarfile,urllib.error,urllib.request,zipfile
from pathlib import Path,PurePosixPath

HERE=Path(__file__).resolve().parent
LOCK_PATH=HERE/'saige-runtime-lock.json'
LOCK_RAW=LOCK_PATH.read_bytes();LOCK=json.loads(LOCK_RAW);LOCK_SHA=hashlib.sha256(LOCK_RAW).hexdigest()
MARKER='.jlinalg-saige.json'
RECOVERY=('Required action: use Ubuntu24.04 Linux x86_64 and Python3.12 for setup,with sufficient private build/disk resources. '
          'Run python3 tools/literature/install_saige.py --home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE '
          '--report NEW_REPORT --allow-install;add --allow-download only where permitted. A populated '
          '--package-cache can be reused;without download permission Pixi is forced offline. '
          'From Windows use Linux Java inside WSL for the SAIGE analysis. If installation is forbidden '
          'or the required runtime is unavailable,SKIP the dependent analysis. Preserve changed/unmanaged '
          'homes,corrupt caches and failed reports;correct the named cause and use new output/home paths. '
          'Native unrelated-select needs no SAIGE runtime and requires independently verified graph coverage.')

def sha(path):
    with Path(path).open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def private(path):
    path=Path(path).absolute()
    if any(p.is_symlink() for p in [path,*path.parents]):raise ValueError('Linked home/cache/report ancestor is unsupported: '+str(path))
    return path.resolve()

def host():
    release={}
    if Path('/etc/os-release').is_file():release=dict(line.split('=',1) for line in Path('/etc/os-release').read_text().splitlines() if '=' in line)
    if platform.system()!='Linux' or platform.machine().lower() not in {'x86_64','amd64'} or release.get('ID','').strip('"')!='ubuntu' or release.get('VERSION_ID','').strip('"')!='24.04' or sys.version_info[:2]!=(3,12):raise ValueError('This setup profile requires Ubuntu24.04 Linux x86_64 with Python3.12')
    return dict(OS=platform.system(),architecture=platform.machine(),Ubuntu='24.04',Python=platform.python_version())

def relative(name):
    p=PurePosixPath(name)
    if p.is_absolute() or '\\' in name or ':' in name or not p.parts or any(x in {'','.','..'} for x in p.parts):raise ValueError('Unsafe archive/runtime member: '+name)
    return p

def obtain(name,cache,reference,download):
    pin=LOCK['archives'][name];target=cache/name
    if target.exists():
        if target.is_symlink() or not target.is_file() or target.stat().st_size!=pin['bytes'] or sha(target)!=pin['sha256']:raise ValueError('Corrupt/linked archive cache member;preserve and choose new cache: '+str(target))
        return target
    if reference and (reference/name).is_file():
        source=reference/name
        if source.is_symlink() or source.stat().st_size!=pin['bytes'] or sha(source)!=pin['sha256']:raise ValueError('Reference archive differs: '+name)
        with source.open('rb') as src,target.open('xb') as dst:shutil.copyfileobj(src,dst)
        return target
    if not download:raise PermissionError('Missing pinned archive and --allow-download absent: '+name)
    temporary=cache/(name+'.part')
    if temporary.exists():raise ValueError('Retained partial archive already exists;preserve it and use a new cache: '+str(temporary))
    error=None
    for url in pin['urls']:
        try:
            request=urllib.request.Request(url,headers={'User-Agent':'JLinAlg-pinned-SAIGE-runtime/1.0'})
            with urllib.request.urlopen(request,timeout=90) as src,temporary.open('xb') as dst:
                total=0
                while chunk:=src.read(1048576):
                    total+=len(chunk)
                    if total>pin['bytes']:raise ValueError('Pinned archive byte bound exceeded: '+name)
                    dst.write(chunk)
            if temporary.stat().st_size!=pin['bytes'] or sha(temporary)!=pin['sha256']:raise ValueError('Downloaded archive checksum/size differs: '+name)
            temporary.rename(target);return target
        except urllib.error.HTTPError as failure:
            error=failure
            if temporary.exists():raise
            if failure.code not in {404,410}:raise
    raise ValueError('Pinned archive unavailable from official URLs: '+name+';'+str(error))

def extract_zip(archive,target):
    target.mkdir(exist_ok=False);seen=set();root=None;total=0
    with zipfile.ZipFile(archive) as bundle:
        for info in bundle.infolist():
            full=relative(info.filename.rstrip('/'))
            if root is None:root=full.parts[0]
            if full.parts[0]!=root:raise ValueError('Source ZIP has multiple roots')
            if len(full.parts)==1:continue
            name=PurePosixPath(*full.parts[1:]);relative(str(name))
            if info.is_dir():continue
            if (info.external_attr>>16)&0o170000==0o120000:raise ValueError('Source ZIP contains a symlink')
            if str(name) in seen:raise ValueError('Duplicate source ZIP member')
            seen.add(str(name));total+=info.file_size
            if len(seen)>20000 or total>512*1048576:raise ValueError('Source ZIP extraction bound exceeded')
            path=target/str(name);path.parent.mkdir(parents=True,exist_ok=True)
            with bundle.open(info) as src,path.open('xb') as dst:shutil.copyfileobj(src,dst)

def extract_tar(archive,target,kind):
    target.mkdir(exist_ok=False);seen=set();total=0;links=[]
    with tarfile.open(archive) as bundle:
        for info in bundle:
            full=relative(info.name)
            parts=full.parts if kind=='pixi' else full.parts[1:]
            if not parts:continue
            name=PurePosixPath(*parts);relative(str(name))
            if info.isdir():continue
            if info.issym() or info.islnk():
                if kind!='plink' or str(name).startswith('2.0/include/'):raise ValueError('Unexpected linked required source member')
                links.append(dict(path=str(name),target=info.linkname));continue
            if not info.isfile() or str(name) in seen:raise ValueError('Nonregular/duplicate source tar member')
            seen.add(str(name));total+=info.size
            if len(seen)>20000 or total>512*1048576:raise ValueError('Source tar extraction bound exceeded')
            path=target/str(name);path.parent.mkdir(parents=True,exist_ok=True)
            with bundle.extractfile(info) as src,path.open('xb') as dst:shutil.copyfileobj(src,dst)
            if kind=='pixi':path.chmod(0o755)
    return links

def original_sources(source):
    result={}
    for name,digest in LOCK['original_reference_files'].items():
        path=source/str(relative(name))
        if not path.is_file() or path.is_symlink() or sha(path)!=digest:raise ValueError('Original source member differs: '+name)
        result[name]=digest
    return result

def conda_packages(env):
    records={}
    for path in sorted((env/'conda-meta').glob('*.json')):
        row=json.loads(path.read_text());name=row['name']
        if name in records:raise ValueError('Duplicate installed package record')
        records[name]={key:row.get(key) for key in ['version','build','sha256','url']}
    if records!=LOCK['conda_packages']:raise ValueError('Installed package family differs from the authenticated environment lock')
    return records

def family(home):
    files={};links={}
    for name in LOCK['inventory_roots']:
        root=home/name
        if not root.is_dir() or root.is_symlink():raise ValueError('Required runtime directory unavailable/linked: '+name)
        for parent,dirs,names in os.walk(root,followlinks=False):
            for child in dirs+names:
                path=Path(parent)/child;key=path.relative_to(home).as_posix()
                if path.is_symlink():links[key]=os.readlink(path)
                elif path.is_file():files[key]=sha(path)
                elif not path.is_dir():raise ValueError('Nonregular runtime member: '+key)
    for name in LOCK['inventory_files']:
        path=home/name
        if not path.is_file() or path.is_symlink():raise ValueError('Missing/linked runtime member: '+name)
        files[name]=sha(path)
    if len(files)+len(links)>120000:raise ValueError('Runtime inventory exceeds120000members')
    return dict(files=files,links=links)

def external_file(path):
    p=Path(path)
    if not p.is_file():raise ValueError('Missing required OS dependency: '+str(p))
    return dict(real_path=str(p.resolve()),sha256=sha(p))

def external_inventory(home,actual):
    env=home/'source/.pixi/envs/default';result={};elfs=[]
    elfs=[env/'bin/Rscript',env/'lib/R/bin/exec/R']+[Path(name) for name in actual['DLL_paths'] if name.startswith('/')]
    loader={'PATH':'/usr/bin:/bin','LC_ALL':'C','LD_LIBRARY_PATH':str(env/'lib')+':'+str(env/'lib/R/lib')}
    for p in elfs:
        with p.open('rb') as stream:is_elf=stream.read(4)==b'\x7fELF'
        if not is_elf:continue
        r=subprocess.run(['/usr/bin/ldd',str(p)],env=loader,capture_output=True,text=True,timeout=30)
        if r.returncode or 'not found' in r.stdout:raise ValueError('Unresolved ELF dependency: '+str(p)+';'+r.stdout+r.stderr)
        for line in r.stdout.splitlines():
            match=re.match(r'^\s*(?:\S+\s+=>\s+)?(/.+?)\s+\(0x[0-9a-f]+\)\s*$',line)
            if match:
                dependency=Path(match.group(1))
                if not dependency.resolve().is_relative_to(home):result[str(dependency)]=external_file(dependency)
    for p in ['/etc/os-release','/bin/bash','/usr/bin/sed','/usr/bin/uname','/usr/bin/dirname','/usr/bin/ldd']:
        result[p]=external_file(p)
        if p in {'/etc/os-release','/usr/bin/ldd'}:continue
        r=subprocess.run(['/usr/bin/ldd',p],env={'PATH':'/usr/bin:/bin','LC_ALL':'C'},capture_output=True,text=True,timeout=30)
        if r.returncode or 'not found' in r.stdout:raise ValueError('Unresolved OS command dependency: '+p)
        for line in r.stdout.splitlines():
            match=re.match(r'^\s*(?:\S+\s+=>\s+)?(/.+?)\s+\(0x[0-9a-f]+\)\s*$',line)
            if match:result[match.group(1)]=external_file(match.group(1))
    return result

def environment(report,home,package_cache,download):
    env={key:value for key,value in os.environ.items() if not key.startswith(('R_','R_LIB','LD_','DYLD_','PYTHON','PIXI','CONDA','MAMBA')) and key not in {'BASH_ENV','ENV','MAKEFLAGS','LIBRARY_PATH','CPATH','C_INCLUDE_PATH','CPLUS_INCLUDE_PATH'}}
    isolated=report/'isolated-home';isolated.mkdir(exist_ok=True)
    env.update(PATH='/usr/bin:/bin',HOME=str(isolated),LC_ALL='C.UTF-8',LANGUAGE='en',R_LIBS_USER='/nonexistent',R_LIBS_SITE='',R_ENVIRON_USER='/dev/null',R_PROFILE_USER='/dev/null',OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',MAKEFLAGS='-j1',PIXI_NO_CONFIG='true',PIXI_NO_PROGRESS='true',PIXI_NO_PATH_UPDATE='1',PIXI_NO_HARD_LINKS='true',PIXI_CACHE_DIR=str(package_cache),PIXI_HOME=str(home/'setup-pixi-home'),PIXI_OFFLINE='false' if download else 'true')
    return env

def run(command,label,report,env,timeout,cwd=None):
    with (report/(label+'.stdout')).open('xb') as stdout,(report/(label+'.stderr')).open('xb') as stderr:
        child=subprocess.Popen(command,cwd=cwd or report,env=env,stdout=stdout,stderr=stderr,start_new_session=True)
        try:code=child.wait(timeout=timeout)
        except BaseException:
            os.killpg(child.pid,signal.SIGTERM)
            try:child.wait(timeout=10)
            except subprocess.TimeoutExpired:os.killpg(child.pid,signal.SIGKILL);child.wait()
            raise
    if code:raise ValueError(label+' failed with exit'+str(code)+';inspect '+str(report/(label+'.stderr')))

def probe(home,report,env,label):
    prefix=home/'source/.pixi/envs/default';run([str(prefix/'bin/Rscript'),'--vanilla',str(home/'adapter/saige_probe.R'),str(prefix)],label,report,env,120)
    value=json.loads((report/(label+'.stdout')).read_text())
    if value['R']!=LOCK['R'] or value['packages']!=LOCK['R_packages'] or value['library_paths']!=str(prefix/'lib/R/library'):raise ValueError('Actual original R/SAIGE package or library-path identity differs')
    return value

def verify(home):
    marker=home/MARKER
    if not marker.is_file() or marker.is_symlink() or marker.stat().st_size>32*1048576:raise ValueError('Missing/invalid admission marker;preserve this unmanaged home')
    value=json.loads(marker.read_text())
    if value.get('profile')!=LOCK['profile'] or value.get('runtime_lock_sha256')!=LOCK_SHA or value.get('home')!=str(home):raise ValueError('Runtime profile/lock/home identity differs')
    if sha(home/'runtime-lock.json')!=LOCK_SHA or sha(home/'source/pixi.lock')!=LOCK['environment_lock_sha256']:raise ValueError('Installed source/runtime lock changed')
    for name,digest in LOCK['original_program_files'].items():
        if sha(home/'source'/name)!=digest:raise ValueError('Original program changed: '+name)
    conda_packages(home/'source/.pixi/envs/default')
    if family(home)!=value.get('inventory'):raise ValueError('Runtime files/links changed;never re-seal this home')
    external=value.get('external_files',{})
    if not external or len(external)>4096:raise ValueError('Invalid OS dependency inventory')
    for name,item in external.items():
        if external_file(name)!=item:raise ValueError('OS runtime dependency changed: '+name)
    return value

def install(args,report):
    info=host();home=private(args.home);cache=private(args.cache);packages=private(args.package_cache or cache/'pixi');reference=private(args.reference_archives) if args.reference_archives else None
    if not str(home).isascii() or any(c.isspace() for c in str(home)):raise ValueError('Original source build requires an ASCII installation prefix without whitespace;select a new private --home such as /opt/omics-saige-v1. Unicode analysis input/output paths are separate and permitted.')
    for a,b in [(home,cache),(home,packages),(home,report),(report,cache),(report,packages)]:
        if a.is_relative_to(b) or b.is_relative_to(a):raise ValueError('Runtime home,cache and report must not overlap')
    env=environment(report,home,packages,args.allow_download)
    if home.exists():
        value=verify(home);probe(home,report,env,'reuse-probe');verify(home)
        return dict(status='reused-verified-runtime',home=str(home),profile=LOCK['profile'],marker_sha256=sha(home/MARKER),host=info)
    if not args.allow_install:raise PermissionError('Runtime absent and --allow-install not supplied;no home created')
    if reference and (reference.is_relative_to(home) or home.is_relative_to(reference)):raise ValueError('Reference archives must not overlap the runtime home')
    cache.mkdir(parents=True,exist_ok=True);packages.mkdir(parents=True,exist_ok=True);archives={name:obtain(name,cache,reference,args.allow_download) for name in LOCK['archives']}
    home.mkdir(parents=True,exist_ok=False);source=home/'source';extract_zip(archives['SAIGE-original-source.zip'],source);original_sources(source);shutil.copy2(HERE/'saige-environment.lock',source/'pixi.lock')
    if sha(source/'pixi.lock')!=LOCK['environment_lock_sha256']:raise ValueError('Bundled environment lock differs')
    extract_tar(archives['pixi-x86_64-unknown-linux-musl.tar.gz'],home/'bin','pixi');pixi=home/'bin/pixi'
    if sha(pixi)!=LOCK['pixi_binary_sha256']:raise ValueError('Original Pixi executable differs')
    base=[str(pixi),'install','--manifest-path',str(source/'pixi.toml'),'--locked','--no-config','--no-hard-links']
    if not args.allow_download:base.append('--offline')
    run(base,'environment-install',report,env,args.timeout,source);prefix=source/'.pixi/envs/default';conda_packages(prefix)
    plink_links=extract_tar(archives['plink-ng-v2.0.0-a.6.16.tar.gz'],source/'plink-ng','plink');extract_tar(archives['lintools_0.1.7.tar.gz'],home/'lintools-source','lintools')
    def pixi_run(command,label):
        call=[str(pixi),'run','--manifest-path',str(source/'pixi.toml'),'--locked','--no-config']
        if not args.allow_download:call.append('--offline')
        run(call+command,label,report,env,args.timeout,source)
    pixi_run(['R','CMD','INSTALL','--library='+str(prefix/'lib/R/library'),str(home/'lintools-source')],'lintools-install')
    objects=sorted((source/'plink-ng/2.0/include').glob('*.cc'))
    if not objects:raise ValueError('Original PLINK required sources unavailable')
    library=source/'libplink2_includes.a';pixi_run(['x86_64-conda-linux-gnu-cc','-std=c++14','-fPIC','-O3','-I'+str(prefix/'include'),'-L'+str(prefix/'lib'),'-o',str(library),*[str(p) for p in objects],'-shared','-lz','-lzstd','-lpthread','-lm','-ldeflate'],'plink-shared-build');shutil.copy2(library,prefix/'lib/libplink2_includes.a')
    pixi_run(['R','CMD','INSTALL','--library='+str(prefix/'lib/R/library'),str(source)],'SAIGE-original-install');original_sources(source)
    if sha(source/'pixi.lock')!=LOCK['environment_lock_sha256']:raise ValueError('Package manager changed the authenticated environment lock')
    adapter=home/'adapter';adapter.mkdir();probe_source=HERE.parents[1]/'src/main/resources/org/jlinalg/omics/saige_probe.R'
    if sha(probe_source)!=LOCK['probe_sha256']:raise ValueError('Bundled runtime probe differs')
    shutil.copy2(probe_source,adapter/'saige_probe.R');(home/'runtime-lock.json').write_bytes(LOCK_RAW);actual=probe(home,report,env,'original-runtime-probe');inventory=family(home);external=external_inventory(home,actual)
    metadata=dict(profile=LOCK['profile'],home=str(home),runtime_lock_sha256=LOCK_SHA,source_commit=LOCK['source_commit'],archives_sha256={name:pin['sha256'] for name,pin in LOCK['archives'].items()},environment_lock_sha256=LOCK['environment_lock_sha256'],conda_packages=LOCK['conda_packages'],inventory=inventory,external_files=external,versions=actual,source_build=dict(original_source_files=LOCK['original_reference_files'],unused_PLINK_links_not_materialized=plink_links,program_source_modified=False),scope=LOCK['scope'],recorded_utc=dt.datetime.now(dt.timezone.utc).isoformat());(home/MARKER).write_text(json.dumps(metadata,indent=2)+'\n');verify(home)
    return dict(status='installed-and-verified-original-runtime',home=str(home),profile=LOCK['profile'],host=info,marker_sha256=sha(home/MARKER),files=len(inventory['files']),links=len(inventory['links']),OS_dependencies=len(external),source_modified=False)

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--home',required=True,type=Path);parser.add_argument('--cache',required=True,type=Path);parser.add_argument('--report',required=True,type=Path);parser.add_argument('--package-cache',type=Path);parser.add_argument('--reference-archives',type=Path);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');parser.add_argument('--timeout',type=int,default=1800);args=parser.parse_args();report=private(args.report);report.mkdir(parents=True,exist_ok=False)
    try:
        if not 1<=args.timeout<=86400:raise ValueError('Require setup timeout1..86400seconds')
        result=install(args,report);code=0
    except PermissionError as failure:result=dict(status='skipped-installation-or-download-forbidden',error=str(failure),required_action=RECOVERY);code=3
    except Exception as failure:result=dict(status='failed-runtime-setup',error=str(failure),required_action=RECOVERY);code=2
    result.update(runtime_lock_sha256=LOCK_SHA,installer_sha256=sha(__file__),allow_install=args.allow_install,allow_download=args.allow_download);(report/'status.json').write_text(json.dumps(result,indent=2)+'\n');(report/'WARNINGS.md').write_text('# '+('SAIGE SETUP VERIFIED — WARNINGS REQUIRE REVIEW' if code==0 else 'SAIGE SETUP SKIPPED OR FAILED')+'\n\n'+json.dumps(result,indent=2)+'\n\n'+RECOVERY+'\n\nOriginal R import-replacement and R4.5.3-built-package notices underR4.5.2 are retained in logs. Review the original program warnings and analysis-specific model/allele/GRM/QC contracts before use.\n');print(json.dumps(result));print(RECOVERY,file=sys.stderr);return code
if __name__=='__main__':raise SystemExit(main())
