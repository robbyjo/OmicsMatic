#!/usr/bin/env python3
"""Explicit private setup of original HOMER5.1 tags/regions/coverage and UCSC conversion.

Only the C++ installation prefix is configured. No OS package, reference genome,
registration or analysis input is downloaded. Analysis never invokes this setup.
"""
import argparse
import base64
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import signal
import struct
import subprocess
import sys
import time
import urllib.request
import zipfile
import zlib
import install_homer as core
import install_homer_genome as genome
import install_homer_named as monitor
import homer_atac_health as fixture

HERE=Path(__file__).resolve().parent
RAW=(HERE/'homer-atac-lock.json').read_bytes();LOCK=json.loads(RAW);LOCK_SHA=hashlib.sha256(RAW).hexdigest()
MARKER='.jlinalg-homer-atac.json'
UTILITIES={'/bin/sh','/usr/bin/env','/usr/bin/mkdir','/usr/bin/mv','/usr/bin/ldd'}
RECOVERY=('Required action: use Linux x86_64 Python/Java, inside WSL on Windows. Where permitted run '
    'python3 tools/literature/install_homer_atac.py --home NEW_PRIVATE_ATAC_HOME --cache ARCHIVE_CACHE '
    '--report NEW_REPORT --allow-install --compiler /PATH/TO/g++ --make /PATH/TO/make '
    '--archive-dir VERIFIED_ORIGINAL_ARCHIVES --converter-path PINNED_BEDGRAPHTOBIGWIG. '
    'Alternatively add --allow-download where downloads are permitted. Supply the exact original5.1 '
    'archive and UCSC converter content pins in homer-atac-lock.json; a newer binary is not substituted. '
    'A C++11 compiler with headers,pthread,math and static C++/GCC runtime libraries is needed only at setup. '
    'Allow owned-process memory,time and workspace for source compilation; Java heap is separate. '
    'If execution,installation or monitoring is forbidden, or required software is absent/changed, '
    'SKIP the affected analysis, exit3. Other failures exit2. Preserve old homes,reports,partials and '
    'corrupt downloads; correct the named cause and use NEW paths. Inspect WARNINGS.md and stderr. '
    'No named genome,alignment,deduplication,RNA or Cell Ranger capability is admitted.')


def safe(path):
    result=core.private(path)
    if not re.fullmatch(r'/[A-Za-z0-9_./-]+',str(result)):raise core.Unavailable('Private ATAC paths need shell-safe ASCII names')
    return result


def execute(command,home,work,label,seconds=180,rss=8589934592,workspace=2147483648,environment=None,allowed=(0,)):
    began=time.monotonic();peak=0;env=core.env(work);env['PATH']=str(home/'bin')+':/usr/bin:/bin'
    if environment is not None:env=environment
    with (work/(label+'.stdout')).open('xb') as stdout,(work/(label+'.stderr')).open('xb') as stderr:
        child=subprocess.Popen(list(map(str,command)),cwd=work,env=env,stdout=stdout,stderr=stderr,start_new_session=True)
        try:
            while child.poll() is None:
                peak=max(peak,monitor.group_rss(child.pid));size=0
                for path in work.rglob('*'):
                    try:
                        if path.is_file():size+=path.stat().st_size
                    except FileNotFoundError:continue
                if peak>rss:raise core.Unavailable('Original ATAC setup process exceeded declared RSS allowance')
                if size>workspace:raise ValueError('Original ATAC setup workspace capacity exceeded')
                if time.monotonic()-began>seconds:raise core.Unavailable('Original ATAC setup timed out; inspect '+label+'.stderr')
                time.sleep(.1)
            code=child.returncode
        except BaseException:
            try:os.killpg(child.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            child.wait(timeout=10);raise
    if code not in allowed:raise core.Unavailable('Original ATAC setup operation exited '+str(code)+'; inspect '+str(work/(label+'.stderr')))
    return dict(command=list(map(str,command)),exit_code=code,seconds=time.monotonic()-began,maximum_observed_group_rss_bytes=peak,stdout_sha256=core.sha(work/(label+'.stdout')),stderr_sha256=core.sha(work/(label+'.stderr')))


def converter_file(args,cache):
    pin=LOCK['converter'];target=core.private(args.converter_path) if args.converter_path else cache/('bedGraphToBigWig-'+pin['sha256'][:16])
    if not target.exists():
        if args.converter_path or not args.allow_download:raise core.Unavailable('Pinned original UCSC converter absent; supply --converter-path or explicitly permit download')
        partial=target.with_name(target.name+'.partial')
        if partial.exists():raise ValueError('Prior converter partial exists; preserve it and choose a new cache')
        began=time.monotonic();size=0
        with urllib.request.urlopen(pin['url'],timeout=45) as response,partial.open('xb') as out:
            while True:
                block=response.read(1048576)
                if not block:break
                size+=len(block)
                if size>pin['bytes'] or time.monotonic()-began>180:raise ValueError('Converter download exceeded pinned size/time')
                out.write(block)
        if partial.stat().st_size!=pin['bytes'] or core.sha(partial)!=pin['sha256']:raise ValueError('Original converter content pin differs; preserve download, no version substitution')
        partial.rename(target)
    if target.is_symlink() or not target.is_file() or target.stat().st_size!=pin['bytes'] or core.sha(target)!=pin['sha256']:raise core.Unavailable('Pinned original converter is missing,linked or changed')
    return target


def obtain_archive(cache,archive_directory,download):
    references=[] if archive_directory is None else [archive_directory]
    archive=core.obtain('homer.v5.1.zip',cache,references,download);pin=LOCK['archives']['homer.v5.1.zip']
    if archive.stat().st_size!=pin['bytes'] or core.sha(archive)!=pin['sha256']:
        raise ValueError('Obtained HOMER archive differs from the separate ATAC content lock; no container/version substitution')
    return archive


def build(archive,home,report,args):
    compiler=core.private(args.compiler);make=core.private(args.make)
    for path in [compiler,make]:
        if not re.fullmatch(r'/[A-Za-z0-9_./+-]+',str(path)) or not path.is_file() or not os.access(path,os.X_OK):raise core.Unavailable('Missing or unsupported compiler/make executable: '+str(path))
    tools={str(p):dict(real_path=str(p.resolve()),sha256=core.sha(p.resolve())) for p in [compiler,make]};work=report/'source';work.mkdir();configured={}
    for name,pin in LOCK['build_sources'].items():
        raw=genome.member_bytes(archive,pin)
        if name=='cpp/SeqTag.cpp':
            lines=raw.splitlines(keepends=True)
            if lines[2]!=base64.b64decode(pin['original_home_line_base64']):raise ValueError('Original C++ installation line differs')
            lines[2]=('const char* HomerConfig::homeDirectory = "'+str(home)+'/";\n').encode();raw=b''.join(lines)
        target=work/Path(name).name;target.write_bytes(raw);configured[target.name]=hashlib.sha256(raw).hexdigest()
    env=core.env(work);env['PATH']=str(compiler.parent)+':'+str(make.parent)+':/usr/bin:/bin';recipe=LOCK['build_recipe'];limits=dict(seconds=args.build_timeout,rss=args.maximum_upstream_memory_bytes,workspace=args.workspace_bytes,environment=env)
    compiler_version=execute([compiler,'--version'],home,work,'compiler-version',**limits);make_version=execute([make,'--version'],home,work,'make-version',**limits)
    compilation=execute([make,'-B','COMPILER='+str(compiler),'DEBUG='+recipe['compile_flags'],*recipe['objects']],home,work,'compile',**limits)
    link=execute([compiler,*[work/name for name in recipe['objects']],*recipe['link_flags'],'-o',home/'bin/makeTagDirectory'],home,work,'link',**limits)
    if any(core.sha(Path(name).resolve())!=value['sha256'] for name,value in tools.items()):raise ValueError('Build tools changed during compilation')
    return dict(status='built-original5.1-tag-builder-installation-prefix-only',recipe=recipe,original_sources={n:p['sha256'] for n,p in LOCK['build_sources'].items()},configured_sources=configured,tool_executables=tools,compiler_version=compiler_version,make_version=make_version,compile=compilation,link=link,binary_sha256=core.sha(home/'bin/makeTagDirectory'),note='Source/configuration and compiler executable identities retained; toolchain/sysroot is not a byte-reproducibility certificate.')


def external_files(home,report,args):
    work=report/'external';work.mkdir();paths={Path(p) for p in UTILITIES}
    for i,path in enumerate([*[home/'bin'/name for name in ['makeTagDirectory','findPeaks','makeUCSCfile','bedGraphToBigWig']],Path('/bin/sh'),Path('/usr/bin/env'),Path('/usr/bin/mkdir'),Path('/usr/bin/mv')]):
        execute(['/usr/bin/ldd',path],home,work,'ldd-'+str(i),seconds=30,rss=args.maximum_upstream_memory_bytes,workspace=args.workspace_bytes);text=(work/('ldd-'+str(i)+'.stdout')).read_text()
        if 'not found' in text:raise core.Unavailable('Required original ATAC/converter shared library is absent')
        if path.name=='makeTagDirectory' and ('libstdc++' in text or 'libgcc_s' in text):raise core.Unavailable('Configured tag builder still needs private dynamic C++/GCC libraries')
        for line in text.splitlines():
            for name in re.findall(r'(?:=>\s+|^\s*)(/[^\s]+)',line):paths.add(Path(name))
    if not 5<=len(paths)<=256:raise ValueError('Unsupported external dependency inventory size')
    result={}
    for path in sorted(paths):
        real=path.resolve(strict=True)
        if not re.fullmatch(r'/(usr|lib|lib64|bin)/[A-Za-z0-9_./+-]+',str(path)) or not real.is_file():raise core.Unavailable('Unsupported host dependency path')
        result[str(path)]=dict(real_path=str(real),bytes=real.stat().st_size,sha256=core.sha(real))
    return result


def bigwig_health(path,bedgraph):
    """Read every original type1 raw block of the fixed single-chromosome health file."""
    if not 64<=path.stat().st_size<=16777216:raise ValueError('Health BigWig size differs')
    raw=path.read_bytes()
    def unpack(fmt,offset):
        size=struct.calcsize(fmt)
        if offset<0 or offset+size>len(raw):raise ValueError('Health BigWig offset escapes file')
        return struct.unpack_from(fmt,raw,offset)
    magic,version,zooms,chrom,data,index,fields,defined,sql,summary,buffer,extension=unpack('<IHHQQQHHQQIQ',0)
    if magic!=0x888ffc26 or version!=4 or fields or defined or sql or not 0<buffer<=1048576:raise ValueError('Health BigWig primary format differs')
    tree_magic,block,key,value,items=unpack('<IIIIQ',chrom)
    leaf,unused,count=unpack('<BBH',chrom+32)
    if tree_magic!=0x78ca8c91 or items!=1 or value!=8 or not 1<=key<=1024 or leaf!=1 or count!=1:raise ValueError('Health BigWig dictionary structure differs')
    name=raw[chrom+36:chrom+36+key];identity,length=unpack('<II',chrom+36+key)
    if name.rstrip(b'\0')!=b'chr1' or identity!=0 or length!=1000000:raise ValueError('Health BigWig dictionary differs')
    if unpack('<I',index)[0]!=0x2468ace0:raise ValueError('Health BigWig raw Rtree differs')
    blocks={};visited=set()
    def node(offset,depth):
        if depth>16 or offset in visited or len(visited)>1024:raise ValueError('Health BigWig raw tree cycle/capacity')
        visited.add(offset);leaf,unused,count=unpack('<BBH',offset)
        if leaf not in (0,1) or count>1024:raise ValueError('Health BigWig raw node differs')
        for i in range(count):
            row=unpack('<IIIIQQ' if leaf else '<IIIIQ',offset+4+i*(32 if leaf else 24));child=row[4]
            if row[0] or row[2] or row[1]>1000000 or row[3]>1000000:raise ValueError('Health BigWig raw range differs')
            if leaf:
                if child<data+8 or row[5]<1 or child+row[5]>index or child in blocks:raise ValueError('Health BigWig raw block differs')
                blocks[child]=row[5]
            else:node(child,depth+1)
    node(index+48,0);observed=[];sections=0
    for offset,size in sorted(blocks.items()):
        inflater=zlib.decompressobj();body=inflater.decompress(raw[offset:offset+size],buffer+1)
        if not inflater.eof or inflater.unused_data or inflater.unconsumed_tail or len(body)>buffer:raise ValueError('Health BigWig raw decompression differs')
        cursor=0
        while cursor<len(body):
            if len(body)-cursor<24:raise ValueError('Truncated health BigWig section')
            identity,lower,upper,step,span,kind,unused,count=struct.unpack_from('<IIIIIBBH',body,cursor);cursor+=24;sections+=1
            if identity!=0 or kind!=1 or count>10000 or cursor+12*count>len(body):raise ValueError('Unsupported health BigWig raw section')
            for i in range(count):
                a,b,v=struct.unpack_from('<IIf',body,cursor);cursor+=12
                if not lower<=a<b<=upper<=1000000 or not math.isfinite(v):raise ValueError('Health BigWig interval differs')
                observed.append(('chr1',a,b,v))
    expected=[]
    for line in bedgraph.read_text().splitlines():
        f=line.split('\t');expected.append((f[0],int(f[1]),int(f[2]),struct.unpack('<f',struct.pack('<f',float(f[3])))[0]))
    if sections!=unpack('<Q',data)[0] or observed!=expected or len(expected)!=LOCK['health']['coverage_rows']:raise ValueError('Health BigWig raw population differs from complete bedGraph')
    return dict(status='passed-every-raw-interval-float32',rows=len(observed),raw_blocks=len(blocks),raw_sections=sections,bigwig_sha256=core.sha(path))


def health(home,report,args):
    work=report/'health';work.mkdir();pin=LOCK['health'];calls=[];limits=dict(seconds=args.timeout,rss=args.maximum_upstream_memory_bytes,workspace=args.workspace_bytes)
    for replicate in [0,1]:
        source=work/('replicate-'+str(replicate)+'.sam');source.write_text(fixture.fixture_sam(replicate));tags=work/('replicate-'+str(replicate));
        if core.sha(source)!=pin['replicate_input_sha256'][replicate]:raise ValueError('Bundled health generator differs')
        calls.append(execute([home/'bin/makeTagDirectory',tags,source,'-format','sam','-unique','-mapq','30','-fragLength','pe'],home,work,'tags-'+str(replicate),**limits))
        if not (tags/'tagInfo.txt').is_file() or core.sha(tags/'chr1.tags.tsv')!=pin['replicate_tag_sha256']:raise ValueError('Original health replicate tag population differs')
    tags=work/'merged';calls.append(execute([home/'bin/makeTagDirectory',tags,'-d',work/'replicate-0',work/'replicate-1','-fragLength','pe'],home,work,'merge',**limits))
    if not (tags/'tagInfo.txt').is_file() or core.sha(tags/'chr1.tags.tsv')!=pin['merged_tag_sha256']:raise ValueError('Original health merged tag population differs')
    genome_row=next(l.split('\t') for l in (tags/'tagInfo.txt').read_text().splitlines() if l.startswith('genome\t'))
    if float(genome_row[2])!=pin['merged_tag_mass']:raise ValueError('Original health merged mass differs')
    calls.append(execute([home/'bin/findPeaks',tags,*pin['peak_arguments']],home,work,'peaks',**limits));rows=fixture.peak_rows((work/'peaks.stdout').read_text())
    if rows!=pin['peak_rows']:raise ValueError('Original health region rows differ')
    calls.append(execute([home/'bin/makeUCSCfile',tags,*pin['coverage_arguments']],home,work,'coverage',**limits))
    if core.sha(work/'coverage.stdout')!=pin['coverage_sha256']:raise ValueError('Original health complete coverage differs')
    (work/'chrom.sizes').write_text('chr1\t1000000\n');calls.append(execute([home/'bin/bedGraphToBigWig',work/'coverage.stdout',work/'chrom.sizes',work/'signal.bw'],home,work,'convert',**limits));bigwig=bigwig_health(work/'signal.bw',work/'coverage.stdout')
    return dict(status=LOCK['health_status'],calls=calls,replicates=2,merged_tag_mass=pin['merged_tag_mass'],merged_tag_sha256=pin['merged_tag_sha256'],peak_count=pin['peak_count'],peak_rows_sha256=hashlib.sha256(rows.encode()).hexdigest(),coverage_rows=pin['coverage_rows'],coverage_sha256=pin['coverage_sha256'],bigwig=bigwig)


def verify_home(home,host):
    home=safe(home);marker=home/MARKER
    if not marker.is_file() or marker.is_symlink() or marker.stat().st_size>1048576:raise core.Unavailable('ATAC admission marker absent,linked or oversized')
    meta=json.loads(marker.read_text());expected=dict(schema_version=1,tool=LOCK['tool'],home=str(home),profile=LOCK['profile'],version=LOCK['version'],host=host,lock_sha256=LOCK_SHA)
    if any(meta.get(k)!=v for k,v in expected.items()):raise core.Unavailable('ATAC version/profile/home/host/lock differs')
    expected_files=set(LOCK['members'])|{'bin/makeTagDirectory','bin/bedGraphToBigWig','share/UCSC_LICENSE','config.txt'};files={}
    for name in expected_files:
        path=core.private(home/name)
        if not path.is_file() or path.stat().st_size>67108864 or name.startswith('bin/') and not os.access(path,os.X_OK):raise core.Unavailable('ATAC member absent,oversized or not executable: '+name)
        files[name]=core.sha(path)
    for name,pin in LOCK['members'].items():
        if files[name]!=pin['sha256'] or (home/name).stat().st_size!=pin['bytes']:raise core.Unavailable('Original ATAC member differs: '+name)
    if files['bin/bedGraphToBigWig']!=LOCK['converter']['sha256'] or files['share/UCSC_LICENSE']!=LOCK['converter']['license_sha256'] or (home/'config.txt').read_text()!=LOCK['generated_config']:raise core.Unavailable('ATAC converter,license or configuration differs')
    build=meta.get('build',{})
    if build.get('status')!='built-original5.1-tag-builder-installation-prefix-only' or build.get('original_sources')!={n:p['sha256'] for n,p in LOCK['build_sources'].items()} or build.get('recipe')!=LOCK['build_recipe'] or build.get('binary_sha256')!=files['bin/makeTagDirectory'] or build.get('compile',{}).get('exit_code')!=0 or build.get('link',{}).get('exit_code')!=0:raise core.Unavailable('Original configured tag-builder build proof differs')
    verify_build(build,home)
    found=set()
    for path in home.rglob('*'):
        if path.is_symlink() or not (path.is_file() or path.is_dir()):raise core.Unavailable('Linked/nonregular ATAC member')
        if path.is_file():found.add(path.relative_to(home).as_posix())
        if len(found)>16:raise core.Unavailable('Extra ATAC files')
    if found!=expected_files|{MARKER} or files!=meta.get('files'):raise core.Unavailable('ATAC home inventory differs')
    external=meta.get('external_files',{})
    if not UTILITIES<=set(external) or not 5<=len(external)<=256:raise core.Unavailable('ATAC host/library closure absent')
    for name,pin in external.items():
        path=Path(name);real=path.resolve(strict=True)
        if not re.fullmatch(r'/(usr|lib|lib64|bin)/[A-Za-z0-9_./+-]+',name) or dict(real_path=str(real),bytes=real.stat().st_size,sha256=core.sha(real))!=pin:raise core.Unavailable('ATAC host/library binding changed: '+name)
    value=meta.get('health',{});pin=LOCK['health']
    if value.get('status')!=LOCK['health_status'] or value.get('replicates')!=2 or value.get('merged_tag_mass')!=pin['merged_tag_mass'] or value.get('merged_tag_sha256')!=pin['merged_tag_sha256'] or value.get('peak_rows_sha256')!=pin['peak_rows_sha256'] or value.get('peak_count')!=3 or value.get('coverage_rows')!=pin['coverage_rows'] or value.get('coverage_sha256')!=pin['coverage_sha256'] or value.get('bigwig',{}).get('status')!='passed-every-raw-interval-float32' or value.get('bigwig',{}).get('rows')!=pin['coverage_rows'] or len(value.get('calls',[]))!=6 or any(c.get('exit_code')!=0 for c in value.get('calls',[])):raise core.Unavailable('Original ATAC scientific health incomplete')
    return meta


def verify_build(build,home):
    tools=build.get('tool_executables',{});compilers=[p for p in tools if Path(p).name.endswith('g++')];makers=[p for p in tools if Path(p).name=='make']
    if len(tools)!=2 or len(compilers)!=1 or len(makers)!=1:raise core.Unavailable('ATAC build tool identities incomplete')
    for name,pin in tools.items():
        if not re.fullmatch(r'/[A-Za-z0-9_./+-]+',name) or not re.fullmatch(r'/[A-Za-z0-9_./+-]+',pin.get('real_path','')) or not re.fullmatch('[0-9a-f]{64}',pin.get('sha256','')):raise core.Unavailable('ATAC historical build-tool identity differs')
    compiler=compilers[0];make=makers[0];recipe=LOCK['build_recipe'];command=build.get('link',{}).get('command',[])
    if len(command)!=1+len(recipe['objects'])+len(recipe['link_flags'])+2:raise core.Unavailable('ATAC link proof incomplete')
    source=Path(command[1]).parent
    if command!=[compiler,*[str(source/name) for name in recipe['objects']],*recipe['link_flags'],'-o',str(home/'bin/makeTagDirectory')] or build.get('compile',{}).get('command')!=[make,'-B','COMPILER='+compiler,'DEBUG='+recipe['compile_flags'],*recipe['objects']]:raise core.Unavailable('ATAC configured build command/home differs')
    for key,path in [('compiler_version',compiler),('make_version',make)]:
        if build.get(key,{}).get('command')!=[path,'--version'] or build.get(key,{}).get('exit_code')!=0:raise core.Unavailable('ATAC historical build version proof incomplete')
    sources=build.get('configured_sources',{});original={Path(name).name:pin['sha256'] for name,pin in LOCK['build_sources'].items()}
    if set(sources)!=set(original) or not re.fullmatch('[0-9a-f]{64}',sources.get('SeqTag.cpp','')) or any(value!=sources[name] for name,value in original.items() if name!='SeqTag.cpp'):raise core.Unavailable('ATAC original/configured source inventory differs')


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ['home','cache','report']:parser.add_argument('--'+key,type=Path,required=True)
    for key in ['archive-dir','converter-path']:parser.add_argument('--'+key,type=Path)
    parser.add_argument('--compiler',type=Path,default=Path('/usr/bin/g++'));parser.add_argument('--make',type=Path,default=Path('/usr/bin/make'))
    for key in ['allow-install','allow-download']:parser.add_argument('--'+key,action='store_true')
    parser.add_argument('--timeout',type=int,default=600);parser.add_argument('--build-timeout',type=int,default=1200);parser.add_argument('--maximum-upstream-memory-bytes',type=int,default=8589934592);parser.add_argument('--workspace-bytes',type=int,default=2147483648)
    args=parser.parse_args(argv);owns=False;code=0;record={}
    try:
        host=core.host();home=safe(args.home);cache=safe(args.cache);report=safe(args.report)
        for a,b in [(home,cache),(home,report),(cache,report)]:
            if a==b or a in b.parents or b in a.parents:raise ValueError('Use separate nonnested home,cache and report')
        if report.exists():raise ValueError('Report exists; choose NEW report')
        report.mkdir(parents=True);owns=True
        if home.exists():verify_home(home,host);record=dict(status='verified-existing-original-ATAC-home',marker_sha256=core.sha(home/MARKER))
        else:
            if not args.allow_install:raise core.Unavailable('Explicit --allow-install is required for a new private ATAC home')
            if not 1<=args.timeout<=86400 or not 1<=args.build_timeout<=86400 or not 1048576<=args.maximum_upstream_memory_bytes<=1099511627776 or not 1048576<=args.workspace_bytes<=1099511627776:raise ValueError('Invalid setup capacities')
            cache.mkdir(parents=True,exist_ok=True);converter=converter_file(args,cache);license=HERE/LOCK['converter']['license_file']
            if not license.is_file() or core.sha(license)!=LOCK['converter']['license_sha256']:raise core.Unavailable('Bundled original UCSC license missing or changed')
            archive=obtain_archive(cache,args.archive_dir,args.allow_download);archive_sha=core.sha(archive)
            converter_sha=core.sha(converter);home.mkdir(parents=True);(home/'bin').mkdir();(home/'share').mkdir()
            with zipfile.ZipFile(archive) as z:
                for name,pin in LOCK['members'].items():
                    path=home/name;path.write_bytes(genome.member_bytes(z,pin));path.chmod(0o755 if name.startswith('bin/') else 0o644)
                (home/'config.txt').write_text(LOCK['generated_config']);build_record=build(z,home,report,args)
            shutil.copyfile(converter,home/'bin/bedGraphToBigWig');(home/'bin/bedGraphToBigWig').chmod(0o755);shutil.copyfile(license,home/'share/UCSC_LICENSE');external=external_files(home,report,args);health_record=health(home,report,args)
            if core.sha(archive)!=archive_sha or core.sha(converter)!=converter_sha:raise ValueError('Original setup inputs changed')
            files={p.relative_to(home).as_posix():core.sha(p) for p in home.rglob('*') if p.is_file()};meta=dict(schema_version=1,tool=LOCK['tool'],home=str(home),profile=LOCK['profile'],version=LOCK['version'],host=host,lock_sha256=LOCK_SHA,files=files,build=build_record,external_files=external,health=health_record)
            core.write(home/MARKER,meta);verify_home(home,host);record=dict(status='installed-and-admitted-original-ATAC-home',marker_sha256=core.sha(home/MARKER))
    except (core.Unavailable,FileNotFoundError,PermissionError) as failure:code=3;record=dict(status='skipped-unavailable-dependency-or-capability',cause=str(failure))
    except Exception as failure:code=2;record=dict(status='failed-retained',cause=repr(failure))
    record.update(exit_code=code,required_action=RECOVERY,scope=LOCK['scope'],transport_warning=LOCK['transport_warning'])
    if owns:
        core.write(report/'installation.json',record);(report/'WARNINGS.md').write_text('# HOMER ATAC '+('SETUP SKIPPED' if code==3 else 'SETUP FAILED' if code else 'REVIEW WARNINGS')+'\n\n'+record.get('cause',LOCK['scope'])+'\n\n'+RECOVERY+'\n\n'+LOCK['transport_warning']+'\n')
    print(json.dumps(record),file=sys.stderr if code else sys.stdout,flush=True);return code


if __name__=='__main__':raise SystemExit(main())
