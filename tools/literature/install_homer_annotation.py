#!/usr/bin/env python3
"""Explicit private admission of original HOMER supplied-GTF annotation (no named genome download)."""
import argparse
import base64
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import stat
import subprocess
import sys
import time
import zipfile
import install_homer as core

HERE=Path(__file__).resolve().parent
RAW=(HERE/'homer-annotation-lock.json').read_bytes()
LOCK=json.loads(RAW)
LOCK_SHA=hashlib.sha256(RAW).hexdigest()
MARKER='.jlinalg-homer-annotation.json'
HEALTH='passed-original-supplied-GTF-annotation-health'
RECOVERY=('Required action: use Linux x86_64 Python/Java, inside WSL on Windows. Where permitted run '
    'python3 tools/literature/install_homer_annotation.py --profile linux-x86_64-annotation-5.1 '
    '--home NEW_PRIVATE_HOME --cache ARCHIVE_CACHE --report NEW_REPORT --allow-install. '
    'Select annotation-4.11 or annotation-4.11.1 explicitly when needed. Add --allow-download only '
    'where permitted, or provide --archive-dir VERIFIED_OFFLINE_ARCHIVES. Missing, forbidden, '
    'unsupported or changed dependencies SKIP the affected analysis, exit3. Preserve changed homes '
    'and failed reports; correct the cause and use new paths. Analysis never installs or substitutes '
    'versions. The separate core homer2 profile does not admit annotation. Supplied-GTF annotation '
    'does not admit named-genome,sequence,motif,tag/coverage,RNA or GO workflows. Cell Ranger excluded.')

def prefix(home):
    return ('#!/usr/bin/env perl\nuse warnings;\nuse lib "'+str(home)+'/bin";\nmy $homeDir = "'+str(home)+'/";\n').encode()

def installed(home,name,pin):
    path=home/name
    if path.is_symlink() or not path.is_file():raise core.Unavailable('Missing/linked annotation runtime member: '+name)
    raw=path.read_bytes()
    if pin['relocate']:
        expected=prefix(home)
        if not raw.startswith(expected):raise core.Unavailable('Annotation script installation path changed: '+name)
        raw=base64.b64decode(pin['original_prefix_base64'])+raw[len(expected):]
    if len(raw)!=pin['bytes'] or hashlib.sha256(raw).hexdigest()!=pin['sha256']:raise core.Unavailable('Original annotation runtime body differs: '+name)
    if name.startswith('bin/') and not os.access(path,os.X_OK):raise core.Unavailable('Annotation runtime member is not executable: '+name)
    return core.sha(path)

def environment(home,work):
    value=core.env(work)
    value.update(PATH=str(home/'bin')+':/usr/bin:/bin',PERL_HASH_SEED='0',PERL_PERTURB_KEYS='0')
    return value

def execute(args,home,work,label,seconds=90):
    out,err=work/(label+'.stdout'),work/(label+'.stderr')
    with out.open('xb') as stdout,err.open('xb') as stderr:
        child=subprocess.Popen(args,cwd=work,env=environment(home,work),stdout=stdout,stderr=stderr,start_new_session=True)
        try:
            deadline=time.monotonic()+seconds
            while child.poll() is None:
                total=0
                for path in work.rglob('*'):
                    # Original Perl helpers create and remove temporary files between directory listing and stat.
                    try:
                        if path.is_file():total+=path.stat().st_size
                    except FileNotFoundError:continue
                if total>33554432:raise ValueError('Original annotation health workspace bound exceeded')
                if time.monotonic()>deadline:raise core.Unavailable('Actual annotation health timed out;inspect retained diagnostics')
                time.sleep(.05)
            code=child.returncode
        except BaseException:
            try:os.killpg(child.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            child.wait(timeout=10);raise
    if code:raise core.Unavailable('Original annotation process failed;inspect '+str(err))
    return dict(command=args,exit_code=code,stdout_sha256=core.sha(out),stderr_sha256=core.sha(err))

def external_files(home,report):
    code='use warnings; use POSIX; use HomerConfig; use Statistics; POSIX::floor(1.2); HomerConfig::loadConfigFile(); print "F\\t$_\\n" for values %INC; print "F\\t$_\\n" for @DynaLoader::dl_shared_objects;'
    call=execute(['/usr/bin/perl','-I'+str(home/'bin'),'-e',code],home,report,'perl-modules')
    if call['exit_code']:raise core.Unavailable('Perl/POSIX/HOMER module closure could not load')
    paths={Path(p) for p in ['/usr/bin/perl','/bin/sh','/usr/bin/env','/usr/bin/rm','/usr/bin/mv','/usr/bin/cat','/usr/bin/ldd']}
    for line in (report/'perl-modules.stdout').read_text().splitlines():
        if not line.startswith('F\t'):raise ValueError('Unexpected Perl closure output')
        path=Path(line[2:])
        if path.parent==home/'bin':continue
        if not path.is_absolute():raise core.Unavailable('Relative external Perl module binding')
        paths.add(path)
    initial=sorted(paths-{Path('/usr/bin/ldd')})+[home/'bin/assignGenomeAnnotation']
    for i,path in enumerate(initial):
        if path.suffix in {'.pm','.pl','.al'}:continue
        call=execute(['/usr/bin/ldd',str(path)],home,report,'ldd-'+str(i))
        if call['exit_code']:raise core.Unavailable('Required annotation ELF closure is unavailable: '+str(path))
        text=(report/('ldd-'+str(i)+'.stdout')).read_text()
        if 'not found' in text:raise core.Unavailable('Annotation shared library is missing')
        for line in text.splitlines():
            matches=re.findall(r'(?:=>\s+|^\s*)(/[^\s]+)',line)
            paths.update(Path(m) for m in matches)
    if len(paths)>256:raise ValueError('Annotation external closure exceeds its member bound')
    result={}
    for path in sorted(paths):
        real=path.resolve(strict=True)
        if not str(path).startswith(('/usr/','/lib/','/lib64/','/bin/')) or not real.is_file():raise core.Unavailable('Unsupported external annotation binding: '+str(path))
        result[str(path)]=dict(real_path=str(real),bytes=real.stat().st_size,sha256=core.sha(real))
    return result

def health(home,report):
    work=report/'health';work.mkdir()
    peaks=[('promoter',9300,9400,'+','NM_A.1',-651),('exon',10010,10080,'+','NM_A.1',44),('intron',10120,10250,'+','NM_A.1',184),('tts',10410,10460,'+','NM_A.1',434),('minus-exon',15310,15380,'-','NM_B.2',55),('minus-promoter',16000,16100,'-','NM_B.2',-650),('distant',24000,24100,'+','NM_B.2',-8650),('unknown',3000,3100,'+','NA','NA'),('boundary',9999,10001,'+','NM_A.1',-1)]
    (work/'peaks.bed').write_text(''.join(f'{"chrX" if p[0]=="unknown" else "chr1"}\t{p[1]}\t{p[2]}\t{p[0]}\t7.5\t{p[3]}\n' for p in peaks))
    (work/'genes.gtf').write_text(''.join(f'chr1\tfixture\texon\t{s}\t{e}\t.\t{d}\t.\tgene_id "{g}"; transcript_id "{t}"; gene_name "{g}symbol"; gene_type "protein_coding";\n' for g,t,d,exons in [('GA','NM_A.1','+',[(10001,10100),(10301,10400)]),('GB','NM_B.2','-',[(15001,15100),(15301,15400)])] for s,e in exons))
    args=['/usr/bin/perl',str(home/'bin/annotatePeaks.pl'),'peaks.bed','none','-gtf','genes.gtf','-size','given','-annStats','annotation-stats.tsv']
    call=execute(args,home,work,'annotation')
    if call['exit_code']:raise core.Unavailable('Actual original supplied-GTF annotation health failed')
    rows=(work/'annotation.stdout').read_text().splitlines()
    if len(rows)!=10 or len(rows[0].split('\t'))!=19:raise ValueError('Original annotation health population/header differs')
    result={}
    for line in rows[1:]:
        fields=line.split('\t')
        if len(fields)!=19 or fields[0] in result:raise ValueError('Original annotation health identity/columns differ')
        result[fields[0]]=fields
    for p in peaks:
        f=result[p[0]]
        if (f[2],f[3],f[4],f[5],f[9],f[10])!=(str(p[1]+1),str(p[2]),p[3],'7.5',str(p[5]),p[4]):raise ValueError('Actual original coordinate/nearest-TSS audit failed: '+p[0])
    if not (work/'annotation-stats.tsv').is_file() or 'Done annotating peaks file' not in (work/'annotation.stderr').read_text():raise ValueError('Original annotation health did not finalize')
    return dict(status=HEALTH,rows=9,known_tss_rows=8,unknown_contig_rows=1,gtf_exons=4,call=call,output_sha256=core.sha(work/'annotation.stdout'))

def verify_home(home,profile,actual_host):
    if not re.fullmatch(r'/[A-Za-z0-9_./-]+',str(home)):raise core.Unavailable('Private annotation home needs a shell/Perl-safe ASCII path')
    path=home/MARKER
    if path.is_symlink() or not path.is_file() or path.stat().st_size>1048576:raise core.Unavailable('Annotation runtime marker absent/linked/oversized')
    meta=json.loads(path.read_text());pin=LOCK['profiles'][profile]
    if meta.get('schema_version')!=1 or meta.get('tool')!='HOMER annotation' or meta.get('home')!=str(home) or meta.get('profile')!=profile or meta.get('version')!=pin['version'] or meta.get('lock_sha256')!=LOCK_SHA or meta.get('host')!=actual_host:raise core.Unavailable('Annotation profile/version/host/path/lock changed')
    expected={n:installed(home,n,p) for n,p in pin['members'].items()}
    if (home/'config.txt').read_bytes()!=LOCK['generated_config'].encode():raise core.Unavailable('Annotation config changed')
    expected['config.txt']=core.sha(home/'config.txt')
    found=set()
    for path in home.rglob('*'):
        if path.is_symlink() or not (path.is_dir() or path.is_file()):raise core.Unavailable('Linked/nonregular annotation runtime member')
        if path.is_file():found.add(path.relative_to(home).as_posix())
    if found!=set(expected)|{MARKER} or meta.get('files')!=expected:raise core.Unavailable('Annotation runtime inventory changed')
    checked=meta.get('health',{})
    if any(checked.get(k)!=v for k,v in dict(status=HEALTH,rows=9,known_tss_rows=8,unknown_contig_rows=1,gtf_exons=4).items()) or checked.get('call',{}).get('exit_code')!=0:raise core.Unavailable('Actual annotation scientific health is incomplete')
    external=meta.get('external_files',{})
    if not 10<=len(external)<=256 or not {'/usr/bin/perl','/bin/sh','/usr/bin/env','/usr/bin/rm','/usr/bin/mv','/usr/bin/cat','/usr/bin/ldd'}<=set(external):raise core.Unavailable('Annotation host/Perl/library closure is incomplete')
    for name,value in external.items():
        path=Path(name);real=path.resolve(strict=True)
        if str(real)!=value.get('real_path') or not real.is_file() or real.stat().st_size!=value.get('bytes') or core.sha(real)!=value.get('sha256'):raise core.Unavailable('Annotation host/Perl/library file changed: '+name)
    return meta

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--profile',choices=sorted(LOCK['profiles']),required=True)
    for key in ['home','cache','report']:parser.add_argument('--'+key,type=Path,required=True)
    parser.add_argument('--archive-dir',type=Path,action='append',default=[])
    parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');args=parser.parse_args(argv)
    record=dict(status='starting',profile=args.profile,lock_sha256=LOCK_SHA,permissions=dict(install=args.allow_install,download=args.allow_download),started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),required_action=RECOVERY);report=None;owns=False;code=0
    try:
        home,cache,report=[core.private(p) for p in [args.home,args.cache,args.report]];references=[core.private(p) for p in args.archive_dir]
        if any(a==b or a in b.parents or b in a.parents for a,b in [(home,cache),(home,report),(cache,report)]):raise ValueError('Home,cache andreport need distinct nonnested paths')
        if report.exists():raise ValueError('Report exists;choose a new report')
        report.mkdir(parents=True);owns=True;actual_host=core.host()
        if not re.fullmatch(r'/[A-Za-z0-9_./-]+',str(home)):raise core.Unavailable('Private annotation home needs a shell/Perl-safe ASCII path')
        record.update(host=actual_host,home=str(home))
        if home.exists():meta=verify_home(home,args.profile,actual_host);record['status']='reused-verified-admitted-annotation-runtime'
        else:
            if not args.allow_install:raise core.Unavailable('Installation not permitted: --allow-install absent; no runtime home created')
            cache.mkdir(parents=True,exist_ok=True);pin=LOCK['profiles'][args.profile];archive=core.obtain(pin['archive'],cache,references,args.allow_download);home.mkdir(parents=True)
            with zipfile.ZipFile(archive) as source:
                for name,p in pin['members'].items():
                    found=[i for i in source.infolist() if i.filename==p['member']]
                    if len(found)!=1 or stat.S_ISLNK(found[0].external_attr>>16) or found[0].file_size!=p['bytes']:raise ValueError('Invalid original annotation archive member')
                    raw=source.read(found[0])
                    if hashlib.sha256(raw).hexdigest()!=p['sha256']:raise ValueError('Original annotation member checksum differs')
                    if p['relocate']:
                        prior=base64.b64decode(p['original_prefix_base64'])
                        if not raw.startswith(prior):raise ValueError('Original installation prefix differs')
                        raw=prefix(home)+raw[len(prior):]
                    dest=home/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(raw);dest.chmod(0o755 if name.startswith('bin/') else 0o644)
            (home/'config.txt').write_text(LOCK['generated_config']);closure=external_files(home,report);science=health(home,report)
            meta=dict(schema_version=1,tool='HOMER annotation',home=str(home),profile=args.profile,version=pin['version'],lock_sha256=LOCK_SHA,host=actual_host,files={n:core.sha(home/n) for n in [*pin['members'],'config.txt']},external_files=closure,health=science,installation_relocation='Only original configureHomer-style lines3/4 in annotatePeaks.pl andHomerConfig.pm;all scientific bodies and binary bytes unchanged',source_archive_sha256=core.sha(archive))
            core.write(home/MARKER,meta);verify_home(home,args.profile,actual_host);record['status']='installed-and-admitted-original-annotation-runtime'
        record.update(health=meta['health'],runtime_marker_sha256=core.sha(home/MARKER))
    except (core.Unavailable,PermissionError,FileNotFoundError) as failure:code=3;record.update(status='skipped-dependency-unavailable',cause=str(failure))
    except Exception as failure:code=2;record.update(status='failed-retained',cause=str(failure))
    record['exit_code']=code
    if owns:
        core.write(report/'installation.json',record)
        (report/'WARNINGS.md').write_text('# HOMER ANNOTATION '+('ANALYSIS SKIPPED' if code==3 else 'SETUP FAILED' if code else 'REVIEW WARNINGS')+'\n\n'+record.get('cause','Original annotation health passed;review scientific scope andhost dependency binding.')+'\n\n'+RECOVERY+'\n\n'+LOCK['transport_warning']+'\n')
    print(json.dumps(record,ensure_ascii=False),file=sys.stderr if code else sys.stdout);return code

if __name__=='__main__':raise SystemExit(main())
