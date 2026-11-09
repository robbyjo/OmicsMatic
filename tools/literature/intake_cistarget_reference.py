# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Capture GPL ctxcore/RcisTarget references into new private workspace paths."""
import argparse,hashlib,io,json,stat,tarfile,traceback,urllib.request,zipfile
from pathlib import Path,PurePosixPath
from setup_deseq2_reference import ROOT,pin,save

CTX_URL='https://files.pythonhosted.org/packages/57/be/b60e62d10d80c80c5492739d6f2833ca2973bbc4088fcecf7053b5719da7/ctxcore-0.2.0-py3-none-any.whl'
CTX_SHA='b90570377e26280c4861ebad1f4cee2fe598167c5d4bd12c1b713f03c9682627'
R_URL='https://bioconductor.org/packages/3.22/bioc/src/contrib/RcisTarget_1.29.0.tar.gz'


def fetch(url):
    request=urllib.request.Request(url,headers={'User-Agent':'OmicsMatic-source-audit/1.0'})
    with urllib.request.urlopen(request,timeout=45) as response:
        raw=response.read(67108865);metadata=dict(url=url,resolved_url=response.url,http_status=response.status,headers=dict(response.headers))
    assert len(raw)<=67108864,'Source response exceeds64 MiB capacity';return raw,metadata


def safe(name):
    path=PurePosixPath(name);assert name and path.as_posix()==name and not path.is_absolute() and '..' not in path.parts and not any(c in name for c in '\\:\0\r\n')
    return path


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);parser.add_argument('--allow-download',action='store_true');args=parser.parse_args()
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/cistarget-runtime');out.mkdir(parents=True)
    state=dict(status='SKIP3_CISTARGET_REFERENCE_DOWNLOAD_UNAVAILABLE',analysis_admitted=False,implementation_credit=False,whole_goal_complete=False)
    try:
        assert args.allow_download,'Download permission absent; skip dependent validation without a network request'
        raw,metadata=fetch('https://pypi.org/pypi/ctxcore/0.2.0/json');(out/'ctxcore-pypi.json').write_bytes(raw);package=json.loads(raw)
        assert package['info']['version']=='0.2.0' and 'General Public License' in package['info']['license']
        wheel=next(row for row in package['urls'] if row['filename']=='ctxcore-0.2.0-py3-none-any.whl')
        assert wheel['url']==CTX_URL and wheel['digests']['sha256']==CTX_SHA
        raw,metadata=fetch(CTX_URL);archive=out/'ctxcore-0.2.0.whl';archive.write_bytes(raw)
        assert hashlib.sha256(raw).hexdigest()==CTX_SHA,'Changed source: preserve capture, skip reference execution, do not refresh hashes'
        save(out/'ctxcore-download.json',dict(**metadata,**pin(archive)));dest=out/'ctxcore-original';dest.mkdir();members={}
        with zipfile.ZipFile(archive) as opened:
            assert len(opened.namelist())==len(set(opened.namelist())) and opened.testzip() is None
            for info in opened.infolist():
                name=info.filename;safe(name);assert not stat.S_ISLNK(info.external_attr>>16) and info.file_size<=33554432
                if info.is_dir():continue
                target=dest/name;assert target.resolve().is_relative_to(dest);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(opened.read(info));members[name]=pin(target)
        save(out/'ctxcore-members.json',members)
        # This archived release is a declared reference, never an inferred paper edition.
        raw,metadata=fetch(R_URL);archive=out/'RcisTarget_1.29.0.tar.gz';archive.write_bytes(raw);save(out/'rcistarget-download.json',dict(**metadata,**pin(archive)))
        dest=out/'rcistarget-original';dest.mkdir();members={}
        with tarfile.open(fileobj=io.BytesIO(raw),mode='r:gz') as opened:
            all_members=opened.getmembers();assert len(all_members)<2000 and sum(m.size for m in all_members)<268435456
            for member in all_members:
                name=member.name.rstrip('/');safe(name);assert not member.issym() and not member.islnk()
                if member.isdir():continue
                assert member.isfile() and member.size<=33554432 and name.startswith('RcisTarget/')
                relative=name[len('RcisTarget/'):];assert relative and relative not in members;target=dest/relative;assert target.resolve().is_relative_to(dest)
                target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(opened.extractfile(member).read());members[relative]=pin(target)
        description=(dest/'DESCRIPTION').read_text(encoding='utf8');assert 'Version: 1.29.0' in description and 'License: GPL-3' in description
        save(out/'rcistarget-members.json',members)
        state.update(status='PASS_GPL_CISTARGET_PRIVATE_REFERENCES_ONLY',ctxcore_version='0.2.0',ctxcore_sha256=CTX_SHA,rcistarget_version='1.29.0',
            rcistarget_source=pin(archive),ctxcore_manifest=pin(out/'ctxcore-members.json'),rcistarget_manifest=pin(out/'rcistarget-members.json'),
            source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},software_installed=False,original_program_executed=False,
            restricted_pycistarget_wrapper_downloaded=False,scope='GPL reference capture only; no historical paper edition inference, runtime installation, analysis or catalog credit.')
    except Exception as failure:
        state['error']=repr(failure);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
        # A failed network/dependency intake admits no original analysis. Retain all earlier captures.
    save(out/'COMPLETED.json',state)
    (out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state.get('error','GPL references captured; no installation or analysis performed.')+'\n\nRequired action: if download/install/execution is forbidden or unavailable, skip dependent reference analysis. Preserve all captures, correct the named prerequisite where permitted and retry NEW paths. Do not silently substitute versions or accept changed hashes. Native implementation is not yet available.\n',encoding='utf8')
    print(state['status'],state.get('error',''),flush=True);return 0 if state['status'].startswith('PASS_') else 3


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/extraction checks')
    raise SystemExit(main())
