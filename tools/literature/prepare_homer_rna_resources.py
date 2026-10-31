#!/usr/bin/env python3
"""Verify or explicitly prepare the eight pinned original HOMER RNA resources.

This prepares data only. It does not install/admit an RNA runtime or run analysis.
Extraction needs --allow-install; missing downloads also need --allow-download.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import stat
import sys
import time
import urllib.error
import urllib.request
import zipfile
import install_homer as core

RAW=Path(__file__).with_name('homer-rna-lock.json').read_bytes()
LOCK=json.loads(RAW)
LOCK_SHA=hashlib.sha256(RAW).hexdigest()
RECOVERY=('Required action: use Linux x86_64 Python, inside WSL on Windows. Where permitted run '
    'python3 tools/literature/prepare_homer_rna_resources.py --resource-root NEW_PRIVATE_RNA_RESOURCE_ROOT '
    '--cache PRIVATE_ARCHIVE_CACHE --report NEW_REPORT --allow-install --archive-dir OFFLINE_ARCHIVE_DIRECTORY. '
    'Supply the exact four v7.0 human/mouse/hg38/mm10 archives in homer-rna-lock.json. '
    'Alternatively omit --archive-dir and add --allow-download where downloads are permitted. '
    'An existing root is verified without extraction. Missing, changed, forbidden or unsupported dependencies '
    'SKIP the affected analysis, exit 3; other failures exit 2. Preserve corrupt archives, partial roots and reports; '
    'correct the named cause and use NEW paths. Read WARNINGS.md. These eight data files alone do not admit '
    'an RNA runtime or analysis. Cell Ranger remains excluded.')

def safe(path):
    value=core.private(path)
    if not re.fullmatch(r'/[A-Za-z0-9_./-]+',str(value)):
        raise core.Unavailable('Original RNA resources need real shell-safe ASCII Linux paths')
    return value

def resource_files(root):
    result={}
    for name,pin in LOCK['resources'].items():
        path=core.private(root/name)
        if not path.is_file() or path.stat().st_size!=pin['bytes'] or core.sha(path)!=pin['sha256']:
            raise core.Unavailable('Original RNA resource absent, linked or changed: '+name)
        result[name]=pin['sha256']
    return result

def archive_path(name,pin,args,cache):
    source=core.private(args.archive_dir)/pin['filename'] if args.archive_dir else cache/pin['filename']
    if not source.exists():
        if args.archive_dir or not args.allow_download:
            raise core.Unavailable('Original offline archive missing or download not allowed: '+pin['filename'])
        cache.mkdir(parents=True,exist_ok=True)
        partial=cache/(pin['filename']+'.download')
        if partial.exists():raise ValueError('Prior download exists; preserve it and choose a NEW cache')
        total=0;deadline=time.monotonic()+1800
        with urllib.request.urlopen(LOCK['config_entries'][name][3],timeout=45) as response,partial.open('xb') as out:
            while True:
                if time.monotonic()>deadline:raise core.Unavailable('Original RNA resource archive download timed out')
                block=response.read(1048576)
                if not block:break
                total+=len(block)
                if total>pin['bytes']:raise ValueError('Original RNA archive exceeds pinned size')
                out.write(block)
        if total!=pin['bytes'] or core.sha(partial)!=pin['sha256']:
            raise ValueError('Downloaded original RNA archive content pin differs; partial retained')
        partial.rename(source)
    source=core.private(source)
    if not source.is_file() or source.stat().st_size!=pin['bytes'] or core.sha(source)!=pin['sha256']:
        raise core.Unavailable('Original resource archive size/SHA256 differs; preserve archive: '+str(source))
    return source

def extract(archive,root,files):
    with zipfile.ZipFile(archive) as source:
        for name,pin in files.items():
            matches=[i for i in source.infolist() if i.filename==pin['original_zip_member']]
            if len(matches)!=1:raise ValueError('Pinned original RNA ZIP member missing or duplicated: '+name)
            member=matches[0]
            if member.is_dir() or stat.S_ISLNK(member.external_attr>>16) or member.file_size!=pin['bytes']:
                raise ValueError('Invalid pinned original RNA ZIP member: '+name)
            destination=root/name
            destination.parent.mkdir(parents=True,exist_ok=True)
            digest=hashlib.sha256();size=0
            with source.open(member) as data,destination.open('xb') as out:
                while True:
                    block=data.read(1048576)
                    if not block:break
                    size+=len(block)
                    if size>pin['bytes']:raise ValueError('Original RNA ZIP member exceeds pinned capacity')
                    digest.update(block);out.write(block)
            if size!=pin['bytes'] or digest.hexdigest()!=pin['sha256']:
                raise ValueError('Extracted original RNA ZIP member content differs; partial retained')

def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['resource-root','cache','report']:parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--archive-dir',type=Path)
    parser.add_argument('--allow-install',action='store_true')
    parser.add_argument('--allow-download',action='store_true')
    args=parser.parse_args(argv);owns=False;code=0;record={}
    try:
        core.host();root=safe(args.resource_root);cache=core.private(args.cache);report=core.private(args.report)
        paths=[root,cache,report]
        if args.archive_dir:paths.append(core.private(args.archive_dir))
        for i,a in enumerate(paths):
            for b in paths[i+1:]:
                if a==b or a in b.parents or b in a.parents:
                    raise ValueError('Use separate nonnested RNA resource/cache/report/offline paths')
        if report.exists():raise ValueError('Report exists; choose NEW report')
        report.mkdir(parents=True);owns=True
        if root.exists():
            record=dict(status='verified-existing-pinned-RNA-resources',files=resource_files(root))
        else:
            if not args.allow_install:raise core.Unavailable('Explicit --allow-install required to create a private RNA resource root')
            sources={n:archive_path(n,p,args,cache) for n,p in LOCK['resource_archives'].items()}
            required=sum(p['bytes'] for p in LOCK['resources'].values());ancestor=root.parent
            while not ancestor.exists():ancestor=ancestor.parent
            if shutil.disk_usage(ancestor).free<required+1073741824:
                raise core.Unavailable('Insufficient disk for all RNA resources and a 1 GiB margin')
            root.mkdir(parents=True)
            for name,archive in sources.items():
                extract(archive,root,{n:p for n,p in LOCK['resources'].items() if p['archive']==name})
            record=dict(status='extracted-and-verified-original-RNA-resources',files=resource_files(root),archives={n:core.sha(p) for n,p in sources.items()})
    except (core.Unavailable,FileNotFoundError,PermissionError,urllib.error.URLError,TimeoutError,ConnectionError) as failure:
        code=3;record=dict(status='skipped-unavailable-dependency-or-capability',cause=str(failure))
    except Exception as failure:
        code=2;record=dict(status='failed-retained',cause=repr(failure))
    record.update(exit_code=code,required_action=RECOVERY,lock_sha256=LOCK_SHA,transport_warning=LOCK['transport_warning'],scope=LOCK['scope'])
    if owns:
        core.write(report/'resources.json',record)
        (report/'WARNINGS.md').write_text('# HOMER RNA RESOURCES '+('SKIPPED' if code==3 else 'FAILED' if code else 'REVIEW WARNINGS')+'\n\n'+record.get('cause',LOCK['scope'])+'\n\n'+RECOVERY+'\n\n'+LOCK['transport_warning']+'\n')
    print(json.dumps(record),file=sys.stderr if code else sys.stdout,flush=True)
    return code

if __name__=='__main__':raise SystemExit(main())
