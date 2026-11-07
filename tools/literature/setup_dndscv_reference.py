# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare pinned original dndscv with permitted missing CRAN binaries in a NEW private library."""
import argparse,gzip,hashlib,io,json,os,re,subprocess,urllib.request,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save
from setup_chipseeker_reference import inventory


def dcf(raw):
    result=[]
    for block in raw.decode('utf8').strip().split('\n\n'):
        row={};key=None
        for line in block.splitlines():
            if line[:1].isspace():
                assert key is not None;row[key]+=' '+line.strip()
            else:
                key,value=line.split(':',1);assert key not in row;row[key]=value.strip()
        result.append(row)
    return result


def binary_members(blob,package):
    with zipfile.ZipFile(io.BytesIO(blob)) as z:
        assert len(z.namelist())==len(set(z.namelist()));files={}
        for item in z.infolist():
            path=PurePosixPath(item.filename);assert not path.is_absolute() and '..' not in path.parts and path.parts[0]==package and '\\' not in item.filename
            if not item.is_dir():files[PurePosixPath(*path.parts[1:]).as_posix()]=z.read(item)
        assert 'MD5' in files;expected={}
        for line in files['MD5'].decode('utf8').splitlines():
            match=re.fullmatch(r'([0-9a-f]{32}) [ *](.+)',line);assert match is not None;name=match[2];assert name not in expected;expected[name]=match[1]
        assert set(expected)==set(files)-{'MD5'},'Original binary member manifest is incomplete'
        assert all(hashlib.md5(files[n]).hexdigest()==v for n,v in expected.items()),'Original binary member checksum differs'
        return dict(members=len(files),upstream_internal_MD5_members=len(expected),captured_archive_sha256=hashlib.sha256(blob).hexdigest(),whole_archive_publisher_checksum_available=False)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-install',action='store_true');p.add_argument('--cran-base',choices=['https://cran.r-project.org','https://cloud.r-project.org','https://cran.rstudio.com'],default='https://cran.r-project.org');a=p.parse_args();base=ROOT/'build/literature-review/dndscv-runtime';out=a.out.resolve();assert out.is_relative_to(base) and not out.exists();out.mkdir();source_home=base/'source-intake-1';receipt=json.loads((source_home/'SOURCE.json').read_bytes());assert all(pin(source_home/'source'/n)==v for n,v in receipt['source_members'].items());libs=list(map(Path,json.loads((ROOT/'build/literature-review/soupx-runtime/reference-1.6.2-1/STARTED.json').read_bytes())['libraries']));before=inventory(libs);save(out/'LIBRARIES-BEFORE.json',before);script=ROOT/'tools/literature/install_dndscv_reference.R';pins={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__).resolve(),script,source_home/'SOURCE.json']}
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    action='Read WARNINGS.md, installer.stdout/stderr, R-RESULT.json and the saved source/download receipts. If installation/download is forbidden or the exact dependency cannot be supplied, SKIP dependent dndscv analysis. Otherwise provision the named permitted source/dependency in a NEW private library and retry NEW paths. Never modify existing libraries, original functions, inputs or substitute a source commit. Namespace preparation alone admits no user analysis.';state=dict(status='SKIP3_DNDSCV_REFERENCE_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,source_pins=pins,installation_authorized=a.allow_install,required_action=action)
    try:
        assert a.allow_install,'Installation is not enabled; dependent analysis skipped';available={}
        for lib in libs:
            for path in lib.glob('*/DESCRIPTION'):
                rows=dcf(path.read_bytes());assert len(rows)==1;available.setdefault(rows[0]['Package'],dict(version=rows[0]['Version'],path=str(path.parent)))
        downloads=out/'downloads';downloads.mkdir();url=a.cran_base+'/bin/windows/contrib/4.6/PACKAGES.gz';save(out/'DOWNLOAD-STARTED.json',dict(index_URL=url,mirror_explicitly_selected=True))
        with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-audit/1'}),timeout=60) as response:raw=response.read(10000001);assert len(raw)<=10000000
        (downloads/'PACKAGES.gz').write_bytes(raw);index={r['Package']:r for r in dcf(gzip.decompress(raw))};required=['seqinr','poilog'];selected={}
        def visit(name):
            if name=='R' or name in available or name in selected:return
            assert name in index,'Required Windows CRAN binary unavailable: '+name;row=index[name];selected[name]=row
            for field in ['Depends','Imports','LinkingTo']:
                for value in row.get(field,'').split(','):
                    dep=value.strip().split(' ',1)[0]
                    if dep:visit(dep)
        for name in required:visit(name)
        bindings={}
        for name,row in selected.items():
            url=a.cran_base+'/bin/windows/contrib/4.6/'+name+'_'+row['Version']+'.zip'
            with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-audit/1'}),timeout=90) as response:blob=response.read(60000001);assert len(blob)<=60000000
            path=downloads/(name+'_'+row['Version']+'.zip');path.write_bytes(blob)
            if 'MD5sum' in row:assert hashlib.md5(blob).hexdigest()==row['MD5sum'],'Official CRAN binary MD5 mismatch'
            integrity=binary_members(blob,name);bindings[name]=dict(url=url,index_record=row,file=pin(path),integrity=integrity)
        save(out/'DOWNLOADS.json',dict(index_URL=a.cran_base+'/bin/windows/contrib/4.6/PACKAGES.gz',index=pin(downloads/'PACKAGES.gz'),packages=bindings,preexisting_resolution=available));library=out/'R-lib';library.mkdir();command=['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla',str(script),str(out),str(source_home/'source'),str(library),*map(str,libs)];save(out/'STARTED.json',dict(command=command,libraries=list(map(str,libs)),source_pins=pins,source_commit=receipt['commit']));env=dict(os.environ,LANG='C',LC_ALL='C',LANGUAGE='en',R_LIBS=';'.join(map(str,[library,*libs])),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',TMPDIR=str(out),R_USER_CACHE_DIR=str(out))
        with (out/'installer.stdout').open('xb') as stdout,(out/'installer.stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
        save(out/'process.json',dict(command=command,exit_code=r.returncode));assert r.returncode==0,'Original private installation failed; inspect installer.stderr';report=json.loads((out/'R-RESULT.json').read_bytes());assert report['status']=='loaded-exact-source-dndscv-reference-only' and report['source_bodies_and_formals_checked'];state.update(status='installed-source-bound-original-dndscv-reference-only',profile=report,source_receipt=pin(source_home/'SOURCE.json'),download_receipt=pin(out/'DOWNLOADS.json'),installed_private_packages=list(bindings))
    except Exception as error:state['detail']=str(error)
    finally:
        after=inventory(libs);save(out/'LIBRARIES-AFTER.json',after);state['preexisting_libraries_unchanged']=before==after;assert all(pin(ROOT/n)==v for n,v in pins.items()) and all(pin(source_home/'source'/n)==v for n,v in receipt['source_members'].items())
        if before!=after:state.update(status='FAIL2_PREEXISTING_LIBRARY_CHANGED',detail='Inspect before/after inventories; no analysis admitted')
        save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('detail','')+'\n\n**REQUIRED ACTION:** '+action+'\n',encoding='utf8',newline='\n')
    print(json.dumps({k:v for k,v in state.items() if k not in ['profile','source_pins']}));return 0 if state['status']=='installed-source-bound-original-dndscv-reference-only' else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/dependency/library checks')
    raise SystemExit(main())
