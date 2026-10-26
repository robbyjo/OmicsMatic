"""Build pinned STAR privately on Windows; installation is distinct from analysis validation."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
from install_aligner_dependencies import LOCK, digest, download, extract_tar, mark, toolchain, verified

ROOT=Path(__file__).resolve().parents[2]
STAR={'version':'2.7.11b','filename':'STAR-2.7.11b.tar.gz',
      'url':'https://github.com/alexdobin/STAR/archive/refs/tags/2.7.11b.tar.gz',
      'sha256':'3f65305e4112bd154c7e22b333dcdaafc681f4a895048fa30fa7ae56cac408e7','size':12466670}
MARKER='.jlinalg-star.json'
FLAGS='-D_DEFAULT_SOURCE -include unistd.h -DSHM_NORESERVE=0'
PATCH=Path(__file__).with_name('star-initialize-transform-quant.patch')
PARAMETERS_SHA256='f7ae6d62627b976614c6e804846d9b3c7a89a442f8d73a5e591fee1a67194706'
PATCH_REASON='Initialize outQuant alongside the other transform-output flags. Upstream leaves it indeterminate when transformation is None; this caused GeneCounts to read /geneInfo.tab in actual Windows fixtures. No scoring or alignment algorithm changes.'


def embed_defaults(source):
    """Equivalent to xxd -i parametersDefault; no source/algorithm edits."""
    data=(source/'parametersDefault').read_bytes()
    rows=[', '.join(f'0x{x:02x}' for x in data[i:i+12]) for i in range(0,len(data),12)]
    (source/'parametersDefault.xxd').write_text('unsigned char parametersDefault[] = {\n  '+',\n  '.join(rows)+'\n};\nunsigned int parametersDefault_len = '+str(len(data))+';\n',encoding='ascii',newline='\n')


def initialize_transform_quant(source):
    """One exact, documented initialization fix; reject source drift rather than patching heuristically."""
    path=source/'ParametersGenome.cpp'
    if digest(path)!=PARAMETERS_SHA256:raise ValueError('STAR ParametersGenome.cpp differs from the pinned source; initialization fix was not applied')
    before=b'    transform.outYes = transform.outSAM = transform.outSJ = false;'
    after=b'    transform.outYes = transform.outSAM = transform.outSJ = transform.outQuant = false;'
    data=path.read_bytes()
    if data.count(before)!=1:raise ValueError('Initialization patch target is not unique')
    expected='--- a/source/ParametersGenome.cpp\n+++ b/source/ParametersGenome.cpp\n@@ -26,1 +26,1 @@\n-'+before.decode()+'\n+'+after.decode()+'\n'
    if PATCH.read_text(encoding='utf-8')!=expected:raise ValueError('Recorded STAR source patch differs from its exact application')
    path.write_bytes(data.replace(before,after))
    return {'path':'source/ParametersGenome.cpp','original_sha256':PARAMETERS_SHA256,'patched_sha256':digest(path),'patch_sha256':digest(PATCH),
        'reason':PATCH_REASON}


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--allow-download',action='store_true');parser.add_argument('--allow-source-build',action='store_true')
    parser.add_argument('--environment',type=Path,default=ROOT/'build/omics-star')
    parser.add_argument('--cache',type=Path,default=ROOT/'build/omics-alignment-download')
    parser.add_argument('--toolchain',type=Path,default=ROOT/'build/omics-bwa-toolchain')
    parser.add_argument('--report',type=Path,default=ROOT/'build/literature-review/star-dependency')
    args=parser.parse_args();environment=args.environment.resolve();report=args.report.resolve();report.mkdir(parents=True,exist_ok=True)
    identity=STAR['sha256']+':'+digest(LOCK)+':'+FLAGS+':sse4.1:embed-v1:quant-init:'+digest(PATCH)
    record={'version':STAR['version'],'source':STAR['url'],'source_sha256':STAR['sha256'],'environment':str(environment),
        'download_authorized':args.allow_download,'source_build_authorized':args.allow_source_build,
        'recorded_utc':dt.datetime.now(dt.timezone.utc).isoformat(),
        'platform_scope':'Private MSYS2 POSIX Windows x86_64 build, SSE4.1 required. Upstream supports Linux/macOS; this is not an official Windows binary.',
        'source_policy':'Pinned upstream source plus one recorded outQuant=false initialization fix in ParametersGenome.cpp; no scoring/alignment algorithm changes. Python emits the xxd-equivalent parameter byte array. C++ flags include POSIX declarations and define optional Linux SHM_NORESERVE as zero. Adapter forces NoSharedMemory and stages uncompressed FASTQ without shell commands.'}
    if verified(environment,MARKER,identity):record['status']='available-not-yet-analysis-validated'
    elif not args.allow_download:record['status']='skipped-install-not-permitted'
    elif environment.exists():record.update(status='skipped-existing-environment',error='Unmanaged/integrity-failed runtime preserved; choose a new --environment')
    elif not args.allow_source_build:record['status']='skipped-source-build-not-permitted'
    elif sys.platform!='win32' or platform.machine().lower() not in {'amd64','x86_64'}:
        record.update(status='skipped-platform-unavailable',error='This installer targets Windows x86_64; provide upstream STAR 2.7.11b natively on Linux/macOS')
    else:
        try:
            archive=download(STAR,args.cache.resolve(),report)
            toolchain(args.toolchain.resolve(),args.cache.resolve(),report,record)
            environment.parent.mkdir(parents=True,exist_ok=True)
            staging=Path(tempfile.mkdtemp(prefix='.jlinalg-star-',dir=environment.parent)).resolve();record['installation_staging_directory']=str(staging)
            with tarfile.open(archive,'r:gz') as source:extract_tar(source,staging)
            source=staging/'STAR-2.7.11b/source';embed_defaults(source);record['source_patch']=initialize_transform_quant(source)
            build_env=os.environ.copy();build_env['PATH']=str(args.toolchain.resolve()/'usr/bin')+os.pathsep+build_env.get('PATH','')
            command=[str(args.toolchain.resolve()/'usr/bin/bash.exe'),'--noprofile','--norc','-c',
                'make -j2 STAR CXX=g++ GIT_CHECK=1 BUILD_DATE=1970-01-01T00:00:00Z BUILD_PLACE=jlinalg-private-msys2 CXXFLAGS_SIMD=-msse4.1 "CXXFLAGSextra='+FLAGS+'"']
            record['build_command']=command;record['toolchain_lock_sha256']=digest(LOCK)
            if (report/'build.log').exists():shutil.copy2(report/'build.log',report/('build-history-'+digest(report/'build.log')[:16]+'.log'))
            with (report/'build.log').open('w',encoding='utf-8') as log:
                result=subprocess.run(command,cwd=source,env=build_env,stdout=log,stderr=subprocess.STDOUT,timeout=1200)
            record['build_exit_code']=result.returncode
            if result.returncode:raise ValueError('STAR source build failed; inspect retained build.log and staging directory')
            bindir=staging/'bin';bindir.mkdir();shutil.copy2(source/'STAR.exe',bindir/'STAR.exe');(staging/'tmp').mkdir()
            for dll in (args.toolchain.resolve()/'usr/bin').glob('*.dll'):shutil.copy2(dll,bindir/dll.name)
            result=subprocess.run([str(bindir/'STAR.exe'),'--version'],capture_output=True,text=True,timeout=30)
            record['version_probe']={'exit_code':result.returncode,'stdout':result.stdout,'stderr':result.stderr}
            if result.returncode or result.stdout.strip()!=STAR['version']:raise ValueError('STAR runtime/version probe failed, including possible unsupported CPU instruction set')
            mark(staging,MARKER,identity);staging.replace(environment);record['status']='installed-not-yet-analysis-validated'
        except Exception as failure:record.update(status='skipped-dependency-unavailable',error=str(failure))
    if not record['status'].startswith('skipped'):
        try:
            record.setdefault('source_patch',{'path':'source/ParametersGenome.cpp','original_sha256':PARAMETERS_SHA256,
                'patched_sha256':digest(environment/'STAR-2.7.11b/source/ParametersGenome.cpp'),'patch_sha256':digest(PATCH),'reason':PATCH_REASON})
            result=subprocess.run([str(environment/'bin/STAR.exe'),'--version'],capture_output=True,text=True,timeout=30)
            record['version_probe']={'exit_code':result.returncode,'stdout':result.stdout,'stderr':result.stderr}
            if result.returncode or result.stdout.strip()!=STAR['version']:raise ValueError('Pinned STAR could not execute on this host')
        except Exception as failure:record.update(status='skipped-runtime-unavailable',error=str(failure))
    log=report/'build.log'
    if log.exists():
        warnings=[line for line in log.read_text(encoding='utf-8',errors='replace').splitlines() if 'warning:' in line.lower()]
        record['compiler_warning_count']=len(warnings);record['compiler_warnings_first_100']=warnings[:100]
    skipped=record['status'].startswith('skipped')
    record['action']=('Skip the dependent analysis. Where permitted run python tools/literature/install_star.py --allow-download --allow-source-build, then supply --star build/omics-star/bin/STAR.exe. If installation/building is forbidden, ask an administrator for STAR 2.7.11b and its runtime libraries. Preserve existing runtime/cache/staging and correct the named cause before using a new directory.' if skipped else 'Supply --star '+str(environment/'bin/STAR.exe')+'. Review compiler warnings in build.log. Version loading does not establish scientific analysis validity; only the typed adapter operations and tested inputs have local evidence.')
    path=report/'dependency-status.json'
    if path.exists():
        old=json.loads(path.read_text());record['history']=old.get('history',[])+[{k:v for k,v in old.items() if k!='history'}]
    path.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    (report/'WARNINGS.md').write_text(('# STAR UNAVAILABLE - ANALYSIS SKIPPED\n\n'+record.get('error',record['status'])+'\n\nRequired action: '+record['action']+'\n') if skipped else '# PRIVATE WINDOWS STAR BUILD\n\n'+record['platform_scope']+'\n\n'+record['source_policy']+'\n\nRequired action: '+record['action']+'\n',encoding='utf-8')
    print(json.dumps(record));return 3 if skipped else 0


if __name__=='__main__':raise SystemExit(main())
