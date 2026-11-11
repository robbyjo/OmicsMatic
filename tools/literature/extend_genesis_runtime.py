# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Clone a pinned private GENESIS runtime and add its optional CRAN test dependencies in the new library only."""
import argparse
import gzip
import hashlib
import importlib.util
import io
import json
import os
import re
import shutil
import stat
import subprocess
import traceback
import urllib.request
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
BASE=ROOT/'build/literature-review/genesis-runtime'
REPOSITORIES=['https://cran.r-project.org/bin/windows/contrib/4.6/','https://cloud.r-project.org/bin/windows/contrib/4.6/']
EXPECTED_OPTIONAL={'SPAtest':'3.1.2','poibin':'1.6','COMPoissonReg':'0.8.2'}
ACTION='SKIP dependent optional GENESIS tests when downloads/installation or the named exact prerequisites are unavailable or forbidden. Where permitted, use --allow-download --allow-install with NEW private output paths. Existing runtimes and shared libraries are never changed. FAIL2: preserve the attempt and resolve its stated integrity/process cause before retrying NEW paths. Setup success alone admits no user-data analysis or historical-paper reproduction.'


class Unavailable(RuntimeError):pass


def dcf(text):
    records=[]
    for paragraph in re.split(r'\n\s*\n',text.strip()):
        record={};key=None
        for line in paragraph.splitlines():
            if line[:1].isspace():
                if key is None:raise ValueError('Invalid continued DCF field')
                record[key]+=' '+line.strip()
            else:
                key,value=line.split(':',1)
                if key in record:raise ValueError('Duplicate DCF field')
                record[key]=value.strip()
        if record:records.append(record)
    return records


def version(value):
    parts=re.split(r'[.-]',value);assert all(p.isdigit() for p in parts),'Unsupported exact dependency version syntax'
    return tuple(int(p) for p in parts)+tuple(0 for _ in range(10-len(parts)))


def dependencies(record):
    for field in ['Depends','Imports','LinkingTo']:
        for item in record.get(field,'').split(','):
            if not item.strip():continue
            match=re.fullmatch(r'\s*([A-Za-z][A-Za-z0-9.]*)\s*(?:\((>=|<=|==|>|<)\s*([0-9][0-9.-]*)\))?\s*',item)
            if not match:raise ValueError('Unrecognized explicit dependency: '+item)
            yield match.groups()


def satisfies(actual,operator,required):
    if operator is None:return True
    a,b=version(actual),version(required)
    return {'>=':a>=b,'<=':a<=b,'==':a==b,'>':a>b,'<':a<b}[operator]


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--profile',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--repository',choices=REPOSITORIES,default=REPOSITORIES[0]);p.add_argument('--allow-download',action='store_true');p.add_argument('--allow-install',action='store_true');a=p.parse_args();out=a.out.resolve()
    if out.exists() or not out.is_relative_to(BASE):print('FAIL2: choose NEW private output beneath '+str(BASE)+'. '+ACTION);return 2
    out.mkdir(parents=True);state=dict(status='SKIP3_OPTIONAL_GENESIS_RUNTIME_UNAVAILABLE',analysis_performed=False,required_action=ACTION);code=3;before=None;roots=[];protected={}
    try:
        if os.name!='nt' or not a.profile.is_file():raise Unavailable('The pinned Windows GENESIS runtime profile is missing')
        if not a.allow_download or not a.allow_install:raise Unavailable('Explicit download and installation authorization flags are required')
        profile=json.loads(a.profile.read_bytes());assert profile['tool']=='GENESIS' and profile['version']=='2.42.0' and profile['r_version']=='4.6.1'
        helper=ROOT/'src/main/resources/org/jlinalg/omics/genesis/prepare_genesis_runtime.py';spec=importlib.util.spec_from_file_location('genesis_runtime_closure',helper);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        roots=[Path(profile['r_home']),Path(profile['private_library'])]
        if not all(root.is_dir() for root in roots) or not Path(profile['rscript']).is_file():raise Unavailable('Required pinned R/private-library files are unavailable')
        assert all(not out.is_relative_to(root) and not root.is_relative_to(out) for root in roots)
        before=module.closure(roots);assert before==profile['files'],'Original complete runtime differs from the supplied profile'
        protected={str(f.resolve()):pin(f) for f in [a.profile,helper,Path(__file__)]}
        archives=out/'downloads';archives.mkdir();downloads=[]
        def download(url,file,maximum):
            try:
                req=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-optional-GENESIS-runtime/1.0'})
                with urllib.request.urlopen(req,timeout=90) as response:
                    raw=response.read(maximum+1);receipt=dict(url=url,final_url=response.url,http_status=response.status,content_type=response.headers.get('Content-Type'))
            except OSError as error:raise Unavailable('Official permitted prerequisite download is unavailable: '+url+' '+repr(error)) from error
            assert len(raw)<=maximum;file.write_bytes(raw);receipt.update(file=file.name,**pin(file));downloads.append(receipt);return raw
        raw=download(a.repository+'PACKAGES.gz',archives/'PACKAGES.gz',20000000);decoded=gzip.decompress(raw);assert len(decoded)<=50000000
        entries=dcf(decoded.decode('utf8'));index={r['Package']:r for r in entries};assert len(index)==len(entries)
        installed={'R':'4.6.1'}
        for root in [roots[0]/'library',roots[1]]:
            for description in root.glob('*/DESCRIPTION'):
                item=dcf(description.read_text(encoding='utf8'))[0];installed[item['Package']]=item['Version']
        requested=list(EXPECTED_OPTIONAL);order=[];visiting=set();selected=set()
        for name,expected in EXPECTED_OPTIONAL.items():
            if name not in index or index[name].get('Version')!=expected:
                raise Unavailable('The exact optional package '+name+' '+expected+' is unavailable in the selected official R4.6 index; do not silently install a different edition')
        def visit(name):
            if name in selected:return
            if name in visiting:raise ValueError('Cyclic required optional package dependencies')
            if name not in index:raise Unavailable('Required official R4.6 binary is unavailable: '+name)
            visiting.add(name);record=index[name]
            for dependency,operator,required in dependencies(record):
                if dependency in installed:
                    if not satisfies(installed[dependency],operator,required):raise Unavailable('A new optional test needs an incompatible pinned base dependency: '+dependency)
                else:
                    visit(dependency)
                    if not satisfies(index[dependency]['Version'],operator,required):raise Unavailable('Official binary dependency version cannot satisfy '+name+' -> '+dependency)
            visiting.remove(name);selected.add(name);order.append(name)
        for name in requested:visit(name)
        for name in order:
            record=index[name];file=archives/(name+'_'+record['Version']+'.zip');raw=download(a.repository+file.name,file,100000000)
            if 'MD5sum' in record:assert hashlib.md5(raw).hexdigest()==record['MD5sum'],'Official archive MD5 differs from the retained index; preserve the discrepancy'
            else:downloads[-1]['archive_digest_absent_from_official_index']=True
            with zipfile.ZipFile(io.BytesIO(raw)) as z:
                members=z.infolist();assert len({i.filename for i in members})==len(members) and sum(i.file_size for i in members)<=500000000
                for info in members:
                    parts=info.filename.split('/');assert parts[0]==name and not any(p in ['.','..'] for p in parts) and '\\' not in info.filename and ':' not in info.filename
                    assert not stat.S_ISLNK(info.external_attr>>16)
                checks={}
                for line in z.read(name+'/MD5').decode('utf8').splitlines():
                    if not line.strip():continue
                    match=re.fullmatch(r'([a-f0-9]{32}) [* ](.+)',line.strip());assert match,'Invalid original package MD5 manifest'
                    relative=match[2];assert relative not in checks;checks[relative]=match[1]
                actual={i.filename.removeprefix(name+'/'):hashlib.md5(z.read(i)).hexdigest() for i in members if not i.is_dir() and i.filename!=name+'/MD5'}
                assert actual==checks,'Complete original archive member MD5 inventory differs'
                downloads[-1]['complete_internal_MD5_file_inventory_checked']=len(checks)
                description=dcf(z.read(name+'/DESCRIPTION').decode('utf8'))[0]
                assert description['Package']==name and description['Version']==record['Version']
                assert re.match(r'R 4\.6\.',description.get('Built','')),'Required binary was built for another R minor release'
                assert 'GPL' in description.get('License',''),'Optional-package license requires separate review'
            state.setdefault('optional_packages',[]).append(dict(package=name,version=record['Version'],license=description['License'],built=description['Built'],archive=pin(file)))
        library=out/'library';shutil.copytree(roots[1],library)
        assert {p.relative_to(library).as_posix():pin(p) for p in library.rglob('*') if p.is_file()}=={str(Path(n).relative_to(roots[1])).replace('\\','/'):v for n,v in before.items() if Path(n).is_relative_to(roots[1])}
        script=out/'install.R';lines=['args <- commandArgs(TRUE)','lib <- normalizePath(args[[1]],winslash="/",mustWork=TRUE)',
            '.libPaths(c(lib,file.path(R.home(),"library")),include.site=FALSE)','stopifnot(getRversion()=="4.6.1")']
        for i,name in enumerate(order):lines.append('utils::install.packages(args[['+str(i+2)+']],lib=lib,repos=NULL,type="win.binary")')
        for name in order:lines.append('stopifnot(requireNamespace("'+name+'",quietly=TRUE),dirname(find.package("'+name+'"))==lib,utils::packageDescription("'+name+'",fields="Version")=="'+index[name]['Version']+'")')
        lines.append('cat("PASS_NEW_PRIVATE_OPTIONAL_GENESIS_DEPENDENCIES_NO_ANALYSIS\\n")');script.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n');temp=out/'tmp';temp.mkdir()
        environment=dict(os.environ,R_HOME=profile['r_home'],R_LIBS_USER=str(library),R_LIBS_SITE='',R_DEFAULT_PACKAGES='base',TMPDIR=str(temp),LC_ALL='C',LANG='C')
        command=[profile['rscript'],'--vanilla',str(script),str(library),*[str(archives/(name+'_'+index[name]['Version']+'.zip')) for name in order]]
        r=subprocess.run(command,cwd=out,env=environment,stdin=subprocess.DEVNULL,capture_output=True,timeout=600)
        (out/'install.stdout').write_bytes(r.stdout);(out/'install.stderr').write_bytes(r.stderr);save(out/'install.process.json',dict(command=command,exit_code=r.returncode))
        assert r.returncode==0 and b'PASS_NEW_PRIVATE_OPTIONAL_GENESIS_DEPENDENCIES_NO_ANALYSIS' in r.stdout,'Original optional package installation/loading failed; inspect retained output'
        installed_files={p.relative_to(library).as_posix():pin(p) for p in library.rglob('*') if p.is_file()}
        for name,value in before.items():
            path=Path(name)
            if path.is_relative_to(roots[1]):assert installed_files[path.relative_to(roots[1]).as_posix()]==value,'Original copied base package changed during optional installation'
        state.update(status='PASS_PRIVATE_ORIGINAL_GENESIS_RUNTIME_SETUP_ONLY',version='2.42.0',private_library=str(library),rscript=dict(path=profile['rscript'],**pin(Path(profile['rscript']))),
            installed_files=installed_files,parent_profile=pin(a.profile),downloads=downloads,producer=pin(Path(__file__)),original_private_library_unchanged=True,shared_R_distribution_unchanged=True);code=0
    except Unavailable as error:state['cause']=repr(error)
    except Exception as error:state.update(status='FAIL2_OPTIONAL_GENESIS_RUNTIME_SETUP',cause=repr(error));code=2;(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    finally:
        try:
            assert all(pin(Path(n))==v for n,v in protected.items())
            if before is not None:assert module.closure(roots)==before
        except Exception as error:state.update(status='FAIL2_OPTIONAL_GENESIS_RUNTIME_PRESERVATION',cause=repr(error));code=2
        (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','Only a new private dependency library was prepared; no user-data analysis is admitted.')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8',newline='\n');save(out/'COMPLETED.json',state)
    print(state['status'],state.get('cause','')[:6000],flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete source/runtime/archive preservation checks')
    raise SystemExit(main())
