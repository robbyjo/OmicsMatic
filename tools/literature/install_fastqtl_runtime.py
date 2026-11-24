"""Stage one pinned FastQTL distribution runtime privately; never install OS packages or change shared libraries."""
from pathlib import Path
import argparse, hashlib, json, os, platform, re, shutil, subprocess, sys, urllib.request

PROFILE = 'fastqtl-2.184-v7-ubuntu-noble-prepared-v1'
PACKAGE = 'fastqtl_2.184+v7+dfsg-4build3_amd64.deb'
URL = 'https://archive.ubuntu.com/ubuntu/pool/universe/f/fastqtl/' + PACKAGE
PACKAGE_SHA = 'd9c9bd1b5481a893dfc5c086bff4948ecec25024c7676bedc76f02b64aaacec8'
# The executable and copyright identities are also enforced by the Java workflow.
BINARY_SHA = '707d47265f6c5ae722a098ac212a0755f9d2095481f76731d4f6313ef10b86c7'
COPYRIGHT_SHA = '40a6b2d085c44edc6a42da8b1d85162463af888c0b5841eafd7fb9d4b381ba7a'
LIBRARIES = set('ld-linux-x86-64.so.2 libRmath.so.1 libhts.so.3 libboost_iostreams.so.1.83.0 libboost_program_options.so.1.83.0 libgsl.so.27 libblas.so.3 libstdc++.so.6 libm.so.6 libgcc_s.so.1 libc.so.6 libdeflate.so.0 libhtscodecs.so.2 liblzma.so.5 libbz2.so.1.0 libz.so.1 libzstd.so.1 libgslcblas.so.0'.split())
ACTION = 'SKIP3: the dependent analysis was skipped. Use Linux x86_64 (Windows: WSL); obtain the named Ubuntu package and missing compatible dependencies through an installation method permitted by your environment, then retry --out with a NEW path. Pass each private library directory using --library-dir. Do not rename incompatible libraries or substitute another FastQTL version. Keep failed staging/logs. A staged runtime is not a completed scientific analysis. Do not publish this private bundle without reviewing source and license obligations for every dependency.'

class Unavailable(Exception):
    pass

def pin(path):
    h=hashlib.sha256();size=0
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1048576),b''):h.update(block);size+=len(block)
    return dict(bytes=size,sha256=h.hexdigest())

def write(path, value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',required=True,type=Path,help='NEW private runtime directory; parent must exist')
    parser.add_argument('--deb',type=Path,help='Already downloaded exact Ubuntu package; otherwise download it from the official archive')
    parser.add_argument('--library-dir',action='append',default=[],type=Path,help='Existing permitted dependency directory, read only; repeat for Rmath/BLAS/other private directories')
    args=parser.parse_args(argv);root=None
    try:
        if platform.system()!='Linux' or platform.machine() not in ('x86_64','amd64'):raise Unavailable('Linux x86_64 Python is required; on Windows run this installer in WSL')
        for command in ('dpkg-deb','ldd'):
            if shutil.which(command) is None:raise Unavailable('Required staging command is unavailable: '+command)
        target=args.out.absolute()
        if target.exists() or not target.parent.is_dir() or target.parent.resolve()!=target.parent:raise ValueError('Output must be NEW, with an existing physical parent')
        target.mkdir();root=target
        (root/'WARNINGS.md').write_text('# PRIVATE FASTQTL RUNTIME — NOT AN ANALYSIS\n\n'+ACTION+'\n',encoding='utf-8')
        archive=root/PACKAGE
        if args.deb:
            if not args.deb.is_file():raise Unavailable('Missing permitted package: '+str(args.deb))
            if args.deb.stat().st_size>1048576:raise ValueError('Package exceeds the pinned distribution size bound')
            shutil.copyfile(args.deb,archive)
        else:
            try:
                with urllib.request.urlopen(urllib.request.Request(URL,headers={'User-Agent':'OmicsMatic-source-audit/1'}),timeout=60) as response:
                    data=response.read(1048577)
                    if len(data)>1048576:raise ValueError('Oversized package response')
                    archive.write_bytes(data)
            except (OSError,TimeoutError) as error:raise Unavailable('Official package download is unavailable or prohibited: '+repr(error)) from error
        if pin(archive)!=dict(bytes=219618,sha256=PACKAGE_SHA):raise ValueError('Package identity differs; preserve it for review and obtain the exact official archive')
        unpacked=root/'package';env={'PATH':'/usr/bin:/bin','LC_ALL':'C','LANG':'C','TZ':'UTC'}
        command=[shutil.which('dpkg-deb'),'-x',str(archive),str(unpacked)]
        result=subprocess.run(command,env=env,capture_output=True,timeout=60);(root/'extraction.stdout').write_bytes(result.stdout);(root/'extraction.stderr').write_bytes(result.stderr)
        if result.returncode:raise Unavailable('Private package extraction failed or was prohibited; inspect extraction.stderr')
        binary=unpacked/'usr/bin/fastQTL';copyright=unpacked/'usr/share/doc/fastqtl/copyright'
        if pin(binary)['sha256']!=BINARY_SHA or pin(copyright)['sha256']!=COPYRIGHT_SHA:raise ValueError('Extracted executable/copyright identity mismatch')
        directories=[str(p.resolve(strict=True)) for p in args.library_dir]
        if any(':' in p or '\n' in p for p in directories):raise ValueError('Unsupported library-directory delimiter')
        if directories:env['LD_LIBRARY_PATH']=':'.join(directories)
        probe=subprocess.run([shutil.which('ldd'),str(binary)],env=env,capture_output=True,timeout=60)
        (root/'ldd.stdout').write_bytes(probe.stdout);(root/'ldd.stderr').write_bytes(probe.stderr)
        listing=probe.stdout.decode('utf-8',errors='strict')
        if probe.returncode or 'not found' in listing:raise Unavailable('Unresolved/incompatible libraries; read ldd.stdout and ldd.stderr for exact SONAMEs')
        resolved={}
        for line in listing.splitlines():
            if 'linux-vdso' in line:continue
            match=re.fullmatch(r'\s*(\S+) => (/\S+)\s+\([^)]*\)\s*',line)
            if match:name,path=match.groups()
            else:
                match=re.fullmatch(r'\s*(/\S+)\s+\([^)]*\)\s*',line)
                if not match:raise ValueError('Unrecognized loader report: '+line)
                path=match.group(1);name=Path(path).name
            if name in resolved:raise ValueError('Duplicate SONAME in loader report')
            resolved[name]=Path(path).resolve(strict=True)
        if set(resolved)!=LIBRARIES:raise Unavailable('Loader dependency set differs from the qualified profile: '+repr(sorted(set(resolved)^LIBRARIES)))
        (root/'bin').mkdir();(root/'lib').mkdir();members={}
        for source,destination in [(binary,root/'bin/fastQTL'),(copyright,root/'COPYRIGHT')]+[(p,root/'lib'/name) for name,p in sorted(resolved.items())]:
            if not source.is_file():raise ValueError('Nonregular runtime source')
            before=pin(source);shutil.copyfile(source,destination)
            if pin(source)!=before or pin(destination)!=before:raise ValueError('Runtime source changed during capture')
            destination.chmod(0o755 if destination.parent.name in ('lib','bin') else 0o644)
            members[str(destination.relative_to(root))]=dict(identity=before,source=str(source))
        write(root/'INSTALLATION.json',dict(status='STAGED_NOT_SCIENTIFICALLY_VALIDATED',profile=PROFILE,package=pin(archive),package_url=URL,members=members,OS_packages_installed=False,shared_libraries_modified=False,help_is_health_probe=False,required_action=ACTION))
        print('STAGED_NOT_SCIENTIFICALLY_VALIDATED: '+str(root)+'\nRun the project workflow on reviewed prepared inputs and read its warnings.');return 0
    except Exception as error:
        code=3 if isinstance(error,Unavailable) else 2;status='SKIP3_DEPENDENT_ANALYSIS_SKIPPED' if code==3 else 'FAIL2_NO_RUNTIME_ADMITTED'
        if root is not None:write(root/'FAILED.json',dict(status=status,error=repr(error),required_action=ACTION))
        print(status+': '+repr(error)+'\n'+ACTION,file=sys.stderr);return code

if __name__=='__main__':raise SystemExit(main())
