#!/usr/bin/env python3
"""Create a private original HOMER5.1 named-annotation home from inspected resources.

No download, package installation, scientific-source patch or canonical-home mutation.
An existing admitted supplied-GTF home supplies the original annotation closure.
The twelve required resource files must match homer-named-lock.json exactly.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import sys
import time
import install_homer_annotation as annotation

core=annotation.core
RAW=Path(__file__).with_name('homer-named-lock.json').read_bytes()
LOCK=json.loads(RAW);LOCK_SHA=__import__('hashlib').sha256(RAW).hexdigest()
MARKER='.jlinalg-homer-named.json'
RECOVERY=('Required action: use Linux x86_64 Python/Java, inside WSL on Windows. '
    'Prepare the exact v7.0 human/mouse/hg38/mm10 resources listed in homer-named-lock.json; '
    'then run python3 tools/literature/install_homer_named.py --annotation-home ADMITTED_5_1_ANNOTATION_HOME '
    '--home NEW_PRIVATE_NAMED_HOME --resource-root PINNED_RESOURCE_ROOT --report NEW_REPORT --allow-install. '
    'The root must contain the twelve original data/genomes and data/accession files at their locked paths. '
    'Only the two configured Perl installation prefixes change. No scientific body is patched. '
    'Allow at least6GiB owned upstream RSS and sufficient available RAM; the full original annotation reads '
    'millions of resource rows. If installation/execution/monitoring is forbidden, resources differ, or memory '
    'is unavailable, SKIP the affected analysis, exit3. Preserve old homes and failed reports; correct the '
    'named cause and use NEW paths. This setup never downloads; analysis never installs. '
    'Read WARNINGS.md and original stderr. Cell Ranger remains excluded.')


def safe(path):
    path=core.private(path)
    if not re.fullmatch(r'/[A-Za-z0-9_./-]+',str(path)):
        raise core.Unavailable('Named home/resource root needs a shell/Perl-safe ASCII path')
    return path


def config(home,resources):
    lines=['# Explicit pinned HOMER named annotation; no ontology or sequence operation.','SOFTWARE','ORGANISMS']
    for name in ['human','mouse']:lines.append('\t'.join(LOCK['config_entries'][name]))
    lines+=['PROMOTERS','GENOMES']
    for name in ['hg38','mm10']:
        row=list(LOCK['config_entries'][name]);row[4]=os.path.relpath(resources/'data/genomes'/name,home)+'/'
        lines.append('\t'.join(row))
    return '\n'.join(lines+['SETTINGS'])+'\n'


def resource_files(root):
    result={}
    for name,pin in LOCK['resources'].items():
        path=core.private(root/name)
        if not path.is_file() or path.stat().st_size!=pin['bytes'] or core.sha(path)!=pin['sha256']:
            raise core.Unavailable('Original named resource missing or changed: '+name)
        result[name]=pin['sha256']
    return result


def available_memory():
    values={f[0].rstrip(':'):int(f[1])*1024 for f in [s.split() for s in Path('/proc/meminfo').read_text().splitlines()] if len(f)>=3 and f[2]=='kB'}
    if 'MemAvailable' not in values:raise core.Unavailable('Available-memory monitoring unavailable')
    return values['MemAvailable']


def resident(path):
    last_state=None
    for attempt in range(20):
        try:
            lines=(path/'status').read_text().splitlines()
        except (FileNotFoundError,ProcessLookupError):return 0
        state=next((s.split()[1] for s in lines if s.startswith('State:')),None)
        last_state=state
        if state in ['Z','X']:return 0
        value=next((s.split() for s in lines if s.startswith('VmRSS:')),None)
        if value is not None:
            if len(value)!=3 or value[2]!='kB':raise core.Unavailable('Invalid process memory units')
            return int(value[1])*1024
        try:
            if exiting_without_address_space((path/'stat').read_text()):return 0
        except (FileNotFoundError,ProcessLookupError):return 0
        time.sleep(.005)
    # A process can disappear just after the final successful status read.
    if not (path/'stat').exists():return 0
    raise core.Unavailable('Cannot monitor nonterminal named process memory: '+path.name+';last state='+str(last_state))


def exiting_without_address_space(text):
    # Linux6.18 exit_mm clears task->mm before finishing teardown. /proc/status
    # then omits VmRSS even with State R. Require PF_EXITING and both zero stat
    # vsize/RSS; no generic missing-memory or running-task exception is allowed.
    try:
        fields=text.rsplit(')',1)[1].split()
        if len(fields)<22:raise ValueError('truncated stat')
        flags=int(fields[6]);virtual=int(fields[20]);rss=int(fields[21])
        if min(flags,virtual,rss)<0:raise ValueError('negative stat field')
        return bool(flags&4) and virtual==0 and rss==0
    except (IndexError,ValueError) as failure:raise core.Unavailable('Malformed process memory stat') from failure


def group_rss(pgid):
    total=0
    for path in Path('/proc').iterdir():
        if not path.name.isdigit():continue
        try:
            fields=(path/'stat').read_text().rsplit(')',1)[1].split()
            if int(fields[2])!=pgid:continue
            total+=resident(path)
        except (FileNotFoundError,ProcessLookupError):continue
        except PermissionError as failure:raise core.Unavailable('Environment forbids required process monitoring') from failure
    return total


def execute(home,work,genome,seconds,rss,workspace):
    if rss<LOCK['minimum_upstream_memory_bytes'] or available_memory()<LOCK['minimum_upstream_memory_bytes']:
        raise core.Unavailable('Original named annotation requires at least6GiB allowed and available upstream memory')
    command=['/usr/bin/perl',str(home/'bin/annotatePeaks.pl'),'peaks.pos',genome,'-size','given','-annStats','annotation-stats.tsv']
    began=time.monotonic();maximum=0
    with (work/'original.stdout').open('xb') as stdout,(work/'original.stderr').open('xb') as stderr:
        child=subprocess.Popen(command,cwd=work,env=annotation.environment(home,work),stdout=stdout,stderr=stderr,start_new_session=True)
        core.write(work/'running.json',dict(pid=child.pid,command=command,rss_bound=rss,timeout=seconds,workspace_bound=workspace))
        try:
            while child.poll() is None:
                maximum=max(maximum,group_rss(child.pid));size=0
                for path in work.rglob('*'):
                    try:
                        if path.is_file():size+=path.stat().st_size
                    except FileNotFoundError:continue
                if maximum>rss:raise core.Unavailable('Original named process exceeded declared RSS capacity')
                if size>workspace:raise ValueError('Named health workspace capacity exceeded')
                if time.monotonic()-began>seconds:raise core.Unavailable('Original named health timed out')
                time.sleep(.1)
            code=child.returncode
        except BaseException:
            try:os.killpg(child.pid,signal.SIGKILL)
            except ProcessLookupError:pass
            child.wait(timeout=10);raise
    if code:raise core.Unavailable('Original named annotation exited '+str(code)+';inspect retained original.stderr')
    return dict(command=command,exit_code=code,seconds=time.monotonic()-began,maximum_observed_group_rss_bytes=maximum,stdout_sha256=core.sha(work/'original.stdout'),stderr_sha256=core.sha(work/'original.stderr'))


def verify_home(home,resources,host):
    home=safe(home);resources=safe(resources);marker=home/MARKER
    if not marker.is_file() or marker.is_symlink() or marker.stat().st_size>1048576:raise core.Unavailable('Named runtime marker absent/linked/oversized')
    meta=json.loads(marker.read_text());expected=dict(schema_version=1,tool=LOCK['tool'],home=str(home),resource_root=str(resources),profile=LOCK['profile'],version=LOCK['version'],lock_sha256=LOCK_SHA,host=host)
    if any(meta.get(k)!=v for k,v in expected.items()):raise core.Unavailable('Named runtime path/profile/host/resource/lock changed')
    files={name:annotation.installed(home,name,pin) for name,pin in LOCK['members'].items()}
    if (home/'config.txt').read_bytes()!=config(home,resources).encode():raise core.Unavailable('Named config differs')
    files['config.txt']=core.sha(home/'config.txt')
    for name,pin in LOCK['resources'].items():
        if name.startswith('data/accession/'):
            if core.private(home/name).stat().st_size!=pin['bytes'] or core.sha(home/name)!=pin['sha256']:raise core.Unavailable('Named organism accession copy changed: '+name)
            files[name]=pin['sha256']
    found=set()
    for path in home.rglob('*'):
        if path.is_symlink() or not (path.is_file() or path.is_dir()):raise core.Unavailable('Linked/nonregular named home member')
        if path.is_file():found.add(path.relative_to(home).as_posix())
    if found!=set(files)|{MARKER} or meta.get('files')!=files:raise core.Unavailable('Named home inventory changed')
    if resource_files(resources)!=meta.get('resources'):raise core.Unavailable('Named resource inventory changed')
    closure=meta.get('external_files',{})
    if not 10<=len(closure)<=256 or not {'/usr/bin/perl','/bin/sh','/usr/bin/env','/usr/bin/rm','/usr/bin/mv','/usr/bin/cat','/usr/bin/ldd'}<=set(closure):raise core.Unavailable('Named Perl/host/library closure incomplete')
    for name,value in closure.items():
        path=Path(name);real=path.resolve(strict=True)
        if not re.fullmatch(r'/(usr|lib|lib64|bin)/[A-Za-z0-9_./+-]+',name) or dict(real_path=str(real),bytes=real.stat().st_size,sha256=core.sha(real))!=value:raise core.Unavailable('Named external binding changed: '+name)
    health=meta.get('health',{})
    if health.get('status')!=LOCK['health_status'] or set(health.get('genomes',{}))!={'mm10','hg38'}:raise core.Unavailable('Named health missing')
    for genome,value in health['genomes'].items():
        if value.get('rows')!=7 or value.get('columns')!=19 or value.get('call',{}).get('exit_code')!=0 or value.get('peaks_sha256')!=__import__('hashlib').sha256(LOCK['health'][genome]['peaks'].encode()).hexdigest():raise core.Unavailable('Named scientific health incomplete')
    return meta


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    for key in ['annotation-home','home','resource-root','report']:parser.add_argument('--'+key,type=Path,required=True)
    parser.add_argument('--allow-install',action='store_true');parser.add_argument('--timeout',type=int,default=600)
    parser.add_argument('--maximum-upstream-memory-bytes',type=int,default=8589934592)
    args=parser.parse_args(argv);owns=False;record={};code=0
    try:
        host=core.host();home=safe(args.home);resources=safe(args.resource_root);report=core.private(args.report);base=safe(args.annotation_home)
        for a,b in [(home,resources),(home,report),(home,base),(report,resources),(report,base)]:
            if a==b or a in b.parents or b in a.parents:raise ValueError('Use separate nonnested runtime,resource,report and base paths')
        if report.exists():raise ValueError('Report exists;choose NEW report')
        report.mkdir(parents=True);owns=True
        if home.exists():
            meta=verify_home(home,resources,host);record=dict(status='verified-existing-original-named-home',marker_sha256=core.sha(home/MARKER))
        else:
            if not args.allow_install:raise core.Unavailable('Explicit --allow-install is required to create a private named home')
            if not 1<=args.timeout<=86400 or not 1<=args.maximum_upstream_memory_bytes<=1099511627776:raise ValueError('Invalid health capacities')
            if annotation.LOCK_SHA!=LOCK['base_annotation_lock_sha256']:raise ValueError('Named lock expects different base annotation lock')
            annotation.verify_home(base,'linux-x86_64-annotation-5.1',host);source_marker=core.sha(base/annotation.MARKER);base_meta=json.loads((base/annotation.MARKER).read_text());pins=resource_files(resources)
            if args.maximum_upstream_memory_bytes<LOCK['minimum_upstream_memory_bytes'] or available_memory()<LOCK['minimum_upstream_memory_bytes']:raise core.Unavailable('At least6GiB upstream memory allowance and available memory required before home creation')
            home.mkdir(parents=True)
            for name,pin in LOCK['members'].items():
                path=home/name;path.parent.mkdir(exist_ok=True,parents=True);raw=(base/name).read_bytes()
                if pin['relocate']:
                    old=annotation.prefix(base)
                    if not raw.startswith(old):raise ValueError('Base configured prefix changed')
                    raw=annotation.prefix(home)+raw[len(old):]
                path.write_bytes(raw);path.chmod(0o755)
            for name in pins:
                if name.startswith('data/accession/'):
                    path=home/name;path.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(resources/name,path)
            (home/'config.txt').write_text(config(home,resources));health={}
            for genome in ['mm10','hg38']:
                work=report/genome;work.mkdir();(work/'peaks.pos').write_text(LOCK['health'][genome]['peaks']);call=execute(home,work,genome,args.timeout,args.maximum_upstream_memory_bytes,1073741824)
                lines=(work/'original.stdout').read_text().splitlines();header=lines[0].split('\t');table=[line.split('\t') for line in lines[1:]];stderr=(work/'original.stderr').read_text()
                if len(header)!=19 or len(table)!=7 or any(len(f)!=19 for f in table) or 'Done annotating peaks file' not in stderr or any(s in stderr for s in ['not found','Could not open',"Can't open",'uninitialized','Illegal division','Segmentation fault']):raise ValueError('Original named health incomplete;inspect retained original stderr')
                byid={f[0]:f for f in table}
                for i,row in enumerate(LOCK['health'][genome]['selected']):
                    label=re.sub(r'-HOMER\d+$','',re.sub(r'-+\d+$','',row['raw_name']))
                    if byid['P'+str(i+1)][8]!=label:raise ValueError('Original named health detailed resource assignment differs')
                health[genome]=dict(rows=7,columns=19,peaks_sha256=core.sha(work/'peaks.pos'),call=call)
                core.write(report/'progress.json',health);print(genome+' original named annotation health passed',flush=True)
            annotation.verify_home(base,'linux-x86_64-annotation-5.1',host)
            if source_marker!=core.sha(base/annotation.MARKER) or pins!=resource_files(resources):raise ValueError('Named source changed during setup')
            files={name:core.sha(home/name) for name in [*LOCK['members'],'config.txt',*[n for n in pins if n.startswith('data/accession/')]]}
            meta=dict(schema_version=1,tool=LOCK['tool'],home=str(home),resource_root=str(resources),profile=LOCK['profile'],version=LOCK['version'],host=host,lock_sha256=LOCK_SHA,files=files,resources=pins,external_files=base_meta['external_files'],health=dict(status=LOCK['health_status'],genomes=health),source_base_marker_sha256=source_marker)
            core.write(home/MARKER,meta);verify_home(home,resources,host);record=dict(status='installed-and-admitted-original-named-annotation',marker_sha256=core.sha(home/MARKER))
    except (core.Unavailable,FileNotFoundError,PermissionError) as failure:code=3;record=dict(status='skipped-unavailable-dependency-or-capability',cause=str(failure))
    except Exception as failure:code=2;record=dict(status='failed-retained',cause=repr(failure))
    record.update(exit_code=code,required_action=RECOVERY,scope=LOCK['scope'],transport_warning=LOCK['transport_warning'])
    if owns:
        core.write(report/'installation.json',record);(report/'WARNINGS.md').write_text('# HOMER NAMED '+('SETUP SKIPPED' if code==3 else 'SETUP FAILED' if code else 'REVIEW WARNINGS')+'\n\n'+record.get('cause',LOCK['scope'])+'\n\n'+RECOVERY+'\n\n'+LOCK['transport_warning']+'\n')
    print(json.dumps(record),file=sys.stderr if code else sys.stdout,flush=True);return code


if __name__=='__main__':raise SystemExit(main())
