# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Download/preserve the exact official reference into NEW workspace paths only."""
import argparse,hashlib,json,stat,sys,traceback,urllib.request,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

URL='https://tools.gi.ut.ee/tools/MR-MEGA_v0.2.zip'
SHA256='e91eb5bdba0a398619e6cd98c351ea72d94e09d6f5a1d25bb7da8a66da938ffe'


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--allow-download',action='store_true');a=p.parse_args()
    out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/mrmega-runtime');out.mkdir(parents=True)
    state=dict(status='SKIP3_MRMEGA_REFERENCE_DOWNLOAD_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        assert a.allow_download,'Download permission absent; no network request, original execution or installation performed'
        request=urllib.request.Request(URL,headers={'User-Agent':'OmicsMatic-source-audit/1.0'})
        with urllib.request.urlopen(request,timeout=60) as response:
            raw=response.read(33554433);metadata=dict(url=URL,resolved_url=response.url,http_status=response.status,headers=dict(response.headers))
        state['status']='FAIL2_MRMEGA_REFERENCE_INTAKE';archive=out/'MR-MEGA_v0.2.zip';archive.write_bytes(raw)
        assert len(raw)<=33554432 and hashlib.sha256(raw).hexdigest()==SHA256,'Official source archive differs; preserve it, skip dependent execution and never refresh a scientific hash to accept another source'
        save(out/'DOWNLOAD.json',dict(status='DOWNLOADED_EXACT_ORIGINAL_ARCHIVE_ONLY',**metadata,**pin(archive)))
        dest=out/'original';dest.mkdir();members={}
        with zipfile.ZipFile(archive) as opened:
            assert len(opened.namelist())==len(set(opened.namelist()))==100 and opened.testzip() is None
            for info in opened.infolist():
                name=info.filename;path=PurePosixPath(name);mode=info.external_attr>>16
                assert name and path.as_posix()==name and not path.is_absolute() and '..' not in path.parts and not any(c in name for c in '\\:\0\r\n')
                assert not stat.S_ISLNK(mode) and (not stat.S_IFMT(mode) or stat.S_ISREG(mode)) and info.file_size<8388608
                data=opened.read(info);target=dest/name;assert target.resolve().is_relative_to(dest);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data);members[name]=pin(target)
        save(out/'MEMBERS.json',members)
        state.update(status='PASS_EXACT_MRMEGA_PRIVATE_SOURCE_INTAKE_ONLY',archive=pin(archive),original_members=100,member_manifest=pin(out/'MEMBERS.json'),
            source_pins={Path(__file__).relative_to(ROOT).as_posix():pin(Path(__file__))},original_program_executed=False,software_installed=False,
            redistribution_scope='Private source capture only. The project development evidence does not redistribute upstream archive/source/binary bytes.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','Exact private source captured; no program executed or installed.')+'\n\nACTION: skip dependent original validation if downloads, installation or execution are forbidden/unavailable. Preserve raw captures and complete errors; correct the named problem where permitted and use NEW output paths. Do not substitute a different source edition or refresh scientific hashes. This helper is not the native MR-MEGA file workflow.\n',encoding='utf8')
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 3 if state['status'].startswith('SKIP3_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source intake checks')
    raise SystemExit(main())
