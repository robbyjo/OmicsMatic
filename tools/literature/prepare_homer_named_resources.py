#!/usr/bin/env python3
"""Verify or explicitly extract the twelve pinned original named-annotation resources.

Archives remain private. HTTP downloads require --allow-download, and extraction
to a new root requires --allow-install. Never overwrites a cache or resource root.
"""
import argparse
import hashlib
from pathlib import Path
import shutil
import stat
import sys
import time
import urllib.request
import zipfile
import install_homer_named as named

RECOVERY=('Required action: use Linux x86_64 Python. Run python3 tools/literature/prepare_homer_named_resources.py '
    '--resource-root NEW_PRIVATE_RESOURCE_ROOT --cache PRIVATE_ARCHIVE_CACHE --report NEW_REPORT --allow-install '
    '--archive-dir OFFLINE_ARCHIVE_DIRECTORY. Supply the four exact archives in homer-named-lock.json. '
    'If permitted, omit --archive-dir and add --allow-download. Downloads use the official HTTP archive URLs '
    'and locked size/SHA256, not authenticated signatures. Existing resources are verified without extraction. '
    'If download/install is forbidden or unavailable, SKIP named annotation, exit3; other independent tools can continue. '
    'Preserve corrupt caches, failed roots and reports; correct the named cause and use NEW paths. '
    'These files do not admit sequence, RNA or ontology operations. '+named.RECOVERY)


def archive_path(name,pin,args,cache):
    source=(named.core.private(args.archive_dir)/pin['filename']) if args.archive_dir else cache/pin['filename']
    if not source.exists():
        if args.archive_dir or not args.allow_download:raise named.core.Unavailable('Original offline archive missing or download not allowed: '+pin['filename'])
        cache.mkdir(parents=True,exist_ok=True);temporary=cache/(pin['filename']+'.download')
        if temporary.exists():raise ValueError('Partial download exists; preserve it and choose a new cache: '+str(temporary))
        url=named.LOCK['config_entries'][name][3];total=0;deadline=time.monotonic()+1800
        with urllib.request.urlopen(url,timeout=45) as response,temporary.open('xb') as out:
            while True:
                if time.monotonic()>deadline:raise named.core.Unavailable('Original archive download timed out')
                block=response.read(1048576)
                if not block:break
                total+=len(block)
                if total>pin['bytes']:raise ValueError('Original archive download exceeds pinned size')
                out.write(block)
        if total!=pin['bytes'] or named.core.sha(temporary)!=pin['sha256']:raise ValueError('Original archive download checksum/size differs; partial retained')
        temporary.rename(source)
    source=named.core.private(source)
    if not source.is_file() or source.stat().st_size!=pin['bytes'] or named.core.sha(source)!=pin['sha256']:raise named.core.Unavailable('Original archive size/SHA256 differs; preserve cache: '+str(source))
    return source


def extract(archive,root,files):
    with zipfile.ZipFile(archive) as source:
        for name,pin in files.items():
            matches=[i for i in source.infolist() if i.filename==pin['original_zip_member']]
            if len(matches)!=1:raise ValueError('Pinned original member missing/duplicated: '+name)
            member=matches[0]
            if member.is_dir() or stat.S_ISLNK(member.external_attr>>16) or member.file_size!=pin['bytes']:raise ValueError('Invalid pinned original ZIP member: '+name)
            path=root/name;path.parent.mkdir(parents=True,exist_ok=True);digest=hashlib.sha256();total=0
            with source.open(member) as data,path.open('xb') as out:
                while True:
                    block=data.read(1048576)
                    if not block:break
                    total+=len(block)
                    if total>pin['bytes']:raise ValueError('Original ZIP member exceeds pinned capacity')
                    digest.update(block);out.write(block)
            if total!=pin['bytes'] or digest.hexdigest()!=pin['sha256']:raise ValueError('Extracted original member checksum differs: '+name)


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ['resource-root','cache','report']:parser.add_argument('--'+name,type=Path,required=True)
    parser.add_argument('--archive-dir',type=Path);parser.add_argument('--allow-install',action='store_true');parser.add_argument('--allow-download',action='store_true');args=parser.parse_args(argv);owns=False;code=0;record={}
    try:
        named.core.host();root=named.safe(args.resource_root);cache=named.core.private(args.cache);report=named.core.private(args.report)
        paths=[root,cache,report]
        if args.archive_dir:paths.append(named.core.private(args.archive_dir))
        for i,a in enumerate(paths):
            for b in paths[i+1:]:
                if a==b or a in b.parents or b in a.parents:raise ValueError('Use separate nonnested resource/cache/report/offline directories')
        if report.exists():raise ValueError('Report exists; choose NEW report')
        report.mkdir(parents=True);owns=True
        if root.exists():
            observed=named.resource_files(root);record=dict(status='verified-existing-pinned-named-resources',files=observed)
        else:
            if not args.allow_install:raise named.core.Unavailable('Explicit --allow-install is required to create a private resource root')
            sources={name:archive_path(name,pin,args,cache) for name,pin in named.LOCK['archives'].items()}
            required=sum(p['bytes'] for p in named.LOCK['resources'].values())
            ancestor=root.parent
            while not ancestor.exists():ancestor=ancestor.parent
            if shutil.disk_usage(ancestor).free<required+1073741824:raise named.core.Unavailable('Insufficient free disk for complete named resources plus1GiB margin')
            root.mkdir(parents=True)
            for name,archive in sources.items():extract(archive,root,{n:p for n,p in named.LOCK['resources'].items() if p['archive']==name})
            observed=named.resource_files(root);record=dict(status='extracted-and-verified-original-named-resources',files=observed,archives={name:named.core.sha(path) for name,path in sources.items()})
    except (named.core.Unavailable,FileNotFoundError,PermissionError,urllib.error.URLError,TimeoutError,ConnectionError) as failure:code=3;record=dict(status='skipped-unavailable-dependency-or-capability',cause=str(failure))
    except Exception as failure:code=2;record=dict(status='failed-retained',cause=repr(failure))
    record.update(exit_code=code,required_action=RECOVERY,transport_warning=named.LOCK['transport_warning'],scope=named.LOCK['scope'])
    if owns:
        named.core.write(report/'resources.json',record);(report/'WARNINGS.md').write_text('# HOMER NAMED RESOURCES '+('SKIPPED' if code==3 else 'FAILED' if code else 'REVIEW WARNINGS')+'\n\n'+record.get('cause',named.LOCK['scope'])+'\n\n'+RECOVERY+'\n\n'+named.LOCK['transport_warning']+'\n')
    print(__import__('json').dumps(record),file=sys.stderr if code else sys.stdout);return code


if __name__=='__main__':raise SystemExit(main())
