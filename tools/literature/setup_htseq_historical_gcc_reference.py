# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Build exact historical HTSeq releases privately; never translate counting source or mutate shared Python."""
import argparse
import hashlib
import json
import os
import re
import subprocess
import tarfile
import urllib.request
import zipfile
from pathlib import Path,PurePosixPath

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/htseq-runtime'
DISTRO='JLinAlg-Omics-SAIGE-20261026'
PY39=ROOT/'build/literature-review/signac-runtime/peaks-reference-setup-3/python/bin/python3.9'
PACKAGES={
 'py39':[('pip','23.3.2'),('setuptools','57.5.0'),('wheel','0.38.4'),('Cython','0.29.37'),('numpy','1.23.5'),('pysam','0.19.1')],
 'py27':[('pip','20.3.4'),('setuptools','44.1.1'),('wheel','0.34.2'),('numpy','1.16.6'),('pysam','0.15.4'),('virtualenv','16.7.12')],
}


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def linux(path):
    path=path.resolve();assert path.drive.lower()=='d:'
    return '/mnt/d/'+path.as_posix()[3:]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--family',choices=['py27','py39'],required=True)
    parser.add_argument('--allow-download',action='store_true')
    parser.add_argument('--allow-install',action='store_true')
    args=parser.parse_args();out=args.out.resolve()
    assert out.is_relative_to(BASE) and not out.exists();out.mkdir(parents=True)
    def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
    action='Skip dependent analyses until the exact historical profile is available. Provision it only where permitted, preserve this attempt and retry into NEW private workspace paths; do not substitute another HTSeq release or translate its counting source.'
    if not(args.allow_download and args.allow_install):
        save(out/'SKIPPED.json',dict(status='SKIP3_HISTORICAL_SETUP_NOT_AUTHORIZED',required_action=action));raise SystemExit(3)
    cache=out/'archives';cache.mkdir();downloads=[]
    def fetch(url,target,limit=134217728):
        request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-HTSeq-reference/1.0'})
        with urllib.request.urlopen(request,timeout=60) as response:
            body=response.read(limit+1);assert len(body)<=limit
            final_url=response.geturl()
        target.write_bytes(body);downloads.append(dict(url=url,final_url=final_url,**pin(target)));save(out/'DOWNLOAD-PROGRESS.json',downloads)
        return body
    def command(label,argv,cwd=None,home=None,timeout=1200,extra=None):
        environment={'PATH':(linux(home/'bin')+':' if home else '')+'/usr/bin:/bin','HOME':linux(out/'temp-home'),
            'LANG':'C','LC_ALL':'C','PYTHONDONTWRITEBYTECODE':'1','PYTHONNOUSERSITE':'1',
            'PIP_NO_INDEX':'1','PIP_DISABLE_PIP_VERSION_CHECK':'1','SWIG':'/usr/bin/false',
            'CC':'/usr/bin/gcc','CXX':'/usr/bin/g++','LDSHARED':'/usr/bin/gcc -shared'}
        if extra:environment.update(extra)
        full=['wsl.exe','-d',DISTRO,'--cd',linux(cwd or ROOT),'--','/usr/bin/env','-i',*[k+'='+v for k,v in environment.items()],
            '/usr/bin/timeout','--signal=TERM','--kill-after=10s',str(timeout)+'s',*map(str,argv)]
        with (out/(label+'.stdout')).open('xb') as stdout,(out/(label+'.stderr')).open('xb') as stderr:
            result=subprocess.run(full,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=timeout+45)
        save(out/(label+'.process.json'),dict(command=full,exit_code=result.returncode))
        assert result.returncode==0,label+': inspect retained stdout/stderr'
        return (out/(label+'.stdout')).read_bytes()
    def extract(archive,target):
        assert not target.exists();target.mkdir()
        with tarfile.open(archive,'r:*') as source:
            entries=source.getmembers();assert len(entries)<=30000
            total=0
            for member in entries:
                name=PurePosixPath(member.name)
                assert not name.is_absolute() and '..' not in name.parts and '\\' not in member.name
                if member.isdir():continue
                assert member.isfile() and member.size<=67108864
                total+=member.size;assert total<=536870912
                destination=target/Path(*name.parts);destination.parent.mkdir(parents=True,exist_ok=True)
                with source.extractfile(member) as stream:body=stream.read(member.size+1)
                assert len(body)==member.size;destination.write_bytes(body)
        children=list(target.iterdir());assert len(children)==1 and children[0].is_dir()
        return children[0]
    (out/'temp-home').mkdir();save(out/'INCOMPLETE.json',dict(status='private-historical-HTSeq-setup-in-progress',family=args.family))
    try:
        wheels=[]
        for name,version in PACKAGES[args.family]:
            metadata=json.loads(fetch('https://pypi.org/pypi/'+name+'/'+version+'/json',cache/(name+'-metadata.json'),16777216))
            candidates=[]
            for row in metadata['urls']:
                filename=row['filename']
                if row['packagetype']!='bdist_wheel':continue
                universal=filename.endswith('-py3-none-any.whl') if args.family=='py39' else filename.endswith(('-py2.py3-none-any.whl','-py2-none-any.whl'))
                if name=='Cython' and args.family=='py39':universal=filename.endswith('-py2.py3-none-any.whl')
                native=('-cp39-cp39-' in filename if args.family=='py39' else '-cp27-cp27mu-' in filename) and 'manylinux' in filename and 'x86_64' in filename
                if universal or native:candidates.append(row)
            assert candidates,(name,version,'No exact compatible wheel')
            row=sorted(candidates,key=lambda r:r['filename'])[0];assert row['url'].startswith('https://files.pythonhosted.org/')
            path=cache/row['filename'];fetch(row['url'],path)
            assert pin(path)['sha256']==row['digests']['sha256'] and path.stat().st_size==row['size']
            wheels.append(dict(package=name,version=version,path=path.name,**pin(path)))
        if args.family=='py39':
            sdk=PY39;assert '3.9.20' in command('base-version',[linux(sdk),'-B','--version']).decode()
            editions=['0.11.1','0.13.5']
        else:
            archive=cache/'Python-2.7.18.tar.xz'
            fetch('https://www.python.org/ftp/python/2.7.18/Python-2.7.18.tar.xz',archive,33554432)
            assert hashlib.md5(archive.read_bytes()).hexdigest()=='fd6cc8ec0a78c44036f825e739f36e5a'
            save(out/'PYTHON-SOURCE.json',dict(url='https://www.python.org/ftp/python/2.7.18/Python-2.7.18.tar.xz',
                archive=pin(archive),official_release='https://www.python.org/downloads/release/python-2718/',
                official_MD5_checked=True,signature_checked=False,shared_python_modified=False))
            source=extract(archive,out/'python27-source');sdk_home=out/'python27';sdk=sdk_home/'bin/python2.7'
            command('python27-configure',['/bin/sh','configure','--prefix='+linux(sdk_home),'--enable-unicode=ucs4','--without-ensurepip'],cwd=source)
            command('python27-build',['/usr/bin/make','-j4'],cwd=source,timeout=1800)
            command('python27-install',['/usr/bin/make','install'],cwd=source)
            command('base-version',[linux(sdk),'-B','-c','import sys;assert sys.version_info[:3]==(2,7,18);assert sys.maxunicode==1114111;print(sys.version)'])
            editions=['0.5.3','0.6.0']
        source_report=json.loads((BASE/'source-intake-2/COMPLETED.json').read_bytes())
        profiles=[]
        for version in editions:
            profile=out/version;profile.mkdir();home=profile/'venv'
            try:
                if args.family=='py39':
                    command(version+'-venv',[linux(sdk),'-B','-m','venv','--without-pip',linux(home)])
                else:
                    virtual=cache/next(w['path'] for w in wheels if w['package']=='virtualenv');target=profile/'virtualenv-source';target.mkdir()
                    with zipfile.ZipFile(virtual) as archive:
                        for name in archive.namelist():
                            p=PurePosixPath(name);assert not p.is_absolute() and '..' not in p.parts
                        archive.extractall(target)
                    command(version+'-venv',[linux(sdk),'-E','-s','-B',linux(target/'virtualenv.py'),'--no-pip','--no-setuptools','--no-wheel',linux(home)])
                python=home/'bin/python'
                pipwheel=cache/next(w['path'] for w in wheels if w['package']=='pip')
                packages=[name+'=='+value for name,value in PACKAGES[args.family] if name!='virtualenv']
                code='import sys,runpy;sys.path.insert(0,'+repr(linux(pipwheel))+');sys.argv='+repr(['pip','install','--no-index','--no-deps','--find-links',linux(cache),*packages])+';runpy.run_module("pip",run_name="__main__")'
                command(version+'-dependencies',[linux(python),'-E','-s','-B','-c',code],home=home)
                entry=next(r for r in source_report['editions'] if r['version']==version)
                archive=BASE/'source-intake-2'/version/entry['archive']['filename'];assert pin(archive)=={k:entry['archive'][k] for k in ['bytes','sha256']}
                source=extract(archive,profile/'build-source')
                wheel_dir=profile/'built-wheels';wheel_dir.mkdir()
                command(version+'-build',[linux(python),'-B','-m','pip','wheel','--no-index','--no-deps','--no-build-isolation','--wheel-dir',linux(wheel_dir),linux(source)],home=home)
                built=list(wheel_dir.glob('*.whl'));assert len(built)==1
                command(version+'-install',[linux(python),'-B','-m','pip','install','--no-index','--no-deps',linux(built[0])],home=home)
                probe='import HTSeq,numpy,pysam,sys,json;assert HTSeq.__version__=='+repr(version)+';print(json.dumps(dict(HTSeq=HTSeq.__version__,numpy=numpy.__version__,pysam=pysam.__version__,python=sys.version,executable=sys.executable)))'
                profile_metadata=json.loads(command(version+'-probe',[linux(python),'-E','-s','-B','-c',probe],home=home))
                status=dict(version=version,status='installed-original-historical-HTSeq',home=linux(home),profile=profile_metadata,
                    official_source_archive=entry['archive'],built_wheel=pin(built[0]),scientific_source_translation=False,
                    original_count_health_pending=True,implementation_credit=False)
                save(profile/'COMPLETED.json',status);profiles.append(status)
            except Exception as failure:
                status=dict(version=version,status='FAIL2_HISTORICAL_PROFILE_SETUP',error=repr(failure),required_action=action)
                save(profile/'FAILURE.json',status);profiles.append(status)
            save(out/'PROFILE-PROGRESS.json',profiles)
        successful=[r for r in profiles if r['status']=='installed-original-historical-HTSeq']
        result=dict(schema_version=1,status='completed-private-historical-HTSeq-reference-setup' if len(successful)==len(editions) else 'partial-private-historical-HTSeq-reference-setup',
            family=args.family,profiles=profiles,successful_profiles=len(successful),wheels=wheels,
            setup_source=pin(Path(__file__)),shared_or_system_python_modified=False,analysis_results_admitted=False,
            original_count_health_pending=True,implementation_credit=False,required_action_on_failure=action)
        save(out/('COMPLETED.json' if len(successful)==len(editions) else 'PARTIAL.json'),result)
        print(json.dumps(dict(status=result['status'],profiles=[dict(version=r['version'],status=r['status']) for r in profiles])))
        if len(successful)!=len(editions):raise SystemExit(2)
    except BaseException as failure:
        if not isinstance(failure,SystemExit):save(out/'FAILURE.json',dict(status='FAIL2_HISTORICAL_HTSEQ_SETUP',error=repr(failure),required_action=action))
        raise


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-source/dependency checks')
    main()
