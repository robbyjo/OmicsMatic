# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Privately install original official SNPRelate prerequisites into a NEW workspace R library; no analysis."""
import argparse
import datetime
import gzip
import hashlib
import html.parser
import json
import os
import re
import subprocess
import traceback
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT, pin, save
BASE=ROOT/'build/literature-review/snprelate-runtime'
ACTION=('SNPRelate analysis is not admitted by setup. If required downloads or installation are unavailable/forbidden, '
        'skip dependent SNPRelate analysis. Supply the named compatible original R4.6 Windows binaries in a new private '
        'workspace library, or resolve the named prerequisite where permitted. Do not replace standalone KING, PLINK2-hosted '
        'KING or the paper\'s SNPRelate context with one another. Existing shared R libraries are not changed.')


class Links(html.parser.HTMLParser):
    def __init__(self):super().__init__();self.links=[]
    def handle_starttag(self,tag,attrs):
        if tag=='a':
            for key,value in attrs:
                if key=='href':self.links.append(value)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path)
    p.add_argument('--allow-download',action='store_true');p.add_argument('--allow-install',action='store_true')
    p.add_argument('--cran-base',choices=['https://cloud.r-project.org','https://cran.r-project.org'],default='https://cloud.r-project.org')
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE)
    out.mkdir(parents=True);records=[]
    state=dict(status='SKIP3_SNPRELATE_RUNTIME_SETUP_UNAVAILABLE',analysis_performed=False,new_catalog_credit=False,whole_goal_complete=False,required_action=ACTION)
    try:
        assert a.allow_download and a.allow_install,'Require explicit --allow-download and --allow-install; no analysis fallback'
        def download(url,name,maximum):
            target=out/name;request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-original-R-reference'})
            with urllib.request.urlopen(request,timeout=90) as response,target.open('xb') as dest:
                count=0
                for chunk in iter(lambda:response.read(1048576),b''):
                    count+=len(chunk);assert count<=maximum;dest.write(chunk)
                record=dict(url=url,final_url=response.geturl(),status=response.status,
                            fetched_utc=datetime.datetime.now(datetime.timezone.utc).isoformat(),**pin(target))
            records.append(record);save(out/(name+'.receipt.json'),record);return target,record
        rscript=Path('C:/Program Files/R/R-4.6.1/bin/Rscript.exe');assert rscript.is_file(),'Require the named original Windows R4.6.1 installation'
        runtime_pin=pin(rscript)
        packages=[]
        for package,version in [('gdsfmt','1.48.2'),('SNPRelate','1.46.0')]:
            page,receipt=download('https://bioconductor.org/packages/release/bioc/html/'+package+'.html',package+'.html',2000000)
            links=Links();links.feed(page.read_text(encoding='utf8'))
            name=package+'_'+version+'.zip';matches=[urllib.parse.urljoin(receipt['final_url'],url) for url in links.links if url.endswith('/'+name) or url==name]
            assert len(set(matches))==1,('Named official binary not uniquely linked',package,matches)
            binary,_=download(matches[0],name,100000000)
            with zipfile.ZipFile(binary) as archive:
                description=archive.read(package+'/DESCRIPTION').decode('utf8')
                assert re.search(r'(?m)^Version: '+re.escape(version)+r'\s*$',description)
                assert re.search(r'(?m)^Built: R 4\.6\.',description),'Original binary is not built for R4.6'
            packages.append((package,version,binary))
        index,_=download(a.cran_base+'/bin/windows/contrib/4.6/PACKAGES.gz','cran-PACKAGES.gz',20000000)
        text=gzip.decompress(index.read_bytes()).decode('utf8')
        block=next(b for b in text.split('\n\n') if b.startswith('Package: RhpcBLASctl\n'))
        version=re.search(r'(?m)^Version: (\S+)$',block).group(1)
        checksum=re.search(r'(?m)^MD5sum: (\S+)$',block);md5=checksum.group(1) if checksum else None
        binary,_=download(a.cran_base+'/bin/windows/contrib/4.6/RhpcBLASctl_'+version+'.zip','RhpcBLASctl_'+version+'.zip',20000000)
        if md5 is not None:
            assert hashlib.md5(binary.read_bytes()).hexdigest()==md5,'CRAN binary differs from its published index MD5; do not substitute'
        with zipfile.ZipFile(binary) as archive:
            assert archive.testzip() is None,'Original CRAN binary ZIP CRC failed'
            description=archive.read('RhpcBLASctl/DESCRIPTION').decode('utf8')
            assert re.search(r'(?m)^Version: '+re.escape(version)+r'\s*$',description)
            assert re.search(r'(?m)^Built: R 4\.6\.',description)
        packages.insert(1,('RhpcBLASctl',version,binary))
        library=out/'library';library.mkdir()
        program=[]
        def literal(value):return json.dumps(str(value).replace('\\','/'))
        program += ['private_library <- '+literal(library),'.libPaths(c(private_library, file.path(R.home(), "library")), include.site=FALSE)',
                    'stopifnot(getRversion() == "4.6.1")']
        for package,version,binary in packages:
            program.append('utils::install.packages('+literal(binary)+', repos=NULL, type="win.binary", lib=private_library)')
            program.append('stopifnot(utils::packageDescription('+literal(package)+', lib.loc=private_library, fields="Version") == '+literal(version)+')')
        program += ['for (package in c("gdsfmt", "RhpcBLASctl", "SNPRelate")) {',
                    '  stopifnot(requireNamespace(package, quietly=TRUE), dirname(find.package(package)) == normalizePath(private_library, winslash="/"))',
                    '  cat("EXACT_NAMESPACE", package, utils::packageDescription(package, fields="Version"), "normalized", as.character(utils::packageVersion(package)), find.package(package), "\\n")','}',
                    'cat("R_VERSION", R.version.string, "\\n")','cat("SETUP_ONLY_NO_ANALYSIS\\n")']
        script=out/'install-originals.R';script.write_text('\n'.join(program)+'\n',encoding='utf8')
        environment=dict(os.environ,LC_ALL='C',LANG='C',R_LIBS_USER=str(library),R_LIBS_SITE='',R_DEFAULT_PACKAGES='base')
        command=[str(rscript),'--vanilla',str(script)]
        with (out/'install.stdout').open('xb') as so,(out/'install.stderr').open('xb') as se:
            result=subprocess.run(command,cwd=out,env=environment,stdin=subprocess.DEVNULL,stdout=so,stderr=se,timeout=240)
        save(out/'install.process.json',dict(command=command,exit_code=result.returncode))
        assert result.returncode==0,('Original private package installation failed; inspect retained install.stdout and install.stderr',result.returncode)
        assert b'SETUP_ONLY_NO_ANALYSIS' in (out/'install.stdout').read_bytes()
        assert pin(rscript)==runtime_pin
        installed={p.relative_to(library).as_posix():pin(p) for p in sorted(library.rglob('*')) if p.is_file()}
        state.update(status='PASS_PRIVATE_ORIGINAL_SNPRELATE146_GDSFMT_RUNTIME_SETUP_ONLY',records=records,
                     packages=[dict(name=p,version=v,archive_pin=pin(f)) for p,v,f in packages],installed_files=installed,
                     cran_index_md5=md5,archive_sha256_locally_recorded=True,
                     rscript=dict(path=str(rscript),**runtime_pin),private_library=str(library),producer=pin(Path(__file__)))
    except Exception as error:
        state.update(cause=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('cause','')+'\n\nREQUIRED ACTION: '+ACTION+'\n',encoding='utf8')
    print(state['status'],state.get('cause','')[:1800],ACTION,flush=True)
    return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable private original-package integrity checks')
    raise SystemExit(main())
