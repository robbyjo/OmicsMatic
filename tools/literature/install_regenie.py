"""Private pinned REGENIE runtimes; Windows builds use an existing read-only Cygwin toolchain."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import signal
import stat
import subprocess
import sys
import tarfile
import tempfile
import zipfile

from install_aligner_dependencies import download, extract_tar, target, verified, mark

ROOT=Path(__file__).resolve().parents[2]
VERSION='4.1.3'
MARKER='.jlinalg-regenie.json'
SOURCE={'filename':'regenie-v4.1.3.tar.gz','url':'https://github.com/rgcgithub/regenie/archive/refs/tags/v4.1.3.tar.gz','sha256':'5a8ea6ce8a89693cbd272dd64290d361285e0aa858376d25c29284dc58a57b0d','size':10013825}
BGEN={'filename':'bgen-v1.1.7.tar.gz','url':'https://code.enkre.net/bgen/tarball/release/v1.1.7','sha256':'6476b077af6c8e98e85fd7e09f58cb3fdf143ff91850c984248fd4dc2d74a8c3','size':25476935}
BINARIES={
 'linux-x86_64':{'filename':'regenie_v4.1.3.gz_x86_64_Linux.zip','url':'https://github.com/rgcgithub/regenie/releases/download/v4.1.3/regenie_v4.1.3.gz_x86_64_Linux.zip','sha256':'6e271dbcbeee2326fbba67de6297a137b56e6b2661b1aa510a62c40dba286b10','size':4044281},
 'macos-x86_64':{'filename':'regenie_v4.1.3.gz_x86_64_OSX.zip','url':'https://github.com/rgcgithub/regenie/releases/download/v4.1.3/regenie_v4.1.3.gz_x86_64_OSX.zip','sha256':'4eea1bb88416e3997a8f574e4f526a7a0dcc3a40145bc97cfea604952f40b885','size':4478134}}
PATCHES=[
 {'name':'regenie-bgen-cygwin-default-source.patch','patch_sha256':'dc040e3d37be910a599ade3dfcc26c1001c81f6b76e663a81a526f19204d9b6f','file':'wscript','original':'e41d4347ff1a9ca2eb85f7dc62828af23bde4ed2ca05ce6ba0c020e076efe478','patched':'6bc3788056e42d3b167cc1f3ff416638294602e6bb30fa9377b390909be2c6b1','old':"cfg.env.CXXFLAGS = [ '-Wall'",'new':"cfg.env.CXXFLAGS = [ '-D_DEFAULT_SOURCE', '-Wall'",'scope':'Build-only Cygwin feature visibility for legacy Boost getpagesize under C++11.'},
 {'name':'regenie-bgen-standard-streampos.patch','patch_sha256':'5b27e3dec6a112a6dbf0ad41b850dade88c9f30b5cb9357c7750eab2752de519','file':'src/View.cpp','original':'4ee12c89f2042b9dbd5e5423ee42ffb1f328158df62f4d18a4a17e88ff3d11ac','patched':'acae9fb55e5d90df5e26759d85f5581357bfd107d80f0d7e4f93574c8dabf7e7','old':'std::ios::streampos origin','new':'std::streampos origin','scope':'Standard type spelling for the same BGEN tellg file-metadata origin; no genotype/statistical algorithm change.'}]

def digest(path):
    with path.open('rb') as stream:return hashlib.file_digest(stream,'sha256').hexdigest()

def profile(system=None,machine=None):
    system=system or sys.platform;machine=(machine or platform.machine()).lower()
    if machine not in {'amd64','x86_64'}:return None
    return 'windows-cygwin-source' if system=='win32' else 'linux-x86_64' if system.startswith('linux') else 'macos-x86_64' if system=='darwin' else None

def identity(selected):
    value={'version':VERSION,'profile':selected,'archive_sha256':SOURCE['sha256'] if selected=='windows-cygwin-source' else BINARIES[selected]['sha256']}
    if selected=='windows-cygwin-source':value.update(bgen_sha256=BGEN['sha256'],patch_sha256=[p['patch_sha256'] for p in PATCHES],build_protocol=1)
    return hashlib.sha256(json.dumps(value,sort_keys=True).encode()).hexdigest()

def process_environment(directory):
    env=os.environ.copy();env['OMP_NUM_THREADS']='1';env['OPENBLAS_NUM_THREADS']='1';env['LC_ALL']='C'
    if sys.platform=='win32':env['PATH']=str(directory)+os.pathsep+str(Path(os.environ['SystemRoot'])/'System32')+os.pathsep+os.environ['SystemRoot']
    else:env['PATH']=str(directory)+os.pathsep+env.get('PATH','')
    return env

def execute(command,cwd,environment,report,name,record,timeout):
    entry={'name':name,'command':[str(x) for x in command],'cwd':str(cwd),'log':str(report/(name+'.log'))};record.setdefault('commands',[]).append(entry)
    with(report/(name+'.log')).open('w',encoding='utf-8') as log:
        child=subprocess.Popen(entry['command'],cwd=cwd,env=environment,stdout=log,stderr=subprocess.STDOUT,start_new_session=sys.platform!='win32')
        try:code=child.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            if sys.platform=='win32':subprocess.run([str(Path(os.environ['SystemRoot'])/'System32/taskkill.exe'),'/PID',str(child.pid),'/T','/F'],stdout=log,stderr=subprocess.STDOUT,timeout=60)
            else:os.killpg(child.pid,signal.SIGKILL)
            child.wait(timeout=60);entry.update(status='failed-timeout',timeout_seconds=timeout);raise RuntimeError(name+' exceeded its time limit; retained '+entry['log'])
    entry.update(exit_code=code,log_sha256=digest(report/(name+'.log')))
    if code:raise RuntimeError(name+' failed with exit '+str(code)+'; inspect '+entry['log'])
    return(report/(name+'.log')).read_text(encoding='utf-8')

def apply_bgen_patches(source):
    results=[]
    for patch in PATCHES:
        if digest(Path(__file__).with_name(patch['name']))!=patch['patch_sha256']:raise ValueError('Compatibility patch integrity mismatch: '+patch['name'])
        file=source/patch['file']
        if digest(file)!=patch['original']:raise ValueError('Unexpected source before compatibility patch: '+patch['file'])
        text=file.read_text(encoding='utf-8')
        if text.count(patch['old'])!=1:raise ValueError('Compatibility edit must match once')
        file.write_text(text.replace(patch['old'],patch['new'],1),encoding='utf-8',newline='\n')
        if digest(file)!=patch['patched']:raise ValueError('Unexpected patched source identity')
        results.append({k:v for k,v in patch.items() if k not in {'old','new'}})
    return results

def copy_cygwin_dependencies(executable,cygwin_bin,staging,environment,report,record):
    pending=[executable,cygwin_bin/'cygpath.exe'];seen=set();dest=staging/'bin';dest.mkdir(parents=True)
    while pending:
        source=pending.pop();key=source.name.casefold()
        if key in seen:continue
        if not source.is_file():raise FileNotFoundError('Required Cygwin runtime file missing: '+str(source))
        seen.add(key);shutil.copy2(source,dest/source.name)
        text=execute([cygwin_bin/'objdump.exe','-p',source],staging,environment,report,'imports-'+str(len(seen)),record,60)
        for dll in re.findall(r'DLL Name:\s*(\S+)',text):
            if dll.casefold().startswith('cyg'):pending.append(cygwin_bin/dll)

def source_build(args,staging,report,record):
    cyg=(args.cygwin_root/'bin').resolve();required=['g++','gcc','gfortran','make','cygpath','objdump']
    for name in required:
        if not(cyg/(name+'.exe')).is_file():raise FileNotFoundError('Existing Cygwin tool is missing: '+name+'.exe; supply --cygwin-root with gcc-g++, gcc-fortran, make, Python 3.8-3.10 and zlib-devel installed')
    python=next((cyg/(name+'.exe') for name in ['python3.9','python3.10','python3.8'] if(cyg/(name+'.exe')).is_file()),None)
    if python is None:raise FileNotFoundError('Legacy BGEN Waf needs an existing Cygwin Python 3.8-3.10; pass --cygwin-root for that read-only toolchain')
    env=process_environment(cyg);tools=[cyg/(name+'.exe') for name in required]+[python,cyg/'cygwin1.dll'];before={str(p):digest(p) for p in tools}
    record['toolchain_executable_sha256']=before
    for name in ['g++','gfortran','make']:execute([cyg/(name+'.exe'),'--version'],report,env,report,'toolchain-'+name.replace('+','p'),record,60)
    execute([python,'--version'],report,env,report,'toolchain-python',record,60)
    work=Path(tempfile.mkdtemp(prefix='regenie-source-',dir=report)).resolve();record['source_work_directory']=str(work)
    for item in [SOURCE,BGEN]:
        archive=download(item,args.cache,report)
        with tarfile.open(archive,'r:gz') as tar:extract_tar(tar,work)
    bgen=work/'v1.1.7';regenie=work/'regenie-4.1.3';record['source_patches']=apply_bgen_patches(bgen)
    execute([python,'-I','waf','configure','--mode=release'],bgen,env,report,'bgen-configure',record,300)
    execute([python,'-I','waf','-j'+str(args.build_threads)],bgen,env,report,'bgen-build',record,args.build_timeout)
    posix=execute([cyg/'cygpath.exe','-u','-a',bgen],work,env,report,'bgen-posix-path',record,60).strip()
    execute([cyg/'make.exe','-j'+str(args.build_threads),'BGEN_PATH='+posix,'CXX=g++ -D_DEFAULT_SOURCE','CFLAGS=-fopenmp -D_DEFAULT_SOURCE','FC=gfortran'],regenie,env,report,'regenie-build',record,args.build_timeout)
    for name,expected in before.items():
        if digest(Path(name))!=expected:raise ValueError('Existing toolchain changed during build; retain attempt and rebuild with stable tools: '+name)
    unchanged={}
    with tarfile.open(args.cache/SOURCE['filename'],'r:gz') as tar:
        for member in tar:
            if member.isfile() and Path(member.name).suffix in {'.cpp','.hpp','.cc','.h','.c','.f'}:
                expected=hashlib.sha256(tar.extractfile(member).read()).hexdigest()
                if digest(work/member.name)!=expected:raise ValueError('Unexpected REGENIE source change: '+member.name)
                unchanged[member.name]=expected
    record['unmodified_regenie_scientific_source_files']=len(unchanged)
    (report/'regenie-source-sha256.json').write_text(json.dumps(unchanged,indent=2)+'\n')
    copy_cygwin_dependencies(regenie/'regenie.exe',cyg,staging,env,report,record)
    licenses=staging/'licenses';licenses.mkdir();shutil.copy2(regenie/'LICENSE',licenses/'REGENIE-LICENSE.txt')
    if(bgen/'LICENSE_1_0.txt').exists():shutil.copy2(bgen/'LICENSE_1_0.txt',licenses/'BGEN-Boost-LICENSE.txt')
    record['gzip_text_io']='disabled: no HAS_BOOST_IOSTREAM; BGEN internal zlib/zstd remains available'

def install(args):
    args.environment=args.environment.resolve();args.cache=args.cache.resolve();args.report=args.report.resolve();args.report.mkdir(parents=True,exist_ok=True)
    selected=profile();record={'version':VERSION,'profile':selected,'environment':str(args.environment),'download_authorized':args.allow_download,'source_build_authorized':args.allow_source_build,'recorded_utc':dt.datetime.now(dt.timezone.utc).isoformat(),'analysis_validation':'Installation/version checks only; execute scientific fixtures and the project adapter checks separately.'}
    code=3
    if selected is None:record.update(status='skipped-platform-unavailable',error='Pinned profiles are Windows x86_64 Cygwin-source, Linux x86_64 and macOS x86_64')
    elif verified(args.environment,MARKER,identity(selected)):
        try:
            text=execute([args.environment/'bin'/('regenie.exe' if selected=='windows-cygwin-source' else 'regenie'),'--version'],args.report,process_environment(args.environment/'bin'),args.report,'reuse-version',record,60)
            if text.strip()!='v'+VERSION:raise ValueError('Installed version differs from pinned version')
            installed=json.loads((args.environment/MARKER).read_text(encoding='utf-8'))
            record.update(status='available-not-yet-analysis-validated',runtime_reused=True,installed_provenance=installed.get('provenance',{}),executable_sha256=digest(args.environment/'bin'/('regenie.exe' if selected=='windows-cygwin-source' else 'regenie')));code=0
        except Exception as error:record.update(status='skipped-runtime-unavailable',error=str(error))
    elif args.environment.exists():record.update(status='skipped-existing-environment',error='Existing unmanaged or integrity-failed runtime preserved; choose a new --environment')
    elif not args.allow_download:record.update(status='skipped-install-not-permitted',error='Downloads are not permitted for this invocation')
    elif selected=='windows-cygwin-source' and not args.allow_source_build:record.update(status='skipped-source-build-not-permitted',error='Windows profile needs explicit --allow-source-build and existing Cygwin prerequisites')
    else:
        try:
            args.environment.parent.mkdir(parents=True,exist_ok=True);staging=Path(tempfile.mkdtemp(prefix='.jlinalg-regenie-runtime-',dir=args.environment.parent)).resolve();record['retained_staging_directory']=str(staging)
            if selected=='windows-cygwin-source':source_build(args,staging,args.report,record)
            else:
                archive=download(BINARIES[selected],args.cache,args.report);work=staging/'archive';work.mkdir();size=0
                with zipfile.ZipFile(archive) as source:
                    for member in source.infolist():
                        target(work,member.filename);size+=member.file_size
                        if stat.S_ISLNK(member.external_attr>>16) or size>512*1024**2:raise ValueError('Unsafe/excessive official ZIP member')
                    source.extractall(work)
                candidates=[p for p in work.rglob('*') if p.is_file() and p.name.startswith('regenie')]
                if len(candidates)!=1:raise ValueError('Ambiguous executable in pinned archive')
                (staging/'bin').mkdir();shutil.copy2(candidates[0],staging/'bin/regenie');(staging/'bin/regenie').chmod(0o755)
            executable=staging/'bin'/('regenie.exe' if selected=='windows-cygwin-source' else 'regenie');text=execute([executable,'--version'],args.report,process_environment(staging/'bin'),args.report,'version',record,60)
            if text.strip()!='v'+VERSION:raise ValueError('Built/installed version differs from pinned version')
            record['executable_sha256']=digest(executable);record['status']='installed-not-yet-analysis-validated';mark(staging,MARKER,identity(selected))
            metadata=json.loads((staging/MARKER).read_text());metadata.update(version=VERSION,profile=selected,provenance=record);(staging/MARKER).write_text(json.dumps(metadata,indent=2)+'\n')
            if staging.parent!=args.environment.parent or args.environment.exists():raise ValueError('Installation publication target changed; retained staging is preserved')
            staging.rename(args.environment);code=0
        except Exception as error:record.update(status='skipped-install-failed',error=str(error))
    record['required_action']='If status is skipped, skip dependent REGENIE analyses. Where permitted run python tools/literature/install_regenie.py --allow-download (Windows additionally --allow-source-build --cygwin-root C:/cygwin64). For missing Cygwin prerequisites supply an existing toolchain with gcc-g++, gcc-fortran, make, Python 3.8-3.10 and zlib-devel, or use a permitted supported Linux/macOS host. Preserve existing/corrupt runtimes and choose a new destination. After installation run scientific/project fixtures; version alone is not validation.'
    (args.report/'dependency-status.json').write_text(json.dumps(record,indent=2)+'\n')
    (args.report/'WARNINGS.md').write_text('# REGENIE DEPENDENCY STATUS\n\n'+record['status']+'\n\n'+record.get('error','Installation is separate from scientific validation; source compiler warnings and compatibility changes remain in retained logs/provenance.')+'\n\nRequired action: '+record['required_action']+'\n')
    return record,code

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--allow-download',action='store_true');ap.add_argument('--allow-source-build',action='store_true');ap.add_argument('--environment',type=Path,default=ROOT/'build/omics-regenie');ap.add_argument('--cache',type=Path,default=ROOT/'build/omics-regenie-download');ap.add_argument('--report',type=Path,default=ROOT/'build/literature-review/regenie-dependency');ap.add_argument('--cygwin-root',type=Path,default=Path('C:/cygwin64'));ap.add_argument('--build-threads',type=int,default=4);ap.add_argument('--build-timeout',type=int,default=3600);args=ap.parse_args()
    if not 1<=args.build_threads<=8 or not 60<=args.build_timeout<=7200:ap.error('build-threads must be 1..8 and build-timeout 60..7200 seconds')
    record,code=install(args);print(json.dumps({k:v for k,v in record.items() if k not in {'commands','toolchain_executable_sha256'}}));return code

if __name__=='__main__':raise SystemExit(main())
