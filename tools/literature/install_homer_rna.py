#!/usr/bin/env python3
"""Explicit private setup of original HOMER5.1 RefSeq exon raw/RPKM workflows.

The eight reference files are prepared separately. Only installation prefixes
are configured; no scientific body, OS package, annotation edition or input is
silently changed. Analysis never invokes this installer.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import time
import urllib.error
import zipfile
import install_homer as core
import install_homer_genome as original
import install_homer_atac as tag_builder
import install_homer_named as memory
import prepare_homer_rna_resources as resources

RAW=Path(__file__).with_name('homer-rna-lock.json').read_bytes()
LOCK=json.loads(RAW);LOCK_SHA=hashlib.sha256(RAW).hexdigest()
MARKER='.jlinalg-homer-rna.json'
UTILITIES={'/usr/bin/perl','/bin/sh','/usr/bin/env','/usr/bin/rm','/usr/bin/mv','/usr/bin/cat','/usr/bin/mkdir','/usr/bin/ldd'}
RECOVERY=('Required action: use Linux x86_64 Python/Java, inside WSL on Windows. Prepare the exact eight '
    'v7.0 resources with prepare_homer_rna_resources.py. Where permitted run python3 tools/literature/install_homer_rna.py '
    '--home NEW_PRIVATE_RNA_HOME --resource-root PINNED_RNA_RESOURCE_ROOT --cache PRIVATE_SOFTWARE_CACHE '
    '--report NEW_REPORT --allow-install --archive-dir OFFLINE_SOFTWARE_DIRECTORY '
    '--compiler /PATH/TO/g++ --make /PATH/TO/make. Supply the exact original homer.v5.1.zip, or add '
    '--allow-download where downloads are permitted. Setup needs C++11 headers,pthread,math and static C++/GCC '
    'libraries. Allow at least 4 GiB upstream RSS and 6 GiB available memory; default upstream allowance is 8 GiB. '
    'Java heap is separate. Missing, changed, unsupported or forbidden dependencies SKIP the affected analysis, '
    'exit 3, with its named cause. Other failures exit 2. Preserve failed homes,reports and corrupt archives; '
    'correct the cause and choose NEW paths. Read WARNINGS.md and original logs. Analysis never installs or '
    'substitutes software. This profile supplies no alignment,deduplication,DESeq2,R or Cell Ranger capability.')

def safe(path):
    return resources.safe(path)

def config(home,root):
    lines=['# Explicit pinned HOMER RNA; no sequence or ontology operation.','SOFTWARE','ORGANISMS']
    for name in ['human','mouse']:lines.append('\t'.join(LOCK['config_entries'][name]))
    lines+=['PROMOTERS','GENOMES']
    for name in ['hg38','mm10']:
        entry=list(LOCK['config_entries'][name]);entry[4]=os.path.relpath(root/'data/genomes'/name,home)+'/'
        lines.append('\t'.join(entry))
    return '\n'.join(lines+['SETTINGS'])+'\n'

def execute(command,home,work,label,args,seconds=None):
    began=time.monotonic();peak=0
    environment=original.environment(home,work)
    with (work/(label+'.stdout')).open('xb') as stdout,(work/(label+'.stderr')).open('xb') as stderr:
        child=subprocess.Popen(list(map(str,command)),cwd=work,env=environment,stdout=stdout,stderr=stderr,start_new_session=True)
        try:
            while child.poll() is None:
                peak=max(peak,memory.group_rss(child.pid));size=0
                for path in work.rglob('*'):
                    try:
                        if path.is_file():size+=path.stat().st_size
                    except FileNotFoundError:continue
                if peak>args.maximum_upstream_memory_bytes:raise core.Unavailable('Original RNA setup exceeded declared upstream RSS allowance')
                if size>args.workspace_bytes:raise ValueError('Original RNA setup workspace capacity exceeded')
                if time.monotonic()-began>(seconds or args.timeout):raise core.Unavailable('Original RNA setup timed out; inspect '+label+'.stderr')
                time.sleep(.1)
            code=child.wait(timeout=10)
        except BaseException:
            try:os.killpg(child.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            child.wait(timeout=10);raise
    alive=[]
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:
            fields=(path/'stat').read_text().rsplit(')',1)[1].split()
            if int(fields[2])==child.pid:alive.append(path.name)
        except (FileNotFoundError,ProcessLookupError):continue
    if alive:
        try:os.killpg(child.pid,signal.SIGKILL)
        except ProcessLookupError:pass
        raise core.Unavailable('Original RNA setup left owned processes alive; retained '+label+' diagnostics')
    if code:raise core.Unavailable('Original RNA setup exited '+str(code)+'; inspect '+str(work/(label+'.stderr')))
    return dict(command=list(map(str,command)),exit_code=code,seconds=time.monotonic()-began,maximum_observed_group_rss_bytes=peak,owned_group_empty_after_wait=True,stdout_sha256=core.sha(work/(label+'.stdout')),stderr_sha256=core.sha(work/(label+'.stderr')))

def require_builder():
    if (tag_builder.LOCK_SHA!=LOCK['tag_builder_reference_lock_sha256']
            or tag_builder.LOCK['build_sources']!=LOCK['build_sources']
            or tag_builder.LOCK['build_recipe']!=LOCK['build_recipe']):
        raise ValueError('RNA source/helper build lock differs from the verified common tag-builder recipe')

def build(archive,home,report,args):
    require_builder()
    try:return tag_builder.build(archive,home,report,args)
    except core.Unavailable as failure:raise core.Unavailable('RNA tag-helper setup: '+str(failure)) from failure

def obtain_archive(cache,directory,download):
    try:
        archive=core.obtain('homer.v5.1.zip',cache,[] if directory is None else [directory],download)
    except ValueError as failure:
        raise core.Unavailable('Original RNA software dependency rejected: '+str(failure)) from failure
    pin=LOCK['software_archives']['homer.v5.1.zip']
    if archive.stat().st_size!=pin['bytes'] or core.sha(archive)!=pin['sha256']:
        raise core.Unavailable('Original RNA software container differs from independent content pin')
    return archive

def external_files(home,report,args):
    work=report/'external';work.mkdir()
    code='use warnings; use POSIX; use HomerConfig; use Statistics; POSIX::floor(1.2); HomerConfig::loadConfigFile(); print "F\\t$_\\n" for values %INC; print "F\\t$_\\n" for @DynaLoader::dl_shared_objects;'
    execute(['/usr/bin/perl','-I'+str(home/'bin'),'-e',code],home,work,'perl-modules',args,30)
    paths={Path(p) for p in UTILITIES}
    for line in (work/'perl-modules.stdout').read_text().splitlines():
        if not line.startswith('F\t'):raise ValueError('Unexpected RNA Perl closure output')
        path=Path(line[2:])
        if path.parent==home/'bin':continue
        if not path.is_absolute():raise core.Unavailable('Relative external RNA Perl module binding')
        paths.add(path)
    initial=sorted(paths-{Path('/usr/bin/ldd')})+[home/'bin/getPeakTags',home/'bin/makeTagDirectory']
    for i,path in enumerate(initial):
        if path.suffix in {'.pm','.pl','.al'}:continue
        execute(['/usr/bin/ldd',path],home,work,'ldd-'+str(i),args,30)
        text=(work/('ldd-'+str(i)+'.stdout')).read_text()
        if 'not found' in text:raise core.Unavailable('Required original RNA shared library is absent')
        if path.name=='makeTagDirectory' and ('libstdc++' in text or 'libgcc_s' in text):
            raise core.Unavailable('Configured RNA tag helper still requires private dynamic C++/GCC libraries')
        for line in text.splitlines():
            paths.update(Path(n) for n in re.findall(r'(?:=>\s+|^\s*)(/[^\s]+)',line))
    if not 10<=len(paths)<=256:raise ValueError('Unsupported RNA external closure inventory size')
    result={}
    for path in sorted(paths):
        real=path.resolve(strict=True)
        if not re.fullmatch(r'/(usr|lib|lib64|bin)/[A-Za-z0-9_./+-]+',str(path)) or not real.is_file():
            raise core.Unavailable('Unsupported original RNA host dependency path')
        result[str(path)]=dict(real_path=str(real),bytes=real.stat().st_size,sha256=core.sha(real))
    return result

def canonical_matrix(path,target):
    rows=[];identities=set()
    with path.open() as stream:
        header=stream.readline().rstrip('\n').split('\t')
        if len(header)!=10 or not header[0].startswith('Transcript/RepeatID'):raise ValueError('Original RNA health matrix header differs')
        for line in stream:
            fields=line.rstrip('\n').split('\t')
            if len(fields)!=10 or fields[0] in identities:raise ValueError('Original RNA health output shape/identities differ')
            identities.add(fields[0])
            if not all(__import__('math').isfinite(float(v)) and float(v)>=0 for v in fields[8:]):raise ValueError('Nonfinite/negative original RNA health value')
            rows.append('\t'.join([fields[0],fields[5],fields[6],*fields[8:]])+'\n')
    rows.sort();target.write_text(''.join(rows))
    return dict(rows=len(rows),canonical_sha256=core.sha(target),row_ids_sha256=hashlib.sha256(('\n'.join(sorted(identities))+'\n').encode()).hexdigest())

def health(home,root,report,args):
    result={}
    for name,pin in LOCK['health']['genomes'].items():
        work=report/name;work.mkdir();calls=[];tags=[]
        for i,replicate in enumerate(pin['replicates']):
            sam=work/('replicate-'+str(i)+'.sam');sam.write_text(replicate['sam']);tagdir=work/('tags-R'+str(i+1));tags.append(tagdir)
            if core.sha(sam)!=replicate['sam_sha256']:raise ValueError('RNA health fixture bytes differ')
            calls.append(execute([home/'bin/makeTagDirectory',tagdir,sam,*LOCK['health']['tag_arguments']],home,work,'tags-'+str(i),args))
            raw=(tagdir/'chr1.tags.tsv').read_bytes();info=(tagdir/'tagInfo.txt').read_text().splitlines()
            if (hashlib.sha256(raw).hexdigest()!=replicate['chr1_tag_sha256'] or len(raw.splitlines())!=replicate['serialized_rows']
                    or float(info[1].split('\t')[2])!=replicate['weight']):raise ValueError('Complete original RNA tag health population differs')
        calls.append(execute(['/usr/bin/perl',home/'bin/analyzeRepeats.pl','rna',name,'-count','exons'],home,work,'expanded',args))
        expanded=work/'expanded.tsv';digest=hashlib.sha256();row_count=0
        with (work/'expanded.stdout').open('rb') as source,expanded.open('xb') as out:
            if not source.readline().startswith(b'RepeatID (cmd='):raise ValueError('Original RNA expansion header differs')
            while True:
                block=source.read(1048576)
                if not block:break
                out.write(block);digest.update(block);row_count+=block.count(b'\n')
        if row_count!=pin['expanded_rows'] or digest.hexdigest()!=pin['expanded_body_sha256']:
            raise ValueError('Complete original RNA feature expansion health differs')
        for i,tagdir in enumerate(tags):calls.append(execute([home/'bin/getPeakTags',expanded,tagdir,*LOCK['health']['count_arguments']],home,work,'parts-'+str(i),args))
        matrices={}
        for mode,flag in [('raw','-raw'),('rpkm','-rpkm')]:
            calls.append(execute(['/usr/bin/perl',home/'bin/analyzeRepeats.pl','rna',name,'-count','exons','-strand','both',flag,'-d',*tags],home,work,mode,args))
            observed=canonical_matrix(work/(mode+'.stdout'),work/(mode+'-canonical.tsv'))
            if observed!=pin['matrices'][mode]:raise ValueError('Complete original RNA '+mode+' health values/lengths/copies differ')
            stderr=(work/(mode+'.stderr')).read_text()
            for token in ['not found','Could not open',"Can't open",'uninitialized','Illegal division','Segmentation fault','length less than 1']:
                if token in stderr:raise ValueError('Original RNA health warning/error requires review: '+token)
            matrices[mode]=observed
        result[name]=dict(replicates=2,weights=[p['weight'] for p in pin['replicates']],tag_sha256=[p['chr1_tag_sha256'] for p in pin['replicates']],expanded_rows=row_count,expanded_body_sha256=digest.hexdigest(),matrices=matrices,calls=calls)
        core.write(report/'progress.json',result);print(name+' complete original RefSeq RNA health passed',flush=True)
    return dict(status=LOCK['health_status'],genomes=result)

def verify_health(value,home):
    if value.get('status')!=LOCK['health_status'] or set(value.get('genomes',{}))!=set(LOCK['health']['genomes']):
        raise core.Unavailable('RNA scientific health absent/incomplete')
    for name,pin in LOCK['health']['genomes'].items():
        actual=value['genomes'][name]
        if (actual.get('replicates')!=2 or actual.get('weights')!=[p['weight'] for p in pin['replicates']]
                or actual.get('tag_sha256')!=[p['chr1_tag_sha256'] for p in pin['replicates']]
                or actual.get('expanded_rows')!=pin['expanded_rows'] or actual.get('expanded_body_sha256')!=pin['expanded_body_sha256']
                or actual.get('matrices')!=pin['matrices']):raise core.Unavailable('Pinned complete RNA health populations differ')
        calls=actual.get('calls',[])
        if len(calls)!=7 or any(c.get('exit_code')!=0 or c.get('owned_group_empty_after_wait') is not True for c in calls):
            raise core.Unavailable('Seven completed original RNA scientific health calls required per genome')
        for call in calls:
            command=call.get('command',[])
            if not command or not (command[0]==str(home/'bin/makeTagDirectory') or command[0]==str(home/'bin/getPeakTags')
                    or command[:2]==['/usr/bin/perl',str(home/'bin/analyzeRepeats.pl')]):
                raise core.Unavailable('RNA health command/home binding differs')

def verify_home(home,root,host):
    require_builder();home=safe(home);root=safe(root);marker=home/MARKER
    if not marker.is_file() or marker.is_symlink() or marker.stat().st_size>1048576:raise core.Unavailable('RNA admission marker absent,linked or oversized')
    meta=json.loads(marker.read_text());expected=dict(schema_version=1,tool=LOCK['tool'],version=LOCK['version'],profile=LOCK['profile'],home=str(home),resource_root=str(root),host=host,lock_sha256=LOCK_SHA)
    if any(meta.get(k)!=v for k,v in expected.items()):raise core.Unavailable('RNA profile/home/resource/host/lock differs')
    files={name:original.installed(home,name,pin) for name,pin in LOCK['members'].items()}
    for name,pin in LOCK['resources'].items():
        if name.startswith('data/accession/'):
            path=core.private(home/name)
            if not path.is_file() or path.stat().st_size!=pin['bytes'] or core.sha(path)!=pin['sha256']:raise core.Unavailable('RNA accession copy differs: '+name)
            files[name]=pin['sha256']
    if (home/'config.txt').read_bytes()!=config(home,root).encode():raise core.Unavailable('RNA genome/organism configuration differs')
    files['config.txt']=core.sha(home/'config.txt');binary=core.private(home/'bin/makeTagDirectory')
    if not binary.is_file() or not os.access(binary,os.X_OK) or not 1<=binary.stat().st_size<=67108864:raise core.Unavailable('RNA tag helper missing or unsupported')
    files['bin/makeTagDirectory']=core.sha(binary);build=meta.get('build',{})
    if (build.get('status')!='built-original5.1-tag-builder-installation-prefix-only'
            or build.get('recipe')!=LOCK['build_recipe'] or build.get('original_sources')!={n:p['sha256'] for n,p in LOCK['build_sources'].items()}
            or build.get('binary_sha256')!=files['bin/makeTagDirectory'] or build.get('compile',{}).get('exit_code')!=0 or build.get('link',{}).get('exit_code')!=0):
        raise core.Unavailable('Original RNA configured-source build proof differs')
    try:tag_builder.verify_build(build,home)
    except core.Unavailable as failure:raise core.Unavailable('RNA tag-helper build proof: '+str(failure)) from failure
    found=set();entries=0
    for path in home.rglob('*'):
        entries+=1
        if entries>128 or path.is_symlink() or not (path.is_file() or path.is_dir()):raise core.Unavailable('Extra/linked/nonregular RNA home entry')
        if path.is_file():found.add(path.relative_to(home).as_posix())
    if found!=set(files)|{MARKER} or meta.get('files')!=files:raise core.Unavailable('RNA home inventory differs')
    if meta.get('resources')!=resources.resource_files(root):raise core.Unavailable('RNA external resource bindings differ')
    closure=meta.get('external_files',{})
    if not UTILITIES<=set(closure) or not 10<=len(closure)<=256:raise core.Unavailable('RNA Perl/POSIX/host/library closure incomplete')
    for name,pin in closure.items():
        path=Path(name);real=path.resolve(strict=True)
        if (not re.fullmatch(r'/(usr|lib|lib64|bin)/[A-Za-z0-9_./+-]+',name) or not real.is_file()
                or dict(real_path=str(real),bytes=real.stat().st_size,sha256=core.sha(real))!=pin):raise core.Unavailable('RNA external host/library binding changed: '+name)
    verify_health(meta.get('health',{}),home)
    return meta

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['home','resource-root','cache','report']:parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--archive-dir',type=Path)
    parser.add_argument('--compiler',type=Path,default=Path('/usr/bin/g++'));parser.add_argument('--make',type=Path,default=Path('/usr/bin/make'))
    parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true')
    parser.add_argument('--timeout',type=int,default=600);parser.add_argument('--build-timeout',type=int,default=1200)
    parser.add_argument('--maximum-upstream-memory-bytes',type=int,default=8589934592);parser.add_argument('--workspace-bytes',type=int,default=2147483648)
    args=parser.parse_args(argv);owns=False;code=0;record={}
    try:
        host=core.host();home=safe(args.home);root=safe(args.resource_root);cache=safe(args.cache);report=safe(args.report)
        paths=[home,root,cache,report]
        if args.archive_dir:paths.append(safe(args.archive_dir))
        for i,a in enumerate(paths):
            for b in paths[i+1:]:
                if a==b or a in b.parents or b in a.parents:raise ValueError('Use separate nonnested RNA home/resource/cache/report/offline paths')
        if report.exists():raise ValueError('Report exists; choose NEW report')
        report.mkdir(parents=True);owns=True
        if home.exists():verify_home(home,root,host);record=dict(status='verified-existing-original-RNA-home',marker_sha256=core.sha(home/MARKER))
        else:
            if not args.allow_install:raise core.Unavailable('Explicit --allow-install required for a new private RNA home')
            require_builder()
            if (not 1<=args.timeout<=86400 or not 1<=args.build_timeout<=86400 or not 1048576<=args.workspace_bytes<=1099511627776
                    or not LOCK['minimum_upstream_memory_bytes']<=args.maximum_upstream_memory_bytes<=1099511627776):
                raise core.Unavailable('RNA health needs valid capacities and at least4GiB upstream RSS allowance')
            if memory.available_memory()<LOCK['minimum_available_memory_bytes']:raise core.Unavailable('RNA setup needs at least6GiB available memory before compilation/science')
            pins=resources.resource_files(root);cache.mkdir(parents=True,exist_ok=True)
            archive=obtain_archive(cache,args.archive_dir,args.allow_download)
            archive_hash=core.sha(archive);home.mkdir(parents=True)
            with zipfile.ZipFile(archive) as source:
                for name,pin in LOCK['members'].items():
                    raw=original.member_bytes(source,pin)
                    if pin['relocate']:
                        prefix=base64.b64decode(pin['original_prefix_base64'])
                        if not raw.startswith(prefix):raise ValueError('Original RNA Perl installation prefix differs')
                        raw=original.prefix(home)+raw[len(prefix):]
                    path=home/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw);path.chmod(0o755 if name.startswith('bin/') else 0o644)
                for name in pins:
                    if name.startswith('data/accession/'):
                        path=home/name;path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(root/name,path)
                (home/'config.txt').write_text(config(home,root));build_record=build(source,home,report,args)
            external=external_files(home,report,args);health_record=health(home,root,report,args)
            if archive_hash!=core.sha(archive) or pins!=resources.resource_files(root):raise ValueError('Original RNA setup input changed')
            for name,pin in LOCK['members'].items():original.installed(home,name,pin)
            files={p.relative_to(home).as_posix():core.sha(p) for p in home.rglob('*') if p.is_file()}
            meta=dict(schema_version=1,tool=LOCK['tool'],version=LOCK['version'],profile=LOCK['profile'],home=str(home),resource_root=str(root),host=host,lock_sha256=LOCK_SHA,files=files,resources=pins,build=build_record,external_files=external,health=health_record)
            core.write(home/MARKER,meta);verify_home(home,root,host);record=dict(status='installed-and-admitted-original-RNA-home',marker_sha256=core.sha(home/MARKER))
    except (core.Unavailable,FileNotFoundError,PermissionError,urllib.error.URLError,TimeoutError,ConnectionError) as failure:
        code=3;record=dict(status='skipped-unavailable-dependency-or-capability',cause=str(failure))
    except Exception as failure:
        code=2;record=dict(status='failed-retained',cause=repr(failure))
    record.update(exit_code=code,required_action=RECOVERY,scope=LOCK['scope'],transport_warning=LOCK['transport_warning'])
    if owns:
        core.write(report/'installation.json',record)
        (report/'WARNINGS.md').write_text('# HOMER RNA '+('SETUP SKIPPED' if code==3 else 'SETUP FAILED' if code else 'REVIEW WARNINGS')+'\n\n'+record.get('cause',LOCK['scope'])+'\n\n'+RECOVERY+'\n\n'+LOCK['transport_warning']+'\n')
    print(json.dumps(record),file=sys.stderr if code else sys.stdout,flush=True)
    return code

if __name__=='__main__':raise SystemExit(main())
