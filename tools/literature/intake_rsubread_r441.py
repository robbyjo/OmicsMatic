# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare locked original R4.4.1/compiler archives and the extraction utility from verified cache or permitted downloads."""
import argparse,concurrent.futures,json,shutil,traceback,urllib.request,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

LOCK={
 'R-4.4.1-win.exe':dict(url='https://cran.r-project.org/bin/windows/base/old/4.4.1/R-4.4.1-win.exe',bytes=85900616,sha256='eb68b50f21927e5e8ae3721d8afa88f74a09cbab19c6a87da41b5a44bd3158df'),
 'rtools44-6459-6401.exe':dict(url='https://cran.r-project.org/bin/windows/Rtools/rtools44/files/rtools44-6459-6401.exe',bytes=451591776,sha256='de9602992848faaeddca41f118dd6dc3de9bbfb00b8d87ebe5522af1dad1904b')}
UNPACK=dict(url='https://www.rathlev-home.de/tools/download/innounp-2.zip',bytes=620210,sha256='f8e45c9676bebc7d7570108ab572673b541fcf7a7d3d24a04319ac6b5315b4e8')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--cache',type=Path);p.add_argument('--unpacker-cache',type=Path);p.add_argument('--allow-download',action='store_true');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/rsubread-runtime');out.mkdir(parents=True);state=dict(status='SKIP3_R441_REFERENCE_DOWNLOAD_UNAVAILABLE',installed=False,analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    def get(name,lock,cache,destination):
        expected={key:lock[key] for key in ['bytes','sha256']};final=lock['url']
        if cache is not None and cache.is_file():
            assert pin(cache)==expected,'Cached original archive differs; preserve it and do not refresh its hash';shutil.copyfile(cache,destination);cached=True
        else:
            if not a.allow_download:raise PermissionError('Required exact archive not cached and download forbidden: '+name)
            with urllib.request.urlopen(lock['url'],timeout=180) as response,destination.open('xb') as file:
                final=response.url;total=0
                while raw:=response.read(1048576):total+=len(raw);assert total<=lock['bytes'];file.write(raw)
            cached=False
        assert pin(destination)==expected,'Original download checksum differs; dependent preparation skipped';return dict(url=lock['url'],final_url=final,status='downloaded-original-installer-only',installer_executed=False,from_verified_cache=cached,**expected)
    try:
        def archive(item):name,lock=item;return name,get(name,lock,a.cache/name if a.cache else None,out/name)
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:records=dict(pool.map(archive,LOCK.items()))
        unpack=out/'unpacker';unpack.mkdir();metadata=get('innounp-2.zip',UNPACK,a.unpacker_cache,unpack/'innounp-2.zip')
        with zipfile.ZipFile(unpack/'innounp-2.zip') as z:
            assert z.testzip() is None;total=0
            for item in z.infolist():
                if item.is_dir():continue
                rel=PurePosixPath(item.filename);assert not rel.is_absolute() and '..' not in rel.parts and ':' not in item.filename and '\\' not in item.filename and item.file_size<16777216;total+=item.file_size;assert total<67108864;file=unpack/rel;file.parent.mkdir(parents=True,exist_ok=True);file.write_bytes(z.read(item))
        save(unpack/'DOWNLOAD.json',dict(requested_url=UNPACK['url'],final_url=metadata['final_url'],bytes=UNPACK['bytes'],sha256=UNPACK['sha256'],installed=False));state.update(status='passed-original-R441-and-Rtools44-download-only',archives=records,unpacker=pin(unpack/'DOWNLOAD.json'),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except PermissionError as error:state['error']=str(error)
    except Exception as error:state.update(status='FAIL2_R441_REFERENCE_ARCHIVE_INTAKE',error=repr(error));(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    state['required_action']='Provide the exact locked archives in the declared cache, or permit downloads where allowed. If files are unavailable, forbidden or different, skip dependent preparation. Retain all downloaded bytes/errors; do not update hashes or substitute versions. No installer is executed by intake or extraction.';save(out/'COMPLETED.json',state);(out/'WARNINGS.md').write_text('# Original reference archive intake only\n\n'+state['status']+'\n\n'+state.get('error','No package build or scientific analysis has run.')+'\n\nRequired action: '+state['required_action']+'\n',encoding='utf8');print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 3 if state['status'].startswith('SKIP3') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable archive/version/checksum gates')
    raise SystemExit(main())
